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

package org.openjdk.nashorn.internal.objects;

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import org.openjdk.nashorn.api.scripting.JSObject;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Getter;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.lookup.Lookup;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyDescriptor;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayData;
import org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import org.openjdk.nashorn.internal.runtime.linker.NashornGuards;
import org.openjdk.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * ArrayBufferView, es6 class or TypedArray implementation
 */
@ScriptClass("ArrayBufferView")
@SuppressWarnings("this-escape")
public abstract class ArrayBufferView extends ScriptObject {
    private final NativeArrayBuffer buffer;
    private final int byteOffset;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    @Override
    public boolean isIntegerIndexed() {
        return true;
    }

    private ArrayBufferView(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength, final Global global) {
        super($nasgenmap$);

        final int bytesPerElement = bytesPerElement();

        checkConstructorArgs(buffer.getByteLength(), bytesPerElement, byteOffset, elementLength);
        setProto(getPrototype(global));

        this.buffer     = buffer;
        this.byteOffset = byteOffset;

        assert byteOffset % bytesPerElement == 0;
        final int start = byteOffset / bytesPerElement;
        final ByteBuffer newNioBuffer = buffer.getNioBuffer().duplicate().order(ByteOrder.nativeOrder());
        final ArrayData  data         = factory().createArrayData(newNioBuffer, start, start + elementLength);

        setArray(data);
    }

    /**
     * Constructor
     *
     * @param buffer         underlying NativeArrayBuffer
     * @param byteOffset     byte offset for buffer
     * @param elementLength  element length in bytes
     */
    protected ArrayBufferView(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength) {
        this(buffer, byteOffset, elementLength, Global.instance());
    }

    private static void checkConstructorArgs(final int byteLength, final int bytesPerElement, final int byteOffset, final int elementLength) {
        if (byteOffset < 0 || elementLength < 0) {
            throw new RuntimeException("byteOffset or length must not be negative, byteOffset=" + byteOffset + ", elementLength=" + elementLength + ", bytesPerElement=" + bytesPerElement);
        } else if (byteOffset + elementLength * bytesPerElement > byteLength) {
            throw new RuntimeException("byteOffset + byteLength out of range, byteOffset=" + byteOffset + ", elementLength=" + elementLength + ", bytesPerElement=" + bytesPerElement);
        } else if (byteOffset % bytesPerElement != 0) {
            throw new RuntimeException("byteOffset must be a multiple of the element size, byteOffset=" + byteOffset + " bytesPerElement=" + bytesPerElement);
        }
    }

    int bytesPerElement() {
        return factory().bytesPerElement;
    }

    /*
     * ES2015 22.2.3 puts buffer, byteOffset, byteLength and length on
     * %TypedArrayPrototype% as accessors, not on the instance, so they are
     * declared in NativeTypedArray and read the view through these.
     */

    NativeArrayBuffer getArrayBuffer() {
        return buffer;
    }

    /**
     * The bytes this view looks at, positioned and limited to its own window.
     *
     * @return a buffer of its own over the same storage
     */
    java.nio.ByteBuffer viewedBytes() {
        final java.nio.ByteBuffer bytes = buffer.getNioBuffer().duplicate();
        bytes.position(byteOffset).limit(byteOffset + getViewByteLength());
        return bytes.slice().order(java.nio.ByteOrder.nativeOrder());
    }

    /** @return how wide one element is */
    int elementWidth() {
        return bytesPerElement();
    }

    int getViewByteOffset() {
        return isDetached() ? 0 : byteOffset;
    }

    int getViewByteLength() {
        return isDetached() ? 0 : ((TypedArrayData<?>)getArray()).getElementLength() * bytesPerElement();
    }

    int getElementLength() {
        return isDetached() ? 0 : elementLength();
    }

    /** ES2015 24.1.1.2: a view over a detached buffer has nothing to look at. */
    boolean isDetached() {
        return buffer.isDetached();
    }

    @Override
    public final Object getLength() {
        return elementLength();
    }

