/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.openjdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static org.openjdk.nashorn.internal.runtime.JSType.UNDEFINED_INT;
import static org.openjdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.lang.classfile.CodeBuilder;
import org.openjdk.nashorn.internal.codegen.CodeBuffer;
import org.openjdk.nashorn.internal.codegen.CompilerConstants;
import org.openjdk.nashorn.internal.runtime.JSType;

/**
 * Type class: INT
 */
class IntType extends BitwiseType {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call TO_STRING = staticCallNoLookup(Integer.class, "toString", String.class, int.class);
    private static final CompilerConstants.Call VALUE_OF  = staticCallNoLookup(Integer.class, "valueOf", Integer.class, int.class);

    protected IntType() {
        super("int", int.class, 2, 1);
    }

    @Override
    public Type nextWider() {
        return NUMBER;
    }

    @Override
    public Class<?> getBoxedType() {
        return Integer.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'I';
    }

    @Override
    public Type ldc(final CodeBuffer method, final Object c) {
        assert c instanceof Integer;

        final int value = (Integer)c;
        method.emit(cb -> cb.loadConstant(value));

        return Type.INT;
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        if (to.isEquivalentTo(this)) {
            return to;
        }

        if (to.isNumber()) {
            method.emit(CodeBuilder::i2d);
        } else if (to.isLong()) {
            method.emit(CodeBuilder::i2l);
        } else if (to.isBoolean()) {
            invokestatic(method, JSType.TO_BOOLEAN_I);
        } else if (to.isString()) {
            invokestatic(method, TO_STRING);
        } else if (to.isObject()) {
            invokestatic(method, VALUE_OF);
        } else {
            throw new UnsupportedOperationException("Illegal conversion " + this + " -> " + to);
        }

        return to;
    }

    @Override
    public Type add(final CodeBuffer method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.emit(CodeBuilder::iadd);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.ADD_EXACT);
        }
        return INT;
    }

    @Override
    public Type shr(final CodeBuffer method) {
        method.emit(CodeBuilder::iushr);
        return INT;
    }

    @Override
    public Type sar(final CodeBuffer method) {
        method.emit(CodeBuilder::ishr);
        return INT;
    }

    @Override
    public Type shl(final CodeBuffer method) {
        method.emit(CodeBuilder::ishl);
        return INT;
    }

    @Override
    public Type and(final CodeBuffer method) {
        method.emit(CodeBuilder::iand);
        return INT;
    }

    @Override
    public Type or(final CodeBuffer method) {
        method.emit(CodeBuilder::ior);
        return INT;
    }

    @Override
    public Type xor(final CodeBuffer method) {
        method.emit(CodeBuilder::ixor);
        return INT;
    }

    @Override
    public Type load(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.iload(slot));
        return INT;
    }

    @Override
    public void store(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.istore(slot));
    }

    @Override
    public Type sub(final CodeBuffer method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.emit(CodeBuilder::isub);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.SUB_EXACT);
        }
        return INT;
    }

    @Override
    public Type mul(final CodeBuffer method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.emit(CodeBuilder::imul);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.MUL_EXACT);
        }
        return INT;
    }

    @Override
    public Type div(final CodeBuffer method, final int programPoint) {
        if (programPoint == INVALID_PROGRAM_POINT) {
            invokestatic(method, JSType.DIV_ZERO);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.DIV_EXACT);
        }
        return INT;
    }

    @Override
    public Type rem(final CodeBuffer method, final int programPoint) {
        if (programPoint == INVALID_PROGRAM_POINT) {
            invokestatic(method, JSType.REM_ZERO);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.REM_EXACT);
        }
        return INT;
    }

    @Override
    public Type neg(final CodeBuffer method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.emit(CodeBuilder::ineg);
        } else {
            ldc(method, programPoint);
            invokestatic(method, JSType.NEGATE_EXACT);
        }
        return INT;
    }

    @Override
    public void _return(final CodeBuffer method) {
        method.emit(CodeBuilder::ireturn);
    }

    @Override
    public Type loadUndefined(final CodeBuffer method) {
        method.emit(cb -> cb.loadConstant(UNDEFINED_INT));
        return INT;
    }

    @Override
    public Type loadForcedInitializer(final CodeBuffer method) {
        method.emit(CodeBuilder::iconst_0);
        return INT;
    }

    @Override
    public Type cmp(final CodeBuffer method, final boolean isCmpG) {
        throw new UnsupportedOperationException("cmp" + (isCmpG ? 'g' : 'l'));
    }

    @Override
    public Type cmp(final CodeBuffer method) {
        throw new UnsupportedOperationException("cmp");
    }

}
