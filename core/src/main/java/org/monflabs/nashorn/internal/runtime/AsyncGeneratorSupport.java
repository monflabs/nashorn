/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Philippe Riand designates this
 * particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.monflabs.nashorn.internal.runtime;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.objects.NativePromise;

/**
 * Runs an ECMAScript 2018 async generator body on its own virtual thread.
 *
 * An async generator is both a generator and an async function: its body may
 * {@code yield} (to whoever is iterating it) and {@code await} (a promise), and
 * every {@code next}/{@code return}/{@code throw} on it answers with a promise.
 * This is the generator baton-pass (a body suspended at a yield, resumed by the
 * next request) fused with the async driver (a suspension at an await subscribes
 * to a promise and resumes from a job), with a queue of pending requests in
 * front so overlapping calls are served one at a time (25.5.3.3).
 *
 * As with generators and async functions, the body is an ordinary compiled
 * function run on a virtual thread, so try/finally, labelled breaks and
 * deoptimisation work across a suspension with no special handling.
 */
public final class AsyncGeneratorSupport {
    /** Set on the body's own thread; the prologue consumes it to fall through. */
    private static final ThreadLocal<AsyncGeneratorSupport> ENTERING = new ThreadLocal<>();

    /** The async generator whose body this thread runs, for yield/await to find. */
    private static final ThreadLocal<AsyncGeneratorSupport> RUNNING = new ThreadLocal<>();

    /** What the driver sends into the body. */
    private sealed interface Resume {
        record Value(Object value) implements Resume { }
        record Error(Object error) implements Resume { }
        record Return(Object value) implements Resume { }
    }

    /** What the body reports back. */
    private sealed interface Step {
        record Started() implements Step { }
        record Awaiting(Object value) implements Step { }
        record Yielded(Object value) implements Step { }
        record Returned(Object value) implements Step { }
        record Failed(RuntimeException error) implements Step { }
    }

