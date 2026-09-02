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

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.EVAL;
import static org.monflabs.nashorn.internal.codegen.CompilerConstants.RETURN;
import static org.monflabs.nashorn.internal.ir.Expression.isAlwaysTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.monflabs.nashorn.internal.ir.AccessNode;
import org.monflabs.nashorn.internal.ir.BaseNode;
import org.monflabs.nashorn.internal.ir.BinaryNode;
import org.monflabs.nashorn.internal.ir.Block;
import org.monflabs.nashorn.internal.ir.BlockLexicalContext;
import org.monflabs.nashorn.internal.ir.BlockStatement;
import org.monflabs.nashorn.internal.ir.BreakNode;
import org.monflabs.nashorn.internal.ir.CallNode;
import org.monflabs.nashorn.internal.ir.CaseNode;
import org.monflabs.nashorn.internal.ir.CatchNode;
import org.monflabs.nashorn.internal.ir.ClassNode;
import org.monflabs.nashorn.internal.ir.ContinueNode;
import org.monflabs.nashorn.internal.ir.DebuggerNode;
import org.monflabs.nashorn.internal.ir.EmptyNode;
import org.monflabs.nashorn.internal.ir.Expression;
import org.monflabs.nashorn.internal.ir.ExpressionStatement;
import org.monflabs.nashorn.internal.ir.ForNode;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.IfNode;
import org.monflabs.nashorn.internal.ir.IndexNode;
import org.monflabs.nashorn.internal.ir.JumpStatement;
import org.monflabs.nashorn.internal.ir.JumpToInlinedFinally;
import org.monflabs.nashorn.internal.ir.LabelNode;
import org.monflabs.nashorn.internal.ir.LexicalContextNode;
import org.monflabs.nashorn.internal.ir.LexicalContext;
import org.monflabs.nashorn.internal.ir.LiteralNode;
import org.monflabs.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import org.monflabs.nashorn.internal.ir.LiteralNode.PrimitiveLiteralNode;
import org.monflabs.nashorn.internal.ir.LoopNode;
import org.monflabs.nashorn.internal.ir.Node;
import org.monflabs.nashorn.internal.ir.ObjectNode;
import org.monflabs.nashorn.internal.ir.ReturnNode;
import org.monflabs.nashorn.internal.ir.RuntimeNode;
import org.monflabs.nashorn.internal.ir.Statement;
import org.monflabs.nashorn.internal.ir.SwitchNode;
import org.monflabs.nashorn.internal.ir.Symbol;
import org.monflabs.nashorn.internal.ir.ThrowNode;
import org.monflabs.nashorn.internal.ir.TryNode;
import org.monflabs.nashorn.internal.ir.UnaryNode;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.WhileNode;
import org.monflabs.nashorn.internal.ir.WithNode;
import org.monflabs.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import org.monflabs.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import org.monflabs.nashorn.internal.parser.Token;
import org.monflabs.nashorn.internal.parser.TokenType;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.ErrorManager;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.Source;
import org.monflabs.nashorn.internal.runtime.logging.DebugLogger;
import org.monflabs.nashorn.internal.runtime.logging.Loggable;
import org.monflabs.nashorn.internal.runtime.logging.Logger;

/**
 * Lower to more primitive operations. After lowering, an AST still has no symbols
 * and types, but several nodes have been turned into more low level constructs
 * and control flow termination criteria have been computed.
 *
 * We do things like code copying/inlining of finallies here, as it is much
 * harder and context dependent to do any code copying after symbols have been
 * finalized.
 */
@Logger(name="lower")
final class Lower extends NodeOperatorVisitor<BlockLexicalContext> implements Loggable {

    private final DebugLogger log;
    private final Source source;

    // Conservative pattern to test if element names consist of characters valid for identifiers.
    // This matches any non-zero length alphanumeric string including _ and $ and not starting with a digit.
    private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("[a-zA-Z_$][\\w$]*");

