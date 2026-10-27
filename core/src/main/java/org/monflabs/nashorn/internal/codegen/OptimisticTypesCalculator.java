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

import static org.monflabs.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import org.monflabs.nashorn.internal.codegen.types.Type;
import org.monflabs.nashorn.internal.ir.AccessNode;
import org.monflabs.nashorn.internal.ir.BaseNode;
import org.monflabs.nashorn.internal.ir.BinaryNode;
import org.monflabs.nashorn.internal.ir.CallNode;
import org.monflabs.nashorn.internal.ir.CatchNode;
import org.monflabs.nashorn.internal.ir.Expression;
import org.monflabs.nashorn.internal.ir.ExpressionStatement;
import org.monflabs.nashorn.internal.ir.ForNode;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.IfNode;
import org.monflabs.nashorn.internal.ir.IndexNode;
import org.monflabs.nashorn.internal.ir.JoinPredecessorExpression;
import org.monflabs.nashorn.internal.ir.LiteralNode;
import org.monflabs.nashorn.internal.ir.LoopNode;
import org.monflabs.nashorn.internal.ir.Node;
import org.monflabs.nashorn.internal.ir.ObjectNode;
import org.monflabs.nashorn.internal.ir.Optimistic;
import org.monflabs.nashorn.internal.ir.OptionalChainNode;
import org.monflabs.nashorn.internal.ir.PropertyNode;
import org.monflabs.nashorn.internal.ir.Symbol;
import org.monflabs.nashorn.internal.ir.TernaryNode;
import org.monflabs.nashorn.internal.ir.UnaryNode;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.WhileNode;
import org.monflabs.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import org.monflabs.nashorn.internal.parser.TokenType;
import org.monflabs.nashorn.internal.runtime.ScriptObject;

/**
 * Assigns optimistic types to expressions that can have them. This class mainly contains logic for which expressions
 * must not ever be marked as optimistic, assigning narrowest non-invalidated types to program points from the
 * compilation environment, as well as initializing optimistic types of global properties for scripts.
 */
final class OptimisticTypesCalculator extends SimpleNodeVisitor {

    final Compiler compiler;

    // Per-function bit set of program points that must never be optimistic.
    /** The spelling the parser gives new.target, which is an identifier in the IR and not a binding. */
    private static final String NEW_TARGET = "new.target";

    final Deque<BitSet> neverOptimistic = new ArrayDeque<>();

    // In a rest-of compilation, the program points of the values already on the stack at the continuation point.
    private BitSet pendingAtContinuation;

