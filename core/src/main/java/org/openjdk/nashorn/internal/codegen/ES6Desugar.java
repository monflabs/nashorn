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


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openjdk.nashorn.internal.ir.BinaryNode;
import org.openjdk.nashorn.internal.ir.Block;
import org.openjdk.nashorn.internal.ir.BlockStatement;
import org.openjdk.nashorn.internal.ir.CatchNode;
import org.openjdk.nashorn.internal.ir.ClassNode;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.ir.Expression;
import org.openjdk.nashorn.internal.ir.FunctionNode;
import org.openjdk.nashorn.internal.ir.ForNode;
import org.openjdk.nashorn.internal.ir.ExpressionStatement;
import org.openjdk.nashorn.internal.ir.IdentNode;
import org.openjdk.nashorn.internal.ir.IfNode;
import org.openjdk.nashorn.internal.ir.ReturnNode;
import org.openjdk.nashorn.internal.ir.IndexNode;
import org.openjdk.nashorn.internal.ir.JoinPredecessorExpression;
import org.openjdk.nashorn.internal.ir.LexicalContext;
import org.openjdk.nashorn.internal.ir.LiteralNode;
import org.openjdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import org.openjdk.nashorn.internal.ir.Node;
import org.openjdk.nashorn.internal.ir.ObjectNode;
import org.openjdk.nashorn.internal.ir.PropertyNode;
import org.openjdk.nashorn.internal.ir.RuntimeNode;
import org.openjdk.nashorn.internal.ir.Statement;
import org.openjdk.nashorn.internal.ir.TernaryNode;
import org.openjdk.nashorn.internal.ir.TryNode;
import org.openjdk.nashorn.internal.ir.UnaryNode;
import org.openjdk.nashorn.internal.ir.VarNode;
import org.openjdk.nashorn.internal.ir.visitor.NodeVisitor;
import org.openjdk.nashorn.internal.parser.Token;
import org.openjdk.nashorn.internal.parser.TokenType;

/**
 * Rewrites the ECMAScript 2015 constructs that have no direct representation in
 * bytecode into ones that do: destructuring patterns become a sequence of
 * ordinary bindings driven by the iterator protocol.
 *
 * <p>This runs <em>before</em> {@link Lower}, not inside it, for three reasons.
 * {@code Lower} finalises control flow and assumes its children are already
 * lowered, while destructuring injects statements that then have to be lowered
 * normally. Running before {@code AssignSymbols} means the temporaries
 * synthesised here get symbols and slots like any other variable. And being
 * inside {@code COMPILE_UPTO_CACHED} keeps the on-demand reparse path identical
 * to the eager one.
 *
 * <p><b>This phase never synthesises a {@code FunctionNode}.</b> Lazy
 * recompilation picks nested functions out of a reparsed AST by identity, so a
 * function invented here would make the recompiled tree disagree with the
 * original - a fault that only shows up in the optimistic half of the suite.
 *
 * <p>The parser has already done a useful part of the work: parameter and
 * declaration destructuring both arrive as destructuring <em>assignments</em>,
 * so handling {@code BinaryNode(ASSIGN, pattern, rhs)} covers declarations,
 * plain assignments and for-init in one place.
 */
final class ES6Desugar extends NodeVisitor<LexicalContext> {
    /** Internal temporaries are named with a leading colon, as elsewhere in the compiler. */
    private static final String TEMP_PREFIX = ":destructuring";

    /**
     * Temporaries for a destructuring assignment written where an expression is
     * wanted rather than as a statement of its own.
     *
     * They have a prefix and a counter of their own because the statement path
     * restarts its counter at every statement, and the two would otherwise pick
     * the same names for temporaries that are live at the same time.
     */
    private static final String EXPRESSION_TEMP_PREFIX = ":dstr";

    /**
     * Where a function keeps the {@code this} its arrow functions see.
     *
     * ES2015 8.1.1.3: an arrow function has no this binding of its own and takes
     * the one of the function it was written in, which is why call and apply
     * cannot change it. Nashorn gives every function its own {@code :this}
     * parameter and refuses to let it be captured across a function boundary, so
     * the enclosing function copies it into an ordinary variable that the arrow
     * reads through the scope it already captures.
     */
    private static final String ARROW_THIS = ":arrowThis";

    /**
     * Whether super() has run, in a derived class constructor.
     *
     * ES2015 8.1.1.3 gives such a constructor a this binding that does not exist
     * until super() returns, and a script can tell: reading this early is a
     * ReferenceError, not a look at a half-built object. Nashorn allocates the
     * object before the constructor is entered, so the binding's state is kept
     * beside it.
     */
    static final String THIS_BINDING = ":thisInitialized";

    /** The global binding a default test compares against. */
    private static final String UNDEFINED_NAME = "undefined";

    /** Distinguishes the temporaries of one statement from the next. */
    private int temporaries;

    /**
     * Whether the statement being expanded is a let/const/var declaration
     * rather than a plain assignment. The bindings are assignments either way,
     * but a declaration's are the initialising ones.
     */
    private boolean declaring;

    /** Whether the bindings being produced have to be expressions. */
    private boolean asExpression;

    /** Names the expression path has used, never reused. */
    private int expressionTemporaries;

    /**
     * Declarations for those names, waiting for the enclosing block. They are
     * vars, so the top of the block is as good a place as any.
     */
    private final List<Statement> pendingDeclarations = new ArrayList<>();

    /**
     * The assignments the expression path must keep its hands off: the ones a
     * statement rewrite is going to take whole, and the ones that are not
     * assignments at all but defaults inside a pattern.
     *
     * They are held by token rather than by identity because a node is rebuilt
     * whenever anything below it changes - the visitor hands {@code leave} a
     * copy, not the node {@code enter} saw - while its token, which is its
     * position in the source, stays with it.
     */
    private final Set<Long> handledElsewhere = new HashSet<>();

    /**
     * Whether this is an on-demand compilation, in which every nested function
     * but the one being compiled has a body the parser did not read.
     */
    private final boolean onDemand;

