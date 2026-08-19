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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The microtask queue promise reactions run on.
 *
 * An embedded engine has no event loop, so the queue is drained when the
 * JavaScript stack empties: entries into script from Java are counted, and the
 * queue runs when that count returns to zero. That is the observable contract
 * for embedders - a promise callback has run by the time eval returns, and not
 * before the code that scheduled it has finished.
 *
 * The queue belongs to a realm and is only ever touched by whichever thread is
 * running that realm's script, so it needs no locking.
 */
public final class JobQueue {
    private final Deque<Runnable> jobs = new ArrayDeque<>();

    /** How deep the current thread is inside script called from Java. */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** Guards against a job scheduling a job forever while already draining. */
    private boolean draining;

    /**
     * Schedules a job to run once the stack empties.
     *
     * @param job the job
     */
    public void enqueue(final Runnable job) {
        jobs.add(job);
    }

    /** Marks entry into script from Java. */
    public static void enterScript() {
        DEPTH.get()[0]++;
    }

    /**
     * Marks the matching exit, and reports whether the stack is now empty.
     *
     * @return true if this was the outermost entry
     */
    public static boolean exitScript() {
        return --DEPTH.get()[0] == 0;
    }

    /** Runs everything queued, including whatever those jobs queue in turn. */
    public void drain() {
        if (draining) {
            return;
        }
        draining = true;
        try {
            Runnable job;
            while ((job = jobs.poll()) != null) {
                // A promise chain can schedule work forever - the specification
                // allows it, and a browser would spin too - but the host has to
                // be able to give up on it. Without this a runner that abandons a
                // wedged evaluation leaves the thread spinning here, allocating,
                // for the life of the process.
                if (Thread.currentThread().isInterrupted()) {
                    jobs.clear();
                    return;
                }
                job.run();
            }
        } finally {
            draining = false;
        }
    }
}
