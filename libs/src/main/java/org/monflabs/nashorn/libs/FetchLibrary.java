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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.EventLoop;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptUtils;

/**
 * The {@code fetch} library: WHATWG {@code fetch(input, init)} returning a
 * promise of a {@code Response}, with {@code Headers}, {@code Request} and
 * {@code Response} - all of it Java, as {@link JSObject}s.
 *
 * <p>The library installs itself from {@link #initialize(JSObject)} rather
 * than through {@link #globals()}, because its objects make promises with the
 * realm's own {@code Promise} constructor and arrays with its {@code Array},
 * which only the global can hand over. A request runs on
 * {@code java.net.http.HttpClient} and, through the realm's
 * {@link EventLoop}, settles its promise on the script's thread - so an
 * {@code eval} that started a request returns once it has completed. The
 * promise rejects with a {@code TypeError} on a network or URL failure; an
 * HTTP error status resolves, with {@code response.ok} false, as the
 * specification says.
 *
 * <p>One class: a constructor object per class, a method object per entry of
 * each class's enum, a switch in each.
 *
 * @since 2017.0.0
 */
public final class FetchLibrary implements ScriptLibrary {

    /** One client per JVM: it is thread safe and pools connections. */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");
    private static final Pattern METHOD = Pattern.compile("[A-Z]+");

    @Override
    public String name() {
        return "fetch";
    }

    @Override
    public void initialize(final JSObject global) {
        final Realm realm = new Realm(global);
        global.setMember("Headers", new Constructor(Kind.Headers, realm));
        global.setMember("Request", new Constructor(Kind.Request, realm));
        global.setMember("Response", new Constructor(Kind.Response, realm));
        global.setMember("fetch", new Fetch(realm));
    }

    /** The three classes. */
    enum Kind {
        Headers, Request, Response
    }

    /** The methods of Headers. */
    enum HeadersMethod {
        append, set, get, has, delete, forEach, entries, keys, values
    }

    /** The methods of Request. */
    enum RequestMethod {
        text, json, clone
    }

    /** The methods of Response, and its one static. */
    enum ResponseMethod {
        text, json, arrayBuffer, clone, error
    }

    // -- the realm's own constructors, captured once per global ------------------------

    /** What the library needs from the global it is installed in. */
    private static final class Realm {
        final JSObject promise;
        final JSObject promiseResolve;
        final JSObject promiseReject;
        final JSObject jsonParse;
        final JSObject json;
        final JSObject array;
        final JSObject uint8Array;

        Realm(final JSObject global) {
            promise = (JSObject)global.getMember("Promise");
            promiseResolve = (JSObject)promise.getMember("resolve");
            promiseReject = (JSObject)promise.getMember("reject");
            json = (JSObject)global.getMember("JSON");
            jsonParse = (JSObject)json.getMember("parse");
            array = (JSObject)global.getMember("Array");
            uint8Array = (JSObject)global.getMember("Uint8Array");
        }

        // What a mirror hands back is a mirror of the realm's object; handed to a
        // script as is, a mirror is an opaque value - not a promise it adopts, not an
        // Error instanceof knows - so everything given to a script is unwrapped first.

        /** new Promise(executor), the executor written in Java. */
        Object newPromise(final BiConsumer<JSObject, JSObject> executor) {
            return ScriptUtils.unwrap(promise.newObject(function((thiz, args) -> {
                executor.accept((JSObject)args[0], (JSObject)args[1]);
                return null;
            })));
        }

        Object resolved(final Object value) {
            return ScriptUtils.unwrap(promiseResolve.call(promise, value));
        }

        Object rejected(final Object reason) {
            return ScriptUtils.unwrap(promiseReject.call(promise, reason));
        }

        Object parseJson(final String text) {
            return ScriptUtils.unwrap(jsonParse.call(json, text));
        }

        Object newArray(final List<?> elements) {
            final JSObject array = (JSObject)this.array.newObject();
            for (int i = 0; i < elements.size(); i++) {
                array.setSlot(i, elements.get(i));
            }
            return ScriptUtils.unwrap(array);
        }

