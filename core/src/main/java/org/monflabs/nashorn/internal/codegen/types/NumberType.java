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

package org.monflabs.nashorn.internal.codegen.types;

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static org.monflabs.nashorn.internal.runtime.JSType.UNDEFINED_DOUBLE;

import java.lang.classfile.CodeBuilder;
import org.monflabs.nashorn.internal.codegen.CodeBuffer;
import org.monflabs.nashorn.internal.codegen.CompilerConstants;
import org.monflabs.nashorn.internal.runtime.JSType;

class NumberType extends NumericType {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Double.class, "valueOf", Double.class, double.class);

    protected NumberType() {
        super("double", double.class, 4, 2);
    }

    @Override
    public Type nextWider() {
        return OBJECT;
    }

    @Override
    public Class<?> getBoxedType() {
        return Double.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'D';
    }

    @Override
    public Type cmp(final CodeBuffer method, final boolean isCmpG) {
        method.emit(isCmpG ? CodeBuilder::dcmpg : CodeBuilder::dcmpl);
        return INT;
    }

    @Override
    public Type load(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.dload(slot));
        return NUMBER;
    }

    @Override
    public void store(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.dstore(slot));
    }

    @Override
    public Type loadUndefined(final CodeBuffer method) {
        method.emit(cb -> cb.loadConstant(UNDEFINED_DOUBLE));
        return NUMBER;
    }

    @Override
    public Type loadForcedInitializer(final CodeBuffer method) {
        method.emit(CodeBuilder::dconst_0);
        return NUMBER;
    }

    @Override
    public Type ldc(final CodeBuffer method, final Object c) {
        assert c instanceof Double;

        final double value = (Double)c;
        method.emit(cb -> cb.loadConstant(value));

        return NUMBER;
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        if (isEquivalentTo(to)) {
            return null;
        }

        if (to.isInteger()) {
            invokestatic(method, JSType.TO_INT32_D);
        } else if (to.isLong()) {
            invokestatic(method, JSType.TO_LONG_D);
        } else if (to.isBoolean()) {
            invokestatic(method, JSType.TO_BOOLEAN_D);
        } else if (to.isString()) {
            invokestatic(method, JSType.TO_STRING_D);
        } else if (to.isObject()) {
            invokestatic(method, VALUE_OF);
        } else {
            throw new UnsupportedOperationException("Illegal conversion " + this + " -> " + to);
        }

        return to;
    }

    @Override
    public Type add(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::dadd);
        return NUMBER;
    }

    @Override
    public Type sub(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::dsub);
        return NUMBER;
    }

    @Override
    public Type mul(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::dmul);
        return NUMBER;
    }

    @Override
    public Type div(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::ddiv);
        return NUMBER;
    }

    @Override
    public Type rem(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::drem);
        return NUMBER;
    }

    @Override
    public Type neg(final CodeBuffer method, final int programPoint) {
        method.emit(CodeBuilder::dneg);
        return NUMBER;
    }

    @Override
    public void _return(final CodeBuffer method) {
        method.emit(CodeBuilder::dreturn);
    }
}
