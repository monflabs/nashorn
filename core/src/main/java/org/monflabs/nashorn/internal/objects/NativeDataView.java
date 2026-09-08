/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * <p>
 * DataView builtin constructor. Based on the specification here:
 * http://www.khronos.org/registry/typedarray/specs/latest/#8
 * </p>
 * <p>
 * An ArrayBuffer is a useful object for representing an arbitrary chunk of data.
 * In many cases, such data will be read from disk or from the network, and will
 * not follow the alignment restrictions that are imposed on the typed array views
 * described earlier. In addition, the data will often be heterogeneous in nature
 * and have a defined byte order. The DataView view provides a low-level interface
 * for reading such data from and writing it to an ArrayBuffer.
 * </p>
 * <p>
 * Regardless of the host computer's endianness, DataView reads or writes values
 * to or from main memory with a specified endianness: big or little.
 * </p>
 */
@ScriptClass("DataView")
public class NativeDataView extends ScriptObject implements NativeArrayBuffer.ResizeListener {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /*
     * ES2015 24.2.4.1-3 put buffer, byteOffset and byteLength on
     * DataView.prototype as accessors, not on the instance, so they are fields
     * here and are read through the getters below.
     */

    /** Underlying ArrayBuffer storage object */
    private final Object buffer;

    /** The offset in bytes from the start of the ArrayBuffer */
    private final int byteOffset;

    /** The number of bytes from the offset that this DataView will reference */
    private int byteLength;

    // underlying ByteBuffer
    private ByteBuffer buf;

    /** ES2024: a view with no explicit length over a resizable buffer tracks it. */
    private boolean autoLength;

    /** The fixed length, for a non-auto view whose out-of-bounds state may flip. */
    private final int fixedLength;

    private NativeDataView(final NativeArrayBuffer arrBuf, final int offset, final int length) {
        this(arrBuf, bufferFrom(arrBuf, offset, length), offset, length);
    }

    private NativeDataView(final NativeArrayBuffer arrBuf, final ByteBuffer buf, final int offset, final int length) {
        super(Global.instance().getDataViewPrototype(), $nasgenmap$);
        this.buffer      = arrBuf;
        this.byteOffset  = offset;
        this.byteLength  = length;
        this.fixedLength = length;
        this.buf         = buf;
        // a resizable buffer rebuilds its views on resize; a no-op for a fixed one
        arrBuf.registerView(this);
    }

    /** Whether this view no longer fits its (resizable) buffer, after a shrink. */
    boolean isOutOfBounds() {
        if (!(buffer instanceof NativeArrayBuffer arrayBuffer) || !arrayBuffer.isResizable() || arrayBuffer.isDetached()) {
            return false;
        }
        final int bufferLength = arrayBuffer.getByteLength();
        if (byteOffset > bufferLength) {
            return true;
        }
        return !autoLength && byteOffset + fixedLength > bufferLength;
    }

    /** The buffer resized: recompute this view's length and rebuild its window. */
    @Override
    public void bufferResized() {
        final NativeArrayBuffer arrayBuffer = (NativeArrayBuffer) buffer;
        final int bufferLength = arrayBuffer.getByteLength();
        final int newLength;
        if (byteOffset > bufferLength) {
            newLength = 0;
        } else if (autoLength) {
            newLength = bufferLength - byteOffset;
        } else {
            newLength = byteOffset + fixedLength <= bufferLength ? fixedLength : 0;
        }
        this.byteLength = newLength;
        this.buf = arrayBuffer.getBuffer(byteOffset, newLength);
    }

