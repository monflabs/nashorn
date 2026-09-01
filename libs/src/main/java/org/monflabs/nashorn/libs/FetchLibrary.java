/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.monflabs.nashorn.libs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.EventLoop;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptUtils;

/**
 * The {@code fetch} library: WHATWG {@code fetch(input, init)} returning a
 * promise of a {@code Response}, with {@code Headers}, {@code Request} and
 * {@code Response}.
 *
 * <p>The classes and {@code fetch} itself are script, in {@code fetch.js};
 * this class is the transport beneath them - one Java function that sends a
 * request through {@code java.net.http.HttpClient} and, on the realm's
 * {@link EventLoop}, hands the response back to the script - so the promise
 * settles on the script's thread and an {@code eval} that started a request
 * returns once it has completed. The promise rejects with a {@code TypeError}
 * on a network or URL failure; an HTTP error status resolves, with
 * {@code response.ok} false, as the specification says.
 *
 * @since 2017.0.0
 */
public final class FetchLibrary implements ScriptLibrary {

    /** The name the transport takes in the global; for the script's use, not a public API. */
    static final String TRANSPORT = "__nashornFetch";

    /** One client per JVM: it is thread safe and pools connections. */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return "fetch";
    }

    @Override
    public Map<String, Object> globals() {
        return Map.of(TRANSPORT, new Transport());
    }

    @Override
    public List<Script> scripts() {
        return List.of(Script.ofResource(FetchLibrary.class, "fetch.js"));
    }

    /**
     * {@code __nashornFetch(url, method, headers, body, onResponse, onError)}:
     * headers as an array of {@code [name, value]} pairs, body a string or
     * null; {@code onResponse(status, statusText, url, headers, text, bytes)}
     * and {@code onError(message)} are called on the script's thread.
     */
    private static final class Transport extends AbstractJSObject {
        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            final JSObject onResponse = (JSObject)args[4];
            final JSObject onError = (JSObject)args[5];
            final HttpRequest request;
            try {
                request = build(args);
            } catch (final RuntimeException e) {
                onError.call(ScriptUtils.undefined(), message(e));
                return ScriptUtils.undefined();
            }
            final EventLoop.Pending pending = EventLoop.current().pending();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, failure) -> {
                if (failure != null) {
                    pending.complete(() -> onError.call(ScriptUtils.undefined(), message(failure)));
                } else {
                    final List<String[]> headers = new ArrayList<>();
                    // an HTTP/2 response carries its pseudo-headers (:status) in the map; they are not headers
                    response.headers().map().forEach((name, values) -> {
                        if (!name.startsWith(":")) {
                            values.forEach(value -> headers.add(new String[] { name, value }));
                        }
                    });
                    final byte[] bytes = response.body();
                    final String text = new String(bytes, charsetOf(response.headers().firstValue("content-type").orElse("")));
                    pending.complete(() -> onResponse.call(ScriptUtils.undefined(), response.statusCode(), reason(response.statusCode()),
                            response.uri().toString(), headers, text, bytes));
                }
            });
            return ScriptUtils.undefined();
        }

        private static HttpRequest build(final Object[] args) {
            final String url = ScriptUtils.toString(args[0]);
            final String method = ScriptUtils.toString(args[1]).toUpperCase(Locale.ROOT);
            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
            final JSObject headers = (JSObject)args[2];
            final int count = ScriptUtils.toInt32(headers.getMember("length"));
            for (int i = 0; i < count; i++) {
                final JSObject pair = (JSObject)headers.getSlot(i);
                try {
                    builder.header(ScriptUtils.toString(pair.getSlot(0)), ScriptUtils.toString(pair.getSlot(1)));
                } catch (final IllegalArgumentException restricted) {
                    // a header the client sets itself: content-length, host, connection...
                }
            }
            final Object body = args[3];
            final boolean withBody = !ScriptUtils.isNullOrUndefined(body) && !method.equals("GET") && !method.equals("HEAD");
            builder.method(method, withBody ? HttpRequest.BodyPublishers.ofString(ScriptUtils.toString(body), StandardCharsets.UTF_8) : HttpRequest.BodyPublishers.noBody());
            return builder.build();
        }

        private static Charset charsetOf(final String contentType) {
            final int at = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (at < 0) {
                return StandardCharsets.UTF_8;
            }
            String name = contentType.substring(at + "charset=".length()).trim();
            final int end = name.indexOf(';');
            if (end >= 0) {
                name = name.substring(0, end).trim();
            }
            try {
                return Charset.forName(name.replace("\"", ""));
            } catch (final RuntimeException unknown) {
                return StandardCharsets.UTF_8;
            }
        }

        private static String message(final Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        }

        private static String reason(final int status) {
            switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "";
            }
        }
    }
}
