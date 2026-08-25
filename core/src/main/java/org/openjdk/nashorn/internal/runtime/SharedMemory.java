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

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The wait queues behind {@code Atomics.wait} and {@code Atomics.notify}
 * (ECMAScript 2017 24.4.11 and 24.4.12).
 *
 * Every other atomic operation is a single access and needs nothing kept
 * anywhere; these two are a rendezvous between agents, so there has to be
 * somewhere for a waiter to be found. A queue belongs to an address - a piece of
 * shared storage and an offset into it - and is created when somebody first
 * waits there and forgotten when the last waiter leaves, so an untouched address
 * costs nothing.
 *
 * An address is the buffer object itself and a byte offset into it. It has to be
 * the buffer rather than the window a view has on it: two views over one
 * SharedArrayBuffer are different ByteBuffer objects over the same memory, and
 * asking one of those for an identity gives something that changes as the bytes
 * do.
 */
public final class SharedMemory {
    /** One address's waiters, and the monitor they wait on. */
    private static final Map<Address, Object> QUEUES = new HashMap<>();

    /**
     * One address: a piece of storage and a byte offset into it.
     *
     * The storage is compared by reference and nothing else. Every realm that
     * shares a buffer wraps it in an object of its own, and a ByteBuffer
     * compares by content - so both of the obvious identities either split one
     * address into several or move as the bytes change.
     */
    private static final class Address {
        private final Object storage;
        private final int offset;

        Address(final Object storage, final int offset) {
            this.storage = storage;
            this.offset = offset;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Address address && address.storage == storage && address.offset == offset;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(storage) * 31 + offset;
        }
    }

    private SharedMemory() {
    }

    /**
     * Waits for the element to be notified, or for the value to have changed, or
     * for the time to run out.
     *
     * @param storage the buffer the address belongs to
     * @param offset  the byte offset into it
     * @param expected what the element must still hold for the wait to happen
     * @param millis   how long to wait, or infinity
     * @param current  reads the element, for the check the specification makes
     *                 while holding the queue
     * @return "not-equal", "timed-out" or "ok"
     */
    public static String wait(final Object storage, final int offset, final int expected, final double millis,
            final IntSupplier current) {
        final Address address = new Address(storage, offset);
        final Object monitor = queueFor(address);

        synchronized (monitor) {
            // the value is read under the queue, so a notify that has already
            // happened cannot be missed between the read and the wait
            if (current.getAsInt() != expected) {
                release(address);
                return "not-equal";
            }
            final long deadline = millis == Double.POSITIVE_INFINITY ? Long.MAX_VALUE
                    : System.currentTimeMillis() + (long)Math.min(millis, Long.MAX_VALUE);
            try {
                final long remaining = deadline == Long.MAX_VALUE ? 0
                        : deadline - System.currentTimeMillis();
                if (deadline != Long.MAX_VALUE && remaining <= 0) {
                    return "timed-out";
                }
                monitor.wait(remaining);
                // waking at or past the deadline is a timeout; waking before it
                // is a notify, or a spurious wake, and answering "ok" to one of
                // those is allowed
                return deadline != Long.MAX_VALUE && System.currentTimeMillis() >= deadline
                        ? "timed-out" : "ok";
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return "timed-out";
            } finally {
                release(address);
            }
        }
    }

    /**
     * Wakes waiters at an address.
     *
     * @param storage the buffer the address belongs to
     * @param offset  the byte offset into it
     * @param count  how many to wake, or infinity for all of them
     * @return how many were woken
     */
    public static int notify(final Object storage, final int offset, final double count) {
        final Address address = new Address(storage, offset);
        final Object monitor;
        final int waiting;
        synchronized (QUEUES) {
            monitor = QUEUES.get(address);
            waiting = monitor == null ? 0 : COUNTS.getOrDefault(address, 0);
        }
        if (monitor == null || waiting == 0) {
            return 0;
        }
        final int woken = (int)Math.min(waiting, count);
        synchronized (monitor) {
            if (woken >= waiting) {
                monitor.notifyAll();
            } else {
                for (int i = 0; i < woken; i++) {
                    monitor.notify();
                }
            }
        }
        return woken;
    }

    private static final Map<Address, Integer> COUNTS = new HashMap<>();

    private static Object queueFor(final Address address) {
        synchronized (QUEUES) {
            COUNTS.merge(address, 1, Integer::sum);
            return QUEUES.computeIfAbsent(address, key -> new Object());
        }
    }

    private static void release(final Address address) {
        synchronized (QUEUES) {
            final int left = COUNTS.merge(address, -1, Integer::sum);
            if (left <= 0) {
                COUNTS.remove(address);
                QUEUES.remove(address);
            }
        }
    }

}