    private int elementLength() {
        // preventExtensions wraps the data in a filter, and what holds the
        // elements is what it wraps
        return ((TypedArrayData<?>)getArray().getUnderlyingData()).getElementLength();
    }

    /**
     * Factory class for byte ArrayBufferViews
     */
    protected static abstract class Factory {
        final int bytesPerElement;
        final int maxElementLength;

        /**
         * Constructor
         *
         * @param bytesPerElement number of bytes per element for this buffer
         */
        public Factory(final int bytesPerElement) {
            this.bytesPerElement  = bytesPerElement;
            this.maxElementLength = Integer.MAX_VALUE / bytesPerElement;
        }

        /**
         * Factory method
         *
         * @param elementLength number of elements
         * @return new ArrayBufferView
         */
        public final ArrayBufferView construct(final int elementLength) {
            if (elementLength > maxElementLength) {
                throw rangeError("inappropriate.array.buffer.length", JSType.toString(elementLength));
            }
            return construct(new NativeArrayBuffer(elementLength * bytesPerElement), 0, elementLength);
        }

        /**
         * Factory method
         *
         * @param buffer         underlying buffer
         * @param byteOffset     byte offset
         * @param elementLength  number of elements
         *
         * @return new ArrayBufferView
         */
        public abstract ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength);

        /**
         * Factory method for array data
         *
         * @param nb    underlying native buffer
         * @param start start element
         * @param end   end element
         *
         * @return      new array data
         */
        public abstract TypedArrayData<?> createArrayData(final ByteBuffer nb, final int start, final int end);

