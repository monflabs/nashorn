/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.nashorn.internal.parser;

import static org.openjdk.nashorn.internal.codegen.CompilerConstants.ANON_FUNCTION_PREFIX;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.EVAL;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.PROGRAM;
import static org.openjdk.nashorn.internal.parser.TokenType.ARROW;
import static org.openjdk.nashorn.internal.parser.TokenType.ASSIGN;
import static org.openjdk.nashorn.internal.parser.TokenType.CASE;
import static org.openjdk.nashorn.internal.parser.TokenType.CATCH;
import static org.openjdk.nashorn.internal.parser.TokenType.CLASS;
import static org.openjdk.nashorn.internal.parser.TokenType.COLON;
import static org.openjdk.nashorn.internal.parser.TokenType.COMMARIGHT;
import static org.openjdk.nashorn.internal.parser.TokenType.COMMENT;
import static org.openjdk.nashorn.internal.parser.TokenType.CONST;
import static org.openjdk.nashorn.internal.parser.TokenType.DECPOSTFIX;
import static org.openjdk.nashorn.internal.parser.TokenType.DECPREFIX;
import static org.openjdk.nashorn.internal.parser.TokenType.ELLIPSIS;
import static org.openjdk.nashorn.internal.parser.TokenType.ELSE;
import static org.openjdk.nashorn.internal.parser.TokenType.EOF;
import static org.openjdk.nashorn.internal.parser.TokenType.EOL;
import static org.openjdk.nashorn.internal.parser.TokenType.EQ_STRICT;
import static org.openjdk.nashorn.internal.parser.TokenType.ESCSTRING;
import static org.openjdk.nashorn.internal.parser.TokenType.EXPORT;
import static org.openjdk.nashorn.internal.parser.TokenType.EXTENDS;
import static org.openjdk.nashorn.internal.parser.TokenType.FINALLY;
import static org.openjdk.nashorn.internal.parser.TokenType.FUNCTION;
import static org.openjdk.nashorn.internal.parser.TokenType.IDENT;
import static org.openjdk.nashorn.internal.parser.TokenType.IF;
import static org.openjdk.nashorn.internal.parser.TokenType.IMPORT;
import static org.openjdk.nashorn.internal.parser.TokenType.INCPOSTFIX;
import static org.openjdk.nashorn.internal.parser.TokenType.LBRACE;
import static org.openjdk.nashorn.internal.parser.TokenType.LBRACKET;
import static org.openjdk.nashorn.internal.parser.TokenType.LET;
import static org.openjdk.nashorn.internal.parser.TokenType.LPAREN;
import static org.openjdk.nashorn.internal.parser.TokenType.EXP;
import static org.openjdk.nashorn.internal.parser.TokenType.MUL;
import static org.openjdk.nashorn.internal.parser.TokenType.PERIOD;
import static org.openjdk.nashorn.internal.parser.TokenType.RBRACE;
import static org.openjdk.nashorn.internal.parser.TokenType.RBRACKET;
import static org.openjdk.nashorn.internal.parser.TokenType.RPAREN;
import static org.openjdk.nashorn.internal.parser.TokenType.SEMICOLON;
import static org.openjdk.nashorn.internal.parser.TokenType.SPREAD_ARRAY;
import static org.openjdk.nashorn.internal.parser.TokenType.STATIC;
import static org.openjdk.nashorn.internal.parser.TokenType.STRING;
import static org.openjdk.nashorn.internal.parser.TokenType.SUPER;
import static org.openjdk.nashorn.internal.parser.TokenType.TEMPLATE;
import static org.openjdk.nashorn.internal.parser.TokenType.TEMPLATE_HEAD;
import static org.openjdk.nashorn.internal.parser.TokenType.TEMPLATE_MIDDLE;
import static org.openjdk.nashorn.internal.parser.TokenType.TEMPLATE_TAIL;
import static org.openjdk.nashorn.internal.parser.TokenType.TERNARY;
import static org.openjdk.nashorn.internal.parser.TokenType.VAR;
import static org.openjdk.nashorn.internal.parser.TokenType.VOID;
import static org.openjdk.nashorn.internal.parser.TokenType.WHILE;
import static org.openjdk.nashorn.internal.parser.TokenType.YIELD;
import static org.openjdk.nashorn.internal.parser.TokenType.YIELD_STAR;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.openjdk.nashorn.internal.codegen.CompilerConstants;
import org.openjdk.nashorn.internal.codegen.Namespace;
import org.openjdk.nashorn.internal.ir.AccessNode;
import org.openjdk.nashorn.internal.ir.BaseNode;
import org.openjdk.nashorn.internal.ir.BinaryNode;
import org.openjdk.nashorn.internal.ir.Block;
import org.openjdk.nashorn.internal.ir.BlockStatement;
import org.openjdk.nashorn.internal.ir.BreakNode;
import org.openjdk.nashorn.internal.ir.CallNode;
import org.openjdk.nashorn.internal.ir.CaseNode;
import org.openjdk.nashorn.internal.ir.CatchNode;
import org.openjdk.nashorn.internal.ir.ClassNode;
import org.openjdk.nashorn.internal.ir.ContinueNode;
import org.openjdk.nashorn.internal.ir.DebuggerNode;
import org.openjdk.nashorn.internal.ir.EmptyNode;
import org.openjdk.nashorn.internal.ir.ErrorNode;
import org.openjdk.nashorn.internal.ir.Expression;
import org.openjdk.nashorn.internal.ir.ExpressionList;
import org.openjdk.nashorn.internal.ir.ExpressionStatement;
import org.openjdk.nashorn.internal.ir.ForNode;
import org.openjdk.nashorn.internal.ir.FunctionNode;
import org.openjdk.nashorn.internal.ir.IdentNode;
import org.openjdk.nashorn.internal.ir.IfNode;
import org.openjdk.nashorn.internal.ir.IndexNode;
import org.openjdk.nashorn.internal.ir.JoinPredecessorExpression;
import org.openjdk.nashorn.internal.ir.LabelNode;
import org.openjdk.nashorn.internal.ir.LexicalContext;
import org.openjdk.nashorn.internal.ir.LiteralNode;
import org.openjdk.nashorn.internal.ir.Module;
import org.openjdk.nashorn.internal.ir.Node;
import org.openjdk.nashorn.internal.ir.ObjectNode;
import org.openjdk.nashorn.internal.ir.PropertyKey;
import org.openjdk.nashorn.internal.ir.PropertyNode;
import org.openjdk.nashorn.internal.ir.ReturnNode;
import org.openjdk.nashorn.internal.ir.RuntimeNode;
import org.openjdk.nashorn.internal.ir.Statement;
import org.openjdk.nashorn.internal.ir.SwitchNode;
import org.openjdk.nashorn.internal.ir.TemplateLiteral;
import org.openjdk.nashorn.internal.ir.TernaryNode;
import org.openjdk.nashorn.internal.ir.ThrowNode;
import org.openjdk.nashorn.internal.ir.TryNode;
import org.openjdk.nashorn.internal.ir.UnaryNode;
import org.openjdk.nashorn.internal.ir.VarNode;
import org.openjdk.nashorn.internal.ir.WhileNode;
import org.openjdk.nashorn.internal.ir.WithNode;
import org.openjdk.nashorn.internal.ir.debug.ASTWriter;
import org.openjdk.nashorn.internal.ir.debug.PrintVisitor;
import org.openjdk.nashorn.internal.ir.visitor.NodeVisitor;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ErrorManager;
import org.openjdk.nashorn.internal.runtime.JSErrorType;
import org.openjdk.nashorn.internal.runtime.ParserException;
import org.openjdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import org.openjdk.nashorn.internal.runtime.ScriptEnvironment;
import org.openjdk.nashorn.internal.runtime.ScriptFunctionData;
import org.openjdk.nashorn.internal.runtime.Source;
import org.openjdk.nashorn.internal.runtime.Timing;
import org.openjdk.nashorn.internal.runtime.linker.NameCodec;
import org.openjdk.nashorn.internal.runtime.logging.DebugLogger;
import org.openjdk.nashorn.internal.runtime.logging.Loggable;
import org.openjdk.nashorn.internal.runtime.logging.Logger;

/**
 * Builds the IR.
 */
@Logger(name="parser")
public class Parser extends AbstractParser implements Loggable {
    private static final String ARGUMENTS_NAME = CompilerConstants.ARGUMENTS_VAR.symbolName();
    private static final String CONSTRUCTOR_NAME = "constructor";
    /** The property name that reparents the object it is written in (B.3.1). */
    private static final String PROTO_PROPERTY_NAME = "__proto__";

    /** The temporary a class declaration is carried out of its own scope in. */
    private static final String CLASS_CARRIER_PREFIX = ":class";

    /**
     * Whether a method's key is being read on its own during a reparse.
     *
     * A method is recompiled from its own source text, which begins at its key,
     * and a computed key may hold a yield or an await - the enclosing generator
     * or async function is not there to say so, because nothing of it is in
     * range. The key was read once already when the whole was parsed, so
     * allowing the words here cannot let anything through that was not allowed
     * then; it only lets the same text be read the same way twice.
     */
    private boolean reparsingPropertyKey;
    /** Whether an async arrow's parameter list is being parsed, where await is a keyword. */
    private boolean inAsyncParameters;
    /**
     * Whether nothing in the statement being parsed has run yet, so that a class
     * expression met now can be lifted into a scope of its own. See
     * {@link #classInOwnScope}.
     */
    private boolean nothingEvaluatedYet;
    /**
     * Where a CoverInitializedName was written, while it is still unknown whether
     * the object literal holding it is a destructuring pattern. Zero when there
     * is none outstanding. See {@link #verifyNoCoverInitializedName}.
     */
    private long coverInitializedName;

    /**
     * The expression parentheses were last read around, which is the one an
     * assignment operator met now would have on its left. See
     * {@link #verifyAssignment}.
     */
    private Expression parenthesized;

    private static final String ASYNC_NAME = "async";
    private static final String AWAIT_NAME = "await";
    private static final String YIELD_NAME = "yield";
    private static final String GET_NAME = "get";
    private static final String SET_NAME = "set";

    /** Current env. */
    private final ScriptEnvironment env;

    /** Is scripting mode. */
    private final boolean scripting;

    private List<Statement> functionDeclarations;

    private final ParserContext lc;
    private final Deque<Object> defaultNames;

    /**
     * Whether the name {@link #getDefaultFunctionName()} last produced came from a
     * binding or a property, which ES2015 12.14.4 turns into the function's name,
     * rather than from a member expression, which it does not.
     */
    private boolean defaultNameIsBinding;

    /** Declarations for the names a module's imports bind, collected while parsing them. */
    private final List<Statement> importedBindings = new ArrayList<>();

    /** Namespace for function names where not explicitly given */
    private final Namespace namespace;

    private final DebugLogger log;

    /** to receive line information from Lexer when scanning multine literals. */
    protected final Lexer.LineInfoReceiver lineInfoReceiver;

    private RecompilableScriptFunctionData reparsedFunction;

    /** Whether this is eval code whose caller was a function, so new.target is legal at its top level. */
    private boolean evalNewTargetAllowed;

    /** Whether this is eval code whose caller was a method, so super is legal at its top level. */
    private boolean evalSuperAllowed;

    /**
     * Records what the direct eval this parses for was called from.
     *
     * ES2015 18.2.1.1 evaluates direct eval code in the caller's function
     * context, so {@code new.target} and {@code super} are legal at the top
     * level of the eval exactly when they were legal where the call was
     * written, and a syntax error otherwise.
     *
     * @param newTargetAllowed the eval was called from a function
     * @param superAllowed     the eval was called from a method
     */
    public void setEvalContext(final boolean newTargetAllowed, final boolean superAllowed) {
        this.evalNewTargetAllowed = newTargetAllowed;
        this.evalSuperAllowed = superAllowed;
    }

