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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.ScriptEngine;
import org.monflabs.js.debugger.ui.model.DebugSession;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.debugger.inprocess.InProcessCdpServer;
import org.testng.annotations.Test;

/**
 * {@link DebuggerPanelTest}'s own scenarios, proving the whole stack - the
 * engine's debugger through {@link InProcessCdpServer}, the connection, the
 * session and the panel - works headlessly, in one JVM, with no socket bound
 * anywhere. The last test is exactly the sequence the playground's Debug
 * button uses.
 */
@SuppressWarnings({"deprecation", "javadoc"})
public class InProcessDebuggerPanelTest extends DebuggerPanelTest {

    @Override
    protected void openServer() {
        server = InProcessCdpServer.open(Debugger.of(engine), InspectOptions.parse("", false));
    }

    @Override
    protected void attachPanel() throws Exception {
        onEdt(() -> panel.attach(((InProcessCdpServer.Handle)server).clientChannel()));
    }

    /**
     * The playground's Debug flow exactly: ask for a pause at the very first
     * statement, attach, and start the script only once the panel is
     * connected. {@code Debugger.enable} does not replay an already-running
     * pause, so a pause requested before any client exists must still reach
     * the client that attaches afterwards.
     */
    @Test
    public void pauseOnStartBeforeAttachIsDeliveredOnceConnected() throws Exception {
        final String url = "file:///work/onstart.js";
        final CompletableFuture<Object> done = new CompletableFuture<>();
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        Debugger.of(engine).pauseOnStart();
        onEdt(() -> panel.onConnectionChange((state, detail) -> {
            connState.set(state);
            if (state == DebuggerPanel.ConnectionState.CONNECTED && started.compareAndSet(false, true)) {
                engine.put(ScriptEngine.FILENAME, "onstart.js");
                worker.submit(() -> {
                    try {
                        done.complete(engine.eval("var a = 1;\na = a + 1;\na;\n//# sourceURL=" + url + "\n"));
                    } catch (final Throwable t) {
                        done.completeExceptionally(t);
                    }
                });
            }
        }));
        attachPanel();

        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        assertEquals(onEdtGet(() -> panel.session().pauseState().frames().get(0).line()), Integer.valueOf(0));

        onEdt(() -> panel.session().resume());
        done.get(TIMEOUT, TimeUnit.SECONDS);
    }
}
