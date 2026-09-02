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

package org.monflabs.nashorn.libs.node;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * Node's {@code Buffer} in Java, exposed by the {@link NodeModuleLoader} as the
 * {@code buffer} module. A {@code Buffer} is a real {@code Uint8Array} - created
 * through the realm's own {@code Uint8Array} and reparented onto a
 * per-realm {@code Buffer.prototype} that chains to {@code Uint8Array.prototype}
 * - so indexing, {@code length}, iteration and {@code instanceof Uint8Array} all
 * work, with Node's encodings and numeric accessors on top.
 *
 * <p>The exported {@code Buffer} object and every method are realm-agnostic:
 * they act on the current realm through {@link Global#instance()} at call time,
 * so the fixed module exports are safe to share across realms.
 *
 * @since 2017.0.0
 */
public final class NodeBuffer {

    /** One Buffer.prototype per realm, chaining to that realm's Uint8Array.prototype. */
    private static final WeakHashMap<Global, ScriptObject> PROTOTYPES = new WeakHashMap<>();

    private NodeBuffer() {
    }

    /**
     * The {@code buffer} module's exports: the {@code Buffer} class named and as
     * the default {@code { Buffer }}.
     * @return the export map
     */
    public static Map<String, Object> exports() {
        final Map<String, Object> def = new LinkedHashMap<>();
        def.put("Buffer", BUFFER);
        final Map<String, Object> exports = new LinkedHashMap<>();
        exports.put("Buffer", BUFFER);
        exports.put("default", namespace(def));
        return exports;
    }

    // ---- the Buffer "class": callable, with statics and a per-realm prototype ----

    private static final JSObject BUFFER = new AbstractJSObject() {
        private final Map<String, Object> statics = statics();

        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object newObject(final Object... args) {
            return call(Undefined.INSTANCE, args);   // new Buffer(x), deprecated, behaves like Buffer.from/alloc
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            if (args.length > 0 && (args[0] instanceof Number)) {
                return alloc(new Object[] {args[0]});
            }
            return from(args);
        }

        @Override
        public Object getMember(final String name) {
            if ("prototype".equals(name)) {
                return prototype(Global.instance());
            }
            return statics.containsKey(name) ? statics.get(name) : super.getMember(name);
        }

        @Override
        public boolean hasMember(final String name) {
            return "prototype".equals(name) || statics.containsKey(name);
        }

        @Override
        public Set<String> keySet() {
            return statics.keySet();
        }

        @Override
        public String toString() {
            return "function Buffer() { [node:buffer] }";
        }
    };

    private static Map<String, Object> statics() {
        final Map<String, Object> s = new LinkedHashMap<>();
        s.put("from", fn("from", NodeBuffer::from));
        s.put("alloc", fn("alloc", NodeBuffer::alloc));
        s.put("allocUnsafe", fn("allocUnsafe", a -> alloc(new Object[] {a.length > 0 ? a[0] : 0})));
        s.put("isBuffer", fn("isBuffer", a -> a.length > 0 && isBuffer(a[0])));
        s.put("isEncoding", fn("isEncoding", a -> a.length > 0 && ENCODINGS.contains(norm(str(a[0])))));
        s.put("byteLength", fn("byteLength", a -> encode(str(a[0]), a.length > 1 ? str(a[1]) : null).length));
        s.put("concat", fn("concat", NodeBuffer::concat));
        s.put("compare", fn("compare", a -> compareBytes(a[0], a[1])));
        return s;
    }

    // ---- statics ----

    private static Object from(final Object[] a) {
        final Object v = a.length > 0 ? a[0] : Undefined.INSTANCE;
        if (v instanceof CharSequence s) {
            return make(encode(s.toString(), a.length > 1 ? str(a[1]) : null));
        }
        if (v instanceof ScriptObject || v instanceof JSObject) {
            final int n = len(v);
            final byte[] b = new byte[n];
            for (int i = 0; i < n; i++) {
                b[i] = (byte)at(v, i);
            }
            return make(b);
        }
        throw typeError("Buffer.from: string, array or buffer expected");
    }

    private static Object alloc(final Object[] a) {
        final int size = (int)toLong(a.length > 0 ? a[0] : 0);
        final byte[] b = new byte[size];
        if (a.length > 1) {
            final byte fill = a[1] instanceof CharSequence s ? (s.length() > 0 ? (byte)s.charAt(0) : 0) : (byte)toLong(a[1]);
            java.util.Arrays.fill(b, fill);
        }
        return make(b);
    }

    private static Object concat(final Object[] a) {
        final Object list = a[0];
        final int count = len(list);
        int total = a.length > 1 ? (int)toLong(a[1]) : 0;
        final byte[][] parts = new byte[count][];
        for (int i = 0; i < count; i++) {
            final Object part = memberAt(list, i);
            final int pl = len(part);
            parts[i] = new byte[pl];
            for (int j = 0; j < pl; j++) {
                parts[i][j] = (byte)at(part, j);
            }
            if (a.length <= 1) {
                total += pl;
            }
        }
        final byte[] out = new byte[total];
        int pos = 0;
        for (final byte[] p : parts) {
            final int n = Math.min(p.length, total - pos);
            System.arraycopy(p, 0, out, pos, n);
            pos += n;
        }
        return make(out);
    }

    // ---- instance methods (this = a Buffer/Uint8Array) ----

    private static Map<String, Object> instanceMethods() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("toString", method("toString", (self, a) -> {
            final int len = len(self);
            final int start = a.length > 1 ? (int)toLong(a[1]) : 0;
            final int end = Math.min(a.length > 2 ? (int)toLong(a[2]) : len, len);
            return decode(bytes(self, start, end), a.length > 0 && a[0] != Undefined.INSTANCE ? str(a[0]) : null);
        }));
        m.put("toJSON", method("toJSON", (self, a) -> {
            final int n = len(self);
            final Object[] data = new Object[n];
            for (int i = 0; i < n; i++) {
                data[i] = at(self, i);
            }
            final Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", "Buffer");
            json.put("data", Global.instance().wrapAsObject(data));
            return namespace(json);
        }));
        m.put("write", method("write", (self, a) -> {
            int offset = 0;
            String enc = null;
            if (a.length > 1 && a[1] instanceof CharSequence) {
                enc = str(a[1]);
            } else if (a.length > 1) {
                offset = (int)toLong(a[1]);
                if (a.length > 3 && a[3] instanceof CharSequence) {
                    enc = str(a[3]);
                } else if (a.length > 2 && a[2] instanceof CharSequence) {
                    enc = str(a[2]);
                }
            }
            final byte[] src = encode(str(a[0]), enc);
            final int n = Math.min(src.length, len(self) - offset);
            for (int i = 0; i < n; i++) {
                put(self, offset + i, src[i]);
            }
            return n;
        }));
        m.put("copy", method("copy", (self, a) -> {
            final Object target = a[0];
            final int targetStart = a.length > 1 ? (int)toLong(a[1]) : 0;
            final int sourceStart = a.length > 2 ? (int)toLong(a[2]) : 0;
            final int sourceEnd = a.length > 3 ? (int)toLong(a[3]) : len(self);
            final int n = Math.min(sourceEnd - sourceStart, len(target) - targetStart);
            for (int i = 0; i < n; i++) {
                put(target, targetStart + i, at(self, sourceStart + i));
            }
            return n;
        }));
        m.put("equals", method("equals", (self, a) -> compareBytes(self, a[0]) == 0));
        m.put("compare", method("compare", (self, a) -> compareBytes(self, a[0])));
        m.put("fill", method("fill", (self, a) -> {
            final int start = a.length > 1 ? (int)toLong(a[1]) : 0;
            final int end = a.length > 2 ? (int)toLong(a[2]) : len(self);
            final byte[] pattern = a[0] instanceof CharSequence s ? encode(s.toString(), null) : new byte[] {(byte)toLong(a[0])};
            if (pattern.length > 0) {
                for (int i = start; i < end; i++) {
                    put(self, i, pattern[(i - start) % pattern.length]);
                }
            }
            return self;
        }));
        m.put("slice", method("slice", NodeBuffer::subarray));
        m.put("subarray", method("subarray", NodeBuffer::subarray));
        m.put("indexOf", method("indexOf", (self, a) -> indexOf(self, a, false)));
        m.put("lastIndexOf", method("lastIndexOf", (self, a) -> indexOf(self, a, true)));
        m.put("includes", method("includes", (self, a) -> indexOf(self, a, false) >= 0));
        // numeric accessors
        rw(m, "UInt8", 1, false, false, false);
        rw(m, "Int8", 1, false, true, false);
        rw(m, "UInt16LE", 2, true, false, false);
        rw(m, "UInt16BE", 2, false, false, false);
        rw(m, "Int16LE", 2, true, true, false);
        rw(m, "Int16BE", 2, false, true, false);
        rw(m, "UInt32LE", 4, true, false, false);
        rw(m, "UInt32BE", 4, false, false, false);
        rw(m, "Int32LE", 4, true, true, false);
        rw(m, "Int32BE", 4, false, true, false);
        rw(m, "FloatLE", 4, true, true, true);
        rw(m, "FloatBE", 4, false, true, true);
        rw(m, "DoubleLE", 8, true, true, true);
        rw(m, "DoubleBE", 8, false, true, true);
        return m;
    }

    private static Object subarray(final Object self, final Object[] a) {
        final Object target = org.monflabs.nashorn.api.scripting.ScriptUtils.unwrap(self);
        if (!(target instanceof ScriptObject base)) {
            return self;
        }
        final Global g = Global.instance();
        final ScriptObject u8proto = (ScriptObject)((ScriptObject)g.get("Uint8Array")).get("prototype");
        final Object sub = ScriptRuntime.call(u8proto.get("subarray"), base, a);   // the real Uint8Array.subarray, not our override
        if (sub instanceof ScriptObject so) {
            so.setProto(prototype(g));
        }
        return sub;
    }

    private static void rw(final Map<String, Object> m, final String name, final int size, final boolean little, final boolean signed, final boolean floating) {
        m.put("read" + name, method("read" + name, (self, a) -> {
            final int off = a.length > 0 ? (int)toLong(a[0]) : 0;
            final ByteBuffer bb = ByteBuffer.wrap(bytes(self, off, off + size)).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            if (floating) {
                return size == 4 ? (double)bb.getFloat() : bb.getDouble();
            }
            switch (size) {
            case 1: return (double)(signed ? bb.get() : (bb.get() & 0xff));
            case 2: return (double)(signed ? bb.getShort() : (bb.getShort() & 0xffff));
            default: return (double)(signed ? bb.getInt() : (bb.getInt() & 0xffffffffL));
            }
        }));
        m.put("write" + name, method("write" + name, (self, a) -> {
            final double value = JSType.toNumber(a[0]);
            final int off = a.length > 1 ? (int)toLong(a[1]) : 0;
            final ByteBuffer bb = ByteBuffer.allocate(size).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            if (floating) {
                if (size == 4) {
                    bb.putFloat((float)value);
                } else {
                    bb.putDouble(value);
                }
            } else if (size == 1) {
                bb.put((byte)(long)value);
            } else if (size == 2) {
                bb.putShort((short)(long)value);
            } else {
                bb.putInt((int)(long)value);
            }
            final byte[] out = bb.array();
            for (int i = 0; i < size; i++) {
                put(self, off + i, out[i]);
            }
            return off + size;
        }));
    }

    private static int indexOf(final Object self, final Object[] a, final boolean last) {
        final Object v = a[0];
        final byte[] needle = v instanceof CharSequence s ? encode(s.toString(), a.length > 2 ? str(a[2]) : null)
                : v instanceof Number ? new byte[] {(byte)toLong(v)} : bytesOf(v);
        final int n = len(self);
        final int start = a.length > 1 && a[1] instanceof Number ? (int)toLong(a[1]) : (last ? n - needle.length : 0);
        if (last) {
            for (int i = Math.min(start, n - needle.length); i >= 0; i--) {
                if (matches(self, i, needle)) {
                    return i;
                }
            }
        } else {
            for (int i = Math.max(start, 0); i <= n - needle.length; i++) {
                if (matches(self, i, needle)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean matches(final Object self, final int at, final byte[] needle) {
        for (int j = 0; j < needle.length; j++) {
            if ((byte)at(self, at + j) != needle[j]) {
                return false;
            }
        }
        return true;
    }

    // ---- realm plumbing ----

    private static ScriptObject prototype(final Global g) {
        synchronized (PROTOTYPES) {
            ScriptObject proto = PROTOTYPES.get(g);
            if (proto == null) {
                final ScriptObject u8proto = (ScriptObject)((ScriptObject)g.get("Uint8Array")).get("prototype");
                proto = g.newObject();
                proto.setProto(u8proto);
                for (final Map.Entry<String, Object> e : instanceMethods().entrySet()) {
                    proto.set(e.getKey(), e.getValue(), 0);
                }
                PROTOTYPES.put(g, proto);
            }
            return proto;
        }
    }

    /** A fresh Uint8Array over the bytes, reparented onto Buffer.prototype. */
    private static Object make(final byte[] bytes) {
        final Global g = Global.instance();
        final Object u8ctor = g.get("Uint8Array");
        final Object from = ((ScriptObject)u8ctor).get("from");
        final int[] ints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            ints[i] = bytes[i] & 0xff;
        }
        final Object arr = ScriptRuntime.call(from, u8ctor, new Object[] {g.wrapAsObject(ints)});
        if (arr instanceof ScriptObject so) {
            so.setProto(prototype(g));
        }
        return arr;
    }

    private static boolean isBuffer(final Object x) {
        final Object u = org.monflabs.nashorn.api.scripting.ScriptUtils.unwrap(x);
        if (!(u instanceof ScriptObject so)) {
            return false;
        }
        final ScriptObject bp = prototype(Global.instance());
        for (ScriptObject p = so.getProto(); p != null; p = p.getProto()) {
            if (p == bp) {
                return true;
            }
        }
        return false;
    }

    // ---- byte access on a Buffer/Uint8Array (this may arrive as a ScriptObject or a mirror) ----

    private static int len(final Object o) {
        if (o instanceof ScriptObject so) {
            return JSType.toInt32(so.get("length"));
        }
        if (o instanceof JSObject j) {
            return JSType.toInt32(j.getMember("length"));
        }
        return 0;
    }

    private static int at(final Object o, final int i) {
        if (o instanceof ScriptObject so) {
            return JSType.toInt32(so.get(i)) & 0xff;
        }
        return JSType.toInt32(((JSObject)o).getSlot(i)) & 0xff;
    }

    private static Object memberAt(final Object o, final int i) {
        if (o instanceof ScriptObject so) {
            return so.get(i);
        }
        return ((JSObject)o).getSlot(i);
    }

    private static void put(final Object o, final int i, final int v) {
        if (o instanceof ScriptObject so) {
            so.set(i, v & 0xff, 0);
        } else {
            ((JSObject)o).setSlot(i, v & 0xff);
        }
    }

    private static byte[] bytes(final Object self, final int start, final int end) {
        final byte[] out = new byte[Math.max(0, end - start)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)at(self, start + i);
        }
        return out;
    }

    private static byte[] bytesOf(final Object o) {
        final int n = len(o);
        final byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte)at(o, i);
        }
        return out;
    }

    private static int compareBytes(final Object a, final Object b) {
        final int la = len(a);
        final int lb = len(b);
        final int n = Math.min(la, lb);
        for (int i = 0; i < n; i++) {
            final int x = at(a, i);
            final int y = at(b, i);
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return Integer.compare(la, lb);
    }

    // ---- encodings (Java's own) ----

    private static final Set<String> ENCODINGS = Set.of("utf8", "latin1", "ascii", "utf16le", "hex", "base64", "base64url");

    private static String norm(final String enc) {
        if (enc == null) {
            return "utf8";
        }
        switch (enc.toLowerCase(Locale.ROOT)) {
        case "utf-8": return "utf8";
        case "ucs2": case "ucs-2": case "utf-16le": return "utf16le";
        case "binary": return "latin1";
        default: return enc.toLowerCase(Locale.ROOT);
        }
    }

    private static byte[] encode(final String str, final String enc) {
        switch (norm(enc)) {
        case "hex": return HexFormat.of().parseHex(str.length() % 2 == 0 ? str : str.substring(0, str.length() - 1));
        case "base64": return Base64.getMimeDecoder().decode(str.replaceAll("[^A-Za-z0-9+/=]", ""));
        case "base64url": return Base64.getUrlDecoder().decode(str.replaceAll("=+$", ""));
        case "latin1": return str.getBytes(StandardCharsets.ISO_8859_1);
        case "ascii": return str.getBytes(StandardCharsets.US_ASCII);
        case "utf16le": return str.getBytes(StandardCharsets.UTF_16LE);
        default: return str.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String decode(final byte[] bytes, final String enc) {
        switch (norm(enc)) {
        case "hex": return HexFormat.of().formatHex(bytes);
        case "base64": return Base64.getEncoder().encodeToString(bytes);
        case "base64url": return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        case "latin1": return new String(bytes, StandardCharsets.ISO_8859_1);
        case "ascii": {
            final byte[] masked = bytes.clone();
            for (int i = 0; i < masked.length; i++) {
                masked[i] &= 0x7f;
            }
            return new String(masked, StandardCharsets.US_ASCII);
        }
        case "utf16le": return new String(bytes, StandardCharsets.UTF_16LE);
        default: return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    // ---- small function helpers ----

    @FunctionalInterface
    private interface Static {
        Object apply(Object[] args);
    }

    @FunctionalInterface
    private interface Method {
        Object apply(Object self, Object[] args);
    }

    private static JSObject fn(final String name, final Static impl) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return impl.apply(args);
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:buffer] }";
            }
        };
    }

    private static JSObject method(final String name, final Method impl) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return impl.apply(thiz, args);
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:buffer] }";
            }
        };
    }

    private static JSObject namespace(final Map<String, Object> members) {
        return new AbstractJSObject() {
            @Override
            public Object getMember(final String name) {
                return members.containsKey(name) ? members.get(name) : super.getMember(name);
            }

            @Override
            public boolean hasMember(final String name) {
                return members.containsKey(name);
            }

            @Override
            public Set<String> keySet() {
                return members.keySet();
            }
        };
    }

    private static String str(final Object o) {
        return o == null || o == Undefined.INSTANCE ? "" : ScriptRuntime.safeToString(o);
    }

    private static long toLong(final Object o) {
        return o instanceof Number n ? n.longValue() : (long)JSType.toNumber(o);
    }

    private static RuntimeException typeError(final String message) {
        return new org.monflabs.nashorn.internal.runtime.ECMAException(Global.instance().newTypeError(message), null);
    }

    private static final class Undefined {
        static final Object INSTANCE = ScriptRuntime.UNDEFINED;
        private Undefined() {
        }
    }
}
