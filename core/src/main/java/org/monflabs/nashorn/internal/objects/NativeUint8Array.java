/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.specialCall;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.syntaxError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HexFormat;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayData;
import org.monflabs.nashorn.internal.runtime.arrays.TypedArrayData;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * Uint8 array for TypedArray extension
 */
@ScriptClass("Uint8Array")
public final class NativeUint8Array extends ArrayBufferView {

    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 1;

    /** ES2015 22.2.6.1: the element size is on the prototype too. */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.PROTOTYPE, name = "BYTES_PER_ELEMENT")
    public static final int BYTES_PER_ELEMENT_PROTOTYPE = 1;

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new NativeUint8Array(buffer, byteOffset, length);
        }

        @Override
        public Uint8ArrayData createArrayData(final ByteBuffer nb, final int start, final int end) {
            return new Uint8ArrayData(nb, start, end);
        }

        @Override
        public String getClassName() {
            return "Uint8Array";
        }
    };

    private static final class Uint8ArrayData extends TypedArrayData<ByteBuffer> {

        private static final MethodHandle GET_ELEM = specialCall(MethodHandles.lookup(), Uint8ArrayData.class, "getElem", int.class, int.class).methodHandle();
        private static final MethodHandle SET_ELEM = specialCall(MethodHandles.lookup(), Uint8ArrayData.class, "setElem", void.class, int.class, int.class).methodHandle();

        private Uint8ArrayData(final ByteBuffer nb, final int start, final int end) {
            super((nb.position(start).limit(end)).slice(), end - start);
        }

        @Override
        protected MethodHandle getGetElem() {
            return GET_ELEM;
        }

        @Override
        protected MethodHandle getSetElem() {
            return SET_ELEM;
        }

        private int getElem(final int index) {
            try {
                return nb.get(index) & 0xff;
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        private void setElem(final int index, final int elem) {
            try {
                if (index < nb.limit()) {
                    nb.put(index, (byte) elem);
                }
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException();
            }
        }

        @Override
        public boolean isUnsigned() {
            return true;
        }

        @Override
        public Class<?> getElementType() {
            return int.class;
        }

        @Override
        public Class<?> getBoxedElementType() {
            return Integer.class;
        }

        @Override
        public int getInt(final int index) {
            return getElem(index);
        }

        @Override
        public int getIntOptimistic(final int index, final int programPoint) {
            return getElem(index);
        }

        @Override
        public double getDouble(final int index) {
            return getInt(index);
        }

        @Override
        public double getDoubleOptimistic(final int index, final int programPoint) {
            return getElem(index);
        }

        @Override
        public Object getObject(final int index) {
            return getInt(index);
        }

        @Override
        public ArrayData set(final int index, final Object value, final boolean strict) {
            return set(index, JSType.toInt32(value), strict);
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            setElem(index, value);
            return this;
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            return set(index, (int)value, strict);
        }

    }

    /**
     * Constructor
     *
     * @param newObj is this typed array instantiated with the new operator
     * @param self   self reference
     * @param args   args
     *
     * @return new typed array
     */
    // ES2015 22.2.5: a typed array constructor's length is 3 - buffer,
    // byteOffset and length - whichever of its four forms is being used
    @Constructor(arity = 3)
    public static NativeUint8Array constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeUint8Array)constructorImpl(newObj, args, FACTORY);
    }

    NativeUint8Array(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
        super(buffer, byteOffset, length);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getUint8ArrayPrototype();
    }

    // ---- ES2026 Uint8Array <-> base64 / hex ----

    private static final String BASE64_STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** Result of a base64/hex decode: how many input chars were consumed, the bytes, and a deferred error. */
    private static final class DecodeResult {
        private final int read;
        private final byte[] bytes;
        private final RuntimeException error;
        private DecodeResult(final int read, final byte[] bytes, final RuntimeException error) {
            this.read = read;
            this.bytes = bytes;
            this.error = error;
        }
    }

    private static NativeUint8Array validate(final Object self) {
        if (self instanceof NativeUint8Array u) {
            return u;
        }
        throw typeError("not.a.uint8.array", ScriptRuntime.safeToString(self));
    }

    /** The bytes this view currently looks at, throwing if it is detached or out of bounds. */
    private static byte[] currentBytes(final NativeUint8Array ta) {
        if (ta.isDetached() || ta.isOutOfBounds()) {
            throw typeError("not.a.uint8.array", "detached");
        }
        final int len = ta.getElementLength();
        final byte[] out = new byte[len];
        if (len > 0) {
            ta.viewedBytes().get(0, out, 0, len);
        }
        return out;
    }

    private static ScriptObject getOptions(final Object options) {
        if (options == ScriptRuntime.UNDEFINED || options == null) {
            return null;
        }
        if (options instanceof ScriptObject so) {
            return so;
        }
        throw typeError("base64.options.not.object");
    }

    private static String getAlphabet(final ScriptObject opts) {
        if (opts == null) {
            return "base64";
        }
        final Object v = opts.get("alphabet");
        if (v == ScriptRuntime.UNDEFINED) {
            return "base64";
        }
        if (!JSType.isString(v)) {
            throw typeError("base64.bad.alphabet");
        }
        final String a = JSType.toString(v);
        if (!a.equals("base64") && !a.equals("base64url")) {
            throw typeError("base64.bad.alphabet");
        }
        return a;
    }

    private static String getLastChunkHandling(final ScriptObject opts) {
        if (opts == null) {
            return "loose";
        }
        final Object v = opts.get("lastChunkHandling");
        if (v == ScriptRuntime.UNDEFINED) {
            return "loose";
        }
        if (!JSType.isString(v)) {
            throw typeError("base64.bad.lastchunkhandling");
        }
        final String l = JSType.toString(v);
        if (!l.equals("loose") && !l.equals("strict") && !l.equals("stop-before-partial")) {
            throw typeError("base64.bad.lastchunkhandling");
        }
        return l;
    }

    private static boolean isAsciiWhitespace(final char c) {
        return c == 0x09 || c == 0x0A || c == 0x0C || c == 0x0D || c == 0x20;
    }

    private static int skipWhitespace(final String s, int i) {
        final int len = s.length();
        while (i < len && isAsciiWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int base64Value(final char c, final boolean url) {
        // reject the standard chars in url mode and vice versa (spec maps only its own alphabet)
        if (url) {
            if (c == '-') {
                return 62;
            }
            if (c == '_') {
                return 63;
            }
            if (c == '+' || c == '/') {
                return -1;
            }
        } else {
            if (c == '+') {
                return 62;
            }
            if (c == '/') {
                return 63;
            }
            if (c == '-' || c == '_') {
                return -1;
            }
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        return -1;
    }

    /** DecodeBase64Chunk: 2/3/4 six-bit values to 1/2/3 bytes; may reject non-zero padding bits. */
    private static byte[] decodeBase64Chunk(final int[] chunk, final int chunkLength, final boolean throwOnExtraBits) {
        switch (chunkLength) {
        case 2: {
            if (throwOnExtraBits && (chunk[1] & 0x0f) != 0) {
                throw syntaxError("base64.bad.padding");
            }
            return new byte[] { (byte) ((chunk[0] << 2) | (chunk[1] >> 4)) };
        }
        case 3: {
            if (throwOnExtraBits && (chunk[2] & 0x03) != 0) {
                throw syntaxError("base64.bad.padding");
            }
            return new byte[] {
                (byte) ((chunk[0] << 2) | (chunk[1] >> 4)),
                (byte) (((chunk[1] & 0x0f) << 4) | (chunk[2] >> 2))
            };
        }
        default: {
            return new byte[] {
                (byte) ((chunk[0] << 2) | (chunk[1] >> 4)),
                (byte) (((chunk[1] & 0x0f) << 4) | (chunk[2] >> 2)),
                (byte) (((chunk[2] & 0x03) << 6) | chunk[3])
            };
        }
        }
    }

    /** ES2026 FromBase64: decode up to maxLength bytes, deferring any error so a partial write can precede it. */
    private static DecodeResult fromBase64(final String string, final String alphabet,
            final String lastChunkHandling, final long maxLength) {
        final boolean url = alphabet.equals("base64url");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final int[] chunk = new int[4];
        int chunkLength = 0;
        int read = 0;
        int index = 0;
        final int length = string.length();
        if (maxLength == 0) {
            return new DecodeResult(0, new byte[0], null);
        }
        for (;;) {
            index = skipWhitespace(string, index);
            if (index == length) {
                if (chunkLength > 0) {
                    if (lastChunkHandling.equals("stop-before-partial")) {
                        return new DecodeResult(read, bytes.toByteArray(), null);
                    }
                    if (lastChunkHandling.equals("loose")) {
                        if (chunkLength == 1) {
                            return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                        }
                        final byte[] dec = decodeBase64Chunk(chunk, chunkLength, false);
                        bytes.write(dec, 0, dec.length);
                    } else {
                        return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                    }
                }
                return new DecodeResult(length, bytes.toByteArray(), null);
            }
            final char c = string.charAt(index);
            index++;
            if (c == '=') {
                if (chunkLength < 2) {
                    return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                }
                index = skipWhitespace(string, index);
                if (chunkLength == 2) {
                    if (index == length) {
                        if (lastChunkHandling.equals("stop-before-partial")) {
                            return new DecodeResult(read, bytes.toByteArray(), null);
                        }
                        return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                    }
                    if (string.charAt(index) == '=') {
                        index++;
                        index = skipWhitespace(string, index);
                    } else {
                        return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                    }
                }
                if (index < length) {
                    return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.padding"));
                }
                final boolean throwOnExtraBits = lastChunkHandling.equals("strict");
                final byte[] dec;
                try {
                    dec = decodeBase64Chunk(chunk, chunkLength, throwOnExtraBits);
                } catch (final RuntimeException e) {
                    return new DecodeResult(read, bytes.toByteArray(), e);
                }
                bytes.write(dec, 0, dec.length);
                return new DecodeResult(length, bytes.toByteArray(), null);
            }
            final int value = base64Value(c, url);
            if (value < 0) {
                return new DecodeResult(read, bytes.toByteArray(), syntaxError("base64.bad.char"));
            }
            final long remaining = maxLength - bytes.size();
            if ((remaining == 1 && chunkLength == 2) || (remaining == 2 && chunkLength == 3)) {
                // completing this chunk would produce a full 3-byte group that overflows the target
                return new DecodeResult(read, bytes.toByteArray(), null);
            }
            chunk[chunkLength++] = value;
            if (chunkLength == 4) {
                final byte[] dec = decodeBase64Chunk(chunk, 4, false);
                bytes.write(dec, 0, dec.length);
                chunkLength = 0;
                read = index;
                if (bytes.size() >= maxLength) {
                    return new DecodeResult(read, bytes.toByteArray(), null);
                }
            }
        }
    }

    /** ES2026 FromHex: decode up to maxLength bytes from hex pairs, deferring any error. */
    private static DecodeResult fromHex(final String string, final long maxLength) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final int length = string.length();
        int read = 0;
        if (length % 2 != 0) {
            return new DecodeResult(0, new byte[0], syntaxError("hex.invalid"));
        }
        while (read < length) {
            if (bytes.size() >= maxLength) {
                break;
            }
            final int hi = hexValue(string.charAt(read));
            final int lo = hexValue(string.charAt(read + 1));
            if (hi < 0 || lo < 0) {
                return new DecodeResult(read, bytes.toByteArray(), syntaxError("hex.invalid"));
            }
            bytes.write((hi << 4) | lo);
            read += 2;
        }
        return new DecodeResult(read, bytes.toByteArray(), null);
    }

    private static int hexValue(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static NativeUint8Array newFromBytes(final byte[] bytes) {
        final NativeUint8Array ta = (NativeUint8Array) FACTORY.construct(bytes.length);
        if (bytes.length > 0) {
            ta.viewedBytes().put(0, bytes, 0, bytes.length);
        }
        return ta;
    }

    private static ScriptObject readWriteResult(final int read, final int written) {
        final ScriptObject result = Global.instance().newObject();
        result.put("read", (double) read, false);
        result.put("written", (double) written, false);
        return result;
    }

    /**
     * ES2026 Uint8Array.fromBase64 ( string [ , options ] ): a new Uint8Array holding the
     * bytes the base64 string decodes to.
     *
     * @param self    the Uint8Array constructor
     * @param string  the base64 text
     * @param options the {@code {alphabet, lastChunkHandling}} options
     * @return a new Uint8Array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static Object fromBase64(final Object self, final Object string, final Object options) {
        if (!JSType.isString(string)) {
            throw typeError("base64.input.not.string");
        }
        final ScriptObject opts = getOptions(options);
        final String alphabet = getAlphabet(opts);
        final String lastChunkHandling = getLastChunkHandling(opts);
        final DecodeResult r = fromBase64(JSType.toString(string), alphabet, lastChunkHandling, Long.MAX_VALUE);
        if (r.error != null) {
            throw r.error;
        }
        return newFromBytes(r.bytes);
    }

    /**
     * ES2026 Uint8Array.fromHex ( string ): a new Uint8Array holding the bytes the hex string decodes to.
     *
     * @param self   the Uint8Array constructor
     * @param string the hex text
     * @return a new Uint8Array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static Object fromHex(final Object self, final Object string) {
        if (!JSType.isString(string)) {
            throw typeError("base64.input.not.string");
        }
        final DecodeResult r = fromHex(JSType.toString(string), Long.MAX_VALUE);
        if (r.error != null) {
            throw r.error;
        }
        return newFromBytes(r.bytes);
    }

    /**
     * ES2026 Uint8Array.prototype.toBase64 ( [ options ] ): the base64 encoding of this array's bytes.
     *
     * @param self    a Uint8Array
     * @param options the {@code {alphabet, omitPadding}} options
     * @return the base64 string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object toBase64(final Object self, final Object options) {
        final NativeUint8Array ta = validate(self);
        final ScriptObject opts = getOptions(options);
        final String alphabet = getAlphabet(opts);
        boolean omitPadding = false;
        if (opts != null) {
            omitPadding = JSType.toBoolean(opts.get("omitPadding"));
        }
        final byte[] bytes = currentBytes(ta);
        Base64.Encoder enc = alphabet.equals("base64url") ? Base64.getUrlEncoder() : Base64.getEncoder();
        if (omitPadding) {
            enc = enc.withoutPadding();
        }
        return enc.encodeToString(bytes);
    }

    /**
     * ES2026 Uint8Array.prototype.toHex ( ): the lowercase hex encoding of this array's bytes.
     *
     * @param self a Uint8Array
     * @return the hex string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object toHex(final Object self) {
        final NativeUint8Array ta = validate(self);
        final byte[] bytes = currentBytes(ta);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * ES2026 Uint8Array.prototype.setFromBase64 ( string [ , options ] ): decode the base64 string
     * into this array (up to its length), returning {@code {read, written}}.
     *
     * @param self    a Uint8Array
     * @param string  the base64 text
     * @param options the {@code {alphabet, lastChunkHandling}} options
     * @return a {@code {read, written}} result object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setFromBase64(final Object self, final Object string, final Object options) {
        final NativeUint8Array ta = validate(self);
        if (!JSType.isString(string)) {
            throw typeError("base64.input.not.string");
        }
        final ScriptObject opts = getOptions(options);
        final String alphabet = getAlphabet(opts);
        final String lastChunkHandling = getLastChunkHandling(opts);
        if (ta.isDetached() || ta.isOutOfBounds()) {
            throw typeError("not.a.uint8.array", "detached");
        }
        final int len = ta.getElementLength();
        final DecodeResult r = fromBase64(JSType.toString(string), alphabet, lastChunkHandling, len);
        final int written = r.bytes.length;
        if (written > 0) {
            ta.viewedBytes().put(0, r.bytes, 0, written);
        }
        if (r.error != null) {
            throw r.error;
        }
        return readWriteResult(r.read, written);
    }

    /**
     * ES2026 Uint8Array.prototype.setFromHex ( string ): decode the hex string into this array
     * (up to its length), returning {@code {read, written}}.
     *
     * @param self   a Uint8Array
     * @param string the hex text
     * @return a {@code {read, written}} result object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setFromHex(final Object self, final Object string) {
        final NativeUint8Array ta = validate(self);
        if (!JSType.isString(string)) {
            throw typeError("base64.input.not.string");
        }
        if (ta.isDetached() || ta.isOutOfBounds()) {
            throw typeError("not.a.uint8.array", "detached");
        }
        final int len = ta.getElementLength();
        final DecodeResult r = fromHex(JSType.toString(string), len);
        final int written = r.bytes.length;
        if (written > 0) {
            ta.viewedBytes().put(0, r.bytes, 0, written);
        }
        if (r.error != null) {
            throw r.error;
        }
        return readWriteResult(r.read, written);
    }
}