    OptimisticTypesCalculator(final Compiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public boolean enterAccessNode(final AccessNode accessNode) {
        tagNeverOptimistic(accessNode.getBase());
        if (accessNode.isSuper()) {
            // a super property read is a static runtime call (loadSuperGet)
            // that answers an Object and is not an optimistic operation; typed
            // narrower, its result would be coerced, and a string comes out 0
            tagNeverOptimistic(accessNode);
        }
        return true;
    }

    @Override
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        if(ScriptObject.PROTO_PROPERTY_NAME.equals(propertyNode.getKeyName())) {
            tagNeverOptimistic(propertyNode.getValue());
        }
        return super.enterPropertyNode(propertyNode);
    }

    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        if(binaryNode.isAssignment()) {
            final Expression lhs = binaryNode.lhs();
            if(!binaryNode.isSelfModifying()) {
                tagNeverOptimistic(lhs);
            }
            if(lhs instanceof IdentNode) {
                final Symbol symbol = ((IdentNode)lhs).getSymbol();
                // Assignment to internal symbols is never optimistic, except for self-assignment expressions
                if(symbol.isInternal() && !binaryNode.rhs().isSelfModifying()) {
                    tagNeverOptimistic(binaryNode.rhs());
                }
            }
        } else if(binaryNode.isTokenType(TokenType.INSTANCEOF)
                || binaryNode.isTokenType(TokenType.EQ_STRICT)
                || binaryNode.isTokenType(TokenType.NE_STRICT)) {
            tagNeverOptimistic(binaryNode.lhs());
            tagNeverOptimistic(binaryNode.rhs());
        }
        return true;
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        tagNeverOptimistic(callNode.getFunction());
        final Expression function = callNode.getFunction();
        if (function instanceof OptionalChainNode chain) {
            // (a?.b)(): the call is emitted through the member access the
            // chain wraps, which asserts it was never typed optimistically
            tagNeverOptimistic(chain.getExpression());
        }
        if (function instanceof BaseNode base && base.isSuper()
                || function instanceof IdentNode ident && ident.isDirectSuper()
                || hasSpreadArgument(callNode)) {
            // super(...), super.m(...) and a call with a spread argument are
            // static runtime calls (SUPER_CALL, SPREAD_CALL and kin) answering
            // an Object, not the optimistic dynamic call other calls compile
            // to; typed narrower, the result would be coerced - a string to 0
            tagNeverOptimistic(callNode);
        }
        return true;
    }

    /** Whether any argument is {@code ...x}; such a call goes through SPREAD_CALL. */
    private static boolean hasSpreadArgument(final CallNode callNode) {
        for (final Expression arg : callNode.getArgs()) {
            if (arg instanceof UnaryNode unary
                    && (unary.isTokenType(TokenType.SPREAD_ARGUMENT) || unary.isTokenType(TokenType.SPREAD_ARRAY)
                        || unary.isTokenType(TokenType.SPREAD_ELEMENTS))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        // Condition is never optimistic (always coerced to boolean).
        tagNeverOptimistic(catchNode.getExceptionCondition());
        return true;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        final Expression expr = expressionStatement.getExpression();
        if(!expr.isSelfModifying()) {
            tagNeverOptimistic(expr);
        }
        return true;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if(forNode.isForInOrOf()) {
            // for..in has the iterable in its "modify"
            tagNeverOptimistic(forNode.getModify());
        } else {
            // Test is never optimistic (always coerced to boolean).
            tagNeverOptimisticLoopTest(forNode);
        }
        return true;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (!neverOptimistic.isEmpty() && compiler.isOnDemandCompilation()) {
            // This is a nested function, and we're doing on-demand compilation. In these compilations, we never descend
            // into nested functions.
            return false;
        }
        if (neverOptimistic.isEmpty() && compiler.getContinuationEntryPoints() != null) {
            pendingAtContinuation = PendingOperands.find(functionNode, compiler.getContinuationEntryPoints()[0]);
        }
        neverOptimistic.push(new BitSet());
        return true;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimistic(ifNode.getTest());
        return true;
    }

    @Override
    public boolean enterIndexNode(final IndexNode indexNode) {
        tagNeverOptimistic(indexNode.getBase());
        if (indexNode.isSuper() || indexNode.isPrivate()) {
            // super[x] and the ES2022 private member read obj.#x are static
            // runtime calls answering an Object, outside the optimistic
            // operation the code generator wraps other reads in; see enterAccessNode
            tagNeverOptimistic(indexNode);
        }
        return true;
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimistic(ternaryNode.getTest());
        return true;
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        if(unaryNode.isTokenType(TokenType.NOT) || unaryNode.isTokenType(TokenType.NEW)) {
            // Operand of boolean negation is never optimistic (always coerced to boolean).
            // Operand of "new" is never optimistic (always coerced to Object).
            tagNeverOptimistic(unaryNode.getExpression());
        }
        return true;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        tagNeverOptimistic(varNode.getName());
        return true;
    }

    @Override
    public boolean enterObjectNode(ObjectNode objectNode) {
        if (objectNode.getSplitRanges() != null) {
            return false;
        }
        return super.enterObjectNode(objectNode);
    }

    @Override
    public boolean enterLiteralNode(LiteralNode<?> literalNode) {
        if (literalNode.isArray() && ((LiteralNode.ArrayLiteralNode) literalNode).getSplitRanges() != null) {
            return false;
        }

        return super.enterLiteralNode(literalNode);
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimisticLoopTest(whileNode);
        return true;
    }

    @Override
    protected Node leaveDefault(final Node node) {
        if(node instanceof Optimistic) {
            return leaveOptimistic((Optimistic)node);
        }
        return node;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        neverOptimistic.pop();
        return functionNode;
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        if (NEW_TARGET.equals(identNode.getName())) {
            // not a variable: answered from the frame by a static runtime
            // call (NEW_TARGET), outside the optimistic machinery, and an
            // Object; typed narrower it would be coerced - a function to 0
            return identNode;
        }
        final Symbol symbol = identNode.getSymbol();
        if(symbol == null) {
            assert identNode.isPropertyName();
            return identNode;
        } else if(symbol.isBytecodeLocal()) {
            // Identifiers accessing bytecode local variables will never be optimistic, as type calculation phase over
            // them will always assign them statically provable types. Note that access to function parameters can still
            // be optimistic if the parameter needs to be in scope as it's used by a nested function.
            return identNode;
        } else if(symbol.isParam() && lc.getCurrentFunction().isVarArg()) {
            // Parameters in vararg methods are not optimistic; we always access them using Object getters.
            return identNode.setType(identNode.getMostPessimisticType());
        } else {
            assert symbol.isScope();
            return leaveOptimistic(identNode);
        }
    }

    private Expression leaveOptimistic(final Optimistic opt) {
        final int pp = opt.getProgramPoint();
        if(isValid(pp) && !neverOptimistic.peek().get(pp)) {
            if (pendingAtContinuation != null && neverOptimistic.size() == 1 && pendingAtContinuation.get(pp)) {
                // The value was computed by the method that deoptimized, and arrives here as an object: typed narrower
                // than that method typed it, it would be converted without a guard - undefined became 0 in
                // "undefined <= (v * 1000 >>> 0)" once v's deoptimization made the comparison an int one. As the
                // widest type it passes through, and the operation consuming it converts it. Recorded as
                // invalidated, so a later recompilation agrees: typed as its guess again, the operation it feeds
                // narrowed with it while a guess beside it, never checked here, had not deoptimized - a string
                // held in a local the operation initialized then arrived at the next continuation as 0.
                final Type pessimistic = opt.getMostPessimisticType();
                compiler.addInvalidatedProgramPoint(pp, pessimistic);
                return (Expression)opt.setType(pessimistic);
            }
            return (Expression)opt.setType(compiler.getOptimisticType(opt));
        }
        return (Expression)opt;
    }

    private void tagNeverOptimistic(final Expression expr) {
        if(expr instanceof Optimistic) {
            final int pp = ((Optimistic)expr).getProgramPoint();
            if(isValid(pp)) {
                neverOptimistic.peek().set(pp);
            }
        }
    }

    private void tagNeverOptimisticLoopTest(final LoopNode loopNode) {
        final JoinPredecessorExpression test = loopNode.getTest();
        if(test != null) {
            tagNeverOptimistic(test.getExpression());
        }
    }

    /**
     * Finds the optimistic expressions whose values are on the stack when the continuation point of a rest-of method
     * is reached: the operands evaluated before the one the continuation point is in, in every enclosing expression up
     * to the statement.
     */
    private static final class PendingOperands extends SimpleNodeVisitor {
        private final FunctionNode root;
        private final int entryPoint;
        private final BitSet pending = new BitSet();
        // Per open node, the expressions among its children that have been evaluated; STATEMENT for a statement.
        private static final List<Expression> STATEMENT = List.of();
        private final Deque<List<Expression>> evaluated = new ArrayDeque<>();
        private boolean found;

        private PendingOperands(final FunctionNode root, final int entryPoint) {
            this.root = root;
            this.entryPoint = entryPoint;
        }

        static BitSet find(final FunctionNode functionNode, final int entryPoint) {
            final PendingOperands finder = new PendingOperands(functionNode, entryPoint);
            functionNode.accept(finder);
            return finder.pending.isEmpty() ? null : finder.pending;
        }

        @Override
        protected boolean enterDefault(final Node node) {
            if (found) {
                return false;
            }
            if (node instanceof Optimistic && ((Optimistic)node).getProgramPoint() == entryPoint) {
                found = true;
                for (final List<Expression> siblings : evaluated) {
                    if (siblings == STATEMENT) {
                        break;
                    }
                    for (final Expression sibling : siblings) {
                        markValue(sibling);
                    }
                }
                return false;
            }
            evaluated.push(node instanceof Expression ? new ArrayList<>() : STATEMENT);
            return true;
        }

        @Override
        protected Node leaveDefault(final Node node) {
            evaluated.pop();
            if (node instanceof Expression && !evaluated.isEmpty() && evaluated.peek() != STATEMENT) {
                evaluated.peek().add((Expression)node);
            }
            return node;
        }

        @Override
        public boolean enterFunctionNode(final FunctionNode functionNode) {
            // Only the function being compiled: a nested one numbers its own program points.
            return functionNode == root && enterDefault(functionNode);
        }

        private void markValue(final Expression expr) {
            if (expr.isSelfModifying()) {
                // an increment or a compound assignment answers a value of the type it computes in, never one left
                // unguarded; and widened, a scope one is emitted without the temporary that holds its base, so every
                // later local moved down a slot from where the deoptimized method saved it (the callee of a call
                // pending beside v0-- was restored from the slot of its this)
                return;
            }
            if (expr instanceof Optimistic && isValid(((Optimistic)expr).getProgramPoint())) {
                pending.set(((Optimistic)expr).getProgramPoint());
            } else if (expr instanceof JoinPredecessorExpression) {
                markValue(((JoinPredecessorExpression)expr).getExpression());
            } else if (expr instanceof TernaryNode) {
                markValue(((TernaryNode)expr).getTrueExpression());
                markValue(((TernaryNode)expr).getFalseExpression());
            } else if (expr instanceof BinaryNode) {
                final BinaryNode binaryNode = (BinaryNode)expr;
                if (binaryNode.isTokenType(TokenType.COMMARIGHT)) {
                    markValue(binaryNode.rhs());
                } else if (binaryNode.isLogical()) {
                    markValue(binaryNode.lhs());
                    markValue(binaryNode.rhs());
                }
            }
        }
    }
}
