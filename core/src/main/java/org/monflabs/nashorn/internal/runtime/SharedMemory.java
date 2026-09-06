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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

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
    /** The queues, by address. */
    private static final Map<Address, Queue> QUEUES = new HashMap<>();

    /**
     * One address's waiters, and the monitor they wait on.
     *
     * The waiters are a list rather than a count because 24.4.12 takes the ones
     * it wakes off the queue as it wakes them, and it holds the monitor while it
     * does: a waiter cannot remove itself, because it is not running yet, and a
     * second notify that could still see it would count it twice.
     */
    private static final class Queue {
        private final Object monitor = new Object();
        private final Deque<Waiter> waiters = new ArrayDeque<>();
        /** How many agents are in a wait here, so an idle address can be dropped. */
        private int users;
    }

    /** One agent waiting at one address. */
    private static final class Waiter {
        private boolean notified;
    }

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
        return wait(storage, offset, millis, () -> current.getAsInt() == expected);
    }

    /**
     * The 64-bit form, for a wait on a {@code BigInt64Array} element (ES2020).
     *
     * @param storage the buffer the address belongs to
     * @param offset  the byte offset into it
     * @param expected what the element must still hold for the wait to happen
     * @param millis   how long to wait, or infinity
     * @param current  reads the 64-bit element
     * @return "not-equal", "timed-out" or "ok"
     */
    public static String wait(final Object storage, final int offset, final long expected, final double millis,
            final LongSupplier current) {
        return wait(storage, offset, millis, () -> current.getAsLong() == expected);
    }

    private static String wait(final Object storage, final int offset, final double millis,
            final BooleanSupplier stillExpected) {
        final Address address = new Address(storage, offset);
        final Queue queue = acquire(address);
        final Waiter waiter = new Waiter();

        try {
            synchronized (queue.monitor) {
                // the value is read under the queue, so a notify that has already
                // happened cannot be missed between the read and the wait
                if (!stillExpected.getAsBoolean()) {
                    return "not-equal";
                }
                final long deadline = millis == Double.POSITIVE_INFINITY ? Long.MAX_VALUE
                        : System.currentTimeMillis() + (long)Math.min(millis, Long.MAX_VALUE);
                queue.waiters.add(waiter);
                try {
                    while (!waiter.notified) {
                        if (deadline == Long.MAX_VALUE) {
                            queue.monitor.wait();
                            continue;
                        }
                        final long remaining = deadline - System.currentTimeMillis();
                        if (remaining <= 0) {
                            return "timed-out";
                        }
                        queue.monitor.wait(remaining);
                    }
                    return "ok";
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "timed-out";
                } finally {
                    queue.waiters.remove(waiter);
                }
            }
        } finally {
            release(address);
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
        final Queue queue;
        synchronized (QUEUES) {
            queue = QUEUES.get(address);
        }
        if (queue == null) {
            return 0;
        }
        synchronized (queue.monitor) {
            int woken = 0;
            while (woken < count && !queue.waiters.isEmpty()) {
                queue.waiters.remove().notified = true;
                woken++;
            }
            if (woken > 0) {
                queue.monitor.notifyAll();
            }
            return woken;
        }
    }

    private static Queue acquire(final Address address) {
        synchronized (QUEUES) {
            final Queue queue = QUEUES.computeIfAbsent(address, key -> new Queue());
            queue.users++;
            return queue;
        }
    }

    private static void release(final Address address) {
        synchronized (QUEUES) {
            final Queue queue = QUEUES.get(address);
            if (queue != null && --queue.users <= 0) {
                QUEUES.remove(address);
            }
        }
    }
}
