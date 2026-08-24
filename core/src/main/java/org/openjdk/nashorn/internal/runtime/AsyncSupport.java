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

package org.openjdk.nashorn.internal.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.objects.NativePromise;

/**
 * Runs an async function body on its own virtual thread, so that {@code await}
 * can be an ordinary blocking call.
 *
 * This is the generator machinery with a different driver. A generator is
 * advanced by whoever holds it calling next; an async function is advanced by a
 * promise reaction, so what a suspension reports is the value being awaited and
 * what resumes it is a job the queue runs later. The body itself is an ordinary
 * compiled function either way, which is what makes try/finally, labelled
 * breaks and deoptimisation work across a suspension with no special handling.
 *
 * The call runs the body as far as its first await before returning, which is
 * what the specification means by an async function starting synchronously.
 */
public final class AsyncSupport {
    /**
     * Set on the body's own thread and consumed by the first thing it does, so
     * that exactly one call falls through into the body. It cannot stay set for
     * the thread's lifetime: an async function that calls another one must get
     * a promise back rather than running that one's body inline.
     */
    private static final ThreadLocal<AsyncSupport> ENTERING = new ThreadLocal<>();

    /** The async function whose body this thread is running, for await to find. */
    private static final ThreadLocal<AsyncSupport> RUNNING = new ThreadLocal<>();

    /** What the driver sends in when it resumes the body. */
    private sealed interface Resume {
        record Value(Object value) implements Resume { }
        record Error(Object error) implements Resume { }
    }

    /** What the body reports back: a suspension, or the end of it. */
    private sealed interface Step {
        record Awaiting(Object value) implements Step { }
        record Returned(Object value) implements Step { }
        record Failed(RuntimeException error) implements Step { }
    }

    private final BlockingQueue<Resume> toBody = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<Step> toDriver = new ArrayBlockingQueue<>(1);

    private final ScriptFunction body;
    private final Object self;
    private final Object[] args;
    private final Global global;

    private Thread thread;

    /** The promise the call handed back, which the body's completion settles. */
    private NativePromise promise;

    private AsyncSupport(final ScriptFunction body, final Object self, final Object[] args, final Global global) {
        this.body = body;
        this.self = self;
        this.args = args;
        this.global = global;
    }

    /**
     * Whether this thread is entering an async function body, which the prologue
     * asks once and only the body's own call may be told yes.
     *
     * @return true when the caller should fall through into the body
     */
    public static boolean entering() {
        if (ENTERING.get() == null) {
            return false;
        }
        ENTERING.remove();
        return true;
    }

    /** The async function body running on this thread, or null. */
    static AsyncSupport running() {
        return RUNNING.get();
    }

    /**
     * Starts an async function call: runs its body as far as its first await and
     * hands back the promise for what it eventually produces.
     *
     * @param body   the function, which is re-entered on the body's own thread
     * @param self   its receiver
     * @param args   its arguments
     * @param global the realm
     * @return the promise the call evaluates to
     */
    public static Object start(final ScriptFunction body, final Object self, final Object[] args,
            final Global global) {
        final AsyncSupport support = new AsyncSupport(body, self, args, global);
        support.promise = NativePromise.newAsyncPromise(global);
        support.advance(null);
        return support.promise;
    }

    /**
     * ES2017 6.2.3.1 Await: suspends the body until the value settles.
     *
     * @param value what is being awaited
     * @return what it fulfilled with
     */
    public Object await(final Object value) {
        deliver(new Step.Awaiting(value));
        final Resume resume = take(toBody);
        if (resume instanceof Resume.Error failed) {
            throw ECMAException.create(failed.error(), null, -1, -1);
        }
        return ((Resume.Value)resume).value();
    }

    /**
     * Runs the body until it awaits, returns or throws, and acts on whichever it
     * did: a suspension subscribes to what is being awaited, an end settles the
     * promise.
     */
    private void advance(final Resume resume) {
        if (thread == null) {
            start();
        } else {
            put(toBody, resume);
        }

        final Step step = take(toDriver);
        if (step instanceof Step.Awaiting awaiting) {
            NativePromise.await(global, awaiting.value(),
                    value -> advance(new Resume.Value(value)),
                    error -> advance(new Resume.Error(error)));
        } else if (step instanceof Step.Returned returned) {
            NativePromise.resolveAsyncPromise(promise, returned.value());
        } else {
            final RuntimeException error = ((Step.Failed)step).error();
            if (error instanceof ECMAException thrown) {
                NativePromise.rejectAsyncPromise(promise, thrown.getThrown());
            } else {
                throw error;
            }
        }
    }

    private void start() {
        thread = Thread.ofVirtual().name("nashorn-async").unstarted(() -> {
            ENTERING.set(this);
            RUNNING.set(this);
            // Context.getGlobal is a thread local, so the body's realm has to be
            // established on this thread before anything script-visible runs.
            final Global previous = Context.getGlobal();
            Context.setGlobal(global);
            try {
                deliver(new Step.Returned(ScriptRuntime.apply(body, self, args)));
            } catch (final RuntimeException e) {
                deliver(new Step.Failed(e));
            } finally {
                Context.setGlobal(previous);
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