    /**
     * Constructor.
     */
    Lower(final Compiler compiler) {
        super(new BlockLexicalContext() {

            @Override
            public List<Statement> popStatements() {
                final List<Statement> newStatements = new ArrayList<>();
                boolean terminated = false;

                final List<Statement> statements = super.popStatements();
                for (final Statement statement : statements) {
                    if (!terminated) {
                        newStatements.add(statement);
                        if (statement.isTerminal() || statement instanceof JumpStatement) { //TODO hasGoto? But some Loops are hasGoto too - why?
                            terminated = true;
                        }
                    } else {
                        FoldConstants.extractVarNodesFromDeadCode(statement, newStatements);
                    }
                }
                return newStatements;
            }

            @Override
            protected Block afterSetStatements(final Block block) {
                final List<Statement> stmts = block.getStatements();
                for(final ListIterator<Statement> li = stmts.listIterator(stmts.size()); li.hasPrevious();) {
                    final Statement stmt = li.previous();
                    // popStatements() guarantees that the only thing after a terminal statement are uninitialized
                    // VarNodes. We skip past those, and set the terminal state of the block to the value of the
                    // terminal state of the first statement that is not an uninitialized VarNode.
                    if(!(stmt instanceof VarNode && ((VarNode)stmt).getInit() == null)) {
                        return block.setIsTerminal(this, stmt.isTerminal());
                    }
                }
                return block.setIsTerminal(this, false);
            }
        });

        this.log = initLogger(compiler.getContext());
        this.source = compiler.getSource();
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        addStatement(breakNode);
        return false;
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        return checkEval(callNode.setFunction(markerFunction(callNode.getFunction())));
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        return true;
    }

    @Override
    public Node leaveCatchNode(final CatchNode catchNode) {
        return addStatement(catchNode);
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        addStatement(continueNode);
        return false;
    }

    @Override
    public boolean enterDebuggerNode(final DebuggerNode debuggerNode) {
        final int line = debuggerNode.getLineNumber();
        final long token = debuggerNode.getToken();
        final int finish = debuggerNode.getFinish();
        addStatement(new ExpressionStatement(line, token, finish, new RuntimeNode(token, finish, RuntimeNode.Request.DEBUGGER, new ArrayList<Expression>())));
        return false;
    }

