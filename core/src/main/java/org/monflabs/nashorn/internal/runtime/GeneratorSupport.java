/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.monflabs.nashorn.internal.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * Runs a generator body on its own virtual thread, so that {@code yield} can be
 * an ordinary blocking call.
 *
 * The alternative - rewriting the body into a state machine - founders on
 * Nashorn's IR, which has no goto, so try/finally would need a hand-built entry
 * table; it would have to synthesise functions, which breaks lazy recompilation;
 * and hoisting every local into a state object would make them all Object and
 * defeat optimistic typing. On a virtual thread the body compiles as an entirely
 * ordinary function, so arbitrary control flow, try/finally, labelled breaks and
 * deoptimisation all work with no special handling.
 *
 * Control is handed back and forth over two one-slot queues, so exactly one of
 * the two threads runs at a time and the generator is never concurrent.
 */
public final class GeneratorSupport {
    /**
     * Set on a generator's own thread and consumed by the first thing the body
     * does, so that exactly one call falls through into the body.
     *
     * It cannot simply stay set for the thread's lifetime: a generator body that
     * calls another generator function - which is what "yield* inner()" does -
     * would then run that one's body inline instead of getting a generator back.
     */
    private static final ThreadLocal<GeneratorSupport> ENTERING = new ThreadLocal<>();

    /** The generator whose body this thread is running, for yield to find. */
    private static final ThreadLocal<GeneratorSupport> RUNNING = new ThreadLocal<>();

    /** What the caller sends in: a value to resume with, or a request to finish. */
    private sealed interface Resume {
        record Next(Object value) implements Resume { }
        record Return(Object value) implements Resume { }
        record Throw(Object error) implements Resume { }
    }

    /** What the body sends back: a yielded value, a return, or a failure. */
    private sealed interface Step {
        /** The parameters are bound and the body is waiting to be advanced. */
        record Started() implements Step { }
        record Yielded(Object value) implements Step { }
        /** A yield* passing the inner iterator's own result object through. */
        record Delegated(Object result) implements Step { }
        record Returned(Object value) implements Step { }
        record Failed(RuntimeException error) implements Step { }
    }

    /**
     * Rethrows the unwinding of a generator body, which a script catch block
     * must not be able to hold on to.
     *
     * A generator asked to return() is unwound with an exception so that its
     * finally blocks run, and a catch block on the way out would otherwise
     * catch it and turn a return into an ordinary resumption.
     *
     * @param thrown whatever the catch block caught
     */
    public static void rethrowIfAbort(final Throwable thrown) {
        if (thrown instanceof Abort abort) {
            throw abort;
        }
    }

    /** Thrown inside the body to unwind it when the caller calls return(). */
    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Object value;

