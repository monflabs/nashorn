/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Philippe Riand designates this
 * particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
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
 */

package org.monflabs.nashorn.internal.codegen;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.monflabs.nashorn.internal.ir.AccessNode;
import org.monflabs.nashorn.internal.ir.BinaryNode;
import org.monflabs.nashorn.internal.ir.CallNode;
import org.monflabs.nashorn.internal.ir.Block;
import org.monflabs.nashorn.internal.ir.BreakNode;
import org.monflabs.nashorn.internal.ir.LabelNode;
import org.monflabs.nashorn.internal.ir.BlockStatement;
import org.monflabs.nashorn.internal.ir.CatchNode;
import org.monflabs.nashorn.internal.ir.ThrowNode;
import org.monflabs.nashorn.internal.ir.ClassNode;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.ir.Expression;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.ForNode;
import org.monflabs.nashorn.internal.ir.ExpressionStatement;
import org.monflabs.nashorn.internal.ir.IdentNode;
import org.monflabs.nashorn.internal.ir.IfNode;
import org.monflabs.nashorn.internal.ir.ReturnNode;
import org.monflabs.nashorn.internal.ir.IndexNode;
import org.monflabs.nashorn.internal.ir.JoinPredecessorExpression;
import org.monflabs.nashorn.internal.ir.LexicalContext;
import org.monflabs.nashorn.internal.ir.LexicalContextNode;
import org.monflabs.nashorn.internal.ir.LiteralNode;
import org.monflabs.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import org.monflabs.nashorn.internal.ir.Node;
import org.monflabs.nashorn.internal.ir.ObjectNode;
import org.monflabs.nashorn.internal.ir.PropertyNode;
import org.monflabs.nashorn.internal.ir.RuntimeNode;
import org.monflabs.nashorn.internal.ir.Statement;
import org.monflabs.nashorn.internal.ir.SwitchNode;
import org.monflabs.nashorn.internal.ir.TernaryNode;
import org.monflabs.nashorn.internal.ir.TryNode;
import org.monflabs.nashorn.internal.ir.UnaryNode;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.WhileNode;
import org.monflabs.nashorn.internal.ir.visitor.NodeVisitor;
import org.monflabs.nashorn.internal.parser.Token;
import org.monflabs.nashorn.internal.parser.TokenType;

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
    static final String ARROW_THIS = ":arrowThis";

    /** The name of the temporary a switch holds its discriminant in. */
    private static final String SWITCH_HELD = ":switch";

    /** The name of the variable an arrow reads its new.target from. */
    static final String ARROW_NEW_TARGET = ":arrowNewTarget";

    /** The name the parser gives the new.target meta-property. */
    private static final String NEW_TARGET = "new.target";

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
    /** The function an arrow inside a derived constructor calls super() through. */
    private static final String SUPER_INIT = ":superInit";

    /** Where a derived class constructor keeps what it was asked to return. */
    private static final String DERIVED_RESULT = ":derivedResult";

    /** The label a return in a derived class constructor leaves the body by. */
    private static final String DERIVED_EXIT = ":derivedExit";


    /** The global binding a default test compares against. */
    private static final String UNDEFINED_NAME = "undefined";

    /** Distinguishes the temporaries of one statement from the next. */
    private int temporaries;

    /**
     * The names B.3.3 gives a var-scoped binding, by the id of the function they
     * belong to.
     *
     * The parser marks every block-level function declaration in sloppy code
     * with an assignment to a name of its own; this pass decides which of those
     * marks may stand - the answer needs the whole enclosing function, which the
     * parser did not have - and declares the survivors at the top of the body.
     */
    private final Map<Integer, Set<String>> annexBVars = new HashMap<>();

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
     * The iterators of the array patterns written in expression position in the
     * statement being desugared, each with the temporary that records whether it
     * is being left by a throw.
     *
     * A pattern in statement position carries its own try; one in expression
     * position becomes a link in a comma chain, which is no place for one, so
     * the guard goes around the whole statement instead. Closing an iterator
     * that finished is a no-op, and so is closing one whose pattern was never
     * reached, so a guard that turns out not to have been needed costs nothing.
     */
    private final List<String[]> expressionGuards = new ArrayList<>();

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
                markTarget(objectPatternTarget(property));
            }
        }
    }

    /** Whether a property is an ES2018 object spread/rest {@code ...target}. */
    private static boolean isObjectSpread(final PropertyNode property) {
        return property.getKey() instanceof UnaryNode unary && unary.isTokenType(TokenType.SPREAD_OBJECT);
    }

    /** The target an object-pattern property binds - the value, or, for a rest, the spread operand. */
    private static Expression objectPatternTarget(final PropertyNode property) {
        return isObjectSpread(property) ? ((UnaryNode) property.getKey()).getExpression() : property.getValue();
    }

    /** Declares, as lets, every name a binding pattern binds. */
    private void declareBoundNames(final Statement at, final Expression pattern, final List<Statement> statements) {
        if (pattern instanceof ArrayLiteralNode array) {
            for (final Expression element : array.getValue()) {
                declareBoundName(at, element, statements);
            }
        } else if (pattern instanceof ObjectNode object) {
            for (final PropertyNode property : object.getElements()) {
                declareBoundName(at, objectPatternTarget(property), statements);
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
        // ES2022 ergonomic brand check: #x in obj (the left operand is a
        // private-name reference produced by the parser)
        if (binaryNode.isTokenType(TokenType.IN)
                && binaryNode.lhs() instanceof IdentNode id && id.isPrivateName()) {
            return new RuntimeNode(binaryNode.getToken(), binaryNode.getFinish(),
                    RuntimeNode.Request.PRIVATE_IN, id, binaryNode.rhs());
        }
        // ES2022 compound or logical assignment to a private member is rewritten
        // to a plain store with the base read once; a plain "=" is left for the
        // code generator, which stores to the private target directly.
        if (binaryNode.isAssignment() && !binaryNode.isTokenType(TokenType.ASSIGN)
                && isPrivateIndex(binaryNode.lhs())) {
            return privateAssignment(binaryNode);
        }
        switch (binaryNode.tokenType()) {
        case ASSIGN_AND:
        case ASSIGN_OR:
        case ASSIGN_NULLISH:
            return logicalAssignment(binaryNode);
        default:
            break;
        }
        if (!binaryNode.isTokenType(TokenType.ASSIGN)
                || !isPattern(binaryNode.lhs())
                || handledElsewhere.contains(binaryNode.getToken())) {
            return super.leaveBinaryNode(binaryNode);
        }
        return destructuringExpression(binaryNode);
    }

    private static boolean isPrivateIndex(final Expression e) {
        return e instanceof IndexNode ix && ix.isPrivate();
    }

    /** A simple private member reference {@code base.#x} for the code generator. */
    private static IndexNode privateIndex(final long token, final int finish, final Expression base, final Expression index) {
        return new IndexNode(token, finish, base, index, false, true);
    }

    /** A fresh read of a private-name binding, so it is not shared across tree positions. */
    private static Expression copyIndex(final Expression index) {
        final IdentNode id = (IdentNode) index;
        return new IdentNode(id.getToken(), id.getFinish(), id.getName());
    }

    /** The non-assigning operator underlying a compound-assignment token. */
    private static TokenType baseOpOf(final TokenType tt) {
        switch (tt) {
        case ASSIGN_ADD:     return TokenType.ADD;
        case ASSIGN_SUB:     return TokenType.SUB;
        case ASSIGN_MUL:     return TokenType.MUL;
        case ASSIGN_DIV:     return TokenType.DIV;
        case ASSIGN_MOD:     return TokenType.MOD;
        case ASSIGN_EXP:     return TokenType.EXP;
        case ASSIGN_BIT_AND: return TokenType.BIT_AND;
        case ASSIGN_BIT_OR:  return TokenType.BIT_OR;
        case ASSIGN_BIT_XOR: return TokenType.BIT_XOR;
        case ASSIGN_SHL:     return TokenType.SHL;
        case ASSIGN_SAR:     return TokenType.SAR;
        case ASSIGN_SHR:     return TokenType.SHR;
        default: throw new AssertionError("not a compound assignment: " + tt);
        }
    }

    private static Expression assign(final long token, final Expression target, final Expression value) {
        return new BinaryNode(Token.recast(token, TokenType.ASSIGN), target, value);
    }

    /**
     * Rewrites a compound or logical assignment whose target is a private member
     * to a plain assignment, reading the base once into a temporary so it is
     * evaluated once for both the read and the write. A plain {@code =} is left
     * alone - the code generator stores to the private target directly.
     */
    private Node privateAssignment(final BinaryNode node) {
        final IndexNode target = (IndexNode) node.lhs();
        final Expression base  = target.getBase();
        final Expression index = target.getIndex();
        final Expression rhs   = node.rhs();
        final long token = node.getToken();
        final int finish = node.getFinish();
        final TokenType tt = node.tokenType();

        final Statement at = new ExpressionStatement(lc.getCurrentFunction().getLineNumber(), token, finish, node);
        final String baseTemp = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
        pendingDeclarations.add(declareTemporary(at, baseTemp));
        final Expression baseInit = assignTemporary(at, baseTemp, base);
        final Expression read  = privateIndex(token, finish, ref(at, baseTemp), copyIndex(index));
        final IndexNode  write = privateIndex(token, finish, ref(at, baseTemp), copyIndex(index));

        switch (tt) {
        case ASSIGN_AND:
        case ASSIGN_OR:
        case ASSIGN_NULLISH: {
            final TokenType sc = tt == TokenType.ASSIGN_AND ? TokenType.AND
                    : tt == TokenType.ASSIGN_OR ? TokenType.OR : TokenType.NULLISH;
            return sequence(at, baseInit, shortCircuitOf(token, sc, read, assign(token, write, rhs)));
        }
        default: {
            final Expression combined = new BinaryNode(Token.recast(token, baseOpOf(tt)), read, rhs);
            return sequence(at, baseInit, assign(token, write, combined));
        }
        }
    }

    /**
     * Rewrites {@code ++obj.#x} / {@code obj.#x--} and friends on a private
     * member into a plain store, the base read once into a temporary. A prefix
     * form is worth the new value; a postfix form is worth the (coerced) old
     * value, kept in a second temporary.
     */
    private Node privateIncDec(final UnaryNode node) {
        final IndexNode target = (IndexNode) node.getExpression();
        final Expression base  = target.getBase();
        final Expression index = target.getIndex();
        final long token = node.getToken();
        final int finish = node.getFinish();
        final boolean postfix = node.isTokenType(TokenType.INCPOSTFIX) || node.isTokenType(TokenType.DECPOSTFIX);
        final TokenType op = node.isTokenType(TokenType.INCPREFIX) || node.isTokenType(TokenType.INCPOSTFIX)
                ? TokenType.ADD : TokenType.SUB;

        final Statement at = new ExpressionStatement(lc.getCurrentFunction().getLineNumber(), token, finish, node);
        final String baseTemp = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
        pendingDeclarations.add(declareTemporary(at, baseTemp));
        final Expression baseInit = assignTemporary(at, baseTemp, base);
        final Expression number = new UnaryNode(Token.recast(token, TokenType.POS),
                privateIndex(token, finish, ref(at, baseTemp), copyIndex(index)));

        if (!postfix) {
            final Expression adjusted = new BinaryNode(Token.recast(token, op), number,
                    LiteralNode.newInstance(token, finish, 1));
            return sequence(at, baseInit,
                    assign(token, privateIndex(token, finish, ref(at, baseTemp), copyIndex(index)), adjusted));
        }
        final String oldTemp = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
        pendingDeclarations.add(declareTemporary(at, oldTemp));
        final Expression oldInit = assignTemporary(at, oldTemp, number);
        final Expression adjusted = new BinaryNode(Token.recast(token, op), ref(at, oldTemp),
                LiteralNode.newInstance(token, finish, 1));
        final Expression set = assign(token, privateIndex(token, finish, ref(at, baseTemp), copyIndex(index)), adjusted);
        return sequence(at, baseInit, sequence(at, oldInit, sequence(at, set, ref(at, oldTemp))));
    }

    /**
     * ES2021 logical assignment: {@code a &&= b} / {@code a ||= b} / {@code a ??= b}
     * become the short-circuit expression {@code a <op> (a = b)}, so the assignment
     * (and any setter it would call) happens only when the operator does not
     * short-circuit. A member target's base - and an index target's key - is read
     * into a temporary first, so {@code f().p ||= b} evaluates {@code f()} once.
     */
    private Expression logicalAssignment(final BinaryNode node) {
        final long token = node.getToken();
        final TokenType shortCircuit = switch (node.tokenType()) {
            case ASSIGN_AND -> TokenType.AND;
            case ASSIGN_OR  -> TokenType.OR;
            default         -> TokenType.NULLISH;
        };
        final Expression target = node.lhs();
        final Expression rhs = node.rhs();
        final Statement at = new ExpressionStatement(lc.getCurrentFunction().getLineNumber(),
                token, node.getFinish(), node);

        if (target instanceof IdentNode ident) {
            // a <op>= b  ->  a <op> (a = b)
            final Expression read = new IdentNode(ident);
            final Expression assign = new BinaryNode(Token.recast(token, TokenType.ASSIGN), ident, rhs);
            return shortCircuitOf(token, shortCircuit, read, assign);
        }
        if (target instanceof AccessNode access) {
            // base is read once into a temp: (:t = base, :t.p <op> (:t.p = b))
            final String baseTemp = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
            pendingDeclarations.add(declareTemporary(at, baseTemp));
            final Expression baseInit = assignTemporary(at, baseTemp, access.getBase());
            final AccessNode read  = new AccessNode(token, access.getFinish(), ref(at, baseTemp), access.getProperty());
            final AccessNode write = new AccessNode(token, access.getFinish(), ref(at, baseTemp), access.getProperty());
            final Expression assign = new BinaryNode(Token.recast(token, TokenType.ASSIGN), write, rhs);
            return sequence(at, baseInit, shortCircuitOf(token, shortCircuit, read, assign));
        }
        if (target instanceof IndexNode index) {
            // base and key are each read once: (:t = base, :k = key, :t[:k] <op> (:t[:k] = b))
            final String baseTemp = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
            final String keyTemp  = EXPRESSION_TEMP_PREFIX + expressionTemporaries++;
            pendingDeclarations.add(declareTemporary(at, baseTemp));
            pendingDeclarations.add(declareTemporary(at, keyTemp));
            final Expression baseInit = assignTemporary(at, baseTemp, index.getBase());
            final Expression keyInit  = assignTemporary(at, keyTemp, index.getIndex());
            final IndexNode read  = new IndexNode(token, index.getFinish(), ref(at, baseTemp), ref(at, keyTemp));
            final IndexNode write = new IndexNode(token, index.getFinish(), ref(at, baseTemp), ref(at, keyTemp));
            final Expression assign = new BinaryNode(Token.recast(token, TokenType.ASSIGN), write, rhs);
            return sequence(at, baseInit,
                    sequence(at, keyInit, shortCircuitOf(token, shortCircuit, read, assign)));
        }
        // no other target survives verifyAssignment
        return super.leaveBinaryNode(node) instanceof Expression e ? e : node;
    }

    /** {@code lhs <op> rhs} for a short-circuit operator, both sides join-predecessors. */
    private static Expression shortCircuitOf(final long token, final TokenType op,
            final Expression lhs, final Expression rhs) {
        return new BinaryNode(Token.recast(token, op),
                new JoinPredecessorExpression(lhs), new JoinPredecessorExpression(rhs));
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
            final Expression step = asExpression(statement);
            if (step != null) {
                chain = comma(token, chain, step);
            }
        }
        return comma(token, chain, ref(at, value));
    }

    /**
     * Wraps a statement in the close its expression-position patterns need.
     *
     * @param statement the statement, already desugared
     * @return it, guarded, or itself when there was nothing to guard
     */
    private Statement guardExpressionIterators(final Statement statement) {
        if (expressionGuards.isEmpty()) {
            return statement;
        }
        final List<String[]> guards = new ArrayList<>(expressionGuards);
        expressionGuards.clear();

        final int line = statement.getLineNumber();
        final long token = statement.getToken();
        final int finish = statement.getFinish();
        final String caught = newTemporary();

        final List<Statement> record = new ArrayList<>(guards.size() + 1);
        final List<Statement> close = new ArrayList<>(guards.size());
        for (final String[] guard : guards) {
            record.add(new ExpressionStatement(line, token, finish,
                    new BinaryNode(Token.recast(token, TokenType.ASSIGN), ref(statement, guard[1]),
                            LiteralNode.newInstance(token, finish, true))));
            close.add(new ExpressionStatement(line, token, finish,
                    runtime(statement, RuntimeNode.Request.ITERATOR_CLOSE_MAYBE,
                            ref(statement, guard[0]), ref(statement, guard[1]))));
        }
        record.add(new ThrowNode(line, token, finish, ref(statement, caught), false));

        return new TryNode(line, token, finish,
                new Block(token, finish, statement),
                List.of(new Block(token, finish, new CatchNode(line, token, finish,
                        ref(statement, caught), null, new Block(token, finish, record), false))),
                new Block(token, finish, close));
    }

    @Override
    public Node leaveExpressionStatement(final ExpressionStatement expressionStatement) {
        return guardExpressionIterators((Statement)super.leaveExpressionStatement(expressionStatement));
    }

    /**
     * One step of the sequence, as an expression rather than a statement, or
     * null for a step that is only a declaration and evaluates nothing.
     */
    private Expression asExpression(final Statement statement) {
        if (statement instanceof ExpressionStatement expressionStatement) {
            return expressionStatement.getExpression();
        }
        final VarNode declaration = (VarNode)statement;
        // the name is declared at the top of the block; here it is only assigned
        pendingDeclarations.add(declaration.setInit(null));
        if (declaration.getInit() == null) {
            // a temporary a later step assigns: there is nothing to evaluate
            return null;
        }
        return new BinaryNode(Token.recast(declaration.getToken(), TokenType.ASSIGN),
                declaration.getName(), declaration.getInit());
    }

    private static Expression comma(final long token, final Expression left, final Expression right) {
        return new BinaryNode(Token.recast(token, TokenType.COMMARIGHT), left, right);
    }

    /**
     * Takes a switch's discriminant out of the block its clauses share.
     *
     * 13.12.11 evaluates the discriminant before that block is entered, so what
     * the clauses declare is not in scope while it runs. The parser draws the
     * block around the whole statement, discriminant included, and holding the
     * value in a temporary in front of the block is what puts it back outside.
     */
    private List<Statement> hoistSwitchDiscriminant(final BlockStatement blockStatement) {
        final Block block = blockStatement.getBlock();
        if (!block.isSwitchBlock() || block.getStatements().size() != 1
                || !(block.getStatements().get(0) instanceof SwitchNode switchNode)) {
            return null;
        }
        final Expression discriminant = switchNode.getExpression();
        if (discriminant instanceof LiteralNode) {
            // there is nothing in it that could tell which scope it ran in, and
            // the temporary would only stand between the switch and its tag
            return null;
        }

        final long token = Token.recast(switchNode.getToken(), TokenType.VAR);
        final int finish = switchNode.getFinish();
        // the position makes the name, so that two switches in one block do not
        // share a temporary and a recompilation names it what the first pass did
        final IdentNode held = new IdentNode(token, finish,
                SWITCH_HELD + Token.descPosition(switchNode.getToken()));
        return List.of(
                new VarNode(switchNode.getLineNumber(), token, finish, held, discriminant,
                        VarNode.IS_LET | VarNode.IS_TEMPORARY),
                blockStatement.setBlock(block.setStatements(lc,
                        List.of(switchNode.setExpression(lc, held)))));
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
            final String annexBName = annexBMarkedName(statement);
            if (annexBName != null) {
                if (annexBApplies(block, annexBName)) {
                    annexBVars.computeIfAbsent(lc.getCurrentFunction().getId(), id -> new LinkedHashSet<>())
                            .add(annexBName);
                    if (expanded != null) {
                        expanded.add(statement);
                    }
                } else {
                    // the name is spoken for: the declaration stays block scoped
                    // and nothing of it is seen outside
                    if (expanded == null) {
                        expanded = new ArrayList<>(statements.subList(0, i));
                    }
                }
                continue;
            }
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
     * The name a statement marks for B.3.3, or null if it is not one of the
     * parser's marks.
     */
    private static String annexBMarkedName(final Statement statement) {
        if (statement instanceof ExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof BinaryNode assignment
                && assignment.isTokenType(TokenType.ASSIGN)
                && assignment.lhs() instanceof IdentNode target
                && target.isAnnexBVarTarget()) {
            return target.getName();
        }
        return null;
    }

    /**
     * Whether B.3.3 gives this block-level function declaration a var-scoped
     * binding as well.
     *
     * The rule is that the extension applies where declaring a var of that name
     * would not have been an error, so the answer is no when the name is a
     * parameter, when it is the arguments object, or when anything between the
     * declaration and the function body binds it lexically - a let, a const, a
     * class, or another block's function declaration. A catch parameter is not
     * one of those: B.3.5 lets a var shadow it, and the tests require the
     * extension to reach through a catch block.
     *
     * The declaration's own block is not examined. A lexical binding of the name
     * beside it is already an early error, and the block-scoped declaration
     * itself is in there.
     *
     * @param block the block the declaration stands in
     * @param name  the name it declares
     * @return true if the name is also bound in the variable environment
     */
    private boolean annexBApplies(final Block block, final String name) {
        final FunctionNode function = lc.getCurrentFunction();

        if (!function.isProgram()) {
            for (final IdentNode parameter : function.getParameters()) {
                if (name.equals(parameter.getName())) {
                    return false;
                }
            }
            if ("arguments".equals(name)) {
                return false;
            }
        }

        final Block body = lc.getFunctionBody(function);
        for (final Iterator<Block> blocks = lc.getBlocks(); blocks.hasNext();) {
            final Block enclosing = blocks.next();
            if (enclosing != block && bindsLexically(enclosing, name)) {
                return false;
            }
            if (enclosing == body) {
                break;
            }
        }

        return true;
    }

    /**
     * Whether a block declares this name with a let, a const, a class, a
     * function, or a catch parameter that is a pattern.
     *
     * B.3.5 lets a var take the name of a *simple* catch parameter, and B.3.3
     * has to reach through such a clause - the tests say so. A destructuring
     * parameter is not covered by it, and the names its pattern binds stay
     * lexical.
     */
    private static boolean bindsLexically(final Block block, final String name) {
        for (final Statement statement : block.getStatements()) {
            if (statement instanceof VarNode varNode
                    && varNode.isBlockScoped()
                    && name.equals(varNode.getName().getName())) {
                return true;
            }
            if (statement instanceof CatchNode catchNode) {
                final Expression parameter = catchNode.getException();
                if (parameter != null && !(parameter instanceof IdentNode)) {
                    final Set<String> bound = new HashSet<>();
                    collectNames(parameter, bound);
                    if (bound.contains(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Declares the names B.3.3 gave this function, at the top of its body.
     *
     * The declaration has no initialiser, so it does not overwrite a binding of
     * that name the function already has - a parameter cannot be one, and a var
     * or a function declaration of the same name is the same binding.
     */
    private FunctionNode declareAnnexBVars(final FunctionNode functionNode) {
        final Set<String> names = annexBVars.remove(functionNode.getId());
        if (names == null || names.isEmpty()) {
            return functionNode;
        }

        // a var belongs to the body's environment, which is nested inside the
        // parameter list's when the parameters have expressions in them
        final Block body = functionNode.getBody();
        final long token = Token.recast(functionNode.getToken(), TokenType.VAR);
        final int finish = functionNode.getFinish();

        final List<Statement> statements = new ArrayList<>();
        for (final String name : names) {
            statements.add(new VarNode(functionNode.getLineNumber(), token, finish,
                    new IdentNode(token, finish, name), null));
        }
        statements.addAll(body.getStatements());

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * The bindings one statement expands into, or null if it is not a
     * destructuring statement and should be left alone.
     */
    private List<Statement> expand(final Statement statement) {
        if (statement instanceof BlockStatement blockStatement) {
            return hoistSwitchDiscriminant(blockStatement);
        }
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
        if (unaryNode.isTokenType(TokenType.AWAIT)) {
            return new RuntimeNode(unaryNode.getToken(), unaryNode.getFinish(),
                    RuntimeNode.Request.AWAIT, unaryNode.getExpression());
        }
        if (unaryNode.isTokenType(TokenType.YIELD) || unaryNode.isTokenType(TokenType.YIELD_STAR)) {
            final RuntimeNode.Request request = unaryNode.isTokenType(TokenType.YIELD_STAR)
                    ? RuntimeNode.Request.YIELD_STAR
                    : RuntimeNode.Request.YIELD;
            return new RuntimeNode(unaryNode.getToken(), unaryNode.getFinish(), request,
                    unaryNode.getExpression());
        }
        // ES2022 ++/-- on a private member: the operand was rewritten to PRIVATE_GET
        if (isPrivateIndex(unaryNode.getExpression())
                && (unaryNode.isTokenType(TokenType.INCPREFIX) || unaryNode.isTokenType(TokenType.DECPREFIX)
                 || unaryNode.isTokenType(TokenType.INCPOSTFIX) || unaryNode.isTokenType(TokenType.DECPOSTFIX))) {
            return privateIncDec(unaryNode);
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
        final FunctionNode current = lc.getCurrentFunction();
        if (current.isAsyncGenerator() && returnNode.getExpression() != null) {
            // 13.10.1 step 3: "return Expression" in an async context awaits the
            // value. An async function gets this from promise adoption when its
            // result settles; an async generator wraps the value in
            // {value, done:true} instead, so the await must be spelled out here -
            // it also costs the extra microtask turn the specification requires.
            return super.leaveReturnNode(returnNode.setExpression(
                    new RuntimeNode(returnNode.getToken(), returnNode.getFinish(),
                            RuntimeNode.Request.AWAIT, returnNode.getExpression())));
        }
        if (!current.isSubclassConstructor()) {
            return super.leaveReturnNode(returnNode);
        }
        // What the constructor answers with is worked out at the end of the body
        // rather than here, because a finally block between here and there runs
        // in between and can make the this binding, or throw. So the value is
        // put by and the body left by a break, which runs those blocks on its
        // way out exactly as a return would have.
        final int line = returnNode.getLineNumber();
        final long token = returnNode.getToken();
        final int finish = returnNode.getFinish();
        final Expression value = returnNode.getExpression();
        return new BlockStatement(line, new Block(token, finish,
                new ExpressionStatement(line, token, finish,
                        new BinaryNode(Token.recast(token, TokenType.ASSIGN),
                                new IdentNode(token, finish, DERIVED_RESULT),
                                value == null ? new IdentNode(token, finish, UNDEFINED_NAME) : value)),
                new BreakNode(line, token, finish, DERIVED_EXIT)));
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
        if (NEW_TARGET.equals(identNode.getName())) {
            // an arrow has no new.target of its own: it reads the one the
            // function that made it published, the way it reads that one's this
            return lc.getCurrentFunction().isArrow()
                    ? new IdentNode(identNode.getToken(), identNode.getFinish(), ARROW_NEW_TARGET)
                    : super.leaveIdentNode(identNode);
        }
        if (!CompilerConstants.THIS.symbolName().equals(identNode.getName())) {
            return super.leaveIdentNode(identNode);
        }

        final FunctionNode function = lc.getCurrentFunction();
        if (function.isArrow()) {
            // The check costs a comparison and is made whatever the arrow was
            // written in, because an arrow compiled on its own cannot tell: only
            // a derived constructor's binding is ever the uninitialised one, so
            // nothing else can fail it.
            return new RuntimeNode(identNode.getToken(), identNode.getFinish(),
                    RuntimeNode.Request.REQUIRE_THIS_INITIALIZED,
                    new IdentNode(identNode.getToken(), identNode.getFinish(), ARROW_THIS));
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
                declareAnnexBVars(bindArrowThis(bindThis(publishNewTarget(publishThis(addAsyncPrologue(addGeneratorPrologue(
                        addClassConstructorGuard(moduleEnvironment(rejectEarlyParameterReads(functionNode))))))))));
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
        // Where the class was written. ES2019 19.2.3.5 makes a class's
        // Function.prototype.toString the whole class, not its constructor, and
        // the constructor's own range is the method it was written as - which is
        // also what a lazy reparse reads, so it cannot be widened in place.
        final int position = Token.descPosition(token);
        final Expression defineClass = new RuntimeNode(token, finish, RuntimeNode.Request.DEFINE_CLASS,
                classNode.getConstructor().getValue(),
                heritage == null ? LiteralNode.newInstance(token, finish) : heritage,
                LiteralNode.newInstance(token, finish, heritage != null),
                LiteralNode.newInstance(token, finish, elements),
                LiteralNode.newInstance(token, finish, position),
                LiteralNode.newInstance(token, finish, finish - position));

        // A named class runs its static fields and blocks from the desugaring
        // that surrounds it, after its own name binding is assigned (so a static
        // element that names the class sees it). An anonymous class has no such
        // binding and no way to name itself, so its static elements run right
        // here, wrapped around the class it just built.
        if (classNode.getIdent() == null) {
            return new RuntimeNode(token, finish, RuntimeNode.Request.RUN_STATIC_ELEMENTS, defineClass);
        }
        return defineClass;
    }

    /**
     * Appends one element's key, flags and value to the flattened element list.
     *
     * A get/set pair written as two class elements arrives as a single
     * PropertyNode holding both, and has to go back out as two entries or the
     * setter is dropped.
     */
    private static void addClassElement(final List<Expression> elements, final PropertyNode element) {
        int shared = element.isStatic() ? ScriptRuntime.CLASS_ELEMENT_STATIC : 0;
        if (element.isPrivate()) {
            shared |= ScriptRuntime.CLASS_ELEMENT_PRIVATE;
        }

        if (element.isStaticBlock()) {
            // a static block: its function is the value, run once with this = the class
            addEntry(elements, element, shared | ScriptRuntime.CLASS_ELEMENT_STATIC_BLOCK, element.getValue());
            return;
        }

        if (element.isField()) {
            // a field: its value is the initializer function (or null for no initializer)
            addEntry(elements, element, shared | ScriptRuntime.CLASS_ELEMENT_FIELD, element.getValue());
            return;
        }

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
        // a field with no initializer has no value function; a null literal stands
        // in so the flattened array holds a node at every slot
        elements.add(value == null ? LiteralNode.newInstance(element.getToken(), element.getFinish()) : value);
    }

    /**
     * A class element's key as a value expression. A written name is an
     * IdentNode standing for a string; a computed key is already an expression;
     * a static block has no key, so a null literal stands in.
     */
    private static Expression keyOf(final PropertyNode element) {
        if (element.isPrivate()) {
            // a private element's runtime key is its PrivateName, produced by
            // reading the :private:x binding the class body declared
            return element.getPrivateNameBinding();
        }
        final Expression key = element.getKey();
        if (key == null) {
            return LiteralNode.newInstance(element.getToken(), element.getFinish());
        }
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
        if (!functionNode.isSubclassConstructor()) {
            // ES2022 15.7.15: a base class's instance fields initialise at the
            // start of construction. A derived class's do so after super()
            // returns instead, which the code generator handles at the super call.
            statements.add(new ExpressionStatement(functionNode.getLineNumber(), token, finish,
                    new RuntimeNode(token, finish, RuntimeNode.Request.INITIALIZE_INSTANCE_ELEMENTS)));
        }
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
        if (functionNode.arrowCallsSuper()) {
            // An arrow has neither this constructor's callee nor its new.target,
            // and cannot be handed them: it may be compiled on its own, with
            // nothing of the constructor in sight but what its scope holds. What
            // goes there is a function that already knows both.
            statements.add(new VarNode(line, token, finish, new IdentNode(token, finish, SUPER_INIT),
                    new RuntimeNode(token, finish, RuntimeNode.Request.SUPER_INITIALIZER)));
        }
        statements.add(new VarNode(line, token, finish, new IdentNode(token, finish, THIS_BINDING),
                new RuntimeNode(token, finish, RuntimeNode.Request.UNINITIALIZED_THIS)));
        statements.add(new VarNode(line, token, finish, new IdentNode(token, finish, DERIVED_RESULT),
                new IdentNode(token, finish, UNDEFINED_NAME)));
        // Both bindings are also read where no statement can reach - the epilogue
        // below is unreachable in a constructor that always throws - and a local
        // that is never read loses its slot, which the code generator then has
        // nothing to load. Reading them here costs nothing: discarding a local
        // variable emits no code at all.
        statements.add(new ExpressionStatement(line, token, finish,
                new IdentNode(token, finish, THIS_BINDING)));
        statements.add(new ExpressionStatement(line, token, finish,
                new IdentNode(token, finish, DERIVED_RESULT)));
        // The body is left by a break rather than by a return, so that what it
        // answered with is examined after everything it leaves through has run:
        // 9.2.2 step 13 is part of constructing, not of returning, and a finally
        // block may still call super() - or throw, which is then the error.
        statements.add(new LabelNode(line, token, finish, DERIVED_EXIT,
                new Block(token, finish, body.getStatements())));
        statements.add(new ReturnNode(line, token, finish,
                new RuntimeNode(token, finish, RuntimeNode.Request.DERIVED_RETURN,
                        new IdentNode(token, finish, DERIVED_RESULT),
                        new IdentNode(token, finish, THIS_BINDING))));

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * super() written inside an arrow function.
     *
     * The arrow has none of what the constructor's own super() is compiled from
     * - not its callee, not its new.target, not the slot the binding lives in -
     * so all three are reached by name through the scope instead, which is what
     * an arrow compiled on its own can still do. The result is bound in the
     * constructor's binding, mirrored into the one arrows read, and made this
     * for the rest of the arrow.
     */
    @Override
    public Node leaveCallNode(final CallNode callNode) {
        final FunctionNode current = lc.getCurrentFunction();
        if (current == null || !current.isArrow()
                || !(callNode.getFunction() instanceof IdentNode called) || !called.isDirectSuper()) {
            return super.leaveCallNode(callNode);
        }

        final long token = Token.recast(callNode.getToken(), TokenType.ASSIGN);
        final int finish = callNode.getFinish();
        final Expression bound = new RuntimeNode(token, finish, RuntimeNode.Request.BIND_THIS,
                new IdentNode(token, finish, THIS_BINDING),
                callNode.setFunction(new IdentNode(called.getToken(), called.getFinish(), SUPER_INIT)));

        return new BinaryNode(token, new IdentNode(token, finish, CompilerConstants.THIS.symbolName()),
                new BinaryNode(token, new IdentNode(token, finish, ARROW_THIS),
                        new BinaryNode(token, new IdentNode(token, finish, THIS_BINDING), bound)));
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
        final int line = functionNode.getLineNumber();
        final Block body = functionNode.getBody();
        final List<Statement> original = body.getStatements();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new ExpressionStatement(line, token, finish,
                new RuntimeNode(token, finish, RuntimeNode.Request.MODULE_SCOPE)));

        // A module runs in two passes (see ModuleRecord.instantiate): the first
        // makes its scope and hoists its declarations for a cyclic dependency to
        // read, the second runs the body. The function declarations the parser
        // hoisted to the front of the body must run in both, so they stay ahead
        // of the guard; the guard returns before anything else on the first pass.
        int i = 0;
        while (i < original.size()
                && original.get(i) instanceof VarNode v && v.isFunctionDeclaration()) {
            statements.add(original.get(i));
            i++;
        }

        // if (MODULE_INSTANTIATING()) { return; }
        statements.add(new IfNode(line, token, finish,
                new RuntimeNode(token, finish, RuntimeNode.Request.MODULE_INSTANTIATING),
                new Block(token, finish, new ReturnNode(line, token, finish, null)),
                null));

        for (; i < original.size(); i++) {
            statements.add(original.get(i));
        }

        return functionNode.setFlag(lc, FunctionNode.HAS_ALL_VARS_IN_SCOPE)
                .setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * Publishes this function's new.target where an arrow written inside it can
     * read it, for the same reason its this is published: 8.1.1.3 resolves the
     * meta-property where the arrow stands, and the arrow's own frame is not
     * the one that holds the answer.
     */
    private FunctionNode publishNewTarget(final FunctionNode functionNode) {
        if (!functionNode.arrowCaptures() || !functionNode.usesNewTarget()) {
            return functionNode;
        }

        final long token = Token.recast(functionNode.getToken(), TokenType.VAR);
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new VarNode(functionNode.getLineNumber(), token, finish,
                new IdentNode(token, finish, ARROW_NEW_TARGET),
                new IdentNode(token, finish, NEW_TARGET)));
        statements.addAll(body.getStatements());

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * Puts the enclosing function's {@code this} into an arrow's own this slot,
     * for an arrow that uses super.
     *
     * A {@code this} written in an arrow is rewritten to read the captured
     * variable, but {@code super.m()} is a call the code generator builds
     * itself, and what it pushes as the receiver is the slot. Filling the slot
     * on the way in is what makes the two agree.
     */
    private FunctionNode bindArrowThis(final FunctionNode functionNode) {
        if (!functionNode.isArrow() || !functionNode.usesSuper()) {
            return functionNode;
        }

        final long token = Token.recast(functionNode.getToken(), TokenType.ASSIGN);
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new ExpressionStatement(functionNode.getLineNumber(), token, finish,
                new BinaryNode(token,
                        new IdentNode(token, finish, CompilerConstants.THIS.symbolName()),
                        new IdentNode(token, finish, ARROW_THIS))));
        statements.addAll(body.getStatements());

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * Copies {@code this} into a variable an arrow function can capture, for a
     * function that contains one reading it.
     */
    private FunctionNode publishThis(final FunctionNode functionNode) {
        if (!functionNode.arrowCaptures()) {
            return functionNode;
        }

        final long token = Token.recast(functionNode.getToken(), TokenType.VAR);
        final int finish = functionNode.getFinish();
        final Block body = functionNode.getBody();

        final List<Statement> statements = new ArrayList<>();
        statements.add(new VarNode(functionNode.getLineNumber(), token, finish,
                new IdentNode(token, finish, ARROW_THIS),
                // a derived constructor has no this of its own until super()
                // makes one, and what it publishes is that binding, kept in step
                // with it by the store beside the super call
                new IdentNode(token, finish, functionNode.isSubclassConstructor()
                        ? THIS_BINDING : CompilerConstants.THIS.symbolName())));
        statements.addAll(body.getStatements());

        return functionNode.setBody(lc, body.setStatements(lc, statements));
    }

    /**
     * ES2015 9.2.12: a parameter is not there until its turn comes.
     *
     * In a function whose parameter list has expressions in it, every binding
     * the list makes is created before any initialiser runs and initialised in
     * order, so an initialiser that reads its own parameter, or a later one,
     * reads a binding that has not been initialised - which is a ReferenceError,
     * where Nashorn read the slot and found undefined.
     *
     * Which reads those are is decided here rather than at run time: at the
     * point an initialiser is written, the parameters that are still to come
     * are exactly the ones the source lists after it. A function written inside
     * an initialiser is left alone - it may well be called after the whole list
     * has run, and then the binding is there.
     */
    private FunctionNode rejectEarlyParameterReads(final FunctionNode functionNode) {
        final Block outer = functionNode.getBody();
        if (!outer.isParameterBlock()) {
            return functionNode;
        }

        // The parser appends one statement per parameter that has something to
        // do - a default, or a pattern to take apart - in parameter order, and
        // the body follows them as the last statement.
        final List<Statement> statements = outer.getStatements();
        final List<IdentNode> parameters = functionNode.getParameters();

        final Set<String> pending = new HashSet<>();
        for (final IdentNode parameter : parameters) {
            if (hasParameterStatement(parameter)) {
                continue;
            }
            pending.add(parameter.getName());
        }
        for (final Statement statement : statements) {
            if (!(statement instanceof BlockStatement)) {
                boundNames(statement, pending);
            }
        }

        final List<Statement> rewritten = new ArrayList<>(statements);
        boolean changed = false;
        int cursor = 0;
        for (final IdentNode parameter : parameters) {
            if (!hasParameterStatement(parameter)) {
                // initialised from the argument at its own position, with
                // nothing written that could read anything
                pending.remove(parameter.getName());
                continue;
            }
            while (cursor < statements.size() && statements.get(cursor) instanceof BlockStatement) {
                cursor++;
            }
            if (cursor >= statements.size()) {
                break;
            }
            final Statement statement = statements.get(cursor);
            final Statement checked = rejectEarlyReads(statement, pending);
            if (checked != statement) {
                rewritten.set(cursor, checked);
                changed = true;
            }
            boundNames(statement, new HashSet<>()).forEach(pending::remove);
            cursor++;
        }
        return changed ? functionNode.setBody(lc, outer.setStatements(lc, rewritten)) : functionNode;
    }

    /** Whether the parser wrote a statement for this parameter. */
    private static boolean hasParameterStatement(final IdentNode parameter) {
        return parameter.isDefaultParameter() || parameter.isDestructuredParameter();
    }


    /** The names one parameter statement binds, added to {@code into} and returned. */
    private static Set<String> boundNames(final Statement statement, final Set<String> into) {
        if (statement instanceof ExpressionStatement expression
                && expression.getExpression() instanceof BinaryNode assignment
                && assignment.isTokenType(TokenType.ASSIGN)) {
            collectNames(assignment.lhs(), into);
        } else if (statement instanceof VarNode declaration) {
            into.add(declaration.getName().getName());
        }
        return into;
    }

    private static void collectNames(final Expression target, final Set<String> into) {
        if (target instanceof IdentNode name) {
            into.add(name.getName());
        } else if (target instanceof ArrayLiteralNode array) {
            for (final Expression element : array.getValue()) {
                if (element != null) {
                    collectNames(element, into);
                }
            }
        } else if (target instanceof ObjectNode object) {
            for (final PropertyNode property : object.getElements()) {
                collectNames(property.getValue(), into);
            }
        } else if (target instanceof UnaryNode rest && rest.isTokenType(TokenType.SPREAD_ARRAY)) {
            collectNames(rest.getExpression(), into);
        } else if (target instanceof BinaryNode withDefault && withDefault.isTokenType(TokenType.ASSIGN)) {
            collectNames(withDefault.lhs(), into);
        }
    }

    /**
     * Rewrites the reads of not-yet-initialised parameters in one statement.
     *
     * Only the initialiser is rewritten. The parser desugars a default into
     * "p = (p === undefined) ? initialiser : p", and the two reads of p it puts
     * around the initialiser are the compiler's own - they are how the incoming
     * argument is reached, not the binding.
     */
    private Statement rejectEarlyReads(final Statement statement, final Set<String> pending) {
        if (!(statement instanceof ExpressionStatement expression)
                || !(expression.getExpression() instanceof BinaryNode assignment)
                || !assignment.isTokenType(TokenType.ASSIGN)
                || !(assignment.rhs() instanceof TernaryNode withDefault)) {
            return statement;
        }
        final Expression initialiser = withDefault.getTrueExpression().getExpression();
        final Expression checked = (Expression)initialiser.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode nested) {
                return false;
            }

            @Override
            public Node leaveIdentNode(final IdentNode identNode) {
                if (!identNode.isPropertyName() && pending.contains(identNode.getName())) {
                    return new RuntimeNode(identNode.getToken(), identNode.getFinish(),
                            RuntimeNode.Request.UNINITIALIZED_BINDING,
                            LiteralNode.newInstance(identNode.getToken(), identNode.getFinish(),
                                    identNode.getName()));
                }
                return identNode;
            }
        });
        if (checked == initialiser) {
            return statement;
        }
        return expression.setExpression(assignment.setRHS(
                withDefault.setTrueExpression(new JoinPredecessorExpression(checked))));
    }

    /**
     * The prologue that turns an async function's body into a coroutine.
     *
     * It reads exactly as a generator's does and for the same reason: the call
     * hands back a promise and returns, and the body is run by re-entering the
     * function on a thread of its own, where the same prologue answers undefined
     * and lets it through.
     */
    private FunctionNode addAsyncPrologue(final FunctionNode functionNode) {
        // an async generator gets the generator prologue instead (it both yields
        // and awaits, and its parameters are bound at the call like a generator)
        if (!functionNode.isAsync() || functionNode.isAsyncGenerator()) {
            return functionNode;
        }

        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();
        final int line = functionNode.getLineNumber();
        final String created = ":async";
        final Block body = functionNode.getBody();
        final List<Statement> statements = new ArrayList<>();

        statements.add(new VarNode(line, Token.recast(token, TokenType.VAR), finish,
                new IdentNode(token, finish, created),
                new RuntimeNode(token, finish, RuntimeNode.Request.ASYNC_ENTER)));

        final Expression isNotUndefined = new RuntimeNode(token, finish, RuntimeNode.Request.IS_NOT_UNDEFINED,
                new IdentNode(token, finish, created), new IdentNode(token, finish, UNDEFINED_NAME));
        statements.add(new IfNode(line, token, finish, isNotUndefined,
                new Block(token, finish, new ReturnNode(line, token, finish,
                        new IdentNode(token, finish, created))), null));

        statements.addAll(body.getStatements());

        // the body's thread is handed the argument array, as a generator's is
        return functionNode
                .setFlag(lc, FunctionNode.NEEDS_VARARGS)
                .setBody(lc, body.setStatements(lc, statements));
    }

    private FunctionNode addGeneratorPrologue(final FunctionNode functionNode) {
        // Both generators and async generators use this: the body is re-entered
        // on its own thread with its parameters bound at the call, before the
        // (async) generator object exists. An async generator's entry request
        // differs, since it also drives awaits and hands back promises.
        if (!functionNode.isGenerator()) {
            return functionNode;
        }
        final boolean asyncGen = functionNode.isAsyncGenerator();

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
                        ? (asyncGen ? RuntimeNode.Request.ASYNC_GENERATOR_ENTER_PARAMETERS
                                    : RuntimeNode.Request.GENERATOR_ENTER_PARAMETERS)
                        : (asyncGen ? RuntimeNode.Request.ASYNC_GENERATOR_ENTER
                                    : RuntimeNode.Request.GENERATOR_ENTER))));

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

        // A generator hands the argument array to the thread that runs its
        // body, so it is compiled to take one - which an arguments object would
        // also have arranged, and used to, except that a parameter named
        // "arguments" is precisely the case where a function has no arguments
        // object to arrange it with.
        return functionNode
                .setFlag(lc, FunctionNode.NEEDS_VARARGS)
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
        // ES2015 13.15.7 gives the catch parameter an environment of its own
        // and runs the body in one inside it, so the body's own declarations
        // are not what a default in the pattern reads. The body stays a block
        // of its own rather than being taken apart into these statements.
        statements.add(new BlockStatement(body.getFirstStatementLineNumber(), body));

        return super.leaveCatchNode(catchNode
                .setException(ref(catchNode, caught))
                .setBody(new Block(body.getToken(), body.getFinish(),
                        body.getFlags() | Block.IS_SYNTHETIC, statements)));
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
        // A pattern in the head of a declaring for-in/of binds afresh on every
        // iteration, so its names are being initialised rather than reassigned.
        // The parser marks the plain "for (const x of xs)" case itself but has
        // no identifier to mark when the binding is a pattern. An assignment
        // target - "for ({ c } of xs)" - is a write like any other, and writing
        // to a constant is a TypeError.
        declaring = forNode.declaresHead();

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

        if (forNode.isForAwait()) {
            hoisted.addAll(asyncIteration(loop, pattern));
        } else if (forNode.isForOf()) {
            hoisted.addAll(closingIteration(loop));
        } else {
            hoisted.add(loop);
        }
        return hoisted;
    }

    /**
     * ES2018 {@code for await (x of xs) body} - the async iteration protocol,
     * spelled out: obtain the async iterator (wrapping a sync one if the source
     * has no @@asyncIterator), then loop awaiting each next() until it is done.
     */
    private List<Statement> asyncIteration(final ForNode forNode, final boolean pattern) {
        final int line = forNode.getLineNumber();
        final long token = forNode.getToken();
        final int finish = forNode.getFinish();
        final String aiter = newTemporary();
        final String ares = newTemporary();
        // :done guards the close: leaving with it true (a rejected next, or a done
        // result) must not close; leaving with it false (break/return/throw once the
        // body was reached) closes. :threw records a throw so the close is swallowed.
        final String doneFlag = newTemporary();
        final String threwFlag = newTemporary();
        final String caught = newTemporary();

        final Statement getIter = temporaryFor(forNode, aiter,
                runtime(forNode, RuntimeNode.Request.GET_ASYNC_ITERATOR, forNode.getModify().getExpression()));

        // The body (with any head destructuring) is guarded so a throw records
        // :threw and rethrows - the async counterpart of the sync for-of catch.
        final Block rethrow = new Block(token, finish,
                new ExpressionStatement(line, token, finish, assignTemporary(forNode, threwFlag,
                        LiteralNode.newInstance(token, finish, true))),
                new ThrowNode(line, token, finish, ref(forNode, caught), false));
        final Block catches = new Block(token, finish,
                new CatchNode(line, token, finish, ref(forNode, caught), null, rethrow, false));
        final TryNode guardedBody = new TryNode(line, token, finish, forNode.getBody(), List.of(catches), null);

        final List<Statement> whileBody = new ArrayList<>();
        // :done = true  (assume the body is not reached this turn)
        whileBody.add(new ExpressionStatement(line, token, finish,
                assignTemporary(forNode, doneFlag, LiteralNode.newInstance(token, finish, true))));
        // var :ares = await :aiter.next();
        whileBody.add(temporaryFor(forNode, ares,
                runtime(forNode, RuntimeNode.Request.AWAIT,
                        runtime(forNode, RuntimeNode.Request.ASYNC_ITERATOR_NEXT, ref(forNode, aiter)))));
        // if (:ares.done) break;
        whileBody.add(new IfNode(line, token, finish, member(forNode, ares, "done"),
                new Block(token, finish, new BreakNode(line, token, finish, null)), null));
        // :done = false  (the body region is reached - close on an abrupt exit)
        whileBody.add(new ExpressionStatement(line, token, finish,
                assignTemporary(forNode, doneFlag, LiteralNode.newInstance(token, finish, false))));
        // bind the loop variable to :ares.value (kept outside the guard so the
        // loop variable keeps its per-iteration scope)
        whileBody.add(bindLoopTarget(forNode, member(forNode, ares, "value"), pattern));
        whileBody.add(guardedBody);

        final WhileNode loop = new WhileNode(line, token, finish, false,
                new JoinPredecessorExpression(LiteralNode.newInstance(token, finish, true)),
                new Block(forNode.getBody().getToken(), forNode.getBody().getFinish(), whileBody));

        // finally { if (!:done) await ASYNC_ITERATOR_RETURN(:aiter, :threw); }
        final Block close = new Block(token, finish,
                new IfNode(line, token, finish,
                        new UnaryNode(Token.recast(token, TokenType.NOT), ref(forNode, doneFlag)),
                        new Block(token, finish, new ExpressionStatement(line, token, finish,
                                runtime(forNode, RuntimeNode.Request.AWAIT,
                                        runtime(forNode, RuntimeNode.Request.ASYNC_ITERATOR_RETURN,
                                                ref(forNode, aiter), ref(forNode, threwFlag))))),
                        null));

        return List.of(getIter,
                new VarNode(line, Token.recast(token, TokenType.VAR), finish, ref(forNode, doneFlag),
                        LiteralNode.newInstance(token, finish, false)),
                new VarNode(line, Token.recast(token, TokenType.VAR), finish, ref(forNode, threwFlag),
                        LiteralNode.newInstance(token, finish, false)),
                new TryNode(line, token, finish, new Block(token, finish, loop), List.of(), close));
    }

    /** {@code holder[name]} - reads a property of the iterator result. */
    private static Expression member(final Statement at, final String holder, final String name) {
        return new IndexNode(Token.recast(at.getToken(), TokenType.LBRACKET), at.getFinish(),
                ref(at, holder), LiteralNode.newInstance(at.getToken(), at.getFinish(), name));
    }

    /** Binds a for-await loop variable to the iterator value: a declaration or an assignment. */
    private Statement bindLoopTarget(final ForNode forNode, final Expression value, final boolean pattern) {
        final int line = forNode.getLineNumber();
        final long token = forNode.getToken();
        final int finish = forNode.getFinish();
        final Expression init = forNode.getInit();
        // A pattern's target is a temporary the pattern branch already declared
        // (and whose destructuring is now in the body); assign to it.
        if (!pattern && forNode.declaresHead() && init instanceof IdentNode name) {
            final boolean perIteration = forNode.hasPerIterationScope();
            return new VarNode(line, Token.recast(token, perIteration ? TokenType.LET : TokenType.VAR), finish,
                    new IdentNode(name).setIsDeclaredHere(), value, perIteration ? VarNode.IS_LET : 0);
        }
        return new ExpressionStatement(line, token, finish,
                new BinaryNode(Token.recast(token, TokenType.ASSIGN), init, value));
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
     *
     * What the close does wrong is reported when the loop was left normally or
     * by a break, and swallowed when it was left by a throw, which would
     * otherwise lose the error the loop was already carrying. Telling the two
     * apart needs the throw seen, so a second temporary records it, set by a
     * catch that rethrows. That catch goes around the loop's body rather than
     * around the loop: a try between the loop and the block that declares its
     * variable hides it from the per-iteration scope the block builds, and the
     * one exit it does not see - the iterator's own next() throwing - has
     * already marked the iterator done, so the close there is a no-op anyway.
     */
    private List<Statement> closingIteration(final ForNode forNode) {
        final String iterator = newTemporary();
        final Expression get = runtime(forNode, RuntimeNode.Request.GET_ITERATOR,
                forNode.getModify().getExpression());
        final Expression store = new BinaryNode(Token.recast(forNode.getToken(), TokenType.ASSIGN),
                ref(forNode, iterator), get);

        final int line = forNode.getLineNumber();
        final long token = forNode.getToken();
        final int finish = forNode.getFinish();
        final String threw = newTemporary();
        final String caught = newTemporary();

        final Block rethrow = new Block(token, finish,
                new ExpressionStatement(line, token, finish,
                        new BinaryNode(Token.recast(token, TokenType.ASSIGN), ref(forNode, threw),
                                LiteralNode.newInstance(token, finish, true))),
                new ThrowNode(line, token, finish, ref(forNode, caught), false));
        final Block catches = new Block(token, finish,
                new CatchNode(line, token, finish, ref(forNode, caught), null, rethrow, false));
        final Block guardedBody = new Block(forNode.getBody().getToken(), forNode.getBody().getFinish(),
                new TryNode(line, token, finish, forNode.getBody(), List.of(catches), null));

        final ForNode loop = forNode.setBody(lc, guardedBody)
                .setModify(lc, new JoinPredecessorExpression(store));
        final Block body = new Block(forNode.getToken(), forNode.getFinish(), loop);
        final Block close = new Block(token, finish,
                new ExpressionStatement(line, token, finish,
                        runtime(forNode, RuntimeNode.Request.ITERATOR_CLOSE_MAYBE,
                                ref(forNode, iterator), ref(forNode, threw))));

        return List.of(declareTemporary(forNode, iterator),
                new VarNode(line, Token.recast(token, TokenType.VAR), finish, ref(forNode, threw),
                        LiteralNode.newInstance(token, finish, false)),
                new TryNode(line, token, finish, body, List.of(), close));
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
            // A comma chain has nowhere to put a try, so the guard goes around
            // the statement the chain is part of; see expressionGuards.
            final String threwInStatement = newTemporary();
            pendingDeclarations.add(declareTemporary(at, threwInStatement));
            expressionGuards.add(new String[] { iterator, threwInStatement });
            destructureArrayElements(at, pattern, iterator, statements);
            return;
        }

        final List<Statement> guarded = new ArrayList<>();
        destructureArrayElements(at, pattern, iterator, guarded);

        final int line = at.getLineNumber();
        final long token = at.getToken();
        final int finish = at.getFinish();
        final String threw = newTemporary();
        final String caught = newTemporary();

        // ES2015 12.14.5.3: a pattern that gives up part way through tells its
        // iterator so, and it gives up as readily by throwing - out of a target
        // reference, a default, or a nested pattern - as by running out of
        // elements to bind. Closing twice is closing once.
        //
        // What the close itself does wrong is reported unless the pattern was
        // given up by a throw, which is the completion 7.4.6 keeps: a return out
        // of a generator being destructured into does report it. Telling the two
        // apart needs the throw seen, so a catch records it and rethrows.
        final Block rethrow = new Block(token, finish,
                new ExpressionStatement(line, token, finish,
                        new BinaryNode(Token.recast(token, TokenType.ASSIGN), ref(at, threw),
                                LiteralNode.newInstance(token, finish, true))),
                new ThrowNode(line, token, finish, ref(at, caught), false));
        statements.add(new VarNode(line, Token.recast(token, TokenType.VAR), finish, ref(at, threw),
                LiteralNode.newInstance(token, finish, false)));
        statements.add(new TryNode(line, token, finish,
                new Block(token, finish, guarded),
                List.of(new Block(token, finish,
                        new CatchNode(line, token, finish, ref(at, caught), null, rethrow, false))),
                new Block(token, finish,
                        new ExpressionStatement(line, token, finish,
                                runtime(at, RuntimeNode.Request.ITERATOR_CLOSE_MAYBE,
                                        ref(at, iterator), ref(at, threw))))));
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

        // ES2018: the keys bound by name, excluded from a trailing ...rest.
        final List<Expression> excludedKeys = new ArrayList<>();
        for (final PropertyNode property : pattern.getElements()) {
            if (isObjectSpread(property)) {
                // ...rest - the grammar guarantees it is the last element. It
                // gets a fresh object of source's own enumerable properties
                // except the ones already bound.
                final Expression restTarget = ((UnaryNode) property.getKey()).getExpression();
                final Expression keysArray = LiteralNode.newInstance(at.getToken(), at.getFinish(),
                        new ArrayList<>(excludedKeys));
                bindWithDefault(at, restTarget,
                        runtime(at, RuntimeNode.Request.COPY_OWN_ENUMERABLE, ref(at, source), keysArray),
                        statements);
                continue;
            }
            Expression key = property.getKey();
            if (property.isComputed()) {
                // 12.15.5.3 evaluates the property name, and makes a property
                // key of it, before the target it will be read into is
                // evaluated: both are observable, and the target's own key is
                // read after this one
                final String held = newTemporary();
                statements.add(temporaryFor(at, held,
                        runtime(at, RuntimeNode.Request.TO_PROPERTY_KEY, key)));
                excludedKeys.add(ref(at, held));
                key = ref(at, held);
            } else {
                // a written name stands for a string; a numeric/string literal
                // key is already a value the runtime coerces to a property key
                excludedKeys.add(key instanceof IdentNode name
                        ? LiteralNode.newInstance(key.getToken(), key.getFinish(), name.getName())
                        : key);
            }
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
            // 14.3.3.3 resolves the target before it reads the value, and both
            // are observable, so the read goes inside the assignment rather than
            // in front of it: what the temporary holds is what the assignment
            // begins by evaluating
            final String holder = newTemporary();
            statements.add(declareTemporary(at, holder));
            bind(at, withDefault.lhs(),
                    sequence(at, assignTemporary(at, holder, value), defaulted(at, holder, withDefault.rhs())),
                    statements);
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
        // The undefined side is the value, not the global name for it: reading
        // the name is observable where the scope is dynamic - a with block's
        // object is asked whether it has one - and 8.5.2 makes no such read
        final Expression isUndefined = runtime(at, RuntimeNode.Request.IS_UNDEFINED,
                ref(at, holder), LiteralNode.newInstance(at.getToken(), at.getFinish(), ScriptRuntime.UNDEFINED));
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

    /** {@code (first, second)}, evaluated in that order and worth the second. */
    private static Expression sequence(final Statement at, final Expression first, final Expression second) {
        return new BinaryNode(Token.recast(at.getToken(), TokenType.COMMARIGHT), first, second);
    }

    /** {@code :temp = value}, for a temporary already declared. */
    private static Expression assignTemporary(final Statement at, final String name, final Expression value) {
        return new BinaryNode(Token.recast(at.getToken(), TokenType.ASSIGN), ref(at, name), value);
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
                return new org.monflabs.nashorn.internal.ir.AccessNode(at.getToken(), at.getFinish(), base, name.getName());
            }
            final Object value = ((LiteralNode<?>)key).getValue();
            if (value instanceof String string) {
                return new org.monflabs.nashorn.internal.ir.AccessNode(at.getToken(), at.getFinish(), base, string);
            }
            // the token type is what says this is o[k] rather than o.k
            return new IndexNode(Token.recast(at.getToken(), TokenType.LBRACKET), at.getFinish(), base, key);
        }
    }
}
