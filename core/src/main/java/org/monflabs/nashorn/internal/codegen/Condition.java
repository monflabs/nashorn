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

package org.monflabs.nashorn.internal.codegen;

import java.lang.classfile.Opcode;


/**
 * Condition enum used for all kinds of jumps, regardless of type
 */
enum Condition {
    EQ,
    NE,
    LE,
    LT,
    GE,
    GT;

    static Opcode toUnary(final Condition c) {
        switch (c) {
        case EQ:
            return Opcode.IFEQ;
        case NE:
            return Opcode.IFNE;
        case LE:
            return Opcode.IFLE;
        case LT:
            return Opcode.IFLT;
        case GE:
            return Opcode.IFGE;
        case GT:
            return Opcode.IFGT;
        default:
            throw new UnsupportedOperationException("toUnary:" + c);
        }
    }

    static Opcode toBinary(final Condition c, final boolean isObject) {
        switch (c) {
        case EQ:
            return isObject ? Opcode.IF_ACMPEQ : Opcode.IF_ICMPEQ;
        case NE:
            return isObject ? Opcode.IF_ACMPNE : Opcode.IF_ICMPNE;
        case LE:
            return Opcode.IF_ICMPLE;
        case LT:
            return Opcode.IF_ICMPLT;
        case GE:
            return Opcode.IF_ICMPGE;
        case GT:
            return Opcode.IF_ICMPGT;
        default:
            throw new UnsupportedOperationException("toBinary:" + c);
        }
    }
}