    ES6Desugar(final boolean onDemand) {
        super(new LexicalContext());
        this.onDemand = onDemand;
    }

    /**
     * Whether a function's body is one the parser skipped, and so must be left
     * exactly as empty as it was found.
     *
     * A class's synthesised default constructor is the exception: it has a body
     * on every compilation, because the parser builds it rather than reading it.
     */
    private boolean isSkipped(final FunctionNode functionNode) {
        return onDemand && functionNode != lc.getOutermostFunction()
                && !functionNode.isDefaultClassConstructor();
    }

    /**
     * Whether an expression is a destructuring pattern rather than a value.
     *
     * @param expression the left hand side of an assignment
     * @return true for array and object patterns
     */
    static boolean isPattern(final Expression expression) {
        return expression instanceof ArrayLiteralNode || expression instanceof ObjectNode;
    }

    /**
     * A destructuring assignment that is the whole of a statement, or the whole
     * initialiser of one, is rewritten by {@link #expand} into a sequence of
     * statements, which reads better than the expression form and is what the
     * declaration cases need - so the expression path is told to leave it.
     */
    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        claim(expressionStatement.getExpression());
        return super.enterExpressionStatement(expressionStatement);
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        claim(varNode.getInit());
        return super.enterVarNode(varNode);
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        claim(forNode.getInit());
        if (forNode.isForInOrOf() && forNode.getInit() != null && isPattern(forNode.getInit())) {
            markPatternInterior(forNode.getInit());
        }
        return super.enterForNode(forNode);
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        if (catchNode.getException() != null && isPattern(catchNode.getException())) {
            markPatternInterior(catchNode.getException());
        }
        return super.enterCatchNode(catchNode);
    }

    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        if (binaryNode.isTokenType(TokenType.ASSIGN) && isPattern(binaryNode.lhs())) {
            markPatternInterior(binaryNode.lhs());
        }
        return super.enterBinaryNode(binaryNode);
    }

    /**
     * Records the defaults of a pattern, so that the expression path does not
     * mistake them for assignments.
     *
     * Inside a pattern, {@code [x, y] = [4, 5]} is the default for a nested
     * pattern rather than an assignment - it is only evaluated when the value
     * matched against it is undefined, and {@link #destructure} emits it. It
     * parses as the same node as a real assignment, so the two are told apart
     * here, on the way down, before the expression path can see either.
     */
    private void markPatternInterior(final Expression pattern) {
        if (pattern instanceof ArrayLiteralNode array) {
            for (final Expression element : array.getValue()) {
                markTarget(element);
            }
        } else if (pattern instanceof ObjectNode object) {
            for (final PropertyNode property : object.getElements()) {
                markTarget(property.getValue());
            }
        }
    }

    /** Declares, as lets, every name a binding pattern binds. */
    private void declareBoundNames(final Statement at, final Expression pattern, final List<Statement> statements) {
        if (pattern instanceof ArrayLiteralNode array) {
            for (final Expression element : array.getValue()) {
                declareBoundName(at, element, statements);
            }
        } else if (pattern instanceof ObjectNode object) {
            for (final PropertyNode property : object.getElements()) {
                declareBoundName(at, property.getValue(), statements);
            }
        }
    }

    private void declareBoundName(final Statement at, final Expression target, final List<Statement> statements) {
        if (target == null) {
            return;
        }
        if (target instanceof UnaryNode rest && rest.isTokenType(TokenType.SPREAD_ARRAY)) {
            declareBoundName(at, rest.getExpression(), statements);
        } else if (target instanceof BinaryNode withDefault && withDefault.isTokenType(TokenType.ASSIGN)) {
            declareBoundName(at, withDefault.lhs(), statements);
        } else if (target instanceof IdentNode name) {
            statements.add(new VarNode(at.getLineNumber(), Token.recast(at.getToken(), TokenType.LET),
                    at.getFinish(), new IdentNode(name).setIsDeclaredHere(), null, VarNode.IS_LET));
        } else {
            declareBoundNames(at, target, statements);
        }
    }

    private void markTarget(final Expression target) {
        if (target == null) {
            return;
        }
        if (target instanceof UnaryNode rest && rest.isTokenType(TokenType.SPREAD_ARRAY)) {
            markTarget(rest.getExpression());
        } else if (target instanceof BinaryNode withDefault && withDefault.isTokenType(TokenType.ASSIGN)) {
            // the default's own right hand side is an ordinary expression, and
            // an assignment there is a real one
            handledElsewhere.add(withDefault.getToken());
            markTarget(withDefault.lhs());
        } else {
            markPatternInterior(target);
        }
    }

    private void claim(final Expression expression) {
        if (expression instanceof BinaryNode assignment && assignment.isTokenType(TokenType.ASSIGN)) {
            if (isPattern(assignment.lhs())) {
                handledElsewhere.add(assignment.getToken());
            } else if (assignment.rhs() instanceof BinaryNode inner
                    && inner.isTokenType(TokenType.ASSIGN) && isPattern(inner.lhs())) {
                // "x = [a] = xs", which expandChainedDestructuring takes whole
                handledElsewhere.add(inner.getToken());
            }
        }
    }

    /**
     * {@code f([a] = xs)} - a destructuring assignment written where a value is
     * wanted.
     *
     * ES2015 12.14.5 says one evaluates to the object it took apart, and it can
     * appear anywhere an expression can: in a call, a condition, a comma, a
     * return. Taking a pattern apart is a sequence of steps, so the expression
     * form is that sequence joined by the comma operator, ending with the
     * temporary the object was read into.
     *
     * A binary node may be replaced by a node of any kind, which is what makes
     * this possible here and not for the super() call.
     */
    @Override
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        if (!binaryNode.isTokenType(TokenType.ASSIGN)
                || !isPattern(binaryNode.lhs())
                || handledElsewhere.contains(binaryNode.getToken())) {
            return super.leaveBinaryNode(binaryNode);
        }
        return destructuringExpression(binaryNode);
    }

    private Expression destructuringExpression(final BinaryNode assignment) {
        final long token = assignment.getToken();
        final int finish = assignment.getFinish();
        final int line = lc.getCurrentFunction().getLineNumber();
        final String value = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;

        // destructure() works in statements, so it is given a carrier to hang
        // line and token information on, and its output is turned back into
        // expressions afterwards
        final Statement at = new ExpressionStatement(line, token, finish, assignment);
        final List<Statement> work = new ArrayList<>();
        final boolean wasDeclaring = declaring;
        final boolean wasExpression = asExpression;
        declaring = false;
        asExpression = true;
        try {
            destructure(at, assignment.lhs(), ref(at, value), work);
        } finally {
            declaring = wasDeclaring;
            asExpression = wasExpression;
        }

        pendingDeclarations.add(declareTemporary(at, value));
        Expression chain = new BinaryNode(Token.recast(token, TokenType.ASSIGN),
                ref(at, value), assignment.rhs());
        for (final Statement statement : work) {
            chain = comma(token, chain, asExpression(statement));
        }
        return comma(token, chain, ref(at, value));
    }

    /** One step of the sequence, as an expression rather than a statement. */
    private Expression asExpression(final Statement statement) {
        if (statement instanceof ExpressionStatement expressionStatement) {
            return expressionStatement.getExpression();
        }
        final VarNode declaration = (VarNode)statement;
        // the name is declared at the top of the block; here it is only assigned
        pendingDeclarations.add(declaration.setInit(null));
        return new BinaryNode(Token.recast(declaration.getToken(), TokenType.ASSIGN),
                declaration.getName(), declaration.getInit());
    }

    private static Expression comma(final long token, final Expression left, final Expression right) {
        return new BinaryNode(Token.recast(token, TokenType.COMMARIGHT), left, right);
    }

    /**
     * Expands the destructuring statements of a block in place.
     *
     * The expansion has to become part of <em>this</em> block rather than being
     * wrapped in one of its own: a block is a scope, so bindings from
     * "let [a] = xs" put inside a synthetic block would go out of scope on the
     * very next line.
     */
    @Override
    public Node leaveBlock(final Block block) {
        final List<Statement> statements = block.getStatements();
        List<Statement> expanded = null;

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            final List<Statement> replacement = expand(statement);
            if (replacement == null) {
                if (expanded != null) {
                    expanded.add(statement);
                }
                continue;
            }
            if (expanded == null) {
                expanded = new ArrayList<>(statements.subList(0, i));
            }
            expanded.addAll(replacement);
        }

        if (!pendingDeclarations.isEmpty()) {
            // the temporaries an expression-position destructuring needed. They
            // are vars, so the top of this block is as good a place as any.
            final List<Statement> declared = new ArrayList<>(pendingDeclarations);
            pendingDeclarations.clear();
            declared.addAll(expanded == null ? statements : expanded);
            expanded = declared;
        }
        // A block that did not change has to be handed back as it stands: a
        // rebuilt one is a different node, and its parent then rebuilds too,
        // which loses whatever an ExpressionStatement was carrying beside its
        // expression - a destructuring declaration's let or const, for one.
        return super.leaveBlock(expanded == null ? block : block.setStatements(lc, expanded));
    }

    /**
     * The bindings one statement expands into, or null if it is not a
     * destructuring statement and should be left alone.
     */
    private List<Statement> expand(final Statement statement) {
        if (statement instanceof ForNode forNode) {
            return forNode.isForInOrOf() ? expandForInOrOf(forNode) : expandForInitialiser(forNode);
        }
        if (statement instanceof TryNode) {
            // already rewritten, or none of our business
            return null;
        }
        if (statement instanceof VarNode varNode) {
            return expandDestructuringInitialiser(varNode);
        }
        if (!(statement instanceof ExpressionStatement expressionStatement)) {
            return null;
        }
        final Expression expression = expressionStatement.getExpression();
        if (!(expression instanceof BinaryNode assignment) || !assignment.isTokenType(TokenType.ASSIGN)) {
            return null;
        }
        if (!isPattern(assignment.lhs())) {
            return expandChainedDestructuring(expressionStatement, assignment);
        }

        temporaries = 0;
        declaring = expressionStatement.destructuringDeclarationType() != null;
        final List<Statement> bindings = new ArrayList<>();
        destructure(expressionStatement, assignment.lhs(), assignment.rhs(), bindings);
        return bindings;
    }

    /**
     * {@code var x = [a, b] = xs}, the same thing in a declaration.
     */
    private List<Statement> expandDestructuringInitialiser(final VarNode varNode) {
        if (!(varNode.getInit() instanceof BinaryNode inner)
                || !inner.isTokenType(TokenType.ASSIGN)
                || !isPattern(inner.lhs())) {
            return null;
        }

        temporaries = 0;
        declaring = false;
        final String value = newTemporary();

        final List<Statement> statements = new ArrayList<>();
        statements.add(temporaryFor(varNode, value, inner.rhs()));
        destructure(varNode, inner.lhs(), ref(varNode, value), statements);
        statements.add(varNode.setInit(ref(varNode, value)));
        return statements;
    }

    /**
     * {@code x = [a, b] = xs}, a destructuring assignment used for its value.
     *
     * ES2015 12.14.5 says the value of one is the object it destructured, which a
     * pattern taken apart into a sequence of bindings no longer leaves anywhere.
     * The right hand side is therefore read into a temporary that the pattern is
     * matched against and that the outer assignment then takes.
     */
    private List<Statement> expandChainedDestructuring(final ExpressionStatement statement,
            final BinaryNode assignment) {
        if (!(assignment.rhs() instanceof BinaryNode inner)
                || !inner.isTokenType(TokenType.ASSIGN)
                || !isPattern(inner.lhs())) {
            return null;
        }

        temporaries = 0;
        declaring = false;
        final String value = newTemporary();

        final List<Statement> statements = new ArrayList<>();
        statements.add(temporaryFor(statement, value, inner.rhs()));
        destructure(statement, inner.lhs(), ref(statement, value), statements);
        statements.add(new ExpressionStatement(statement.getLineNumber(), statement.getToken(),
                statement.getFinish(),
                new BinaryNode(assignment.getToken(), assignment.lhs(), ref(statement, value))));
        return statements;
    }

    /**
     * {@code yield value}.
     *
     * The body runs on its own thread, so a yield is an ordinary call that
     * blocks until the generator is advanced again - no state machine, and no
     * restriction on where a yield may appear.
     */
    @Override
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        if (unaryNode.isTokenType(TokenType.YIELD) || unaryNode.isTokenType(TokenType.YIELD_STAR)) {
            final RuntimeNode.Request request = unaryNode.isTokenType(TokenType.YIELD_STAR)
                    ? RuntimeNode.Request.YIELD_STAR
                    : RuntimeNode.Request.YIELD;
            return new RuntimeNode(unaryNode.getToken(), unaryNode.getFinish(), request,
                    unaryNode.getExpression());
        }
        return super.leaveUnaryNode(unaryNode);
    }

    /**
     * {@code function f(a, ...rest) body}.
     *
     * The rest parameter leaves the parameter list - which also gives
     * Function.length the arity the spec asks for, counting only the parameters
     * before it - and becomes a local bound at the top of the body from the
     * argument array. The function is marked as having had one so that it is
     * compiled variable arity and that array exists.
     */
    /**
     * {@code return x} in a derived class constructor.
     *
     * ES2015 9.2.2 step 13 makes the completion value of one mean something it
     * means nowhere else: an object is the result, undefined hands back the
     * object super() made, and anything else is a TypeError where a base
     * constructor would simply have ignored it.
     */
    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        if (!lc.getCurrentFunction().isSubclassConstructor()) {
            return super.leaveReturnNode(returnNode);
        }
        final long token = returnNode.getToken();
        final int finish = returnNode.getFinish();
        final Expression value = returnNode.getExpression();
        return returnNode.setExpression(new RuntimeNode(token, finish, RuntimeNode.Request.DERIVED_RETURN,
                value == null ? new IdentNode(token, finish, "undefined") : value,
                new IdentNode(token, finish, THIS_BINDING)));
    }

    /** {@code this}, checked against the binding having been made. */
    private static Expression checkedThis(final long token, final int finish) {
        return new RuntimeNode(token, finish, RuntimeNode.Request.REQUIRE_THIS_INITIALIZED,
                new IdentNode(token, finish, THIS_BINDING));
    }

    /**
     * {@code this} inside an arrow function, which is the enclosing function's.
     *
     * The nearest enclosing function that is not itself an arrow is the one that
     * publishes it - the parser marked it while parsing this very reference -
     * and arrows nested in arrows all reach the same one, because none of them
     * declares the variable.
     */
    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        if (!CompilerConstants.THIS.symbolName().equals(identNode.getName())) {
            return super.leaveIdentNode(identNode);
        }

        final FunctionNode function = lc.getCurrentFunction();
        if (function.getKind() == FunctionNode.Kind.ARROW) {
            return new IdentNode(identNode.getToken(), identNode.getFinish(), ARROW_THIS);
        }
        if (function.isSubclassConstructor()) {
            return checkedThis(identNode.getToken(), identNode.getFinish());
        }
        return super.leaveIdentNode(identNode);
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        if (isSkipped(functionNode)) {
            return super.leaveFunctionNode(functionNode);
        }

        final FunctionNode withGenerator =
                publishThis(bindThis(addGeneratorPrologue(addClassConstructorGuard(moduleEnvironment(functionNode)))));
        final List<IdentNode> parameters = withGenerator.getParameters();
        if (parameters.isEmpty() || !parameters.get(parameters.size() - 1).isRestParameter()) {
            return super.leaveFunctionNode(withGenerator);
        }
        final FunctionNode functionNode0 = withGenerator;

        final IdentNode rest = parameters.get(parameters.size() - 1);
        final List<IdentNode> declared = parameters.subList(0, parameters.size() - 1);

        final Block body = functionNode0.getBody();
        final List<Statement> statements = new ArrayList<>();
        statements.add(new VarNode(functionNode0.getLineNumber(),
                Token.recast(functionNode0.getToken(), TokenType.VAR), rest.getFinish(),
                new IdentNode(rest.getToken(), rest.getFinish(), rest.getName()),
                new RuntimeNode(rest.getToken(), rest.getFinish(), RuntimeNode.Request.REST_ARGUMENTS,
                        LiteralNode.newInstance(rest.getToken(), rest.getFinish(), declared.size()))));
        statements.addAll(body.getStatements());

        return super.leaveFunctionNode(functionNode0
                .setFlag(lc, FunctionNode.ES6_HAS_REST_PARAMETER)
                .setParameters(lc, new ArrayList<>(declared))
                .setBody(lc, body.setStatements(lc, statements)));
    }

    /**
     * A class definition.
     *
     * The whole class becomes one runtime call rather than a sequence of
     * property assignments, because the ordering rules are not an object
     * literal's: a computed key has to be evaluated in source order interleaved
     * with the methods, and class elements are non-enumerable, which the object
     * literal path cannot express. The elements are passed as one flat array of
     * key, flags and value so that the call has a fixed arity.
     *
     * The functions are the parser's own - nothing here synthesises a
     * FunctionNode, including the default constructor, which the parser already
     * supplies as {@code constructor(...args) { super(...args) }}.
     */
    @Override
    public Node leaveClassNode(final ClassNode classNode) {
        final long token = classNode.getToken();
        final int finish = classNode.getFinish();

        final List<Expression> elements = new ArrayList<>();
        for (final PropertyNode element : classNode.getClassElements()) {
            addClassElement(elements, element);
        }

        final Expression heritage = classNode.getClassHeritage();
        return new RuntimeNode(token, finish, RuntimeNode.Request.DEFINE_CLASS,
                classNode.getConstructor().getValue(),
                heritage == null ? LiteralNode.newInstance(token, finish) : heritage,
                LiteralNode.newInstance(token, finish, heritage != null),
                LiteralNode.newInstance(token, finish, elements));
    }

    /**
     * Appends one element's key, flags and value to the flattened element list.
     *
     * A get/set pair written as two class elements arrives as a single
     * PropertyNode holding both, and has to go back out as two entries or the
     * setter is dropped.
     */
    private static void addClassElement(final List<Expression> elements, final PropertyNode element) {
        final int shared = element.isStatic() ? ScriptRuntime.CLASS_ELEMENT_STATIC : 0;

        if (element.getGetter() != null || element.getSetter() != null) {
            if (element.getGetter() != null) {
                addEntry(elements, element, shared | ScriptRuntime.CLASS_ELEMENT_GETTER, element.getGetter());
            }
            if (element.getSetter() != null) {
                addEntry(elements, element, shared | ScriptRuntime.CLASS_ELEMENT_SETTER, element.getSetter());
            }
            return;
        }

        addEntry(elements, element, shared, element.getValue());
    }

    private static void addEntry(final List<Expression> elements, final PropertyNode element, final int flags,
            final Expression value) {
        elements.add(keyOf(element));
        elements.add(LiteralNode.newInstance(element.getToken(), element.getFinish(), flags));
        elements.add(value);
    }

    /**
     * A class element's key as a value expression. A written name is an
     * IdentNode standing for a string; a computed key is already an expression.
     */
    private static Expression keyOf(final PropertyNode element) {
        final Expression key = element.getKey();
        if (!element.isComputed() && key instanceof IdentNode name) {
            return LiteralNode.newInstance(key.getToken(), key.getFinish(), name.getName());
        }
        return key;
    }

    /**
     * A class constructor may only be reached with new, so it says so itself.
     *
     * The guard goes in the body rather than at the call site because a class
     * constructor is an ordinary function object as far as the linker is
     * concerned, and it can be reached by apply, by call, or by a bound wrapper.
     */
    private FunctionNode addClassConstructorGuard(final FunctionNode functionNode) {
        if (!functionNode.isClassConstructor()) {
            return functionNode;
        }
        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();
        final List<Statement> statements = new ArrayList<>();
        statements.add(new ExpressionStatement(functionNode.getLineNumber(), token, finish,
                new RuntimeNode(token, finish, RuntimeNode.Request.REQUIRE_NEW)));
        statements.addAll(body.getStatements());
        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * Gives a generator function the prologue that turns it into a generator.
     *
     * A generator function is compiled as an ordinary function that plays two
     * roles: called normally it must hand back a generator object without
     * running anything, and the body runs later by calling the same function
     * again from the generator's own thread. The prologue distinguishes them.
     */
    /**
     * Declares the this-binding state of a derived class constructor, and checks
     * at the end that super() actually ran.
     *
     * ES2015 9.2.2 step 13: a derived constructor that falls off its end returns
     * its this binding, and reading a binding that was never made is a
     * ReferenceError - so a constructor that forgets super() fails there rather
     * than quietly returning a half-built object.
     */
    private FunctionNode bindThis(final FunctionNode functionNode) {
        if (!functionNode.isSubclassConstructor()) {
            return functionNode;
        }

        final long token = Token.recast(functionNode.getToken(), TokenType.VAR);
        final int finish = functionNode.getFinish();
        final int line = functionNode.getLineNumber();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new VarNode(line, token, finish, new IdentNode(token, finish, THIS_BINDING),
                new RuntimeNode(token, finish, RuntimeNode.Request.UNINITIALIZED_THIS)));
        statements.addAll(body.getStatements());
        statements.add(new ReturnNode(line, token, finish, checkedThis(token, finish)));

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * A module hands over the scope that is its environment, as its first act.
     *
     * ES2015 15.2.1.17 gives a module's top level declarations a scope of their
     * own rather than the global object, and the importing side has to reach
     * them by name, so every one of them is put in scope. The call is the only
     * moment at which the imports can be installed: after the scope exists and
     * before any of the body has run.
     */
    private FunctionNode moduleEnvironment(final FunctionNode functionNode) {
        if (!functionNode.isModule()) {
            return functionNode;
        }

        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new ExpressionStatement(functionNode.getLineNumber(), token, finish,
                new RuntimeNode(token, finish, RuntimeNode.Request.MODULE_SCOPE)));
        statements.addAll(body.getStatements());

        return functionNode.setFlag(lc, FunctionNode.HAS_ALL_VARS_IN_SCOPE)
                .setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * Copies {@code this} into a variable an arrow function can capture, for a
     * function that contains one reading it.
     */
    private FunctionNode publishThis(final FunctionNode functionNode) {
        if (!functionNode.arrowUsesThis()) {
            return functionNode;
        }

        final long token = Token.recast(functionNode.getToken(), TokenType.VAR);
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new VarNode(functionNode.getLineNumber(), token, finish,
                new IdentNode(token, finish, ARROW_THIS),
                new IdentNode(token, finish, CompilerConstants.THIS.symbolName())));
        statements.addAll(body.getStatements());

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    private FunctionNode addGeneratorPrologue(final FunctionNode functionNode) {
        if (functionNode.getKind() != FunctionNode.Kind.GENERATOR) {
            return functionNode;
        }

        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final int line = functionNode.getLineNumber();
        final String created = ":generator";

        // ES2015 25.2.1.1: a generator's parameters are bound when it is called,
        // and only then is the generator object made, so a default that throws
        // or a pattern that does not match fails at the call rather than at the
        // first next().
        //
        // The body is run by re-entering the whole function on the generator's
        // own thread, which means the parameter list is not somewhere the caller
        // can simply run first: whatever runs it, runs it on one thread only,
        // and its bindings are locals of that invocation. So for a function that
        // has a parameter list worth speaking of - the parser gives one a
        // parameter block, with the real body nested inside it - the thread is
        // started by the call rather than by the first next(), and the call
        // waits at the head of the nested body, by which point the parameters
        // are bound. Everything a parameter list can do therefore happens once,
        // in the right order, and before the call returns.
        final Block outer = functionNode.getBody();
        final Block body = parameterisedBody(outer);
        final boolean parameterised = body != outer;
        final List<Statement> statements = new ArrayList<>();

        // var :generator = GENERATOR_ENTER();
        statements.add(new VarNode(line, Token.recast(token, TokenType.VAR), finish,
                new IdentNode(token, finish, created),
                new RuntimeNode(token, finish, parameterised
                        ? RuntimeNode.Request.GENERATOR_ENTER_PARAMETERS
                        : RuntimeNode.Request.GENERATOR_ENTER)));

        // if (:generator !== undefined) { return :generator; }
        final Expression isNotUndefined = new RuntimeNode(token, finish, RuntimeNode.Request.IS_NOT_UNDEFINED,
                new IdentNode(token, finish, created), new IdentNode(token, finish, UNDEFINED_NAME));
        final Block returnBlock = new Block(token, finish,
                new ReturnNode(line, token, finish, new IdentNode(token, finish, created)));
        statements.add(new IfNode(line, token, finish, isNotUndefined, returnBlock, null));

        statements.addAll(outer.getStatements());

        if (parameterised) {
            // The head of the nested body is where the call gets its generator
            // object back and the body waits for its first next(): the parameter
            // statements are in front of it, and nothing of the body proper is.
            final List<Statement> nested = new ArrayList<>();
            nested.add(new ExpressionStatement(line, token, finish,
                    new RuntimeNode(token, finish, RuntimeNode.Request.GENERATOR_PARAMETERS_BOUND)));
            nested.addAll(body.getStatements());
            statements.set(statements.size() - 1,
                    new BlockStatement(line, body.setStatements(lc, nested)));
        }

        // A generator is compiled with an arguments object. It needs the argument
        // array to replay the call on the generator's thread, and going through
        // arguments rather than merely forcing variable arity is what makes the
        // parameter reads safe: a bare varargs function indexes the array without
        // a bounds check, so a generator called with fewer arguments than it
        // declares would fail with ArrayIndexOutOfBoundsException.
        return functionNode
                .setFlag(lc, FunctionNode.USES_ARGUMENTS)
                .setBody(lc, outer.setStatements(lc, statements));
    }

    /**
     * The block a function's own statements live in, which is the function body
     * unless a parameter list desugared into statements of its own - then it is
     * the block nested at the end of the parameter block.
     */
    private static Block parameterisedBody(final Block body) {
        if (!body.isParameterBlock() || body.getStatements().isEmpty()) {
            return body;
        }
        return body.getLastStatement() instanceof BlockStatement nested ? nested.getBlock() : body;
    }


    /**
     * {@code catch ([e]) body}.
     *
     * The exception has to arrive in a plain binding - the catch clause names a
     * single slot - so the pattern is matched against a temporary at the top of
     * the handler instead.
     */
    @Override
    public Node leaveCatchNode(final CatchNode catchNode) {
        final Expression exception = catchNode.getException();
        if (exception == null || !isPattern(exception)) {
            return super.leaveCatchNode(catchNode);
        }

        temporaries = 0;
        // ES2015 13.15.7: the names a catch parameter's pattern binds are the
        // catch clause's own bindings. Nobody has declared them - the parser
        // declares a plain "catch (e)" through the catch node itself, and has no
        // single identifier to declare when the parameter is a pattern - so the
        // declarations are emitted here, in front of the bindings, exactly as
        // the parser emits them for "let [a] = xs".
        declaring = true;
        final String caught = newTemporary();

        final Block body = catchNode.getBody();
        final List<Statement> statements = new ArrayList<>();
        declareBoundNames(catchNode, exception, statements);
        destructure(catchNode, exception, ref(catchNode, caught), statements);
        statements.addAll(body.getStatements());

        return super.leaveCatchNode(catchNode
                .setException(ref(catchNode, caught))
                .setBody(body.setStatements(lc, statements)));
    }

    /**
     * {@code for (var [k, v] of entries) body}.
     *
     * The loop itself cannot destructure, so it binds one temporary per
     * iteration and the pattern is matched against that temporary at the top of
     * the body - which is also where the spec says the binding happens.
     */
    private List<Statement> expandForInOrOf(final ForNode forNode) {
        final Expression init = forNode.getInit();
        final boolean pattern = init != null && isPattern(init);
        if (!pattern && !forNode.isForOf()) {
            return null;
        }

        temporaries = 0;
        // A for-in/of pattern binds afresh on every iteration, so its names are
        // being initialised rather than reassigned. The parser marks the plain
        // "for (const x of xs)" case itself but has no identifier to mark when
        // the binding is a pattern.
        declaring = true;

        ForNode loop = forNode;
        final List<Statement> hoisted = new ArrayList<>();
        if (pattern) {
            final String element = newTemporary();
            final List<Statement> bindings = new ArrayList<>();
            destructure(forNode, init, ref(forNode, element), bindings);

            final Block body = forNode.getBody();
            final List<Statement> statements = new ArrayList<>(bindings);
            statements.addAll(body.getStatements());

            hoisted.add(declareTemporary(forNode, element));
            loop = loop.setInit(lc, ref(forNode, element))
                       .setBody(lc, body.setStatements(lc, statements));
        }

        if (forNode.isForOf()) {
            hoisted.addAll(closingIteration(loop));
        } else {
            hoisted.add(loop);
        }
        return hoisted;
    }

    /**
     * {@code for (x of xs) body}, wrapped so that leaving the loop early tells
     * the iterator about it.
     *
     * ES2015 13.7.5.13 calls IteratorClose on any abrupt completion of a for-of -
     * break, return, throw, or a labelled break out of an enclosing statement -
     * which is what lets a generator being iterated run its finally blocks. That
     * is a try/finally, and rather than emit one by hand in the code generator,
     * where the loop is a bare java.util.Iterator with no handler around it, the
     * loop is put inside a real one here and left to the lowering phase.
     *
     * The iterator has to be reachable from the finally block, so it is obtained
     * here into a temporary instead of by the loop itself; the code generator's
     * call to it then passes the temporary straight through. Closing an iterator
     * that ran to completion is a no-op, so the normal exit costs nothing beyond
     * the call.
     */
    private List<Statement> closingIteration(final ForNode forNode) {
        final String iterator = newTemporary();
        final Expression get = runtime(forNode, RuntimeNode.Request.GET_ITERATOR,
                forNode.getModify().getExpression());
        final Expression store = new BinaryNode(Token.recast(forNode.getToken(), TokenType.ASSIGN),
                ref(forNode, iterator), get);

        final ForNode loop = forNode.setModify(lc, new JoinPredecessorExpression(store));
        final Block body = new Block(forNode.getToken(), forNode.getFinish(), loop);
        final Block close = new Block(forNode.getToken(), forNode.getFinish(),
                new ExpressionStatement(forNode.getLineNumber(), forNode.getToken(), forNode.getFinish(),
                        runtime(forNode, RuntimeNode.Request.ITERATOR_CLOSE_QUIET, ref(forNode, iterator))));

        return List.of(declareTemporary(forNode, iterator),
                new TryNode(forNode.getLineNumber(), forNode.getToken(), forNode.getFinish(),
                        body, List.of(), close));
    }

    /**
     * {@code for (var [a] = xs; test; next) body}.
     *
     * A C-style initialiser runs exactly once before the loop, so the bindings
     * can simply be hoisted in front of it.
     */
    private List<Statement> expandForInitialiser(final ForNode forNode) {
        if (!(forNode.getInit() instanceof BinaryNode assignment)
                || !assignment.isTokenType(TokenType.ASSIGN)
                || !isPattern(assignment.lhs())) {
            return null;
        }

        temporaries = 0;
        declaring = true;
        final List<Statement> statements = new ArrayList<>();
        destructure(forNode, assignment.lhs(), assignment.rhs(), statements);
        statements.add(forNode.setInit(lc, null));
        return statements;
    }

    /**
     * Binds every name in one pattern against a value.
     *
     * @param at          the statement being replaced, for line and token information
     * @param pattern     an array or object pattern
     * @param value       the expression it is being matched against
     * @param statements  where the resulting bindings are appended
     */
    private void destructure(final Statement at, final Expression pattern, final Expression value,
            final List<Statement> statements) {
        if (pattern instanceof ArrayLiteralNode array) {
            destructureArray(at, array, value, statements);
        } else {
            destructureObject(at, (ObjectNode)pattern, value, statements);
        }
    }

    /**
     * {@code [a, , b = 1, ...rest] = xs}, driven by the iterator protocol so that
     * it works on anything iterable rather than only on arrays.
     */
    private void destructureArray(final Statement at, final ArrayLiteralNode pattern, final Expression value,
            final List<Statement> statements) {
        final String iterator = newTemporary();
        statements.add(temporaryFor(at, iterator, runtime(at, RuntimeNode.Request.GET_ITERATOR, value)));

        if (asExpression) {
            // A comma chain has nowhere to put a try, so a pattern written where
            // an expression is wanted does without the guard below.
            destructureArrayElements(at, pattern, iterator, statements);
            return;
        }

        final List<Statement> guarded = new ArrayList<>();
        destructureArrayElements(at, pattern, iterator, guarded);

        // ES2015 12.14.5.3: a pattern that gives up part way through tells its
        // iterator so, and it gives up as readily by throwing - out of a target
        // reference, a default, or a nested pattern - as by running out of
        // elements to bind. Closing twice is closing once.
        statements.add(new TryNode(at.getLineNumber(), at.getToken(), at.getFinish(),
                new Block(at.getToken(), at.getFinish(), guarded), List.of(),
                new Block(at.getToken(), at.getFinish(),
                        new ExpressionStatement(at.getLineNumber(), at.getToken(), at.getFinish(),
                                runtime(at, RuntimeNode.Request.ITERATOR_CLOSE_QUIET, ref(at, iterator))))));
    }

    private void destructureArrayElements(final Statement at, final ArrayLiteralNode pattern,
            final String iterator, final List<Statement> statements) {
        for (final Expression element : pattern.getValue()) {
            if (element == null) {
                // an elision still consumes an element
                statements.add(new ExpressionStatement(at.getLineNumber(), at.getToken(), at.getFinish(),
                        runtime(at, RuntimeNode.Request.ITERATOR_NEXT, ref(at, iterator))));
                continue;
            }

            if (element instanceof UnaryNode unary && unary.isTokenType(TokenType.SPREAD_ARRAY)) {
                bind(at, unary.getExpression(),
                        runtime(at, RuntimeNode.Request.ITERATOR_REST, ref(at, iterator)), statements);
                continue;
            }

            bindWithDefault(at, element,
                    runtime(at, RuntimeNode.Request.ITERATOR_NEXT, ref(at, iterator)), statements);
        }

        // ES2015 13.3.3.6: a pattern that stops before its iterator is done has
        // to say so, which is how a generator gets to run its finally blocks. A
        // rest element always drains the iterator, so there is nothing to close.
        if (!endsWithRest(pattern)) {
            statements.add(new ExpressionStatement(at.getLineNumber(), at.getToken(), at.getFinish(),
                    runtime(at, RuntimeNode.Request.ITERATOR_CLOSE, ref(at, iterator))));
        }
    }

    private static boolean endsWithRest(final ArrayLiteralNode pattern) {
        final Expression[] elements = pattern.getValue();
        return elements.length > 0 && elements[elements.length - 1] instanceof UnaryNode unary
                && unary.isTokenType(TokenType.SPREAD_ARRAY);
    }

    /** {@code {p, q: t, r = 1} = o}. */
    private void destructureObject(final Statement at, final ObjectNode pattern, final Expression value,
            final List<Statement> statements) {
        final String source = newTemporary();
        statements.add(temporaryFor(at, source,
                runtime(at, RuntimeNode.Request.REQUIRE_OBJECT_COERCIBLE, value)));

        for (final PropertyNode property : pattern.getElements()) {
            final Expression key = property.getKey();
            final Expression read = property.isComputed() || !(key instanceof LiteralNode<?> || key instanceof IdentNode)
                    ? new IndexNode(Token.recast(at.getToken(), TokenType.LBRACKET), at.getFinish(), ref(at, source), key)
                    : new AccessOrIndex(at, ref(at, source), key).build();
            bindWithDefault(at, property.getValue(), read, statements);
        }
    }

    /**
     * Binds a pattern element, honouring {@code = default} if it carries one.
     *
     * The value has to go through a temporary first: the default applies when
     * the value is undefined, and the value comes from a side-effecting
     * iterator step that must not run twice.
     */
    private void bindWithDefault(final Statement at, final Expression element, final Expression value,
            final List<Statement> statements) {
        if (element instanceof BinaryNode withDefault && withDefault.isTokenType(TokenType.ASSIGN)
                && !isPattern(withDefault.lhs())) {
            final String holder = newTemporary();
            statements.add(temporaryFor(at, holder, value));
            bind(at, withDefault.lhs(),
                    defaulted(at, holder, withDefault.rhs()), statements);
            return;
        }

        if (element instanceof BinaryNode withDefault && withDefault.isTokenType(TokenType.ASSIGN)) {
            // a nested pattern that itself carries a default: [ [a] = [1] ] = xs
            final String holder = newTemporary();
            statements.add(temporaryFor(at, holder, value));
            destructure(at, withDefault.lhs(),
                    defaulted(at, holder, withDefault.rhs()), statements);
            return;
        }

        bind(at, element, value, statements);
    }

    /** {@code holder === undefined ? fallback : holder} */
    private Expression defaulted(final Statement at, final String holder, final Expression fallback) {
        // The undefined side has to be the global "undefined" identifier rather
        // than a literal: the code generator recognises an undefined check by
        // finding that symbol, and falls back to a generic strict comparison -
        // and then a null dereference - for anything else.
        final Expression isUndefined = runtime(at, RuntimeNode.Request.IS_UNDEFINED,
                ref(at, holder), ref(at, UNDEFINED_NAME));
        return new TernaryNode(Token.recast(at.getToken(), TokenType.TERNARY), isUndefined,
                new JoinPredecessorExpression(fallback),
                new JoinPredecessorExpression(ref(at, holder)));
    }

    /** Binds one target - a name, a member expression, or a nested pattern. */
    private void bind(final Statement at, final Expression target, final Expression value,
            final List<Statement> statements) {
        if (isPattern(target)) {
            destructure(at, target, value, statements);
            return;
        }

        // Always an assignment, never a declaration, even for let and const: the
        // parser has already emitted a declaration for every name in the pattern
        // (Parser.variableDeclarationList), so declaring them again here would be
        // a redeclaration error. Marking the name as declared here says that this
        // assignment is that declaration's initialiser, which is what keeps
        // "const [a] = xs" from looking like a write to an existing constant.
        final Expression assigned = declaring && target instanceof IdentNode name
                ? name.setIsDeclaredHere()
                : target;
        statements.add(new ExpressionStatement(at.getLineNumber(), at.getToken(), at.getFinish(),
                new BinaryNode(Token.recast(at.getToken(), TokenType.ASSIGN), assigned, value)));
    }

    /**
     * A fresh internal name. Temporaries are per statement rather than pooled:
     * nested patterns hold several live at once, and the local variable type
     * calculation collapses the slots again anyway.
     */
    private String newTemporary() {
        return TEMP_PREFIX + temporaries++;
    }

    /** A fresh reference to a name. IdentNodes are immutable but not shareable across uses. */
    private static IdentNode ref(final Statement at, final String name) {
        return new IdentNode(at.getToken(), at.getFinish(), name);
    }

    /** An undefined-initialised declaration, for a temporary used by a later statement. */
    private static VarNode declareTemporary(final Statement at, final String name) {
        return new VarNode(at.getLineNumber(), Token.recast(at.getToken(), TokenType.VAR),
                at.getFinish(), ref(at, name), null);
    }

    /** Declares a temporary. Always a var: it must outlive any block the pattern creates. */
    private static VarNode temporaryFor(final Statement at, final String name, final Expression value) {
        return new VarNode(at.getLineNumber(), Token.recast(at.getToken(), TokenType.VAR),
                at.getFinish(), ref(at, name), value);
    }

    private static RuntimeNode runtime(final Statement at, final RuntimeNode.Request request,
            final Expression... args) {
        return new RuntimeNode(at.getToken(), at.getFinish(), request, args);
    }

    /**
     * Chooses between {@code o.name} and {@code o[expr]} for a pattern's key.
     * A literal key that is a valid identifier reads better - and links better -
     * as a property access.
     */
    private static final class AccessOrIndex {
        private final Statement at;
        private final Expression base;
        private final Expression key;

        AccessOrIndex(final Statement at, final Expression base, final Expression key) {
            this.at = at;
            this.base = base;
            this.key = key;
        }

        Expression build() {
            if (key instanceof IdentNode name) {
                return new org.openjdk.nashorn.internal.ir.AccessNode(at.getToken(), at.getFinish(), base, name.getName());
            }
            final Object value = ((LiteralNode<?>)key).getValue();
            if (value instanceof String string) {
                return new org.openjdk.nashorn.internal.ir.AccessNode(at.getToken(), at.getFinish(), base, string);
            }
            // the token type is what says this is o[k] rather than o.k
            return new IndexNode(Token.recast(at.getToken(), TokenType.LBRACKET), at.getFinish(), base, key);
        }
    }
}
