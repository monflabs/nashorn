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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.monflabs.nashorn.debugger.CdpClientChannel;

/**
 * The client side of an in-process CDP pipe - see {@link ServerEnd}. Sending
 * hands the message straight to the server's inbox queue; there is nothing to
 * queue in the other direction, since the server delivers directly.
 */
final class ClientEnd implements CdpClientChannel {
    private final AtomicBoolean closed;
    private volatile ChannelListener listener;
    private volatile ServerEnd peer;

    ClientEnd(final AtomicBoolean closed) {
        this.closed = closed;
    }

    void setPeer(final ServerEnd peer) {
        this.peer = peer;
    }

    ChannelListener listener() {
        return listener;
    }

    @Override
    public void listen(final ChannelListener listener) {
        this.listener = listener;
    }

    @Override
    public CompletionStage<?> sendText(final String text) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("connection closed"));
        }
        peer.deliver(text);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void requestClose() {
        peer.close(1000, "connection closed");
    }
}
