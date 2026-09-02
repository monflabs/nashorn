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

package org.monflabs.nashorn.debugger.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.monflabs.nashorn.debugger.json.Json;

/**
 * A CDP client over the JDK's WebSocket: sends requests, matches responses
 * by id, queues events.
 */
final class CdpClient implements AutoCloseable {
    static final long TIMEOUT = 20;

    private final WebSocket socket;
    private final BlockingQueue<Map<String, Object>> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private long nextId = 1;

    CdpClient(final String url) throws Exception {
        socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
            @Override
            @SuppressWarnings("unchecked")
            public CompletionStage<?> onText(final WebSocket ws, final CharSequence data, final boolean last) {
                partial.append(data);
                if (last) {
                    final Map<String, Object> message = (Map<String, Object>)Json.parse(partial.toString());
                    partial.setLength(0);
                    if (message.containsKey("id")) {
                        responses.add(message);
                    } else {
                        events.add(message);
                    }
                }
                ws.request(1);
                return null;
            }
        }).get(TIMEOUT, TimeUnit.SECONDS);
    }

    /** Sends a request and returns its result, or throws with the error's message. */
    Map<String, Object> call(final String method, final Object... params) throws Exception {
        final long id = nextId++;
        socket.sendText(Json.write(Json.object("id", id, "method", method, "params", Json.object(params))), true).get(TIMEOUT, TimeUnit.SECONDS);
        while (true) {
            final Map<String, Object> response = responses.poll(TIMEOUT, TimeUnit.SECONDS);
            if (response == null) {
                throw new AssertionError("no response to " + method + " within " + TIMEOUT + "s");
            }
            if (((Number)response.get("id")).longValue() != id) {
                continue;
            }
            if (response.containsKey("error")) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> error = (Map<String, Object>)response.get("error");
                throw new CdpFailure(((Number)error.get("code")).intValue(), String.valueOf(error.get("message")));
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> result = (Map<String, Object>)response.get("result");
            return result;
        }
    }

    /** The next event of a given method, skipping others. */
    Map<String, Object> event(final String method) throws Exception {
        while (true) {
            final Map<String, Object> event = events.poll(TIMEOUT, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
            }
            if (method.equals(event.get("method"))) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> params = (Map<String, Object>)event.get("params");
                return params;
            }
        }
    }

    boolean hasEvents() {
        return !events.isEmpty();
    }

    void sendRaw(final String text) throws Exception {
        socket.sendText(text, true).get(TIMEOUT, TimeUnit.SECONDS);
    }

    Map<String, Object> rawResponse() throws Exception {
        return responses.poll(TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            // the server may already be gone
        }
    }

    static final class CdpFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final int code;

        CdpFailure(final int code, final String message) {
            super(message);
            this.code = code;
        }
    }
}
