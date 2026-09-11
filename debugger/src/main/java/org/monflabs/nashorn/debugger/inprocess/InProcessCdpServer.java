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

package org.monflabs.nashorn.debugger.inprocess;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.debugger.CdpClientChannel;
import org.monflabs.nashorn.debugger.cdp.CdpSession;

/**
 * A same-JVM, socket-free Chrome DevTools Protocol session: the counterpart to
 * {@link org.monflabs.nashorn.debugger.CdpServer} for when the engine and a
 * debugger client (for instance the Swing debugger panel) run in the very same
 * process, or when no network stack is available at all. Reuses
 * the CDP session's own JSON-RPC dispatch unchanged - only the transport
 * underneath it differs.
 *
 * <pre>
 *   final Debugger debugger = Debugger.of(engine);
 *   final InProcessCdpServer.Handle handle =
 *           InProcessCdpServer.open(debugger, InspectOptions.parse("", false));
 *   // hand handle.clientChannel() to a debugger UI in the same JVM
 * </pre>
 *
 * @since 2026.1.0
 */
public final class InProcessCdpServer {

    /**
     * A running in-process session.
     *
     * @since 2026.1.0
     */
    public static final class Handle implements AutoCloseable {
        private final ServerEnd serverEnd;
        private final ClientEnd clientEnd;

        private Handle(final ServerEnd serverEnd, final ClientEnd clientEnd) {
            this.serverEnd = serverEnd;
            this.clientEnd = clientEnd;
        }

        /**
         * The client-side channel - hand this to the debugger UI's own
         * {@code CdpClientChannel}-accepting {@code attach} or {@code open}.
         *
         * @return the client channel
         */
        public CdpClientChannel clientChannel() {
            return clientEnd;
        }

        @Override
        public void close() {
            serverEnd.close(1001, "server closing");
        }
    }

    private InProcessCdpServer() {
    }

    /**
     * Starts an in-process session for a debugger: no socket, no port, no
     * network stack of any kind - the only way to reach it is through the
     * returned {@link Handle}'s own {@link Handle#clientChannel()}.
     *
     * <p>Unlike {@code CdpServer.open}, {@code options.host()} and
     * {@code options.port()} are meaningless here and ignored.
     * {@code options.waitForDebugger()} is deliberately not supported, and is
     * rejected outright: {@code CdpServer.open} can block the caller until a
     * third party discovers the port and connects, but an in-process pipe has
     * no third party - the only possible client is whoever receives the
     * {@link Handle} this method returns, so blocking the caller before
     * returning it would deadlock forever rather than merely freeze a UI
     * thread until an external client shows up.
     *
     * @param debugger the debugger to expose
     * @param options only {@code waitForDebugger()} is consulted, and must be false
     * @return the running session's handle
     */
    public static Handle open(final Debugger debugger, final InspectOptions options) {
        if (options.waitForDebugger()) {
            throw new IllegalArgumentException(
                    "InProcessCdpServer.open() cannot honor waitForDebugger=true: "
                    + "there is no third party to attach before this call returns the only handle a client could ever use");
        }
        final AtomicBoolean closed = new AtomicBoolean();
        final ServerEnd serverEnd = new ServerEnd(closed);
        final ClientEnd clientEnd = new ClientEnd(closed);
        serverEnd.setPeer(clientEnd);
        clientEnd.setPeer(serverEnd);
        final String id = UUID.randomUUID().toString();
        Thread.ofVirtual().name("nashorn-debugger-inprocess")
                .start(() -> new CdpSession(debugger, serverEnd, id, () -> { }).run());
        return new Handle(serverEnd, clientEnd);
    }
}