    @Override
    public boolean enterJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        addStatement(jumpToInlinedFinally);
        return false;
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        return false;
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        final String name = getConstantPropertyName(indexNode.getIndex());
        if (name != null) {
            // If index node is a constant property name convert index node to access node.
            assert indexNode.isIndex();
            final AccessNode access = new AccessNode(indexNode.getToken(), indexNode.getFinish(),
                    indexNode.getBase(), name);
            // super["x"] is a super reference as much as super.x is, and losing
            // that here left the code generator loading "super" as a variable
            return indexNode.isSuper() ? access.setIsSuper() : access;
        }
        return super.leaveIndexNode(indexNode);
    }

    @Override
    public Node leaveDELETE(final UnaryNode delete) {
        final Expression expression = delete.getExpression();
        if (expression instanceof IdentNode || expression instanceof BaseNode) {
            return delete;
        }
        return new BinaryNode(Token.recast(delete.getToken(), TokenType.COMMARIGHT), expression,
                LiteralNode.newInstance(delete.getToken(), delete.getFinish(), true));
    }

    // If expression is a primitive literal that is not an array index and does return its string value. Else return null.
    private static String getConstantPropertyName(final Expression expression) {
        if (expression instanceof LiteralNode.PrimitiveLiteralNode) {
            final Object value = ((LiteralNode) expression).getValue();
            if (value instanceof String && SAFE_PROPERTY_NAME.matcher((String) value).matches()) {
                return (String) value;
            }
        }
        return null;
    }

    @Override
    public Node leaveExpressionStatement(final ExpressionStatement expressionStatement) {
        final Expression expr = expressionStatement.getExpression();
        ExpressionStatement node = expressionStatement;

        final FunctionNode currentFunction = lc.getCurrentFunction();

        if (currentFunction.isProgram()) {
            if (!isInternalExpression(expr) && !isEvalResultAssignment(expr)) {
                node = expressionStatement.setExpression(
                    new BinaryNode(
                        Token.recast(
                            expressionStatement.getToken(),
                            TokenType.ASSIGN),
                        compilerConstant(RETURN),
                    expr));
            }
        }

        if (expressionStatement.destructuringDeclarationType() != null) {
            throwNotImplementedYet("es6.destructuring", expressionStatement);
        }

        return addStatement(node);
    }

    @Override
    public Node leaveBlockStatement(final BlockStatement blockStatement) {
        return addStatement(blockStatement);
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        return super.enterForNode(forNode);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        ForNode newForNode = forNode;

        final Expression test = forNode.getTest();
        if (!forNode.isForInOrOf() && isAlwaysTrue(test)) {
            newForNode = forNode.setTest(lc, null);
        }

        newForNode = checkEscape(newForNode);
        // No enclosing block needed for for-in/of: the parser already created one
        // to capture let/const declarations.
        addResettingStatement(newForNode);
        return newForNode;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        return super.enterFunctionNode(functionNode);
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        log.info("END FunctionNode: ", functionNode.getName());
        return functionNode;
    }

    @Override
    public Node leaveIfNode(final IfNode ifNode) {
        return addResettingStatement(ifNode);
    }

    @Override
    public Node leaveIN(final BinaryNode binaryNode) {
        return new RuntimeNode(binaryNode);
    }

    @Override
    public Node leaveINSTANCEOF(final BinaryNode binaryNode) {
        return new RuntimeNode(binaryNode);
    }

    @Override
    public Node leaveLabelNode(final LabelNode labelNode) {
        return addStatement(labelNode);
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        addStatement(returnNode); //ReturnNodes are always terminal, marked as such in constructor
        return returnNode;
    }

    @Override
    public Node leaveCaseNode(final CaseNode caseNode) {
        // Try to represent the case test as an integer
        final Node test = caseNode.getTest();
        if (test instanceof LiteralNode) {
            final LiteralNode<?> lit = (LiteralNode<?>)test;
            if (lit.isNumeric() && !(lit.getValue() instanceof Integer)) {
                if (JSType.isRepresentableAsInt(lit.getNumber())) {
                    return caseNode.setTest((Expression)LiteralNode.newInstance(lit, lit.getInt32()).accept(this));
                }
            }
        }
        return caseNode;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        if (lc.getCurrentFunction().isProgram()) {
            addResettingStatement(new EmptyNode(switchNode));
        }
        if(!switchNode.isUniqueInteger()) {
            // Wrap it in a block so its internally created tag is restricted in scope
            addStatementEnclosedInBlock(switchNode);
        } else {
            addStatement(switchNode);
        }
        return switchNode;
    }

    @Override
    public Node leaveThrowNode(final ThrowNode throwNode) {
        return addStatement(throwNode); //ThrowNodes are always terminal, marked as such in constructor
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T ensureUniqueNamesIn(final T node) {
        return (T)node.accept(new SimpleNodeVisitor() {
            @Override
            public Node leaveFunctionNode(final FunctionNode functionNode) {
                final String name = functionNode.getName();
                return functionNode.setName(lc, lc.getCurrentFunction().uniqueName(name));
            }

            @Override
            public Node leaveDefault(final Node labelledNode) {
                return labelledNode.ensureUniqueLabels(lc);
            }
        });
    }

    private static Block createFinallyBlock(final Block finallyBody) {
        final List<Statement> newStatements = new ArrayList<>();
        for (final Statement statement : finallyBody.getStatements()) {
            newStatements.add(statement);
            if (statement.hasTerminalFlags()) {
                break;
            }
        }
        return finallyBody.setStatements(null, newStatements);
    }

    private Block catchAllBlock(final TryNode tryNode) {
        final int  lineNumber = tryNode.getLineNumber();
        final long token      = tryNode.getToken();
        final int  finish     = tryNode.getFinish();

        final IdentNode exception = new IdentNode(token, finish, lc.getCurrentFunction().uniqueName(CompilerConstants.EXCEPTION_PREFIX.symbolName()));

        final Block catchBody = new Block(token, finish, new ThrowNode(lineNumber, token, finish, new IdentNode(exception), true));
        assert catchBody.isTerminal(); //ends with throw, so terminal

        final CatchNode catchAllNode  = new CatchNode(lineNumber, token, finish, new IdentNode(exception), null, catchBody, true);
        final Block     catchAllBlock = new Block(token, finish, catchAllNode);

        //catchallblock -> catchallnode (catchnode) -> exception -> throw

        return (Block)catchAllBlock.accept(this); //not accepted. has to be accepted by lower
    }

    private IdentNode compilerConstant(final CompilerConstants cc) {
        final FunctionNode functionNode = lc.getCurrentFunction();
        return new IdentNode(functionNode.getToken(), functionNode.getFinish(), cc.symbolName());
    }

    private static boolean isTerminalFinally(final Block finallyBlock) {
        return finallyBlock.getLastStatement().hasTerminalFlags();
    }

    /**
     * The labels that, from inside {@code tryNode}, name a loop that is itself
     * inside it.
     *
     * The visitor in {@link #spliceFinally} decides whether a jump leaves the
     * try by asking whether its target resolves in a lexical context that
     * begins at the try, so a label outside the try is a label it cannot see.
     * Ordinarily that is the right answer - a jump to something outside does
     * leave - but the desugaring of {@code for-of} puts a try between the loop
     * and a label on it, to close the iterator, and a {@code continue} to that
     * label does not leave the try at all. Splicing the finally into it would
     * close the iterator on every turn of the loop; worse, the spliced jump
     * ends up in a block where the loop is no longer in scope, and the target
     * cannot be resolved at all later on.
     *
     * <p>A label on a statement that contains a loop, with a try in between,
     * cannot arise any other way: {@code continue} naming a label that is not
     * on an iteration statement is an early error, so a label whose loop is
     * inside this try is a label this try was inserted underneath. That
     * reasoning is what limits this to {@code continue}. A {@code break} may
     * name any label, so one naming a statement that holds both this try and a
     * loop leaves the try as any other outward jump does.
     *
     * @param tryNode the try node about to have its finally spliced in
     * @return the names of those labels, empty if the try holds no loop
     */
    private Set<String> loopLabelsInside(final TryNode tryNode) {
        final Set<String> names = new HashSet<>();
        for (final Iterator<LexicalContextNode> iter = lc.getAllNodes(); iter.hasNext();) {
            final LexicalContextNode node = iter.next();
            if (node instanceof FunctionNode || node instanceof LoopNode) {
                // a label further out than a loop names that loop, not this try
                break;
            }
            if (node instanceof LabelNode labelNode) {
                names.add(labelNode.getLabelName());
            }
        }
        return names.isEmpty() || !holdsLoop(tryNode.getBody()) ? Collections.emptySet() : names;
    }

    private static boolean holdsLoop(final Block block) {
        final boolean[] found = new boolean[1];
        block.accept(new SimpleNodeVisitor() {
            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                return false;
            }

            @Override
            public boolean enterDefault(final Node node) {
                if (node instanceof LoopNode) {
                    found[0] = true;
                }
                return !found[0];
            }
        });
        return found[0];
    }

    /**
     * Splice finally code into all endpoints of a trynode
     * @param tryNode the try node
     * @param rethrow the rethrowing throw nodes from the synthetic catch block
     * @param finallyBody the code in the original finally block
     * @return new try node after splicing finally code (same if nop)
     */
    private TryNode spliceFinally(final TryNode tryNode, final ThrowNode rethrow, final Block finallyBody) {
        assert tryNode.getFinallyBody() == null;

        final Block finallyBlock = createFinallyBlock(finallyBody);
        final ArrayList<Block> inlinedFinallies = new ArrayList<>();
        final FunctionNode fn = lc.getCurrentFunction();
        final Set<String> loopLabelsInside = loopLabelsInside(tryNode);
        final TryNode newTryNode = (TryNode)tryNode.accept(new SimpleNodeVisitor() {

            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                // do not enter function nodes - finally code should not be inlined into them
                return false;
            }

            @Override
            public Node leaveThrowNode(final ThrowNode throwNode) {
                if (rethrow == throwNode) {
                    return new BlockStatement(prependFinally(finallyBlock, throwNode));
                }
                return throwNode;
            }

            @Override
            public Node leaveBreakNode(final BreakNode breakNode) {
                return leaveJumpStatement(breakNode);
            }

            @Override
            public Node leaveContinueNode(final ContinueNode continueNode) {
                return leaveJumpStatement(continueNode);
            }

            private Node leaveJumpStatement(final JumpStatement jump) {
                // NOTE: leaveJumpToInlinedFinally deliberately does not delegate to this method, only break and
                // continue are edited. JTIF nodes should not be changed, rather the surroundings of
                // break/continue/return that were moved into the inlined finally block itself will be changed.

                // If this visitor's lc doesn't find the target of the jump, it means it's external to the try block.
                if (jump.getTarget(lc) == null
                        && !(jump instanceof ContinueNode && loopLabelsInside.contains(jump.getLabelName()))) {
                    return createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, jump));
                }
                return jump;
            }

            @Override
            public Node leaveReturnNode(final ReturnNode returnNode) {
                final Expression expr = returnNode.getExpression();
                if (isTerminalFinally(finallyBlock)) {
                    if (expr == null) {
                        // Terminal finally; no return expression.
                        return createJumpToInlinedFinally(fn, inlinedFinallies, ensureUniqueNamesIn(finallyBlock));
                    }
                    // Terminal finally; has a return expression.
                    final List<Statement> newStatements = new ArrayList<>(2);
                    final int retLineNumber = returnNode.getLineNumber();
                    final long retToken = returnNode.getToken();
                    // Expression is evaluated for side effects.
                    newStatements.add(new ExpressionStatement(retLineNumber, retToken, returnNode.getFinish(), expr));
                    newStatements.add(createJumpToInlinedFinally(fn, inlinedFinallies, ensureUniqueNamesIn(finallyBlock)));
                    return new BlockStatement(retLineNumber, new Block(retToken, finallyBlock.getFinish(), newStatements));
                } else if (expr == null || expr instanceof PrimitiveLiteralNode<?> || (expr instanceof IdentNode && RETURN.symbolName().equals(((IdentNode)expr).getName()))) {
                    // Nonterminal finally; no return expression, or returns a primitive literal, or returns :return.
                    // Just move the return expression into the finally block.
                    return createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, returnNode));
                } else {
                    // We need to evaluate the result of the return in case it is complex while still in the try block,
                    // store it in :return, and return it afterwards.
                    final List<Statement> newStatements = new ArrayList<>();
                    final int retLineNumber = returnNode.getLineNumber();
                    final long retToken = returnNode.getToken();
                    final int retFinish = returnNode.getFinish();
                    final Expression resultNode = new IdentNode(expr.getToken(), expr.getFinish(), RETURN.symbolName());
                    // ":return = <expr>;"
                    newStatements.add(new ExpressionStatement(retLineNumber, retToken, retFinish, new BinaryNode(Token.recast(returnNode.getToken(), TokenType.ASSIGN), resultNode, expr)));
                    // inline finally and end it with "return :return;"
                    newStatements.add(createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, returnNode.setExpression(resultNode))));
                    return new BlockStatement(retLineNumber, new Block(retToken, retFinish, newStatements));
                }
            }
        });
        addStatement(inlinedFinallies.isEmpty() ? newTryNode : newTryNode.setInlinedFinallies(lc, inlinedFinallies));
        // TODO: if finallyStatement is terminal, we could just have sites of inlined finallies jump here.
        addStatement(new BlockStatement(finallyBlock));

        return newTryNode;
    }

    private static JumpToInlinedFinally createJumpToInlinedFinally(final FunctionNode fn, final List<Block> inlinedFinallies, final Block finallyBlock) {
        final String labelName = fn.uniqueName(":finally");
        final long token = finallyBlock.getToken();
        final int finish = finallyBlock.getFinish();
        inlinedFinallies.add(new Block(token, finish, new LabelNode(finallyBlock.getFirstStatementLineNumber(),
                token, finish, labelName, finallyBlock)));
        return new JumpToInlinedFinally(labelName);
    }

    private static Block prependFinally(final Block finallyBlock, final Statement statement) {
        final Block inlinedFinally = ensureUniqueNamesIn(finallyBlock);
        if (isTerminalFinally(finallyBlock)) {
            return inlinedFinally;
        }
        final List<Statement> stmts = inlinedFinally.getStatements();
        final List<Statement> newStmts = new ArrayList<>(stmts.size() + 1);
        newStmts.addAll(stmts);
        newStmts.add(statement);
        return new Block(inlinedFinally.getToken(), statement.getFinish(), newStmts);
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        // 13.15.8 evaluates a try as UpdateEmpty(result, undefined): a body that
        // produces no value leaves the statement worth undefined rather than
        // leaving whatever came before it showing through. The reset goes in
        // front of the statement rather than after it, because what the body
        // assigns has to survive - and because by the time the statement is
        // left, a finally block has been spliced through it.
        resetCompletionValue(tryNode);
        return super.enterTryNode(tryNode);
    }

    @Override
    public Node leaveTryNode(final TryNode tryNode) {
        final Block finallyBody = discardCompletionValue(tryNode.getFinallyBody());
        TryNode newTryNode = tryNode.setFinallyBody(lc, null);

        // No finally or empty finally
        if (finallyBody == null || finallyBody.getStatementCount() == 0) {
            final List<CatchNode> catches = newTryNode.getCatches();
            if (catches == null || catches.isEmpty()) {
                // A completely degenerate try block: empty finally, no catches. Replace it with try body.
                return addResettingStatement(new BlockStatement(tryNode.getBody()));
            }
            return addResettingStatement(ensureUnconditionalCatch(newTryNode));
        }

        /*
         * create a new try node
         *    if we have catches:
         *
         *    try            try
         *       x              try
         *    catch               x
         *       y              catch
         *    finally z           y
         *                   catchall
         *                        rethrow
         *
         *   otherwise
         *
         *   try              try
         *      x               x
         *   finally          catchall
         *      y               rethrow
         *
         *
         *   now splice in finally code wherever needed
         *
         */
        final Block catchAll = catchAllBlock(tryNode);

        final List<ThrowNode> rethrows = new ArrayList<>(1);
        catchAll.accept(new SimpleNodeVisitor() {
            @Override
            public boolean enterThrowNode(final ThrowNode throwNode) {
                rethrows.add(throwNode);
                return true;
            }
        });
        assert rethrows.size() == 1;

        if (!tryNode.getCatchBlocks().isEmpty()) {
            final Block outerBody = new Block(newTryNode.getToken(), newTryNode.getFinish(), ensureUnconditionalCatch(newTryNode));
            newTryNode = newTryNode.setBody(lc, outerBody).setCatchBlocks(lc, null);
        }

        newTryNode = newTryNode.setCatchBlocks(lc, Arrays.asList(catchAll));

        /*
         * Now that the transform is done, we have to go into the try and splice
         * the finally block in front of any statement that is outside the try
         */
        return (TryNode)lc.replace(tryNode, spliceFinally(newTryNode, rethrows.get(0), finallyBody));
    }

    private TryNode ensureUnconditionalCatch(final TryNode tryNode) {
        final List<CatchNode> catches = tryNode.getCatches();
        if(catches == null || catches.isEmpty() || catches.get(catches.size() - 1).getExceptionCondition() == null) {
            return tryNode;
        }
        // If the last catch block is conditional, add an unconditional rethrow block
        final List<Block> newCatchBlocks = new ArrayList<>(tryNode.getCatchBlocks());

        newCatchBlocks.add(catchAllBlock(tryNode));
        return tryNode.setCatchBlocks(lc, newCatchBlocks);
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {

        return super.enterUnaryNode(unaryNode);
    }

    @Override
    public boolean enterASSIGN(BinaryNode binaryNode) {
        if (binaryNode.lhs() instanceof ObjectNode || binaryNode.lhs() instanceof ArrayLiteralNode) {
            throwNotImplementedYet("es6.destructuring", binaryNode);
        }
        return super.enterASSIGN(binaryNode);
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        addStatement(varNode);
        if (varNode.getFlag(VarNode.IS_LAST_FUNCTION_DECLARATION)
                && lc.getCurrentFunction().isProgram()
                && ((FunctionNode) varNode.getInit()).isAnonymous()) {
            new ExpressionStatement(varNode.getLineNumber(), varNode.getToken(), varNode.getFinish(), new IdentNode(varNode.getName())).accept(this);
        }
        return varNode;
    }

    @Override
    public Node leaveWhileNode(final WhileNode whileNode) {
        final Expression test = whileNode.getTest();
        final Block body = whileNode.getBody();

        if (isAlwaysTrue(test)) {
            //turn it into a for node without a test.
            final ForNode forNode = (ForNode)new ForNode(whileNode.getLineNumber(), whileNode.getToken(), whileNode.getFinish(), body, 0).accept(this);
            lc.replace(whileNode, forNode);
            return forNode;
        }

         return addResettingStatement(checkEscape(whileNode));
    }

    @Override
    public Node leaveWithNode(final WithNode withNode) {
        return addResettingStatement(withNode);
    }

    /**
     * Given a function node that is a callee in a CallNode, replace it with
     * the appropriate marker function. This is used by {@link CodeGenerator}
     * for fast scope calls
     *
     * @param function function called by a CallNode
     * @return transformed node to marker function or identity if not ident/access/indexnode
     */
    private static Expression markerFunction(final Expression function) {
        if (function instanceof IdentNode) {
            return ((IdentNode)function).setIsFunction();
        } else if (function instanceof BaseNode) {
            return ((BaseNode)function).setIsFunction();
        }
        return function;
    }

    /**
     * Calculate a synthetic eval location for a node for the stacktrace, for example src#17<eval>
     * @param node a node
     * @return eval location
     */
    private String evalLocation(final IdentNode node) {
        final Source source = lc.getCurrentFunction().getSource();
        final int pos = node.position();
        return source.getName() + '#' + source.getLine(pos) + ':' + source.getColumn(pos)
               + "<eval>";
    }

    /**
     * Check whether a call node may be a call to eval. In that case we
     * clone the args in order to create the following construct in
     * {@link CodeGenerator}
     *
     * <pre>
     * if (calledFuntion == buildInEval) {
     *    eval(cloned arg);
     * } else {
     *    cloned arg;
     * }
     * </pre>
     *
     * @param callNode call node to check if it's an eval
     */
    private CallNode checkEval(final CallNode callNode) {
        if (callNode.getFunction() instanceof IdentNode) {

            final List<Expression> args = callNode.getArgs();
            final IdentNode callee = (IdentNode)callNode.getFunction();

            // 'eval' call with at least one argument
            if (!args.isEmpty() && EVAL.symbolName().equals(callee.getName())) {
                final List<Expression> evalArgs = new ArrayList<>(args.size());
                for(final Expression arg: args) {
                    evalArgs.add((Expression)ensureUniqueNamesIn(arg).accept(this));
                }
                return callNode.setEvalArgs(new CallNode.EvalArgs(evalArgs, evalLocation(callee)));
            }
        }

        return callNode;
    }

    /**
     * Helper that given a loop body makes sure that it is not terminal if it
     * has a continue that leads to the loop header or to outer loops' loop
     * headers. This means that, even if the body ends with a terminal
     * statement, we cannot tag it as terminal
     *
     * @param loopBody the loop body to check
     * @return true if control flow may escape the loop
     */
    private static boolean controlFlowEscapes(final LexicalContext lex, final Block loopBody) {
        final List<Node> escapes = new ArrayList<>();

        loopBody.accept(new SimpleNodeVisitor() {
            @Override
            public Node leaveBreakNode(final BreakNode node) {
                escapes.add(node);
                return node;
            }

            @Override
            public Node leaveContinueNode(final ContinueNode node) {
                // all inner loops have been popped.
                if (lex.contains(node.getTarget(lex))) {
                    escapes.add(node);
                }
                return node;
            }
        });

        return !escapes.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private <T extends LoopNode> T checkEscape(final T loopNode) {
        final boolean escapes = controlFlowEscapes(lc, loopNode.getBody());
        if (escapes) {
            return (T)loopNode.
                setBody(lc, loopNode.getBody().setIsTerminal(lc, false)).
                setControlFlowEscapes(lc, escapes);
        }
        return loopNode;
    }


    private Node addStatement(final Statement statement) {
        lc.appendStatement(statement);
        return statement;
    }

    /**
     * Adds a statement whose completion value is undefined unless its body
     * produces one.
     *
     * ES2015 evaluates an if, an iteration statement, a switch, a try and a with
     * as UpdateEmpty(result, undefined): a body that produces no value leaves
     * the statement worth undefined rather than leaving whatever came before it
     * showing through. Nashorn keeps the value a program has reached so far in
     * :return, so all this takes is clearing it first - anything the body does
     * produce assigns over the top.
     */
    /**
     * Stops a finally block from deciding the completion value.
     *
     * ES2015 13.15.8: what a try statement is worth is what its body or its
     * catch produced, and a finally that produces a value of its own does not
     * replace it - "try { 1 } finally { 2 }" is worth 1. The statements of a
     * program assign to :return as they go, so the ones in a finally block have
     * that assignment taken back off them; a function inside it is untouched,
     * since its statements were never a program's.
     */
    private Block discardCompletionValue(final Block finallyBody) {
        if (finallyBody == null || !lc.getCurrentFunction().isProgram()) {
            return finallyBody;
        }
        if (finallyBody.getStatementCount() > 0 && isTerminalFinally(finallyBody)) {
            // ... unless the finally ends abruptly, in which case its completion
            // is the try's - "do { try { 1 } finally { 2; break } } while (false)"
            // is worth 2 - and what came before it never shows. UpdateEmpty makes
            // that undefined for a finally that produced no value of its own, so
            // the value is cleared in front of it rather than kept
            final List<Statement> statements = new ArrayList<>(finallyBody.getStatementCount() + 1);
            statements.add(completionValueReset(finallyBody.getStatements().get(0)));
            statements.addAll(finallyBody.getStatements());
            return finallyBody.setStatements(lc, statements);
        }
        return (Block)finallyBody.accept(new SimpleNodeVisitor() {
            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                return false;
            }

            @Override
            public Node leaveExpressionStatement(final ExpressionStatement statement) {
                if (isEvalResultAssignment(statement.getExpression())) {
                    return statement.setExpression(((BinaryNode)statement.getExpression()).rhs());
                }
                return statement;
            }
        });
    }

    private void resetCompletionValue(final Statement statement) {
        if (lc.getCurrentFunction().isProgram()) {
            addStatement(completionValueReset(statement));
        }
    }

    /** {@code :return = void 0}, at the position of a statement. */
    private ExpressionStatement completionValueReset(final Statement statement) {
        final long token = statement.getToken();
        return new ExpressionStatement(statement.getLineNumber(), token, statement.getFinish(),
                new BinaryNode(Token.recast(token, TokenType.ASSIGN), compilerConstant(RETURN),
                        new UnaryNode(Token.recast(token, TokenType.VOID),
                                LiteralNode.newInstance(token, statement.getFinish(), 0))));
    }

    private Node addResettingStatement(final Statement statement) {
        resetCompletionValue(statement);
        return addStatement(statement);
    }

    private void addStatementEnclosedInBlock(final Statement stmt) {
        BlockStatement b = BlockStatement.createReplacement(stmt, Collections.singletonList(stmt));
        if(stmt.isTerminal()) {
            b = b.setBlock(b.getBlock().setIsTerminal(null, true));
        }
        addStatement(b);
    }

    /**
     * An internal expression has a symbol that is tagged internal. Check if
     * this is such a node
     *
     * @param expression expression to check for internal symbol
     * @return true if internal, false otherwise
     */
    private static boolean isInternalExpression(final Expression expression) {
        if (expression instanceof RuntimeNode runtime) {
            // Closing an iterator is something the desugaring put there, not
            // something the program said, so it is not what the program is worth
            final RuntimeNode.Request request = runtime.getRequest();
            return request == RuntimeNode.Request.ITERATOR_CLOSE
                    || request == RuntimeNode.Request.ITERATOR_CLOSE_QUIET
                    || request == RuntimeNode.Request.ITERATOR_CLOSE_MAYBE;
        }
        if (expression instanceof BinaryNode assignment && assignment.isTokenType(TokenType.ASSIGN)
                && assignment.lhs() instanceof IdentNode target && isInternalName(target.getName())) {
            // Something being put by in a compiler temporary is not what the
            // program is worth either: a class declaration carries its class out
            // of its own scope that way, and 14.5.16 leaves the completion value
            // of one empty.
            return true;
        }
        if (!(expression instanceof IdentNode)) {
            return false;
        }
        final Symbol symbol = ((IdentNode)expression).getSymbol();
        return symbol != null && symbol.isInternal();
    }

    /** Whether a name is one the compiler made up rather than one the program wrote. */
    private static boolean isInternalName(final String name) {
        return !name.isEmpty() && name.charAt(0) == ':';
    }

    /**
     * Is this an assignment to the special variable that hosts scripting eval
     * results, i.e. __return__?
     *
     * @param expression expression to check whether it is $evalresult = X
     * @return true if an assignment to eval result, false otherwise
     */
    private static boolean isEvalResultAssignment(final Node expression) {
        if (expression instanceof BinaryNode) {
            final Node lhs = ((BinaryNode)expression).lhs();
            if (lhs instanceof IdentNode) {
                return ((IdentNode)lhs).getName().equals(RETURN.symbolName());
            }
        }
        return false;
    }

    private void throwNotImplementedYet(final String msgId, final Node node) {
        final long token = node.getToken();
        final int line = source.getLine(node.getStart());
        final int column = source.getColumn(node.getStart());
        final String message = ECMAErrors.getMessage("unimplemented." + msgId);
        final String formatted = ErrorManager.format(message, source, line, column, token);
        throw new RuntimeException(formatted);
    }
}
