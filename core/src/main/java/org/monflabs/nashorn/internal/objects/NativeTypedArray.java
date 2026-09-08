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

package org.monflabs.nashorn.internal.objects;

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.ArrayList;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * %TypedArray%, the abstract superclass of the nine typed array constructors
 * (ECMAScript 2015, 22.2.1-22.2.3).
 *
 * It is not reachable by name from a script: the only way to it is
 * {@code Object.getPrototypeOf(Int8Array)}. Everything the nine concrete types
 * share lives here - the methods on %TypedArrayPrototype%, the four accessors
 * describing the view, and {@code from} and {@code of} on the constructor - so
 * that the concrete prototypes hold nothing but {@code constructor} and
 * {@code BYTES_PER_ELEMENT}, which is what the specification says and what
 * conformance tests check with hasOwnProperty.
 *
 * The iteration methods are the ones {@link NativeArray} already implements
 * generically over any array-like script object, and a typed array is one, so
 * they are delegated to rather than written twice. What cannot be delegated is
 * anything that produces a new array - map, filter, slice and subarray have to
 * produce a typed array of the same type, not an Array - and sort, whose default
 * comparison is numeric here and textual there.
 */
@ScriptClass("TypedArray")
public final class NativeTypedArray extends ScriptObject {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeTypedArray() {
        // don't create me!
        throw new UnsupportedOperationException();
    }

