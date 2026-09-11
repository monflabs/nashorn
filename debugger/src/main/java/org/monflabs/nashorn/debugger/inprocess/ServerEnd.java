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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.monflabs.nashorn.debugger.CdpClientChannel;
import org.monflabs.nashorn.debugger.CdpTransport;

/**
 * The server ({@code CdpSession}) side of an in-process CDP pipe. Inbound
 * (client-to-server) messages go through a queue, since {@link #run} is a
 * blocking pull loop exactly like {@code WebSocketConnection.run}; outbound
 * ones are delivered by calling the peer's listener directly, synchronously,
 * on the sending thread - safe because the UI's connection and session
 * already document that their callbacks may arrive on any thread and are
 * trampolined onto their own executor.
 */
final class ServerEnd implements CdpTransport {
    private static final Object CLOSE = new Object();

    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed;
    private volatile ClientEnd peer;

    ServerEnd(final AtomicBoolean closed) {
        this.closed = closed;
    }

    void setPeer(final ClientEnd peer) {
        this.peer = peer;
    }

    @Override
    public void run(final Consumer<String> onMessage) throws IOException {
        while (true) {
            final Object item;
            try {
                item = inbox.take();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (item == CLOSE) {
                return;
            }
            onMessage.accept((String) item);
        }
    }

    @Override
    public void send(final String text) throws IOException {
        if (closed.get()) {
            throw new IOException("connection closed");
        }
        final CdpClientChannel.ChannelListener listener = peer.listener();
        if (listener != null) {
            listener.onText(text, true);
        }
    }

    @Override
    public void close(final int code, final String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        inbox.offer(CLOSE);
        final CdpClientChannel.ChannelListener listener = peer.listener();
        if (listener != null) {
            listener.onClosed(reason);
        }
    }

    void deliver(final String text) {
        inbox.offer(text);
    }
}
