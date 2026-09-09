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

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.specialCall;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayData;
import org.monflabs.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * ES2025 Float16Array: a typed array of IEEE 754 binary16 (half-precision)
 * elements. The bytes are stored as 16-bit values in a {@link ShortBuffer};
 * each element is widened to a {@code double} on read and rounded from one on
 * write, through the JDK's {@code Float.float16ToFloat}/{@code floatToFloat16}
 * (round-to-nearest-even), which is exactly the ES {@code Float16Array}
 * conversion.
 */
@ScriptClass("Float16Array")
public final class NativeFloat16Array extends ArrayBufferView {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 2;

    /** ES2015 22.2.6.1: the element size is on the prototype too. */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.PROTOTYPE, name = "BYTES_PER_ELEMENT")
    public static final int BYTES_PER_ELEMENT_PROTOTYPE = 2;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new NativeFloat16Array(buffer, byteOffset, length);
        }

        @Override
        public Float16ArrayData createArrayData(final ByteBuffer nb, final int start, final int end) {
            return new Float16ArrayData(nb.asShortBuffer(), start, end);
        }

        @Override
        public String getClassName() {
            return "Float16Array";
        }
    };

    private static final class Float16ArrayData extends TypedArrayData<ShortBuffer> {

        private static final MethodHandle GET_ELEM = specialCall(MethodHandles.lookup(), Float16ArrayData.class, "getElem", double.class, int.class).methodHandle();
        private static final MethodHandle SET_ELEM = specialCall(MethodHandles.lookup(), Float16ArrayData.class, "setElem", void.class, int.class, double.class).methodHandle();

        private Float16ArrayData(final ShortBuffer nb, final int start, final int end) {
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

        @Override
        public Class<?> getElementType() {
            return double.class;
        }

        @Override
        public Class<?> getBoxedElementType() {
            return Double.class;
        }

        private double getElem(final int index) {
            try {
                return Float.float16ToFloat(nb.get(index));
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        private void setElem(final int index, final double elem) {
            try {
                if (index < nb.limit()) {
                    nb.put(index, doubleToFloat16(elem));
                }
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException();
            }
        }

        @Override
        public int getInt(final int index) {
            return (int) getDouble(index);
        }

        @Override
        public int getIntOptimistic(final int index, final int programPoint) {
            return JSType.toInt32(getElem(index));
        }

        @Override
        public double getDouble(final int index) {
            return getElem(index);
        }

        @Override
        public double getDoubleOptimistic(final int index, final int programPoint) {
            return getElem(index);
        }

        @Override
        public Object getObject(final int index) {
            return getDouble(index);
        }

        @Override
        public ArrayData set(final int index, final Object value, final boolean strict) {
            return set(index, JSType.toNumber(value), strict);
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            return set(index, (double) value, strict);
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            setElem(index, value);
            return this;
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
    public static NativeFloat16Array constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeFloat16Array)constructorImpl(newObj, args, FACTORY);
    }

    NativeFloat16Array(final NativeArrayBuffer buffer, final int byteOffset, final int byteLength) {
        super(buffer, byteOffset, byteLength);
    }

    /**
     * Round an IEEE double to binary16 (half) bits with round-to-nearest-even,
     * the ES {@code Float16Array}/{@code Math.f16round}/{@code DataView.setFloat16}
     * conversion. A plain {@code (float) d} then {@link Float#floatToFloat16}
     * double-rounds - a value just above a half tie can collapse onto the tie and
     * round the wrong way - so the double is first narrowed to {@code float} with
     * round-to-odd, which the final round-to-nearest-even then rounds correctly.
     *
     * @param d the value
     * @return the binary16 bit pattern
     */
    static short doubleToFloat16(final double d) {
        final float f = (float) d;
        final float rounded;
        if (Float.isNaN(f) || (double) f == d || (Float.floatToRawIntBits(f) & 1) == 1) {
            // exact, NaN, or already odd: no double-rounding to correct
            rounded = f;
        } else {
            // inexact and even: move to the bracketing neighbour, which is odd
            rounded = d > (double) f ? Math.nextUp(f) : Math.nextDown(f);
        }
        return Float.floatToFloat16(rounded);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected boolean isFloatArray() {
        return true;
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getFloat16ArrayPrototype();
    }
}
