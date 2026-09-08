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
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A realm's event loop: the microtask queue promise reactions run on, and
 * behind it the macrotasks a host library adds - timers, and work posted from
 * other threads once an asynchronous operation completes.
 *
 * An embedded engine has no event loop of its own, so this one runs when the
 * JavaScript stack empties: entries into script from Java are counted, and the
 * queue drains when that count returns to zero. First every microtask, as
 * before; then, for as long as there is a timer waiting, a task posted, or an
 * operation pending, the loop waits for the next of them, runs it, and drains
 * the microtasks it produced. That is the observable contract for embedders:
 * a promise callback has run by the time eval returns, and so has a timer's -
 * eval returns when the script is idle, not merely when its synchronous code
 * is done. A script with nothing scheduled sees no difference at all.
 *
 * Microtasks and timers belong to the thread running the realm's script; only
 * posting a task is safe from any thread. The loop stops when its thread is
 * interrupted - the host's way to give up on a script that never goes idle.
 */
public final class JobQueue {
    private final Deque<Runnable> jobs = new ArrayDeque<>();

    /**
     * Per-thread state, in one array so the hot exit path takes a single
     * ThreadLocal lookup: {@code [0]} is how deep the thread is inside script
     * called from Java, {@code [1]} is a worker flag.
     *
     * The worker flag is set on a generator/async body's own virtual thread.
     * Such a thread is a coroutine worker driven by the event loop, never the
     * event loop itself: it must not drain the (per-realm, shared) job queue when
     * its script depth returns to zero, or it would run the microtasks of the
     * thread that is parked waiting for it - out of order, off the wrong thread.
     * Only the outermost eval/invoke thread drains; a worker hands its result
     * back and that thread schedules the continuation as an ordinary microtask.
     */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[2]);

    /** Guards against a job scheduling a job forever while already draining. */
    private boolean draining;

    // -- macrotasks ----------------------------------------------------------------

    /** A timer: a task, when it is due, and its place in line among those due at once. */
    private static final class Timed implements Comparable<Timed> {
        final Runnable task;
        final long dueNanos;
        final long sequence;
        volatile boolean cancelled;

        Timed(final Runnable task, final long dueNanos, final long sequence) {
            this.task = task;
            this.dueNanos = dueNanos;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(final Timed other) {
            final int byTime = Long.compare(dueNanos, other.dueNanos);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }

    private final PriorityQueue<Timed> timers = new PriorityQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentLinkedQueue<Runnable> posted = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition arrived = lock.newCondition();

    /**
     * Microtasks handed in from another thread, drained as microtasks rather
     * than macrotasks. A macrotask (a timer, a posted task) runs only once the
     * microtask queue is empty, so a script spinning on a promise chain - the
     * shape the test262 harness's own setTimeout takes when the host has no
     * native one - would starve a macrotask forever. The ES2024
     * {@code Atomics.waitAsync} timeout is a promise job, so it must reach the
     * agent this way, over {@link #scheduleMicrotask}.
     */
    private final ConcurrentLinkedQueue<Runnable> externalMicrotasks = new ConcurrentLinkedQueue<>();

    /** A do-nothing macrotask that bounces control back to the microtask drain. */
    private static final Runnable BOUNCE = () -> { };

    /** One shared daemon thread times every realm's {@link #scheduleMicrotask}. */
    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "nashorn-microtask-timer");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Schedules a job to run once the stack empties.
     *
     * @param job the job
     */
    public void enqueue(final Runnable job) {
        jobs.add(job);
    }

    /**
     * Schedules a task to run on the loop's thread once the delay has passed
     * and the stack is empty. Only the loop's own thread schedules.
     *
     * @param task the task
     * @param delayMillis how long to wait, at least
     * @return a handle to cancel it with
     */
    public Object schedule(final Runnable task, final long delayMillis) {
        final Timed timed = new Timed(task, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, delayMillis)), sequence.getAndIncrement());
        timers.add(timed);
        return timed;
    }

    /**
     * Cancels a scheduled task; nothing happens if it has run already.
     *
     * @param handle what {@link #schedule} returned
     */
    public void cancel(final Object handle) {
        if (handle instanceof Timed timed) {
            timed.cancelled = true;
            timers.remove(timed);
        }
    }

    /**
     * Schedules a task to run as a microtask once the delay has passed. Unlike
     * {@link #schedule}, the task is not a timer the loop polls for at the
     * bottom of the stack: when it is due it is handed in as a microtask, so it
     * runs even while the loop is busy with a promise chain. May be called from
     * any thread.
     *
     * @param task the task
     * @param delayMillis how long to wait, at least
     * @return a handle to cancel it with
     */
    public Object scheduleMicrotask(final Runnable task, final long delayMillis) {
        return TIMER.schedule(() -> enqueueExternalMicrotask(task),
                Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels a task scheduled with {@link #scheduleMicrotask}; nothing happens
     * if it has run already.
     *
     * @param handle what {@link #scheduleMicrotask} returned
     */
    public void cancelMicrotask(final Object handle) {
        if (handle instanceof ScheduledFuture<?> future) {
            future.cancel(false);
        }
    }

    /**
     * Hands a microtask in from another thread, waking the loop if it is idle.
     *
     * @param task the microtask
     */
    public void enqueueExternalMicrotask(final Runnable task) {
        externalMicrotasks.add(task);
        lock.lock();
        try {
            arrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Counts an operation in flight, so that the loop waits for it: it ends
     * with {@link #post} or {@link #discard}.
     */
    public void begin() {
        pending.incrementAndGet();
    }

    /**
     * Posts a task from any thread, to run on the loop's thread once the stack
     * is empty, and ends an operation begun with {@link #begin} if there is one.
     *
     * @param task the task
     * @param endsPending whether this completes an operation counted with {@link #begin}
     */
    public void post(final Runnable task, final boolean endsPending) {
        lock.lock();
        try {
            posted.add(task);
            if (endsPending) {
                pending.decrementAndGet();
            }
            arrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Ends an operation begun with {@link #begin} that has nothing to run. */
    public void discard() {
        lock.lock();
        try {
            pending.decrementAndGet();
            arrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Whether anything at all is waiting to run: a microtask, a timer, a posted task or an operation in flight. */
    public boolean isBusy() {
        return !jobs.isEmpty() || !timers.isEmpty() || !posted.isEmpty()
                || !externalMicrotasks.isEmpty() || pending.get() > 0;
    }

    /** Marks entry into script from Java. */
    public static void enterScript() {
        DEPTH.get()[0]++;
    }

    /**
     * Marks the matching exit and reports whether this thread should now drain
     * the job queue: it was the outermost entry <em>and</em> this is not a
     * coroutine worker thread (see {@link #DEPTH}). One ThreadLocal lookup.
     *
     * @return true if the current thread should drain the queue
     */
    public static boolean exitScriptShouldDrain() {
        final int[] state = DEPTH.get();
        return --state[0] == 0 && state[1] == 0;
    }

    /**
     * Marks the current thread as a generator/async coroutine worker, which must
     * never drain the job queue (see {@link #DEPTH}). Called once when a body's
     * virtual thread starts.
     */
    public static void markWorkerThread() {
        DEPTH.get()[1] = 1;
    }

    /**
     * Runs everything queued, including whatever those jobs queue in turn:
     * the microtasks, then the macrotasks as they come due, each followed by
     * the microtasks it produced, until nothing is left and nothing is
     * pending.
     */
    public void drain() {
        if (draining) {
            return;
        }
        draining = true;
        try {
            for (;;) {
                if (!drainMicrotasks()) {
                    return;
                }
                final Runnable next = nextMacrotask();
                if (next == null) {
                    return;
                }
                next.run();
            }
        } finally {
            draining = false;
        }
    }

    /** Runs the microtasks; false if the thread was interrupted and the loop gave up. */
    private boolean drainMicrotasks() {
        for (;;) {
            // microtasks handed in from another thread (a waitAsync timeout)
            // join the queue, ahead of the macrotasks so a promise-chain spin
            // cannot outrun them
            Runnable external;
            while ((external = externalMicrotasks.poll()) != null) {
                jobs.add(external);
            }
            final Runnable job = jobs.poll();
            if (job == null) {
                return true;
            }
            // A promise chain can schedule work forever - the specification
            // allows it, and a browser would spin too - but the host has to
            // be able to give up on it. Without this a runner that abandons a
            // wedged evaluation leaves the thread spinning here, allocating,
            // for the life of the process.
            if (Thread.currentThread().isInterrupted()) {
                abandon();
                return false;
            }
            job.run();
        }
    }

    /**
     * The next macrotask to run, waiting for it if one is due later or an
     * operation is in flight; null when the loop is idle or was interrupted.
     */
    private Runnable nextMacrotask() {
        for (;;) {
            if (Thread.currentThread().isInterrupted()) {
                abandon();
                return null;
            }
            // a microtask handed in while the loop was idle: bounce back up to
            // the microtask drain to run it
            if (!externalMicrotasks.isEmpty()) {
                return BOUNCE;
            }
            final Runnable ready = posted.poll();
            if (ready != null) {
                return ready;
            }
            final Timed timer = timers.peek();
            final long now = System.nanoTime();
            if (timer != null && timer.dueNanos <= now) {
                timers.poll();
                if (timer.cancelled) {
                    continue;
                }
                return timer.task;
            }
            if (timer == null && pending.get() == 0) {
                return null;
            }
            lock.lock();
            try {
                if (!posted.isEmpty() || !externalMicrotasks.isEmpty()) {
                    continue;
                }
                if (timer != null) {
                    arrived.awaitNanos(timer.dueNanos - now);
                } else {
                    arrived.await();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                abandon();
                return null;
            } finally {
                lock.unlock();
            }
        }
    }

    /** Drops everything: the host has given up on this script. */
    private void abandon() {
        jobs.clear();
        timers.clear();
        posted.clear();
        externalMicrotasks.clear();
        pending.set(0);
    }
}