    /** Thrown to unwind the body when it is asked to return, so finally runs. */
    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final transient Object value;
        Abort(final Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    /** A queued next/return/throw and the promise its call handed back. */
    private static final int NEXT = 0;
    private static final int RETURN = 1;
    private static final int THROW = 2;
    private record Request(int kind, Object value, NativePromise promise) { }

    private final BlockingQueue<Resume> toBody = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<Step> toDriver = new ArrayBlockingQueue<>(1);
    private final ArrayDeque<Request> queue = new ArrayDeque<>();

    private final ScriptFunction body;
    private final Object self;
    private final Object[] args;
    private final Global global;

    private Thread thread;
    private boolean done;

    /**
     * @param body   the async generator function, re-entered on the body's thread
     * @param self   its receiver
     * @param args   its arguments
     * @param global the realm
     */
    public AsyncGeneratorSupport(final ScriptFunction body, final Object self, final Object[] args, final Global global) {
        this.body = body;
        this.self = self;
        this.args = args;
        this.global = global;
    }

    /**
     * Whether this call is the body's own re-entry rather than a call that makes
     * the generator. Consumes the marker.
     *
     * @return true if the caller should fall through into the body
     */
    public static boolean entering() {
        if (ENTERING.get() == null) {
            return false;
        }
        ENTERING.remove();
        return true;
    }

    /** The async generator whose body this thread runs, or null. */
    public static AsyncGeneratorSupport running() {
        return RUNNING.get();
    }

    // ---- body side ---------------------------------------------------------

    /**
     * ES2018 Await inside an async generator body.
     *
     * @param value what is awaited
     * @return what it fulfilled with
     */
    public Object await(final Object value) {
        deliver(new Step.Awaiting(value));
        return afterResume(take(toBody));
    }

    /**
     * ES2018 25.5.3.7 AsyncGeneratorYield: the value is awaited, then produced
     * to the consumer, and the body waits to be resumed.
     *
     * @param value what is yielded
     * @return the value the generator is resumed with
     */
    public Object yield(final Object value) {
        final Object awaited = await(value);
        deliver(new Step.Yielded(awaited));
        return afterYieldResume(take(toBody));
    }

    /**
     * The resumption of a suspended {@code yield}: like {@link #afterResume}, but
     * a return completion awaits its value first (25.5.3.7 AsyncGeneratorYield -
     * a {@code return()} at a yield resumes with {@code Await(resumptionValue)}
     * before the body unwinds).
     */
    private Object afterYieldResume(final Resume resume) {
        if (resume instanceof Resume.Return ret) {
            throw new Abort(await(ret.value()));
        }
        return afterResume(resume);
    }

    /**
     * ES2018 {@code yield*} in an async generator: await the value, yield it,
     * and report how the generator was resumed (so the delegation can forward a
     * throw()/return() to the inner iterator) rather than acting on it.
     *
     * @param value the value the inner iterator produced
     * @return two elements: "next"/"throw"/"return" and the value sent in
     */
    public Object[] yieldStarStep(final Object value) {
        // The delegated value is yielded as-is: a value from an async inner
        // iterator is not unwrapped, and a value from a sync one was already
        // awaited by the async-from-sync adaptor. (Only a plain yield awaits.)
        deliver(new Step.Yielded(value));
        final Resume resume = take(toBody);
        if (resume instanceof Resume.Return ret) {
            // AsyncGeneratorYield step 8: a return resumption awaits its value
            // before the delegation acts on it (the delegation then awaits again
            // when forwarding to the inner iterator, or completing without one).
            return new Object[] { "return", await(ret.value()) };
        }
        if (resume instanceof Resume.Error thrown) {
            return new Object[] { "throw", thrown.error() };
        }
        return new Object[] { "next", ((Resume.Value) resume).value() };
    }

    /**
     * The unwinding an async generator body performs to return a value, which is
     * how {@code yield*} passes on a return it could not delegate.
     *
     * @param value the value to return
     * @return the exception to throw
     */
    public static RuntimeException returning(final Object value) {
        return new Abort(value);
    }

    /**
     * Rethrows an async-generator return unwind, so a generated {@code catch}
     * (or a synthetic one, such as a destructuring's iterator-close guard) cannot
     * swallow it and turn a {@code return()} into an ordinary completion.
     *
     * @param thrown whatever the catch block caught
     */
    static void rethrowIfAbort(final Throwable thrown) {
        if (thrown instanceof Abort abort) {
            throw abort;
        }
    }

    private Object afterResume(final Resume resume) {
        if (resume instanceof Resume.Return ret) {
            throw new Abort(ret.value());
        }
        if (resume instanceof Resume.Error thrown) {
            throw ECMAException.create(thrown.error(), null, -1, -1);
        }
        return ((Resume.Value) resume).value();
    }

    /**
     * Runs the body as far as its parameter bindings, before the generator
     * object exists (25.3.3.1 via FunctionDeclarationInstantiation), so a
     * default that throws throws at the call.
     */
    public void bindParameters() {
        start();
        final Step step = take(toDriver);
        if (step instanceof Step.Started) {
            return;
        }
        done = true;
        if (step instanceof Step.Failed failed) {
            throw failed.error();
        }
        throw new IllegalStateException("async generator body ran before its parameters were bound");
    }

    /** Where a body whose parameters were bound at the call waits for its first request. */
    public void parametersBound() {
        deliver(new Step.Started());
        afterResume(take(toBody));
    }

    // ---- driver side -------------------------------------------------------

    /**
     * Enqueue a next/return/throw and return the promise it settles.
     *
     * @param kind  NEXT, RETURN or THROW
     * @param value the argument
     * @return the promise for the {value, done} result
     */
    private NativePromise enqueue(final int kind, final Object value) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        final boolean idle = queue.isEmpty();
        queue.add(new Request(kind, value, promise));
        if (idle) {
            pumpFront();
        }
        return promise;
    }

    /** next(value): a promise for {value, done}. */
    public Object next(final Object value) {
        return enqueue(NEXT, value);
    }

    /** return(value): a promise for {value, done:true}. */
    public Object doReturn(final Object value) {
        return enqueue(RETURN, value);
    }

    /** throw(error): a promise that rejects (or {value, done} if a finally recovers). */
    public Object doThrow(final Object error) {
        return enqueue(THROW, error);
    }

    /** Serve the request at the head of the queue, then the next, until empty. */
    private void pumpFront() {
        final Request req = queue.peek();
        if (req == null) {
            return;
        }
        if (done) {
            queue.poll();
            settleDone(req);
            return;
        }
        if (thread == null && req.kind() != NEXT) {
            // return/throw on a generator that never started: complete or reject
            // without running the body (its finally blocks never entered).
            queue.poll();
            done = true;
            settleDone(req);
            return;
        }
        advanceFront(req, toResume(req));
    }

