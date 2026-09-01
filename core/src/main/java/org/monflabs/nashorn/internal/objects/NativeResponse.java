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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
 * WHATWG Fetch: the Response class - status, headers, url, a body readable
 * once as text, JSON or an ArrayBuffer. Installed by the fetch standard
 * library; what fetch resolves with.
 */
@ScriptClass("Response")
public final class NativeResponse extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private int status;
    private final String statusText;
    private final NativeHeaders headers;
    private final String url;
    private final String text;
    private final byte[] bytes;   // the wire bytes of a fetched body; null for one made by hand
    private boolean bodyUsed;

    private NativeResponse(final Global global, final int status, final String statusText, final NativeHeaders headers, final String url, final String text, final byte[] bytes) {
        super(global.getResponsePrototype(), $nasgenmap$);
        this.status = status;
        this.statusText = statusText;
        this.headers = headers;
        this.url = url;
        this.text = text;
        this.bytes = bytes;
    }

    /**
     * new Response(body, init).
     *
     * @param isNew whether called with new
     * @param self self
     * @param body the body, as a string; null or undefined for none
     * @param init status, statusText, headers, url
     * @return the response
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean isNew, final Object self, final Object body, final Object init) {
        if (!isNew) {
            throw ECMAErrors.typeError("constructor.requires.new", "Response");
        }
        final Global global = Global.instance();
        int status = 200;
        String statusText = "";
        NativeHeaders headers = NativeHeaders.empty(global);
        String url = "";
        final Object s = NativeRequest.member(init, "status");
        if (!JSType.nullOrUndefined(s)) {
            status = JSType.toInt32(s);
            if (status < 200 || status > 599) {
                throw new ECMAException(global.newRangeError("Response: status " + status + " is out of range"), null);
            }
        }
        final Object st = NativeRequest.member(init, "statusText");
        if (!JSType.nullOrUndefined(st)) {
            statusText = JSType.toString(st);
        }
        final Object h = NativeRequest.member(init, "headers");
        if (!JSType.nullOrUndefined(h)) {
            headers = (NativeHeaders)NativeHeaders.construct(true, ScriptRuntime.UNDEFINED, h);
        }
        final Object u = NativeRequest.member(init, "url");
        if (!JSType.nullOrUndefined(u)) {
            url = JSType.toString(u);
        }
        return new NativeResponse(global, status, statusText, headers, url, JSType.nullOrUndefined(body) ? "" : JSType.toString(body), null);
    }

    /** What fetch resolves with. */
    public static NativeResponse fetched(final Global global, final int status, final String statusText, final NativeHeaders headers, final String url, final String text, final byte[] bytes) {
        return new NativeResponse(global, status, statusText, headers, url, text, bytes);
    }

    private static ECMAException typeError(final String message) {
        return new ECMAException(Global.instance().newTypeError(message), null);
    }

    private static NativeResponse self(final Object self, final String what) {
        if (!(self instanceof NativeResponse response)) {
            throw typeError("Response." + what + ": the receiver is not a Response");
        }
        return response;
    }

    /** A promise resolved with a value. */
    static Object resolved(final Global global, final Object value) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        NativePromise.resolveAsyncPromise(promise, value);
        return promise;
    }

    /** A promise rejected with a TypeError. */
    static Object rejected(final Global global, final String message) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        NativePromise.rejectAsyncPromise(promise, global.newTypeError(message));
        return promise;
    }

    /** A promise of the text parsed as JSON, rejected with a TypeError if it does not parse. */
    static Object parsed(final Global global, final String text, final String what) {
        try {
            return resolved(global, NativeJSON.parse(global, text, ScriptRuntime.UNDEFINED));
        } catch (final ECMAException e) {
            return rejected(global, what + ": " + e.getMessage());
        }
    }

    /** Marks the body read; the rejection if it was read already, else null. */
    private Object consume(final Global global) {
        if (bodyUsed) {
            return rejected(global, "Response: body already used");
        }
        bodyUsed = true;
        return null;
    }

    /** @param self self @return the status */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object status(final Object self) {
        return self(self, "status").status;
    }

    /** @param self self @return the status text */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object statusText(final Object self) {
        return self(self, "statusText").statusText;
    }

    /** @param self self @return whether the status is 2xx */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object ok(final Object self) {
        final int status = self(self, "ok").status;
        return status >= 200 && status < 300;
    }

    /** @param self self @return the url */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object url(final Object self) {
        return self(self, "url").url;
    }

    /** @param self self @return the headers */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object headers(final Object self) {
        return self(self, "headers").headers;
    }

    /** @param self self @return whether the body has been read */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static Object bodyUsed(final Object self) {
        return self(self, "bodyUsed").bodyUsed;
    }

    /**
     * A promise of the body as text; the body can be read once.
     * @param self self
     * @return the promise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object text(final Object self) {
        final NativeResponse response = self(self, "text");
        final Global global = Global.instance();
        final Object used = response.consume(global);
        return used != null ? used : resolved(global, response.text);
    }

    /**
     * A promise of the body parsed as JSON.
     * @param self self
     * @return the promise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object json(final Object self) {
        final NativeResponse response = self(self, "json");
        final Global global = Global.instance();
        final Object used = response.consume(global);
        return used != null ? used : parsed(global, response.text, "Response.json");
    }

    /**
     * A promise of the body's bytes as an ArrayBuffer.
     * @param self self
     * @return the promise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object arrayBuffer(final Object self) {
        final NativeResponse response = self(self, "arrayBuffer");
        final Global global = Global.instance();
        final Object used = response.consume(global);
        if (used != null) {
            return used;
        }
        final byte[] bytes = response.bytes != null ? response.bytes : response.text.getBytes(StandardCharsets.ISO_8859_1);
        return resolved(global, new NativeArrayBuffer(ByteBuffer.wrap(bytes.clone()), global));
    }

    /**
     * A copy, with an unread body.
     * @param self self
     * @return the copy
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object clone(final Object self) {
        final NativeResponse response = self(self, "clone");
        final Global global = Global.instance();
        final NativeResponse copy = new NativeResponse(global, response.status, response.statusText, response.headers.copy(global), response.url, response.text, response.bytes);
        copy.status = response.status;
        return copy;
    }

    /**
     * Response.error(): a network error response, status 0.
     * @param self the constructor
     * @return the response
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object error(final Object self) {
        final Global global = Global.instance();
        final NativeResponse response = new NativeResponse(global, 200, "", NativeHeaders.empty(global), "", "", null);
        response.status = 0;
        return response;
    }

    /** Response.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Response";

    @Override
    public String getClassName() {
        return "Response";
    }
}