    /**
     * Create a new DataView object using the passed ArrayBuffer for its
     * storage. Optional byteOffset and byteLength can be used to limit the
     * section of the buffer referenced. The byteOffset indicates the offset in
     * bytes from the start of the ArrayBuffer, and the byteLength is the number
     * of bytes from the offset that this DataView will reference. If both
     * byteOffset and byteLength are omitted, the DataView spans the entire
     * ArrayBuffer range. If the byteLength is omitted, the DataView extends from
     * the given byteOffset until the end of the ArrayBuffer.
     *
     * If the given byteOffset and byteLength references an area beyond the end
     * of the ArrayBuffer an exception is raised.

     * @param newObj if this constructor was invoked with 'new' or not
     * @param self   constructor function object
     * @param args   arguments to the constructor
     * @return newly constructed DataView object
     */
    @Constructor(arity = 1)
    public static NativeDataView constructor(final boolean newObj, final Object self, final Object... args) {
        if (!newObj) {
            throw typeError("constructor.requires.new", "DataView");
        }
        if (args.length == 0 || !(args[0] instanceof NativeArrayBuffer arrayBuffer)) {
            throw typeError("not.an.arraybuffer.in.dataview");
        }

        // ES2015 24.2.2.1: both arguments go through ToIndex, and both
        // conversions happen before the buffer is asked anything - either of
        // them can detach it
        final int offset = ArrayBufferView.toIndex(args.length > 1 ? args[1] : UNDEFINED);
        final Object requested = args.length > 2 ? args[2] : UNDEFINED;
        final int requestedLength = requested == UNDEFINED ? 0 : ArrayBufferView.toIndex(requested);

        if (arrayBuffer.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        final int bufferLength = arrayBuffer.getByteLength();
        if (offset > bufferLength) {
            throw rangeError("dataview.constructor.offset");
        }

        // 24.2.2.1 step 12 reads new.target's prototype once the offset has been
        // found to fit; reading it can run user code that detaches or - ES2024 -
        // resizes the buffer, so step 13 re-checks and the length is measured
        // against the buffer as it stands afterwards.
        final ScriptObject prototype = Global.instance().takeNewTargetPrototype();
        if (arrayBuffer.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        final int currentLength = arrayBuffer.getByteLength();
        if (offset > currentLength) {
            throw rangeError("dataview.constructor.offset");
        }
        final int length;
        if (requested == UNDEFINED) {
            length = currentLength - offset;
        } else {
            length = requestedLength;
            if (offset + length > currentLength) {
                throw rangeError("dataview.constructor.offset");
            }
        }

        final NativeDataView view = new NativeDataView(arrayBuffer, offset, length);
        // ES2024: no explicit length over a resizable buffer means length-tracking
        if (requested == UNDEFINED && arrayBuffer.isResizable()) {
            view.autoLength = true;
        }
        if (prototype != null) {
            view.setInitialProto(prototype);
        }
        return view;
    }

    /**
     * ES2015 24.2.4.1 get DataView.prototype.buffer.
     *
     * @param self self reference
     * @return the buffer the view is over
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object buffer(final Object self) {
        return described(self).buffer;
    }

    /**
     * ES2015 24.2.4.2 get DataView.prototype.byteLength.
     *
     * @param self self reference
     * @return the view's length in bytes
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteLength(final Object self) {
        final NativeDataView view = checkSelf(self);
        if (view.isOutOfBounds()) {
            throw typeError("detached.array.buffer");
        }
        return view.byteLength;
    }

    /**
     * ES2015 24.2.4.3 get DataView.prototype.byteOffset.
     *
     * @param self self reference
     * @return where the view starts in its buffer
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteOffset(final Object self) {
        final NativeDataView view = checkSelf(self);
        if (view.isOutOfBounds()) {
            throw typeError("detached.array.buffer");
        }
        return view.byteOffset;
    }

    /**
     * ES2015 24.2.4.21 DataView.prototype [ @@toStringTag ].
     *
     * Unlike %TypedArray%'s, which is an accessor that answers for the receiver,
     * this one is a plain string on the prototype.
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "DataView";

    // ES2015 24.2.1.1 GetViewValue and 24.2.1.2 SetViewValue: the receiver is
    // checked first, then the index is converted, then - for a set - the value,
    // then the endianness flag; only after all of that is the buffer asked
    // whether it is still there, and the index whether it is in range. Every
    // step of that is script-visible, and the suite checks the order of all of it.

    /**
     * ES2015 24.2.4 DataView.prototype.getInt8.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int getInt8(final Object self, final Object byteOffset) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final ByteBuffer buffer = viewed(view, index, 1);
        return buffer.get(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setInt8.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setInt8(final Object self, final Object byteOffset, final Object value) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final int number = JSType.toInt32(value);
        viewed(view, index, 1).put(index, (byte)(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getUint8.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int getUint8(final Object self, final Object byteOffset) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final ByteBuffer buffer = viewed(view, index, 1);
        return 0xFF & buffer.get(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setUint8.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setUint8(final Object self, final Object byteOffset, final Object value) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final int number = JSType.toInt32(value);
        viewed(view, index, 1).put(index, (byte)(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getInt16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int getInt16(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 2).order(order(little));
        return buffer.getShort(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setInt16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setInt16(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final int number = JSType.toInt32(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 2).order(order(little)).putShort(index, (short)(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getUint16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int getUint16(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 2).order(order(little));
        return 0xFFFF & buffer.getShort(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setUint16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setUint16(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final int number = JSType.toInt32(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 2).order(order(little)).putShort(index, (short)(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getInt32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int getInt32(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 4).order(order(little));
        return buffer.getInt(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setInt32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setInt32(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final int number = JSType.toInt32(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 4).order(order(little)).putInt(index, number);
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getUint32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double getUint32(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 4).order(order(little));
        return JSType.toUint32(buffer.getInt(index));
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setUint32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setUint32(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final double number = JSType.toNumber(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 4).order(order(little)).putInt(index, (int)JSType.toUint32(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getFloat32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double getFloat32(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 4).order(order(little));
        return buffer.getFloat(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setFloat32.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setFloat32(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final double number = JSType.toNumber(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 4).order(order(little)).putFloat(index, (float)(number));
        return UNDEFINED;
    }

    /**
     * ES2025 25.3.4 DataView.prototype.getFloat16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the half-precision value at that offset, widened to a Number
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double getFloat16(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 2).order(order(little));
        return Float.float16ToFloat(buffer.getShort(index));
    }

    /**
     * ES2025 25.3.4 DataView.prototype.setFloat16.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setFloat16(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final double number = JSType.toNumber(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 2).order(order(little)).putShort(index, NativeFloat16Array.doubleToFloat16(number));
        return UNDEFINED;
    }

    /**
     * ES2015 24.2.4 DataView.prototype.getFloat64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the value at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double getFloat64(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final ByteBuffer buffer = viewed(view, index, 8).order(order(little));
        return buffer.getDouble(index);
    }

    /**
     * ES2015 24.2.4 DataView.prototype.setFloat64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setFloat64(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final double number = JSType.toNumber(value);
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 8).order(order(little)).putDouble(index, number);
        return UNDEFINED;
    }

    /**
     * ES2020 24.3.4 DataView.prototype.getBigInt64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the signed 64-bit BigInt at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object getBigInt64(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        return java.math.BigInteger.valueOf(viewed(view, index, 8).order(order(little)).getLong(index));
    }

    /**
     * ES2020 24.3.4 DataView.prototype.getBigUint64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to read from
     * @param littleEndian whether to read in little endian order
     * @return the unsigned 64-bit BigInt at that offset
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object getBigUint64(final Object self, final Object byteOffset, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final boolean little = JSType.toBoolean(littleEndian);
        final long v = viewed(view, index, 8).order(order(little)).getLong(index);
        final java.math.BigInteger signed = java.math.BigInteger.valueOf(v);
        return v >= 0 ? signed : signed.add(java.math.BigInteger.ONE.shiftLeft(64));
    }

    /**
     * ES2020 24.3.4 DataView.prototype.setBigInt64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the BigInt value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setBigInt64(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        // ToBigInt then the low 64 bits (BigInteger.longValue wraps mod 2**64)
        final long number = NativeBigInt.toBigInt(value).longValue();
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 8).order(order(little)).putLong(index, number);
        return UNDEFINED;
    }

    /**
     * ES2020 24.3.4 DataView.prototype.setBigUint64.
     *
     * @param self DataView object
     * @param byteOffset byte offset to write at
     * @param value the BigInt value to write
     * @param littleEndian whether to write in little endian order
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setBigUint64(final Object self, final Object byteOffset, final Object value, final Object littleEndian) {
        final NativeDataView view = described(self);
        final int index = ArrayBufferView.toIndex(byteOffset);
        final long number = NativeBigInt.toBigInt(value).longValue();
        final boolean little = JSType.toBoolean(littleEndian);
        viewed(view, index, 8).order(order(little)).putLong(index, number);
        return UNDEFINED;
    }


    @Override
    public String getClassName() {
        // which is what Object.prototype.toString reports, and agrees with the
        // @@toStringTag above without putting every object's toString through a
        // symbol lookup to find it out
        return "DataView";
    }

    // internals only below this point

    private static ByteBuffer bufferFrom(final NativeArrayBuffer nab, final int offset, final int length) {
        try {
            return nab.getBuffer(offset, length);
        } catch (final IllegalArgumentException iae) {
            throw rangeError(iae, "dataview.constructor.offset");
        }
    }

    /** The receiver as a DataView, without asking whether its buffer is still there. */
    private static NativeDataView described(final Object self) {
        if (self instanceof NativeDataView view) {
            return view;
        }
        throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
    }

    /**
     * The receiver as a usable DataView.
     *
     * ES2015 24.2.4.2 and 24.2.4.3 answer for a detached view by throwing,
     * where a typed array's equivalents answer zero - the two disagree, and
     * both are checked.
     */
    private static NativeDataView checkSelf(final Object self) {
        final NativeDataView view = described(self);
        if (view.buffer instanceof NativeArrayBuffer arrayBuffer && arrayBuffer.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        return view;
    }

    /**
     * The storage to read or write, once the index is known to be in range.
     *
     * @param view  the view, already known to be one
     * @param index where in it, already converted
     * @param size  how many bytes the value takes
     */
    private static ByteBuffer viewed(final NativeDataView view, final int index, final int size) {
        if (view.buffer instanceof NativeArrayBuffer arrayBuffer && arrayBuffer.isDetached()) {
            // the index conversion, and for a set the value conversion, both run
            // script and either can have detached the buffer since the receiver
            // was checked
            throw typeError("detached.array.buffer");
        }
        // ES2024: a view whose range no longer fits a resized buffer is out of
        // bounds - like a detached one, it has nothing to look at
        if (view.isOutOfBounds()) {
            throw typeError("detached.array.buffer");
        }
        if (index + size > view.byteLength) {
            throw rangeError("dataview.offset");
        }
        return view.buf;
    }

    private static ByteOrder order(final boolean littleEndian) {
        return littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }
}
