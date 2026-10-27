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

import static org.monflabs.nashorn.internal.runtime.UnwarrantedOptimismException.FIRST_PROGRAM_POINT;
import static org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.MAX_PROGRAM_POINT_VALUE;

import java.util.HashSet;
import java.util.Set;
import org.monflabs.nashorn.internal.IntDeque;
import org.monflabs.nashorn.internal.ir.AccessNode;
import org.monflabs.nashorn.internal.ir.BinaryNode;
import org.monflabs.nashorn.internal.ir.CallNode;
import org.monflabs.nashorn.internal.ir.Expression;
import org.monflabs.nashorn.internal.ir.ForNode;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.IndexNode;
import org.monflabs.nashorn.internal.ir.Node;
import org.monflabs.nashorn.internal.ir.Optimistic;
import org.monflabs.nashorn.internal.ir.UnaryNode;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import org.monflabs.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * Find program points in the code that are needed for optimistic assumptions
 */
class ProgramPoints extends SimpleNodeVisitor {

    private final IntDeque nextProgramPoint = new IntDeque();
    private final Set<Node> noProgramPoint = new HashSet<>();

    /**
     * The next program point of the current function, or {@link #NO_PROGRAM_POINT} when it has
     * used them all: a call site encodes its program point in the bits of its flags that are
     * left, so a function has at most {@code MAX_PROGRAM_POINT_VALUE} of them. Past that, as in a
     * function of tens of thousands of expressions, the rest is compiled without optimistic types.
     */
    private int next() {
        final int next = nextProgramPoint.getAndIncrement();
        return next > MAX_PROGRAM_POINT_VALUE ? NO_PROGRAM_POINT : next;
    }

    private static final int NO_PROGRAM_POINT = -1;

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        nextProgramPoint.push(FIRST_PROGRAM_POINT);
        return true;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        nextProgramPoint.pop();
        return functionNode;
    }

    private Expression setProgramPoint(final Optimistic optimistic) {
        if (noProgramPoint.contains(optimistic)) {
            return (Expression)optimistic;
        }
        if (!optimistic.canBeOptimistic()) {
            return (Expression)optimistic;
        }
        final int programPoint = next();
        return (Expression)(programPoint == NO_PROGRAM_POINT ? optimistic : optimistic.setProgramPoint(programPoint));
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        noProgramPoint.add(varNode.getName());
        return true;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        if (identNode.isInternal()) {
            noProgramPoint.add(identNode);
        }
        return true;
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        if(identNode.isPropertyName()) {
            return identNode;
        }
        return setProgramPoint(identNode);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        // The binding of a for-in or for-of loop takes each value optimistically, as a plain "for (x of xs)" does
        // through its identifier's program point. The internal identifier a destructuring binding is desugared
        // through gets none as an internal one, and would be converted optimistically at no program point to resume.
        if (forNode.isForInOrOf() && forNode.getInit() instanceof IdentNode init && init.isInternal()
                && !UnwarrantedOptimismException.isValid(init.getProgramPoint())) {
            final int programPoint = next();
            if (programPoint != NO_PROGRAM_POINT) {
                return forNode.setInit(lc, (Expression)init.setProgramPoint(programPoint));
            }
        }
        return forNode;
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        return setProgramPoint(callNode);
    }

    @Override
    public Node leaveAccessNode(final AccessNode accessNode) {
        return setProgramPoint(accessNode);
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        return setProgramPoint(indexNode);
    }

    @Override
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        return setProgramPoint(binaryNode);
    }

    @Override
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        return setProgramPoint(unaryNode);
    }
}
