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

package org.monflabs.js.debugger.ui.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.debugger.CdpServer;
import org.monflabs.js.debugger.ui.cdp.CdpConnection;
import org.monflabs.js.debugger.ui.cdp.CdpException;
import org.monflabs.js.debugger.ui.cdp.Json;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The transport against a real engine and CDP server: request/response,
 * protocol errors, events, close, and the one-client-at-a-time refusal.
 */
@SuppressWarnings({"javadoc", "unchecked", "deprecation", "try"})
public class CdpConnectionTest {
    protected static final long TIMEOUT = 20;

    protected ScriptEngine engine;
    protected AutoCloseable server;

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        openServer();
    }

    /** Starts the transport under test. Overridden for the in-process one. */
    protected void openServer() throws Exception {
        server = CdpServer.open(Debugger.of(engine), InspectOptions.parse("127.0.0.1:0", false));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
        Debugger.of(engine).close();
    }

    /** A test listener that queues events and remembers the close reason. */
    protected static final class Events implements CdpConnection.Listener {
        final LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        final AtomicReference<String> closed = new AtomicReference<>();

        @Override
        public void onEvent(final String method, final Map<String, Object> params) {
            queue.add(Json.object("method", method, "params", params));
        }

        @Override
        public void onClosed(final String reason) {
            closed.set(reason);
        }

        Map<String, Object> next(final String method) throws InterruptedException {
            while (true) {
                final Map<String, Object> event = queue.poll(TIMEOUT, TimeUnit.SECONDS);
                if (event == null) {
                    throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
                }
                if (method.equals(event.get("method"))) {
                    return (Map<String, Object>)event.get("params");
                }
            }
        }
    }

    /** Opens a client on the transport under test. Overridden for the in-process one. */
    protected CdpConnection connect(final Events events) throws Exception {
        return CdpConnection.connect(((CdpServer.Handle)server).webSocketUrl(), events).get(TIMEOUT, TimeUnit.SECONDS);
    }

    /** Runs a call expected to fail and returns the CdpException it carried. */
    protected static CdpException failureOf(final Callable call) {
        try {
            call.run();
        } catch (final ExecutionException e) {
            return (CdpException)e.getCause();
        } catch (final Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
        throw new AssertionError("expected a failure");
    }

    protected interface Callable {
        void run() throws Exception;
    }

    @Test
    public void callsRoundTripAndErrorsSurface() throws Exception {
        final Events events = new Events();
        try (CdpConnection connection = connect(events)) {
            connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            // Runtime.enable makes the server announce the context
            assertNotNull(events.next("Runtime.executionContextCreated").get("context"));

            // an unknown method is a protocol error, code -32601
            final CdpException cdp = failureOf(() -> connection.call("Nope.doesNotExist", null).get(TIMEOUT, TimeUnit.SECONDS));
            assertEquals(cdp.code(), -32601);
            assertTrue(!cdp.isTransport());
        }
    }

    @Test
    public void eventsArriveOnTheListener() throws Exception {
        final Events events = new Events();
        try (CdpConnection connection = connect(events)) {
            connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            engine.put(ScriptEngine.FILENAME, "probe.js");
            engine.eval("1 + 1;");
            final Map<String, Object> script = events.next("Debugger.scriptParsed");
            assertTrue(String.valueOf(script.get("url")).endsWith("/probe.js"), String.valueOf(script.get("url")));
        }
    }

    @Test
    public void closeFailsPendingAndTellsTheListener() throws Exception {
        final Events events = new Events();
        final CdpConnection connection = connect(events);
        connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
        connection.close();
        assertNotNull(events.closed.get());
        final CdpException cdp = failureOf(() -> connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS));
        assertEquals(cdp.code(), CdpException.TRANSPORT_CLOSED);
    }

    @Test
    public void aSecondClientIsRefusedAsBusy() throws Exception {
        final Events first = new Events();
        try (CdpConnection ignored = connect(first)) {
            final CdpException cdp = failureOf(() -> CdpConnection.connect(
                    ((CdpServer.Handle)server).webSocketUrl(), new Events()).get(TIMEOUT, TimeUnit.SECONDS));
            assertEquals(cdp.code(), CdpException.TRANSPORT_BUSY, "message was: " + cdp.getMessage());
            assertTrue(cdp.isTransport());
        }
    }
}
