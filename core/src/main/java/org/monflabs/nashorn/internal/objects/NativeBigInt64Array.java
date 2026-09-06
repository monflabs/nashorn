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

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayData;
import org.monflabs.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * BigInt64Array (ES2020 22.2): a typed array whose elements are 64-bit signed
 * BigInt values.
 */
@ScriptClass("BigInt64Array")
public final class NativeBigInt64Array extends ArrayBufferView {
    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 8;

    /** The element size is on the prototype too. */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.PROTOTYPE, name = "BYTES_PER_ELEMENT")
    public static final int BYTES_PER_ELEMENT_PROTOTYPE = 8;

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new NativeBigInt64Array(buffer, byteOffset, length);
        }

        @Override
        public BigInt64ArrayData createArrayData(final ByteBuffer nb, final int start, final int length) {
            return new BigInt64ArrayData(nb.asLongBuffer(), start, length);
        }

        @Override
        public String getClassName() {
            return "BigInt64Array";
        }
    };

    private static final class BigInt64ArrayData extends TypedArrayData<LongBuffer> {

        private BigInt64ArrayData(final LongBuffer nb, final int start, final int end) {
            super((nb.position(start).limit(end)).slice(), end - start);
        }

        // BigInt elements are objects, so the continuous (int/double) fast path
        // does not apply - element access goes through the generic getObject /
        // set(index, Object) linker path, which handles boxed elements.
        @Override
        protected MethodHandle getGetElem() {
            return null;
        }

        @Override
        protected MethodHandle getSetElem() {
            return null;
        }

        @Override
        public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
            return null;
        }

        @Override
        public MethodHandle getElementSetter(final Class<?> elementType) {
            return null;
        }

        private long getElem(final int index) {
            try {
                return nb.get(index);
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        private void setElem(final int index, final long elem) {
            try {
                if (index < nb.limit()) {
                    nb.put(index, elem);
                }
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException();
            }
        }

        @Override
        public Class<?> getElementType() {
            return BigInteger.class;
        }

        @Override
        public Class<?> getBoxedElementType() {
            return BigInteger.class;
        }

        @Override
        public int getInt(final int index) {
            throw new ClassCastException(); //a BigInt element never reads as an int
        }

        @Override
        public double getDouble(final int index) {
            throw new ClassCastException();
        }

        @Override
        public Object getObject(final int index) {
            return BigInteger.valueOf(getElem(index));
        }

        @Override
        public ArrayData set(final int index, final Object value, final boolean strict) {
            setElem(index, NativeBigInt.toBigInt(value).longValue());
            return this;
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            // ES2020 IntegerIndexedElementSet: ToBigInt(Number) throws a TypeError
            return set(index, (Object)Integer.valueOf(value), strict);
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            return set(index, (Object)Double.valueOf(value), strict);
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
    @Constructor(arity = 3)
    public static NativeBigInt64Array constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeBigInt64Array)constructorImpl(newObj, args, FACTORY);
    }

    NativeBigInt64Array(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
        super(buffer, byteOffset, length);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getBigInt64ArrayPrototype();
    }

    @Override
    public boolean isBigIntArray() {
        return true;
    }
}
