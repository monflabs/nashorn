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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
 * Uint32 array for TypedArray extension
 */
@ScriptClass("Uint32Array")
public final class NativeUint32Array extends ArrayBufferView {
    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 4;

    /** ES2015 22.2.6.1: the element size is on the prototype too. */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.PROTOTYPE, name = "BYTES_PER_ELEMENT")
    public static final int BYTES_PER_ELEMENT_PROTOTYPE = 4;

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteBegin, final int length) {
            return new NativeUint32Array(buffer, byteBegin, length);
        }

        @Override
        public Uint32ArrayData createArrayData(final ByteBuffer nb, final int start, final int end) {
            return new Uint32ArrayData(nb.order(ByteOrder.nativeOrder()).asIntBuffer(), start, end);
        }

        @Override
        public String getClassName() {
            return "Uint32Array";
        }
    };

    private static final class Uint32ArrayData extends TypedArrayData<IntBuffer> {

        private static final MethodHandle GET_ELEM = specialCall(MethodHandles.lookup(), Uint32ArrayData.class, "getElem", double.class, int.class).methodHandle();
        private static final MethodHandle SET_ELEM = specialCall(MethodHandles.lookup(), Uint32ArrayData.class, "setElem", void.class, int.class, int.class).methodHandle();

        private Uint32ArrayData(final IntBuffer nb, final int start, final int end) {
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
        public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
            if (returnType == int.class) {
                return null;
            }
            return getContinuousElementGetter(getClass(), GET_ELEM, returnType, programPoint);
        }

        private int getRawElem(final int index) {
            try {
                return nb.get(index);
            } catch (final IndexOutOfBoundsException e) {
                throw new ClassCastException(); //force relink - this works for unoptimistic too
            }
        }

        private double getElem(final int index) {
            return JSType.toUint32(getRawElem(index));
        }

        private void setElem(final int index, final int elem) {
            try {
                if (index < nb.limit()) {
                    nb.put(index, elem);
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
            return double.class;
        }

        @Override
        public Class<?> getBoxedElementType() {
            return Double.class;
        }

        @Override
        public int getInt(final int index) {
            return getRawElem(index);
        }

        @Override
        public int getIntOptimistic(final int index, final int programPoint) {
            return JSType.toUint32Optimistic(getRawElem(index), programPoint);
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
            return getElem(index);
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
    @Constructor(arity = 1)
    public static NativeUint32Array constructor(final boolean newObj, final Object self, final Object... args) {
        return (NativeUint32Array)constructorImpl(newObj, args, FACTORY);
    }

    NativeUint32Array(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
        super(buffer, byteOffset, length);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getUint32ArrayPrototype();
    }
}