    /**
     * Resume the front request's body, settling it on yield/return/throw and
     * subscribing (async) on await - on settlement the body is resumed and this
     * is re-entered, the same request still at the head.
     */
    private void advanceFront(final Request req, final Resume resume) {
        final Step step = resumeBody(resume);
        if (step instanceof Step.Awaiting awaiting) {
            try {
                NativePromise.await(global, awaiting.value(),
                        value -> advanceFront(req, new Resume.Value(value)),
                        error -> advanceFront(req, new Resume.Error(error)));
            } catch (final ECMAException wrapperError) {
                // PromiseResolve threw (a poisoned constructor): the await is a
                // throw completion, delivered back into the body at its await point
                advanceFront(req, new Resume.Error(wrapperError.getThrown()));
            }
            return;
        }
        queue.poll();
        if (step instanceof Step.Yielded yielded) {
            resolve(req.promise(), yielded.value(), false);
        } else if (step instanceof Step.Returned returned) {
            done = true;
            resolve(req.promise(), returned.value(), true);
        } else {
            done = true;
            final RuntimeException error = ((Step.Failed) step).error();
            if (error instanceof ECMAException thrown) {
                NativePromise.rejectAsyncPromise(req.promise(), thrown.getThrown());
            } else {
                throw error;
            }
        }
        pumpFront();
    }

    private Step resumeBody(final Resume resume) {
        if (thread == null) {
            start();
        } else {
            put(toBody, resume);
        }
        return take(toDriver);
    }

    private Resume toResume(final Request req) {
        return switch (req.kind()) {
            case RETURN -> new Resume.Return(req.value());
            case THROW -> new Resume.Error(req.value());
            default -> new Resume.Value(req.value());
        };
    }

    /**
     * Settle a request against an already-finished (or never-started) generator
     * (25.5.3.4 AsyncGeneratorResumeNext), then serve the next queued request. A
     * return awaits its value first (AwaitReturn), which suspends the drain until
     * it settles - so the pump is continued from the await's callbacks, not here.
     */
    private void settleDone(final Request req) {
        switch (req.kind()) {
            case RETURN -> {
                try {
                    NativePromise.await(global, req.value(),
                            awaited -> {
                                resolve(req.promise(), awaited, true);
                                pumpFront();
                            },
                            error -> {
                                NativePromise.rejectAsyncPromise(req.promise(), error);
                                pumpFront();
                            });
                } catch (final ECMAException wrapperError) {
                    // PromiseResolve threw (a poisoned constructor): reject and drain on
                    NativePromise.rejectAsyncPromise(req.promise(), wrapperError.getThrown());
                    pumpFront();
                }
            }
            case THROW -> {
                NativePromise.rejectAsyncPromise(req.promise(), req.value());
                pumpFront();
            }
            default -> {
                resolve(req.promise(), ScriptRuntime.UNDEFINED, true);
                pumpFront();
            }
        }
    }

    private void resolve(final NativePromise promise, final Object value, final boolean isDone) {
        final org.monflabs.nashorn.internal.runtime.ScriptObject result = global.newObject();
        result.set("value", value, 0);
        result.set("done", isDone, 0);
        NativePromise.resolveAsyncPromise(promise, result);
    }

    private void start() {
        thread = Thread.ofVirtual().name("nashorn-async-generator").unstarted(() -> {
            ENTERING.set(this);
            RUNNING.set(this);
            JobQueue.markWorkerThread();
            try {
                Context.runWithGlobal(global, () ->
                    deliver(new Step.Returned(ScriptRuntime.apply(body, self, args))));
            } catch (final Abort abort) {
                deliver(new Step.Returned(abort.value));
            } catch (final RuntimeException e) {
                deliver(new Step.Failed(e));
            } finally {
                RUNNING.remove();
                ENTERING.remove();
            }
        });
        thread.start();
    }

    private void deliver(final Step step) {
        put(toDriver, step);
    }

    private static <T> void put(final BlockingQueue<T> queue, final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T take(final BlockingQueue<T> queue) {
        try {
            return queue.take();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