    /**
     * ES2015 22.2.1.1: %TypedArray% is not directly constructible, and calling it
     * is a TypeError too. It exists only to be inherited from.
     *
     * @param newObj is this a new object
     * @param self   self reference
     * @param args   arguments
     * @return never returns
     */
    @Constructor(arity = 0)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        throw typeError("cant.instantiate.abstract.typed.array");
    }

    /**
     * ES2015 22.2.2.4 get %TypedArray% [ @@species ].
     *
     * @param self self reference
     * @return the constructor itself, which is what the default species is
     */
    @Getter(where = Where.CONSTRUCTOR, name = "@@species", attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object species(final Object self) {
        return self;
    }

    /**
     * ES2015 22.2.2.1 %TypedArray%.from.
     *
     * @param self    the constructor it was called on
     * @param args    source, optional mapping function and its this
     * @return a new typed array
     */
    @Function(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object from(final Object self, final Object... args) {
        final Object source  = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object mapfn   = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object thisArg = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;

        if (mapfn != ScriptRuntime.UNDEFINED) {
            callable(mapfn);
        }

        // ES2015 22.2.2.1 reads the source, then makes the target, and only then
        // maps: the mapping function runs with the target already in existence,
        // and can do things to it - detach its buffer, for one - that the writes
        // that follow have to live with. Array.from would run it during the
        // read, which is a step too early.
        final ScriptObject values = (ScriptObject)NativeArray.from(ScriptRuntime.UNDEFINED, source,
                ScriptRuntime.UNDEFINED, ScriptRuntime.UNDEFINED);
        final int length = JSType.toInt32(values.getLength());
        final ArrayBufferView target = allocate(self, length);
        for (int i = 0; i < length; i++) {
            final Object value = values.get(i);
            target.set(i, mapfn == ScriptRuntime.UNDEFINED ? value
                    : ScriptRuntime.call(mapfn, thisArg, new Object[] { value, (double)i }), 0);
        }
        return target;
    }

    /**
     * ES2015 22.2.2.2 %TypedArray%.of.
     *
     * @param self  the constructor it was called on
     * @param args  the elements
     * @return a new typed array holding them
     */
    @Function(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object of(final Object self, final Object... args) {
        final ArrayBufferView target = allocate(self, args.length);
        for (int i = 0; i < args.length; i++) {
            target.set(i, args[i], 0);
        }
        return target;
    }

    /**
     * ES2015 22.2.3.1 get %TypedArray%.prototype.buffer.
     *
     * @param self self reference
     * @return the buffer the view is over
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object buffer(final Object self) {
        return described(self).getArrayBuffer();
    }

    /**
     * ES2015 22.2.3.2 get %TypedArray%.prototype.byteLength.
     *
     * @param self self reference
     * @return the view's length in bytes
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteLength(final Object self) {
        return described(self).getViewByteLength();
    }

    /**
     * ES2015 22.2.3.3 get %TypedArray%.prototype.byteOffset.
     *
     * @param self self reference
     * @return where the view starts in its buffer
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteOffset(final Object self) {
        return described(self).getViewByteOffset();
    }

    /**
     * ES2015 22.2.3.17 get %TypedArray%.prototype.length.
     *
     * @param self self reference
     * @return the number of elements
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int length(final Object self) {
        return described(self).getElementLength();
    }

    /**
     * ES2015 22.2.3.31 get %TypedArray%.prototype [ @@toStringTag ].
     *
     * @param self self reference
     * @return the name of the concrete type, or undefined for anything else
     */
    @Getter(where = Where.PROTOTYPE, name = "@@toStringTag", attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object toStringTag(final Object self) {
        return self instanceof ArrayBufferView view ? view.getClassName() : ScriptRuntime.UNDEFINED;
    }

    /**
     * ES2015 22.2.3.22 %TypedArray%.prototype.set.
     *
     * @param self   self reference
     * @param array  what to copy in
     * @param offset where to start writing
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object set(final Object self, final Object array, final Object offset) {
        return ArrayBufferView.setImpl(view(self), array, offset);
    }

    /**
     * ES2015 22.2.3.26 %TypedArray%.prototype.subarray - a new view over the same
     * buffer, sharing its storage.
     *
     * @param self  self reference
     * @param begin first element, negative counting from the end
     * @param end   one past the last, defaulting to the length
     * @return the new view
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object subarray(final Object self, final Object begin, final Object end) {
        // 22.2.3.27 does not validate the array first: a detached buffer has a
        // length of zero here, and what refuses it is the constructor at the
        // end - by which time the arguments have been converted, which a script
        // can see them being
        return ArrayBufferView.subarrayImpl(described(self), begin, end);
    }

    /**
     * ES2015 22.2.3.23 %TypedArray%.prototype.slice - unlike subarray, a copy.
     *
     * @param self  self reference
     * @param start first element, negative counting from the end
     * @param end   one past the last, defaulting to the length
     * @return a new typed array of the same type
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object slice(final Object self, final Object start, final Object end) {
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        final int from = ArrayBufferView.relativeIndex(start, length, 0);
        final int to   = ArrayBufferView.relativeIndex(end, length, length);
        final int count = Math.max(to - from, 0);

        final ArrayBufferView result = ArrayBufferView.speciesCreate(source, count);
        if (count > 0) {
            // ES2015 22.2.3.23 step 14.a: converting the arguments, and the
            // species constructor itself, can have detached the source
            if (source.isDetached()) {
                throw typeError("detached.array.buffer");
            }
            // ES2024: they can also have resized it - a fixed source left out of
            // bounds throws, a length-tracking one clamps the copy to what still
            // fits, leaving the rest of the result at zero.
            if (source.isOutOfBounds()) {
                throw typeError("detached.array.buffer");
            }
            final int copyTo = Math.min(to, source.getElementLength());
            for (int i = from, j = 0; i < copyTo; i++, j++) {
                result.set(j, source.get(i), 0);
            }
        }
        return result;
    }

    /**
     * ES2015 22.2.3.19 %TypedArray%.prototype.map.
     *
     * @param self       self reference
     * @param callbackfn what to apply to each element
     * @param thisArg    its this
     * @return a new typed array holding the results
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object map(final Object self, final Object callbackfn, final Object thisArg) {
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        callable(callbackfn);

        // 22.2.3.19 step 6: the result is made before the callback runs even
        // once, which is observable through a species constructor
        final ArrayBufferView result = ArrayBufferView.speciesCreate(source, length);
        for (int i = 0; i < length; i++) {
            result.set(i, call(callbackfn, thisArg, source.get(i), i, source), 0);
        }
        return result;
    }

    /**
     * ES2015 22.2.3.9 %TypedArray%.prototype.filter.
     *
     * @param self       self reference
     * @param callbackfn what decides whether an element is kept
     * @param thisArg    its this
     * @return a new typed array holding the elements it kept
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object filter(final Object self, final Object callbackfn, final Object thisArg) {
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        callable(callbackfn);

        final List<Object> kept = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            final Object value = source.get(i);
            if (JSType.toBoolean(call(callbackfn, thisArg, value, i, source))) {
                kept.add(value);
            }
        }

        // 22.2.3.9 step 11: how many were kept is only known now, which is why
        // filter builds its result at the end where map builds it at the start
        final ArrayBufferView result = ArrayBufferView.speciesCreate(source, kept.size());
        for (int i = 0; i < kept.size(); i++) {
            result.set(i, kept.get(i), 0);
        }
        return result;
    }

    /** The callback these methods take, which has to be one before anything else happens. */
    private static void callable(final Object callbackfn) {
        if (!Bootstrap.isCallable(callbackfn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackfn));
        }
    }

    /** One call of such a callback, with the arguments ES2015 22.2.3 gives it. */
    private static Object call(final Object callbackfn, final Object thisArg,
            final Object value, final int index, final ArrayBufferView self) {
        return ScriptRuntime.call(callbackfn, thisArg, new Object[] { value, (double)index, self });
    }

    /**
     * ES2015 22.2.3.25 %TypedArray%.prototype.sort.
     *
     * Sorting is in place and, unlike Array.prototype.sort, numeric by default -
     * a typed array holds numbers, so ordering them as text would be useless. A
     * comparison function is honoured, and is then Array's sort over a copy.
     *
     * @param self      self reference
     * @param comparefn optional comparison function
     * @return the array itself
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object sort(final Object self, final Object comparefn) {
        final ArrayBufferView array = view(self);
        final int length = array.getElementLength();

        if (comparefn == ScriptRuntime.UNDEFINED) {
            if (array.isBigIntArray()) {
                // ES2020 22.2.3.26: a BigInt array's default sort is numeric on BigInts
                final BigInteger[] bigs = new BigInteger[length];
                for (int i = 0; i < length; i++) {
                    bigs[i] = NativeBigInt.toBigInt(array.get(i));
                }
                Arrays.sort(bigs);
                for (int i = 0; i < length; i++) {
                    array.set(i, bigs[i], 0);
                }
                return array;
            }
            final double[] elements = new double[length];
            for (int i = 0; i < length; i++) {
                elements[i] = JSType.toNumber(array.get(i));
            }
            // Arrays.sort orders -0.0 before 0.0 and NaN last, which is what
            // ES2015 22.2.3.25 asks for
            Arrays.sort(elements);
            for (int i = 0; i < length; i++) {
                array.set(i, elements[i], 0);
            }
            return array;
        }

        final Object[] elements = new Object[length];
        for (int i = 0; i < length; i++) {
            elements[i] = array.get(i);
        }
        final ScriptObject sorted = NativeArray.sort(Global.allocate(elements), comparefn);
        for (int i = 0; i < length; i++) {
            array.set(i, sorted.get(i), 0);
        }
        return array;
    }


    /**
     * ES2015 22.2.3.14 %TypedArray%.prototype.join.
     *
     * @param self      self reference
     * @param separator what to put between elements
     * @return the joined text
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static String join(final Object self, final Object separator) {
        return NativeArray.join(view(self), separator);
    }

    /**
     * ES2015 22.2.3.21 %TypedArray%.prototype.reverse, in place.
     *
     * @param self self reference
     * @return the array itself
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object reverse(final Object self) {
        return NativeArray.reverse(view(self));
    }

    /**
     * ES2023 %TypedArray%.prototype.toReversed(): a reversed copy of the same
     * kind, leaving the original untouched.
     *
     * @param self self reference
     * @return a new, reversed typed array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object toReversed(final Object self) {
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        final ArrayBufferView result = ArrayBufferView.createSameType(source, length);
        for (int i = 0; i < length; i++) {
            result.set(i, source.get(length - 1 - i), 0);
        }
        return result;
    }

    /**
     * ES2023 %TypedArray%.prototype.toSorted(comparefn): a sorted copy of the
     * same kind, leaving the original untouched.
     *
     * @param self      self reference
     * @param comparefn the comparator, or undefined for the numeric default
     * @return a new, sorted typed array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object toSorted(final Object self, final Object comparefn) {
        if (comparefn != ScriptRuntime.UNDEFINED && !Bootstrap.isCallable(comparefn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(comparefn));
        }
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        final ArrayBufferView result = ArrayBufferView.createSameType(source, length);
        for (int i = 0; i < length; i++) {
            result.set(i, source.get(i), 0);
        }
        return sort(result, comparefn);
    }

    /**
     * ES2023 %TypedArray%.prototype.with(index, value): a copy of the same kind
     * with one element replaced. The index may count from the end; one out of
     * range is a RangeError. The value is coerced to the array's element type
     * before the range is checked.
     *
     * @param self  self reference
     * @param index the index to replace, negative counting from the end
     * @param value the replacement value
     * @return a new typed array with the one element changed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object with(final Object self, final Object index, final Object value) {
        final ArrayBufferView source = view(self);
        final int length = source.getElementLength();
        final double relative = JSType.toInteger(index);
        final double actual = relative >= 0 ? relative : length + relative;
        final Object numeric = source.isBigIntArray() ? NativeBigInt.toBigInt(value) : (Object)JSType.toNumber(value);
        // ES2024 IsValidIntegerIndex: the index is checked against the current
        // length - the coercions above can have resized the backing buffer - and
        // rejected if the view was left out of bounds.
        if (source.isOutOfBounds() || actual < 0 || actual >= source.getElementLength()) {
            throw rangeError("inappropriate.array.index", JSType.toString(index));
        }
        final ArrayBufferView result = ArrayBufferView.createSameType(source, length);
        final int target = (int)actual;
        for (int i = 0; i < length; i++) {
            result.set(i, i == target ? numeric : source.get(i), 0);
        }
        return result;
    }

    /**
     * ES2015 22.2.3.8 %TypedArray%.prototype.fill.
     *
     * @param self  self reference
     * @param value what to write
     * @param start first element
     * @param end   one past the last
     * @return the array itself
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object fill(final Object self, final Object value, final Object start, final Object end) {
        final ArrayBufferView array = view(self);
        final int length = array.getElementLength();

        // ES2015 22.2.3.8 converts all three arguments before it writes
        // anything, and each conversion is script-visible and can detach the
        // buffer - which is why the check for that comes after all of them
        // Boxed, because a typed array's storage truncates a primitive double
        // towards the element type's limits where ES2015 7.1.5 ToInt32 wraps
        final Object filler = array.isBigIntArray() ? NativeBigInt.toBigInt(value) : JSType.toNumber(value);
        final int from = ArrayBufferView.relativeIndex(start, length, 0);
        final int to   = ArrayBufferView.relativeIndex(end, length, length);

        if (array.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        // ES2024: those conversions can have resized the buffer - a fixed view
        // left out of bounds throws, a length-tracking one fills only what still
        // fits.
        if (array.isOutOfBounds()) {
            throw typeError("detached.array.buffer");
        }
        final int fillTo = Math.min(to, array.getElementLength());

        for (int i = from; i < fillTo; i++) {
            array.set(i, filler, 0);
        }
        return array;
    }

    /**
     * ES2015 22.2.3.5 %TypedArray%.prototype.copyWithin.
     *
     * @param self   self reference
     * @param target where to copy to
     * @param start  first element to copy
     * @param end    one past the last
     * @return the array itself
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object copyWithin(final Object self, final Object target, final Object start, final Object end) {
        final ArrayBufferView array = view(self);
        final int length = array.getElementLength();

        final int to    = ArrayBufferView.relativeIndex(target, length, 0);
        final int from  = ArrayBufferView.relativeIndex(start, length, 0);
        final int last  = ArrayBufferView.relativeIndex(end, length, length);
        int count = Math.min(last - from, length - to);

        if (count > 0) {
            // 22.2.3.5 step 15.a, after the three conversions and only if there
            // is anything to move
            if (array.isDetached()) {
                throw typeError("detached.array.buffer");
            }
            // ES2024: those conversions can have resized the backing buffer - a
            // fixed view left out of bounds throws, a length-tracking one clamps
            // the move to what still fits.
            if (array.isOutOfBounds()) {
                throw typeError("detached.array.buffer");
            }
            final int currentLength = array.getElementLength();
            count = Math.max(0, Math.min(count, Math.min(currentLength - to, currentLength - from)));
            // the ranges may overlap, so the source is read out before the
            // first element of the target is written
            final Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                values[i] = array.get(from + i);
            }
            for (int i = 0; i < count; i++) {
                array.set(to + i, values[i], 0);
            }
        }
        return array;
    }

    /**
     * ES2015 22.2.3.13 %TypedArray%.prototype.indexOf.
     *
     * @param self          self reference
     * @param searchElement what to look for
     * @param fromIndex     where to start
     * @return the index, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double indexOf(final Object self, final Object searchElement, final Object fromIndex) {
        return NativeArray.indexOf(view(self), searchElement, fromIndex);
    }

    /**
     * ECMAScript 2016 22.2.3.13.1 %TypedArray%.prototype.includes.
     *
     * @param self          self reference
     * @param searchElement what to look for
     * @param fromIndex     where to start
     * @return whether it is there
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean includes(final Object self, final Object searchElement, final Object fromIndex) {
        return NativeArray.includes(view(self), searchElement, fromIndex);
    }

    /**
     * ECMAScript 2022 23.2.3.1 %TypedArray%.prototype.at ( index )
     *
     * @param self  the typed array
     * @param index where to read, negative counting from the end
     * @return the element there, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object at(final Object self, final Object index) {
        return NativeArray.at(view(self), index);
    }

    /**
     * ES2015 22.2.3.16 %TypedArray%.prototype.lastIndexOf.
     *
     * @param self self reference
     * @param args what to look for, and optionally where to start
     * @return the index, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double lastIndexOf(final Object self, final Object... args) {
        return NativeArray.lastIndexOf(view(self), args);
    }

    /**
     * ES2015 22.2.3.7 %TypedArray%.prototype.every.
     *
     * @param self       self reference
     * @param callbackfn the predicate
     * @param thisArg    its this
     * @return whether every element satisfies it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean every(final Object self, final Object callbackfn, final Object thisArg) {
        return NativeArray.every(view(self), callbackfn, thisArg);
    }

    /**
     * ES2015 22.2.3.24 %TypedArray%.prototype.some.
     *
     * @param self       self reference
     * @param callbackfn the predicate
     * @param thisArg    its this
     * @return whether any element satisfies it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean some(final Object self, final Object callbackfn, final Object thisArg) {
        return NativeArray.some(view(self), callbackfn, thisArg);
    }

    /**
     * ES2015 22.2.3.12 %TypedArray%.prototype.forEach.
     *
     * @param self       self reference
     * @param callbackfn what to call for each element
     * @param thisArg    its this
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object forEach(final Object self, final Object callbackfn, final Object thisArg) {
        return NativeArray.forEach(view(self), callbackfn, thisArg);
    }

    /**
     * ES2015 22.2.3.19 %TypedArray%.prototype.reduce.
     *
     * @param self self reference
     * @param args the reducer and an optional initial value
     * @return the reduced value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduce(final Object self, final Object... args) {
        return NativeArray.reduce(view(self), args);
    }

    /**
     * ES2015 22.2.3.20 %TypedArray%.prototype.reduceRight.
     *
     * @param self self reference
     * @param args the reducer and an optional initial value
     * @return the reduced value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduceRight(final Object self, final Object... args) {
        return NativeArray.reduceRight(view(self), args);
    }

    /**
     * ES2015 22.2.3.10 %TypedArray%.prototype.find.
     *
     * @param self      self reference
     * @param predicate the test
     * @param thisArg   its this
     * @return the first element that passes, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object find(final Object self, final Object predicate, final Object thisArg) {
        return NativeArray.find(view(self), predicate, thisArg);
    }

    /**
     * ES2015 22.2.3.11 %TypedArray%.prototype.findIndex.
     *
     * @param self      self reference
     * @param predicate the test
     * @param thisArg   its this
     * @return the index of the first element that passes, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object findIndex(final Object self, final Object predicate, final Object thisArg) {
        return NativeArray.findIndex(view(self), predicate, thisArg);
    }

    /**
     * ES2023 %TypedArray%.prototype.findLast.
     *
     * @param self      self reference
     * @param predicate the test
     * @param thisArg   its this
     * @return the last element that passes, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object findLast(final Object self, final Object predicate, final Object thisArg) {
        return NativeArray.findLast(view(self), predicate, thisArg);
    }

    /**
     * ES2023 %TypedArray%.prototype.findLastIndex.
     *
     * @param self      self reference
     * @param predicate the test
     * @param thisArg   its this
     * @return the index of the last element that passes, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object findLastIndex(final Object self, final Object predicate, final Object thisArg) {
        return NativeArray.findLastIndex(view(self), predicate, thisArg);
    }

    /**
     * ES2015 22.2.3.6 %TypedArray%.prototype.entries.
     *
     * @param self self reference
     * @return an iterator over index/value pairs
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return ArrayIterator.newArrayKeyValueIterator(view(self));
    }

    /**
     * ES2015 22.2.3.15 %TypedArray%.prototype.keys.
     *
     * @param self self reference
     * @return an iterator over the indices
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object keys(final Object self) {
        return ArrayIterator.newArrayKeyIterator(view(self));
    }

    /**
     * ES2015 22.2.3.29 %TypedArray%.prototype.values, which is also
     * %TypedArray%.prototype [ @@iterator ] - the same function object, wired up
     * in {@link Global}.
     *
     * @param self self reference
     * @return an iterator over the elements
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return ArrayIterator.newArrayValueIterator(view(self));
    }

    /**
     * ES2015 22.2.3.27 %TypedArray%.prototype.toLocaleString.
     *
     * @param self self reference
     * @return the elements, joined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleString(final Object self) {
        return NativeArray.toLocaleString(view(self));
    }

    /**
     * The receiver as a typed array.
     *
     * Every method here is generic over the nine types but not over anything
     * else: ES2015 22.2.3.5.1 rejects a this that is not a typed array before it
     * looks at any argument.
     */
    /**
     * The receiver as a typed array, without asking whether its buffer is still
     * there.
     *
     * ES2015 22.2.3.1-3 and 22.2.3.17: the four accessors that describe a view
     * answer for a detached one rather than failing - the buffer as it was, and
     * zero for the three lengths.
     */
    private static ArrayBufferView described(final Object self) {
        if (self instanceof ArrayBufferView view) {
            return view;
        }
        throw typeError("not.a.typed.array", ScriptRuntime.safeToString(self));
    }

    private static ArrayBufferView view(final Object self) {
        final ArrayBufferView view = described(self);
        if (view.isDetached() || view.isOutOfBounds()) {
            // ES2015 22.2.3.5.1 / ES2024 ValidateTypedArray: a detached buffer -
            // or (ES2024) a view left out of bounds by a resize - is checked for
            // before any argument is even looked at.
            throw typeError("detached.array.buffer");
        }
        return view;
    }

    /**
     * Builds the typed array {@code constructor} makes, empty and long enough.
     *
     * from and of are inherited by the nine concrete constructors, so the this
     * they are called on says which one to build. ES2015 22.2.2.1 and 22.2.2.2
     * both go through TypedArrayCreate, so a constructor that hands back
     * something other than a typed array, or one too short to hold what was
     * asked for, is a TypeError rather than a mystery later on.
     */
    private static ArrayBufferView allocate(final Object constructor, final int length) {
        if (!(constructor instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        return ArrayBufferView.typedArrayCreate(
                ScriptRuntime.construct(function, (double)length), length);
    }

}
