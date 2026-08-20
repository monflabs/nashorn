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

import static org.openjdk.nashorn.internal.codegen.CompilerConstants.specialCall;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static org.openjdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayData;
import org.openjdk.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * Uint8 clamped array for TypedArray extension
 */
@ScriptClass("Uint8ClampedArray")
public final class NativeUint8ClampedArray extends ArrayBufferView {
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
            return new NativeUint8ClampedArray(buffer, byteOffset, length);
        }

        @Override
        public Uint8ClampedArrayData createArrayData(final ByteBuffer nb, final int start, final int end) {
            return new Uint8ClampedArrayData(nb, start, end);
        }

        @Override
        public String getClassName() {
            return "Uint8ClampedArray";
        }
    };

    private static final class Uint8ClampedArrayData extends TypedArrayData<ByteBuffer> {

        private static final MethodHandle GET_ELEM = specialCall(MethodHandles.lookup(), Uint8ClampedArrayData.class, "getElem", int.class, int.class).methodHandle();
        private static final MethodHandle SET_ELEM = specialCall(MethodHandles.lookup(), Uint8ClampedArrayData.class, "setElem", void.class, int.class, int.class).methodHandle();
        private static final MethodHandle RINT_D   = staticCall(MethodHandles.lookup(), Uint8ClampedArrayData.class, "rint", double.class, double.class).methodHandle();
        private static final MethodHandle RINT_O   = staticCall(MethodHandles.lookup(), Uint8ClampedArrayData.class, "rint", Object.class, Object.class).methodHandle();

        private Uint8ClampedArrayData(final ByteBuffer nb, final int start, final int end) {
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
            return int.class;
        }

        @Override
        public Class<?> getBoxedElementType() {
            return int.class;
        }

        private int getElem(final int index) {
            try {
                return nb.get(index) & 0xff;
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        @Override
        public MethodHandle getElementSetter(final Class<?> elementType) {
            final MethodHandle setter = super.getElementSetter(elementType); //getContinuousElementSetter(getClass(), setElem(), elementType);
            if (setter != null) {
                if (elementType == Object.class) {
                    return MH.filterArguments(setter, 2, RINT_O);
                } else if (elementType == double.class) {
                    return MH.filterArguments(setter, 2, RINT_D);
                }
            }
            return setter;
        }

        private void setElem(final int index, final int elem) {
            try {
                if (index < nb.limit()) {
                    final byte clamped;
                    if ((elem & 0xffff_ff00) == 0) {
                        clamped = (byte) elem;
                    } else {
                        clamped = elem < 0 ? 0 : (byte) 0xff;
                    }
                    nb.put(index, clamped);
                }
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException();
            }
        }

        @Override
        public boolean isClamped() {
            return true;
        }

        @Override
        public boolean isUnsigned() {
            return true;
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
            return set(index, JSType.toNumber(value), strict);
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            setElem(index, value);
            return this;
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            return set(index, (int) rint(value), strict);
        }

        private static double rint(final double rint) {
            return (int)Math.rint(rint);
        }

        @SuppressWarnings("unused")
        private static Object rint(final Object rint) {
            return rint(JSType.toNumber(rint));
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
    public static NativeUint8ClampedArray constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeUint8ClampedArray)constructorImpl(newObj, args, FACTORY);
    }

    NativeUint8ClampedArray(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
        super(buffer, byteOffset, length);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getUint8ClampedArrayPrototype();
    }
}
