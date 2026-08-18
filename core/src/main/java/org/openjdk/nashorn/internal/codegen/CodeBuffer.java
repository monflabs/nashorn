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

package org.openjdk.nashorn.internal.codegen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The body of one method, recorded as it is generated and written out when the
 * class it belongs to is.
 * <p>
 * {@code java.lang.classfile} only hands out a {@link CodeBuilder} inside the
 * callback that builds a method, but Nashorn's code generator has several
 * methods open at a time - a nested function is emitted while its enclosing
 * function still is - so emission is deferred instead of streamed. Each
 * operation is one {@code Consumer<CodeBuilder>}; those that capture nothing
 * are method references, which the JVM does not allocate.
 * <p>
 * Labels are the reason this cannot simply record {@code CodeElement}s: a
 * classfile label belongs to the builder that created it, so the labels the
 * code generator jumps to are mapped to real ones at write time.
 */
public final class CodeBuffer {
    /** Sized for a method body rather than for a handful of elements. */
    private final List<Consumer<CodeBuilder>> ops = new ArrayList<>(64);

    /** Labels bound or referenced by this method, mapped while writing. */
    private Map<Label, java.lang.classfile.Label> labels;

    /**
     * Instructions emitted so far. Not a bytecode offset - offsets are only
     * known once written - but it orders labels the same way, which is all
     * {@link Label#isAfter(Label)} needs.
     */
    private int instructionCount;

    /** Records one instruction. */
    public void emit(final Consumer<CodeBuilder> op) {
        ops.add(op);
        instructionCount++;
    }

    /** Records a jump to a label that may not have been bound yet. */
    public void branch(final Opcode opcode, final Label target) {
        emit(cb -> cb.branch(opcode, resolve(cb, target)));
    }

    /** Binds a label here. */
    public void bind(final Label label) {
        label.setPosition(instructionCount);
        ops.add(cb -> cb.labelBinding(resolve(cb, label)));
    }

    public void tableSwitch(final int low, final int high, final Label defaultTarget, final Label[] targets) {
        emit(cb -> cb.tableswitch(low, high, resolve(cb, defaultTarget), switchCases(cb, low, targets)));
    }

    public void lookupSwitch(final Label defaultTarget, final int[] values, final Label[] targets) {
        emit(cb -> {
            final List<SwitchCase> cases = new ArrayList<>(targets.length);
            for (int i = 0; i < targets.length; i++) {
                cases.add(SwitchCase.of(values[i], resolve(cb, targets[i])));
            }
            cb.lookupswitch(resolve(cb, defaultTarget), cases);
        });
    }

    public void tryCatch(final Label start, final Label end, final Label handler, final ClassDesc catchType) {
        ops.add(cb -> {
            if (catchType == null) {
                cb.exceptionCatchAll(resolve(cb, start), resolve(cb, end), resolve(cb, handler));
            } else {
                cb.exceptionCatch(resolve(cb, start), resolve(cb, end), resolve(cb, handler), catchType);
            }
        });
    }

    public void localVariable(final String name, final ClassDesc type, final Label start, final Label end, final int slot) {
        ops.add(cb -> cb.localVariable(slot, name, type, resolve(cb, start), resolve(cb, end)));
    }

    public void lineNumber(final int line) {
        ops.add(cb -> cb.lineNumber(line));
    }

    /** Writes everything recorded into a real builder. */
    public void writeTo(final CodeBuilder cb) {
        for (final Consumer<CodeBuilder> op : ops) {
            op.accept(cb);
        }
    }

    private java.lang.classfile.Label resolve(final CodeBuilder cb, final Label label) {
        if (labels == null) {
            labels = new IdentityHashMap<>();
        }
        return labels.computeIfAbsent(label, unused -> cb.newLabel());
    }

    private List<SwitchCase> switchCases(final CodeBuilder cb, final int low, final Label[] targets) {
        final List<SwitchCase> cases = new ArrayList<>(targets.length);
        for (int i = 0; i < targets.length; i++) {
            cases.add(SwitchCase.of(low + i, resolve(cb, targets[i])));
        }
        return cases;
    }
}