    /**
     * Constructor
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors) {
        this(env, source, errors, env._strict, null);
    }

    /**
     * Constructor
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     * @param strict  strict
     * @param log debug logger if one is needed
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors, final boolean strict, final DebugLogger log) {
        this(env, source, errors, strict, 0, log);
    }

    /**
     * Construct a parser.
     *
     * @param env     script environment
     * @param source  source to parse
     * @param errors  error manager
     * @param strict  parser created with strict mode enabled.
     * @param lineOffset line offset to start counting lines from
     * @param log debug logger if one is needed
     */
    public Parser(final ScriptEnvironment env, final Source source, final ErrorManager errors, final boolean strict, final int lineOffset, final DebugLogger log) {
        super(source, errors, strict, lineOffset);
        this.lc = new ParserContext();
        this.defaultNames = new ArrayDeque<>();
        this.env = env;
        this.namespace = new Namespace(env.getNamespace());
        this.scripting = env._scripting;
        if (this.scripting) {
            this.lineInfoReceiver = (receiverLine, receiverLinePosition) -> {
                // update the parser maintained line information
                Parser.this.line = receiverLine;
                Parser.this.linePosition = receiverLinePosition;
            };
        } else {
            // non-scripting mode script can't have multi-line literals
            this.lineInfoReceiver = null;
        }

        this.log = log == null ? DebugLogger.DISABLED_LOGGER : log;
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    /**
     * Sets the name for the first function. This is only used when reparsing anonymous functions to ensure they can
     * preserve their already assigned name, as that name doesn't appear in their source text.
     * @param name the name for the first parsed function.
     */
    public void setFunctionName(final String name) {
        defaultNames.push(createIdentNode(0, 0, name));
    }

    /**
     * Sets the {@link RecompilableScriptFunctionData} representing the function being reparsed (when this
     * parser instance is used to reparse a previously parsed function, as part of its on-demand compilation).
     * This will trigger various special behaviors, such as skipping nested function bodies.
     * @param reparsedFunction the function being reparsed.
     */
    public void setReparsedFunction(final RecompilableScriptFunctionData reparsedFunction) {
        this.reparsedFunction = reparsedFunction;
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * This is the default parse call, which will name the function node
     * {code :program} {@link CompilerConstants#PROGRAM}
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse() {
        return parse(PROGRAM.symbolName(), 0, source.getLength(), 0);
    }

    /**
     * Set up first token. Skips opening EOL.
     */
    private void scanFirstToken() {
        k = -1;
        next();
    }

    /**
     * Execute parse and return the resulting function node.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * This should be used to create one and only one function node
     *
     * @param scriptName name for the script, given to the parsed FunctionNode
     * @param startPos start position in source
     * @param len length of parse
     * @param reparseFlags flags provided by {@link RecompilableScriptFunctionData} as context for
     * the code being reparsed. This allows us to recognize special forms of functions such
     * as property getters and setters or instances of ES6 method shorthand in object literals.
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parse(final String scriptName, final int startPos, final int len, final int reparseFlags) {
        final boolean isTimingEnabled = env.isTimingEnabled();
        final long t0 = isTimingEnabled ? System.nanoTime() : 0L;
        log.info(this, " begin for '", scriptName, "'");

        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, startPos, len, stream, scripting && !env._no_syntax_extensions, reparsedFunction != null);
            lexer.line = lexer.pendingLine = lineOffset + 1;
            line = lineOffset;

            scanFirstToken();
            // Begin parse.
            return program(scriptName, reparseFlags);
        } catch (final Exception e) {
            handleParseException(e);

            return null;
        } finally {
            final String end = this + " end '" + scriptName + "'";
            if (isTimingEnabled) {
                env._timing.accumulateTime(toString(), System.nanoTime() - t0);
                log.info(end, "' in ", Timing.toMillisPrint(System.nanoTime() - t0), " ms");
            } else {
                log.info(end);
            }
        }
    }

    /**
     * Parse and return the resulting module.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail
     *
     * @param moduleName name for the module, given to the parsed FunctionNode
     * @param startPos start position in source
     * @param len length of parse
     *
     * @return function node resulting from successful parse
     */
    public FunctionNode parseModule(final String moduleName, final int startPos, final int len) {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, startPos, len, stream, scripting && !env._no_syntax_extensions, reparsedFunction != null);
            lexer.line = lexer.pendingLine = lineOffset + 1;
            line = lineOffset;

            scanFirstToken();
            // Begin parse.
            return module(moduleName);
        } catch (final Exception e) {
            handleParseException(e);

            return null;
        }
    }

    /**
     * Entry point for parsing a module.
     *
     * @param moduleName the module name
     * @return the parsed module
     */
    public FunctionNode parseModule(final String moduleName) {
        return parseModule(moduleName, 0, source.getLength());
    }

    /**
     * Parse list of function parameters. A comma
     * separated list of function parameter identifiers is expected to be parsed.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail. This method is used to check if parameter Strings
     * passed to "Function" constructor is a valid or not.
     */
    public void parseFormalParameterList() {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, stream, scripting && !env._no_syntax_extensions);

            scanFirstToken();

            formalParameterList(TokenType.EOF, false);
        } catch (final Exception e) {
            handleParseException(e);
        }
    }

    /**
     * Execute parse.
     * Errors will be thrown and the error manager will contain information
     * if parsing should fail. This method is used to check if code String
     * passed to "Function" constructor is a valid function body or not.
     *
     */
    public void parseFunctionBody() {
        try {
            stream = new TokenStream();
            lexer  = new Lexer(source, stream, scripting && !env._no_syntax_extensions);
            final int functionLine = line;

            scanFirstToken();

            // Make a fake token for the function.
            final long functionToken = Token.toDesc(FUNCTION, 0, source.getLength());
            // Set up the function to append elements.

            final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), PROGRAM.symbolName());
            final ParserContextFunctionNode function = createParserContextFunctionNode(ident, functionToken, FunctionNode.Kind.NORMAL, functionLine, Collections.emptyList());
            lc.push(function);

            final ParserContextBlockNode body = newBlock();

            functionDeclarations = new ArrayList<>();
            sourceElements(0);
            addFunctionDeclarations(function);
            functionDeclarations = null;

            restoreBlock(body);
            body.setFlag(Block.NEEDS_SCOPE);

            final Block functionBody = new Block(functionToken, source.getLength() - 1,
                body.getFlags() | Block.IS_SYNTHETIC, body.getStatements());
            lc.pop(function);

            expect(EOF);

            final FunctionNode functionNode = createFunctionNode(
                    function,
                    functionToken,
                    ident,
                    Collections.emptyList(),
                    FunctionNode.Kind.NORMAL,
                    functionLine,
                    functionBody);
            printAST(functionNode);
        } catch (final Exception e) {
            handleParseException(e);
        }
    }

    private void handleParseException(final Exception e) {
        // Extract message from exception.  The message will be in error
        // message format.
        String message = e.getMessage();

        // If empty message.
        if (message == null) {
            message = e.toString();
        }

        // Issue message.
        if (e instanceof ParserException) {
            errors.error((ParserException)e);
        } else {
            errors.error(message);
        }

        if (env._dump_on_error) {
            e.printStackTrace(env.getErr());
        }
    }

    /**
     * Skip to a good parsing recovery point.
     */
    private void recover(final Exception e) {
        if (e != null) {
            // Extract message from exception.  The message will be in error
            // message format.
            String message = e.getMessage();

            // If empty message.
            if (message == null) {
                message = e.toString();
            }

            // Issue message.
            if (e instanceof ParserException) {
                errors.error((ParserException)e);
            } else {
                errors.error(message);
            }

            if (env._dump_on_error) {
                e.printStackTrace(env.getErr());
            }
        }

        // Skip to a recovery point.
        loop:
        while (true) {
            switch (type) {
            case EOF:
                // Can not go any further.
                break loop;
            case EOL:
            case SEMICOLON:
            case RBRACE:
                // Good recovery points.
                next();
                break loop;
            default:
                // So we can recover after EOL.
                nextOrEOL();
                break;
            }
        }
    }

    /**
     * Set up a new block.
     *
     * @return New block.
     */
    private ParserContextBlockNode newBlock() {
        return lc.push(new ParserContextBlockNode(token));
    }

    private ParserContextFunctionNode createParserContextFunctionNode(final IdentNode ident, final long functionToken, final FunctionNode.Kind kind, final int functionLine, final List<IdentNode> parameters) {
        // A statement of the function's own is being started, and a class inside
        // it may not be lifted out past the function's boundary.
        nothingEvaluatedYet = false;

        // Build function name.
        final StringBuilder sb = new StringBuilder();

        final ParserContextFunctionNode parentFunction = lc.getCurrentFunction();
        if (parentFunction != null && !parentFunction.isProgram()) {
            sb.append(parentFunction.getName()).append(CompilerConstants.NESTED_FUNCTION_SEPARATOR.symbolName());
        }

        assert ident.getName() != null;
        sb.append(ident.getName());

        final String name = namespace.uniqueName(sb.toString());
        assert parentFunction != null || kind == FunctionNode.Kind.MODULE || name.equals(PROGRAM.symbolName()) : "name = " + name;

        int flags = 0;
        if (isStrictMode) {
            flags |= FunctionNode.IS_STRICT;
        }
        if (parentFunction == null) {
            flags |= FunctionNode.IS_PROGRAM;
        }

        final ParserContextFunctionNode functionNode = new ParserContextFunctionNode(functionToken, ident, name, namespace, functionLine, kind, parameters);
        functionNode.setFlag(flags);
        return functionNode;
    }

    private FunctionNode createFunctionNode(final ParserContextFunctionNode function, final long startToken, final IdentNode ident, final List<IdentNode> parameters, final FunctionNode.Kind kind, final int functionLine, final Block body) {
        // assert body.isFunctionBody() || body.getFlag(Block.IS_PARAMETER_BLOCK) && ((BlockStatement) body.getLastStatement()).getBlock().isFunctionBody();
        // Start new block.
        final FunctionNode functionNode =
            new FunctionNode(
                source,
                functionLine,
                body.getToken(),
                Token.descPosition(body.getToken()),
                startToken,
                function.getLastToken(),
                namespace,
                ident,
                function.getName(),
                parameters,
                function.getParameterExpressions(),
                kind,
                function.getFlags(),
                body,
                function.getEndParserState(),
                function.getModule(),
                function.getDebugFlags());

        printAST(functionNode);

        return functionNode;
    }

    /**
     * Restore the current block.
     */
    private void restoreBlock(final ParserContextBlockNode block) {
        lc.pop(block);
    }

    /**
     * Get the statements in a block.
     * @return Block statements.
     */
    private Block getBlock(final boolean needsBraces) {
        final long blockToken = token;
        final ParserContextBlockNode newBlock = newBlock();
        try {
            // Block opening brace.
            if (needsBraces) {
                expect(LBRACE);
            }
            // Accumulate block statements.
            statementList();

        } finally {
            restoreBlock(newBlock);
        }

        // Block closing brace.
        if (needsBraces) {
            expect(RBRACE);
        }

        final int flags = newBlock.getFlags() | (needsBraces ? 0 : Block.IS_SYNTHETIC);
        return new Block(blockToken, finish, flags, newBlock.getStatements());
    }

    /**
     * Get all the statements generated by a single statement.
     * @return Statements.
     */

    private Block getStatement() {
        if (type == LBRACE) {
            return getBlock(true);
        }
        // Set up new block. Captures first token.
        final ParserContextBlockNode newBlock = newBlock();
        try {
            statement(false, 0, true);
        } finally {
            restoreBlock(newBlock);
        }
        return new Block(newBlock.getToken(), finish, newBlock.getFlags() | Block.IS_SYNTHETIC, newBlock.getStatements());
    }

    /**
     * Detect calls to special functions.
     * @param ident Called function.
     */
    private void detectSpecialFunction(final IdentNode ident) {
        final String name = ident.getName();

        if (EVAL.symbolName().equals(name)) {
            markEval(lc);
        } else if (SUPER.getName().equals(name)) {
            assert ident.isDirectSuper();
            markSuperCall(lc);
        }
    }

    /**
     * Detect use of special properties.
     * @param ident Referenced property.
     */
    private void detectSpecialProperty(final IdentNode ident) {
        if (isArguments(ident)) {
            // skip over arrow functions, e.g. function f() { return (() => arguments.length)(); }
            getCurrentNonArrowFunction().setFlag(FunctionNode.USES_ARGUMENTS);
        }
    }

    private static boolean isArguments(final String name) {
        return ARGUMENTS_NAME.equals(name);
    }

    static boolean isArguments(final IdentNode ident) {
        return isArguments(ident.getName());
    }

    /**
     * Tells whether a IdentNode can be used as L-value of an assignment
     *
     * @param ident IdentNode to be checked
     * @return whether the ident can be used as L-value
     */
    private static boolean checkIdentLValue(final IdentNode ident) {
        return ident.tokenType().getKind() != TokenKind.KEYWORD;
    }

    /**
     * Verify an assignment expression.
     * @param op  Operation token.
     * @param lhs Left hand side expression.
     * @param rhs Right hand side expression.
     * @return Verified expression.
     */
    private Expression verifyAssignment(final long op, final Expression lhs, final Expression rhs) {
        final TokenType opType = Token.descType(op);

        switch (opType) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_EXP:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
            if (lhs instanceof IdentNode) {
                if (isReservedTarget(lhs)) {
                    return referenceError(lhs, rhs, env._early_lvalue_error);
                }
                if (!checkIdentLValue((IdentNode)lhs)) {
                    return referenceError(lhs, rhs, false);
                }
                verifyIdent((IdentNode)lhs, "assignment");
                break;
            } else if (lhs instanceof AccessNode || lhs instanceof IndexNode) {
                break;
            } else if (opType == ASSIGN && isDestructuringLhs(lhs) && lhs != parenthesized) {
                verifyDestructuringAssignmentPattern(lhs, "assignment");
                break;
            } else {
                return referenceError(lhs, rhs, env._early_lvalue_error);
            }
        default:
            break;
        }

        // Build up node.
        if(BinaryNode.isLogical(opType)) {
            return new BinaryNode(op, new JoinPredecessorExpression(lhs), new JoinPredecessorExpression(rhs));
        }
        return new BinaryNode(op, lhs, rhs);
    }

    private boolean isDestructuringLhs(final Expression lhs) {
        return lhs instanceof ObjectNode || lhs instanceof LiteralNode.ArrayLiteralNode;
    }

    private void verifyDestructuringAssignmentPattern(final Expression pattern, final String contextString) {
        // the object literal is a pattern after all, so a shorthand with an
        // initializer in it is a property definition rather than an error
        coverInitializedName = 0L;
        assert pattern instanceof ObjectNode || pattern instanceof LiteralNode.ArrayLiteralNode;
        pattern.accept(new VerifyDestructuringPatternNodeVisitor(new LexicalContext()) {
            @Override
            protected void verifySpreadElement(final Expression lvalue) {
                if (!checkValidLValue(lvalue, contextString)) {
                    throw error(AbstractParser.message("invalid.lvalue"), lvalue.getToken());
                }
            }

            @Override
            public boolean enterIdentNode(final IdentNode identNode) {
                verifyIdent(identNode, contextString);
                if (!checkIdentLValue(identNode)) {
                    referenceError(identNode, null, true);
                    return false;
                }
                return false;
            }

            @Override
            public boolean enterAccessNode(final AccessNode accessNode) {
                return false;
            }

            @Override
            public boolean enterIndexNode(final IndexNode indexNode) {
                return false;
            }

            @Override
            protected boolean enterDefault(final Node node) {
                throw error(String.format("unexpected node in AssignmentPattern: %s", node));
            }
        });
    }

    /**
     * Reduce increment/decrement to simpler operations.
     * @param firstToken First token.
     * @param tokenType  Operation token (INCPREFIX/DEC.)
     * @param expression Left hand side expression.
     * @param isPostfix  Prefix or postfix.
     * @return           Reduced expression.
     */
    private static UnaryNode incDecExpression(final long firstToken, final TokenType tokenType, final Expression expression, final boolean isPostfix) {
        if (isPostfix) {
            return new UnaryNode(Token.recast(firstToken, tokenType == DECPREFIX ? DECPOSTFIX : INCPOSTFIX), expression.getStart(), Token.descPosition(firstToken) + Token.descLength(firstToken), expression);
        }

        return new UnaryNode(firstToken, expression);
    }

    /**
     * -----------------------------------------------------------------------
     *
     * Grammar based on
     *
     *      ECMAScript Language Specification
     *      ECMA-262 5th Edition / December 2009
     *
     * -----------------------------------------------------------------------
     */

    /**
     * Program :
     *      SourceElements?
     *
     * See 14
     *
     * Parse the top level script.
     */
    private FunctionNode program(final String scriptName, final int reparseFlags) {
        // Make a pseudo-token for the script holding its start and length.
        final long functionToken = Token.toDesc(FUNCTION, Token.descPosition(Token.withDelimiter(token)), source.getLength());
        final int  functionLine  = line;

        final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), scriptName);
        final ParserContextFunctionNode script = createParserContextFunctionNode(
                ident,
                functionToken,
                FunctionNode.Kind.SCRIPT,
                functionLine,
                Collections.emptyList());
        lc.push(script);
        final ParserContextBlockNode body = newBlock();

        functionDeclarations = new ArrayList<>();
        sourceElements(reparseFlags);
        addFunctionDeclarations(script);
        functionDeclarations = null;

        restoreBlock(body);
        body.setFlag(Block.NEEDS_SCOPE);
        final Block programBody = new Block(functionToken, finish, body.getFlags() | Block.IS_SYNTHETIC | Block.IS_BODY, body.getStatements());
        lc.pop(script);
        script.setLastToken(token);

        expect(EOF);

        return createFunctionNode(script, functionToken, ident, Collections.emptyList(), FunctionNode.Kind.SCRIPT, functionLine, programBody);
    }

    /**
     * Directive value or null if statement is not a directive.
     *
     * @param stmt Statement to be checked
     * @return Directive value if the given statement is a directive
     */
    private String getDirective(final Node stmt) {
        if (stmt instanceof ExpressionStatement) {
            final Node expr = ((ExpressionStatement)stmt).getExpression();
            if (expr instanceof LiteralNode) {
                final LiteralNode<?> lit = (LiteralNode<?>)expr;
                final long litToken = lit.getToken();
                final TokenType tt = Token.descType(litToken);
                // A directive is either a string or an escape string
                if (tt == TokenType.STRING || tt == TokenType.ESCSTRING) {
                    // Make sure that we don't unescape anything. Return as seen in source!
                    return source.getString(lit.getStart(), Token.descLength(litToken));
                }
            }
        }

        return null;
    }

    /**
     * SourceElements :
     *      SourceElement
     *      SourceElements SourceElement
     *
     * See 14
     *
     * Parse the elements of the script or function.
     */
    private void sourceElements(final int reparseFlags) {
        List<Node>    directiveStmts        = null;
        boolean       checkDirective        = true;
        int           functionFlags          = reparseFlags;
        final boolean oldStrictMode         = isStrictMode;


        try {
            // If is a script, then process until the end of the script.
            while (type != EOF) {
                // Break if the end of a code block.
                if (type == RBRACE) {
                    break;
                }

                try {
                    // Get the next element.
                    statement(true, functionFlags, false);
                    functionFlags = 0;

                    // check for directive prologues
                    if (checkDirective) {
                        // skip any debug statement like line number to get actual first line
                        final Statement lastStatement = lc.getLastStatement();

                        // get directive prologue, if any
                        final String directive = getDirective(lastStatement);

                        // If we have seen first non-directive statement,
                        // no more directive statements!!
                        checkDirective = directive != null;

                        if (checkDirective) {
                            if (!oldStrictMode) {
                                if (directiveStmts == null) {
                                    directiveStmts = new ArrayList<>();
                                }
                                directiveStmts.add(lastStatement);
                            }

                            // handle use strict directive
                            if ("use strict".equals(directive)) {
                                isStrictMode = true;
                                final ParserContextFunctionNode function = lc.getCurrentFunction();

                                // ES2015 14.1.2: a function whose parameter list
                                // is not simple may not say "use strict" in its
                                // body. The parameter list would have to be
                                // parsed under a strictness its own text
                                // declares, which is why it is an early error
                                // rather than a matter of ordering.
                                if (function != null && !function.isSimpleParameterList()) {
                                    throw error(AbstractParser.message("strict.use.strict.non.simple.parameters"),
                                            lastStatement.getToken());
                                }

                                function.setFlag(FunctionNode.IS_STRICT);

                                // We don't need to check these, if lexical environment is already strict
                                if (!oldStrictMode) {
                                    // check that directives preceding this one do not violate strictness
                                    for (final Node statement : directiveStmts) {
                                        // the get value will force unescape of preceding
                                        // escaped string directives
                                        getValue(statement.getToken());
                                    }

                                    // verify that function name as well as parameter names
                                    // satisfy strict mode restrictions.
                                    verifyIdent(function.getIdent(), "function name");
                                    for (final IdentNode param : function.getParameters()) {
                                        verifyIdent(param, "function parameter");
                                    }
                                }
                            } else if (Context.DEBUG) {
                                final int debugFlag = FunctionNode.getDirectiveFlag(directive);
                                if (debugFlag != 0) {
                                    final ParserContextFunctionNode function = lc.getCurrentFunction();
                                    function.setDebugFlag(debugFlag);
                                }
                            }
                        }
                    }
                } catch (final Exception e) {
                    final int errorLine = line;
                    final long errorToken = token;
                    //recover parsing
                    recover(e);
                    final ErrorNode errorExpr = new ErrorNode(errorToken, finish);
                    final ExpressionStatement expressionStatement = new ExpressionStatement(errorLine, errorToken, finish, errorExpr);
                    appendStatement(expressionStatement);
                }

                // No backtracking from here on.
                stream.commit(k);
            }
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    /**
     * Parse any of the basic statement types.
     *
     * Statement :
     *      BlockStatement
     *      VariableStatement
     *      EmptyStatement
     *      ExpressionStatement
     *      IfStatement
     *      BreakableStatement
     *      ContinueStatement
     *      BreakStatement
     *      ReturnStatement
     *      WithStatement
     *      LabelledStatement
     *      ThrowStatement
     *      TryStatement
     *      DebuggerStatement
     *
     * BreakableStatement :
     *      IterationStatement
     *      SwitchStatement
     *
     * BlockStatement :
     *      Block
     *
     * Block :
     *      { StatementList opt }
     *
     * StatementList :
     *      StatementListItem
     *      StatementList StatementListItem
     *
     * StatementItem :
     *      Statement
     *      Declaration
     *
     * Declaration :
     *     HoistableDeclaration
     *     ClassDeclaration
     *     LexicalDeclaration
     *
     * HoistableDeclaration :
     *     FunctionDeclaration
     *     GeneratorDeclaration
     */
    private void statement() {
        statement(false, 0, false);
    }

    /**
     * @param topLevel does this statement occur at the "top level" of a script or a function?
     * @param reparseFlags reparse flags to decide whether to allow property "get" and "set" functions or ES6 methods.
     * @param singleStatement are we in a single statement context?
     */
    private void statement(final boolean topLevel, final int reparseFlags, final boolean singleStatement) {
        if ((reparseFlags & ScriptFunctionData.IS_ES6_METHOD) != 0
                && (reparseFlags & ScriptFunctionData.IS_PROPERTY_ACCESSOR) == 0) {
            // The recorded source range of a method starts at its name, so on a
            // reparse the whole "statement" is the method - there is nothing else
            // in range. It has to be recognised before the switch below, because
            // a property name may be a reserved word ("return() {}" is a method,
            // not a return statement) or a string or number literal, none of
            // which reach the identifier case.
            reparsedMethodStatement(reparseFlags);
            return;
        }
        switch (type) {
        case LBRACE:
            block();
            break;
        case VAR:
            variableStatement(type);
            break;
        case SEMICOLON:
            emptyStatement();
            break;
        case IF:
            ifStatement();
            break;
        case FOR:
            forStatement();
            break;
        case WHILE:
            whileStatement();
            break;
        case DO:
            doStatement();
            break;
        case CONTINUE:
            continueStatement();
            break;
        case BREAK:
            breakStatement();
            break;
        case RETURN:
            returnStatement();
            break;
        case WITH:
            withStatement();
            break;
        case SWITCH:
            switchStatement();
            break;
        case THROW:
            throwStatement();
            break;
        case TRY:
            tryStatement();
            break;
        case DEBUGGER:
            debuggerStatement();
            break;
        case RPAREN:
        case RBRACKET:
        case EOF:
            expect(SEMICOLON);
            break;
        case FUNCTION:
            // As per spec (ECMA section 12), function declarations as arbitrary statement
            // is not "portable". Implementation can issue a warning or disallow the same.
            if (singleStatement) {
                // ES2015 13.13.1: LabelledItem : FunctionDeclaration is a Syntax
                // Error wherever it appears. Annex B B.3.2 takes it back for a
                // label at the top of a statement list in sloppy code, and this
                // engine does not implement Annex B.
                throw error(AbstractParser.message("expected.stmt", "function declaration"), token);
            }
            functionExpression(true, topLevel);
            return;
        default:
            if (lookaheadIsAsyncFunction()) {
                if (singleStatement) {
                    throw error(AbstractParser.message("expected.stmt", "async function declaration"), token);
                }
                final long asyncToken = token;
                next();
                functionExpression(true, topLevel, true, asyncToken);
                return;
            }
            // A lexical declaration is a StatementListItem rather than a
            // Statement, so it cannot be the body of an if or a loop. "const"
            // and "class" can only have been meant as one and are reported;
            // "let" in sloppy code is also an ordinary identifier, and 13.6 has
            // it read as one there - "if (x) let \n {}" is the identifier and
            // then a block, by way of a semicolon inserted at the newline.
            if (type == CONST || type == LET && (!singleStatement || lookaheadIsArrayPattern())
                    && lookaheadIsLetDeclaration(false)) {
                if (singleStatement) {
                    throw error(AbstractParser.message("expected.stmt", type.getName() + " declaration"), token);
                }
                variableStatement(type);
                break;
            } else if (type == CLASS) {
                if (singleStatement) {
                    throw error(AbstractParser.message("expected.stmt", "class declaration"), token);
                }
                classDeclaration(false);
                break;
            }
            if (env._const_as_var && type == CONST) {
                variableStatement(TokenType.VAR);
                break;
            }

            if (type == IDENT || isNonStrictModeIdent()) {
                if (T(k + 1) == COLON) {
                    labelStatement();
                    return;
                }

                if ((reparseFlags & ScriptFunctionData.IS_PROPERTY_ACCESSOR) != 0) {
                    final String ident = (String) getValue();
                    final long propertyToken = token;
                    final int propertyLine = line;
                    if (GET_NAME.equals(ident)) {
                        next();
                        reparsingPropertyKey = true;
                        try {
                            addPropertyFunctionStatement(propertyGetterFunction(propertyToken, propertyLine));
                        } finally {
                            reparsingPropertyKey = false;
                        }
                        return;
                    } else if (SET_NAME.equals(ident)) {
                        next();
                        reparsingPropertyKey = true;
                        try {
                            addPropertyFunctionStatement(propertySetterFunction(propertyToken, propertyLine));
                        } finally {
                            reparsingPropertyKey = false;
                        }
                        return;
                    }
                }
            }

            expressionStatement();
            break;
        }
    }

    /**
     * A method being recompiled on its own, whose source text is just
     * {@code name(params) { body }} - or {@code *name(params) { body }} for a
     * generator in an object literal.
     */
    private void reparsedMethodStatement(final int reparseFlags) {
        // The token is captured first: it has to be the one the function was
        // originally created with, or the reparsed function no longer matches the
        // recorded one and its compilation data is lost.
        final long propertyToken = token;
        final int propertyLine = line;
        // A generator method's source begins with the star, which has to be
        // consumed or the reparse fails on it as an operand. The star is there for
        // an object literal method and absent for a class one, whose recorded
        // range starts at the name, so the flag is the authority and the star is
        // merely consumed if present.
        final boolean generator = (reparseFlags & ScriptFunctionData.IS_ES6_GENERATOR) != 0;
        if (type == MUL) {
            next();
        }
        // An async method's recorded range starts at the name too, so the flag
        // is the authority here as well; an "async" in front is merely consumed.
        final boolean async = (reparseFlags & ScriptFunctionData.IS_ES6_ASYNC) != 0;
        if (lookaheadIsAsyncMethod()) {
            next();
        }
        // A computed key is read the same way as a written one and can name the
        // same string, so whether it was computed has to be remembered rather
        // than inferred from what it names: 14.5 takes the constructor from a
        // key that was written, and ["constructor"] is an ordinary method.
        final boolean computed = type == LBRACKET;
        final Expression propertyKey;
        reparsingPropertyKey = true;
        try {
            propertyKey = propertyName();
        } finally {
            reparsingPropertyKey = false;
        }
        final String ident = !computed && propertyKey instanceof PropertyKey key ? key.getPropertyName() : null;

        // A reparsed method has to be given back the flags it was parsed with, or
        // it is no longer recognisably a method: super would be rejected outright,
        // and super(...) needs to know that the class had an extends clause.
        int flags = FunctionNode.ES6_IS_METHOD;
        if (CONSTRUCTOR_NAME.equals(ident)) {
            flags |= FunctionNode.ES6_IS_CLASS_CONSTRUCTOR;
            if ((reparseFlags & ScriptFunctionData.IS_ES6_SUBCLASS_CONSTRUCTOR) != 0) {
                flags |= FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR | FunctionNode.ES6_HAS_DIRECT_SUPER;
            }
        }
        addPropertyFunctionStatement(propertyMethodFunction(propertyKey, propertyToken, propertyLine, generator, async, flags, computed));
    }

    private void addPropertyFunctionStatement(final PropertyFunction propertyFunction) {
        final FunctionNode fn = propertyFunction.functionNode;
        functionDeclarations.add(new ExpressionStatement(fn.getLineNumber(), fn.getToken(), finish, fn));
    }

    /**
     * ClassDeclaration[Yield, Default] :
     *   class BindingIdentifier[?Yield] ClassTail[?Yield]
     *   [+Default] class ClassTail[?Yield]
     */
    private ClassNode classDeclaration(final boolean isDefault) {
        final int classLineNumber = line;

        if (isDefault) {
            return classExpression(false);
        }

        // ES2015 14.5.14 gives a class a scope of its own holding its name,
        // which is what the methods see: an immutable binding, separate from the
        // mutable one the declaration makes in the block around it, so that
        // reassigning the name afterwards leaves what the methods read alone.
        // The class is built inside that scope and carried out of it in a
        // temporary, because the name the outside knows it by is shadowed there.
        final long classToken = token;
        final String carrier = namespace.uniqueName(CLASS_CARRIER_PREFIX);
        final IdentNode carrierIdent = new IdentNode(classToken, finish, carrier);
        appendStatement(new VarNode(classLineNumber, Token.recast(classToken, VAR), finish,
                new IdentNode(classToken, finish, carrier), null, VarNode.IS_LET | VarNode.IS_TEMPORARY));

        final ParserContextBlockNode scope = newBlock();
        final ClassNode classExpression;
        try {
            classExpression = classExpression(true);
            final IdentNode name = classExpression.getIdent();
            appendStatement(new VarNode(classLineNumber, classExpression.getToken(), name.getFinish(),
                    name.setIsDeclaredHere(), classExpression, VarNode.IS_CONST));
            appendStatement(new ExpressionStatement(classLineNumber, classToken, finish,
                    new BinaryNode(Token.recast(classToken, ASSIGN), carrierIdent,
                            new IdentNode(name.getToken(), name.getFinish(), name.getName()))));
        } finally {
            restoreBlock(scope);
        }
        appendStatement(new BlockStatement(classLineNumber,
                new Block(classToken, finish, scope.getFlags() | Block.IS_SYNTHETIC, scope.getStatements())));

        appendStatement(new VarNode(classLineNumber, classExpression.getToken(),
                classExpression.getIdent().getFinish(), classExpression.getIdent(),
                new IdentNode(classToken, finish, carrier), VarNode.IS_LET));
        return classExpression;
    }

    /** Whether the function about to be read has a name. */
    private boolean lookaheadIsNamedFunction() {
        assert type == FUNCTION;
        final int name = T(k + 1) == MUL ? k + 2 : k + 1;
        return T(name) != LPAREN;
    }

    /** Whether the class about to be read has a name. */
    private boolean lookaheadIsNamedClass() {
        assert type == CLASS;
        return T(k + 1) == IDENT;
    }

    /**
     * A named class expression, given the scope ES2015 14.5.14 says it has.
     *
     * The scope holds an immutable binding of the class's own name and covers
     * the heritage and the method bodies, so that what a method reads is the
     * class however the name is reassigned outside. There is no way to put a
     * scope around an expression, so the class is built in a block of its own,
     * placed in front of the statement it belongs to, and a temporary is left
     * where the expression was. That is only the same thing when nothing in the
     * statement has run yet, which is what {@link #nothingEvaluatedYet} tracks:
     * a class expression reached any later is read as it was before, without a
     * scope, rather than being lifted past something that has to run first.
     */
    private Expression classInOwnScope() {
        final int classLineNumber = line;
        final long classToken = token;
        final String carrier = namespace.uniqueName(CLASS_CARRIER_PREFIX);
        appendStatement(new VarNode(classLineNumber, Token.recast(classToken, VAR), finish,
                new IdentNode(classToken, finish, carrier), null, VarNode.IS_LET | VarNode.IS_TEMPORARY));

        final ParserContextBlockNode scope = newBlock();
        try {
            final ClassNode classExpression = classExpression(false);
            final IdentNode name = classExpression.getIdent();
            appendStatement(new VarNode(classLineNumber, classExpression.getToken(), name.getFinish(),
                    name.setIsDeclaredHere(), classExpression, VarNode.IS_CONST));
            appendStatement(new ExpressionStatement(classLineNumber, classToken, finish,
                    new BinaryNode(Token.recast(classToken, ASSIGN),
                            new IdentNode(classToken, finish, carrier),
                            new IdentNode(name.getToken(), name.getFinish(), name.getName()))));
        } finally {
            restoreBlock(scope);
        }
        appendStatement(new BlockStatement(classLineNumber,
                new Block(classToken, finish, scope.getFlags() | Block.IS_SYNTHETIC, scope.getStatements())));

        return new IdentNode(classToken, finish, carrier);
    }

    /**
     * ClassExpression[Yield] :
     *   class BindingIdentifier[?Yield]opt ClassTail[?Yield]
     */
    private ClassNode classExpression(final boolean isStatement) {
        assert type == CLASS;
        final int classLineNumber = line;
        final long classToken = token;
        next();

        // ES2015 10.2.1: a class body is strict code, and that covers the class's
        // own name - "class let {}" and "class static {}" are SyntaxErrors even
        // in sloppy surroundings. The name is read here, before classTail, so
        // strict mode has to be entered here too.
        final boolean oldStrictMode = isStrictMode;
        isStrictMode = true;
        final IdentNode className;
        try {
            className = isStatement || type == IDENT ? getIdent() : null;
            if (className != null) {
                verifyIdent(className, "class name");
            }
        } finally {
            isStrictMode = oldStrictMode;
        }

        // ES2015 14.5.15 / 12.14.4: an anonymous class expression takes the name
        // of the binding it is assigned to. It names only the constructor - there
        // is no binding of that name inside the class body, which is what a
        // written class name would add - so it is carried separately.
        final String constructorName;
        if (className != null) {
            constructorName = className.getName();
        } else {
            defaultNameIsBinding = false;
            final String inferred = getDefaultFunctionName();
            constructorName = !isStatement && defaultNameIsBinding && inferred != null && isValidIdentifier(inferred)
                    ? inferred : null;
        }

        return classTail(classLineNumber, classToken, className, constructorName, isStatement);
    }

    private static final class ClassElementKey {
        private final boolean isStatic;
        private final String propertyName;

        private ClassElementKey(final boolean isStatic, final String propertyName) {
            this.isStatic = isStatic;
            this.propertyName = propertyName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (isStatic ? 1231 : 1237);
            result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof ClassElementKey) {
                final ClassElementKey other = (ClassElementKey) obj;
                return this.isStatic == other.isStatic && Objects.equals(this.propertyName, other.propertyName);
            }
            return false;
        }
    }

    /**
     * Parse ClassTail and ClassBody.
     *
     * ClassTail[Yield] :
     *   ClassHeritage[?Yield]opt { ClassBody[?Yield]opt }
     * ClassHeritage[Yield] :
     *   extends LeftHandSideExpression[?Yield]
     *
     * ClassBody[Yield] :
     *   ClassElementList[?Yield]
     * ClassElementList[Yield] :
     *   ClassElement[?Yield]
     *   ClassElementList[?Yield] ClassElement[?Yield]
     * ClassElement[Yield] :
     *   MethodDefinition[?Yield]
     *   static MethodDefinition[?Yield]
     *   ;
     */
    private ClassNode classTail(final int classLineNumber, final long classToken,
            final IdentNode className, final String constructorName, final boolean isStatement) {
        final boolean oldStrictMode = isStrictMode;
        isStrictMode = true;
        try {
            Expression classHeritage = null;
            if (type == EXTENDS) {
                next();
                classHeritage = leftHandSideExpression();
            }

            expect(LBRACE);

            PropertyNode constructor = null;
            final ArrayList<PropertyNode> classElements = new ArrayList<>();
            final Map<ClassElementKey, Integer> keyToIndexMap = new HashMap<>();
            for (;;) {
                if (type == SEMICOLON) {
                    next();
                    continue;
                }
                if (type == RBRACE) {
                    break;
                }
                final long classElementToken = token;
                boolean isStatic = false;
                if (type == STATIC) {
                    isStatic = true;
                    next();
                }

                final long methodStartToken = token;
                boolean async = lookaheadIsAsyncMethod();
                if (async) {
                    next();
                }
                boolean generator = false;
                if (type == MUL) {
                    generator = true;
                    next();
                }
                final PropertyNode classElement = methodDefinition(methodStartToken, isStatic,
                        classHeritage != null, generator, async);
                if (classElement.isComputed()) {
                    classElements.add(classElement);
                } else if (!classElement.isStatic() && CONSTRUCTOR_NAME.equals(classElement.getKeyName())) {
                    if (constructor == null) {
                        // ES2015 14.5.15: the class binding names the constructor.
                        // The function is called "constructor" as written, and the
                        // name is fixed here rather than at run time because the
                        // name property is backed by an internal accessor that
                        // cannot be redefined.
                        // an anonymous class has an anonymous constructor, whatever
                        // the element it was written as is called
                        constructor = constructorName == null
                                ? classElement.setValue(((FunctionNode)classElement.getValue()).setFlag(null, FunctionNode.IS_ANONYMOUS))
                                : classElement.setValue(((FunctionNode)classElement.getValue()).setName(null, constructorName));
                    } else {
                        throw error(AbstractParser.message("multiple.constructors"), classElementToken);
                    }
                } else {
                    // Check for duplicate method definitions and combine accessor methods.
                    // In ES6, a duplicate is never an error regardless of strict mode (in consequence of computed property names).

                    final ClassElementKey key = new ClassElementKey(classElement.isStatic(), classElement.getKeyName());
                    final Integer existing = keyToIndexMap.get(key);

                    if (existing == null) {
                        keyToIndexMap.put(key, classElements.size());
                        classElements.add(classElement);
                    } else {
                        final PropertyNode existingProperty = classElements.get(existing);

                        final Expression   value  = classElement.getValue();
                        final FunctionNode getter = classElement.getGetter();
                        final FunctionNode setter = classElement.getSetter();

                        if (value != null || existingProperty.getValue() != null) {
                            keyToIndexMap.put(key, classElements.size());
                            classElements.add(classElement);
                        } else if (getter != null) {
                            assert existingProperty.getGetter() != null || existingProperty.getSetter() != null;
                            classElements.set(existing, existingProperty.setGetter(getter));
                        } else if (setter != null) {
                            assert existingProperty.getGetter() != null || existingProperty.getSetter() != null;
                            classElements.set(existing, existingProperty.setSetter(setter));
                        }
                    }
                }
            }

            final long lastToken = token;
            expect(RBRACE);

            if (constructor == null) {
                constructor = createDefaultClassConstructor(classLineNumber, classToken, lastToken, className, constructorName, classHeritage != null);
            }

            classElements.trimToSize();
            // The class ends at its closing brace. finish has moved past it, on
            // to whatever follows, and the class's own extent is what its
            // toString answers with.
            final int classFinish = Token.descPosition(lastToken) + Token.descLength(lastToken);
            return new ClassNode(classLineNumber, classToken, classFinish, className, classHeritage, constructor, classElements, isStatement);
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    private PropertyNode createDefaultClassConstructor(final int classLineNumber, final long classToken, final long lastToken, final IdentNode className, final String constructorName, final boolean subclass) {
        final int ctorFinish = finish;
        final List<Statement> statements;
        final List<IdentNode> parameters;
        final long identToken = Token.recast(classToken, TokenType.IDENT);
        if (subclass) {
            final IdentNode superIdent = createIdentNode(identToken, ctorFinish, SUPER.getName()).setIsDirectSuper();
            final IdentNode argsIdent = createIdentNode(identToken, ctorFinish, "args").setIsRestParameter();
            final Expression spreadArgs = new UnaryNode(Token.recast(classToken, TokenType.SPREAD_ARGUMENT), argsIdent);
            final CallNode superCall = new CallNode(classLineNumber, classToken, ctorFinish, superIdent, Collections.singletonList(spreadArgs), false);
            statements = Collections.singletonList(new ExpressionStatement(classLineNumber, classToken, ctorFinish, superCall));
            parameters = Collections.singletonList(argsIdent);
        } else {
            statements = Collections.emptyList();
            parameters = Collections.emptyList();
        }

        final Block body = new Block(classToken, ctorFinish, Block.IS_BODY, statements);
        final IdentNode ctorName = constructorName != null
                ? createIdentNode(identToken, ctorFinish, constructorName)
                : createIdentNode(identToken, ctorFinish, CONSTRUCTOR_NAME);
        final ParserContextFunctionNode function = createParserContextFunctionNode(ctorName, classToken, FunctionNode.Kind.NORMAL, classLineNumber, parameters);
        function.setLastToken(lastToken);

        function.setFlag(FunctionNode.ES6_IS_METHOD);
        function.setFlag(FunctionNode.ES6_IS_CLASS_CONSTRUCTOR);
        // This constructor has no source text of its own; its token points at the
        // class, which does not reparse on its own for an anonymous class
        // expression. The flag tells the compiler to cache its AST instead.
        function.setFlag(FunctionNode.ES6_IS_DEFAULT_CONSTRUCTOR);
        if (subclass) {
            function.setFlag(FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR);
            function.setFlag(FunctionNode.ES6_HAS_DIRECT_SUPER);
        }
        if (constructorName == null) {
            function.setFlag(FunctionNode.IS_ANONYMOUS);
        }

        return new PropertyNode(classToken, ctorFinish, ctorName, createFunctionNode(
                        function,
                        classToken,
                        ctorName,
                        parameters,
                        FunctionNode.Kind.NORMAL,
                        classLineNumber,
                        body
                        ), null, null, false, false);
    }

    private PropertyNode methodDefinition(final long startToken, final boolean isStatic, final boolean subclass,
            final boolean generator, final boolean async) {
        // the method's source starts at whichever of async and * came first, so
        // that Function.prototype.toString gives back what was written; "static"
        // belongs to the class body rather than to the method, and is left out
        final long methodToken = startToken;
        final int methodLine = line;
        final boolean computed = type == LBRACKET;
        final boolean isIdent = type == IDENT;
        final Expression propertyName = propertyName();
        int flags = FunctionNode.ES6_IS_METHOD;
        if (!computed) {
            final String name = ((PropertyKey)propertyName).getPropertyName();
            if (!generator && isIdent && type != LPAREN && name.equals(GET_NAME)) {
                checkEscapedAccessor(methodToken, name);
                final PropertyFunction methodDefinition = propertyGetterFunction(methodToken, methodLine, flags);
                verifyAllowedMethodName(methodDefinition.key, isStatic, methodDefinition.computed, false, true);
                return new PropertyNode(methodToken, finish, methodDefinition.key, null, methodDefinition.functionNode, null, isStatic, methodDefinition.computed);
            } else if (!generator && isIdent && type != LPAREN && name.equals(SET_NAME)) {
                checkEscapedAccessor(methodToken, name);
                final PropertyFunction methodDefinition = propertySetterFunction(methodToken, methodLine, flags);
                verifyAllowedMethodName(methodDefinition.key, isStatic, methodDefinition.computed, false, true);
                return new PropertyNode(methodToken, finish, methodDefinition.key, null, null, methodDefinition.functionNode, isStatic, methodDefinition.computed);
            } else {
                if (!isStatic && !generator && name.equals(CONSTRUCTOR_NAME)) {
                    flags |= FunctionNode.ES6_IS_CLASS_CONSTRUCTOR;
                    if (subclass) {
                        flags |= FunctionNode.ES6_IS_SUBCLASS_CONSTRUCTOR;
                    }
                }
                verifyAllowedMethodName(propertyName, isStatic, false, generator, false, async);
            }
        }
        final PropertyFunction methodDefinition = propertyMethodFunction(propertyName, methodToken, methodLine, generator, async, flags, computed);
        return new PropertyNode(methodToken, finish, methodDefinition.key, methodDefinition.functionNode, null, null, isStatic, computed);
    }

    /**
     * ES6 14.5.1 Static Semantics: Early Errors.
     */
    private void verifyAllowedMethodName(final Expression key, final boolean isStatic, final boolean computed, final boolean generator, final boolean accessor) {
        verifyAllowedMethodName(key, isStatic, computed, generator, accessor, false);
    }

    private void verifyAllowedMethodName(final Expression key, final boolean isStatic, final boolean computed, final boolean generator, final boolean accessor, final boolean async) {
        if (!computed && !isStatic && async && ((PropertyKey) key).getPropertyName().equals(CONSTRUCTOR_NAME)) {
            // ES2017 14.6: a class constructor is not an async function
            throw error(AbstractParser.message("generator.constructor"), key.getToken());
        }
        if (!computed) {
            if (!isStatic && generator && ((PropertyKey) key).getPropertyName().equals(CONSTRUCTOR_NAME)) {
                throw error(AbstractParser.message("generator.constructor"), key.getToken());
            }
            if (!isStatic && accessor && ((PropertyKey) key).getPropertyName().equals(CONSTRUCTOR_NAME)) {
                throw error(AbstractParser.message("accessor.constructor"), key.getToken());
            }
            if (isStatic && ((PropertyKey) key).getPropertyName().equals("prototype")) {
                throw error(AbstractParser.message("static.prototype.method"), key.getToken());
            }
        }
    }

    /**
     * block :
     *      { StatementList? }
     *
     * see 12.1
     *
     * Parse a statement block.
     */
    private void block() {
        appendStatement(new BlockStatement(line, getBlock(true)));
    }

    /**
     * StatementList :
     *      Statement
     *      StatementList Statement
     *
     * See 12.1
     *
     * Parse a list of statements.
     */
    private void statementList() {
        // Accumulate statements until end of list. */
        loop:
        while (type != EOF) {
            switch (type) {
            case CASE:
            case DEFAULT:
            case RBRACE:
                break loop;
            default:
                break;
            }

            // Get next statement.
            statement();
        }
    }

    /**
     * Make sure that the identifier name used is allowed.
     *
     * @param ident         Identifier that is verified
     * @param contextString String used in error message to give context to the user
     */
    /** The words ECMAScript reserves in strict code but not in sloppy code. */
    private static boolean isFutureStrictName(final String name) {
        return switch (name) {
            case "implements", "interface", "let", "package", "private",
                 "protected", "public", "static", "yield" -> true;
            default -> false;
        };
    }

    private void verifyIdent(final IdentNode ident, final String contextString) {
        verifyStrictIdent(ident, contextString);
        checkEscapedKeyword(ident);
        // ES2017 14.6: await is a keyword inside an async function, so it names
        // nothing there - not a binding, not a label, not a parameter. ES2015
        // 11.6.2.2 reserves it throughout a module as well, whether or not
        // anything in it is async.
        if (AWAIT_NAME.equals(ident.getName()) && (inAsyncFunction() || inModule())) {
            throw error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
        }
    }

    /**
     * Make sure that in strict mode, the identifier name used is allowed.
     *
     * @param ident         Identifier that is verified
     * @param contextString String used in error message to give context to the user
     */
    private void verifyStrictIdent(final IdentNode ident, final String contextString) {
        if (isStrictMode) {
            switch (ident.getName()) {
            case "eval":
            case "arguments":
                throw error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
            default:
                break;
            }

            if (ident.isFutureStrictName() || isFutureStrictName(ident.getName())) {
                // The flag comes from the token type, which an escaped spelling
                // never gets: "l\u0065t" lexes as a plain identifier. The name
                // has to be checked as well, since it means the same thing.
                throw error(AbstractParser.message("strict.name", ident.getName(), contextString), ident.getToken());
            }
        }
    }

    /**
     * ES6 11.6.2: A code point in a ReservedWord cannot be expressed by a | UnicodeEscapeSequence.
     */
    private void checkEscapedKeyword(final IdentNode ident) {
        if (!ident.containsEscapes()) {
            return;
        }
        final TokenType tokenType = TokenLookup.lookupKeyword(ident.getName().toCharArray(), 0, ident.getName().length());
        if (tokenType != IDENT && !(tokenType.getKind() == TokenKind.FUTURESTRICT && !isStrictMode)) {
            throw error(AbstractParser.message("keyword.escaped.character"), ident.getToken());
        }
        // yield is a keyword only inside a generator, where the escape hides it
        // from the lexer and so from the check above
        if ("yield".equals(ident.getName()) && insideGenerator()) {
            throw error(AbstractParser.message("keyword.escaped.character"), ident.getToken());
        }
    }

    /** Whether the function being parsed is a generator, arrows being transparent. */
    private boolean insideGenerator() {
        final ParserContextFunctionNode function = getCurrentNonArrowFunction();
        return function != null && function.getKind() == FunctionNode.Kind.GENERATOR;
    }

    /**
     * ES2015 11.6.2: get and set name an accessor only when they are written as
     * themselves; spelled with an escape they are ordinary identifiers, and an
     * ordinary identifier cannot stand where one of those two does.
     */
    /**
     * Whether what is being read is the given contextual keyword, spelled as
     * itself.
     *
     * A keyword that is not reserved is recognised by its text, and 11.6.2
     * makes a spelling with an escape in it an ordinary identifier - which is
     * not what the production holding the keyword accepts.
     */
    private boolean isUnescaped(final String keyword) {
        return type == IDENT && keyword.equals(getValue()) && Token.descLength(token) == keyword.length();
    }

    private void checkEscapedAccessor(final long propertyToken, final String ident) {
        if (Token.descLength(propertyToken) != ident.length()) {
            throw error(AbstractParser.message("keyword.escaped.character"), propertyToken);
        }
    }

    /*
     * VariableStatement :
     *      var VariableDeclarationList ;
     *
     * VariableDeclarationList :
     *      VariableDeclaration
     *      VariableDeclarationList , VariableDeclaration
     *
     * VariableDeclaration :
     *      Identifier Initializer?
     *
     * Initializer :
     *      = AssignmentExpression
     *
     * See 12.2
     *
     * Parse a VAR statement.
     * @param isStatement True if a statement (not used in a FOR.)
     */
    private void variableStatement(final TokenType varType) {
        variableDeclarationList(varType, true, -1);
    }

    private static final class ForVariableDeclarationListResult {
        /** First missing const or binding pattern initializer. */
        Expression missingAssignment;
        /** First declaration with an initializer. */
        long declarationWithInitializerToken;
        /** Destructuring assignments. */
        Expression init;
        Expression firstBinding;
        Expression secondBinding;

        void recordMissingAssignment(final Expression binding) {
            if (missingAssignment == null) {
                missingAssignment = binding;
            }
        }

        void recordDeclarationWithInitializer(final long token) {
            if (declarationWithInitializerToken == 0L) {
                declarationWithInitializerToken = token;
            }
        }

        void addBinding(final Expression binding) {
            if (firstBinding == null) {
                firstBinding = binding;
            } else if (secondBinding == null)  {
                secondBinding = binding;
            }
            // ignore the rest
        }

        void addAssignment(final Expression assignment) {
            if (init == null) {
                init = assignment;
            } else {
                init = new BinaryNode(Token.recast(init.getToken(), COMMARIGHT), init, assignment);
            }
        }
    }

    /**
     * @param isStatement {@code true} if a VariableStatement, {@code false} if a {@code for} loop VariableDeclarationList
     */
    private ForVariableDeclarationListResult variableDeclarationList(final TokenType varType, final boolean isStatement, final int sourceOrder) {
        // VAR tested in caller.
        assert varType == VAR || varType == LET || varType == CONST;
        final int varLine = line;
        final long varToken = token;

        next();

        int varFlags = 0;
        if (varType == LET) {
            varFlags |= VarNode.IS_LET;
        } else if (varType == CONST) {
            varFlags |= VarNode.IS_CONST;
        }

        final ForVariableDeclarationListResult forResult = isStatement ? null : new ForVariableDeclarationListResult();
        while (true) {
            // Get name of var.
            if (type == YIELD && inGeneratorFunction()) {
                expect(IDENT);
            }

            final String contextString = "variable name";
            final Expression binding = bindingIdentifierOrPattern(contextString);
            final boolean isDestructuring = !(binding instanceof IdentNode);
            if (isDestructuring) {
                // in a for-in or for-of head every name the pattern binds is
                // given its value by the loop header, on every turn; see the
                // declaration made for a plain name below
                final int finalVarFlags = !isStatement && (varType == LET || varType == CONST)
                        ? varFlags | VarNode.IS_FOR_HEAD_BINDING : varFlags;
                verifyDestructuringBindingPattern(binding, identNode -> {
                    verifyIdent(identNode, contextString);
                    if (!env._parse_only) {
                        // don't bother adding a variable if we are just parsing!
                        final VarNode var = new VarNode(varLine, varToken, sourceOrder, identNode.getFinish(), identNode.setIsDeclaredHere(), null, finalVarFlags);
                        appendStatement(var);
                    }
                });
            }

            // Assume no init.
            Expression init = null;

            // Look for initializer assignment.
            if (type == ASSIGN) {
                if (!isStatement) {
                    forResult.recordDeclarationWithInitializer(varToken);
                }
                next();

                // Get initializer expression. Suppress IN if not statement.
                if (!isDestructuring) {
                    defaultNames.push(binding);
                }
                try {
                    nothingEvaluatedYet = true;
                    init = assignmentExpression(!isStatement);
                } finally {
                    nothingEvaluatedYet = false;
                    if (!isDestructuring) {
                        defaultNames.pop();
                    }
                }
            } else if (isStatement) {
                if (isDestructuring) {
                    throw error(AbstractParser.message("missing.destructuring.assignment"), token);
                } else if (varType == CONST) {
                    throw error(AbstractParser.message("missing.const.assignment", ((IdentNode)binding).getName()));
                }
                // else, if we are in a for loop, delay checking until we know the kind of loop
            }

            if (!isDestructuring) {
                assert init != null || varType != CONST || !isStatement;
                final IdentNode ident = (IdentNode)binding;
                if (!isStatement && (varType == LET || varType == CONST) && ident.getName().equals("let")) {
                    // ES2015 13.7.5.1 keeps "let" out of a for loop's lexical
                    // declaration only; "for (var let of ...)" binds a variable
                    // named let, which sloppy code may do anywhere else too
                    throw error(AbstractParser.message("let.binding.for"));
                }
                // Only set declaration flag on lexically scoped let/const as it adds runtime overhead.
                final IdentNode name = varType == LET || varType == CONST ? ident.setIsDeclaredHere() : ident;
                if (!isStatement) {
                    if (init == null && varType == CONST) {
                        forResult.recordMissingAssignment(name);
                    }
                    forResult.addBinding(new IdentNode(name));
                }
                // ES2015 13.7.5.11 has a lexical binding in a for-in or for-of
                // head named here and given a value by the loop header, on every
                // turn; until the first of those it is in its dead zone, which
                // it would not be if this declaration initialised it.
                final int declarationFlags = isStatement || init != null || !(varType == LET || varType == CONST)
                        ? varFlags : varFlags | VarNode.IS_FOR_HEAD_BINDING;
                final VarNode var = new VarNode(varLine, varToken, sourceOrder, finish, name, init, declarationFlags);
                appendStatement(var);
            } else {
                assert init != null || !isStatement;
                if (init != null) {
                    final Expression assignment = verifyAssignment(Token.recast(varToken, ASSIGN), binding, init);
                    if (isStatement) {
                        appendStatement(new ExpressionStatement(varLine, assignment.getToken(), finish, assignment, varType));
                    } else {
                        forResult.addAssignment(assignment);
                        forResult.addBinding(assignment);
                    }
                } else if (!isStatement) {
                    forResult.recordMissingAssignment(binding);
                    forResult.addBinding(binding);
                }
            }

            if (type != COMMARIGHT) {
                break;
            }
            next();
        }

        // If is a statement then handle end of line.
        if (isStatement) {
            endOfLine();
        }

        return forResult;
    }

    private boolean isBindingIdentifier() {
        return type == IDENT || isNonStrictModeIdent();
    }

    private IdentNode bindingIdentifier(final String contextString) {
        final IdentNode name = getIdent();
        verifyIdent(name, contextString);
        return name;
    }

    private Expression bindingPattern() {
        if (type == LBRACKET) {
            return arrayLiteral();
        } else if (type == LBRACE) {
            return objectLiteral();
        } else {
            throw error(AbstractParser.message("expected.binding"));
        }
    }

    /**
     * The name of the synthetic parameter that receives the value a destructuring
     * parameter pattern is matched against.
     *
     * The colon prefix is the compiler's convention for a name no script can
     * write, and - unlike the "arguments[0]" this used to produce - it is a legal
     * JVM field name, which matters as soon as the parameter is captured and
     * becomes a field of the scope object.
     *
     * @param index position of the parameter in the list
     * @return the synthetic name
     */
    private static String destructuredParameterName(final int index) {
        return ":destructuredParameter" + index;
    }

    private Expression bindingIdentifierOrPattern(final String contextString) {
        if (isBindingIdentifier()) {
            return bindingIdentifier(contextString);
        } else {
            return bindingPattern();
        }
    }

    private abstract class VerifyDestructuringPatternNodeVisitor extends NodeVisitor<LexicalContext> {
        VerifyDestructuringPatternNodeVisitor(final LexicalContext lc) {
            super(lc);
        }

        @Override
        public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
            if (literalNode.isArray()) {
                if (((LiteralNode.ArrayLiteralNode)literalNode).hasSpread() && ((LiteralNode.ArrayLiteralNode)literalNode).hasTrailingComma()) {
                    throw error("Rest element must be last", literalNode.getElementExpressions().get(literalNode.getElementExpressions().size() - 1).getToken());
                }
                boolean restElement = false;
                for (final Expression element : literalNode.getElementExpressions()) {
                    if (element != null) {
                        if (restElement) {
                            throw error("Unexpected element after rest element", element.getToken());
                        }
                        if (element.isTokenType(SPREAD_ARRAY)) {
                            restElement = true;
                            final Expression lvalue = ((UnaryNode) element).getExpression();
                            verifySpreadElement(lvalue);
                        }
                        element.accept(this);
                    }
                }
                return false;
            } else {
                return enterDefault(literalNode);
            }
        }

        protected abstract void verifySpreadElement(Expression lvalue);

        @Override
        public boolean enterObjectNode(final ObjectNode objectNode) {
            return true;
        }

        @Override
        public boolean enterPropertyNode(final PropertyNode propertyNode) {
            if (propertyNode.getValue() != null) {
                propertyNode.getValue().accept(this);
                return false;
            } else {
                return enterDefault(propertyNode);
            }
        }

        @Override
        public boolean enterBinaryNode(final BinaryNode binaryNode) {
            if (binaryNode.isTokenType(ASSIGN)) {
                binaryNode.lhs().accept(this);
                // Initializer(rhs) can be any AssignmentExpression
                return false;
            } else {
                return enterDefault(binaryNode);
            }
        }

        @Override
        public boolean enterUnaryNode(final UnaryNode unaryNode) {
            if (unaryNode.isTokenType(SPREAD_ARRAY)) {
                // rest element
                return true;
            } else {
                return enterDefault(unaryNode);
            }
        }
    }

    /**
     * Verify destructuring variable declaration binding pattern and extract bound variable declarations.
     */
    /**
     * ES2015 13.15.1: a catch parameter is a binding of the catch block, so a let,
     * a const or a class of the same name inside it declares the name twice. A
     * var does not, in the one case the specification still allows it - which is
     * why only the block scoped declarations are looked at.
     */
    /** Whether a property name is the one that reparents the object. */
    private static boolean isProtoProperty(final Expression propertyName) {
        return propertyName instanceof IdentNode name && name.isProtoPropertyName();
    }

    /**
     * ES2015 12.2.6.1: a shorthand property written with an initializer is an
     * error unless the object literal holding it is a destructuring pattern,
     * which the cover grammar leaves open until the whole expression has been
     * read. This is where it is closed.
     */
    private void verifyNoCoverInitializedName() {
        if (coverInitializedName != 0L) {
            final long where = coverInitializedName;
            coverInitializedName = 0L;
            throw error(AbstractParser.message("invalid.property.initializer"), where);
        }
    }

    private void verifyCatchParameterNames(final Expression parameter, final Block catchBody) {
        final Set<String> bound = new HashSet<>();
        if (parameter instanceof IdentNode name) {
            bound.add(name.getName());
        } else if (parameter != null) {
            verifyDestructuringBindingPattern(parameter, identNode -> bound.add(identNode.getName()));
        }
        for (final Statement statement : catchBody.getStatements()) {
            if (statement instanceof VarNode declaration && declaration.isBlockScoped()
                    && bound.contains(declaration.getName().getName())) {
                throw error(AbstractParser.message("duplicate.binding", declaration.getName().getName()),
                        declaration.getToken());
            }
        }
    }

    private void verifyDestructuringBindingPattern(final Expression pattern, final Consumer<IdentNode> identifierCallback) {
        // the object literal is a pattern after all, so a shorthand with an
        // initializer in it is a property definition rather than an error
        coverInitializedName = 0L;
        assert (pattern instanceof BinaryNode && pattern.isTokenType(ASSIGN)) ||
                pattern instanceof ObjectNode || pattern instanceof LiteralNode.ArrayLiteralNode;
        pattern.accept(new VerifyDestructuringPatternNodeVisitor(new LexicalContext()) {
            @Override
            protected void verifySpreadElement(final Expression lvalue) {
                // Only the shape is checked here. The names are reported by the
                // traversal this returns to, which descends into the rest
                // element like any other - walking it here as well reported
                // every name a rest element binds twice, and "[...{ length }]"
                // was rejected as a duplicate binding of itself.
                if (!(lvalue instanceof IdentNode) && !isDestructuringLhs(lvalue)) {
                    throw error("Expected a valid binding identifier", lvalue.getToken());
                }
            }

            @Override
            public boolean enterIdentNode(final IdentNode identNode) {
                identifierCallback.accept(identNode);
                return false;
            }

            @Override
            protected boolean enterDefault(final Node node) {
                throw error(String.format("unexpected node in BindingPattern: %s", node));
            }
        });
    }

    /**
     * EmptyStatement :
     *      ;
     *
     * See 12.3
     *
     * Parse an empty statement.
     */
    private void emptyStatement() {
        if (env._empty_statements) {
            appendStatement(new EmptyNode(line, token, Token.descPosition(token) + Token.descLength(token)));
        }

        // SEMICOLON checked in caller.
        next();
    }

    /**
     * ExpressionStatement :
     *      Expression ; // [lookahead ~({ or  function )]
     *
     * See 12.4
     *
     * Parse an expression used in a statement block.
     */
    private void expressionStatement() {
        // Lookahead checked in caller.
        final int  expressionLine  = line;
        final long expressionToken = token;

        // Get expression and add as statement.
        nothingEvaluatedYet = true;
        final Expression expression;
        try {
            expression = expression();
        } finally {
            nothingEvaluatedYet = false;
        }
        verifyNoCoverInitializedName();

        if (expression != null) {
            final ExpressionStatement expressionStatement = new ExpressionStatement(expressionLine, expressionToken, finish, expression);
            appendStatement(expressionStatement);
        } else {
            expect(null);
        }

        endOfLine();
    }

    /**
     * IfStatement :
     *      if ( Expression ) Statement else Statement
     *      if ( Expression ) Statement
     *
     * See 12.5
     *
     * Parse an IF statement.
     */
    private void ifStatement() {
        // Capture IF token.
        final int  ifLine  = line;
        final long ifToken = token;
         // IF tested in caller.
        next();

        expect(LPAREN);
        final Expression test = expression();
        expect(RPAREN);
        final Block pass = getStatement();

        Block fail = null;
        if (type == ELSE) {
            next();
            fail = getStatement();
        }

        appendStatement(new IfNode(ifLine, ifToken, fail != null ? fail.getFinish() : pass.getFinish(), test, pass, fail));
    }

    /**
     * ... IterationStatement:
     *           ...
     *           for ( Expression[NoIn]?; Expression? ; Expression? ) Statement
     *           for ( var VariableDeclarationList[NoIn]; Expression? ; Expression? ) Statement
     *           for ( LeftHandSideExpression in Expression ) Statement
     *           for ( var VariableDeclaration[NoIn] in Expression ) Statement
     *
     * See 12.6
     *
     * Parse a FOR statement.
     */
    @SuppressWarnings("fallthrough")
    private void forStatement() {
        final long forToken = token;
        final int forLine = line;
        // start position of this for statement. This is used
        // for sort order for variables declared in the initializer
        // part of this 'for' statement (if any).
        final int forStart = Token.descPosition(forToken);
        // When ES6 for-let is enabled we create a container block to capture the LET.
        final ParserContextBlockNode outer = newBlock();

        // Create FOR node, capturing FOR token.
        final ParserContextLoopNode forNode = new ParserContextLoopNode();
        lc.push(forNode);
        Block body = null;
        Expression init = null;
        JoinPredecessorExpression test = null;
        JoinPredecessorExpression modify = null;
        ForVariableDeclarationListResult varDeclList = null;

        int flags = 0;
        boolean isForOf = false;

        try {
            // FOR tested in caller.
            next();

            // Nashorn extension: for each expression.
            // iterate property values rather than property names.
            if (!env._no_syntax_extensions && type == IDENT && "each".equals(getValue())) {
                flags |= ForNode.IS_FOR_EACH;
                next();
            }

            expect(LPAREN);

            // what the head starts with, for the two spellings ES2015 13.7.5 and
            // ES2017 13.7 keep out of a for-of's left hand side
            final long headToken = token;
            final TokenType headType = type;
            final boolean headIsAsync = isUnescapedAsync();

            TokenType varType = null;
            switch (type) {
            case VAR:
                // Var declaration captured in for outer block.
                varDeclList = variableDeclarationList(varType = type, false, forStart);
                break;
            case SEMICOLON:
                break;
            default:
                if (type == LET && lookaheadIsLetDeclaration(true) || type == CONST) {
                    flags |= ForNode.PER_ITERATION_SCOPE;
                    // LET/CONST declaration captured in container block created above.
                    varDeclList = variableDeclarationList(varType = type, false, forStart);
                    break;
                }
                if (env._const_as_var && type == CONST) {
                    // Var declaration captured in for outer block.
                    varDeclList = variableDeclarationList(varType = TokenType.VAR, false, forStart);
                    break;
                }

                init = expression(unaryExpression(), COMMARIGHT.getPrecedence(), true);
                break;
            }

            switch (type) {
            case SEMICOLON:
                // for (init; test; modify)
                if (varDeclList != null) {
                    assert init == null;
                    init = varDeclList.init;
                    // late check for missing assignment, now we know it's a for (init; test; modify) loop
                    if (varDeclList.missingAssignment != null) {
                        if (varDeclList.missingAssignment instanceof IdentNode) {
                            throw error(AbstractParser.message("missing.const.assignment", ((IdentNode)varDeclList.missingAssignment).getName()));
                        } else {
                            throw error(AbstractParser.message("missing.destructuring.assignment"), varDeclList.missingAssignment.getToken());
                        }
                    }
                }

                // for each (init; test; modify) is invalid
                if ((flags & ForNode.IS_FOR_EACH) != 0) {
                    throw error(AbstractParser.message("for.each.without.in"), token);
                }

                expect(SEMICOLON);
                if (type != SEMICOLON) {
                    test = joinPredecessorExpression();
                }
                expect(SEMICOLON);
                if (type != RPAREN) {
                    modify = joinPredecessorExpression();
                }
                break;

            case IDENT:
                if ("of".equals(getValue())) {
                    // ES2015 11.6.2: "of" is a keyword here only when written as
                    // itself; spelled with an escape it is an ordinary
                    // identifier, and one cannot stand where this does
                    if (Token.descLength(token) != 2) {
                        throw error(AbstractParser.message("keyword.escaped.character"), token);
                    }
                    if (varDeclList == null) {
                        // 13.7.5: the left hand side of a for-of may not start
                        // with "let", which would otherwise be read as the
                        // declaration the same head can hold, nor with "async",
                        // which "for await" needs the room for
                        if (headType == LET) {
                            throw error(AbstractParser.message("let.binding.for"), headToken);
                        }
                        if (headIsAsync && init instanceof IdentNode ident
                                && ASYNC_NAME.equals(ident.getName())) {
                            // only the bare word: "for (async.x of ...)" is a
                            // member expression and means what it says
                            throw error(AbstractParser.message("expected.stmt", "async of"), headToken);
                        }
                    }
                    isForOf = true;
                    // fall through
                } else {
                    expect(SEMICOLON); // fail with expected message
                    break;
                }
            case IN:
                flags |= isForOf ? ForNode.IS_FOR_OF : ForNode.IS_FOR_IN;
                test = new JoinPredecessorExpression();
                if (varDeclList != null) {
                    // for (var|let|const ForBinding in|of expression)
                    if (varDeclList.secondBinding != null) {
                        // for (var i, j in obj) is invalid
                        throw error(AbstractParser.message("many.vars.in.for.in.loop", isForOf ? "of" : "in"), varDeclList.secondBinding.getToken());
                    }
                    if (varDeclList.declarationWithInitializerToken != 0 && (isStrictMode || type != TokenType.IN || varType != VAR || varDeclList.init != null)) {
                        // ES5 legacy: for (var i = AssignmentExpressionNoIn in Expression)
                        // Invalid in ES6, but allow it in non-strict mode if no ES6 features used,
                        // i.e., error if strict, for-of, let/const, or destructuring
                        throw error(AbstractParser.message("for.in.loop.initializer", isForOf ? "of" : "in"), varDeclList.declarationWithInitializerToken);
                    }
                    init = varDeclList.firstBinding;
                    flags |= ForNode.DECLARES_HEAD;
                    assert init instanceof IdentNode || isDestructuringLhs(init);
                } else {
                    // for (expr in obj)
                    assert init != null : "for..in/of init expression can not be null here";

                    // check if initial expression is a valid L-value
                    if (!checkValidLValue(init, isForOf ? "for-of iterator" : "for-in iterator")) {
                        throw error(AbstractParser.message("not.lvalue.for.in.loop", isForOf ? "of" : "in"), init.getToken());
                    }
                }

                next();

                // For-of only allows AssignmentExpression.
                modify = isForOf ? new JoinPredecessorExpression(assignmentExpression(false)) : joinPredecessorExpression();
                break;

            default:
                expect(SEMICOLON);
                break;
            }

            expect(RPAREN);

            // Set the for body.
            body = getStatement();
        } finally {
            lc.pop(forNode);

            for (final Statement var : forNode.getStatements()) {
                assert var instanceof VarNode;
                appendStatement(var);
            }
            if (body != null) {
                appendStatement(new ForNode(forLine, forToken, body.getFinish(), body, (forNode.getFlags() | flags), init, test, modify));
            }
            if (outer != null) {
                restoreBlock(outer);
                if (body != null) {
                    List<Statement> statements = new ArrayList<>();
                    for (final Statement var : outer.getStatements()) {
                        if(var instanceof VarNode && !((VarNode)var).isBlockScoped()) {
                            appendStatement(var);
                        }else {
                            statements.add(var);
                        }
                    }
                    appendStatement(new BlockStatement(forLine, new Block(
                                    outer.getToken(),
                                    body.getFinish(),
                                    statements)));
                }
            }
        }
    }

    private boolean checkValidLValue(final Expression init, final String contextString) {
        if (init instanceof IdentNode) {
            if (!checkIdentLValue((IdentNode)init)) {
                return false;
            }
            verifyIdent((IdentNode)init, contextString);
            return true;
        } else if (init instanceof AccessNode || init instanceof IndexNode) {
            return true;
        } else if (isDestructuringLhs(init)) {
            verifyDestructuringAssignmentPattern(init, contextString);
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("fallthrough")
    /**
     * Whether a "let" here is followed by a "[", across newlines and comments.
     *
     * ES2015 13.4 keeps "let [" out of an ExpressionStatement, so it is a
     * declaration or nothing - which makes it an error where a declaration
     * cannot go, rather than the identifier the bare word would otherwise be.
     */
    private boolean lookaheadIsArrayPattern() {
        assert type == LET;
        for (int i = 1;; i++) {
            final TokenType t = T(k + i);
            if (t != EOL && t != COMMENT) {
                return t == LBRACKET;
            }
        }
    }

    private boolean lookaheadIsLetDeclaration(final boolean ofContextualKeyword) {
        assert type == LET;
        for (int i = 1;; i++) {
            final TokenType t = T(k + i);
            switch (t) {
            case EOL:
            case COMMENT:
                continue;
            case IDENT:
                if (ofContextualKeyword && "of".equals(getValue(getToken(k + i)))) {
                    return false;
                }
                // fall through
            case LBRACKET:
            case LBRACE:
                return true;
            default:
                // accept future strict tokens in non-strict mode (including LET)
                return !isStrictMode && t.getKind() == TokenKind.FUTURESTRICT;
            }
        }
    }

    /**
     * ...IterationStatement :
     *           ...
     *           while ( Expression ) Statement
     *           ...
     *
     * See 12.6
     *
     * Parse while statement.
     */
    private void whileStatement() {
        // Capture WHILE token.
        final long whileToken = token;
        final int whileLine = line;
        // WHILE tested in caller.
        next();

        final ParserContextLoopNode whileNode = new ParserContextLoopNode();
        lc.push(whileNode);

        final JoinPredecessorExpression test;
        final Block body;

        try {
            expect(LPAREN);
            test = joinPredecessorExpression();
            expect(RPAREN);
            body = getStatement();
        } finally {
            lc.pop(whileNode);
        }

        appendStatement(new WhileNode(whileLine, whileToken, body.getFinish(), false, test, body));
    }

    /**
     * ...IterationStatement :
     *           ...
     *           do Statement while( Expression ) ;
     *           ...
     *
     * See 12.6
     *
     * Parse DO WHILE statement.
     */
    private void doStatement() {
        // Capture DO token.
        final long doToken = token;
        final int doLine;
        // DO tested in the caller.
        next();

        final ParserContextLoopNode doWhileNode = new ParserContextLoopNode();
        lc.push(doWhileNode);

        final Block body;
        final JoinPredecessorExpression test;

        try {
           // Get DO body.
            body = getStatement();

            expect(WHILE);
            expect(LPAREN);
            doLine = line;
            test = joinPredecessorExpression();
            expect(RPAREN);

            if (type == SEMICOLON) {
                endOfLine();
            }
        } finally {
            lc.pop(doWhileNode);
        }

        appendStatement(new WhileNode(doLine, doToken, finish, true, test, body));
    }

    /**
     * ContinueStatement :
     *      continue Identifier? ; // [no LineTerminator here]
     *
     * See 12.7
     *
     * Parse CONTINUE statement.
     */
    private void continueStatement() {
        // Capture CONTINUE token.
        final int  continueLine  = line;
        final long continueToken = token;
        // CONTINUE tested in caller.
        nextOrEOL();

        ParserContextLabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = lc.findLabel(ident.getName());

            if (labelNode == null) {
                throw error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        final String labelName = labelNode == null ? null : labelNode.getLabelName();
        final ParserContextLoopNode targetNode = lc.getContinueTo(labelName);

        if (targetNode == null) {
            throw error(AbstractParser.message("illegal.continue.stmt"), continueToken);
        }

        endOfLine();

        // Construct and add CONTINUE node.
        appendStatement(new ContinueNode(continueLine, continueToken, finish, labelName));
    }

    /**
     * BreakStatement :
     *      break Identifier? ; // [no LineTerminator here]
     *
     * See 12.8
     *
     */
    private void breakStatement() {
        // Capture BREAK token.
        final int  breakLine  = line;
        final long breakToken = token;
        // BREAK tested in caller.
        nextOrEOL();

        ParserContextLabelNode labelNode = null;

        // SEMICOLON or label.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            final IdentNode ident = getIdent();
            labelNode = lc.findLabel(ident.getName());

            if (labelNode == null) {
                throw error(AbstractParser.message("undefined.label", ident.getName()), ident.getToken());
            }

            break;
        }

        //either an explicit label - then get its node or just a "break" - get first breakable
        //targetNode is what we are breaking out from.
        final String labelName = labelNode == null ? null : labelNode.getLabelName();
        final ParserContextBreakableNode targetNode = lc.getBreakable(labelName);

        if( targetNode instanceof ParserContextBlockNode) {
            targetNode.setFlag(Block.IS_BREAKABLE);
        }

        if (targetNode == null) {
            throw error(AbstractParser.message("illegal.break.stmt"), breakToken);
        }

        endOfLine();

        // Construct and add BREAK node.
        appendStatement(new BreakNode(breakLine, breakToken, finish, labelName));
    }

    /**
     * ReturnStatement :
     *      return Expression? ; // [no LineTerminator here]
     *
     * See 12.9
     *
     * Parse RETURN statement.
     */
    private void returnStatement() {
        // check for return outside function
        if (lc.getCurrentFunction().getKind() == FunctionNode.Kind.SCRIPT || lc.getCurrentFunction().getKind() == FunctionNode.Kind.MODULE) {
            throw error(AbstractParser.message("invalid.return"));
        }

        // Capture RETURN token.
        final int  returnLine  = line;
        final long returnToken = token;
        // RETURN tested in caller.
        nextOrEOL();

        Expression expression = null;

        // SEMICOLON or expression.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
            break;

        default:
            expression = expression();
            break;
        }

        endOfLine();

        // Construct and add RETURN node.
        appendStatement(new ReturnNode(returnLine, returnToken, finish, expression));
    }

    /**
     * Parse YieldExpression.
     *
     * YieldExpression[In] :
     *   yield
     *   yield [no LineTerminator here] AssignmentExpression[?In, Yield]
     *   yield [no LineTerminator here] * AssignmentExpression[?In, Yield]
     */
    @SuppressWarnings("fallthrough")
    private Expression yieldExpression(final boolean noIn) {
        assert inGeneratorFunction();
        // Capture YIELD token.
        long yieldToken = token;
        // YIELD tested in caller.
        assert type == YIELD;
        nextOrEOL();

        final Expression expression;

        boolean yieldAsterisk = false;
        if (type == MUL) {
            yieldAsterisk = true;
            yieldToken = Token.recast(yieldToken, YIELD_STAR);
            next();
        }

        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
        case EOF:
        case COMMARIGHT:
        case RPAREN:
        case RBRACKET:
        case COLON:
            if (!yieldAsterisk) {
                // treat (yield) as (yield void 0)
                expression = newUndefinedLiteral(yieldToken, finish);
                if (type == EOL) {
                    next();
                }
                break;
            } else {
                // AssignmentExpression required, fall through
            }

        default:
            expression = assignmentExpression(noIn);
            break;
        }

        // Construct and add YIELD node.
        return new UnaryNode(yieldToken, expression);
    }

    private static UnaryNode newUndefinedLiteral(final long token, final int finish) {
        return new UnaryNode(Token.recast(token, VOID), LiteralNode.newInstance(token, finish, 0));
    }

    /**
     * WithStatement :
     *      with ( Expression ) Statement
     *
     * See 12.10
     *
     * Parse WITH statement.
     */
    private void withStatement() {
        // Capture WITH token.
        final int  withLine  = line;
        final long withToken = token;
        // WITH tested in caller.
        next();

        // ECMA 12.10.1 strict mode restrictions
        if (isStrictMode) {
            throw error(AbstractParser.message("strict.no.with"), withToken);
        }

        expect(LPAREN);
        final Expression expression = expression();
        expect(RPAREN);
        final Block body = getStatement();

        appendStatement(new WithNode(withLine, withToken, finish, expression, body));
    }

    /**
     * SwitchStatement :
     *      switch ( Expression ) CaseBlock
     *
     * CaseBlock :
     *      { CaseClauses? }
     *      { CaseClauses? DefaultClause CaseClauses }
     *
     * CaseClauses :
     *      CaseClause
     *      CaseClauses CaseClause
     *
     * CaseClause :
     *      case Expression : StatementList?
     *
     * DefaultClause :
     *      default : StatementList?
     *
     * See 12.11
     *
     * Parse SWITCH statement.
     */
    private void switchStatement() {
        final int  switchLine  = line;
        final long switchToken = token;

        // Block to capture variables declared inside the switch statement.
        final ParserContextBlockNode switchBlock = newBlock();

        // SWITCH tested in caller.
        next();

        // Create and add switch statement.
        final ParserContextSwitchNode switchNode = new ParserContextSwitchNode();
        lc.push(switchNode);

        CaseNode defaultCase = null;
        // Prepare to accumulate cases.
        final List<CaseNode> cases = new ArrayList<>();

        final Expression expression;

        try {
            expect(LPAREN);
            expression = expression();
            expect(RPAREN);

            expect(LBRACE);


            while (type != RBRACE) {
                // Prepare for next case.
                Expression caseExpression = null;
                final long caseToken = token;

                switch (type) {
                case CASE:
                    next();
                    caseExpression = expression();
                    break;

                case DEFAULT:
                    if (defaultCase != null) {
                        throw error(AbstractParser.message("duplicate.default.in.switch"));
                    }
                    next();
                    break;

                default:
                    // Force an error.
                    expect(CASE);
                    break;
                }

                expect(COLON);

                // Get CASE body.
                final Block statements = getBlock(false); // TODO: List<Statement> statements = caseStatementList();
                final CaseNode caseNode = new CaseNode(caseToken, finish, caseExpression, statements);

                if (caseExpression == null) {
                    defaultCase = caseNode;
                }

                cases.add(caseNode);
            }

            next();
        } finally {
            lc.pop(switchNode);
            restoreBlock(switchBlock);
        }

        final SwitchNode switchStatement = new SwitchNode(switchLine, switchToken, finish, expression, cases, defaultCase);
        appendStatement(new BlockStatement(switchLine, new Block(switchToken, finish, switchBlock.getFlags() | Block.IS_SYNTHETIC | Block.IS_SWITCH_BLOCK, switchStatement)));
    }

    /**
     * LabelledStatement :
     *      Identifier : Statement
     *
     * See 12.12
     *
     * Parse label statement.
     */
    /**
     * Whether the statement about to be read is an iteration statement, past
     * any further labels of its own.
     *
     * 13.8.1 lets continue name a label of an iteration statement and of
     * nothing else, and 13.13.1 passes a label set down through the labels of
     * one statement to the statement itself - so what a label is written in
     * front of is what decides, and not what turns out to be inside it.
     */
    private boolean labelsIterationStatement() {
        int i = 0;
        for (;;) {
            final TokenType at = T(k + i);
            if (at == EOL || at == COMMENT) {
                i++;
            } else if (at == IDENT && T(k + i + 1) == COLON) {
                i += 2;
            } else {
                return at == TokenType.FOR || at == WHILE || at == TokenType.DO;
            }
        }
    }

    private void labelStatement() {
        // Capture label token.
        final long labelToken = token;
        // Get label ident.
        final IdentNode ident = getIdent();
        verifyIdent(ident, "label");
        // ES2015 12.1.1: yield is a keyword inside a generator, so it does not
        // name a label there. A function written inside one may still be called
        // yield - that name belongs to the function and is read without [Yield].
        if (YIELD_NAME.equals(ident.getName()) && inGeneratorFunction()) {
            throw error(AbstractParser.message("strict.name", ident.getName(), "label"), ident.getToken());
        }

        expect(COLON);

        if (lc.findLabel(ident.getName()) != null) {
            throw error(AbstractParser.message("duplicate.label", ident.getName()), labelToken);
        }

        final ParserContextLabelNode labelNode = new ParserContextLabelNode(ident.getName(),
                labelsIterationStatement());
        final Block body;
        try {
            lc.push(labelNode);
            body = getStatement();
        } finally {
            assert lc.peek() instanceof ParserContextLabelNode;
            lc.pop(labelNode);
        }

        appendStatement(new LabelNode(line, labelToken, finish, ident.getName(), body));
    }

    /**
     * ThrowStatement :
     *      throw Expression ; // [no LineTerminator here]
     *
     * See 12.13
     *
     * Parse throw statement.
     */
    private void throwStatement() {
        // Capture THROW token.
        final int  throwLine  = line;
        final long throwToken = token;
        // THROW tested in caller.
        nextOrEOL();

        Expression expression = null;

        // SEMICOLON or expression.
        switch (type) {
        case RBRACE:
        case SEMICOLON:
        case EOL:
            break;

        default:
            expression = expression();
            break;
        }

        if (expression == null) {
            throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        endOfLine();

        appendStatement(new ThrowNode(throwLine, throwToken, finish, expression, false));
    }

    /**
     * TryStatement :
     *      try Block Catch
     *      try Block Finally
     *      try Block Catch Finally
     *
     * Catch :
     *      catch( Identifier if Expression ) Block
     *      catch( Identifier ) Block
     *
     * Finally :
     *      finally Block
     *
     * See 12.14
     *
     * Parse TRY statement.
     */
    private void tryStatement() {
        // Capture TRY token.
        final int  tryLine  = line;
        final long tryToken = token;
        // TRY tested in caller.
        next();

        // Container block needed to act as target for labeled break statements
        final int startLine = line;
        final ParserContextBlockNode outer = newBlock();
        // Create try.

        try {
            final Block       tryBody     = getBlock(true);
            final List<Block> catchBlocks = new ArrayList<>();

            while (type == CATCH) {
                final int  catchLine  = line;
                final long catchToken = token;
                next();
                expect(LPAREN);

                // ES6 catch parameter can be a BindingIdentifier or a BindingPattern
                // http://www.ecma-international.org/ecma-262/6.0/
                final String contextString = "catch argument";
                final Expression exception = bindingIdentifierOrPattern(contextString);
                final boolean isDestructuring = !(exception instanceof IdentNode);
                if (isDestructuring) {
                    // ES6 13.15.1: the bound names of a catch parameter must be
                    // unique - "catch ([a, a])" is an early error.
                    final Set<String> boundNames = new HashSet<>();
                    verifyDestructuringBindingPattern(exception, identNode -> {
                        verifyIdent(identNode, contextString);
                        if (!boundNames.add(identNode.getName())) {
                            throw error(AbstractParser.message("duplicate.binding", identNode.getName()),
                                    identNode.getToken());
                        }
                    });
                } else {
                    // ECMA 12.4.1 strict mode restrictions
                    verifyIdent((IdentNode) exception, "catch argument");
                }


                // Nashorn extension: catch clause can have optional
                // condition. So, a single try can have more than one
                // catch clause each with it's own condition.
                final Expression ifExpression;
                if (!env._no_syntax_extensions && type == IF) {
                    next();
                    // Get the exception condition.
                    ifExpression = expression();
                } else {
                    ifExpression = null;
                }

                expect(RPAREN);

                final ParserContextBlockNode catchBlock = newBlock();
                try {
                    // Get CATCH body.
                    final Block catchBody = getBlock(true);
                    verifyCatchParameterNames(exception, catchBody);
                    final CatchNode catchNode = new CatchNode(catchLine, catchToken, finish, exception, ifExpression, catchBody, false);
                    appendStatement(catchNode);
                } finally {
                    restoreBlock(catchBlock);
                    catchBlocks.add(new Block(catchBlock.getToken(), finish, catchBlock.getFlags() | Block.IS_SYNTHETIC, catchBlock.getStatements()));
                }

                // If unconditional catch then should to be the end.
                if (ifExpression == null) {
                    break;
                }
            }

            // Prepare to capture finally statement.
            Block finallyStatements = null;

            if (type == FINALLY) {
                next();
                finallyStatements = getBlock(true);
            }

            // Need at least one catch or a finally.
            if (catchBlocks.isEmpty() && finallyStatements == null) {
                throw error(AbstractParser.message("missing.catch.or.finally"), tryToken);
            }

            final TryNode tryNode = new TryNode(tryLine, tryToken, finish, tryBody, catchBlocks, finallyStatements);
            // Add try.
            assert lc.peek() == outer;
            appendStatement(tryNode);
        } finally {
            restoreBlock(outer);
        }

        appendStatement(new BlockStatement(startLine, new Block(tryToken, finish, outer.getFlags() | Block.IS_SYNTHETIC, outer.getStatements())));
    }

    /**
     * DebuggerStatement :
     *      debugger ;
     *
     * See 12.15
     *
     * Parse debugger statement.
     */
    private void  debuggerStatement() {
        // Capture DEBUGGER token.
        final int  debuggerLine  = line;
        final long debuggerToken = token;
        // DEBUGGER tested in caller.
        next();
        endOfLine();
        appendStatement(new DebuggerNode(debuggerLine, debuggerToken, finish));
    }

    /**
     * PrimaryExpression :
     *      this
     *      IdentifierReference
     *      Literal
     *      ArrayLiteral
     *      ObjectLiteral
     *      RegularExpressionLiteral
     *      TemplateLiteral
     *      CoverParenthesizedExpressionAndArrowParameterList
     *
     * CoverParenthesizedExpressionAndArrowParameterList :
     *      ( Expression )
     *      ( )
     *      ( ... BindingIdentifier )
     *      ( Expression , ... BindingIdentifier )
     *
     * Parse primary expression.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private Expression primaryExpression() {
        // Capture first token.
        final int  primaryLine  = line;
        final long primaryToken = token;

        switch (type) {
        case THIS:
            final String name = type.getName();
            next();
            markThis(lc);
            return new IdentNode(primaryToken, finish, name);
        case IDENT:
            final IdentNode ident = getIdent();
            if (ident == null) {
                break;
            }
            detectSpecialProperty(ident);
            checkEscapedKeyword(ident);
            return ident;
        case OCTAL_LEGACY:
            if (isStrictMode) {
               throw error(AbstractParser.message("strict.no.octal"), token);
            }
        case NON_OCTAL_DECIMAL:
            if (isStrictMode) {
               throw error(AbstractParser.message("strict.no.leading.zero"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case OCTAL:
        case BINARY_NUMBER:
        case FLOATING:
        case REGEX:
        case XML:
            return getLiteral();
        case FALSE:
            next();
            return LiteralNode.newInstance(primaryToken, finish, false);
        case TRUE:
            next();
            return LiteralNode.newInstance(primaryToken, finish, true);
        case NULL:
            next();
            return LiteralNode.newInstance(primaryToken, finish);
        case LBRACKET:
            return arrayLiteral();
        case LBRACE:
            return objectLiteral();
        case LPAREN:
            next();

            if (type == RPAREN) {
                // ()
                nextOrEOL();
                expectDontAdvance(ARROW);
                return new ExpressionList(primaryToken, finish, Collections.emptyList());
            } else if (type == ELLIPSIS) {
                // (...rest)
                final Expression restParam;
                final TokenType afterEllipsis = T(k + 1);
                if (afterEllipsis == LBRACKET || afterEllipsis == LBRACE) {
                    // (...[a, b]) => : the pattern is matched against what the
                    // rest gathers, but the arrow that owns the parameter does
                    // not exist yet, so the pattern is carried out of here and
                    // taken apart in verifyArrowParameter, where it does.
                    final long restToken = token;
                    next();
                    restParam = new UnaryNode(Token.recast(restToken, TokenType.SPREAD_ARRAY), bindingPattern());
                } else {
                    restParam = formalParameterList(false).get(0);
                }
                expectDontAdvance(RPAREN);
                nextOrEOL();
                expectDontAdvance(ARROW);
                return new ExpressionList(primaryToken, finish, Collections.singletonList(restParam));
            }

            final Expression expression = expression();

            if (type == COMMARIGHT) {
                // "(a, b,) => ..." - a trailing comma is only legal here because
                // the parentheses turn out to hold an arrow function's parameter
                // list (ES2017 14.2), so the arrow has to follow. Anywhere else
                // the comma operator wants an operand after it.
                next();
                expectDontAdvance(RPAREN);
                nextOrEOL();
                expectDontAdvance(ARROW);
                return new ExpressionList(primaryToken, finish, List.of(expression));
            }

            expect(RPAREN);

            // 12.15.1: what parentheses hold has the assignment target type of
            // what is inside them, and an object or array literal has one only
            // when it stands as a pattern - which, held in parentheses, it does
            // not. The node is remembered rather than flagged: what follows it
            // is the operator that has to be told, and nothing is parsed in
            // between.
            parenthesized = expression;
            return expression;
        case TEMPLATE:
        case TEMPLATE_HEAD:
            return templateLiteral();

        default:
            // In this context some operator tokens mark the start of a literal.
            if (lexer.scanLiteral(primaryToken, type, lineInfoReceiver)) {
                next();
                return getLiteral();
            }
            if (isNonStrictModeIdent()) {
                // ES2015 12.1.1: yield is a keyword inside a generator, so it is
                // not an identifier reference there. A YieldExpression is an
                // AssignmentExpression and nothing narrower, so one written
                // where only a narrower expression fits - "void yield" - has no
                // reading at all rather than being a read of a variable.
                if (type == YIELD && inGeneratorFunction()) {
                    throw error(AbstractParser.message("strict.name", YIELD_NAME, "identifier"), primaryToken);
                }
                return getIdent();
            }
            break;
        }

        return null;
    }

    /**
     * ArrayLiteral :
     *      [ Elision? ]
     *      [ ElementList ]
     *      [ ElementList , Elision? ]
     *      [ expression for (LeftHandExpression in expression) ( (if ( Expression ) )? ]
     *
     * ElementList : Elision? AssignmentExpression
     *      ElementList , Elision? AssignmentExpression
     *
     * Elision :
     *      ,
     *      Elision ,
     *
     * See 12.1.4
     * JavaScript 1.8
     *
     * Parse array literal.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private LiteralNode<Expression[]> arrayLiteral() {
        // Capture LBRACKET token.
        final long arrayToken = token;
        // LBRACKET tested in caller.
        next();

        // An element is not the whole of what a binding is being given, so
        // "var a = [function () {}]" leaves the function anonymous.
        hideDefaultName();
        try {
            return arrayLiteral(arrayToken);
        } finally {
            defaultNames.pop();
        }
    }

    private LiteralNode<Expression[]> arrayLiteral(final long arrayToken) {

        // Prepare to accumulate elements.
        final List<Expression> elements = new ArrayList<>();
        // Track elisions.
        boolean elision = true;
        boolean hasSpread = false;
        loop:
        while (true) {
            long spreadToken = 0;
            switch (type) {
            case RBRACKET:
                next();

                break loop;

            case COMMARIGHT:
                next();

                // If no prior expression
                if (elision) {
                    elements.add(null);
                }

                elision = true;

                break;

            case ELLIPSIS:
                hasSpread = true;
                spreadToken = token;
                next();
                // fall through

            default:
                if (!elision) {
                    throw error(AbstractParser.message("expected.comma", type.getNameOrType()));
                }

                // Add expression element.
                Expression expression = assignmentExpression(false);
                if (expression != null) {
                    if (spreadToken != 0) {
                        expression = new UnaryNode(Token.recast(spreadToken, SPREAD_ARRAY), expression);
                    }
                    elements.add(expression);
                } else {
                    expect(RBRACKET);
                }

                elision = false;
                break;
            }
        }

        return LiteralNode.newInstance(arrayToken, finish, elements, hasSpread, elision);
    }

    /**
     * ObjectLiteral :
     *      { }
     *      { PropertyNameAndValueList } { PropertyNameAndValueList , }
     *
     * PropertyNameAndValueList :
     *      PropertyAssignment
     *      PropertyNameAndValueList , PropertyAssignment
     *
     * See 11.1.5
     *
     * Parse an object literal.
     * @return Expression node.
     */
    private ObjectNode objectLiteral() {
        // Capture LBRACE token.
        final long objectToken = token;
        // LBRACE tested in caller.
        next();

        // Object context.
        // Prepare to accumulate elements.
        final List<PropertyNode> elements = new ArrayList<>();
        final Map<String, Integer> map = new HashMap<>();

        // Create a block for the object literal.
        boolean commaSeen = true;
        loop:
        while (true) {
            switch (type) {
                case RBRACE:
                    next();
                    break loop;

                case COMMARIGHT:
                    if (commaSeen) {
                        throw error(AbstractParser.message("expected.property.id", type.getNameOrType()));
                    }
                    next();
                    commaSeen = true;
                    break;

                default:
                    if (!commaSeen) {
                        throw error(AbstractParser.message("expected.comma", type.getNameOrType()));
                    }

                    commaSeen = false;
                    // Get and add the next property.
                    final PropertyNode property = propertyAssignment();

                    if (property.isComputed()) {
                        elements.add(property);
                        break;
                    }

                    final String key = property.getKeyName();
                    final Integer existing = map.get(key);

                    if (existing == null) {
                        map.put(key, elements.size());
                        elements.add(property);
                        break;
                    }

                    final PropertyNode existingProperty = elements.get(existing);

                    // ECMA section 11.1.5 Object Initialiser
                    // point # 4 on property assignment production
                    final Expression   value  = property.getValue();
                    final FunctionNode getter = property.getGetter();
                    final FunctionNode setter = property.getSetter();

                    final Expression   prevValue  = existingProperty.getValue();
                    final FunctionNode prevGetter = existingProperty.getGetter();
                    final FunctionNode prevSetter = existingProperty.getSetter();

                    // ES2015 dropped the ES5 duplicate-property restriction; only
                    // a repeated __proto__ in an object literal is still an error.
                    if (property.getKey() instanceof IdentNode && ((IdentNode)property.getKey()).isProtoPropertyName() &&
                                    existingProperty.getKey() instanceof IdentNode && ((IdentNode)existingProperty.getKey()).isProtoPropertyName()) {
                        throw error(AbstractParser.message("multiple.proto.key"), property.getToken());
                    }

                    if (value != null || prevValue != null) {
                        map.put(key, elements.size());
                        elements.add(property);
                    } else if (getter != null) {
                        assert prevGetter != null || prevSetter != null;
                        elements.set(existing, existingProperty.setGetter(getter));
                    } else if (setter != null) {
                        assert prevGetter != null || prevSetter != null;
                        elements.set(existing, existingProperty.setSetter(setter));
                    }
                    break;
            }
        }

        return new ObjectNode(objectToken, finish, elements);
    }

    private void checkPropertyRedefinition(final PropertyNode property, final Expression value, final FunctionNode getter, final FunctionNode setter, final Expression prevValue, final FunctionNode prevGetter, final FunctionNode prevSetter) {
        // ECMA 11.1.5 strict mode restrictions
        if (isStrictMode && value != null && prevValue != null) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        final boolean isPrevAccessor = prevGetter != null || prevSetter != null;
        final boolean isAccessor     = getter != null     || setter != null;

        // data property redefined as accessor property
        if (prevValue != null && isAccessor) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        // accessor property redefined as data
        if (isPrevAccessor && value != null) {
            throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
        }

        if (isAccessor && isPrevAccessor) {
            if (getter != null && prevGetter != null ||
                    setter != null && prevSetter != null) {
                throw error(AbstractParser.message("property.redefinition", property.getKeyName()), property.getToken());
            }
        }
    }

    /**
     * LiteralPropertyName :
     *      IdentifierName
     *      StringLiteral
     *      NumericLiteral
     *
     * @return PropertyName node
     */
    @SuppressWarnings("fallthrough")
    private PropertyKey literalPropertyName() {
        switch (type) {
        case IDENT:
            return getIdent().setIsPropertyName();
        case OCTAL_LEGACY:
            if (isStrictMode) {
                throw error(AbstractParser.message("strict.no.octal"), token);
            }
        case NON_OCTAL_DECIMAL:
            if (isStrictMode) {
                throw error(AbstractParser.message("strict.no.leading.zero"), token);
            }
        case STRING:
        case ESCSTRING:
        case DECIMAL:
        case HEXADECIMAL:
        case OCTAL:
        case BINARY_NUMBER:
        case FLOATING:
            return getLiteral();
        default:
            return getIdentifierName().setIsPropertyName();
        }
    }

    /**
     * ComputedPropertyName :
     *      AssignmentExpression
     *
     * @return PropertyName node
     */
    private Expression computedPropertyName() {
        expect(LBRACKET);
        final Expression expression = assignmentExpression(false);
        expect(RBRACKET);
        return expression;
    }

    /**
     * PropertyName :
     *      LiteralPropertyName
     *      ComputedPropertyName
     *
     * @return PropertyName node
     */
    private Expression propertyName() {
        if (type == LBRACKET) {
            return computedPropertyName();
        } else {
            return (Expression)literalPropertyName();
        }
    }

    /**
     * PropertyAssignment :
     *      PropertyName : AssignmentExpression
     *      get PropertyName ( ) { FunctionBody }
     *      set PropertyName ( PropertySetParameterList ) { FunctionBody }
     *
     * PropertySetParameterList :
     *      Identifier
     *
     * PropertyName :
     *      IdentifierName
     *      StringLiteral
     *      NumericLiteral
     *
     * See 11.1.5
     *
     * Parse an object literal property.
     * @return Property or reference node.
     */
    private PropertyNode propertyAssignment() {
        // Capture firstToken.
        final long propertyToken = token;
        final int  functionLine  = line;

        final boolean async = lookaheadIsAsyncMethod();
        if (async) {
            next();
        }

        final Expression propertyName;
        final boolean isIdentifier;

        boolean generator = false;
        if (type == MUL) {
            generator = true;
            next();
        }

        final boolean computed = type == LBRACKET;
        if (type == IDENT) {
            // Get IDENT.
            final long identToken = token;
            final String ident = (String)expectValue(IDENT);

            // "async get x() {}" is not an accessor: after async comes a
            // property name and nothing else
            if (!async && type != COLON && type != LPAREN) {

                switch (ident) {
                case GET_NAME:
                    checkEscapedAccessor(identToken, ident);
                    final PropertyFunction getter = propertyGetterFunction(propertyToken, functionLine);
                    return new PropertyNode(propertyToken, finish, getter.key, null, getter.functionNode, null, false, getter.computed);

                case SET_NAME:
                    checkEscapedAccessor(identToken, ident);
                    final PropertyFunction setter = propertySetterFunction(propertyToken, functionLine);
                    return new PropertyNode(propertyToken, finish, setter.key, null, null, setter.functionNode, false, setter.computed);
                default:
                    break;
                }
            }

            isIdentifier = true;
            IdentNode identNode = createIdentNode(propertyToken, finish, ident).setIsPropertyName();
            if (type == COLON && ident.equals("__proto__")) {
                identNode = identNode.setIsProtoPropertyName();
            }
            propertyName = identNode;
        } else {
            isIdentifier = isNonStrictModeIdent();
            final Expression written = propertyName();
            // B.3.1 reads the property name, not the way it was written, so
            // "'__proto__': value" reparents the object as the bare name does.
            // The tree API is shown what was written.
            propertyName = !computed && type == COLON && !env._parse_only && written instanceof LiteralNode<?> literal
                    && PROTO_PROPERTY_NAME.equals(literal.getString())
                    ? createIdentNode(propertyToken, finish, PROTO_PROPERTY_NAME)
                            .setIsPropertyName().setIsProtoPropertyName()
                    : written;
        }

        Expression propertyValue;

        if (generator) {
            expectDontAdvance(LPAREN);
        }

        if (async && type != LPAREN) {
            // "async" in front of a name promises a method, so a property that
            // is not one has an identifier where it should have a parameter list
            throw error(AbstractParser.message("expected", "(", type.getNameOrType()));
        }

        if (type == LPAREN) {
            propertyValue = propertyMethodFunction(propertyName, propertyToken, functionLine, generator, async,
                    FunctionNode.ES6_IS_METHOD, computed).functionNode;
        } else if (isIdentifier && (type == COMMARIGHT || type == RBRACE || type == ASSIGN)) {
            propertyValue = createIdentNode(propertyToken, finish, ((IdentNode) propertyName).getPropertyName());
            if (type == ASSIGN) {
                // ES2015 12.2.6.1: "{ a = 1 }" is only a property definition
                // inside a destructuring pattern, and the cover grammar means
                // that is not known yet. Where it is written is remembered, and
                // reported unless the literal turns out to be one.
                if (coverInitializedName == 0L) {
                    coverInitializedName = token;
                }
                final long assignToken = token;
                next();
                // ES2015 12.14.5.2: "{ p = function () {} }" names the function
                // after the name it is defaulting
                final Expression rhs;
                defaultNames.push(computed ? "" : propertyName);
                try {
                    rhs = assignmentExpression(false);
                } finally {
                    defaultNames.pop();
                }
                propertyValue = verifyAssignment(assignToken, propertyValue, rhs);
            }
        } else {
            expect(COLON);

            // a computed key is only known once it has been evaluated, so the
            // function is left nameless here and named at run time from the key.
            // "__proto__ : value" names nothing either: it is not a property
            // definition at all, and 12.2.6.9 does no NamedEvaluation
            defaultNames.push(computed || isProtoProperty(propertyName) ? "" : propertyName);
            try {
                propertyValue = assignmentExpression(false);
            } finally {
                defaultNames.pop();
            }
        }

        return new PropertyNode(propertyToken, finish, propertyName, propertyValue, null, null, false, computed);
    }

    private PropertyFunction propertyGetterFunction(final long getSetToken, final int functionLine) {
        return propertyGetterFunction(getSetToken, functionLine, FunctionNode.ES6_IS_METHOD);
    }

    private PropertyFunction propertyGetterFunction(final long getSetToken, final int functionLine, final int flags) {
        final boolean computed = type == LBRACKET;
        final Expression propertyName = propertyName();
        final String getterName = propertyName instanceof PropertyKey ? ((PropertyKey) propertyName).getPropertyName() : getDefaultValidFunctionName(functionLine, false);
        final IdentNode getNameNode = createIdentNode((propertyName).getToken(), finish, NameCodec.encode("get " + getterName));
        expect(LPAREN);
        expect(RPAREN);

        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(getNameNode, getSetToken, FunctionNode.Kind.GETTER, functionLine, Collections.emptyList());
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        Block functionBody;


        try {
            functionBody = functionBody(functionNode);
        } finally {
            lc.pop(functionNode);
        }

        final FunctionNode  function = createFunctionNode(
                functionNode,
                getSetToken,
                getNameNode,
                Collections.emptyList(),
                FunctionNode.Kind.GETTER,
                functionLine,
                functionBody);

        return new PropertyFunction(propertyName, function, computed);
    }

    private PropertyFunction propertySetterFunction(final long getSetToken, final int functionLine) {
        return propertySetterFunction(getSetToken, functionLine, FunctionNode.ES6_IS_METHOD);
    }

    private PropertyFunction propertySetterFunction(final long getSetToken, final int functionLine, final int flags) {
        final boolean computed = type == LBRACKET;
        final Expression propertyName = propertyName();
        final String setterName = propertyName instanceof PropertyKey ? ((PropertyKey) propertyName).getPropertyName() : getDefaultValidFunctionName(functionLine, false);
        final IdentNode setNameNode = createIdentNode((propertyName).getToken(), finish, NameCodec.encode("set " + setterName));

        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(setNameNode, getSetToken, FunctionNode.Kind.SETTER, functionLine, null);
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        final List<IdentNode> parameters;
        Block functionBody;
        try {
            // ES2015 14.3: a setter's parameter is a FormalParameter, so it may
            // carry a default or be a pattern, and it gets the parameter block
            // that comes with either. Nashorn read a bare identifier, so
            // "set a(_ = 1)" did not parse at all. Missing one is still
            // tolerated, which the specification does not allow.
            final ParserContextBlockNode parameterBlock = newBlock();
            try {
                expect(LPAREN);
                parameters = formalParameterList(RPAREN, false);
                functionNode.setParameters(parameters);
                expect(RPAREN);
            } finally {
                restoreBlock(parameterBlock);
            }

            functionBody = maybeWrapBodyInParameterBlock(functionBody(functionNode), parameterBlock);
        } finally {
            lc.pop(functionNode);
        }


        final FunctionNode  function = createFunctionNode(
                functionNode,
                getSetToken,
                setNameNode,
                parameters,
                FunctionNode.Kind.SETTER,
                functionLine,
                functionBody);

        return new PropertyFunction(propertyName, function, computed);
    }

    /**
     * A method's source range is taken from its function token, and a method named
     * by a string literal starts at the quote. A string token spans only the
     * contents, so recompiling such a method on its own would begin lexing inside
     * the literal and fail on the missing close quote.
     */
    private static long includeOpeningQuote(final long propertyToken) {
        final TokenType type = Token.descType(propertyToken);
        if (type != TokenType.STRING && type != TokenType.ESCSTRING) {
            return propertyToken;
        }
        return Token.toDesc(type, Token.descPosition(propertyToken) - 1, Token.descLength(propertyToken) + 2);
    }

    private PropertyFunction propertyMethodFunction(final Expression key, final long propertyToken, final int methodLine, final boolean generator, final int flags, final boolean computed) {
        return propertyMethodFunction(key, propertyToken, methodLine, generator, false, flags, computed);
    }

    private PropertyFunction propertyMethodFunction(final Expression key, final long propertyToken, final int methodLine, final boolean generator, final boolean async, final int flags, final boolean computed) {
        final long methodToken = includeOpeningQuote(propertyToken);
        // The name reaches bytecode as the name of a method, so a property name
        // holding a character the class file format reserves - a dot, most
        // commonly, from a numeric key like [1.1] - has to be escaped, the same
        // way an accessor's is below.
        final String methodName = key instanceof PropertyKey propertyKey
                ? NameCodec.encode(propertyKey.getPropertyName())
                : getDefaultValidFunctionName(methodLine, false);
        final IdentNode methodNameNode = createIdentNode(key.getToken(), finish, methodName);

        final FunctionNode.Kind functionKind = async ? FunctionNode.Kind.ASYNC
                : generator ? FunctionNode.Kind.GENERATOR : FunctionNode.Kind.NORMAL;
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(methodNameNode, methodToken, functionKind, methodLine, null);
        functionNode.setFlag(flags);
        if (computed) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        }
        lc.push(functionNode);

        try {
            final ParserContextBlockNode parameterBlock = newBlock();
            final List<IdentNode> parameters;
            try {
                expect(LPAREN);
                parameters = formalParameterList(generator);
                functionNode.setParameters(parameters);
                expect(RPAREN);
            } finally {
                restoreBlock(parameterBlock);
            }

            Block functionBody = functionBody(functionNode);

            functionBody = maybeWrapBodyInParameterBlock(functionBody, parameterBlock);

            final FunctionNode  function = createFunctionNode(
                            functionNode,
                            methodToken,
                            methodNameNode,
                            parameters,
                            functionKind,
                            methodLine,
                            functionBody);
            return new PropertyFunction(key, function, computed);
        } finally {
            lc.pop(functionNode);
        }
    }

    private static class PropertyFunction {
        final Expression key;
        final FunctionNode functionNode;
        final boolean computed;

        PropertyFunction(final Expression key, final FunctionNode function, final boolean computed) {
            this.key = key;
            this.functionNode = function;
            this.computed = computed;
        }
    }

    /**
     * LeftHandSideExpression :
     *      NewExpression
     *      CallExpression
     *
     * CallExpression :
     *      MemberExpression Arguments
     *      SuperCall
     *      CallExpression Arguments
     *      CallExpression [ Expression ]
     *      CallExpression . IdentifierName
     *
     * SuperCall :
     *      super Arguments
     *
     * See 11.2
     *
     * Parse left hand side expression.
     * @return Expression node.
     */
    private Expression leftHandSideExpression() {
        int  callLine  = line;
        long callToken = token;

        Expression lhs = memberExpression();

        if (type == LPAREN) {
            final List<Expression> arguments = optimizeList(argumentList());

            // Catch special functions.
            if (lhs instanceof IdentNode) {
                detectSpecialFunction((IdentNode)lhs);
                checkEscapedKeyword((IdentNode)lhs);
            }

            lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);
        }

        loop:
        while (true) {
            // Capture token.
            callLine  = line;
            callToken = token;

            switch (type) {
            case LPAREN: {
                // Get NEW or FUNCTION arguments.
                final List<Expression> arguments = optimizeList(argumentList());

                // Create call node.
                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            case LBRACKET: {
                next();

                // Get array index.
                final Expression rhs = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(callToken, finish, lhs, rhs);

                break;
            }
            case PERIOD: {
                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(callToken, finish, lhs, property.getName());

                break;
            }
            case TEMPLATE:
            case TEMPLATE_HEAD: {
                // tagged template literal
                final List<Expression> arguments = templateLiteralArgumentList();

                // Create call node.
                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            default:
                break loop;
            }
        }

        return lhs;
    }

    /**
     * NewExpression :
     *      MemberExpression
     *      new NewExpression
     *
     * See 11.2
     *
     * Parse new expression.
     * @return Expression node.
     */
    private Expression newExpression() {
        final long newToken = token;
        // NEW is tested in caller.
        next();

        if (type == PERIOD) {
            next();
            if (isUnescaped("target")) {
                // an arrow has no new.target of its own and reads the one of
                // the function that made it, so what says whether this is
                // legal is the nearest function that is not an arrow. On an
                // on-demand re-parse that function is not on the stack, and
                // the eager parse over the same text already ruled on it.
                final ParserContextFunctionNode enclosing = getCurrentNonArrowFunction();
                final boolean atTopLevel = (enclosing == null || enclosing.isProgram()) && reparsedFunction == null;
                if (atTopLevel && !evalNewTargetAllowed) {
                    throw error(AbstractParser.message("new.target.in.function"), token);
                }
                next();
                markNewTarget(lc);
                return new IdentNode(newToken, finish, "new.target");
            } else {
                throw error(AbstractParser.message("expected.target"), token);
            }
        }

        // Get function base.
        final int  callLine    = line;
        final Expression constructor = memberExpression();
        if (constructor == null) {
            return null;
        }
        // Get arguments.
        ArrayList<Expression> arguments;

        // Allow for missing arguments.
        if (type == LPAREN) {
            arguments = argumentList();
        } else {
            arguments = new ArrayList<>();
        }

        // Nashorn extension: This is to support the following interface implementation
        // syntax:
        //
        //     var r = new java.lang.Runnable() {
        //         run: function() { println("run"); }
        //     };
        //
        // The object literal following the "new Constructor()" expression
        // is passed as an additional (last) argument to the constructor.
        if (!env._no_syntax_extensions && type == LBRACE) {
            arguments.add(objectLiteral());
        }

        final CallNode callNode = new CallNode(callLine, constructor.getToken(), finish, constructor, optimizeList(arguments), true);

        return new UnaryNode(newToken, callNode);
    }

    /**
     * MemberExpression :
     *      PrimaryExpression
     *        FunctionExpression
     *        ClassExpression
     *        GeneratorExpression
     *      MemberExpression [ Expression ]
     *      MemberExpression . IdentifierName
     *      MemberExpression TemplateLiteral
     *      SuperProperty
     *      MetaProperty
     *      new MemberExpression Arguments
     *
     * SuperProperty :
     *      super [ Expression ]
     *      super . IdentifierName
     *
     * MetaProperty :
     *      NewTarget
     *
     * Parse member expression.
     * @return Expression node.
     */
    @SuppressWarnings("fallthrough")
    private Expression memberExpression() {
        // Prepare to build operation.
        Expression lhs;
        boolean isSuper = false;

        // Every leaf of an expression is reached through here, so this is where
        // a statement stops being one that has not run anything yet. A class
        // expression only gets a scope of its own while it does - "(" and "new"
        // evaluate nothing themselves, so they leave it standing.
        final boolean unevaluated = nothingEvaluatedYet;
        if (type != CLASS && type != LPAREN && type != TokenType.NEW) {
            nothingEvaluatedYet = false;
        }

        switch (type) {
        case NEW:
            // Get new expression.
            lhs = newExpression();
            break;

        case FUNCTION:
            // Get function expression.
            lhs = functionExpression(false, false);
            break;

        case CLASS:
            nothingEvaluatedYet = false;
            lhs = unevaluated && lookaheadIsNamedClass() ? classInOwnScope() : classExpression(false);
            break;

        case IDENT:
            if (lookaheadIsAsyncFunction()) {
                final long asyncToken = token;
                next();
                lhs = functionExpression(false, false, true, asyncToken);
                break;
            }
            // fall through to the ordinary primary expression
            lhs = primaryExpression();
            break;

        case SUPER: {
            final ParserContextFunctionNode currentFunction = getCurrentNonArrowFunction();
            // On an on-demand re-parse the enclosing method is not on the stack -
            // whatever is being compiled sits at the top - so there is nothing
            // here to ask. The eager parse read the same text with the whole
            // chain in place and would have rejected an illegal super then.
            // eval code stands where it was called from, so a method's eval
            // may read super even though the program it compiles to is not one
            final boolean inMethod = currentFunction.isMethod() || reparsedFunction != null
                    || currentFunction.isProgram() && evalSuperAllowed;
            if (inMethod) {
                final long identToken = Token.recast(token, IDENT);
                next();
                lhs = createIdentNode(identToken, finish, SUPER.getName());

                switch (type) {
                    case LBRACKET:
                    case PERIOD:
                        markSuper(lc);
                        isSuper = true;
                        break;
                    case LPAREN:
                        if (currentFunction.isSubclassConstructor() || reparsedFunction != null) {
                            // an arrow calling super() needs the constructor's
                            // home object and this, exactly as a property access does
                            markSuper(lc);
                            lhs = ((IdentNode)lhs).setIsDirectSuper();
                            break;
                        } else {
                            // fall through to throw error
                        }
                    default:
                        throw error(AbstractParser.message("invalid.super"), identToken);
                }
                break;
            }
            // super outside a method: fall through and let primaryExpression complain
        }

        default:
            // Get primary expression.
            lhs = primaryExpression();
            break;
        }

        loop:
        while (true) {
            // Capture token.
            final long callToken = token;

            switch (type) {
            case LBRACKET: {
                next();

                // Get array index.
                final Expression index = expression();

                expect(RBRACKET);

                // Create indexing node.
                lhs = new IndexNode(callToken, finish, lhs, index);

                if (isSuper) {
                    isSuper = false;
                    lhs = ((BaseNode) lhs).setIsSuper();
                }

                break;
            }
            case PERIOD: {
                if (lhs == null) {
                    throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
                }

                next();

                final IdentNode property = getIdentifierName();

                // Create property access node.
                lhs = new AccessNode(callToken, finish, lhs, property.getName());

                if (isSuper) {
                    isSuper = false;
                    lhs = ((BaseNode) lhs).setIsSuper();
                }

                break;
            }
            case TEMPLATE:
            case TEMPLATE_HEAD: {
                // tagged template literal
                final int callLine = line;
                final List<Expression> arguments = templateLiteralArgumentList();

                lhs = new CallNode(callLine, callToken, finish, lhs, arguments, false);

                break;
            }
            default:
                break loop;
            }
        }

        return lhs;
    }

    /**
     * Arguments :
     *      ( )
     *      ( ArgumentList )
     *
     * ArgumentList :
     *      AssignmentExpression
     *      ... AssignmentExpression
     *      ArgumentList , AssignmentExpression
     *      ArgumentList , ... AssignmentExpression
     *
     * See 11.2
     *
     * Parse function call arguments.
     * @return Argument list.
     */
    private ArrayList<Expression> argumentList() {
        // an argument is not the whole of what a binding is being given
        hideDefaultName();
        try {
            return argumentListBody();
        } finally {
            defaultNames.pop();
        }
    }

    private ArrayList<Expression> argumentListBody() {
        // Prepare to accumulate list of arguments.
        final ArrayList<Expression> nodeList = new ArrayList<>();
        // LPAREN tested in caller.
        next();

        // Track commas.
        boolean first = true;

        while (type != RPAREN) {
            // Comma prior to every argument except the first.
            if (!first) {
                expect(COMMARIGHT);
                // ES2017 12.3.6: an argument list may end with a comma, so that
                // adding an argument does not touch the line before it
                if (type == RPAREN) {
                    break;
                }
            } else {
                first = false;
            }

            long spreadToken = 0;
            if (type == ELLIPSIS) {
                spreadToken = token;
                next();
            }

            // Get argument expression.
            Expression expression = assignmentExpression(false);
            if (spreadToken != 0) {
                expression = new UnaryNode(Token.recast(spreadToken, TokenType.SPREAD_ARGUMENT), expression);
            }
            nodeList.add(expression);
        }

        expect(RPAREN);
        return nodeList;
    }

    private static <T> List<T> optimizeList(final ArrayList<T> list) {
        return List.copyOf(list);
    }

    /**
     * FunctionDeclaration :
     *      function Identifier ( FormalParameterList? ) { FunctionBody }
     *
     * FunctionExpression :
     *      function Identifier? ( FormalParameterList? ) { FunctionBody }
     *
     * See 13
     *
     * Parse function declaration.
     * @param isStatement True if for is a statement.
     *
     * @return Expression node.
     */
    private Expression functionExpression(final boolean isStatement, final boolean topLevel) {
        return functionExpression(isStatement, topLevel, false, 0L);
    }

    /**
     * @param async      whether "async" was written in front of it
     * @param asyncToken that "async", which is where the function's source
     *                   begins - an on-demand recompilation re-reads it from
     *                   there, and starting at "function" would lose it
     */
    private Expression functionExpression(final boolean isStatement, final boolean topLevel, final boolean async,
            final long asyncToken) {
        final long functionToken = async ? asyncToken : token;
        final int  functionLine  = line;
        // FUNCTION is tested in caller.
        assert type == FUNCTION;
        next();

        boolean generator = false;
        if (type == MUL) {
            generator = true;
            next();
        }

        IdentNode name = null;

        if (isBindingIdentifier()) {
            if (type == YIELD && ((!isStatement && generator) || (isStatement && inGeneratorFunction()))) {
                // 12.1.1 Early SyntaxError if:
                // GeneratorExpression with BindingIdentifier yield
                // HoistableDeclaration with BindingIdentifier yield in generator function body
                expect(IDENT);
            }
            name = getIdent();
            verifyIdent(name, "function name");
            // ES2017 14.6.1: an async function is not called await either
            if (async && AWAIT_NAME.equals(name.getName())) {
                throw error(AbstractParser.message("strict.name", name.getName(), "function name"), name.getToken());
            }
        } else if (isStatement) {
            // Nashorn extension: anonymous function statements.
            // Do not allow anonymous function statement if extensions
            // are now allowed. But if we are reparsing then anon function
            // statement is possible - because it was used as function
            // expression in surrounding code.
            if (env._no_syntax_extensions && reparsedFunction == null) {
                expect(IDENT);
            }
        }

        // name is null, generate anonymous name
        boolean isAnonymous = false;
        boolean hasInferredName = false;
        if (name == null) {
            final String tmpName = getDefaultValidFunctionName(functionLine, isStatement);
            name = new IdentNode(functionToken, Token.descPosition(functionToken), tmpName);
            isAnonymous = true;
            hasInferredName = defaultNameIsBinding && !isStatement;
        }

        final FunctionNode.Kind functionKind = async ? FunctionNode.Kind.ASYNC
                : generator ? FunctionNode.Kind.GENERATOR : FunctionNode.Kind.NORMAL;
        List<IdentNode> parameters = Collections.emptyList();
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(name, functionToken, functionKind, functionLine, parameters);
        lc.push(functionNode);

        final Block functionBody;
        // Hide the current default name across function boundaries. E.g. "x3 = function x1() { function() {}}"
        // If we didn't hide the current default name, then the innermost anonymous function would receive "x3".
        hideDefaultName();
        try {
            final ParserContextBlockNode parameterBlock = newBlock();
            try {
                expect(LPAREN);
                parameters = formalParameterList(generator);
                functionNode.setParameters(parameters);
                expect(RPAREN);
            } finally {
                restoreBlock(parameterBlock);
            }

            functionBody = maybeWrapBodyInParameterBlock(functionBody(functionNode), parameterBlock);
        } finally {
            defaultNames.pop();
            lc.pop(functionNode);
        }

        if (isStatement) {
            // ES2015 makes a function declaration in a block lexically scoped and
            // always legal, so --function-statement-error/-warning no longer have
            // anything to report here.
            functionNode.setFlag(FunctionNode.IS_DECLARED);
            if (isArguments(name)) {
               lc.getCurrentFunction().setFlag(FunctionNode.DEFINES_ARGUMENTS);
            }
        }

        if (isAnonymous) {
            functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
            if (hasInferredName) {
                functionNode.setFlag(FunctionNode.ES6_HAS_INFERRED_NAME);
            }
        }

        verifyParameterList(parameters, functionNode);

        final FunctionNode function = createFunctionNode(
                functionNode,
                functionToken,
                name,
                parameters,
                functionKind,
                functionLine,
                functionBody);

        if (isStatement) {
            if (isAnonymous) {
                appendStatement(new ExpressionStatement(functionLine, functionToken, finish, function));
                return function;
            }

            // mark ES6 block functions as lexically scoped. A module's own top
            // level is lexical too: 15.2.1.1 counts its function declarations
            // among its LexicallyDeclaredNames, so two of a name, or one beside
            // a var of the same name, is an early error there where in a script
            // it is not.
            final int     varFlags = topLevel && !isModuleTopLevel() ? 0 : VarNode.IS_LET;
            final VarNode varNode  = new VarNode(functionLine, functionToken, finish, name, function, varFlags);
            if (topLevel) {
                functionDeclarations.add(varNode);
            } else {
                prependStatement(varNode); // Hoist to beginning of current block
            }
        }

        return function;
    }

    private void verifyParameterList(final List<IdentNode> parameters, final ParserContextFunctionNode functionNode) {
        final IdentNode duplicateParameter = functionNode.getDuplicateParameterBinding();
        if (duplicateParameter != null) {
            if (functionNode.isStrict() || FunctionNode.isArrow(functionNode.getKind()) || !functionNode.isSimpleParameterList()) {
                throw error(AbstractParser.message("strict.param.redefinition", duplicateParameter.getName()), duplicateParameter.getToken());
            }

            final int arity = parameters.size();
            final HashSet<String> parametersSet = new HashSet<>(arity);

            for (int i = arity - 1; i >= 0; i--) {
                final IdentNode parameter = parameters.get(i);
                String parameterName = parameter.getName();

                if (parametersSet.contains(parameterName)) {
                    // redefinition of parameter name, rename in non-strict mode
                    parameterName = functionNode.uniqueName(parameterName);
                    final long parameterToken = parameter.getToken();
                    parameters.set(i, new IdentNode(parameterToken, Token.descPosition(parameterToken), functionNode.uniqueName(parameterName)));
                }
                parametersSet.add(parameterName);
            }
        }
    }

    private static Block maybeWrapBodyInParameterBlock(final Block functionBody, final ParserContextBlockNode parameterBlock) {
        assert functionBody.isFunctionBody();
        if (!parameterBlock.getStatements().isEmpty()) {
            parameterBlock.appendStatement(new BlockStatement(functionBody));
            return new Block(parameterBlock.getToken(), functionBody.getFinish(), (functionBody.getFlags() | Block.IS_PARAMETER_BLOCK) & ~Block.IS_BODY, parameterBlock.getStatements());
        }
        return functionBody;
    }

    /**
     * Adds one of a parameter list's desugared statements to the function body.
     *
     * On an on-demand compilation the parser reads the parameter list of every
     * function it passes, because the caller needs to know the shape, but reads
     * only the body of the one being compiled. A statement added to a body that
     * was skipped makes it something other than empty, which every later phase
     * takes for a body it is meant to look at.
     */
    private void appendParameterStatement(final ParserContextFunctionNode currentFunction,
            final Statement statement) {
        if (reparsedFunction != null && currentFunction.getId() > reparsedFunction.getFunctionNodeId()) {
            return;
        }
        lc.getFunctionBody(currentFunction).appendStatement(statement);
    }

    private String getDefaultValidFunctionName(final int functionLine, final boolean isStatement) {
        defaultNameIsBinding = false;
        final String defaultFunctionName = getDefaultFunctionName();
        if (isValidIdentifier(defaultFunctionName)) {
            if (isStatement) {
                // The name will be used as the LHS of a symbol assignment. We add the anonymous function
                // prefix to ensure that it can't clash with another variable.
                return ANON_FUNCTION_PREFIX.symbolName() + defaultFunctionName;
            }
            return defaultFunctionName;
        }
        return ANON_FUNCTION_PREFIX.symbolName() + functionLine;
    }

    private static boolean isValidIdentifier(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); ++i) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String getDefaultFunctionName() {
        if (!defaultNames.isEmpty()) {
            final Object nameExpr = defaultNames.peek();
            if (nameExpr instanceof PropertyKey) {
                markDefaultNameUsed();
                defaultNameIsBinding = true;
                return ((PropertyKey)nameExpr).getPropertyName();
            } else if (nameExpr instanceof AccessNode) {
                markDefaultNameUsed();
                // "o.p = function () {}" leaves the function anonymous
                defaultNameIsBinding = false;
                return ((AccessNode)nameExpr).getProperty();
            }
        }
        return null;
    }

    private void markDefaultNameUsed() {
        defaultNames.pop();
        hideDefaultName();
    }

    private void hideDefaultName() {
        // Can be any value as long as getDefaultFunctionName doesn't recognize it as something it can extract a value
        // from. Can't be null
        defaultNames.push("");
    }

    /**
     * FormalParameterList :
     *      Identifier
     *      FormalParameterList , Identifier
     *
     * See 13
     *
     * Parse function parameter list.
     * @return List of parameter nodes.
     */
    private List<IdentNode> formalParameterList(final boolean yield) {
        return formalParameterList(RPAREN, yield);
    }

    /**
     * Same as the other method of the same name - except that the end
     * token type expected is passed as argument to this method.
     *
     * FormalParameterList :
     *      Identifier
     *      FormalParameterList , Identifier
     *
     * See 13
     *
     * Parse function parameter list.
     * @return List of parameter nodes.
     */
    private List<IdentNode> formalParameterList(final TokenType endType, final boolean yield) {
        // Prepare to gather parameters.
        final ArrayList<IdentNode> parameters = new ArrayList<>();
        // Track commas.
        boolean first = true;

        while (type != endType) {
            // Comma prior to every argument except the first.
            if (!first) {
                expect(COMMARIGHT);
                // ES2017 14.1: a parameter list may end with a comma too - but
                // not after a rest parameter, which the loop below rejects by
                // requiring the list to end where the rest parameter does
                if (type == endType) {
                    break;
                }
            } else {
                first = false;
            }

            boolean restParameter = false;
            if (type == ELLIPSIS) {
                next();
                restParameter = true;
            }

            if (type == YIELD && yield) {
                expect(IDENT);
            }

            final long paramToken = token;
            final int paramLine = line;
            final String contextString = "function parameter";
            IdentNode ident;
            if (restParameter && !isBindingIdentifier()) {
                // ES2015 14.1: a rest element may be a pattern, "...[a, b]". The
                // rest is gathered into a parameter of its own, as it is for a
                // name, and the pattern is matched against that - which is also
                // what the pattern branch below does with an ordinary parameter.
                final Expression pattern = bindingPattern();
                ident = createIdentNode(paramToken, pattern.getFinish(),
                        destructuredParameterName(parameters.size()))
                        .setIsDestructuredParameter().setIsRestParameter();
                verifyDestructuringParameterBindingPattern(pattern, paramToken, paramLine, contextString);
                // a rest element ends the list, and takes no initializer
                expectDontAdvance(endType);

                final ParserContextFunctionNode restFunction = lc.getCurrentFunction();
                if (restFunction != null) {
                    if (env._parse_only) {
                        restFunction.addParameterExpression(ident, pattern);
                    } else {
                        final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), pattern, ident);
                        appendParameterStatement(restFunction, new ExpressionStatement(paramLine,
                                assignment.getToken(), assignment.getFinish(), assignment));
                    }
                    restFunction.setSimpleParameterList(false);
                }
                parameters.add(ident);
                break;
            }
            if (isBindingIdentifier() || restParameter) {
                ident = bindingIdentifier(contextString);

                if (restParameter) {
                    ident = ident.setIsRestParameter();
                    // rest parameter must be last
                    expectDontAdvance(endType);
                    final ParserContextFunctionNode restFunction = lc.getCurrentFunction();
                    if (restFunction != null) {
                        restFunction.addParameterBinding(ident);
                        // gathering the rest is what makes a parameter list not
                        // simple, as much as a default or a pattern does; the
                        // loop ends here and never reached where the others say so
                        restFunction.setSimpleParameterList(false);
                    }
                    parameters.add(ident);
                    break;
                } else if (type == ASSIGN) {
                    next();
                    ident = ident.setIsDefaultParameter();

                    if (type == YIELD && yield) {
                        // error: yield in default expression
                        expect(IDENT);
                    }

                    // default parameter. ES2015 14.1.19: an anonymous function
                    // written as one is named after the parameter
                    final Expression initializer;
                    defaultNames.push(ident);
                    try {
                        initializer = assignmentExpression(false);
                    } finally {
                        defaultNames.pop();
                    }

                    final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                    if (currentFunction != null) {
                        if (env._parse_only) {
                            // keep what is seen in source "as is" and save it as parameter expression
                            final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), ident, initializer);
                            currentFunction.addParameterExpression(ident, assignment);
                        } else {
                            // desugar to: param = (param === undefined) ? initializer : param;
                            // possible alternative: if (param === undefined) param = initializer;
                            final BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                            final TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                            final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), ident, value);
                            appendParameterStatement(currentFunction, new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                        }
                    }
                }

                final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    currentFunction.addParameterBinding(ident);
                    if (ident.isRestParameter() || ident.isDefaultParameter()) {
                        currentFunction.setSimpleParameterList(false);
                    }
                }
            } else {
                final Expression pattern = bindingPattern();
                // Introduce synthetic temporary parameter to capture the object to be destructured.
                ident = createIdentNode(paramToken, pattern.getFinish(), destructuredParameterName(parameters.size())).setIsDestructuredParameter();
                verifyDestructuringParameterBindingPattern(pattern, paramToken, paramLine, contextString);

                Expression value = ident;
                if (type == ASSIGN) {
                    next();
                    ident = ident.setIsDefaultParameter();

                    // binding pattern with initializer. desugar to: (param === undefined) ? initializer : param
                    final Expression initializer = assignmentExpression(false);

                    if (env._parse_only) {
                        // we don't want the synthetic identifier in parse only mode
                        value = initializer;
                    } else {
                        // TODO initializer must not contain yield expression if yield=true (i.e. this is generator function's parameter list)
                        final BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                        value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                    }
                }

                final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    // destructuring assignment
                    final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), pattern, value);
                    if (env._parse_only) {
                        // in parse-only mode, represent source tree "as is"
                        if (ident.isDefaultParameter()) {
                            currentFunction.addParameterExpression(ident, assignment);
                        } else {
                            currentFunction.addParameterExpression(ident, pattern);
                        }
                    } else {
                        appendParameterStatement(currentFunction, new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                    }
                }
            }
            parameters.add(ident);
        }

        parameters.trimToSize();
        return parameters;
    }

    private void verifyDestructuringParameterBindingPattern(final Expression pattern, final long paramToken, final int paramLine, final String contextString) {
        // the object literal is a pattern after all, so a shorthand with an
        // initializer in it is a property definition rather than an error
        coverInitializedName = 0L;
        verifyDestructuringBindingPattern(pattern, identNode -> {
            verifyIdent(identNode, contextString);

            final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                // declare function-scope variables for destructuring bindings
                if (!env._parse_only) {
                    appendParameterStatement(currentFunction, new VarNode(paramLine, Token.recast(paramToken, VAR), pattern.getFinish(), identNode, null));
                }
                // detect duplicate bounds names in parameter list
                currentFunction.addParameterBinding(identNode);
                currentFunction.setSimpleParameterList(false);
            }
        });
    }

    /**
     * FunctionBody :
     *      SourceElements?
     *
     * See 13
     *
     * Parse function body.
     * @return function node (body.)
     */
    private Block functionBody(final ParserContextFunctionNode functionNode) {
        // a body is statements of its own, and nothing in it answers the
        // question an expression outside it left open
        final long outerCoverInitializedName = coverInitializedName;
        coverInitializedName = 0L;
        try {
            return functionBody0(functionNode);
        } finally {
            coverInitializedName = outerCoverInitializedName;
        }
    }

    private Block functionBody0(final ParserContextFunctionNode functionNode) {
        ParserContextBlockNode body = null;
        final long bodyToken = token;
        Block functionBody;
        int bodyFinish = 0;

        final boolean parseBody;
        Serializable endParserState = null;
        try {
            // Create a new function block.
            body = newBlock();
            if (env._debug_scopes) {
                // debug scope options forces everything to be in scope
                markEval(lc);
            }
            assert functionNode != null;
            final int functionId = functionNode.getId();
            parseBody = reparsedFunction == null || functionId <= reparsedFunction.getFunctionNodeId();
            // Nashorn extension: expression closures
            if ((!env._no_syntax_extensions || FunctionNode.isArrow(functionNode.getKind())) && type != LBRACE) {
                /*
                 * Example:
                 *
                 * function square(x) x * x;
                 * print(square(3));
                 */

                // just expression as function body
                final Expression expr = assignmentExpression(false);
                final long lastToken = previousToken;
                functionNode.setLastToken(previousToken);
                assert lc.getCurrentBlock() == lc.getFunctionBody(functionNode);
                // EOL uses length field to store the line number
                final int lastFinish = Token.descPosition(lastToken) + (Token.descType(lastToken) == EOL ? 0 : Token.descLength(lastToken));
                // Only create the return node if we aren't skipping nested functions. Note that we aren't
                // skipping parsing of these extended functions; they're considered to be small anyway. Also,
                // they don't end with a single well known token, so it'd be very hard to get correctly (see
                // the note below for reasoning on skipping happening before instead of after RBRACE for
                // details).
                if (parseBody) {
                    functionNode.setFlag(FunctionNode.HAS_EXPRESSION_BODY);
                    final ReturnNode returnNode = new ReturnNode(functionNode.getLineNumber(), expr.getToken(), lastFinish, expr);
                    appendStatement(returnNode);
                }
                // bodyFinish = finish;
            } else {
                expectDontAdvance(LBRACE);
                if (parseBody || !skipFunctionBody(functionNode)) {
                    next();
                    // Gather the function elements.
                    final List<Statement> prevFunctionDecls = functionDeclarations;
                    functionDeclarations = new ArrayList<>();
                    try {
                        sourceElements(0);
                        addFunctionDeclarations(functionNode);
                    } finally {
                        functionDeclarations = prevFunctionDecls;
                    }

                    if (parseBody) {
                        // Since the lexer can read ahead and lexify some number of tokens in advance and have
                        // them buffered in the TokenStream, we need to produce a lexer state as it was just
                        // before it lexified RBRACE, and not whatever is its current (quite possibly well read
                        // ahead) state.
                        endParserState = new ParserState(Token.descPosition(token), line, linePosition);

                        // NOTE: you might wonder why do we capture/restore parser state before RBRACE instead of
                        // after RBRACE; after all, we could skip the below "expect(RBRACE);" if we captured the
                        // state after it. The reason is that RBRACE is a well-known token that we can expect and
                        // will never involve us getting into a weird lexer state, and as such is a great reparse
                        // point. Typical example of a weird lexer state after RBRACE would be:
                        //     function this_is_skipped() { ... } "use strict";
                        // because lexer is doing weird off-by-one maneuvers around string literal quotes. Instead
                        // of compensating for the possibility of a string literal (or similar) after RBRACE,
                        // we'll rather just restart parsing from this well-known, friendly token instead.
                    }
                }
                bodyFinish = finish;
                functionNode.setLastToken(token);
                expect(RBRACE);
            }
        } finally {
            restoreBlock(body);
        }

        // NOTE: we can only do alterations to the function node after restoreFunctionNode.

        if (parseBody) {
            functionNode.setEndParserState(endParserState);
        } else if (!body.getStatements().isEmpty()){
            // This is to ensure the body is empty when !parseBody but we couldn't skip parsing it (see
            // skipFunctionBody() for possible reasons). While it is not strictly necessary for correctness to
            // enforce empty bodies in nested functions that were supposed to be skipped, we do assert it as
            // an invariant in few places in the compiler pipeline, so for consistency's sake we'll throw away
            // nested bodies early if we were supposed to skip 'em.
            body.setStatements(Collections.emptyList());
        }

        if (reparsedFunction != null) {
            // We restore the flags stored in the function's ScriptFunctionData that we got when we first
            // eagerly parsed the code. We're doing it because some flags would be set based on the
            // content of the function, or even content of its nested functions, most of which are normally
            // skipped during an on-demand compilation.
            final RecompilableScriptFunctionData data = reparsedFunction.getScriptFunctionData(functionNode.getId());
            if (data != null) {
                // Data can be null if when we originally parsed the file, we removed the function declaration
                // as it was dead code.
                functionNode.setFlag(data.getFunctionFlags());
                // This compensates for missing markEval() in case the function contains an inner function
                // that contains eval(), that now we didn't discover since we skipped the inner function.
                if (functionNode.hasNestedEval()) {
                    assert functionNode.hasScopeBlock();
                    body.setFlag(Block.NEEDS_SCOPE);
                }
            }
        }
        functionBody = new Block(bodyToken, bodyFinish, body.getFlags() | Block.IS_BODY, body.getStatements());
        return functionBody;
    }

    private boolean skipFunctionBody(final ParserContextFunctionNode functionNode) {
        if (reparsedFunction == null) {
            // Not reparsing, so don't skip any function body.
            return false;
        }
        // Skip to the RBRACE of this function, and continue parsing from there.
        final RecompilableScriptFunctionData data = reparsedFunction.getScriptFunctionData(functionNode.getId());
        if (data == null) {
            // Nested function is not known to the reparsed function. This can happen if the FunctionNode was
            // in dead code that was removed. Both FoldConstants and Lower prune dead code. In that case, the
            // FunctionNode was dropped before a RecompilableScriptFunctionData could've been created for it.
            return false;
        }
        final ParserState parserState = (ParserState)data.getEndParserState();
        assert parserState != null;

        if (k < stream.last() && start < parserState.position && parserState.position <= Token.descPosition(stream.get(stream.last()))) {
            // RBRACE is already in the token stream, so fast forward to it
            for (; k < stream.last(); k++) {
                final long nextToken = stream.get(k + 1);
                if (Token.descPosition(nextToken) == parserState.position && Token.descType(nextToken) == RBRACE) {
                    token = stream.get(k);
                    type = Token.descType(token);
                    next();
                    assert type == RBRACE && start == parserState.position;
                    return true;
                }
            }
        }

        stream.reset();
        lexer = parserState.createLexer(source, lexer, stream, scripting && !env._no_syntax_extensions);
        line = parserState.line;
        linePosition = parserState.linePosition;
        // Doesn't really matter, but it's safe to treat it as if there were a semicolon before
        // the RBRACE.
        type = SEMICOLON;
        scanFirstToken();

        return true;
    }

    /**
     * Encapsulates part of the state of the parser, enough to reconstruct the state of both parser and lexer
     * for resuming parsing after skipping a function body.
     */
    private static class ParserState implements Serializable {
        private final int position;
        private final int line;
        private final int linePosition;

        private static final long serialVersionUID = -2382565130754093694L;

        ParserState(final int position, final int line, final int linePosition) {
            this.position = position;
            this.line = line;
            this.linePosition = linePosition;
        }

        Lexer createLexer(final Source source, final Lexer lexer, final TokenStream stream, final boolean scripting) {
            final Lexer newLexer = new Lexer(source, position, lexer.limit - position, stream, scripting, true);
            newLexer.restoreState(new Lexer.State(position, Integer.MAX_VALUE, line, -1, linePosition, SEMICOLON));
            return newLexer;
        }
    }

    private void printAST(final FunctionNode functionNode) {
        if (functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_AST)) {
            env.getErr().println(new ASTWriter(functionNode));
        }

        if (functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_PARSE)) {
            env.getErr().println(new PrintVisitor(functionNode, true, false));
        }
    }

    private void addFunctionDeclarations(final ParserContextFunctionNode functionNode) {
        VarNode lastDecl = null;
        for (int i = functionDeclarations.size() - 1; i >= 0; i--) {
            Statement decl = functionDeclarations.get(i);
            if (lastDecl == null && decl instanceof VarNode) {
                decl = lastDecl = ((VarNode)decl).setFlag(VarNode.IS_LAST_FUNCTION_DECLARATION);
                functionNode.setFlag(FunctionNode.HAS_FUNCTION_DECLARATIONS);
            }
            prependStatement(decl);
        }
    }

    private RuntimeNode referenceError(final Expression lhs, final Expression rhs, final boolean earlyError) {
        if (env._parse_only || earlyError) {
            // ES2015 12.4.4 and 12.15.1 make an assignment or an update whose
            // target is not a valid one an early SyntaxError. ES5.1 left the
            // choice open and Nashorn reported a ReferenceError, which is what
            // the runtime path below still does for the cases the specification
            // leaves until then.
            throw error(JSErrorType.SYNTAX_ERROR, AbstractParser.message("invalid.lvalue"), lhs.getToken());
        }
        final ArrayList<Expression> args = new ArrayList<>();
        args.add(lhs);
        args.add(Objects.requireNonNullElseGet(rhs, () -> LiteralNode.newInstance(lhs.getToken(), lhs.getFinish())));
        args.add(LiteralNode.newInstance(lhs.getToken(), lhs.getFinish(), lhs.toString()));
        return new RuntimeNode(lhs.getToken(), lhs.getFinish(), RuntimeNode.Request.REFERENCE_ERROR, args);
    }

    /**
     * PostfixExpression :
     *      LeftHandSideExpression
     *      LeftHandSideExpression ++ // [no LineTerminator here]
     *      LeftHandSideExpression -- // [no LineTerminator here]
     *
     * See 11.3
     *
     * UnaryExpression :
     *      PostfixExpression
     *      delete UnaryExpression
     *      void UnaryExpression
     *      typeof UnaryExpression
     *      ++ UnaryExpression
     *      -- UnaryExpression
     *      + UnaryExpression
     *      - UnaryExpression
     *      ~ UnaryExpression
     *      ! UnaryExpression
     *
     * See 11.4
     *
     * Parse unary expression.
     * @return Expression node.
     */
    /**
     * ES2016 12.6: the left operand of {@code **} is an UpdateExpression, so a
     * unary operator in front of it is an early error - {@code -2 ** 2} has to
     * be written {@code (-2) ** 2} or {@code -(2 ** 2)}, because the two read
     * alike and mean different things.
     *
     * A parenthesised operand never reaches here: it is a primary expression, so
     * {@code (-2) ** 2} is unaffected. {@code ++a ** 2} is unaffected too, an
     * update expression being exactly what the grammar allows.
     */
    private void rejectExponentiationOfUnary() {
        if (type == EXP) {
            throw error(AbstractParser.message("unary.before.exponentiation"), token);
        }
    }

    private Expression unaryExpression() {
        final long unaryToken = token;

        if (isAwaitExpression()) {
            return awaitExpression();
        }

        switch (type) {
        case ADD:
        case SUB: {
            final TokenType opType = type;
            next();
            final Expression expr = unaryExpression();
            rejectExponentiationOfUnary();
            return new UnaryNode(Token.recast(unaryToken, (opType == TokenType.ADD) ? TokenType.POS : TokenType.NEG), expr);
        }
        case DELETE: {
            next();
            final Expression operand = unaryExpression();
            rejectExponentiationOfUnary();
            // ES2015 12.5.3.1: strict code may not delete a binding, which is
            // what an identifier of its own names - parentheses around it make
            // no difference, and "this" and "new.target" are not bindings
            if (isStrictMode && operand instanceof IdentNode ident && !isReservedTarget(operand)) {
                throw error(AbstractParser.message("strict.cant.delete.ident", ident.getName()),
                        ident.getToken());
            }
            return new UnaryNode(unaryToken, operand);
        }
        case VOID:
        case TYPEOF:
        case BIT_NOT:
        case NOT:
            next();
            final Expression expr = unaryExpression();
            rejectExponentiationOfUnary();
            return new UnaryNode(unaryToken, expr);

        case INCPREFIX:
        case DECPREFIX:
            final TokenType opType = type;
            next();

            final Expression lhs = leftHandSideExpression();
            // ++, -- without operand..
            if (lhs == null) {
                throw error(AbstractParser.message("expected.lvalue", type.getNameOrType()));
            }

            return verifyIncDecExpression(unaryToken, opType, lhs, false);

        default:
            break;
        }

        final Expression expression = leftHandSideExpression();

        if (last != EOL) {
            switch (type) {
            case INCPREFIX:
            case DECPREFIX:
                final long opToken = token;
                final TokenType opType = type;
                // ++, -- without operand..
                if (expression == null) {
                    throw error(AbstractParser.message("expected.lvalue", type.getNameOrType()));
                }
                next();

                return verifyIncDecExpression(opToken, opType, expression, true);
            default:
                break;
            }
        }

        if (expression == null) {
            throw error(AbstractParser.message("expected.operand", type.getNameOrType()));
        }

        return expression;
    }

    /**
     * Whether an expression is one of the two that read as an identifier here
     * but can never be assigned to.
     *
     * "this" and "new.target" are parsed as identifiers, which is what lets
     * them through the check that everything else has to pass: ES2015 12.4.4
     * and 12.5.7 ask whether the operand's AssignmentTargetType is simple, and
     * for these two it is not, so "++this" is an early error rather than
     * something that fails when it runs.
     */
    private static boolean isReservedTarget(final Expression expression) {
        if (!(expression instanceof IdentNode ident)) {
            return false;
        }
        final String name = ident.getName();
        return "this".equals(name) || "new.target".equals(name);
    }

    private Expression verifyIncDecExpression(final long unaryToken, final TokenType opType, final Expression lhs, final boolean isPostfix) {
        assert lhs != null;

        if (!(lhs instanceof AccessNode ||
              lhs instanceof IndexNode ||
              lhs instanceof IdentNode)
                || isReservedTarget(lhs)) {
            return referenceError(lhs, null, env._early_lvalue_error);
        }

        if (lhs instanceof IdentNode) {
            if (!checkIdentLValue((IdentNode)lhs)) {
                return referenceError(lhs, null, false);
            }
            verifyIdent((IdentNode)lhs, "operand for " + opType.getName() + " operator");
        }

        return incDecExpression(unaryToken, opType, lhs, isPostfix);
    }

    /**
     * {@code
     * MultiplicativeExpression :
     *      UnaryExpression
     *      MultiplicativeExpression * UnaryExpression
     *      MultiplicativeExpression / UnaryExpression
     *      MultiplicativeExpression % UnaryExpression
     *
     * See 11.5
     *
     * AdditiveExpression :
     *      MultiplicativeExpression
     *      AdditiveExpression + MultiplicativeExpression
     *      AdditiveExpression - MultiplicativeExpression
     *
     * See 11.6
     *
     * ShiftExpression :
     *      AdditiveExpression
     *      ShiftExpression << AdditiveExpression
     *      ShiftExpression >> AdditiveExpression
     *      ShiftExpression >>> AdditiveExpression
     *
     * See 11.7
     *
     * RelationalExpression :
     *      ShiftExpression
     *      RelationalExpression < ShiftExpression
     *      RelationalExpression > ShiftExpression
     *      RelationalExpression <= ShiftExpression
     *      RelationalExpression >= ShiftExpression
     *      RelationalExpression instanceof ShiftExpression
     *      RelationalExpression in ShiftExpression // if !noIf
     *
     * See 11.8
     *
     *      RelationalExpression
     *      EqualityExpression == RelationalExpression
     *      EqualityExpression != RelationalExpression
     *      EqualityExpression === RelationalExpression
     *      EqualityExpression !== RelationalExpression
     *
     * See 11.9
     *
     * BitwiseANDExpression :
     *      EqualityExpression
     *      BitwiseANDExpression & EqualityExpression
     *
     * BitwiseXORExpression :
     *      BitwiseANDExpression
     *      BitwiseXORExpression ^ BitwiseANDExpression
     *
     * BitwiseORExpression :
     *      BitwiseXORExpression
     *      BitwiseORExpression | BitwiseXORExpression
     *
     * See 11.10
     *
     * LogicalANDExpression :
     *      BitwiseORExpression
     *      LogicalANDExpression && BitwiseORExpression
     *
     * LogicalORExpression :
     *      LogicalANDExpression
     *      LogicalORExpression || LogicalANDExpression
     *
     * See 11.11
     *
     * ConditionalExpression :
     *      LogicalORExpression
     *      LogicalORExpression ? AssignmentExpression : AssignmentExpression
     *
     * See 11.12
     *
     * AssignmentExpression :
     *      ConditionalExpression
     *      LeftHandSideExpression AssignmentOperator AssignmentExpression
     *
     * AssignmentOperator :
     *      = *= /= %= += -= <<= >>= >>>= &= ^= |=
     *
     * See 11.13
     *
     * Expression :
     *      AssignmentExpression
     *      Expression , AssignmentExpression
     *
     * See 11.14
     * }
     *
     * Parse expression.
     * @return Expression node.
     */
    protected Expression expression() {
        Expression assignmentExpression = assignmentExpression(false);
        while (type == COMMARIGHT) {
            if (T(k + 1) == RPAREN) {
                // "(a, b,)" - the comma ends an arrow function's parameter list
                // rather than joining two operands (ES2017 14.2), so it is left
                // for whoever opened the parenthesis to make sense of. The comma
                // operator would want something after it.
                break;
            }
            final long commaToken = token;
            next();

            boolean rhsRestParameter = false;
            if (type == ELLIPSIS) {
                // (a, b, ...rest) is not a valid expression, unless we're parsing the parameter list of an arrow function (we need to throw the right error).
                // But since the rest parameter is always last, at least we know that the expression has to end here and be followed by RPAREN and ARROW, so peek ahead.
                if (isRestParameterEndOfArrowFunctionParameterList()) {
                    final long restToken = token;
                    final TokenType afterEllipsis = T(k + 1);
                    next();
                    if (afterEllipsis == LBRACKET || afterEllipsis == LBRACE) {
                        // "(a, ...[b]) =>": as for a rest pattern written on its
                        // own, the pattern is carried out of here and taken
                        // apart in verifyArrowParameter, once the arrow it
                        // belongs to exists. The parameter list ends here.
                        final Expression pattern = new UnaryNode(Token.recast(restToken, TokenType.SPREAD_ARRAY),
                                bindingPattern());
                        assert type == RPAREN;
                        return new BinaryNode(commaToken, assignmentExpression, pattern);
                    }
                    rhsRestParameter = true;
                }
            }

            // ES2015 12.1.2 NamedEvaluation gives "x = function () {}" the name
            // x, and does so only when the function is the whole of what x is
            // assigned. An operand of the comma operator is not, which is what
            // "x = (0, function () {})" relies on to stay anonymous.
            hideDefaultName();
            Expression rhs;
            try {
                rhs = assignmentExpression(false);
            } finally {
                defaultNames.pop();
            }

            if (rhsRestParameter) {
                rhs = ((IdentNode)rhs).setIsRestParameter();
                // Our only valid move is to end Expression here and continue with ArrowFunction.
                // We've already checked that this is the parameter list of an arrow function (see above).
                // RPAREN is next, so we'll finish the binary expression and drop out of the loop.
                assert type == RPAREN;
            }

            assignmentExpression = new BinaryNode(commaToken, assignmentExpression, rhs);
        }
        return assignmentExpression;
    }

    private Expression expression(final int minPrecedence, final boolean noIn) {
        return expression(unaryExpression(), minPrecedence, noIn);
    }

    private JoinPredecessorExpression joinPredecessorExpression() {
        return new JoinPredecessorExpression(expression());
    }

    private Expression expression(final Expression exprLhs, final int minPrecedence, final boolean noIn) {
        // Get the precedence of the next operator.
        int precedence = type.getPrecedence();
        Expression lhs = exprLhs;

        // While greater precedence.
        while (type.isOperator(noIn) && precedence >= minPrecedence) {
            // Capture the operator token.
            final long op = token;

            if (type == TERNARY) {
                // Skip operator.
                next();

                // Pass expression. Middle expression of a conditional expression can be a "in"
                // expression - even in the contexts where "in" is not permitted.
                // neither branch is the whole of what is being assigned, so
                // neither takes the name a binding would otherwise lend it
                hideDefaultName();
                final Expression trueExpr;
                final Expression falseExpr;
                try {
                    // Both branches are AssignmentExpressions, which is more than
                    // an expression begun from a unary one: a yield is read as a
                    // name that way, and an arrow is not read at all.
                    trueExpr = assignmentExpression(false);

                    expect(COLON);

                    // Fail expression.
                    falseExpr = assignmentExpression(noIn);
                } finally {
                    defaultNames.pop();
                }

                // Build up node.
                lhs = new TernaryNode(op, lhs, new JoinPredecessorExpression(trueExpr), new JoinPredecessorExpression(falseExpr));
            } else {
                // Skip operator.
                next();

                 // Get the next primary expression.
                Expression rhs;
                final boolean isAssign = Token.descType(op) == ASSIGN;
                if (isAssign) {
                    defaultNames.push(lhs);
                } else {
                    // an operand of "||", "+" or the like is not the whole of
                    // what is being assigned, so it takes no name from it
                    hideDefaultName();
                }
                try {
                    rhs = unaryExpression();
                    // Get precedence of next operator.
                    int nextPrecedence = type.getPrecedence();

                    // Subtask greater precedence.
                    while (type.isOperator(noIn) &&
                           (nextPrecedence > precedence ||
                           nextPrecedence == precedence && !type.isLeftAssociative())) {
                        rhs = expression(rhs, nextPrecedence, noIn);
                        nextPrecedence = type.getPrecedence();
                    }
                } finally {
                    defaultNames.pop();
                }
                lhs = verifyAssignment(op, lhs, rhs);
            }

            precedence = type.getPrecedence();
        }

        return lhs;
    }

    /**
     * AssignmentExpression.
     *
     * AssignmentExpression[In, Yield] :
     *   ConditionalExpression[?In, ?Yield]
     *   [+Yield] YieldExpression[?In]
     *   ArrowFunction[?In, ?Yield]
     *   LeftHandSideExpression[?Yield] = AssignmentExpression[?In, ?Yield]
     *   LeftHandSideExpression[?Yield] AssignmentOperator AssignmentExpression[?In, ?Yield]
     *
     * @param noIn {@code true} if IN operator should be ignored.
     * @return the assignment expression
     */
    protected Expression assignmentExpression(final boolean noIn) {
        // This method is protected so that subclass can get details
        // at assignment expression start point!

        if (type == YIELD && inGeneratorFunction()) {
            return yieldExpression(noIn);
        }

        if (lookaheadIsAsyncArrow()) {
            final long asyncToken = token;
            final int asyncLine = line;
            next();
            // an async arrow's parameters are read with await already a keyword,
            // which is why they are parsed before there is an arrow to be inside
            final boolean wasAsyncParameters = inAsyncParameters;
            inAsyncParameters = true;
            final Expression paramListExpr;
            try {
                paramListExpr = conditionalExpression(noIn);
            } finally {
                inAsyncParameters = wasAsyncParameters;
            }
            return arrowFunction(asyncToken, asyncLine,
                    paramListExpr instanceof ExpressionList list
                            ? (list.getExpressions().isEmpty() ? null : list.getExpressions().get(0))
                            : paramListExpr,
                    true);
        }

        final long startToken = token;
        final int startLine = line;
        final Expression exprLhs = conditionalExpression(noIn);

        if (type == ARROW) {
            if (checkNoLineTerminator()) {
                final Expression paramListExpr;
                if (exprLhs instanceof ExpressionList) {
                    paramListExpr = (((ExpressionList)exprLhs).getExpressions().isEmpty() ? null : ((ExpressionList)exprLhs).getExpressions().get(0));
                } else {
                    paramListExpr = exprLhs;
                }
                return arrowFunction(startToken, startLine, paramListExpr);
            }
        }
        assert !(exprLhs instanceof ExpressionList);

        if (isAssignmentOperator(type)) {
            final boolean isAssign = type == ASSIGN;
            if (isAssign) {
                defaultNames.push(exprLhs);
            }
            try {
                final long assignToken = token;
                next();
                final Expression exprRhs = assignmentExpression(noIn);
                return verifyAssignment(assignToken, exprLhs, exprRhs);
            } finally {
                if (isAssign) {
                    defaultNames.pop();
                }
            }
        } else {
            return exprLhs;
        }
    }

    /**
     * Is type one of {@code = *= /= %= += -= <<= >>= >>>= &= ^= |=}?
     */
    private static boolean isAssignmentOperator(final TokenType type) {
        switch (type) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_EXP:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
            return true;
        }
        return false;
    }

    /**
     * ConditionalExpression.
     */
    private Expression conditionalExpression(final boolean noIn) {
        return expression(TERNARY.getPrecedence(), noIn);
    }

    /**
     * ArrowFunction.
     *
     * @param startToken start token of the ArrowParameters expression
     * @param functionLine start line of the arrow function
     * @param paramListExpr ArrowParameters expression or {@code null} for {@code ()} (empty list)
     */
    private Expression arrowFunction(final long startToken, final int functionLine, final Expression paramListExpr) {
        return arrowFunction(startToken, functionLine, paramListExpr, false);
    }

    private Expression arrowFunction(final long startToken, final int functionLine, final Expression paramListExpr,
            final boolean async) {
        // caller needs to check that there's no LineTerminator between parameter list and arrow
        assert type != ARROW || checkNoLineTerminator();
        expect(ARROW);

        final long functionToken = Token.recast(startToken, ARROW);
        // ES2015 12.14.4 names an arrow after the binding it is assigned to; with
        // nothing to take a name from it keeps the internal one, which is not a
        // valid identifier and so never reported.
        final String inferred = getDefaultValidFunctionName(functionLine, false);
        final boolean hasInferredName = defaultNameIsBinding;
        final IdentNode name = new IdentNode(functionToken, Token.descPosition(functionToken),
                hasInferredName ? inferred : NameCodec.encode("=>:") + functionLine);
        final ParserContextFunctionNode functionNode = createParserContextFunctionNode(name, functionToken,
                async ? FunctionNode.Kind.ASYNC_ARROW : FunctionNode.Kind.ARROW, functionLine, null);
        functionNode.setFlag(FunctionNode.IS_ANONYMOUS);
        if (hasInferredName) {
            functionNode.setFlag(FunctionNode.ES6_HAS_INFERRED_NAME);
        }

        lc.push(functionNode);
        try {
            final ParserContextBlockNode parameterBlock = newBlock();
            final List<IdentNode> parameters;
            try {
                parameters = convertArrowFunctionParameterList(paramListExpr, functionLine);
                functionNode.setParameters(parameters);

                if (!functionNode.isSimpleParameterList()) {
                    markEvalInArrowParameterList(parameterBlock);
                }
            } finally {
                restoreBlock(parameterBlock);
            }
            Block functionBody = functionBody(functionNode);

            functionBody = maybeWrapBodyInParameterBlock(functionBody, parameterBlock);

            verifyParameterList(parameters, functionNode);

            return createFunctionNode(
                            functionNode,
                            functionToken,
                            name,
                            parameters,
                            async ? FunctionNode.Kind.ASYNC_ARROW : FunctionNode.Kind.ARROW,
                            functionLine,
                            functionBody);
        } finally {
            lc.pop(functionNode);
        }
    }

    private void markEvalInArrowParameterList(final ParserContextBlockNode parameterBlock) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        final ParserContextFunctionNode current = iter.next();
        final ParserContextFunctionNode parent = iter.next();

        if (parent.getFlag(FunctionNode.HAS_EVAL) != 0) {
            // we might have flagged has-eval in the parent function during parsing the parameter list,
            // if the parameter list contains eval; must tag arrow function as has-eval.
            for (final Statement st : parameterBlock.getStatements()) {
                st.accept(new NodeVisitor<>(new LexicalContext()) {
                    @Override
                    public boolean enterCallNode(final CallNode callNode) {
                        if (callNode.getFunction() instanceof IdentNode && ((IdentNode) callNode.getFunction()).getName().equals("eval")) {
                            current.setFlag(FunctionNode.HAS_EVAL);
                        }
                        return true;
                    }
                });
            }
            // TODO: function containing the arrow function should not be flagged has-eval
        }
    }

    private List<IdentNode> convertArrowFunctionParameterList(final Expression paramListExpr, final int functionLine) {
        final List<IdentNode> parameters;
        if (paramListExpr == null) {
            // empty parameter list, i.e. () =>
            parameters = Collections.emptyList();
        } else if (paramListExpr instanceof IdentNode || paramListExpr.isTokenType(ASSIGN)
                || isDestructuringLhs(paramListExpr) || isRestPattern(paramListExpr)) {
            parameters = Collections.singletonList(verifyArrowParameter(paramListExpr, 0, functionLine));
        } else if (paramListExpr instanceof BinaryNode && Token.descType(paramListExpr.getToken()) == COMMARIGHT) {
            // the comma expression leans left, so it is taken apart from the
            // last parameter back; the parameters are then read in the order
            // they were written, which is the order their defaults run in and
            // the order in which each becomes visible to the next
            final List<Expression> written = new ArrayList<>();
            Expression car = paramListExpr;
            do {
                written.add(0, ((BinaryNode) car).rhs());
                car = ((BinaryNode) car).lhs();
            } while (car instanceof BinaryNode && Token.descType(car.getToken()) == COMMARIGHT);
            written.add(0, car);

            parameters = new ArrayList<>(written.size());
            for (final Expression param : written) {
                parameters.add(verifyArrowParameter(param, parameters.size(), functionLine));
            }
        } else {
            throw error(AbstractParser.message("expected.arrow.parameter"), paramListExpr.getToken());
        }
        return parameters;
    }

    /** "...[a, b]", carried out of the parenthesized form primaryExpression saw. */
    private boolean isRestPattern(final Expression expression) {
        return expression instanceof UnaryNode spread && spread.isTokenType(SPREAD_ARRAY)
                && isDestructuringLhs(spread.getExpression());
    }

    private IdentNode verifyArrowParameter(final Expression param, final int index, final int paramLine) {
        final String contextString = "function parameter";
        if (param instanceof IdentNode) {
            final IdentNode ident = (IdentNode)param;
            verifyIdent(ident, contextString);
            final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                currentFunction.addParameterBinding(ident);
            }
            return ident;
        }

        if (param.isTokenType(ASSIGN)) {
            final Expression lhs = ((BinaryNode) param).lhs();
            final long paramToken = lhs.getToken();
            final Expression initializer = ((BinaryNode) param).rhs();
            if (lhs instanceof IdentNode) {
                // default parameter. The mark is what SetFunctionLength counts
                // up to, so an arrow's parameters have to carry it as a
                // function's do - the cover grammar this comes from does not
                // put it there.
                final IdentNode ident = ((IdentNode) lhs).setIsDefaultParameter();

                final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    if (env._parse_only) {
                        currentFunction.addParameterExpression(ident, param);
                    } else {
                        final BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                        final TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                        final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), ident, value);
                        appendParameterStatement(currentFunction, new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                    }

                    currentFunction.addParameterBinding(ident);
                    currentFunction.setSimpleParameterList(false);
                }
                return ident;
            } else if (isDestructuringLhs(lhs)) {
                // binding pattern with initializer
                // Introduce synthetic temporary parameter to capture the object to be destructured.
                final IdentNode ident = createIdentNode(paramToken, param.getFinish(), destructuredParameterName(index)).setIsDestructuredParameter().setIsDefaultParameter();
                verifyDestructuringParameterBindingPattern(param, paramToken, paramLine, contextString);

                final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
                if (currentFunction != null) {
                    if (env._parse_only) {
                        currentFunction.addParameterExpression(ident, param);
                    } else {
                        final BinaryNode test = new BinaryNode(Token.recast(paramToken, EQ_STRICT), ident, newUndefinedLiteral(paramToken, finish));
                        final TernaryNode value = new TernaryNode(Token.recast(paramToken, TERNARY), test, new JoinPredecessorExpression(initializer), new JoinPredecessorExpression(ident));
                        // the target is the pattern, not the whole "pattern = initializer"
                        // the arrow was written with; assigning to that is not a
                        // destructuring assignment at all and nothing downstream knows it
                        final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), lhs, value);
                        appendParameterStatement(currentFunction, new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                    }
                }
                return ident;
            }
        } else if (isRestPattern(param)) {
            // "...[a, b]" - the rest is gathered into a parameter of its own and
            // the pattern is matched against it, as it is for a function
            final Expression pattern = ((UnaryNode)param).getExpression();
            final long paramToken = pattern.getToken();
            final IdentNode ident = createIdentNode(paramToken, pattern.getFinish(),
                    destructuredParameterName(index)).setIsDestructuredParameter().setIsRestParameter();
            verifyDestructuringParameterBindingPattern(pattern, paramToken, paramLine, contextString);

            final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                if (env._parse_only) {
                    currentFunction.addParameterExpression(ident, pattern);
                } else {
                    final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), pattern, ident);
                    appendParameterStatement(currentFunction, new ExpressionStatement(paramLine,
                            assignment.getToken(), assignment.getFinish(), assignment));
                }
                currentFunction.setSimpleParameterList(false);
            }
            return ident;
        } else if (isDestructuringLhs(param)) {
            // binding pattern
            final long paramToken = param.getToken();

            // Introduce synthetic temporary parameter to capture the object to be destructured.
            final IdentNode ident = createIdentNode(paramToken, param.getFinish(), destructuredParameterName(index)).setIsDestructuredParameter();
            verifyDestructuringParameterBindingPattern(param, paramToken, paramLine, contextString);

            final ParserContextFunctionNode currentFunction = lc.getCurrentFunction();
            if (currentFunction != null) {
                if (env._parse_only) {
                    currentFunction.addParameterExpression(ident, param);
                } else {
                    final BinaryNode assignment = new BinaryNode(Token.recast(paramToken, ASSIGN), param, ident);
                    appendParameterStatement(currentFunction, new ExpressionStatement(paramLine, assignment.getToken(), assignment.getFinish(), assignment));
                }
            }
            return ident;
        }
        throw error(AbstractParser.message("invalid.arrow.parameter"), param.getToken());
    }

    private boolean checkNoLineTerminator() {
        assert type == ARROW;
        if (last == RPAREN) {
            return true;
        } else if (last == IDENT) {
            return true;
        }
        for (int i = k - 1; i >= 0; i--) {
            final TokenType t = T(i);
            switch (t) {
            case RPAREN:
            case IDENT:
                return true;
            case EOL:
                return false;
            case COMMENT:
                continue;
            default:
                return t.getKind() == TokenKind.FUTURESTRICT;
            }
        }
        return false;
    }

    /**
     * Peek ahead to see if what follows after the ellipsis is a rest parameter
     * at the end of an arrow function parameter list.
     */
    private boolean isRestParameterEndOfArrowFunctionParameterList() {
        assert type == ELLIPSIS;
        // find the rest parameter, then RPAREN, ARROW, in that order, skipping
        // over EOL (where allowed) and COMMENT
        int i = 1;
        for (;;) {
            final TokenType t = T(k + i++);
            if (t == IDENT) {
                break;
            } else if (t == LBRACKET || t == LBRACE) {
                // "...[a, b]" - a pattern rather than a name, whose end is
                // where the bracket it opened with closes again
                i = endOfBracketed(i);
                if (i < 0) {
                    return false;
                }
                break;
            } else if (t != EOL && t != COMMENT) {
                return false;
            }
        }
        for (;;) {
            final TokenType t = T(k + i++);
            if (t == RPAREN) {
                break;
            } else if (t != EOL && t != COMMENT) {
                return false;
            }
        }
        for (;;) {
            final TokenType t = T(k + i++);
            if (t == ARROW) {
                break;
            } else if (t != COMMENT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scan past a bracketed group whose opening bracket is at {@code k + i - 1}.
     *
     * @param i the offset just after the opening bracket
     * @return the offset just after the matching close, or -1 if there is none
     */
    private int endOfBracketed(final int i) {
        int at = i;
        int depth = 1;
        while (depth > 0) {
            final TokenType t = T(k + at++);
            if (t == LBRACKET || t == LBRACE || t == LPAREN) {
                depth++;
            } else if (t == RBRACKET || t == RBRACE || t == RPAREN) {
                depth--;
            } else if (t == EOF) {
                return -1;
            }
        }
        return at;
    }

    /**
     * Parse an end of line.
     */
    private void endOfLine() {
        switch (type) {
        case SEMICOLON:
        case EOL:
            next();
            break;
        case RPAREN:
        case RBRACKET:
        case RBRACE:
        case EOF:
            break;
        default:
            if (last != EOL) {
                expect(SEMICOLON);
            }
            break;
        }
    }

    /**
     * Parse untagged template literal as string concatenation.
     */
    private Expression templateLiteral() {
        assert type == TEMPLATE || type == TEMPLATE_HEAD;
        final boolean noSubstitutionTemplate = type == TEMPLATE;
        long lastLiteralToken = token;
        LiteralNode<?> literal = getLiteral();
        if (noSubstitutionTemplate) {
            return literal;
        }

        if (env._parse_only) {
            final List<Expression> exprs = new ArrayList<>();
            exprs.add(literal);
            TokenType lastLiteralType;
            do {
                final Expression expression = expression();
                if (type != TEMPLATE_MIDDLE && type != TEMPLATE_TAIL) {
                    throw error(AbstractParser.message("unterminated.template.expression"), token);
                }
                exprs.add(expression);
                lastLiteralType = type;
                literal = getLiteral();
                exprs.add(literal);
            } while (lastLiteralType == TEMPLATE_MIDDLE);
            return new TemplateLiteral(exprs);
        } else {
            Expression concat = literal;
            TokenType lastLiteralType;
            do {
                final Expression expression = expression();
                if (type != TEMPLATE_MIDDLE && type != TEMPLATE_TAIL) {
                    throw error(AbstractParser.message("unterminated.template.expression"), token);
                }
                concat = new BinaryNode(Token.recast(lastLiteralToken, TokenType.ADD), concat, expression);
                lastLiteralType = type;
                lastLiteralToken = token;
                literal = getLiteral();
                concat = new BinaryNode(Token.recast(lastLiteralToken, TokenType.ADD), concat, literal);
            } while (lastLiteralType == TEMPLATE_MIDDLE);
            return concat;
        }
    }

    /**
     * Parse tagged template literal as argument list.
     * @return argument list for a tag function call (template object, ...substitutions)
     */
    private List<Expression> templateLiteralArgumentList() {
        assert type == TEMPLATE || type == TEMPLATE_HEAD;
        final ArrayList<Expression> argumentList = new ArrayList<>();
        final ArrayList<Expression> rawStrings = new ArrayList<>();
        final ArrayList<Expression> cookedStrings = new ArrayList<>();
        argumentList.add(null); // filled at the end

        final long templateToken = token;
        final boolean hasSubstitutions = type == TEMPLATE_HEAD;
        addTemplateLiteralString(rawStrings, cookedStrings);

        if (hasSubstitutions) {
            TokenType lastLiteralType;
            do {
                final Expression expression = expression();
                if (type != TEMPLATE_MIDDLE && type != TEMPLATE_TAIL) {
                    throw error(AbstractParser.message("unterminated.template.expression"), token);
                }
                argumentList.add(expression);

                lastLiteralType = type;
                addTemplateLiteralString(rawStrings, cookedStrings);
            } while (lastLiteralType == TEMPLATE_MIDDLE);
        }

        final LiteralNode<Expression[]> rawStringArray = LiteralNode.newInstance(templateToken, finish, rawStrings);
        final LiteralNode<Expression[]> cookedStringArray = LiteralNode.newInstance(templateToken, finish, cookedStrings);

        if (!env._parse_only) {
            final RuntimeNode templateObject = new RuntimeNode(templateToken, finish, RuntimeNode.Request.GET_TEMPLATE_OBJECT, rawStringArray, cookedStringArray);
            argumentList.set(0, templateObject);
        } else {
            argumentList.set(0, rawStringArray);
        }
        return optimizeList(argumentList);
    }

    private void addTemplateLiteralString(final ArrayList<Expression> rawStrings, final ArrayList<Expression> cookedStrings) {
        final long stringToken = token;
        final String rawString = lexer.valueOfRawString(stringToken);
        final String cookedString = (String) getValue();
        next();
        rawStrings.add(LiteralNode.newInstance(stringToken, finish, rawString));
        cookedStrings.add(LiteralNode.newInstance(stringToken, finish, cookedString));
    }


    /**
     * Parse a module.
     *
     * Module :
     *      ModuleBody?
     *
     * ModuleBody :
     *      ModuleItemList
     */
    private FunctionNode module(final String moduleName) {
        final boolean oldStrictMode = isStrictMode;
        try {
            isStrictMode = true; // Module code is always strict mode code. (ES6 10.2.1)

            // Make a pseudo-token for the script holding its start and length.
            final int functionStart = Math.min(Token.descPosition(Token.withDelimiter(token)), finish);
            final long functionToken = Token.toDesc(FUNCTION, functionStart, source.getLength() - functionStart);
            final int  functionLine  = line;

            final IdentNode ident = new IdentNode(functionToken, Token.descPosition(functionToken), moduleName);
            final ParserContextFunctionNode script = createParserContextFunctionNode(
                            ident,
                            functionToken,
                            FunctionNode.Kind.MODULE,
                            functionLine,
                            Collections.emptyList());
            lc.push(script);

            final ParserContextModuleNode module = new ParserContextModuleNode(moduleName);
            lc.push(module);

            final ParserContextBlockNode body = newBlock();

            functionDeclarations = new ArrayList<>();
            moduleBody();
            addFunctionDeclarations(script);
            functionDeclarations = null;

            restoreBlock(body);
            body.setFlag(Block.NEEDS_SCOPE);
            // the names the imports bind are declared first, so that the module's
            // own code reaches them through its environment rather than through
            // the global object; they store nothing, the values are installed
            // before the body runs
            final List<Statement> moduleStatements = new ArrayList<>(importedBindings);
            moduleStatements.addAll(body.getStatements());
            importedBindings.clear();
            final Block programBody = new Block(functionToken, finish, body.getFlags() | Block.IS_SYNTHETIC | Block.IS_BODY, moduleStatements);
            lc.pop(module);
            lc.pop(script);
            script.setLastToken(token);

            expect(EOF);

            final Module parsedModule = module.createModule();
            verifyUniqueExports(parsedModule, functionToken);
            verifyExportedBindings(parsedModule, moduleStatements, functionToken);
            script.setModule(parsedModule);
            return createFunctionNode(script, functionToken, ident, Collections.emptyList(), FunctionNode.Kind.MODULE, functionLine, programBody);
        } finally {
            isStrictMode = oldStrictMode;
        }
    }

    /**
     * ES2015 15.2.1.1: a module's exported names have to be unique.
     *
     * The names a star export brings in are not known until the graph is linked
     * and are not checked here; two written out with the same name are an early
     * error, whichever kind they are.
     */
    /**
     * ES2015 15.2.1.1: every name a module exports has to be one it declares.
     * Exporting a name the global happens to have, or one nothing declares, is
     * an error rather than a re-export of it.
     *
     * Only what the module writes down counts as a declaration. A let inside a
     * block of the module's own is not one either, and is missed here rather
     * than reported, because unwinding which of the nested declarations reach
     * the top is work the symbol assignment does later and better.
     */
    private void verifyExportedBindings(final Module parsedModule, final List<Statement> statements, final long moduleToken) {
        if (env._parse_only) {
            // the tree API is shown the module as written, without the
            // declarations the runtime needs behind it, so there is nothing here
            // that could tell a missing binding from an unwritten one
            return;
        }
        final Set<String> declared = new HashSet<>();
        final NodeVisitor<LexicalContext> collector = new NodeVisitor<>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                return false;
            }

            @Override
            public boolean enterVarNode(final VarNode varNode) {
                declared.add(varNode.getName().getName());
                return true;
            }
        };
        for (final Statement statement : statements) {
            statement.accept(collector);
        }
        for (final Module.ExportEntry entry : parsedModule.getLocalExportEntries()) {
            final String local = entry.getLocalName().getName();
            if (!declared.contains(local)) {
                throw error(AbstractParser.message("export.not.declared", local), moduleToken);
            }
        }
    }

    private void verifyUniqueExports(final Module parsedModule, final long moduleToken) {
        final Set<String> seen = new HashSet<>();
        for (final Module.ExportEntry entry : parsedModule.getLocalExportEntries()) {
            if (!seen.add(entry.getExportName().getName())) {
                throw error(AbstractParser.message("duplicate.export", entry.getExportName().getName()), moduleToken);
            }
        }
        for (final Module.ExportEntry entry : parsedModule.getIndirectExportEntries()) {
            if (!seen.add(entry.getExportName().getName())) {
                throw error(AbstractParser.message("duplicate.export", entry.getExportName().getName()), moduleToken);
            }
        }
    }

    /**
     * Parse module body.
     *
     * ModuleBody :
     *      ModuleItemList
     *
     * ModuleItemList :
     *      ModuleItem
     *      ModuleItemList ModuleItem
     *
     * ModuleItem :
     *      ImportDeclaration
     *      ExportDeclaration
     *      StatementListItem
     */
    private void moduleBody() {
        while (type != EOF) {
            switch (type) {
            case IMPORT:
                importDeclaration();
                break;
            case EXPORT:
                exportDeclaration();
                break;
            default:
                // StatementListItem
                statement(true, 0, false);
                break;
            }
        }
    }


    /**
     * Parse import declaration.
     *
     * ImportDeclaration :
     *     import ImportClause FromClause ;
     *     import ModuleSpecifier ;
     * ImportClause :
     *     ImportedDefaultBinding
     *     NameSpaceImport
     *     NamedImports
     *     ImportedDefaultBinding , NameSpaceImport
     *     ImportedDefaultBinding , NamedImports
     * ImportedDefaultBinding :
     *     ImportedBinding
     * ModuleSpecifier :
     *     StringLiteral
     * ImportedBinding :
     *     BindingIdentifier
     */
    private void importDeclaration() {
        final int startPosition = start;
        final int importLine = line;
        expect(IMPORT);
        final ParserContextModuleNode module = lc.getCurrentModule();
        if (type == STRING || type == ESCSTRING) {
            // import ModuleSpecifier ;
            final IdentNode moduleSpecifier = createIdentNode(token, finish, (String) getValue());
            next();
            module.addModuleRequest(moduleSpecifier);
        } else {
            // import ImportClause FromClause ;
            List<Module.ImportEntry> importEntries;
            if (type == MUL) {
                importEntries = Collections.singletonList(nameSpaceImport(startPosition));
            } else if (type == LBRACE) {
                importEntries = namedImports(startPosition);
            } else if (isBindingIdentifier()) {
                // ImportedDefaultBinding
                final IdentNode importedDefaultBinding = bindingIdentifier("ImportedBinding");
                // the name imported is "default"; the identifier written is what
                // it binds to, and the two are only the same for "import { x }"
                final IdentNode defaultName = createIdentNode(
                        Token.recast(importedDefaultBinding.getToken(), IDENT),
                        importedDefaultBinding.getFinish(), Module.DEFAULT_NAME);
                final Module.ImportEntry defaultImport =
                        Module.ImportEntry.importSpecifier(defaultName, importedDefaultBinding, startPosition, finish);

                if (type == COMMARIGHT) {
                    next();
                    // "import def, { a } from m" binds the default as well as the
                    // named ones; it used to be dropped on the floor here
                    importEntries = new ArrayList<>();
                    importEntries.add(defaultImport);
                    if (type == MUL) {
                        importEntries.add(nameSpaceImport(startPosition));
                    } else if (type == LBRACE) {
                        importEntries.addAll(namedImports(startPosition));
                    } else {
                        throw error(AbstractParser.message("expected.named.import"));
                    }
                } else {
                    importEntries = Collections.singletonList(defaultImport);
                }
            } else {
                throw error(AbstractParser.message("expected.import"));
            }

            final IdentNode moduleSpecifier = fromClause();
            module.addModuleRequest(moduleSpecifier);
            for (final Module.ImportEntry importEntry : importEntries) {
                module.addImportEntry(importEntry.withFrom(moduleSpecifier, finish));
                declareImportedBinding(importEntry.getLocalName(), importLine);
            }
        }
        endOfLine();
    }

    /**
     * Declares the name an import binds, so that the module's own code reaches it
     * through the module's environment rather than through the global object.
     *
     * The declaration has no initialiser and so stores nothing: the binding's
     * value is installed as an accessor before the body runs, because ES2015
     * 8.1.1.5 makes an import name a binding somewhere else rather than a copy.
     */
    private void declareImportedBinding(final IdentNode localName, final int importLine) {
        if (localName == null || env._parse_only) {
            // the tree API is shown the module as written, not the declarations
            // the runtime needs behind it
            return;
        }
        final long varToken = Token.recast(localName.getToken(), VAR);
        importedBindings.add(new VarNode(importLine, varToken, localName.getFinish(), localName, null));
    }

    /**
     * NameSpaceImport :
     *     * as ImportedBinding
     *
     * @param startPosition the start of the import declaration
     * @return imported binding identifier
     */
    private Module.ImportEntry nameSpaceImport(final int startPosition) {
        assert type == MUL;
        final IdentNode starName = createIdentNode(Token.recast(token, IDENT), finish, Module.STAR_NAME);
        next();
        final long asToken = token;
        final String as = (String) expectValue(IDENT);
        if (!"as".equals(as) || Token.descLength(asToken) != as.length()) {
            throw error(AbstractParser.message("expected.as"), asToken);
        }
        final IdentNode localNameSpace = bindingIdentifier("ImportedBinding");
        return Module.ImportEntry.importSpecifier(starName, localNameSpace, startPosition, finish);
    }

    /**
     * NamedImports :
     *     { }
     *     { ImportsList }
     *     { ImportsList , }
     * ImportsList :
     *     ImportSpecifier
     *     ImportsList , ImportSpecifier
     * ImportSpecifier :
     *     ImportedBinding
     *     IdentifierName as ImportedBinding
     * ImportedBinding :
     *     BindingIdentifier
     */
    private List<Module.ImportEntry> namedImports(final int startPosition) {
        assert type == LBRACE;
        next();
        final List<Module.ImportEntry> importEntries = new ArrayList<>();
        while (type != RBRACE) {
            final boolean bindingIdentifier = isBindingIdentifier();
            final long nameToken = token;
            final IdentNode importName = getIdentifierName();
            if (isUnescaped("as")) {
                next();
                final IdentNode localName = bindingIdentifier("ImportedBinding");
                importEntries.add(Module.ImportEntry.importSpecifier(importName, localName, startPosition, finish));
            } else if (!bindingIdentifier) {
                throw error(AbstractParser.message("expected.binding.identifier"), nameToken);
            } else {
                // the name is the binding as well as the export it names, and
                // module code is strict, so "arguments" and "eval" are out
                verifyIdent(importName, "ImportedBinding");
                importEntries.add(Module.ImportEntry.importSpecifier(importName, startPosition, finish));
            }
            if (type == COMMARIGHT) {
                next();
            } else {
                break;
            }
        }
        expect(RBRACE);
        return importEntries;
    }

    /**
     * FromClause :
     *     from ModuleSpecifier
     */
    private IdentNode fromClause() {
        final long fromToken = token;
        final String name = (String) expectValue(IDENT);
        if (!"from".equals(name) || Token.descLength(fromToken) != name.length()) {
            throw error(AbstractParser.message("expected.from"), fromToken);
        }
        if (type == STRING || type == ESCSTRING) {
            final IdentNode moduleSpecifier = createIdentNode(Token.recast(token, IDENT), finish, (String) getValue());
            next();
            return moduleSpecifier;
        } else {
            throw error(expectMessage(STRING));
        }
    }

    /**
     * Parse export declaration.
     *
     * ExportDeclaration :
     *     export * FromClause ;
     *     export ExportClause FromClause ;
     *     export ExportClause ;
     *     export VariableStatement
     *     export Declaration
     *     export default HoistableDeclaration[Default]
     *     export default ClassDeclaration[Default]
     *     export default [lookahead !in {function, class}] AssignmentExpression[In] ;
     */
    private void exportDeclaration() {
        expect(EXPORT);
        final int startPosition = start;
        final ParserContextModuleNode module = lc.getCurrentModule();
        switch (type) {
            case MUL: {
                final IdentNode starName = createIdentNode(Token.recast(token, IDENT), finish, Module.STAR_NAME);
                next();
                final IdentNode moduleRequest = fromClause();
                endOfLine();
                module.addModuleRequest(moduleRequest);
                module.addStarExportEntry(Module.ExportEntry.exportStarFrom(starName, moduleRequest, startPosition, finish));
                break;
            }
            case LBRACE: {
                final List<Module.ExportEntry> exportEntries = exportClause(startPosition);
                if (isUnescaped("from")) {
                    final IdentNode moduleRequest = fromClause();
                    module.addModuleRequest(moduleRequest);
                    for (final Module.ExportEntry exportEntry : exportEntries) {
                        module.addIndirectExportEntry(exportEntry.withFrom(moduleRequest, finish));
                    }
                } else {
                    for (final Module.ExportEntry exportEntry : exportEntries) {
                        module.addLocalExportEntry(exportEntry);
                    }
                }
                endOfLine();
                break;
            }
            case DEFAULT:
                final IdentNode defaultName = createIdentNode(Token.recast(token, IDENT), finish, Module.DEFAULT_NAME);
                next();
                final Expression assignmentExpression;
                IdentNode ident;
                final int lineNumber = line;
                final long rhsToken = token;
                final boolean declaration;
                // whether what is exported is a function, which 15.2.1.16.4 has
                // ready before the module's body starts, named or not
                boolean hoistable = false;
                // 15.2.3.11: what a module exports by default and does not name
                // is named "default", which is the NamedEvaluation the binding
                // it is given performs
                defaultNames.push(createIdentNode(Token.recast(rhsToken, IDENT), finish, Module.DEFAULT_NAME));
                try {
                switch (type) {
                    case FUNCTION: {
                        // 15.2.3.11: "export default function F() {}" is a
                        // hoistable declaration and binds F in the module, as
                        // the same function written without the export would.
                        // Only the anonymous form is an expression, bound to the
                        // name the module keeps its default export under.
                        final boolean named = lookaheadIsNamedFunction();
                        final FunctionNode function = (FunctionNode) functionExpression(named, true);
                        assignmentExpression = function;
                        ident = named ? function.getIdent() : null;
                        declaration = true;
                        hoistable = true;
                        break;
                    }
                    case CLASS: {
                        final boolean named = T(k + 1) == IDENT;
                        final ClassNode classNode = classDeclaration(!named);
                        assignmentExpression = classNode;
                        ident = named ? classNode.getIdent() : null;
                        declaration = true;
                        break;
                    }
                    default:
                        if (lookaheadIsAsyncFunction()) {
                            // "export default async function A() {}" is a
                            // hoistable declaration too
                            final long asyncToken = token;
                            next();
                            final boolean named = lookaheadIsNamedFunction();
                            final FunctionNode async = (FunctionNode)
                                    functionExpression(named, true, true, asyncToken);
                            assignmentExpression = async;
                            ident = named ? async.getIdent() : null;
                            declaration = true;
                            hoistable = true;
                            break;
                        }
                        assignmentExpression = assignmentExpression(false);
                        ident = null;
                        declaration = false;
                        break;
                }
                } finally {
                    defaultNames.pop();
                }
                if (ident != null) {
                    module.addLocalExportEntry(Module.ExportEntry.exportDefault(defaultName, ident, startPosition, finish));
                } else {
                    ident = createIdentNode(Token.recast(rhsToken, IDENT), finish, Module.DEFAULT_EXPORT_BINDING_NAME);
                    // 15.2.1.16.4: the binding is lexical and uninitialised
                    // until the export runs, which reading it before then is a
                    // ReferenceError - except for a function, which is ready
                    // before the body starts, as a declaration of one is
                    final VarNode binding = new VarNode(lineNumber, Token.recast(rhsToken, LET), finish,
                            ident, assignmentExpression, VarNode.IS_LET);
                    if (hoistable) {
                        functionDeclarations.add(binding);
                    } else {
                        lc.appendStatementToCurrentNode(binding);
                    }
                    if (!declaration) {
                        endOfLine();
                    }
                    module.addLocalExportEntry(Module.ExportEntry.exportDefault(defaultName, ident, startPosition, finish));
                }
                break;
            case VAR:
            case LET:
            case CONST:
                final List<Statement> statements = lc.getCurrentBlock().getStatements();
                final int previousEnd = statements.size();
                variableStatement(type);
                for (final Statement statement : statements.subList(previousEnd, statements.size())) {
                    if (statement instanceof VarNode) {
                        module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(((VarNode) statement).getName(), startPosition, finish));
                    }
                }
                break;
            case CLASS: {
                final ClassNode classDeclaration = classDeclaration(false);
                module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(classDeclaration.getIdent(), startPosition, finish));
                break;
            }
            case FUNCTION: {
                final FunctionNode functionDeclaration = (FunctionNode) functionExpression(true, true);
                module.addLocalExportEntry(Module.ExportEntry.exportSpecifier(functionDeclaration.getIdent(), startPosition, finish));
                break;
            }
            default:
                throw error(AbstractParser.message("invalid.export"), token);
        }
    }

    /**
     * ExportClause :
     *     { }
     *     { ExportsList }
     *     { ExportsList , }
     * ExportsList :
     *     ExportSpecifier
     *     ExportsList , ExportSpecifier
     * ExportSpecifier :
     *     IdentifierName
     *     IdentifierName as IdentifierName
     *
     * @return a list of ExportSpecifiers
     */
    private List<Module.ExportEntry> exportClause(final int startPosition) {
        assert type == LBRACE;
        next();
        final List<Module.ExportEntry> exports = new ArrayList<>();
        while (type != RBRACE) {
            final IdentNode localName = getIdentifierName();
            if (isUnescaped("as")) {
                next();
                final IdentNode exportName = getIdentifierName();
                exports.add(Module.ExportEntry.exportSpecifier(exportName, localName, startPosition, finish));
            } else {
                exports.add(Module.ExportEntry.exportSpecifier(localName, startPosition, finish));
            }
            if (type == COMMARIGHT) {
                next();
            } else {
                break;
            }
        }
        expect(RBRACE);
        return exports;
    }

    @Override
    public String toString() {
        return "'JavaScript Parsing'";
    }

    private void markEval(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        boolean flaggedCurrentFn = false;
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!flaggedCurrentFn) {
                fn.setFlag(FunctionNode.HAS_EVAL);
                flaggedCurrentFn = true;
                if (FunctionNode.isArrow(fn.getKind())) {
                    // possible use of this in an eval that's nested in an arrow function, e.g.:
                    // function fun(){ return (() => eval("this"))(); };
                    markThis(lc);
                    markNewTarget(lc);
                }
            } else {
                fn.setFlag(FunctionNode.HAS_NESTED_EVAL);
            }
            final ParserContextBlockNode body = lc.getFunctionBody(fn);
            // NOTE: it is crucial to mark the body of the outer function as needing scope even when we skip
            // parsing a nested function. functionBody() contains code to compensate for the lack of invoking
            // this method when the parser skips a nested function.
            body.setFlag(Block.NEEDS_SCOPE);
            fn.setFlag(FunctionNode.HAS_SCOPE_BLOCK);
        }
    }

    private void prependStatement(final Statement statement) {
        lc.prependStatementToCurrentNode(statement);
    }

    private void appendStatement(final Statement statement) {
        lc.appendStatementToCurrentNode(statement);
    }

    private void markSuperCall(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        boolean insideArrow = false;
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!FunctionNode.isArrow(fn.getKind())) {
                // on a re-parse the constructor is not on the stack, so there is
                // nothing here that could know it was one
                assert fn.isSubclassConstructor() || reparsedFunction != null;
                fn.setFlag(FunctionNode.ES6_HAS_DIRECT_SUPER);
                if (insideArrow) {
                    // an arrow has none of what super() is compiled from, and
                    // reaches it through the scope instead
                    fn.setFlag(FunctionNode.ES6_ARROW_CALLS_SUPER);
                }
                break;
            }
            insideArrow = true;
        }
    }

    private ParserContextFunctionNode getCurrentNonArrowFunction() {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!FunctionNode.isArrow(fn.getKind())) {
                return fn;
            }
        }
        return null;
    }

    private static void markThis(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        boolean throughArrow = false;
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            fn.setFlag(FunctionNode.USES_THIS);
            if (!FunctionNode.isArrow(fn.getKind())) {
                if (throughArrow) {
                    // an arrow inside this function reads its this, so it has to
                    // be published where the arrow can capture it
                    fn.setFlag(FunctionNode.ES6_ARROW_USES_THIS);
                }
                break;
            }
            throughArrow = true;
        }
    }

    /**
     * ES2015 8.1.1.3: an arrow resolves super where it was written, so every
     * arrow between the super and the method that encloses it has to know, in
     * order to carry the method's home object along.
     */
    private static void markSuper(final ParserContext lc) {
        // a super property reference is called on this, so it reads it too
        markThis(lc);
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            fn.setFlag(FunctionNode.ES6_USES_SUPER);
            if (!FunctionNode.isArrow(fn.getKind())) {
                break;
            }
        }
    }

    private void markNewTarget(final ParserContext lc) {
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!FunctionNode.isArrow(fn.getKind())) {
                // an eval program reads the caller's new.target through its own
                // callee, so it is marked too, which is what gives it one
                if (!fn.isProgram() || evalNewTargetAllowed) {
                    fn.setFlag(FunctionNode.ES6_USES_NEW_TARGET);
                }
                break;
            }
        }
    }

    /** Whether what is being parsed is a module, where "await" is a reserved word. */
    /** Whether what is being parsed is a statement of a module's own body. */
    private boolean isModuleTopLevel() {
        final ParserContextFunctionNode function = lc.getCurrentFunction();
        return function != null && function.getKind() == FunctionNode.Kind.MODULE;
    }

    private boolean inModule() {
        for (final Iterator<ParserContextFunctionNode> iter = lc.getFunctions(); iter.hasNext();) {
            if (iter.next().getKind() == FunctionNode.Kind.MODULE) {
                return true;
            }
        }
        return false;
    }

    private boolean inGeneratorFunction() {
        return reparsingPropertyKey || lc.getCurrentFunction().getKind() == FunctionNode.Kind.GENERATOR;
    }

    /**
     * Whether the function being parsed is an async one.
     *
     * await belongs to the function it is written in and nothing else: an
     * ordinary arrow inside an async function is not itself async, and cannot
     * await.
     */
    private boolean inAsyncFunction() {
        if (inAsyncParameters || reparsingPropertyKey) {
            return true;
        }
        final Iterator<ParserContextFunctionNode> iter = lc.getFunctions();
        while (iter.hasNext()) {
            final FunctionNode.Kind kind = iter.next().getKind();
            if (kind == FunctionNode.Kind.ASYNC || kind == FunctionNode.Kind.ASYNC_ARROW) {
                return true;
            }
            if (kind != FunctionNode.Kind.ARROW) {
                // an ordinary function written inside an async one is not async,
                // and await is an ordinary name again in it
                return false;
            }
        }
        // the parameter list of a Function built from strings is parsed before
        // there is any function to be inside
        return false;
    }

    /**
     * Whether what follows is "async function" on one line.
     *
     * "async" is not a keyword: it names an async function only when it is
     * written immediately before one, with no line break in between, and is an
     * ordinary identifier everywhere else.
     */
    private boolean lookaheadIsAsyncFunction() {
        if (!isUnescapedAsync()) {
            return false;
        }
        for (int i = 1;; i++) {
            final TokenType t = T(k + i);
            switch (t) {
            case COMMENT:
                continue;
            case FUNCTION:
                return true;
            default:
                return false;
            }
        }
    }

    /**
     * Whether the current token is a contextual "async" opening a method
     * definition - "async" written in front of a property name, rather than
     * naming a property or a method itself.
     */
    private boolean lookaheadIsAsyncMethod() {
        if (!isUnescapedAsync()) {
            return false;
        }
        switch (T(k + 1)) {
        case COLON:      // { async: 1 }
        case LPAREN:     // { async() {} }
        case COMMARIGHT: // { async, }
        case RBRACE:     // { async }
        case ASSIGN:     // { async = 1 }
        case EOL:        // a line break makes it an ordinary name
        case SEMICOLON:
            return false;
        default:
            return true;
        }
    }

    /**
     * Whether what follows is an async arrow function - "async" written in
     * front of an arrow's parameter list, on the same line.
     */
    private boolean lookaheadIsAsyncArrow() {
        if (!isUnescapedAsync()) {
            return false;
        }
        // async x => ...
        if (T(k + 1) == IDENT && T(k + 2) == ARROW) {
            return true;
        }
        if (T(k + 1) != LPAREN) {
            return false;
        }
        // async ( ... ) => ..., which needs the matching parenthesis found first
        int depth = 0;
        for (int i = 1;; i++) {
            final TokenType t = T(k + i);
            switch (t) {
            case LPAREN:
                depth++;
                break;
            case RPAREN:
                if (--depth == 0) {
                    return T(k + i + 1) == ARROW;
                }
                break;
            case EOF:
                return false;
            default:
                break;
            }
        }
    }

    /**
     * Whether the current token is the word "async" written as itself.
     *
     * ES2017 11.6.2 applies to a contextual keyword as much as to a reserved
     * one: spelled with an escape it is an ordinary identifier, and an ordinary
     * identifier does not make the function after it async.
     */
    private boolean isUnescapedAsync() {
        return type == IDENT && ASYNC_NAME.equals(getValue())
                && Token.descLength(token) == ASYNC_NAME.length();
    }

    /** Whether the current token is a contextual "await" that opens an AwaitExpression. */
    private boolean isAwaitExpression() {
        return type == IDENT && AWAIT_NAME.equals(getValue()) && inAsyncFunction();
    }

    /**
     * AwaitExpression : await UnaryExpression (ES2017 14.6).
     */
    private Expression awaitExpression() {
        final long awaitToken = Token.recast(token, TokenType.AWAIT);
        next();
        return new UnaryNode(awaitToken, unaryExpression());
    }
}
