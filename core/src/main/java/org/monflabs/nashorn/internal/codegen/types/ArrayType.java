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

package org.monflabs.nashorn.internal.codegen.types;


import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import org.monflabs.nashorn.internal.codegen.CodeBuffer;

/**
 * This is an array type, i.e. OBJECT_ARRAY, NUMBER_ARRAY.
 */
public class ArrayType extends ObjectType implements BytecodeArrayOps {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     *
     * @param clazz the Java class representation of the array
     */
    protected ArrayType(final Class<?> clazz) {
        super(clazz);
    }

    /**
     * Get the element type of the array elements e.g. for OBJECT_ARRAY, this is OBJECT
     *
     * @return the element type
     */
    public Type getElementType() {
        return Type.typeFor(getTypeClass().getComponentType());
    }

    @Override
    public void astore(final CodeBuffer method) {
        method.emit(CodeBuilder::aastore);
    }

    @Override
    public Type aload(final CodeBuffer method) {
        method.emit(CodeBuilder::aaload);
        return getElementType();
    }

    @Override
    public Type arraylength(final CodeBuffer method) {
        method.emit(CodeBuilder::arraylength);
        return INT;
    }

    @Override
    public Type newarray(final CodeBuffer method) {
        final ClassDesc element = getElementType().getClassDesc();
        method.emit(cb -> cb.anewarray(element));
        return this;
    }

    @Override
    public Type newarray(final CodeBuffer method, final int dims) {
        final ClassDesc array = getClassDesc();
        method.emit(cb -> cb.multianewarray(array, dims));
        return this;
    }

    @Override
    public String toString() {
        return "array<elementType=" + getElementType().getTypeClass().getSimpleName() + '>';
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        assert to.isObject();
        assert !to.isArray() || ((ArrayType)to).getElementType() == getElementType();
        return to;
    }

}