        /** An ArrayBuffer holding these bytes: new Uint8Array(arrayOfNumbers).buffer, a Java byte[] not being array-like to the constructor. */
        Object arrayBuffer(final byte[] bytes) {
            final List<Object> numbers = new ArrayList<>(bytes.length);
            for (final byte b : bytes) {
                numbers.add(b & 0xFF);
            }
            return ScriptUtils.unwrap(((JSObject)uint8Array.newObject(newArray(numbers))).getMember("buffer"));
        }

        /** The error object of a TypeError, to reject a promise with. */
        Object typeError(final String message) {
            return ScriptUtils.unwrap(ScriptUtils.typeError(message).getEcmaError());
        }
    }

    private interface Body {
        Object call(Object thiz, Object... args);
    }

    private static JSObject function(final Body body) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return body.call(thiz, args);
            }
        };
    }

    private static Object arg(final Object[] args, final int index) {
        return index < args.length ? args[index] : ScriptUtils.undefined();
    }

    // -- the objects ------------------------------------------------------------------------

    /** An instance of one of the classes: named, with data properties and shared methods. */
    private abstract static class Instance extends AbstractJSObject {
        final Realm realm;
        private final Map<String, JSObject> methods;
        private Map<String, Object> extras;

        Instance(final Realm realm, final Map<String, JSObject> methods) {
            this.realm = realm;
            this.methods = methods;
        }

        Map<String, JSObject> methodsOf() {
            return methods;
        }

        /** The value of a data property, or null if there is none by that name. */
        abstract Object property(String name);

        /** The names of the data properties. */
        abstract List<String> propertyNames();

        @Override
        public Object getMember(final String name) {
            if (extras != null && extras.containsKey(name)) {
                return extras.get(name);
            }
            final Object value = property(name);
            if (value != null) {
                return value;
            }
            final JSObject method = methods.get(name);
            return method != null ? method : ScriptUtils.undefined();
        }

        @Override
        public boolean hasMember(final String name) {
            return (extras != null && extras.containsKey(name)) || property(name) != null || methods.containsKey(name);
        }

        @Override
        public void setMember(final String name, final Object value) {
            if (extras == null) {
                extras = new LinkedHashMap<>();
            }
            extras.put(name, value);
        }

        @Override
        public void removeMember(final String name) {
            if (extras != null) {
                extras.remove(name);
            }
        }

        @Override
        public Set<String> keySet() {
            final Set<String> keys = new java.util.LinkedHashSet<>(propertyNames());
            if (extras != null) {
                keys.addAll(extras.keySet());
            }
            return keys;
        }

        @Override
        public Object getDefaultValue(final Class<?> hint) {
            return "[object " + getClassName() + "]";
        }
    }

    /** A method of a class: dispatches on its enum entry, with the receiver checked. */
    private static final class Method<E extends Enum<E>> extends AbstractJSObject {
        interface Impl<E> {
            Object call(E method, Object thiz, Object[] args);
        }

        private final E method;
        private final Impl<E> impl;

        Method(final E method, final Impl<E> impl) {
            this.method = method;
            this.impl = impl;
        }

        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object getMember(final String name) {
            return "name".equals(name) ? method.name() : super.getMember(name);
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            return impl.call(method, thiz, args);
        }
    }

    private static <E extends Enum<E>> Map<String, JSObject> methods(final Class<E> names, final Method.Impl<E> impl) {
        final Map<String, JSObject> methods = new LinkedHashMap<>();
        for (final E method : names.getEnumConstants()) {
            methods.put(method.name(), new Method<>(method, impl));
        }
        return Collections.unmodifiableMap(methods);
    }

    private static <T> T receiver(final Object thiz, final Class<T> type, final String what) {
        if (!type.isInstance(thiz)) {
            throw ScriptUtils.typeError(what + ": the receiver is not a " + type.getSimpleName());
        }
        return type.cast(thiz);
    }

    /** Headers, Request or Response as a constructor: new, instanceof, and Response.error. */
    private static final class Constructor extends AbstractJSObject {
        private final Kind kind;
        private final Realm realm;
        private final Map<String, JSObject> headersMethods;
        private final Map<String, JSObject> requestMethods;
        private final Map<String, JSObject> responseMethods;

        Constructor(final Kind kind, final Realm realm) {
            this.kind = kind;
            this.realm = realm;
            headersMethods = methods(HeadersMethod.class, Headers::invoke);
            requestMethods = methods(RequestMethod.class, Request::invoke);
            responseMethods = methods(ResponseMethod.class, Response::invoke);
        }

        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public String getClassName() {
            return "Function";
        }

        @Override
        public Object getMember(final String name) {
            switch (name) {
            case "name":
                return kind.name();
            case "length":
                return kind == Kind.Headers ? 0 : 1;
            case "error":
                return kind == Kind.Response ? responseMethods.get("error") : ScriptUtils.undefined();
            default:
                return super.getMember(name);
            }
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            throw ScriptUtils.typeError(kind + " constructor requires 'new'");
        }

        @Override
        public Object newObject(final Object... args) {
            switch (kind) {
            case Headers:
                return new Headers(realm, headersMethods, arg(args, 0));
            case Request:
                return Request.of(realm, requestMethods, headersMethods, arg(args, 0), arg(args, 1));
            case Response:
                return Response.of(realm, responseMethods, headersMethods, arg(args, 0), arg(args, 1));
            default:
                throw new IllegalStateException();
            }
        }

        @Override
        public boolean isInstance(final Object instance) {
            switch (kind) {
            case Headers:
                return instance instanceof Headers;
            case Request:
                return instance instanceof Request;
            case Response:
                return instance instanceof Response;
            default:
                return false;
            }
        }

        @Override
        public Object getDefaultValue(final Class<?> hint) {
            return "function " + kind + "() { [native code] }";
        }
    }

    // -- Headers ---------------------------------------------------------------------------

    /** A header list: lower-case names, several values a name, iteration in name order. */
    static final class Headers extends Instance {
        private final Map<String, List<String>> map = new TreeMap<>();

        Headers(final Realm realm, final Map<String, JSObject> methods, final Object init) {
            super(realm, methods);
            if (init instanceof Headers other) {
                other.map.forEach((name, values) -> map.put(name, new ArrayList<>(values)));
            } else if (init instanceof JSObject object && object.isArray()) {
                final int count = ScriptUtils.toInt32(object.getMember("length"));
                for (int i = 0; i < count; i++) {
                    if (!(object.getSlot(i) instanceof JSObject pair) || !pair.isArray() || ScriptUtils.toInt32(pair.getMember("length")) != 2) {
                        throw ScriptUtils.typeError("Headers: an init array holds [name, value] pairs");
                    }
                    append(pair.getSlot(0), pair.getSlot(1));
                }
            } else if (init instanceof JSObject object) {
                for (final String name : object.keySet()) {
                    append(name, object.getMember(name));
                }
            } else if (!ScriptUtils.isNullOrUndefined(init)) {
                throw ScriptUtils.typeError("Headers: cannot make headers from " + ScriptUtils.typeOf(init));
            }
        }

        private static String name(final Object name) {
            final String text = ScriptUtils.toString(name);
            if (!HEADER_NAME.matcher(text).matches()) {
                throw ScriptUtils.typeError("Headers: invalid header name \"" + text + "\"");
            }
            return text.toLowerCase(Locale.ROOT);
        }

        private static String value(final Object value) {
            return ScriptUtils.toString(value).strip();
        }

        void append(final Object name, final Object value) {
            map.computeIfAbsent(name(name), k -> new ArrayList<>()).add(value(value));
        }

        String get(final String name) {
            final List<String> values = map.get(name);
            return values == null ? null : String.join(", ", values);
        }

        /** name, joined value - in name order. */
        List<String[]> rows() {
            final List<String[]> rows = new ArrayList<>();
            map.forEach((name, values) -> rows.add(new String[] { name, String.join(", ", values) }));
            return rows;
        }

        Headers copy(final Map<String, JSObject> methods) {
            return new Headers(realm, methods, this);
        }

        @Override
        public String getClassName() {
            return "Headers";
        }

        @Override
        Object property(final String name) {
            return null;
        }

        @Override
        List<String> propertyNames() {
            return List.of();
        }

        static Object invoke(final HeadersMethod method, final Object thiz, final Object[] args) {
            final Headers self = receiver(thiz, Headers.class, "Headers." + method);
            switch (method) {
            case append:
                self.append(arg(args, 0), arg(args, 1));
                return ScriptUtils.undefined();
            case set:
                self.map.put(name(arg(args, 0)), new ArrayList<>(List.of(value(arg(args, 1)))));
                return ScriptUtils.undefined();
            case get:
                return self.get(name(arg(args, 0)));
            case has:
                return self.map.containsKey(name(arg(args, 0)));
            case delete:
                self.map.remove(name(arg(args, 0)));
                return ScriptUtils.undefined();
            case forEach: {
                final Object callback = arg(args, 0);
                if (!(callback instanceof JSObject function) || !ScriptUtils.isCallable(callback)) {
                    throw ScriptUtils.typeError("Headers.forEach: the argument is not a function");
                }
                final Object thisArg = arg(args, 1);
                for (final String[] row : self.rows()) {
                    function.call(thisArg, row[1], row[0], self);
                }
                return ScriptUtils.undefined();
            }
            case entries: {
                final List<Object> pairs = new ArrayList<>();
                for (final String[] row : self.rows()) {
                    pairs.add(self.realm.newArray(List.of(row[0], row[1])));
                }
                return self.realm.newArray(pairs);
            }
            case keys: {
                final List<Object> names = new ArrayList<>();
                for (final String[] row : self.rows()) {
                    names.add(row[0]);
                }
                return self.realm.newArray(names);
            }
            case values: {
                final List<Object> values = new ArrayList<>();
                for (final String[] row : self.rows()) {
                    values.add(row[1]);
                }
                return self.realm.newArray(values);
            }
            default:
                throw new IllegalStateException(method.name());
            }
        }
    }

    // -- Request -----------------------------------------------------------------------------

    /** A request: url, method, headers, an optional string body. */
    static final class Request extends Instance {
        final String url;
        final String method;
        final Headers headers;
        final String body;

        private Request(final Realm realm, final Map<String, JSObject> methods, final String url, final String method, final Headers headers, final String body) {
            super(realm, methods);
            this.url = url;
            this.method = method;
            this.headers = headers;
            this.body = body;
        }

        /** new Request(input, init): from a URL string or another Request, with init's method, headers and body. */
        static Request of(final Realm realm, final Map<String, JSObject> methods, final Map<String, JSObject> headersMethods, final Object input, final Object init) {
            if (ScriptUtils.isUndefined(input)) {
                throw ScriptUtils.typeError("Request: 1 argument required");
            }
            String url;
            String method = "GET";
            Headers headers;
            String body = null;
            if (input instanceof Request other) {
                url = other.url;
                method = other.method;
                headers = other.headers.copy(headersMethods);
                body = other.body;
            } else {
                url = ScriptUtils.toString(input);
                headers = new Headers(realm, headersMethods, ScriptUtils.undefined());
            }
            if (init instanceof JSObject options) {
                final Object m = options.getMember("method");
                if (!ScriptUtils.isNullOrUndefined(m)) {
                    method = methodOf(m);
                }
                final Object h = options.getMember("headers");
                if (!ScriptUtils.isNullOrUndefined(h)) {
                    headers = new Headers(realm, headersMethods, h);
                }
                final Object b = options.getMember("body");
                if (!ScriptUtils.isNullOrUndefined(b)) {
                    if (method.equals("GET") || method.equals("HEAD")) {
                        throw ScriptUtils.typeError("Request: a " + method + " request cannot have a body");
                    }
                    body = ScriptUtils.toString(b);
                }
            }
            return new Request(realm, methods, url, method, headers, body);
        }

        private static String methodOf(final Object value) {
            final String method = ScriptUtils.toString(value).toUpperCase(Locale.ROOT);
            if (!METHOD.matcher(method).matches()) {
                throw ScriptUtils.typeError("Request: invalid method \"" + value + "\"");
            }
            return method;
        }

        @Override
        public String getClassName() {
            return "Request";
        }

        @Override
        Object property(final String name) {
            switch (name) {
            case "url": return url;
            case "method": return method;
            case "headers": return headers;
            case "bodyUsed": return false;
            default: return null;
            }
        }

        @Override
        List<String> propertyNames() {
            return List.of("url", "method", "headers", "bodyUsed");
        }

        static Object invoke(final RequestMethod method, final Object thiz, final Object[] args) {
            final Request self = receiver(thiz, Request.class, "Request." + method);
            switch (method) {
            case text:
                return self.realm.resolved(self.body == null ? "" : self.body);
            case json:
                try {
                    return self.realm.resolved(self.realm.parseJson(self.body == null ? "" : self.body));
                } catch (final RuntimeException e) {
                    return self.realm.rejected(self.realm.typeError("Request.json: " + e.getMessage()));
                }
            case clone:
                return new Request(self.realm, self.methodsOf(), self.url, self.method, self.headers.copy(self.headers.methodsOf()), self.body);
            default:
                throw new IllegalStateException(method.name());
            }
        }
    }

    // -- Response ----------------------------------------------------------------------------

    /** A response: status, headers, url, and a body readable once. */
    static final class Response extends Instance {
        int status;
        final String statusText;
        final Headers headers;
        final String url;
        final String text;
        final byte[] bytes;   // the wire bytes of a fetched body; null for one made by hand
        boolean bodyUsed;

        Response(final Realm realm, final Map<String, JSObject> methods, final int status, final String statusText, final Headers headers, final String url, final String text, final byte[] bytes) {
            super(realm, methods);
            this.status = status;
            this.statusText = statusText;
            this.headers = headers;
            this.url = url;
            this.text = text;
            this.bytes = bytes;
        }

        /** new Response(body, init). */
        static Response of(final Realm realm, final Map<String, JSObject> methods, final Map<String, JSObject> headersMethods, final Object body, final Object init) {
            int status = 200;
            String statusText = "";
            Headers headers = new Headers(realm, headersMethods, ScriptUtils.undefined());
            String url = "";
            if (init instanceof JSObject options) {
                final Object s = options.getMember("status");
                if (!ScriptUtils.isNullOrUndefined(s)) {
                    status = ScriptUtils.toInt32(s);
                    if (status < 200 || status > 599) {
                        throw ScriptUtils.rangeError("Response: status " + status + " is out of range");
                    }
                }
                final Object st = options.getMember("statusText");
                if (!ScriptUtils.isNullOrUndefined(st)) {
                    statusText = ScriptUtils.toString(st);
                }
                final Object h = options.getMember("headers");
                if (!ScriptUtils.isNullOrUndefined(h)) {
                    headers = new Headers(realm, headersMethods, h);
                }
                final Object u = options.getMember("url");
                if (!ScriptUtils.isNullOrUndefined(u)) {
                    url = ScriptUtils.toString(u);
                }
            }
            return new Response(realm, methods, status, statusText, headers, url, ScriptUtils.isNullOrUndefined(body) ? "" : ScriptUtils.toString(body), null);
        }

        @Override
        public String getClassName() {
            return "Response";
        }

        @Override
        Object property(final String name) {
            switch (name) {
            case "status": return status;
            case "statusText": return statusText;
            case "ok": return status >= 200 && status < 300;
            case "url": return url;
            case "headers": return headers;
            case "bodyUsed": return bodyUsed;
            default: return null;
            }
        }

        @Override
        List<String> propertyNames() {
            return List.of("status", "statusText", "ok", "url", "headers", "bodyUsed");
        }

        /** Marks the body read; the rejection if it was read already, else null. */
        private Object consume() {
            if (bodyUsed) {
                return realm.rejected(realm.typeError("Response: body already used"));
            }
            bodyUsed = true;
            return null;
        }

        static Object invoke(final ResponseMethod method, final Object thiz, final Object[] args) {
            if (method == ResponseMethod.error) {
                // the static: Response.error(), a network error response
                final Response self = receiver(errorReceiver(thiz), Response.class, "Response.error");
                return self;
            }
            final Response self = receiver(thiz, Response.class, "Response." + method);
            switch (method) {
            case text: {
                final Object used = self.consume();
                return used != null ? used : self.realm.resolved(self.text);
            }
            case json: {
                final Object used = self.consume();
                if (used != null) {
                    return used;
                }
                try {
                    return self.realm.resolved(self.realm.parseJson(self.text));
                } catch (final RuntimeException e) {
                    return self.realm.rejected(self.realm.typeError("Response.json: " + e.getMessage()));
                }
            }
            case arrayBuffer: {
                final Object used = self.consume();
                if (used != null) {
                    return used;
                }
                return self.realm.resolved(self.realm.arrayBuffer(self.bytes != null ? self.bytes : self.text.getBytes(StandardCharsets.ISO_8859_1)));
            }
            case clone:
                return new Response(self.realm, self.methodsOf(), self.status, self.statusText, self.headers.copy(self.headers.methodsOf()), self.url, self.text, self.bytes);
            default:
                throw new IllegalStateException(method.name());
            }
        }

        /** Response.error() is called on the constructor; make the response it describes. */
        private static Object errorReceiver(final Object thiz) {
            if (thiz instanceof Constructor constructor && constructor.kind == Kind.Response) {
                final Response response = new Response(constructor.realm, constructor.responseMethods, 200, "", new Headers(constructor.realm, constructor.headersMethods, ScriptUtils.undefined()), "", "", null);
                response.status = 0;
                return response;
            }
            return thiz;
        }
    }

    // -- fetch ---------------------------------------------------------------------------------

    /** fetch(input, init): a promise of a Response. */
    private static final class Fetch extends AbstractJSObject {
        private final Realm realm;
        private final Map<String, JSObject> headersMethods;
        private final Map<String, JSObject> requestMethods;
        private final Map<String, JSObject> responseMethods;

        Fetch(final Realm realm) {
            this.realm = realm;
            headersMethods = methods(HeadersMethod.class, Headers::invoke);
            requestMethods = methods(RequestMethod.class, Request::invoke);
            responseMethods = methods(ResponseMethod.class, Response::invoke);
        }

        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object getMember(final String name) {
            switch (name) {
            case "name": return "fetch";
            case "length": return 1;
            default: return super.getMember(name);
            }
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            return realm.newPromise((resolve, reject) -> {
                final Request request;
                final HttpRequest wire;
                try {
                    request = Request.of(realm, requestMethods, headersMethods, arg(args, 0), arg(args, 1));
                    wire = build(request);
                } catch (final RuntimeException e) {
                    reject.call(null, realm.typeError("fetch: " + message(e)));
                    return;
                }
                final EventLoop.Pending pending = EventLoop.current().pending();
                CLIENT.sendAsync(wire, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, failure) -> {
                    if (failure != null) {
                        pending.complete(() -> reject.call(null, realm.typeError("fetch: " + message(failure))));
                        return;
                    }
                    final byte[] bytes = response.body();
                    final String text = new String(bytes, charsetOf(response.headers().firstValue("content-type").orElse("")));
                    pending.complete(() -> {
                        final Headers headers = new Headers(realm, headersMethods, ScriptUtils.undefined());
                        // an HTTP/2 response carries its pseudo-headers (:status) in the map; they are not headers
                        response.headers().map().forEach((name, values) -> {
                            if (!name.startsWith(":")) {
                                values.forEach(value -> headers.append(name, value));
                            }
                        });
                        resolve.call(null, new Response(realm, responseMethods, response.statusCode(), reason(response.statusCode()), headers, response.uri().toString(), text, bytes));
                    });
                });
            });
        }

        private static HttpRequest build(final Request request) {
            final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url));
            for (final String[] row : request.headers.rows()) {
                try {
                    builder.header(row[0], row[1]);
                } catch (final IllegalArgumentException restricted) {
                    // a header the client sets itself: content-length, host, connection...
                }
            }
            final boolean withBody = request.body != null && !request.method.equals("GET") && !request.method.equals("HEAD");
            builder.method(request.method, withBody ? HttpRequest.BodyPublishers.ofString(request.body, StandardCharsets.UTF_8) : HttpRequest.BodyPublishers.noBody());
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
