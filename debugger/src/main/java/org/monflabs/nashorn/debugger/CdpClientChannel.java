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

import java.util.concurrent.CompletionStage;

/**
 * The client side of one CDP connection, from a UI's protocol layer own point
 * of view: send outbound text, be told about inbound text and the close. Two
 * implementations exist - one over {@code java.net.http.WebSocket} (a real
 * socket), one in-process (no socket at all) - the protocol layer above has
 * no idea which one it is talking to.
 *
 * <p>Deliberately living in this module rather than in the debugger UI: it is
 * the one shared type an in-process transport (which must live here,
 * alongside {@link CdpTransport} and the session) can hand back to a UI
 * client without this module ever depending on the UI - the dependency runs
 * the other way.
 *
 * @since 2026.1.0
 */
public interface CdpClientChannel {

    /**
     * Registers where inbound frames and the close arrive. Called exactly
     * once, before any {@link #sendText(String)}.
     *
     * @param listener the listener
     */
    void listen(ChannelListener listener);

    /**
     * Sends one complete text message.
     *
     * @param text the message
     * @return a stage completing once the send succeeds or fails
     */
    CompletionStage<?> sendText(String text);

    /**
     * Requests that the channel close. Idempotent; safe even if the peer is
     * already gone.
     */
    void requestClose();

    /**
     * Where inbound frames and the close land. Callbacks may arrive on any
     * thread - the caller of {@link #listen(ChannelListener)} is responsible
     * for any marshaling it needs.
     *
     * @since 2026.1.0
     */
    interface ChannelListener {

        /**
         * One inbound frame, possibly a fragment - {@code last} says whether
         * more is coming for the same logical message.
         *
         * @param data the frame's text
         * @param last whether this completes the message
         */
        void onText(CharSequence data, boolean last);

        /**
         * The channel closed - cleanly, on error, or because it never opened.
         * Fires exactly once.
         *
         * @param reason a short description
         */
        void onClosed(String reason);
    }
}
