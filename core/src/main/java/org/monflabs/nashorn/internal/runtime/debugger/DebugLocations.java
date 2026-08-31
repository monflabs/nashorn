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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.util.List;
import org.monflabs.nashorn.internal.ir.BinaryNode;
import org.monflabs.nashorn.internal.ir.BlockStatement;
import org.monflabs.nashorn.internal.ir.EmptyNode;
import org.monflabs.nashorn.internal.ir.Expression;
import org.monflabs.nashorn.internal.ir.ExpressionStatement;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.IfNode;
import org.monflabs.nashorn.internal.ir.LabelNode;
import org.monflabs.nashorn.internal.ir.Node;
import org.monflabs.nashorn.internal.ir.ReturnNode;
import org.monflabs.nashorn.internal.ir.RuntimeNode;
import org.monflabs.nashorn.internal.ir.Statement;
import org.monflabs.nashorn.internal.ir.UnaryNode;
import org.monflabs.nashorn.internal.parser.TokenType;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * Finds the breakable positions of a parse tree. The rule that decides what a
 * position is lives here, and the code generator asks the same rule, so the
 * table and the hooks agree; what the desugaring adds after the parse, the
 * hook bootstraps add to the table.
 */
public final class DebugLocations extends SimpleNodeVisitor {
    private final ScriptInfo info;
    private final Source source;

    private DebugLocations(final ScriptInfo info, final Source source) {
        this.info = info;
        this.source = source;
    }

    /**
     * Records every breakable statement of a tree into its source's info.
     * @param root the parsed program or module
     * @param source the source it was parsed from
     * @return the info
     */
    public static ScriptInfo collect(final FunctionNode root, final Source source) {
        final ScriptInfo info = ScriptInfo.of(source);
        root.accept(new DebugLocations(info, source));
        return info;
    }

    @Override
    protected boolean enterDefault(final Node node) {
        if (node instanceof Statement statement && isBreakable(statement)) {
            info.add(statement.getLineNumber() - 1, source.getColumn(statement.position()));
        }
        return true;
    }

    /**
     * Whether the code generator gives a statement a hook: the ones a person
     * would put a breakpoint on, and none of the ones the compiler wrote.
     *
     * @param statement the statement
     * @return true if it gets a hook
     */
    public static boolean isBreakable(final Statement statement) {
        if (statement.getLineNumber() == Node.NO_LINE_NUMBER || statement.getToken() == Node.NO_TOKEN) {
            return false;
        }
        if (statement instanceof EmptyNode || statement instanceof LabelNode || statement instanceof BlockStatement) {
            return false;
        }
        if (statement instanceof VarNode var) {
            // a declaration without an initializer does nothing; a function
            // declaration is hoisted, and V8 does not stop on one either; and
            // arguments = :arguments and its kin are the compiler's own
            final Expression init = var.getInit();
            return init != null && !var.isFunctionDeclaration() && !isInternal(var.getName()) && !isInternal(init);
        }
        if (statement instanceof ExpressionStatement es) {
            final Expression expression = es.getExpression();
            if (expression instanceof BinaryNode binary && binary.isAssignment() && isInternal(binary.lhs())) {
                // a program's expression statements are lowered to assignments
                // of the completion value, :return = expr, which stand for the
                // statement; the resets, :return = void 0, are the compiler's own
                return !(binary.rhs() instanceof UnaryNode unary && unary.isTokenType(TokenType.VOID));
            }
            return !isInternal(expression);
        }
        if (statement instanceof IfNode ifNode) {
            return !mentionsInternal(ifNode.getTest());
        }
        if (statement instanceof ReturnNode ret) {
            return !isInternal(ret.getExpression());
        }
        return true;
    }

    private static boolean mentionsInternal(final Expression expression) {
        if (isInternal(expression)) {
            return true;
        }
        if (expression instanceof RuntimeNode runtime) {
            final List<Expression> args = runtime.getArgs();
            for (final Expression arg : args) {
                if (isInternal(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInternal(final Expression expression) {
        return expression instanceof IdentNode ident && ident.getName().startsWith(":");
    }
}
