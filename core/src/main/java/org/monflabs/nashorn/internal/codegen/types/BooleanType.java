/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static org.monflabs.nashorn.internal.runtime.JSType.UNDEFINED_INT;

import java.lang.classfile.CodeBuilder;
import org.monflabs.nashorn.internal.codegen.CodeBuffer;
import org.monflabs.nashorn.internal.codegen.CompilerConstants;

/**
 * The boolean type class
 */
public final class BooleanType extends Type {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Boolean.class, "valueOf", Boolean.class, boolean.class);
    private static final CompilerConstants.Call TO_STRING = staticCallNoLookup(Boolean.class, "toString", String.class, boolean.class);

    /**
     * Constructor
     */
    BooleanType() {
        super("boolean", boolean.class, 1, 1);
    }

    @Override
    public Type nextWider() {
        return INT;
    }

    @Override
    public Class<?> getBoxedType() {
        return Boolean.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'I';
    }

    @Override
    public Type loadUndefined(final CodeBuffer method) {
        method.emit(cb -> cb.loadConstant(UNDEFINED_INT));
        return BOOLEAN;
    }

    @Override
    public Type loadForcedInitializer(final CodeBuffer method) {
        method.emit(CodeBuilder::iconst_0);
        return BOOLEAN;
    }

    @Override
    public void _return(final CodeBuffer method) {
        method.emit(CodeBuilder::ireturn);
    }

    @Override
    public Type load(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.iload(slot));
        return BOOLEAN;
    }

    @Override
    public void store(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.istore(slot));
    }

    @Override
    public Type ldc(final CodeBuffer method, final Object c) {
        assert c instanceof Boolean;
        method.emit((Boolean)c ? CodeBuilder::iconst_1 : CodeBuilder::iconst_0);
        return BOOLEAN;
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        if (isEquivalentTo(to)) {
            return to;
        }

        if (to.isNumber()) {
            method.emit(CodeBuilder::i2d);
        } else if (to.isLong()) {
            method.emit(CodeBuilder::i2l);
        } else if (to.isInteger()) {
            //nop
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
        // Adding booleans in JavaScript is perfectly valid, they add as if false=0 and true=1
        return Type.INT.add(method, programPoint);
    }
}
