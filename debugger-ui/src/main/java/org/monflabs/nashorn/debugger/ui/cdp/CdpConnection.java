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

package org.monflabs.nashorn.debugger.ui.cdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Chrome DevTools Protocol client over the JDK's {@link WebSocket}: it sends
 * requests and completes a future per id, and delivers events and the close to
 * a {@link Listener}. Everything is asynchronous - nothing here blocks - so it
 * can drive a UI without ever stalling the event thread.
 *
 * <p>The listener callbacks arrive on the WebSocket's own reader thread; a UI
 * built on this connection trampolines them onto its own thread. The
 * {@code call} futures likewise complete on a WebSocket thread. Sends are
 * serialised internally, since the JDK WebSocket forbids overlapping
 * {@code sendText} calls.
 */
public final class CdpConnection implements AutoCloseable {

    /** How events and the connection's end reach the client. On a WebSocket thread. */
    public interface Listener {
        /**
         * A protocol event - one with no {@code id}.
         * @param method the event's method, such as {@code "Debugger.paused"}
         * @param params its parameters, never null (an empty map if the event carried none)
         */
        void onEvent(String method, Map<String, Object> params);

        /**
         * The connection closed - cleanly, on error, or because it never opened.
         * Fires exactly once; every pending call has already been failed.
         * @param reason a short description
         */
        void onClosed(String reason);
    }

    private final WebSocket socket;
    private final Listener listener;
    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final StringBuilder partial = new StringBuilder();
    // sends are chained so no two sendText calls overlap, which the JDK forbids
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);
    private volatile boolean closed;

    private CdpConnection(final WebSocket socket, final Listener listener) {
        this.socket = socket;
        this.listener = listener;
    }

    /**
     * Opens a connection.
     * @param wsUrl the {@code ws://host:port/...} url the server published
     * @param listener where events and the close go
     * @return a future for the open connection; it fails with a {@link CdpException}
     *         whose code is {@link CdpException#TRANSPORT_BUSY} when the server already
     *         has a client, or {@link CdpException#TRANSPORT_CLOSED} for any other
     *         connect failure
     */
    public static CompletableFuture<CdpConnection> connect(final String wsUrl, final Listener listener) {
        Objects.requireNonNull(listener, "listener");
        final Holder holder = new Holder();
        final WebSocket.Listener wsListener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(final WebSocket ws, final CharSequence data, final boolean last) {
                if (holder.connection != null) {
                    holder.connection.onText(data, last);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(final WebSocket ws, final int statusCode, final String reason) {
                if (holder.connection != null) {
                    holder.connection.onClosed(reason == null || reason.isEmpty() ? "connection closed" : reason);
                }
                return null;
            }

            @Override
            public void onError(final WebSocket ws, final Throwable error) {
                if (holder.connection != null) {
                    holder.connection.onClosed(String.valueOf(error.getMessage()));
                }
            }
        };
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), wsListener)
                .handle((ws, error) -> {
                    if (error != null) {
                        throw translate(error);
                    }
                    holder.connection = new CdpConnection(ws, listener);
                    return holder.connection;
                });
    }

    private static CdpException translate(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebSocketHandshakeException handshake && handshake.getResponse() != null
                    && handshake.getResponse().statusCode() == 403) {
                return new CdpException(CdpException.TRANSPORT_BUSY, "another client is already attached");
            }
            if (t instanceof CdpException already) {
                return already;
            }
        }
        return new CdpException(CdpException.TRANSPORT_CLOSED, "cannot connect: " + error.getMessage());
    }

    /**
     * Sends a request.
     * @param method the method, such as {@code "Debugger.resume"}
     * @param params its parameters, or null for none
     * @return a future for the {@code result}; it fails with a {@link CdpException}
     *         carrying the server's error, or {@link CdpException#TRANSPORT_CLOSED}
     *         if the connection is gone
     */
    public CompletableFuture<Map<String, Object>> call(final String method, final Map<String, Object> params) {
        final long id = nextId.getAndIncrement();
        final CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        if (closed) {
            result.completeExceptionally(new CdpException(CdpException.TRANSPORT_CLOSED, "connection closed"));
            return result;
        }
        pending.put(id, result);
        final String text = Json.write(Json.object("id", id, "method", method, "params", params == null ? Json.object() : params));
        synchronized (this) {
            sendChain = sendChain.thenCompose(ignored -> socket.sendText(text, true));
            sendChain.exceptionally(error -> {
                final CompletableFuture<Map<String, Object>> waiting = pending.remove(id);
                if (waiting != null) {
                    waiting.completeExceptionally(new CdpException(CdpException.TRANSPORT_CLOSED, "send failed: " + error.getMessage()));
                }
                return null;
            });
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void onText(final CharSequence data, final boolean last) {
        partial.append(data);
        if (!last) {
            return;
        }
        final Map<String, Object> message;
        try {
            message = (Map<String, Object>)Json.parse(partial.toString());
        } catch (final RuntimeException malformed) {
            partial.setLength(0);
            return;
        }
        partial.setLength(0);
        if (message.containsKey("id")) {
            final long id = ((Number)message.get("id")).longValue();
            final CompletableFuture<Map<String, Object>> waiting = pending.remove(id);
            if (waiting == null) {
                return;
            }
            if (message.containsKey("error")) {
                final Map<String, Object> error = (Map<String, Object>)message.get("error");
                waiting.completeExceptionally(new CdpException(((Number)error.get("code")).intValue(), String.valueOf(error.get("message"))));
            } else {
                final Object result = message.get("result");
                waiting.complete(result instanceof Map ? (Map<String, Object>)result : Json.object());
            }
        } else if (message.containsKey("method")) {
            final Object params = message.get("params");
            listener.onEvent(String.valueOf(message.get("method")), params instanceof Map ? (Map<String, Object>)params : Json.object());
        }
    }

    private void onClosed(final String reason) {
        if (closed) {
            return;
        }
        closed = true;
        for (final Long id : pending.keySet()) {
            final CompletableFuture<Map<String, Object>> waiting = pending.remove(id);
            if (waiting != null) {
                waiting.completeExceptionally(new CdpException(CdpException.TRANSPORT_CLOSED, reason));
            }
        }
        listener.onClosed(reason);
    }

    /** Closes the connection; pending calls fail and the listener is told once. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } catch (final RuntimeException alreadyGone) {
            // the server may already be gone
        }
        onClosed("connection closed");
    }

    /** Lets the WebSocket listener reach the connection built after buildAsync completes. */
    private static final class Holder {
        private volatile CdpConnection connection;
    }
}
