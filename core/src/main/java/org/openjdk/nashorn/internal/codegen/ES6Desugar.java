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
import java.util.List;
import org.openjdk.nashorn.internal.ir.BinaryNode;
import org.openjdk.nashorn.internal.ir.Block;
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

    ES6Desugar() {
        super(new LexicalContext());
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
        if (!(statement instanceof ExpressionStatement expressionStatement)) {
            return null;
        }
        final Expression expression = expressionStatement.getExpression();
        if (!(expression instanceof BinaryNode assignment)
                || !assignment.isTokenType(TokenType.ASSIGN)
                || !isPattern(assignment.lhs())) {
            return null;
        }

        temporaries = 0;
        declaring = expressionStatement.destructuringDeclarationType() != null;
        final List<Statement> bindings = new ArrayList<>();
        destructure(expressionStatement, assignment.lhs(), assignment.rhs(), bindings);
        return bindings;
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
    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        final FunctionNode withGenerator = addGeneratorPrologue(functionNode);
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
     * Gives a generator function the prologue that turns it into a generator.
     *
     * A generator function is compiled as an ordinary function that plays two
     * roles: called normally it must hand back a generator object without
     * running anything, and the body runs later by calling the same function
     * again from the generator's own thread. The prologue distinguishes them.
     */
    private FunctionNode addGeneratorPrologue(final FunctionNode functionNode) {
        if (functionNode.getKind() != FunctionNode.Kind.GENERATOR) {
            return functionNode;
        }

        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final int line = functionNode.getLineNumber();
        final String created = ":generator";

        final Block body = functionNode.getBody();
        final List<Statement> statements = new ArrayList<>();

        // var :generator = GENERATOR_ENTER();
        statements.add(new VarNode(line, Token.recast(token, TokenType.VAR), finish,
                new IdentNode(token, finish, created),
                new RuntimeNode(token, finish, RuntimeNode.Request.GENERATOR_ENTER)));

        // if (:generator !== undefined) { return :generator; }
        final Expression isNotUndefined = new RuntimeNode(token, finish, RuntimeNode.Request.IS_NOT_UNDEFINED,
                new IdentNode(token, finish, created), new IdentNode(token, finish, UNDEFINED_NAME));
        final Block returnBlock = new Block(token, finish,
                new ReturnNode(line, token, finish, new IdentNode(token, finish, created)));
        statements.add(new IfNode(line, token, finish, isNotUndefined, returnBlock, null));

        statements.addAll(body.getStatements());
        // A generator is compiled with an arguments object. It needs the argument
        // array to replay the call on the generator's thread, and going through
        // arguments rather than merely forcing variable arity is what makes the
        // parameter reads safe: a bare varargs function indexes the array without
        // a bounds check, so a generator called with fewer arguments than it
        // declares would fail with ArrayIndexOutOfBoundsException.
        return functionNode
                .setFlag(lc, FunctionNode.USES_ARGUMENTS)
                .setBody(lc, body.setStatements(lc, statements));
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
        declaring = false;
        final String caught = newTemporary();

        final Block body = catchNode.getBody();
        final List<Statement> statements = new ArrayList<>();
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
        if (init == null || !isPattern(init)) {
            return null;
        }

        temporaries = 0;
        // A for-in/of pattern binds afresh on every iteration, so its names are
        // being initialised rather than reassigned. The parser marks the plain
        // "for (const x of xs)" case itself but has no identifier to mark when
        // the binding is a pattern.
        declaring = true;
        final String element = newTemporary();

        final List<Statement> bindings = new ArrayList<>();
        destructure(forNode, init, ref(forNode, element), bindings);

        final Block body = forNode.getBody();
        final List<Statement> statements = new ArrayList<>(bindings);
        statements.addAll(body.getStatements());

        final ForNode bound = forNode
                .setInit(lc, ref(forNode, element))
                .setBody(lc, body.setStatements(lc, statements));

        return List.of(declareTemporary(forNode, element), bound);
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
                    ? new IndexNode(at.getToken(), at.getFinish(), ref(at, source), key)
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
            return new IndexNode(at.getToken(), at.getFinish(), base, key);
        }
    }
}
