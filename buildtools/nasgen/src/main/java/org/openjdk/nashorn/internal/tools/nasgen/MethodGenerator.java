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

package org.openjdk.nashorn.internal.tools.nasgen;

import static java.lang.constant.ConstantDescs.CD_void;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_Specialization;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_Specialization_init2;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_Specialization_init3;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.OBJ_ANNO_PKG;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

/**
 * Emits the bytecode nasgen generates, on top of a {@link CodeBuilder}.
 *
 * The builder already knows how to pick instruction forms (iconst/bipush/ldc and
 * so on) and computes maxs and stack maps, so this only adds the shorthands the
 * generators speak in.
 */
final class MethodGenerator {
    static final ClassDesc EMPTY_LINK_LOGIC_TYPE = ClassDesc.of(OBJ_ANNO_PKG, "SpecializedFunction$LinkLogic$Empty");

    private final CodeBuilder cb;
    private final MethodTypeDesc type;

    MethodGenerator(final CodeBuilder cb, final MethodTypeDesc type) {
        this.cb = cb;
        this.type = type;
    }

    void newObject(final ClassDesc clazz) {
        cb.new_(clazz);
    }

    /** Loads {@code this}; fails if the method being generated is static. */
    void loadThis() {
        cb.aload(cb.receiverSlot());
    }

    void loadLocal(final int index) {
        cb.aload(index);
    }

    void returnValue() {
        cb.return_(TypeKind.from(type.returnType()));
    }

    void returnVoid() {
        assert CD_void.equals(type.returnType()) : type;
        cb.return_();
    }

    void loadLiteral(final ConstantDesc value) {
        cb.loadConstant(value);
    }

    void pushNull() {
        cb.aconst_null();
    }

    void push(final int value) {
        cb.loadConstant(value);
    }

    void pop() {
        cb.pop();
    }

    void dup() {
        cb.dup();
    }

    /** Pushes a method handle for a static method. */
    void loadStaticHandle(final ClassDesc owner, final String name, final MethodTypeDesc methodType) {
        cb.loadConstant(MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, owner, name, methodType));
    }

    /** Pushes a method handle for a virtual method. */
    void loadVirtualHandle(final ClassDesc owner, final String name, final MethodTypeDesc methodType) {
        cb.loadConstant(MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.VIRTUAL, owner, name, methodType));
    }

    // invokes, field get/sets
    void invokeInterface(final ClassDesc owner, final String method, final MethodTypeDesc desc) {
        cb.invokeinterface(owner, method, desc);
    }

    void invokeVirtual(final ClassDesc owner, final String method, final MethodTypeDesc desc) {
        cb.invokevirtual(owner, method, desc);
    }

    void invokeSpecial(final ClassDesc owner, final String method, final MethodTypeDesc desc) {
        cb.invokespecial(owner, method, desc);
    }

    void invokeStatic(final ClassDesc owner, final String method, final MethodTypeDesc desc) {
        cb.invokestatic(owner, method, desc);
    }

    void putStatic(final ClassDesc owner, final String field, final ClassDesc desc) {
        cb.putstatic(owner, field, desc);
    }

    void getStatic(final ClassDesc owner, final String field, final ClassDesc desc) {
        cb.getstatic(owner, field, desc);
    }

    void putField(final ClassDesc owner, final String field, final ClassDesc desc) {
        cb.putfield(owner, field, desc);
    }

    void getField(final ClassDesc owner, final String field, final ClassDesc desc) {
        cb.getfield(owner, field, desc);
    }

    /**
     * Pushes a {@code Specialization[]} built from the given members, or null if
     * there are none.
     */
    void memberInfoArray(final ClassDesc className, final List<MemberInfo> mis) {
        if (mis.isEmpty()) {
            pushNull();
            return;
        }

        push(mis.size());
        cb.anewarray(CD_Specialization);
        int pos = 0;
        for (final MemberInfo mi : mis) {
            dup();
            push(pos++);
            cb.new_(CD_Specialization);
            dup();
            loadStaticHandle(className, mi.getJavaName(), mi.getMethodType());
            final ClassDesc linkLogicClass = mi.getLinkLogicClass();
            final boolean hasLinkLogic = !EMPTY_LINK_LOGIC_TYPE.equals(linkLogicClass);
            if (hasLinkLogic) {
                cb.loadConstant(linkLogicClass);
            }
            cb.loadConstant(mi.isOptimistic() ? 1 : 0);
            cb.loadConstant(mi.convertsNumericArgs() ? 1 : 0);
            invokeSpecial(CD_Specialization, INIT, hasLinkLogic ? MTD_Specialization_init3 : MTD_Specialization_init2);
            cb.aastore();
        }
    }

    /**
     * Continues an instruction the surrounding transform is relaying through unchanged.
     */
    void relay(final Opcode opcode, final ClassDesc owner, final String name, final MethodTypeDesc desc) {
        cb.invoke(opcode, owner, name, desc, opcode == Opcode.INVOKEINTERFACE);
    }
}
