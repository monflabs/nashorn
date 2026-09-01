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

package org.monflabs.nashorn.api.scripting;

import java.util.Objects;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.JobQueue;

/**
 * The event loop of a realm, for Java code that gives scripts asynchronous
 * behaviour: timers, and operations that complete on other threads.
 *
 * <p>An embedded engine runs its loop when the JavaScript stack empties - as
 * the outermost {@code eval} or function call from Java unwinds. It first runs
 * the microtasks (promise reactions, {@link #queueMicrotask}), then, for as
 * long as a timer is waiting, a task is posted or an operation is
 * {@linkplain #pending() pending}, waits for the next of them, runs it, and
 * runs the microtasks it produced. So a script that scheduled a timer or
 * started a request keeps its {@code eval} busy until it is idle; one that did
 * neither returns as soon as its synchronous code is done, as it always did.
 * A loop that never goes idle - an interval nobody clears - is ended by
 * interrupting the script's thread, which is what the playground's Stop and
 * the debugger's {@code terminate} do.
 *
 * <p>Everything but {@link Pending#complete} must be called on the thread
 * running the realm's script - from where a script called you, typically -
 * and the tasks run there too, with the realm bound, so a task may call script
 * functions freely.
 *
 * @since 2017.0.0
 */
public final class EventLoop {
    private final JobQueue queue;

    private EventLoop(final JobQueue queue) {
        this.queue = queue;
    }

    /**
     * The event loop of the realm bound on this thread.
     *
     * @return the loop
     * @throws IllegalStateException with no realm bound - call from where a script called you
     */
    public static EventLoop current() {
        final Global global = Context.getGlobal();
        if (global == null) {
            throw new IllegalStateException("no script realm is bound on this thread: call this from where a script called you");
        }
        return new EventLoop(global.getJobQueue());
    }

    /**
     * Queues a microtask: it runs once the current script code has finished,
     * before any timer or posted task, in order with the promise reactions.
     *
     * @param job the job, run on the loop's thread with the realm bound
     */
    public void queueMicrotask(final Runnable job) {
        queue.enqueue(Objects.requireNonNull(job, "job"));
    }

    /**
     * Schedules a task to run once the delay has passed and the script is
     * between turns - a {@code setTimeout}.
     *
     * @param task the task, run on the loop's thread with the realm bound
     * @param delayMillis the delay; at least 0
     * @return the timer, to cancel with
     */
    public Timer schedule(final Runnable task, final long delayMillis) {
        return new Timer(queue, queue.schedule(Objects.requireNonNull(task, "task"), delayMillis));
    }

    /**
     * Registers an operation in flight - a request sent, a computation handed
     * to another thread - so that the loop waits for it instead of declaring
     * the script idle. Complete it, from any thread, with what should run on
     * the loop's thread once it has a result; or cancel it.
     *
     * @return the pending operation
     */
    public Pending pending() {
        queue.begin();
        return new Pending(queue);
    }

    /**
     * Runs a task on the loop's thread at the next opportunity, from any thread,
     * without holding the loop open for it: if the script is already idle when
     * this is called, the task runs at the next turn of the loop, whenever
     * that is. For a result the script is waiting for, use {@link #pending()}.
     *
     * @param task the task
     */
    public void post(final Runnable task) {
        queue.post(Objects.requireNonNull(task, "task"), false);
    }

    /** A scheduled task, to cancel. */
    public static final class Timer {
        private final JobQueue queue;
        private final Object handle;

        Timer(final JobQueue queue, final Object handle) {
            this.queue = queue;
            this.handle = handle;
        }

        /** Cancels the task; nothing happens if it ran already. Loop thread only. */
        public void cancel() {
            queue.cancel(handle);
        }
    }

    /** An operation the loop waits for; completes once. */
    public static final class Pending {
        private final JobQueue queue;
        private boolean done;

        Pending(final JobQueue queue) {
            this.queue = queue;
        }

        /**
         * Completes the operation: the continuation runs on the loop's thread
         * with the realm bound, and the loop no longer waits for this. Safe
         * from any thread; a second completion is ignored.
         *
         * @param continuation what to run with the result - settle a promise, call a callback
         */
        public synchronized void complete(final Runnable continuation) {
            if (done) {
                return;
            }
            done = true;
            queue.post(Objects.requireNonNull(continuation, "continuation"), true);
        }

        /** Ends the operation with nothing to run. Safe from any thread; ignored after a completion. */
        public synchronized void cancel() {
            if (done) {
                return;
            }
            done = true;
            queue.discard();
        }
    }
}
