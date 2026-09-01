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

package org.monflabs.nashorn.internal.objects;

import java.util.Locale;
import java.util.regex.Pattern;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * WHATWG Fetch: the Request class - a URL, a method, headers, an optional
 * string body. Installed by the fetch standard library.
 */
@ScriptClass("Request")
public final class NativeRequest extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private static final Pattern METHOD = Pattern.compile("[A-Z]+");

    private final String url;
    private final String method;
    private final NativeHeaders headers;
    private final String body;

    private NativeRequest(final Global global, final String url, final String method, final NativeHeaders headers, final String body) {
        super(global.getRequestPrototype(), $nasgenmap$);
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.body = body;
    }

    /**
     * new Request(input, init).
     *
     * @param isNew whether called with new
     * @param self self
     * @param input a URL string or a Request
     * @param init method, headers, body
     * @return the request
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean isNew, final Object self, final Object input, final Object init) {
        if (!isNew) {
            throw ECMAErrors.typeError("constructor.requires.new", "Request");
        }
        return from(Global.instance(), input, init);
    }

    /** What fetch and the constructor share: a request from (input, init). */
    public static NativeRequest from(final Global global, final Object input, final Object init) {
        if (input == ScriptRuntime.UNDEFINED) {
            throw typeError("Request: 1 argument required");
        }
        String url;
        String method = "GET";
        NativeHeaders headers;
        String body = null;
        if (input instanceof NativeRequest other) {
            url = other.url;
            method = other.method;
            headers = other.headers.copy(global);
            body = other.body;
        } else {
            url = JSType.toString(input);
            headers = NativeHeaders.empty(global);
        }
        final Object m = member(init, "method");
        if (!JSType.nullOrUndefined(m)) {
            method = methodOf(m);
        }
        final Object h = member(init, "headers");
        if (!JSType.nullOrUndefined(h)) {
            headers = (NativeHeaders)NativeHeaders.construct(true, ScriptRuntime.UNDEFINED, h);
        }
        final Object b = member(init, "body");
        if (!JSType.nullOrUndefined(b)) {
            if (method.equals("GET") || method.equals("HEAD")) {
                throw typeError("Request: a " + method + " request cannot have a body");
            }
            body = JSType.toString(b);
        }
        return new NativeRequest(global, url, method, headers, body);
    }

    /** A member of an init object, or undefined; the init may be a script object or a JSObject. */
    static Object member(final Object init, final String name) {
        if (init instanceof ScriptObject object) {
            return object.get(name);
        }
        if (init instanceof JSObject object) {
            final Object value = object.getMember(name);
            return value == null ? ScriptRuntime.UNDEFINED : value;
        }
        return ScriptRuntime.UNDEFINED;
    }

    private static String methodOf(final Object value) {
        final String method = JSType.toString(value).toUpperCase(Locale.ROOT);
        if (!METHOD.matcher(method).matches()) {
            throw typeError("Request: invalid method \"" + value + "\"");
        }
        return method;
    }

    private static ECMAException typeError(final String message) {
        return new ECMAException(Global.instance().newTypeError(message), null);
    }

    private static NativeRequest self(final Object self, final String what) {
        if (!(self instanceof NativeRequest request)) {
            throw typeError("Request." + what + ": the receiver is not a Request");
        }
        return request;
    }

    /** The URL. */
    public String url() {
        return url;
    }

    /** The method, upper case. */
    public String method() {
        return method;
    }

    /** The headers. */
    public NativeHeaders headers() {
        return headers;
    }

    /** The body, or null. */
    public String body() {
        return body;
    }

    /** @param self self @return the url */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object url(final Object self) {
        return self(self, "url").url;
    }

    /** @param self self @return the method */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object method(final Object self) {
        return self(self, "method").method;
    }

    /** @param self self @return the headers */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object headers(final Object self) {
        return self(self, "headers").headers;
    }

    /** @param self self @return false: a request body is read whole */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object bodyUsed(final Object self) {
        self(self, "bodyUsed");
        return false;
    }

    /**
     * A promise of the body as text.
     * @param self self
     * @return the promise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object text(final Object self) {
        final NativeRequest request = self(self, "text");
        return NativeResponse.resolved(Global.instance(), request.body == null ? "" : request.body);
    }

    /**
     * A promise of the body parsed as JSON.
     * @param self self
     * @return the promise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object json(final Object self) {
        final NativeRequest request = self(self, "json");
        return NativeResponse.parsed(Global.instance(), request.body == null ? "" : request.body, "Request.json");
    }

    /**
     * A copy.
     * @param self self
     * @return the copy
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object clone(final Object self) {
        final NativeRequest request = self(self, "clone");
        final Global global = Global.instance();
        return new NativeRequest(global, request.url, request.method, request.headers.copy(global), request.body);
    }

    /** Request.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Request";

    @Override
    public String getClassName() {
        return "Request";
    }
}
