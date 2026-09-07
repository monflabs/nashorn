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

package org.monflabs.nashorn.libs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.objects.NativeHeaders;
import org.monflabs.nashorn.internal.objects.NativePromise;
import org.monflabs.nashorn.internal.objects.NativeRequest;
import org.monflabs.nashorn.internal.objects.NativeResponse;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JobQueue;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;

/**
 * The {@code fetch} library: WHATWG {@code fetch(input, init)} returning a
 * promise of a {@code Response}, with {@code Headers}, {@code Request} and
 * {@code Response} as built-in classes - real prototypes, read-only
 * accessors, iteration - installed into a global on request.
 *
 * <p>A request runs on {@code java.net.http.HttpClient}, created on the first
 * fetch of the process and shared, and settles its promise on the script's
 * thread through the realm's event loop - so an {@code eval} that started a
 * request returns once it has completed. The promise rejects with a
 * {@code TypeError} on a network or URL failure; an HTTP error status resolves,
 * with {@code response.ok} false, as the specification says.
 *
 * @since 2017.0.0
 */
public final class FetchLibrary implements ScriptLibrary {

    /** One client per JVM, made on first use: it is thread safe and pools connections. */
    private static final class Client {
        static final HttpClient INSTANCE = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static final MethodHandle FETCH;
    static {
        try {
            FETCH = MethodHandles.lookup().findStatic(FetchLibrary.class, "fetch",
                    MethodType.methodType(Object.class, Object.class, Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public String name() {
        return "fetch";
    }

    @Override
    public void initialize(final JSObject global) {
        Global.instance().installFetchLibrary(ScriptFunction.createBuiltin("fetch", FETCH));
    }

    /**
     * fetch(input, init).
     *
     * @param self self, unused
     * @param input a URL string or a Request
     * @param init method, headers, body
     * @return a promise of a Response
     */
    @SuppressWarnings("unused")
    private static Object fetch(final Object self, final Object input, final Object init) {
        Global.requireEventLoop("fetch");
        final Global global = Global.instance();
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        final NativeRequest request;
        final HttpRequest wire;
        try {
            request = NativeRequest.from(global, input, init);
            wire = build(request);
        } catch (final ECMAException e) {
            NativePromise.rejectAsyncPromise(promise, e.getThrown());
            return promise;
        } catch (final RuntimeException e) {
            NativePromise.rejectAsyncPromise(promise, global.newTypeError("fetch: " + message(e)));
            return promise;
        }
        final JobQueue loop = global.getJobQueue();
        loop.begin();
        Client.INSTANCE.sendAsync(wire, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, failure) -> {
            if (failure != null) {
                loop.post(() -> NativePromise.rejectAsyncPromise(promise, global.newTypeError("fetch: " + message(failure))), true);
                return;
            }
            final byte[] bytes = response.body();
            final String text = new String(bytes, charsetOf(response.headers().firstValue("content-type").orElse("")));
            loop.post(() -> {
                final NativeHeaders headers = (NativeHeaders)NativeHeaders.construct(true, null, null);
                // an HTTP/2 response carries its pseudo-headers (:status) in the map; they are not headers
                response.headers().map().forEach((name, values) -> {
                    if (!name.startsWith(":")) {
                        values.forEach(value -> NativeHeaders.append(headers, name, value));
                    }
                });
                NativePromise.resolveAsyncPromise(promise, NativeResponse.fetched(global, response.statusCode(), reason(response.statusCode()),
                        headers, response.uri().toString(), text, bytes));
            }, true);
        });
        return promise;
    }

    private static HttpRequest build(final NativeRequest request) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()));
        for (final Map.Entry<String, List<String>> header : request.headers().asMap().entrySet()) {
            try {
                builder.header(header.getKey(), String.join(", ", header.getValue()));
            } catch (final IllegalArgumentException restricted) {
                // a header the client sets itself: content-length, host, connection...
            }
        }
        final String method = request.method();
        final boolean withBody = request.body() != null && !method.equals("GET") && !method.equals("HEAD");
        builder.method(method, withBody ? HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8) : HttpRequest.BodyPublishers.noBody());
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