        Abort(final Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private final BlockingQueue<Resume> toBody = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<Step> toCaller = new ArrayBlockingQueue<>(1);

    private final ScriptFunction body;
    private final Object self;
    private final Object[] args;
    private final Global global;

    private Thread thread;
    private boolean done;

    /** Whether the body is between a resume and the step it answers with. */
    private boolean executing;

    /**
     * Set when nobody can advance this generator any more.
     *
     * Once it is set the body must never block handing a value back: whatever it
     * yielded last is still sitting unconsumed in the one-slot queue, so a
     * blocking put would wait for a reader that is never coming and the thread
     * would be stuck for the life of the program.
     */
    private volatile boolean abandoned;

    /**
     * @param body   the generator function, which is re-entered on the generator's thread
     * @param self   its this value
     * @param args   its arguments
     * @param global the realm the generator belongs to
     */
    public GeneratorSupport(final ScriptFunction body, final Object self, final Object[] args, final Global global) {
        this.body = body;
        this.self = self;
        this.args = args;
        this.global = global;
    }

    /**
     * Whether this call is the one that should run a generator body, rather than
     * create a generator. Consumes the marker, so only the first call on a
     * generator's thread says yes.
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

    /**
     * The generator whose body the calling thread is running.
     *
     * @return the generator, or null outside any generator body
     */
    public static GeneratorSupport running() {
        return RUNNING.get();
    }

    /** Whether the generator has finished, by returning or throwing. */
    public boolean isDone() {
        return done;
    }

    /**
     * Suspends the body and hands a value to whoever is advancing the generator.
     *
     * @param value the yielded value
     * @return the value the generator is resumed with
     */
    public Object yield(final Object value) {
        deliver(new Step.Yielded(value));
        return awaitResume();
    }

    /**
     * Yields a value and reports how the generator was resumed instead of acting
     * on it.
     *
     * This is what {@code yield*} needs. An ordinary yield turns a throw() into
     * a throw and a return() into an unwinding, both at the point of the yield;
     * a delegating one has to hand either to the iterator it is delegating to,
     * which may well answer with a value and carry on.
     *
     * @param value what to yield
     * @return two elements: how it was resumed - "next", "throw" or "return" -
     *         and the value that came with it
     */
    public Object[] yieldDelegating(final Object value) {
        deliver(new Step.Delegated(value));
        final Resume resume = take(toBody);
        if (resume instanceof Resume.Return ret) {
            return new Object[] { "return", ret.value() };
        }
        if (resume instanceof Resume.Throw thrown) {
            return new Object[] { "throw", thrown.error() };
        }
        return new Object[] { "next", ((Resume.Next)resume).value() };
    }

    /**
     * The unwinding a generator body performs when it is to return a value,
     * which is how {@code yield*} passes on a return it could not delegate.
     *
     * @param value what the generator returns
     * @return the exception to throw
     */
    public static RuntimeException returning(final Object value) {
        return new Abort(value);
    }

    /**
     * Runs the body as far as its parameter bindings and no further.
     *
     * ES2015 25.2.1.1 binds a generator's parameters at the call, before the
     * generator object exists, so a default that throws throws there. The body
     * is a whole function re-entered on this thread, so the parameter list can
     * only be run here - and the call waits for it, which is what keeps its
     * effects in front of everything the caller does next.
     */
    public void bindParameters() {
        start();
        final Step step = take(toCaller);
        if (step instanceof Step.Started) {
            return;
        }
        // the body cannot reach its first statement without passing the barrier,
        // so anything else is a parameter list that threw
        done = true;
        if (step instanceof Step.Failed failed) {
            throw failed.error();
        }
        throw new IllegalStateException("generator body ran before its parameters were bound");
    }

    /**
     * Where a body whose parameters were bound at the call waits for its first
     * next(), which is where an ordinary generator's body starts.
     */
    public void parametersBound() {
        deliver(new Step.Started());
        awaitResume();
    }

    private Object awaitResume() {
        final Resume resume = take(toBody);
        if (resume instanceof Resume.Return ret) {
            // return() unwinds the body so that its finally blocks run
            throw new Abort(ret.value());
        }
        if (resume instanceof Resume.Throw thrown) {
            throw ECMAException.create(thrown.error(), null, -1, -1);
        }
        return ((Resume.Next)resume).value();
    }

    /**
     * Advances the generator.
     *
     * @param resumeValue what to send in
     * @return the step the body reached
     */
    public Object[] next(final Object resumeValue) {
        return advance(new Resume.Next(resumeValue));
    }

    /**
     * Asks the generator to finish, running its finally blocks.
     *
     * @param value the value to return
     * @return the step the body reached
     */
    public Object[] doReturn(final Object value) {
        if (thread == null || done) {
            done = true;
            return new Object[] { value, Boolean.TRUE };
        }
        return advance(new Resume.Return(value));
    }

    /**
     * Throws into the generator at its suspension point.
     *
     * @param error the value to throw
     * @return the step the body reached
     */
    public Object[] doThrow(final Object error) {
        if (thread == null || done) {
            done = true;
            throw ECMAException.create(error, null, -1, -1);
        }
        return advance(new Resume.Throw(error));
    }

    /** Runs the body until it yields, returns or throws. Result is {value, done}. */
    private Object[] advance(final Resume resume) {
        if (executing) {
            // ES2015 25.3.3.2 step 5: a body that asks its own generator for the
            // next value would wait for itself, so it is told no instead
            throw ECMAErrors.typeError("generator.already.running");
        }
        if (done) {
            return new Object[] { ScriptRuntime.UNDEFINED, Boolean.TRUE };
        }

        executing = true;
        final Step step;
        try {
            if (thread == null) {
                start();
            } else {
                put(toBody, resume);
            }
            step = take(toCaller);
        } finally {
            executing = false;
        }

        if (step instanceof Step.Yielded yielded) {
            return new Object[] { yielded.value(), Boolean.FALSE };
        }
        if (step instanceof Step.Delegated delegated) {
            // 14.4.14 yields the result object the inner iterator made, rather
            // than taking it apart and building another
            return new Object[] { delegated.result(), Boolean.FALSE, Boolean.TRUE };
        }
        done = true;
        if (step instanceof Step.Failed failed) {
            throw failed.error();
        }
        return new Object[] { ((Step.Returned)step).value(), Boolean.TRUE };
    }

    private void start() {
        thread = Thread.ofVirtual().name("nashorn-generator").unstarted(() -> {
            ENTERING.set(this);
            RUNNING.set(this);
            // Context.getGlobal is scoped per thread, so the body's realm has to
            // be established on this thread before anything script-visible runs -
            // scoped values are not inherited by an unstructured thread start.
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

    /**
     * Unwinds a generator nobody can advance any more.
     *
     * A suspended body owns a parked thread, and that thread keeps itself alive,
     * so an abandoned generator would leak one for the life of the program. When
     * the generator object becomes unreachable the body is asked to return,
     * which lets it unwind and its thread exit.
     *
     * The observable consequence is that a finally block in an abandoned
     * generator runs at collection time rather than never - which the spec does
     * not describe, but which no program can distinguish from never having run,
     * since nothing holds a reference to observe it with.
     */
    public void abandon() {
        if (thread == null || done) {
            return;
        }
        done = true;
        abandoned = true;
        // Clear whatever the body handed back last and nobody collected, so that
        // its unwinding has somewhere to put its final step, and offer rather
        // than put in case the body is not waiting at all.
        toCaller.clear();
        toBody.offer(new Resume.Return(ScriptRuntime.UNDEFINED));
    }

    /** Hands a step back, without ever blocking once the generator is abandoned. */
    private void deliver(final Step step) {
        if (abandoned) {
            toCaller.offer(step);
            return;
        }
        put(toCaller, step);
    }

    private static <T> void put(final BlockingQueue<T> queue, final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("generator handoff interrupted", e);
        }
    }

    private static <T> T take(final BlockingQueue<T> queue) {
        try {
            return queue.take();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("generator handoff interrupted", e);
        }
    }
}
