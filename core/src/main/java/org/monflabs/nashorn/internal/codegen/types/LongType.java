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
import static org.monflabs.nashorn.internal.runtime.JSType.UNDEFINED_LONG;

import java.lang.classfile.CodeBuilder;
import org.monflabs.nashorn.internal.codegen.CodeBuffer;
import org.monflabs.nashorn.internal.codegen.CompilerConstants;
import org.monflabs.nashorn.internal.runtime.JSType;

/**
 * Type class: LONG
 */
class LongType extends Type {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Long.class, "valueOf", Long.class, long.class);

    protected LongType(final String name) {
        super(name, long.class, 3, 2);
    }

    protected LongType() {
        this("long");
    }

    @Override
    public Type nextWider() {
        return NUMBER;
    }

    @Override
    public Class<?> getBoxedType() {
        return Long.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'J';
    }

    @Override
    public Type load(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.lload(slot));
        return LONG;
    }

    @Override
    public void store(final CodeBuffer method, final int slot) {
        assert slot != -1;
        method.emit(cb -> cb.lstore(slot));
    }

    @Override
    public Type ldc(final CodeBuffer method, final Object c) {
        assert c instanceof Long;

        final long value = (Long)c;
        method.emit(cb -> cb.loadConstant(value));

        return Type.LONG;
    }

    @Override
    public Type convert(final CodeBuffer method, final Type to) {
        if (isEquivalentTo(to)) {
            return to;
        }

        if (to.isNumber()) {
            method.emit(CodeBuilder::l2d);
        } else if (to.isInteger()) {
            invokestatic(method, JSType.TO_INT32_L);
        } else if (to.isBoolean()) {
            method.emit(CodeBuilder::l2i);
        } else if (to.isObject()) {
            invokestatic(method, VALUE_OF);
        } else {
            assert false : "Illegal conversion " + this + " -> " + to;
        }

        return to;
    }

    @Override
    public Type add(final CodeBuffer method, final int programPoint) {
        throw new UnsupportedOperationException("add");
    }

    @Override
    public void _return(final CodeBuffer method) {
        method.emit(CodeBuilder::lreturn);
    }

    @Override
    public Type loadUndefined(final CodeBuffer method) {
        method.emit(cb -> cb.loadConstant(UNDEFINED_LONG));
        return LONG;
    }

    @Override
    public Type loadForcedInitializer(final CodeBuffer method) {
        method.emit(CodeBuilder::lconst_0);
        return LONG;
    }
}
