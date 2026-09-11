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

package org.monflabs.js.debugger.ui;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.awt.Font;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.ScriptEngine;
import javax.swing.SwingUtilities;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.debugger.CdpServer;
import org.monflabs.js.debugger.ui.model.DebugSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The panel headless (no JFrame, as {@code java.awt.headless=true} requires):
 * it composes, attaches to a real server, reflects a paused run, and cleans up.
 */
@SuppressWarnings({"deprecation", "javadoc"})
public class DebuggerPanelTest {
    protected static final long TIMEOUT = 20;

    protected ScriptEngine engine;
    protected AutoCloseable server;
    protected ExecutorService worker;
    protected DebuggerPanel panel;

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        openServer();
        worker = Executors.newSingleThreadExecutor();
        onEdt(() -> panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false));
    }

    /** Starts the transport under test. Overridden for the in-process one. */
    protected void openServer() throws Exception {
        server = CdpServer.open(Debugger.of(engine), InspectOptions.parse("127.0.0.1:0", false));
    }

    /** Points the panel at the transport under test. */
    protected void attachPanel() throws Exception {
        onEdt(() -> panel.attach(((CdpServer.Handle)server).webSocketUrl()));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        onEdt(() -> panel.close());
        server.close();
        worker.shutdownNow();
        Debugger.of(engine).close();
    }

    protected static void onEdt(final Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }

    /** A value read from the EDT. */
    protected static <T> T onEdtGet(final java.util.concurrent.Callable<T> c) throws Exception {
        final AtomicReference<T> ref = new AtomicReference<>();
        final AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ref.set(c.call());
            } catch (final Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) {
            throw err.get();
        }
        return ref.get();
    }

    protected void waitUntil(final EdtCondition condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        while (System.nanoTime() < deadline) {
            if (onEdtGet(condition::holds)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within " + TIMEOUT + "s");
    }

    protected interface EdtCondition {
        boolean holds();
    }

    @Test
    public void thePanelComposes() throws Exception {
        assertNotNull(panel);
        assertTrue(onEdtGet(() -> panel.getComponentCount() > 0));
    }

    @Test
    public void attachRunAndPauseReflectsInTheStack() throws Exception {
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
        attachPanel();
        waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

        // set a breakpoint by url and run a script reporting that url
        final String url = "file:///work/panel.js";
        onEdt(() -> panel.session().toggleBreakpoint(url, 1));
        final CompletableFuture<Object> done = new CompletableFuture<>();
        engine.put(ScriptEngine.FILENAME, "panel.js");
        worker.submit(() -> {
            try {
                done.complete(engine.eval("var a = 1;\na = a + 1;\na;\n//# sourceURL=" + url + "\n"));
            } catch (final Throwable t) {
                done.completeExceptionally(t);
            }
        });

        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        assertEquals(onEdtGet(() -> panel.session().pauseState().frames().get(0).line()), Integer.valueOf(1));

        onEdt(() -> panel.session().resume());
        done.get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void detachWhilePausedReleasesTheScript() throws Exception {
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
        attachPanel();
        waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

        final String url = "file:///work/hang.js";
        onEdt(() -> panel.session().toggleBreakpoint(url, 0));
        final CompletableFuture<Object> done = new CompletableFuture<>();
        engine.put(ScriptEngine.FILENAME, "hang.js");
        worker.submit(() -> {
            try {
                done.complete(engine.eval("var a = 1;\na;\n//# sourceURL=" + url + "\n"));
            } catch (final Throwable t) {
                done.completeExceptionally(t);
            }
        });
        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        onEdt(() -> panel.detach());
        done.get(TIMEOUT, TimeUnit.SECONDS);   // released only if detach resumed
    }
}
