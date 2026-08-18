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

package org.openjdk.nashorn.internal.codegen.types;

import static org.openjdk.nashorn.internal.codegen.CompilerConstants.classDesc;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.invoke.MethodHandle;
import org.openjdk.nashorn.internal.codegen.CodeBuffer;
import org.openjdk.nashorn.internal.codegen.CompilerConstants;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Undefined;

/**
 * Type class: OBJECT This is the object type, used for all object types. It can
 * contain a class that is a more specialized object
 */
class ObjectType extends Type {
    private static final long serialVersionUID = 1L;

    protected ObjectType() {
        this(Object.class);
    }

    protected ObjectType(final Class<?> clazz) {
        super("object",
                clazz,
                clazz == Object.class ? Type.MAX_WEIGHT : 10,
                1);
    }

    @Override
    public String toString() {
        return "object" + (getTypeClass() != Object.class ? "<type=" + getTypeClass().getSimpleName() + '>' : "");
    }

    @Override
    public String getShortDescriptor() {
        return getTypeClass() == Object.class ? "Object" : getTypeClass().getSimpleName();
    }

    @Override
    public Type add(final CodeBuffer method, final int programPoint) {
        invokestatic(method, ScriptRuntime.ADD);
        return Type.OBJECT;
    }

    @Override
    public Type load(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.aload(slot));
        return this;
    }

    @Override
    public void store(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.astore(slot));
    }

    @Override
    public Type loadUndefined(final CodeBuffer method) {
        method.emit(cb -> cb.getstatic(classDesc(ScriptRuntime.class), "UNDEFINED", classDesc(Undefined.class)));
        return UNDEFINED;
    }

    @Override
    public Type loadForcedInitializer(final CodeBuffer method) {
        method.emit(CodeBuilder::aconst_null);
        // TODO: do we need a special type for null, e.g. Type.NULL? It should be assignable to any other object type
        // without a checkast in convert.
        return OBJECT;
    }

    @Override
    public Type loadEmpty(final CodeBuffer method) {
        method.emit(cb -> cb.getstatic(classDesc(ScriptRuntime.class), "EMPTY", classDesc(Undefined.class)));
        return UNDEFINED;
    }

    @Override
    public Type ldc(final CodeBuffer method, final Object c) {
        if (c == null) {
            method.emit(CodeBuilder::aconst_null);
        } else if (c instanceof Undefined) {
            return loadUndefined(method);
        } else if (c instanceof String s) {
            method.emit(cb -> cb.loadConstant(s));
            return STRING;
        } else if (c instanceof DirectMethodHandleDesc handle) {
            method.emit(cb -> cb.loadConstant(handle));
            return Type.typeFor(MethodHandle.class);
        } else {
            throw new UnsupportedOperationException("implementation missing for class " + c.getClass() + " value=" + c);
        }

        return Type.OBJECT;
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        final boolean toString = to.isString();
        if (!toString) {
            if (to.isArray()) {
                final Type elemType = ((ArrayType)to).getElementType();

                //note that if this an array, things won't work. see {link @ArrayType} subclass.
                //we also have the unpleasant case of NativeArray which looks like an Object, but is
                //an array to the type system. This is treated specially at the known load points

                final ClassDesc arrayClass;
                if (elemType.isString()) {
                    arrayClass = classDesc(String[].class);
                } else if (elemType.isNumber()) {
                    arrayClass = classDesc(double[].class);
                } else if (elemType.isLong()) {
                    arrayClass = classDesc(long[].class);
                } else if (elemType.isInteger()) {
                    arrayClass = classDesc(int[].class);
                } else {
                    arrayClass = classDesc(Object[].class);
                }
                method.emit(cb -> cb.checkcast(arrayClass));
                return to;
            } else if (to.isObject()) {
                final Class<?> toClass = to.getTypeClass();
                if(!toClass.isAssignableFrom(getTypeClass())) {
                    final ClassDesc target = classDesc(toClass);
                    method.emit(cb -> cb.checkcast(target));
                }
                return to;
            }
        } else if (isString()) {
            return to;
        }

        if (to.isInteger()) {
            invokestatic(method, JSType.TO_INT32);
        } else if (to.isNumber()) {
            invokestatic(method, JSType.TO_NUMBER);
        } else if (to.isLong()) {
            invokestatic(method, JSType.TO_LONG);
        } else if (to.isBoolean()) {
            invokestatic(method, JSType.TO_BOOLEAN);
        } else if (to.isString()) {
            invokestatic(method, JSType.TO_PRIMITIVE_TO_STRING);
        } else if (to.isCharSequence()) {
            invokestatic(method, JSType.TO_PRIMITIVE_TO_CHARSEQUENCE);
        } else {
            throw new UnsupportedOperationException("Illegal conversion " + this + " -> " + to + " " + isString() + " " + toString);
        }

        return to;
    }

    @Override
    public void _return(final CodeBuffer method) {
        method.emit(CodeBuilder::areturn);
    }

    @Override
    public char getBytecodeStackType() {
        return 'A';
    }
}
