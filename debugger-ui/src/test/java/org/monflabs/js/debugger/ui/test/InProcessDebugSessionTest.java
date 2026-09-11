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

import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.debugger.inprocess.InProcessCdpServer;

/**
 * {@link DebugSessionTest}'s own scenarios - attach, breakpoints, pauses,
 * frames, scopes, evaluation, stepping, the staleness guard, reattach, the
 * console and watches - against {@link InProcessCdpServer} instead of a real
 * socket. The same {@link org.monflabs.js.debugger.ui.model.DebugSession}
 * drives both; only the attach differs.
 *
 * <p>An in-process channel is one-shot: once it closes there is nothing to
 * redial. So rather than opening one server up front and reattaching to it,
 * each attach opens a fresh session on the very same engine and debugger,
 * which is exactly what the playground's Debug button does. The reattach
 * scenarios still prove what they were written to prove - a client-owned
 * breakpoint survives a detach and is re-armed on the next attach - just not
 * against one literal server instance.
 */
@SuppressWarnings({"javadoc", "deprecation"})
public class InProcessDebugSessionTest extends DebugSessionTest {

    @Override
    protected void openServer() {
        // nothing up front: the channel is opened per attach, below
    }

    @Override
    protected void attachSession() {
        final AutoCloseable previous = server;
        final InProcessCdpServer.Handle handle =
                InProcessCdpServer.open(Debugger.of(engine), InspectOptions.parse("", false));
        server = handle;
        if (previous != null) {
            // only now: closing the old session ends its CDP conversation, and
            // a conversation that still holds a pause resumes it on the way out
            try {
                previous.close();
            } catch (final Exception ignored) {
                // it is going away either way
            }
        }
        session.attach(handle.clientChannel());
    }
}
