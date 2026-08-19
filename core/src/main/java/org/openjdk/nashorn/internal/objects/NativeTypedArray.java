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

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.Arrays;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Getter;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;

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

        return construct(self, NativeArray.from(ScriptRuntime.UNDEFINED, source, mapfn, thisArg));
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
        return construct(self, Global.allocate(args.clone()));
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
        return ArrayBufferView.subarrayImpl(view(self), begin, end);
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
        final int from   = NativeArrayBuffer.adjustIndex(JSType.toInt32(start), length);
        final int to     = NativeArrayBuffer.adjustIndex(
                end != ScriptRuntime.UNDEFINED ? JSType.toInt32(end) : length, length);

        final ArrayBufferView result = source.factory().construct(Math.max(to - from, 0));
        for (int i = from, j = 0; i < to; i++, j++) {
            result.set(j, source.get(i), 0);
        }
        return result;
    }

    /**
     * ES2015 22.2.3.19 %TypedArray%.prototype.map.
     *
     * @param self       self reference
     * @param callbackfn what to apply to each element
     * @param thisArg    its this
     * @return a new typed array of the same type holding the results
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object map(final Object self, final Object callbackfn, final Object thisArg) {
        final ArrayBufferView source = view(self);
        return copyInto(source, NativeArray.map(source, callbackfn, thisArg));
    }

    /**
     * ES2015 22.2.3.9 %TypedArray%.prototype.filter.
     *
     * @param self       self reference
     * @param callbackfn what decides whether an element is kept
     * @param thisArg    its this
     * @return a new typed array of the same type holding the elements it kept
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object filter(final Object self, final Object callbackfn, final Object thisArg) {
        final ArrayBufferView source = view(self);
        return copyInto(source, NativeArray.filter(source, callbackfn, thisArg));
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

    /** A new typed array of {@code source}'s type, holding what an Array method produced. */
    private static ArrayBufferView copyInto(final ArrayBufferView source, final ScriptObject elements) {
        final int length = (int)JSType.toUint32(elements.getLength());
        final ArrayBufferView result = source.factory().construct(length);
        for (int i = 0; i < length; i++) {
            result.set(i, elements.get(i), 0);
        }
        return result;
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
        return NativeArray.fill(view(self), value, start, end);
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
        return NativeArray.copyWithin(view(self), target, start, end);
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
        if (view.isDetached()) {
            // ES2015 22.2.3.5.1 ValidateTypedArray: a detached buffer is checked
            // for before any argument is even looked at
            throw typeError("detached.array.buffer");
        }
        return view;
    }

    /**
     * Builds a typed array of the type {@code constructor} makes.
     *
     * from and of are inherited by the nine concrete constructors, so the this
     * they are called on says which one to build; ES2015 22.2.2.1 requires it to
     * be a constructor and nothing more.
     */
    private static Object construct(final Object constructor, final Object elements) {
        if (!(constructor instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        return ScriptRuntime.construct(function, elements);
    }

}
