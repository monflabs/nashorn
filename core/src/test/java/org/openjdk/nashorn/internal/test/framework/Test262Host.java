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

package org.openjdk.nashorn.internal.test.framework;

import org.openjdk.nashorn.internal.objects.NativeArrayBuffer;

/**
 * The part of test262's {@code $262} host object that a script cannot do for
 * itself.
 *
 * The engine's own packages are exported to this one only for the test run, and
 * a script reaching them through {@code Java.type} is subject to the access
 * rules of wherever it happens to be running. This class is on the class path
 * with the rest of the framework, so it is reachable from anywhere, and it is
 * the only thing the host object needs to name.
 */
public final class Test262Host {
    /**
     * The agents this one started, in the order they were started.
     *
     * An agent is a thread with a realm of its own and no way to reach this
     * one's objects: everything that passes between them is either a string or
     * a piece of shared storage, which is what makes the atomic operations the
     * only thing they have in common.
     */
    private static final java.util.List<Agent> AGENTS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** Reports the agents have sent back, oldest first. */
    private static final java.util.concurrent.BlockingQueue<String> REPORTS =
            new java.util.concurrent.LinkedBlockingQueue<>();

    /** What this agent is waiting for a broadcast on, when it is an agent. */
    private static final ThreadLocal<java.util.concurrent.SynchronousQueue<java.nio.ByteBuffer>> INBOX =
            new ThreadLocal<>();

    private record Agent(Thread thread, java.util.concurrent.SynchronousQueue<java.nio.ByteBuffer> inbox) { }

    private Test262Host() {
    }

    /**
     * $262.agent.start: runs a source text as an agent of its own.
     *
     * @param source what the agent runs
     */
    public static void agentStart(final String source) {
        final java.util.concurrent.SynchronousQueue<java.nio.ByteBuffer> inbox =
                new java.util.concurrent.SynchronousQueue<>();
        final Thread thread = new Thread(() -> {
            INBOX.set(inbox);
            try {
                final javax.script.ScriptEngine engine =
                        new org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory()
                                .getScriptEngine("--language=es6");
                engine.eval(AGENT_HOST_OBJECT);
                engine.eval(source);
            } catch (final Exception e) {
                // an agent that fails leaves no report, and the test times out
                // saying so, which is more use than a stack trace from a thread
                // nobody is watching
                System.err.println("agent failed: " + e);
            }
        }, "test262-agent");
        thread.setDaemon(true);
        AGENTS.add(new Agent(thread, inbox));
        thread.start();
    }

    /**
     * $262.agent.broadcast: hands shared storage to every agent and waits until
     * each of them has taken it.
     *
     * @param buffer the SharedArrayBuffer to hand over
     */
    public static void agentBroadcast(final Object buffer) {
        final java.nio.ByteBuffer storage =
                org.openjdk.nashorn.internal.objects.NativeSharedArrayBuffer.storageOf(buffer);
        if (storage == null) {
            throw new IllegalArgumentException("broadcast needs a SharedArrayBuffer");
        }
        for (final Agent agent : AGENTS.toArray(new Agent[0])) {
            try {
                agent.inbox().put(storage);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * $262.agent.receiveBroadcast, from inside an agent: waits for the storage
     * and wraps it in this agent's own realm.
     *
     * @return a SharedArrayBuffer of this realm over the shared bytes
     */
    public static Object agentReceiveBroadcast() {
        final java.util.concurrent.SynchronousQueue<java.nio.ByteBuffer> inbox = INBOX.get();
        if (inbox == null) {
            throw new IllegalStateException("receiveBroadcast outside an agent");
        }
        try {
            return org.openjdk.nashorn.internal.objects.NativeSharedArrayBuffer.wrap(
                    inbox.take(), org.openjdk.nashorn.internal.runtime.Context.getGlobal());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * $262.agent.report, from inside an agent.
     *
     * @param report what to tell the agent that started this one
     */
    public static void agentReport(final String report) {
        REPORTS.add(report);
    }

    /**
     * $262.agent.getReport: the oldest report not yet collected.
     *
     * @return the report, or null when there is none waiting
     */
    public static String agentGetReport() {
        return REPORTS.poll();
    }

    /**
     * $262.agent.sleep.
     *
     * @param millis how long to sleep for
     */
    public static void agentSleep(final double millis) {
        try {
            Thread.sleep((long)millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * $262.agent.monotonicNow.
     *
     * @return a millisecond reading that only ever goes forwards
     */
    public static double agentMonotonicNow() {
        return System.nanoTime() / 1_000_000.0;
    }

    /** Forgets the agents of the execution that has finished. */
    public static void agentReset() {
        AGENTS.clear();
        REPORTS.clear();
    }

    /** What an agent's own realm gets, which is the host object minus the parts only a parent has. */
    private static final String AGENT_HOST_OBJECT =
            "var $262 = { global: this,"
            + "  evalScript: function (s) { return (0, eval)(s); },"
            + "  gc: function () {},"
            + "  agent: {"
            + "    receiveBroadcast: function (cb) {"
            + "      cb(Java.type('org.openjdk.nashorn.internal.test.framework.Test262Host').agentReceiveBroadcast());"
            + "    },"
            + "    report: function (r) {"
            + "      Java.type('org.openjdk.nashorn.internal.test.framework.Test262Host').agentReport(String(r));"
            + "    },"
            + "    sleep: function (ms) {"
            + "      Java.type('org.openjdk.nashorn.internal.test.framework.Test262Host').agentSleep(ms);"
            + "    },"
            + "    monotonicNow: function () {"
            + "      return Java.type('org.openjdk.nashorn.internal.test.framework.Test262Host').agentMonotonicNow();"
            + "    },"
            + "    leaving: function () {}"
            + "  }"
            + "};";

    /**
     * ES2015 24.1.1.3 DetachArrayBuffer, which the suite uses to check what
     * every operation over a detached buffer does.
     *
     * @param buffer the ArrayBuffer to detach
     */
    public static void detachArrayBuffer(final Object buffer) {
        NativeArrayBuffer.detach(buffer);
    }
}
