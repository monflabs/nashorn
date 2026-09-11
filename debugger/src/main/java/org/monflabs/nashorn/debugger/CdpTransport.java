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

package org.monflabs.nashorn.debugger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The server side of one CDP connection, from
 * the CDP session's own point of view:
 * read and deliver inbound messages until the peer goes away, send outbound
 * ones, close. Two implementations exist -
 * {@code WebSocketConnection} (a real socket) and an in-process one (no socket at all, for when the engine and a
 * debugger client run in the same JVM) - {@code CdpSession} itself has no
 * idea which one it is talking to.
 *
 * @since 2026.1.0
 */
public interface CdpTransport {

    /**
     * Delivers each inbound text message to {@code onMessage} until the peer
     * disconnects. Blocks the calling thread for the connection's whole
     * lifetime - callers run this on a dedicated thread.
     *
     * @param onMessage where each inbound message goes
     * @throws IOException if the connection breaks
     */
    void run(Consumer<String> onMessage) throws IOException;

    /**
     * Sends one complete text message. Safe to call from any thread.
     *
     * @param text the message
     * @throws IOException if it cannot be sent
     */
    void send(String text) throws IOException;

    /**
     * Closes the connection. Idempotent.
     *
     * @param code a close status code
     * @param reason a short reason
     */
    void close(int code, String reason);
}