        /**
         * Get the class name for this type of buffer
         *
         * @return class name
         */
        public abstract String getClassName();
    }

    /**
     * Get the factor for this kind of buffer
     * @return Factory
     */
    protected abstract Factory factory();

    /**
     * Get the prototype for this ArrayBufferView
     * @param global global instance
     * @return prototype
     */
    protected abstract ScriptObject getPrototype(final Global global);

    @Override
    public final String getClassName() {
        return factory().getClassName();
    }

    /**
     * Check if this array contains floats
     * @return true if float array (or double)
     */
    protected boolean isFloatArray() {
        return false;
    }

    /**
     * Inheritable constructor implementation
     *
     * @param newObj   is this a new constructor
     * @param args     arguments
     * @param factory  factory
     *
     * @return new ArrayBufferView
     */
    protected static ArrayBufferView constructorImpl(final boolean newObj, final Object[] args, final Factory factory) {
        if (!newObj) {
            // ES2015 22.2.4: a typed array constructor is not callable
            throw typeError("constructor.requires.new", factory.getClassName());
        }

        final Object arg0 = args.length != 0 ? args[0] : ScriptRuntime.UNDEFINED;

        if (arg0 instanceof NativeArrayBuffer buffer) {
            return fromBuffer(buffer, args, factory);
        }
        if (arg0 instanceof ArrayBufferView source) {
            return fromTypedArray(source, factory);
        }
        if (arg0 instanceof ScriptObject || arg0 instanceof JSObject) {
            return fromObject(arg0, factory);
        }
        // ES2015 22.2.4.2: anything else is a length, and ToIndex rejects a
        // negative or fractional one rather than rounding it
        return factory.construct(toIndex(arg0));
    }

    /**
     * ES2015 22.2.4.5, a view over a buffer somebody else owns.
     *
     * The order is the specification's, and is checked: both arguments are
     * converted - which is script-visible, and can detach the buffer - before
     * the buffer is asked whether it is still there.
     */
    private static ArrayBufferView fromBuffer(final NativeArrayBuffer buffer, final Object[] args, final Factory factory) {
        final int elementSize = factory.bytesPerElement;

        final long offset = toIndex(args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED);
        if (offset % elementSize != 0) {
            throw rangeError("byteoffset.not.multiple.of.element.size",
                    JSType.toString((double)offset), JSType.toString(elementSize));
        }

        final Object requested = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;
        final long length = requested == ScriptRuntime.UNDEFINED ? 0 : toIndex(requested);

        if (buffer.isDetached()) {
            throw typeError("detached.array.buffer");
        }

        final long byteLength = buffer.getByteLength();
        final long newByteLength;
        if (requested == ScriptRuntime.UNDEFINED) {
            if (byteLength % elementSize != 0) {
                throw rangeError("bytelength.not.multiple.of.element.size",
                        JSType.toString((double)byteLength), JSType.toString(elementSize));
            }
            newByteLength = byteLength - offset;
            if (newByteLength < 0) {
                throw rangeError("typed.array.out.of.range", JSType.toString((double)offset));
            }
        } else {
            newByteLength = length * elementSize;
            if (offset + newByteLength > byteLength) {
                throw rangeError("typed.array.out.of.range", JSType.toString((double)offset));
            }
        }

        return factory.construct(buffer, (int)offset, (int)(newByteLength / elementSize));
    }

    /** ES2015 22.2.4.3, a copy of another typed array, converted element by element. */
    private static ArrayBufferView fromTypedArray(final ArrayBufferView source, final Factory factory) {
        if (source.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        final int length = source.elementLength();
        final ArrayBufferView dest = factory.construct(length);
        for (int i = 0; i < length; i++) {
            dest.set(i, source.get(i), 0);
        }
        return dest;
    }

    /**
     * ES2015 22.2.4.4, from anything else that is an object.
     *
     * One that is iterable is drained through its iterator; one that is not is
     * read as an array-like - its length, and then its elements, by ordinary
     * property reads - which is what makes a plain {length: 2, 0: x, 1: y} work.
     */
    private static ArrayBufferView fromObject(final Object object, final Factory factory) {
        if (isIterable(object)) {
            final List<Object> values = new ArrayList<>();
            final Iterator<?> iterator = (Iterator<?>)ScriptRuntime.GET_ITERATOR(object);
            while (iterator.hasNext()) {
                values.add(iterator.next());
            }
            return filled(factory, values);
        }

        final ScriptObject source = (ScriptObject)object;
        final double length = toLength(source.get("length"));
        if (length > Integer.MAX_VALUE) {
            throw rangeError("inappropriate.array.buffer.length", JSType.toString(length));
        }

        if (source instanceof NativeArray) {
            // The array is standing in for its own iterator, and draining an
            // iterator collects every value before any of them is converted -
            // which a conversion that empties the array can tell apart from
            // reading each element just before converting it.
            final List<Object> values = new ArrayList<>((int)length);
            for (int i = 0; i < length; i++) {
                values.add(source.get(i));
            }
            return filled(factory, values);
        }

        // 22.2.4.4 step 8: a plain array-like is read one element at a time,
        // and each is stored before the next is read
        final ArrayBufferView dest = factory.construct((int)length);
        for (int i = 0; i < length; i++) {
            dest.set(i, source.get(i), 0);
        }
        return dest;
    }

    private static ArrayBufferView filled(final Factory factory, final List<Object> values) {
        final ArrayBufferView dest = factory.construct(values.size());
        for (int i = 0; i < values.size(); i++) {
            dest.set(i, values.get(i), 0);
        }
        return dest;
    }

    /**
     * ES2015 7.1.15 ToLength, in double so that a length beyond an int is seen
     * for what it is rather than wrapped.
     */
    private static double toLength(final Object value) {
        final double number = JSType.toNumber(value);
        if (Double.isNaN(number) || number <= 0) {
            return 0;
        }
        final double integer = Math.floor(number);
        return Math.min(integer, 9007199254740991d);
    }

    /**
     * Whether 22.2.4.4 step 4 finds an iterator worth using.
     *
     * An ordinary array is read as an array-like even though it is iterable:
     * iterating one and reading it are the same sequence of property reads, and
     * reading is the cheaper of the two by a wide margin. That holds only while
     * nobody has replaced the array iterator, which is what the guard asks.
     */
    private static boolean isIterable(final Object object) {
        if (object instanceof NativeArray && Global.isBuiltinArrayPrototypeIterator()) {
            return false;
        }
        if (!(object instanceof ScriptObject source)) {
            // a foreign object: let the iterator protocol decide
            return true;
        }
        final Object iterator = source.get(NativeSymbol.iterator);
        return iterator != ScriptRuntime.UNDEFINED && iterator != null;
    }

    /**
     * ES2015 7.1.17 ToIndex: a length or an offset, which is a non-negative
     * integer and nothing else. Undefined is zero; anything that is not an
     * integer in range is a RangeError rather than something rounded.
     *
     * @param value the argument as written
     * @return the index it denotes
     */
    /**
     * ES2015 7.1.17 ToIndex, which reaches to 2^53-1: whether anything that
     * long can be allocated is a separate question, asked where the allocation
     * happens.
     *
     * @param value the argument as written
     * @return the index
     */
    static long toIndexLong(final Object value) {
        if (value == ScriptRuntime.UNDEFINED) {
            return 0;
        }
        final double number = JSType.toNumber(value);
        final double integer = Double.isNaN(number) ? 0
                : number < 0 ? Math.ceil(number) : Math.floor(number);
        if (integer < 0 || integer > 9007199254740991d) {
            throw rangeError("not.an.index", JSType.toString(value));
        }
        return (long)integer;
    }

    static int toIndex(final Object value) {
        if (value == ScriptRuntime.UNDEFINED) {
            return 0;
        }
        // JSType.toInteger answers an int, which is exactly the clamping this
        // has to catch rather than perform, so ToInteger is done in double
        final double number = JSType.toNumber(value);
        final double integer = Double.isNaN(number) ? 0
                : number < 0 ? Math.ceil(number) : Math.floor(number);
        if (integer < 0 || integer > Integer.MAX_VALUE) {
            // Beyond an int nothing can be allocated anyway, so the two reasons
            // a value is out of range - negative, and too large to be a length -
            // are the same answer here.
            throw rangeError("not.an.index", JSType.toString(value));
        }
        return (int)integer;
    }

    /**
     * ES2015 22.2.3.5.1 TypedArraySpeciesCreate: what the operations deriving
     * one typed array from another build.
     *
     * Unlike ArraySpeciesCreate there is no shortcut for the ordinary case. A
     * typed array's constructor is reached through its prototype, and an
     * instance may carry a constructor of its own, so the only way to know that
     * the default applies is to look - and next to copying every element, two
     * property reads are not what these methods cost.
     *
     * @param exemplar the array being derived from
     * @param length   how long the new one is to be
     * @return the array to fill in
     */
    static ArrayBufferView speciesCreate(final ArrayBufferView exemplar, final int length) {
        final ScriptFunction species = speciesConstructor(exemplar);
        if (species == null) {
            return exemplar.factory().construct(length);
        }
        return typedArrayCreate(ScriptRuntime.construct(species, (double)length), length);
    }

    /**
     * The species constructor to derive from, or null for the default one.
     *
     * ES2015 7.3.20 SpeciesConstructor: the constructor property, then its
     * @@species; either being absent means the default, and anything present
     * that is not a constructor is a TypeError.
     */
    private static ScriptFunction speciesConstructor(final ArrayBufferView exemplar) {
        final Object constructor = exemplar.get("constructor");
        if (constructor == ScriptRuntime.UNDEFINED) {
            return null;
        }
        if (!(constructor instanceof ScriptObject ctor)) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        final Object species = ctor.get(NativeSymbol.species);
        if (species == ScriptRuntime.UNDEFINED || species == null) {
            return null;
        }
        if (!(species instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(species));
        }
        return function;
    }

    /**
     * ES2015 22.2.4.6 TypedArrayCreate: what a species constructor hands back
     * has to be a usable typed array, and one long enough for what was asked.
     */
    static ArrayBufferView typedArrayCreate(final Object created, final int length) {
        if (!(created instanceof ArrayBufferView result)) {
            throw typeError("not.a.typed.array", ScriptRuntime.safeToString(created));
        }
        if (result.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        if (length >= 0 && result.elementLength() < length) {
            throw typeError("typed.array.too.short", JSType.toString(length));
        }
        return result;
    }

    /**
     * ES2015 22.2.3.26 %TypedArray%.prototype.subarray - another view over the
     * same buffer, so a species constructor is given the buffer rather than a
     * length.
     *
     * There is no detachment check: subarray is one of the few methods that does
     * not begin with ValidateTypedArray.
     */
    protected static ScriptObject subarrayImpl(final Object self, final Object begin0, final Object end0) {
        final ArrayBufferView source = (ArrayBufferView)self;
        final int bytesPerElement = source.bytesPerElement();
        final int elementLength = source.getElementLength();

        final int begin = relativeIndex(begin0, elementLength, 0);
        final int end = relativeIndex(end0, elementLength, elementLength);
        final int length = Math.max(end - begin, 0);
        final int byteOffset = begin * bytesPerElement + source.byteOffset;

        assert source.byteOffset % bytesPerElement == 0;

        final ScriptFunction species = speciesConstructor(source);
        if (species == null) {
            return source.factory().construct(source.buffer, byteOffset, length);
        }
        return typedArrayCreate(
                ScriptRuntime.construct(species, source.buffer, (double)byteOffset, (double)length), -1);
    }

    /**
     * An argument that indexes from either end, as slice, subarray, fill and
     * copyWithin all take: ToInteger, negative counting back from the length,
     * clamped to it.
     *
     * @param value      the argument as written
     * @param length     what it is relative to
     * @param ifUndefined what an absent argument means
     * @return an index in 0..length
     */
    static int relativeIndex(final Object value, final int length, final int ifUndefined) {
        if (value == ScriptRuntime.UNDEFINED) {
            return ifUndefined;
        }
        final double number = JSType.toNumber(value);
        final double integer = Double.isNaN(number) ? 0
                : number < 0 ? Math.ceil(number) : Math.floor(number);
        if (integer < 0) {
            return (int)Math.max(length + integer, 0);
        }
        return (int)Math.min(integer, length);
    }

    /**
     * Inheritable implementation of set, if no efficient implementation is available
     *
     * @param self     ArrayBufferView instance
     * @param array    array
     * @param offset0  array offset
     *
     * @return result of setter
     */
    protected static Object setImpl(final Object self, final Object array, final Object offset0) {
        final ArrayBufferView dest = (ArrayBufferView)self;

        // ES2015 22.2.3.22.1 and .2 agree on their opening: the offset is
        // converted first, and only then is the target buffer asked whether it
        // is still there
        final double asNumber = JSType.toNumber(offset0);
        final double offset = Double.isNaN(asNumber) ? 0
                : asNumber < 0 ? Math.ceil(asNumber) : Math.floor(asNumber);
        if (offset < 0) {
            throw rangeError("typed.array.offset.out.of.range", JSType.toString(offset0));
        }
        if (dest.isDetached()) {
            throw typeError("detached.array.buffer");
        }

        final int targetLength = dest.elementLength();

        if (array instanceof ArrayBufferView source) {
            if (source.isDetached()) {
                throw typeError("detached.array.buffer");
            }
            final int length = source.elementLength();
            if (length + offset > targetLength) {
                throw rangeError("typed.array.offset.out.of.range", JSType.toString(offset0));
            }
            final int at = (int)offset;

            if (source.getClass() == dest.getClass() && source.buffer != dest.buffer) {
                // Same element type and separate storage: nothing to convert and
                // nothing to overlap, so the elements go across as themselves
                // rather than as boxed numbers. This is the common copy.
                if (dest.isFloatArray()) {
                    for (int i = 0; i < length; i++) {
                        dest.set(at + i, source.getDouble(i, INVALID_PROGRAM_POINT), 0);
                    }
                } else {
                    for (int i = 0; i < length; i++) {
                        dest.set(at + i, source.getInt(i, INVALID_PROGRAM_POINT), 0);
                    }
                }
                return ScriptRuntime.UNDEFINED;
            }

            // The two views can be over the same buffer, and the ranges can
            // overlap, so the source is read out before any of it is written
            final Object[] values = new Object[length];
            for (int i = 0; i < length; i++) {
                values[i] = source.get(i);
            }
            for (int i = 0; i < length; i++) {
                dest.set(at + i, values[i], 0);
            }
            return ScriptRuntime.UNDEFINED;
        }

        // 22.2.3.22.1: anything else is read as an array-like, whatever it is -
        // its length, and then its elements, by ordinary property reads
        if (!(JSType.toScriptObject(array) instanceof ScriptObject source)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(array));
        }
        final long length = JSType.toUint32(source.get("length"));
        if (length + offset > targetLength) {
            throw rangeError("typed.array.offset.out.of.range", JSType.toString(offset0));
        }
        for (int i = 0; i < length; i++) {
            // ToNumber first, because reading the source can detach the target
            final Object value = JSType.toNumber(source.get(i));
            if (dest.isDetached()) {
                throw typeError("detached.array.buffer");
            }
            dest.set((int)offset + i, value, 0);
        }

        return ScriptRuntime.UNDEFINED;
    }

    private static void copyElements(final ArrayBufferView dest, final int length, final ScriptObject source, final int offset) {
        if (!dest.isFloatArray()) {
            for (int i = 0, j = offset; i < length; i++, j++) {
                dest.set(j, source.getInt(i, INVALID_PROGRAM_POINT), 0);
            }
        } else {
            for (int i = 0, j = offset; i < length; i++, j++) {
                dest.set(j, source.getDouble(i, INVALID_PROGRAM_POINT), 0);
            }
        }
    }

    private static int lengthToInt(final long length) {
        if (length > Integer.MAX_VALUE || length < 0) {
            throw rangeError("inappropriate.array.buffer.length", JSType.toString(length));
        }
        return (int)(length & Integer.MAX_VALUE);
    }

    /**
     * Implementation of subarray if no efficient override exists
     *
     * @param self    ArrayBufferView instance
     * @param begin0  begin index
     * @param end0    end index
     *
     * @return sub array
     */

    /*
     * ES2015 9.4.5: a typed array is an integer-indexed exotic object, and what
     * makes it exotic is how it answers for a key that looks like a number. Such
     * a key is its own business whether or not it names an element: it is never
     * an ordinary property, never reaches the prototype chain, and cannot be
     * turned into one by defineProperty. A key that does not look like a number
     * is ordinary in every way, which is why each of these falls through.
     */

    /**
     * ES2015 7.1.16 CanonicalNumericIndexString: the number a key denotes, when
     * the key is exactly what ToString makes of that number.
     *
     * "1.1", "-0" and "NaN" are all canonical and none of them is a valid index;
     * "1e2" and " 1" are not canonical, and are ordinary property names.
     *
     * @return the number, or null if the key is an ordinary name
     */
    private static Double canonicalNumericIndex(final Object key) {
        if (!(key instanceof String name)) {
            return null;
        }
        if (name.isEmpty()) {
            return null;
        }
        final char first = name.charAt(0);
        if ((first < '0' || first > '9') && first != '-' && first != 'N' && first != 'I') {
            // nothing ToString ever produces starts with anything else, and this
            // is on the path of every named property read
            return null;
        }
        if ("-0".equals(name)) {
            return -0.0;
        }
        final double number = JSType.toNumber(name);
        return JSType.toString(number).equals(name) ? number : null;
    }

    /** Whether a key names an element of this array: canonical, integral, in range. */
    private boolean isElementIndex(final Double index) {
        if (index == null) {
            return false;
        }
        final double value = index;
        if (Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0)) {
            return false;
        }
        return value == Math.floor(value) && !Double.isInfinite(value)
                && value >= 0 && value < getElementLength();
    }

    @Override
    public Object get(final Object key) {
        final Double index = canonicalNumericIndex(key);
        if (index != null && !isElementIndex(index)) {
            // 9.4.5.4 step 3.c: undefined, rather than whatever the prototype
            // chain has under that name
            return ScriptRuntime.UNDEFINED;
        }
        return super.get(key);
    }

    @Override
    public boolean has(final Object key) {
        final Double index = canonicalNumericIndex(key);
        if (index != null) {
            return isElementIndex(index);
        }
        return super.has(key);
    }

    /**
     * Whether a write to this key is one 9.4.5.5 drops on the floor.
     *
     * Out of range, the write is simply not made: it does not become an
     * ordinary property, and it is not an error either. An integer key that is
     * out of range the array data already handles; what has to be caught here
     * is a key that only looks like one - "-0", "1.5", "NaN" - which would
     * otherwise be taken for an ordinary property name.
     */
    private boolean dropWrite(final Object key) {
        final Double index = canonicalNumericIndex(key);
        return index != null && !isElementIndex(index);
    }

    private boolean dropWrite(final double key) {
        // An index that arrives as a number is already canonical, so the string
        // it would make of itself does not have to be made: only the range
        // matters. This is every element write an internal loop performs.
        if (key >= 0 && key < getElementLength() && key == Math.floor(key)
                && Double.doubleToRawLongBits(key) != Double.doubleToRawLongBits(-0.0)) {
            return false;
        }
        return dropWrite(JSType.toString(key));
    }

    private boolean dropWrite(final int key) {
        return key < 0 || key >= getElementLength();
    }

    @Override
    public void set(final Object key, final Object value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    /**
     * ES2021 10.4.5.5 [[Set]]: a canonical numeric index is the array's own
     * business whoever is writing through it. Written on the array itself it is
     * an element write; written with another receiver it is dropped and reported
     * as done, so a name that only looks like an index never reaches a property
     * of the receiver's, and the value is never even converted.
     */
    @Override
    public boolean setWithReceiver(final Object key, final Object value, final Object receiver) {
        final Double index = canonicalNumericIndex(key);
        if (index != null) {
            if (receiver == this) {
                set(key, value, 0);
                return true;
            }
            if (!isElementIndex(index)) {
                return true;
            }
        }
        return super.setWithReceiver(key, value, receiver);
    }

    @Override
    public void set(final Object key, final int value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final Object key, final double value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final double key, final Object value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final double key, final int value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final double key, final double value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final int key, final Object value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final int key, final int value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public void set(final int key, final double value, final int callSiteFlags) {
        if (!dropWrite(key)) {
            super.set(key, value, callSiteFlags);
        }
    }

    @Override
    public boolean defineOwnProperty(final Object key, final Object propertyDesc, final boolean reject) {
        final Double index = canonicalNumericIndex(key);
        if (index == null) {
            return super.defineOwnProperty(key, propertyDesc, reject);
        }

        // 9.4.5.3: an element can be redefined, but only as the kind of property
        // it already is - a writable, enumerable, configurable data property
        final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);
        final boolean acceptable = isElementIndex(index)
                && desc.type() != PropertyDescriptor.ACCESSOR
                && !(desc.has(PropertyDescriptor.CONFIGURABLE) && !desc.isConfigurable())
                && !(desc.has(PropertyDescriptor.ENUMERABLE) && !desc.isEnumerable())
                && !(desc.has(PropertyDescriptor.WRITABLE) && !desc.isWritable());
        if (!acceptable) {
            if (reject) {
                throw typeError("cant.redefine.property", JSType.toString(key), ScriptRuntime.safeToString(this));
            }
            return false;
        }
        if (desc.has(PropertyDescriptor.VALUE)) {
            super.set(key, desc.getValue(), 0);
        }
        return true;
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final Double index = canonicalNumericIndex(key);
        if (index != null) {
            // an element cannot be deleted; anything else numeric was never there
            return !isElementIndex(index);
        }
        return super.delete(key, strict);
    }

    @Override
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        // A constant key is linked by name rather than going through set, so the
        // same rule has to be applied at link time: "sample.NaN = 1" writes
        // nothing, where "sample.foo = 1" is an ordinary property.
        if (dropWrite(NashornCallSiteDescriptor.getOperand(desc))) {
            return new GuardedInvocation(Lookup.EMPTY_SETTER,
                    NashornGuards.getMapGuard(getMap(), true));
        }
        return super.findSetMethod(desc, request);
    }

    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastGetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findGetIndexMethod(desc, request);
    }

    @Override
    protected GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastSetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findSetIndexMethod(desc, request);
    }
}
