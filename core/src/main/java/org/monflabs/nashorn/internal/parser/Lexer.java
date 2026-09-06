/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.parser;

import static org.monflabs.nashorn.internal.parser.TokenType.ADD;
import static org.monflabs.nashorn.internal.parser.TokenType.BINARY_NUMBER;
import static org.monflabs.nashorn.internal.parser.TokenType.COMMENT;
import static org.monflabs.nashorn.internal.parser.TokenType.DECIMAL;
import static org.monflabs.nashorn.internal.parser.TokenType.DIRECTIVE_COMMENT;
import static org.monflabs.nashorn.internal.parser.TokenType.EOF;
import static org.monflabs.nashorn.internal.parser.TokenType.EOL;
import static org.monflabs.nashorn.internal.parser.TokenType.ERROR;
import static org.monflabs.nashorn.internal.parser.TokenType.ESCSTRING;
import static org.monflabs.nashorn.internal.parser.TokenType.FLOATING;
import static org.monflabs.nashorn.internal.parser.TokenType.FUNCTION;
import static org.monflabs.nashorn.internal.parser.TokenType.HEXADECIMAL;
import static org.monflabs.nashorn.internal.parser.TokenType.LBRACE;
import static org.monflabs.nashorn.internal.parser.TokenType.LPAREN;
import static org.monflabs.nashorn.internal.parser.TokenType.OCTAL;
import static org.monflabs.nashorn.internal.parser.TokenType.NON_OCTAL_DECIMAL;
import static org.monflabs.nashorn.internal.parser.TokenType.OCTAL_LEGACY;
import static org.monflabs.nashorn.internal.parser.TokenType.RBRACE;
import static org.monflabs.nashorn.internal.parser.TokenType.REGEX;
import static org.monflabs.nashorn.internal.parser.TokenType.RPAREN;
import static org.monflabs.nashorn.internal.parser.TokenType.STRING;
import static org.monflabs.nashorn.internal.parser.TokenType.TEMPLATE;
import static org.monflabs.nashorn.internal.parser.TokenType.THIS;
import static org.monflabs.nashorn.internal.parser.TokenType.SUPER;
import static org.monflabs.nashorn.internal.parser.TokenType.RBRACKET;
import static org.monflabs.nashorn.internal.parser.TokenType.DIV;
import static org.monflabs.nashorn.internal.parser.TokenType.ASSIGN_DIV;
import static org.monflabs.nashorn.internal.parser.TokenType.TEMPLATE_HEAD;
import static org.monflabs.nashorn.internal.parser.TokenType.TEMPLATE_MIDDLE;
import static org.monflabs.nashorn.internal.parser.TokenType.TEMPLATE_TAIL;
import static org.monflabs.nashorn.internal.parser.TokenType.XML;

import java.io.Serializable;

import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.ErrorManager;
import org.monflabs.nashorn.internal.runtime.JSErrorType;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ParserException;
import org.monflabs.nashorn.internal.runtime.Source;
import org.monflabs.nashorn.internal.runtime.options.Options;

/**
 * Responsible for converting source content into a stream of tokens.
 *
 */
@SuppressWarnings("fallthrough")
public class Lexer extends Scanner {
    private static final boolean XML_LITERALS = Options.getBooleanProperty("nashorn.lexer.xmlliterals");

    /** Content source. */
    private final Source source;

    /** Buffered stream for tokens. */
    private final TokenStream stream;

    /** True if here and edit strings are supported. */
    private final boolean scripting;

    /** True if ECMA-262 Annex B's HTML-like comments are recognised. */
    private final boolean annexB;

    /**
     * Whether nothing but whitespace and same-line delimited comments has been
     * seen since the last line terminator.
     *
     * B.1.1 lets {@code -->} begin a comment only there: the line it is on must
     * hold nothing else in front of it, and a multi-line comment that contained
     * a line terminator counts as one.
     */
    private boolean lineIsBlank = true;

    /** True if parsing in ECMAScript 6 mode. */

    /** True if a nested scan. (scan to completion, no EOF.) */
    private final boolean nested;

    /** Pending new line number and position. */
    int pendingLine;

    /** Position of last EOL + 1. */
    private int linePosition;

    /** Type of last token added. */
    private TokenType last;

    private final boolean pauseOnFunctionBody;
    private boolean pauseOnNextLeftBrace;

    private int templateExpressionOpenBraces;

    private static final String JAVASCRIPT_OTHER_WHITESPACE =
        "\u2028" + // line separator
        "\u2029" + // paragraph separator
        "\u00a0" + // Latin-1 space
        "\u1680" + // Ogham space mark
        "\u180e" + // separator, Mongolian vowel
        "\u2000" + // en quad
        "\u2001" + // em quad
        "\u2002" + // en space
        "\u2003" + // em space
        "\u2004" + // three-per-em space
        "\u2005" + // four-per-em space
        "\u2006" + // six-per-em space
        "\u2007" + // figure space
        "\u2008" + // punctuation space
        "\u2009" + // thin space
        "\u200a" + // hair space
        "\u202f" + // narrow no-break space
        "\u205f" + // medium mathematical space
        "\u3000" + // ideographic space
        "\ufeff"   // byte order mark
        ;

    private static final String JAVASCRIPT_WHITESPACE_IN_REGEXP =
        "\\u000a" + // line feed
        "\\u000d" + // carriage return (ctrl-m)
        "\\u2028" + // line separator
        "\\u2029" + // paragraph separator
        "\\u0009" + // tab
        "\\u0020" + // ASCII space
        "\\u000b" + // tabulation line
        "\\u000c" + // ff (ctrl-l)
        "\\u00a0" + // Latin-1 space
        "\\u1680" + // Ogham space mark
        "\\u180e" + // separator, Mongolian vowel
        "\\u2000" + // en quad
        "\\u2001" + // em quad
        "\\u2002" + // en space
        "\\u2003" + // em space
        "\\u2004" + // three-per-em space
        "\\u2005" + // four-per-em space
        "\\u2006" + // six-per-em space
        "\\u2007" + // figure space
        "\\u2008" + // punctuation space
        "\\u2009" + // thin space
        "\\u200a" + // hair space
        "\\u202f" + // narrow no-break space
        "\\u205f" + // medium mathematical space
        "\\u3000" + // ideographic space
        "\\ufeff"   // byte order mark
        ;

    static String unicodeEscape(final char ch) {
        final StringBuilder sb = new StringBuilder();

        sb.append("\\u");

        final String hex = Integer.toHexString(ch);
        sb.append("0".repeat(4 - hex.length()));
        sb.append(hex);

        return sb.toString();
    }

    /**
     * Constructor
     *
     * @param source    the source
     * @param stream    the token stream to lex
     */
    public Lexer(final Source source, final TokenStream stream) {
        this(source, stream, false);
    }

    /**
     * Constructor
     *
     * @param source    the source
     * @param stream    the token stream to lex
     * @param scripting are we in scripting mode
     */
    public Lexer(final Source source, final TokenStream stream, final boolean scripting) {
        this(source, 0, source.getLength(), stream, scripting, true, false);
    }

    /**
     * Constructor
     *
     * @param source    the source
     * @param start     start position in source from which to start lexing
     * @param len       length of source segment to lex
     * @param stream    token stream to lex
     * @param scripting are we in scripting mode
     * @param annexB    are ECMA-262 Annex B's HTML-like comments recognised
     * @param pauseOnFunctionBody if true, lexer will return from {@link #lexify()} when it encounters a
     * function body. This is used with the feature where the parser is skipping nested function bodies to
     * avoid reading ahead unnecessarily when we skip the function bodies.
     */
    public Lexer(final Source source, final int start, final int len, final TokenStream stream, final boolean scripting, final boolean annexB, final boolean pauseOnFunctionBody) {
        super(source.getContent(), 1, start, len);
        this.source      = source;
        this.stream      = stream;
        this.scripting   = scripting;
        this.annexB      = annexB;
        this.nested      = false;
        this.pendingLine = 1;
        this.last        = EOL;

        this.pauseOnFunctionBody = pauseOnFunctionBody;
    }

    private Lexer(final Lexer lexer, final State state) {
        super(lexer, state);

        source = lexer.source;
        stream = lexer.stream;
        scripting = lexer.scripting;
        annexB = lexer.annexB;
        nested = true;

        pendingLine = state.pendingLine;
        linePosition = state.linePosition;
        last = EOL;
        pauseOnFunctionBody = false;
    }

    static class State extends Scanner.State {
        /** Pending new line number and position. */
        public final int pendingLine;

        /** Position of last EOL + 1. */
        public final int linePosition;

        /** Type of last token added. */
        public final TokenType last;

        /*
         * Constructor.
         */

        State(final int position, final int limit, final int line, final int pendingLine, final int linePosition, final TokenType last) {
            super(position, limit, line);

            this.pendingLine = pendingLine;
            this.linePosition = linePosition;
            this.last = last;
        }
    }

    /**
     * Save the state of the scan.
     *
     * @return Captured state.
     */
    @Override
    State saveState() {
        return new State(position, limit, line, pendingLine, linePosition, last);
    }

    /**
     * Restore the state of the scan.
     *
     * @param state
     *            Captured state.
     */
    void restoreState(final State state) {
        super.restoreState(state);

        pendingLine = state.pendingLine;
        linePosition = state.linePosition;
        last = state.last;
    }

    /**
     * Add a new token to the stream.
     *
     * @param type
     *            Token type.
     * @param start
     *            Start position.
     * @param end
     *            End position.
     */
    protected void add(final TokenType type, final int start, final int end) {
        // Record last token.
        last = type;

        // B.1.1's --> needs to know whether anything but whitespace and
        // same-line comments has been on this line. A comment leaves the answer
        // where it was - the multi-line case sets it in skipComments - and
        // anything else settles it.
        if (type == EOL) {
            lineIsBlank = true;
        } else if (type != COMMENT && type != DIRECTIVE_COMMENT) {
            lineIsBlank = false;
        }

        // Only emit the last EOL in a cluster.
        if (type == EOL) {
            pendingLine = end;
            linePosition = start;
        } else {
            // Write any pending EOL to stream.
            if (pendingLine != -1) {
                stream.put(Token.toDesc(EOL, linePosition, pendingLine));
                pendingLine = -1;
            }

            // Write token to stream.
            stream.put(Token.toDesc(type, start, end - start));
        }
    }

    /**
     * Add a new token to the stream.
     *
     * @param type
     *            Token type.
     * @param start
     *            Start position.
     */
    protected void add(final TokenType type, final int start) {
        add(type, start, position);
    }

    /**
     * Return the String of valid whitespace characters for regular
     * expressions in JavaScript
     * @return regexp whitespace string
     */
    public static String getWhitespaceRegExp() {
        return JAVASCRIPT_WHITESPACE_IN_REGEXP;
    }

    /**
     * Skip end of line.
     *
     * @param addEOL true if EOL token should be recorded.
     */
    private void skipEOL(final boolean addEOL) {

        if (ch0 == '\r') { // detect \r\n pattern
            skip(1);
            if (ch0 == '\n') {
                skip(1);
            }
        } else { // all other space, ch0 is guaranteed to be EOL or \0
            skip(1);
        }

        // bump up line count
        line++;

        if (addEOL) {
            // Add an EOL token.
            add(EOL, position, line);
        }
    }

    /**
     * Skip over rest of line including end of line.
     *
     * @param addEOL true if EOL token should be recorded.
     */
    private void skipLine(final boolean addEOL) {
        // Ignore characters.
        while (!isEOL(ch0) && !atEOF()) {
            skip(1);
        }
        // Skip over end of line.
        skipEOL(addEOL);
    }

    /**
     * Test whether a char is valid JavaScript whitespace
     * @param ch a char
     * @return true if valid JavaScript whitespace
     */
    public static boolean isJSWhitespace(final char ch) {
        return ch == ' '                  // space
            || ch >= '\t' && ch <= '\r'   // 0x09..0x0d: tab, line feed, tabulation line, ff, carriage return
            || ch >= 160 && isOtherJSWhitespace(ch);
    }

    private static boolean isOtherJSWhitespace(final char ch) {
        return JAVASCRIPT_OTHER_WHITESPACE.indexOf(ch) != -1;
    }

    /**
     * Test whether a char is valid JavaScript end of line
     * @param ch a char
     * @return true if valid JavaScript end of line
     */
    public static boolean isJSEOL(final char ch) {
        return ch == '\n'      // line feed
            || ch == '\r'      // carriage return (ctrl-m)
            || ch == '\u2028'  // line separator
            || ch == '\u2029'; // paragraph separator
    }

    /**
     * Test whether a char closes a string literal. ES2019's JSON-superset change
     * (25.5) lets U+2028/U+2029 appear unescaped in a string literal, so - unlike
     * {@link #isJSEOL} - they do not end it; a raw CR or LF still does.
     * @param ch a char
     * @return true if the char ends a string literal
     */
    public static boolean isStringLiteralEOL(final char ch) {
        return ch == '\n' || ch == '\r';
    }

    /**
     * Test if char is a string delimiter, e.g. '\' or '"'.
     * @param ch a char
     * @return true if string delimiter
     */
    protected boolean isStringDelimiter(final char ch) {
        return ch == '\'' || ch == '"';
    }

    /**
     * Test if char is a template literal delimiter ('`').
     */
    private static boolean isTemplateDelimiter(final char ch) {
        return ch == '`';
    }

    /**
     * Test whether a char is valid JavaScript whitespace
     * @param ch a char
     * @return true if valid JavaScript whitespace
     */
    protected boolean isWhitespace(final char ch) {
        return Lexer.isJSWhitespace(ch);
    }

    /**
     * Test whether a char is valid JavaScript end of line
     * @param ch a char
     * @return true if valid JavaScript end of line
     */
    protected boolean isEOL(final char ch) {
        return Lexer.isJSEOL(ch);
    }

    /**
     * Skip over whitespace and detect end of line, adding EOL tokens if
     * encountered.
     *
     * @param addEOL true if EOL tokens should be recorded.
     */
    private void skipWhitespace(final boolean addEOL) {
        while (isWhitespace(ch0)) {
            if (isEOL(ch0)) {
                skipEOL(addEOL);
            } else {
                skip(1);
            }
        }
    }

    /**
     * Skip over one of ECMA-262 B.1.1's HTML-like comments.
     *
     * {@code <!--} opens one wherever it appears. {@code -->} opens one only at
     * the head of a line, where the grammar lets whitespace and delimited
     * comments precede it but nothing else - so "a-->b" is a decrement and a
     * comparison, as it has always been, and only a line that begins with the
     * three characters is a comment.
     *
     * @return true if a comment was skipped
     */
    private boolean skipHTMLComment() {
        final int start = position;

        if (ch0 == '<' && ch1 == '!' && ch2 == '-' && ch3 == '-') {
            skip(4);
        } else if (ch0 == '-' && ch1 == '-' && ch2 == '>' && lineIsBlank) {
            skip(3);
        } else {
            return false;
        }

        while (!atEOF() && !isEOL(ch0)) {
            skip(1);
        }

        add(COMMENT, start);
        return true;
    }

    /**
     * Skip over comments.
     *
     * @return True if a comment.
     */
    protected boolean skipComments() {
        // Save the current position.
        final int start = position;

        if (ch0 == '/') {
            // Is it a // comment.
            if (ch1 == '/') {
                // Skip over //.
                skip(2);

                boolean directiveComment = (ch0 == '#' || ch0 == '@') && (ch1 == ' ');

                // Scan for EOL.
                while (!atEOF() && !isEOL(ch0)) {
                    skip(1);
                }
                // Did detect a comment.
                add(directiveComment? DIRECTIVE_COMMENT : COMMENT, start);
                return true;
            } else if (ch1 == '*') {
                // Skip over /*.
                skip(2);
                boolean multiLine = false;
                // Scan for */.
                while (!atEOF() && !(ch0 == '*' && ch1 == '/')) {
                    // If end of line handle else skip character.
                    if (isEOL(ch0)) {
                        multiLine = true;
                        skipEOL(true);
                    } else {
                        skip(1);
                    }
                }

                if (atEOF()) {
                    // TODO - Report closing */ missing in parser.
                    add(ERROR, start);
                } else {
                    // Skip */.
                    skip(2);
                }

                // B.1.1 lets --> follow a multi-line comment that held a line
                // terminator, and a comment that held none leaves the line as
                // it found it
                lineIsBlank = lineIsBlank || multiLine;

                // Did detect a comment.
                add(COMMENT, start);
                return true;
            }
        } else if (ch0 == '#') {
            assert scripting;
            // shell style comment
            // Skip over #.
            skip(1);
            // Scan for EOL.
            while (!atEOF() && !isEOL(ch0)) {
                skip(1);
            }
            // Did detect a comment.
            add(COMMENT, start);
            return true;
        }

        // Not a comment.
        return false;
    }

    /**
     * Convert a regex token to a token object.
     *
     * @param start  Position in source content.
     * @param length Length of regex token.
     * @return Regex token object.
     */
    public RegexToken valueOfPattern(final int start, final int length) {
        // Save the current position.
        final int savePosition = position;
        // Reset to beginning of content.
        reset(start);
        // Buffer for recording characters.
        final StringBuilder sb = new StringBuilder(length);

        // Skip /.
        skip(1);
        boolean inBrackets = false;
        // Scan for closing /, stopping at end of line.
        while (!atEOF() && ch0 != '/' && !isEOL(ch0) || inBrackets) {
            // Skip over escaped character.
            if (ch0 == '\\') {
                sb.append(ch0);
                sb.append(ch1);
                skip(2);
            } else {
                if (ch0 == '[') {
                    inBrackets = true;
                } else if (ch0 == ']') {
                    inBrackets = false;
                }

                // Skip literal character.
                sb.append(ch0);
                skip(1);
            }
        }

        // Get pattern as string.
        final String regex = sb.toString();

        // Skip /.
        skip(1);

        // Options as string: what the token holds after the pattern, which the
        // scan that made the token has already bounded - scanning again for an
        // identifier reads past the end of it, since Java counts a character
        // ECMAScript reads as whitespace as part of a name
        final String options = source.getString(position, start + length - position);

        reset(savePosition);

        // Compile the pattern.
        return new RegexToken(regex, options);
    }

    /**
     * Return true if the given token can be the beginning of a literal.
     *
     * @param token a token
     * @return true if token can start a literal.
     */
    public boolean canStartLiteral(final TokenType token) {
        return token.startsWith('/') || ((scripting || XML_LITERALS) && token.startsWith('<'));
    }

    /**
     * interface to receive line information for multi-line literals.
     */
    protected interface LineInfoReceiver {
        /**
         * Receives line information
         * @param line last line number
         * @param linePosition position of last line
         */
        public void lineInfo(int line, int linePosition);
    }

    /**
     * Check whether the given token represents the beginning of a literal. If so scan
     * the literal and return <code>true</code>, otherwise return false.
     *
     * @param token the token.
     * @param startTokenType the token type.
     * @param lir LineInfoReceiver that receives line info for multi-line string literals.
     * @return True if a literal beginning with startToken was found and scanned.
     */
    protected boolean scanLiteral(final long token, final TokenType startTokenType, final LineInfoReceiver lir) {
        // Check if it can be a literal.
        if (!canStartLiteral(startTokenType)) {
            return false;
        }
        // We break on ambiguous tokens so if we already moved on it can't be a literal.
        if (stream.get(stream.last()) != token) {
            return false;
        }

        // Record current position in case multiple heredocs start on this line - see JDK-8073653
        final State state = saveState();
        // Rewind to token start position
        reset(Token.descPosition(token));

        if (ch0 == '/') {
            return scanRegEx();
        } else if (ch0 == '<') {
            if (ch1 == '<') {
                return scanHereString(lir, state);
            } else if (Character.isJavaIdentifierStart(ch1)) {
                return scanXMLLiteral();
            }
        }

        return false;
    }

    /**
     * Settles a / or /= the scan of a template substitution paused on: a
     * regular expression where an operand is expected, a division after one.
     * The parser makes the same decision from its grammar for a top-level
     * scan; a nested scan has no parser to hand it to.
     */
    private void resolveSlash() {
        final long token = stream.get(stream.last());
        final TokenType type = Token.descType(token);
        if (type != DIV && type != ASSIGN_DIV) {
            return;
        }
        if (endsOperand(Token.descType(stream.get(stream.last() - 1)))) {
            return;
        }
        stream.removeLast();
        reset(Token.descPosition(token));
        if (!scanRegEx()) {
            // not a regular expression after all: the operator, back
            skip(type.getLength());
            add(type, position - type.getLength());
        }
    }

    /** Whether an expression can end with a token, so that a / after it divides. */
    private static boolean endsOperand(final TokenType type) {
        switch (type) {
        case RPAREN:
        case RBRACKET:
        case RBRACE:
        case THIS:
        case SUPER:
            return true;
        case TEMPLATE_HEAD:
        case TEMPLATE_MIDDLE:
            return false;
        default:
            return type.getKind() == TokenKind.LITERAL;
        }
    }

    /**
     * Scan over regex literal.
     *
     * @return True if a regex literal.
     */
    private boolean scanRegEx() {
        assert ch0 == '/';
        // Make sure it's not a comment.
        if (ch1 != '/' && ch1 != '*') {
            // Record beginning of literal.
            final int start = position;
            // Skip /.
            skip(1);
            boolean inBrackets = false;

            // Scan for closing /, stopping at end of line.
            while (!atEOF() && (ch0 != '/' || inBrackets) && !isEOL(ch0)) {
                // Skip over escaped character.
                if (ch0 == '\\') {
                    skip(1);
                    if (isEOL(ch0)) {
                        reset(start);
                        return false;
                    }
                } else {
                    if (ch0 == '[') {
                        inBrackets = true;
                    } else if (ch0 == ']') {
                        inBrackets = false;
                    }

                    // Skip literal character.
                }
                skip(1);
            }

            // If regex literal.
            if (ch0 == '/') {
                // Skip /.
                skip(1);

                // Skip over options. A flag is an identifier part, and what
                // ECMAScript counts as one does not include the characters it
                // reads as whitespace - the byte order mark among them, which
                // Java's answer treats as ignorable and so as part of a name
                while (!atEOF() && Character.isJavaIdentifierPart(ch0) && !isJSWhitespace(ch0)
                        || ch0 == '\\' && ch1 == 'u') {
                    skip(1);
                }

                // Add regex token.
                add(REGEX, start);
                // Regex literal detected.
                return true;
            }

            // False start try again.
            reset(start);
        }

        // Regex literal not detected.
        return false;
    }

    /**
     * Convert a digit to a integer.  Can't use Character.digit since we are
     * restricted to ASCII by the spec.
     *
     * @param ch   Character to convert.
     * @param base Numeric base.
     *
     * @return The converted digit or -1 if invalid.
     */
    protected static int convertDigit(final char ch, final int base) {
        int digit;

        if ('0' <= ch && ch <= '9') {
            digit = ch - '0';
        } else if ('A' <= ch && ch <= 'Z') {
            digit = ch - 'A' + 10;
        } else if ('a' <= ch && ch <= 'z') {
            digit = ch - 'a' + 10;
        } else {
            return -1;
        }

        return digit < base ? digit : -1;
    }


    /**
     * Get the value of a hexadecimal numeric sequence.
     *
     * @param length Number of digits.
     * @param type   Type of token to report against.
     * @return Value of sequence or < 0 if no digits.
     */
    /**
     * ES2015 11.8.4: the digits of a braced unicode escape, after the brace.
     *
     * There must be at least one digit, the value must be a code point, and the
     * closing brace must be there.
     *
     * @return the code point, or -1 if the sequence is not one
     */
    private int bracedCodePoint() {
        int value = 0;
        int digits = 0;
        while (convertDigit(ch0, 16) != -1) {
            value = convertDigit(ch0, 16) | value << 4;
            digits++;
            skip(1);
            if (value > Character.MAX_CODE_POINT) {
                error(Lexer.message("invalid.hex"), STRING, position, limit);
                return -1;
            }
        }
        if (digits == 0 || ch0 != '}') {
            error(Lexer.message("invalid.hex"), STRING, position, limit);
            return -1;
        }
        skip(1);
        return value;
    }

    /**
     * The code point a {@code \\u} escape in an identifier names.
     *
     * ES2015 11.6.1 lets an identifier be written with either form of the
     * escape, so {@code \\u{61}bc} and {@code \\u0061bc} are both abc, and the
     * braced one can name a code point outside the basic plane.
     *
     * The two leading characters are already consumed.
     *
     * @return the code point, or -1 if the escape was malformed
     */
    private int identifierEscape() {
        if (ch0 == '{') {
            skip(1);
            return bracedCodePoint();
        }
        return hexSequence(4, TokenType.IDENT);
    }

    private int hexSequence(final int length, final TokenType type) {
        int value = 0;

        for (int i = 0; i < length; i++) {
            final int digit = convertDigit(ch0, 16);

            if (digit == -1) {
                error(Lexer.message("invalid.hex"), type, position, limit);
                return i == 0 ? -1 : value;
            }

            value = digit | value << 4;
            skip(1);
        }

        return value;
    }

    /**
     * Get the value of an octal numeric sequence. This parses up to 3 digits with a maximum value of 255.
     *
     * @return Value of sequence.
     */
    private int octalSequence() {
        int value = 0;

        for (int i = 0; i < 3; i++) {
            final int digit = convertDigit(ch0, 8);

            if (digit == -1) {
                break;
            }
            value = digit | value << 3;
            skip(1);

            if (i == 1 && value >= 32) {
                break;
            }
        }
        return value;
    }

    /**
     * Convert a string to a JavaScript identifier.
     *
     * @param start  Position in source content.
     * @param length Length of token.
     * @return Ident string or null if an error.
     */
    private String valueOfIdent(final int start, final int length) throws RuntimeException {
        // Save the current position.
        final int savePosition = position;
        // End of scan.
        final int end = start + length;
        // Reset to beginning of content.
        reset(start);
        // Buffer for recording characters.
        final StringBuilder sb = new StringBuilder(length);

        // Scan until end of line or end of file.
        while (!atEOF() && position < end && !isEOL(ch0)) {
            // If escape character.
            if (ch0 == '\\' && ch1 == 'u') {
                skip(2);
                final int ch = identifierEscape();
                assert ch >= 0;
                // the check is on the character, so it can only be made for one:
                // truncating a supplementary code point to a char asks about
                // something else entirely, and an escaped U+13000 came out as U+3000
                assert ch > 0xFFFF || !isWhitespace((char)ch);
                sb.appendCodePoint(ch);
            } else {
                // Add regular character.
                sb.append(ch0);
                skip(1);
            }
        }

        // Restore position.
        reset(savePosition);

        return sb.toString();
    }

    /**
     * Scan over and identifier or keyword. Handles identifiers containing
     * encoded Unicode chars.
     *
     * Example:
     *
     * var \u0042 = 44;
     */
    private void scanIdentifierOrKeyword() {
        // Record beginning of identifier.
        final int start = position;
        // Scan identifier.
        final int length = scanIdentifier();
        // Check to see if it is a keyword.
        final TokenType type = TokenLookup.lookupKeyword(content, start, length);
        if (type == FUNCTION && pauseOnFunctionBody) {
            pauseOnNextLeftBrace = true;
        }
        // Add keyword or identifier token.
        add(type, start);
    }

    /**
     * Convert a string to a JavaScript string object.
     *
     * @param start  Position in source content.
     * @param length Length of token.
     * @return JavaScript string object.
     */
    private String valueOfString(final int start, final int length, final boolean strict) throws RuntimeException {
        // Save the current position.
        final int savePosition = position;
        // Calculate the end position.
        final int end = start + length;
        // Reset to beginning of string.
        reset(start);

        // Buffer for recording characters.
        final StringBuilder sb = new StringBuilder(length);

        // Scan until end of string.
        while (position < end) {
            // If escape character.
            if (ch0 == '\\') {
                skip(1);

                final char next = ch0;
                final int afterSlash = position;

                skip(1);

                // Special characters.
                switch (next) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7': {
                    if (strict) {
                        // "\0" itself is allowed in strict mode. Only other 'real'
                        // octal escape sequences are not allowed (eg. "\02", "\31").
                        // See section 7.8.4 String literals production EscapeSequence
                        if (next != '0' || (ch0 >= '0' && ch0 <= '9')) {
                            error(Lexer.message("strict.no.octal"), STRING, position, limit);
                        }
                    }
                    reset(afterSlash);
                    // Octal sequence.
                    final int ch = octalSequence();

                    if (ch < 0) {
                        sb.append('\\');
                        sb.append('x');
                    } else {
                        sb.append((char)ch);
                    }
                    break;
                }
                case '8':
                case '9':
                    // ES2021 B.1.2 NonOctalDecimalEscapeSequence: the escape
                    // stands for the digit itself, and only where the legacy
                    // octal escapes above are also allowed
                    if (strict) {
                        error(Lexer.message("strict.no.legacy.escape"), STRING, position, limit);
                    }
                    sb.append(next);
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case '\'':
                    sb.append('\'');
                    break;
                case '\"':
                    sb.append('\"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '\r': // CR | CRLF
                    if (ch0 == '\n') {
                        skip(1);
                    }
                    // fall through
                case '\n': // LF
                case '\u2028': // LS
                case '\u2029': // PS
                    // continue on the next line, slash-return continues string
                    // literal
                    break;
                case 'x': {
                    // Hex sequence.
                    final int ch = hexSequence(2, STRING);

                    if (ch < 0) {
                        sb.append('\\');
                        sb.append('x');
                    } else {
                        sb.append((char)ch);
                    }
                }
                    break;
                case 'u': {
                    // Unicode sequence, either four digits or ES2015's braced
                    // form, which may name a code point outside the basic plane
                    // and so contributes a surrogate pair rather than one char.
                    if (ch0 == '{') {
                        skip(1);
                        final int codePoint = bracedCodePoint();
                        if (codePoint < 0) {
                            sb.append("\\u{");
                        } else {
                            sb.appendCodePoint(codePoint);
                        }
                        break;
                    }
                    final int ch = hexSequence(4, STRING);

                    if (ch < 0) {
                        sb.append('\\');
                        sb.append('u');
                    } else {
                        sb.append((char)ch);
                    }
                }
                    break;
                case 'v':
                    sb.append('\u000B');
                    break;
                // All other characters.
                default:
                    sb.append(next);
                    break;
                }
            } else if (ch0 == '\r') {
                // Convert CR-LF or CR to LF line terminator.
                sb.append('\n');
                skip(ch1 == '\n' ? 2 : 1);
            } else {
                // Add regular character.
                sb.append(ch0);
                skip(1);
            }
        }

        // Restore position.
        reset(savePosition);

        return sb.toString();
    }

    /**
     * Scan over a string literal.
     * @param add true if we are not just scanning but should actually modify the token stream
     */
    protected void scanString(final boolean add) {
        // Type of string.
        TokenType type = STRING;
        // Record starting quote.
        final char quote = ch0;
        // Skip over quote.
        skip(1);

        // Record beginning of string content.
        final State stringState = saveState();

        // Scan until close quote or end of line.
        while (!atEOF() && ch0 != quote && !isStringLiteralEOL(ch0)) {
            // Skip over escaped character.
            if (ch0 == '\\') {
                type = ESCSTRING;
                skip(1);
                if (isEOL(ch0)) {
                    // Multiline string literal
                    skipEOL(false);
                    continue;
                }
            }
            // Skip literal character.
            skip(1);
        }

        // If close quote.
        if (ch0 == quote) {
            // Skip close quote.
            skip(1);
        } else {
            error(Lexer.message("missing.close.quote"), STRING, position, limit);
        }

        // If not just scanning.
        if (add) {
            // Record end of string.
            stringState.setLimit(position - 1);

            if (scripting && !stringState.isEmpty()) {
                switch (quote) {
                case '"':
                    // Only edit double quoted strings.
                    editString(type, stringState);
                    break;
                case '\'':
                    // Add string token without editing.
                    add(type, stringState.position, stringState.limit);
                    break;
                default:
                    break;
                }
            } else {
                /// Add string token without editing.
                add(type, stringState.position, stringState.limit);
            }
        }
    }

    /**
     * Scan over a template string literal.
     */
    private void scanTemplate() {
        assert ch0 == '`';
        TokenType type = TEMPLATE;

        // Skip over quote and record beginning of string content.
        skip(1);
        State stringState = saveState();

        // Scan until close quote
        while (!atEOF()) {
            // Skip over escaped character.
            if (ch0 == '`') {
                skip(1);
                // Record end of string.
                stringState.setLimit(position - 1);
                add(type == TEMPLATE ? type : TEMPLATE_TAIL, stringState.position, stringState.limit);
                return;
            } else if (ch0 == '$' && ch1 == '{') {
                skip(2);
                stringState.setLimit(position - 2);
                add(type == TEMPLATE ? TEMPLATE_HEAD : type, stringState.position, stringState.limit);

                // scan to RBRACE. lexify pauses after a / or /=, which the
                // parser would otherwise settle from its grammar; nothing
                // resumes a nested lexer, so this one settles it itself.
                final Lexer expressionLexer = new Lexer(this, saveState());
                expressionLexer.templateExpressionOpenBraces = 1;
                expressionLexer.lexify();
                while (expressionLexer.templateExpressionOpenBraces > 0 && !expressionLexer.atEOF()) {
                    expressionLexer.resolveSlash();
                    expressionLexer.lexify();
                }
                restoreState(expressionLexer.saveState());

                // scan next middle or tail of the template literal
                assert ch0 == '}';
                type = TEMPLATE_MIDDLE;

                // Skip over rbrace and record beginning of string content.
                skip(1);
                stringState = saveState();

                continue;
            } else if (ch0 == '\\') {
                skip(1);
                // EscapeSequence
                if (isEOL(ch0)) {
                    // LineContinuation
                    skipEOL(false);
                    continue;
                }
            }  else if (isEOL(ch0)) {
                // LineTerminatorSequence
                skipEOL(false);
                continue;
            }

            // Skip literal character.
            skip(1);
        }

        error(Lexer.message("missing.close.quote"), TEMPLATE, position, limit);
    }

    /**
     * Convert string to number.
     *
     * @param valueString  String to convert.
     * @param radix        Numeric base.
     * @return Converted number.
     */
    private static Number valueOf(final String valueString, final int radix) throws NumberFormatException {
        try {
            return Integer.parseInt(valueString, radix);
        } catch (final NumberFormatException e) {
            if (radix == 10) {
                return Double.valueOf(valueString);
            }

            double value = 0.0;

            for (int i = 0; i < valueString.length(); i++) {
                final char ch = valueString.charAt(i);
                // Preverified, should always be a valid digit.
                final int digit = convertDigit(ch, radix);
                value *= radix;
                value += digit;
            }

            return value;
        }
    }

    /**
     * Scan a number.
     */
    protected void scanNumber() {
        // Record beginning of number.
        final int start = position;
        // Assume value is a decimal.
        TokenType type = DECIMAL;

        // First digit of number.
        int digit = convertDigit(ch0, 10);

        // If number begins with 0x.
        if (digit == 0 && (ch1 == 'x' || ch1 == 'X') && convertDigit(ch2, 16) != -1) {
            // Skip over 0xN.
            skip(3);
            // Skip over remaining digits.
            while (convertDigit(ch0, 16) != -1) {
                skip(1);
            }

            type = HEXADECIMAL;
        } else if (digit == 0 && (ch1 == 'o' || ch1 == 'O') && convertDigit(ch2, 8) != -1) {
            // Skip over 0oN.
            skip(3);
            // Skip over remaining digits.
            while (convertDigit(ch0, 8) != -1) {
                skip(1);
            }

            type = OCTAL;
        } else if (digit == 0 && (ch1 == 'b' || ch1 == 'B') && convertDigit(ch2, 2) != -1) {
            // Skip over 0bN.
            skip(3);
            // Skip over remaining digits.
            while (convertDigit(ch0, 2) != -1) {
                skip(1);
            }

            type = BINARY_NUMBER;
        } else {
            // Check for possible octal constant.
            boolean octal = digit == 0;
            final boolean leadingZero = digit == 0;
            // Skip first digit if not leading '.'.
            if (digit != -1) {
                skip(1);
            }

            // Skip remaining digits.
            while ((digit = convertDigit(ch0, 10)) != -1) {
                // Check octal only digits.
                octal = octal && digit < 8;
                // Skip digit.
                skip(1);
            }

            if (octal && position - start > 1) {
                type = OCTAL_LEGACY;
            } else if (leadingZero && position - start > 1 && ch0 != '.' && ch0 != 'E' && ch0 != 'e') {
                // "08" - a decimal written with a leading zero, which is what
                // 11.8.3 has no production for outside Annex B and what strict
                // code may not contain
                type = NON_OCTAL_DECIMAL;
            } else if (ch0 == '.' || ch0 == 'E' || ch0 == 'e') {
                // Must be a double.
                if (ch0 == '.') {
                    // Skip period.
                    skip(1);
                    // Skip mantissa.
                    while (convertDigit(ch0, 10) != -1) {
                        skip(1);
                    }
                }

                // Detect exponent.
                if (ch0 == 'E' || ch0 == 'e') {
                    // Skip E.
                    skip(1);
                    // Detect and skip exponent sign.
                    if (ch0 == '+' || ch0 == '-') {
                        skip(1);
                    }
                    // Skip exponent.
                    while (convertDigit(ch0, 10) != -1) {
                        skip(1);
                    }
                }

                type = FLOATING;
            }
        }

        if (Character.isJavaIdentifierStart(ch0)) {
            error(Lexer.message("missing.space.after.number"), type, position, 1);
        }

        // Add number token.
        add(type, start);
    }

    /**
     * Convert a regex token to a token object.
     *
     * @param start  Position in source content.
     * @param length Length of regex token.
     * @return Regex token object.
     */
    XMLToken valueOfXML(final int start, final int length) {
        return new XMLToken(source.getString(start, length));
    }

    /**
     * Scan over a XML token.
     *
     * @return TRUE if is an XML literal.
     */
    private boolean scanXMLLiteral() {
        assert ch0 == '<' && Character.isJavaIdentifierStart(ch1);
        if (XML_LITERALS) {
            // Record beginning of xml expression.
            final int start = position;

            int openCount = 0;

            do {
                if (ch0 == '<') {
                    if (ch1 == '/' && Character.isJavaIdentifierStart(ch2)) {
                        skip(3);
                        openCount--;
                    } else if (Character.isJavaIdentifierStart(ch1)) {
                        skip(2);
                        openCount++;
                    } else if (ch1 == '?') {
                        skip(2);
                    } else if (ch1 == '!' && ch2 == '-' && ch3 == '-') {
                        skip(4);
                    } else {
                        reset(start);
                        return false;
                    }

                    while (!atEOF() && ch0 != '>') {
                        if (ch0 == '/' && ch1 == '>') {
                            openCount--;
                            skip(1);
                            break;
                        } else if (ch0 == '\"' || ch0 == '\'') {
                            scanString(false);
                        } else {
                            skip(1);
                        }
                    }

                    if (ch0 != '>') {
                        reset(start);
                        return false;
                    }

                    skip(1);
                } else if (atEOF()) {
                    reset(start);
                    return false;
                } else {
                    skip(1);
                }
            } while (openCount > 0);

            add(XML, start);
            return true;
        }

        return false;
    }

    /**
     * Scan over identifier characters.
     *
     * @return Length of identifier or zero if none found.
     */
    private int scanIdentifier() {
        final int start = position;

        // Make sure first character is valid start character.
        if (ch0 == '\\' && ch1 == 'u') {
            skip(2);
            final int ch = identifierEscape();

            if (!isIdentifierStart(ch)) {
                error(Lexer.message("illegal.identifier.character"), TokenType.IDENT, start, position);
            }
        } else if (!isIdentifierStart(codePointHere())) {
            // Not an identifier.
            return 0;
        }

        // Make sure remaining characters are valid part characters.
        while (!atEOF()) {
            if (ch0 == '\\' && ch1 == 'u') {
                skip(2);
                final int ch = identifierEscape();

                if (!isIdentifierPart(ch)) {
                    error(Lexer.message("illegal.identifier.character"), TokenType.IDENT, start, position);
                }
            } else {
                final int codePoint = codePointHere();
                if (!isIdentifierPart(codePoint)) {
                    break;
                }
                skip(Character.charCount(codePoint));
            }
        }

        // Length of identifier sequence.
        return position - start;
    }

    /**
     * The code point at the read position, which is two characters when it is
     * written as a surrogate pair - an identifier may be spelled with characters
     * outside the basic plane, and testing the halves of one separately says no
     * to every such name.
     */
    private int codePointHere() {
        if (ch0 < Character.MIN_HIGH_SURROGATE) {
            return ch0;
        }
        return Character.isHighSurrogate(ch0) && Character.isLowSurrogate(ch1)
                ? Character.toCodePoint(ch0, ch1)
                : ch0;
    }

    /**
     * ES2015 11.6.1 IdentifierStart: a code point with the Unicode ID_Start
     * property, or one of the two JavaScript adds. Java's own idea of an
     * identifier is close but not the same - it takes currency symbols, which
     * ID_Start does not, and it is asked here about code points rather than
     * characters.
     */
    private static boolean isIdentifierStart(final int codePoint) {
        if (codePoint < 128) {
            // almost every identifier is spelled in this range, and the tables
            // are consulted often enough for it to be worth saying so
            return codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z'
                    || codePoint == '$' || codePoint == '_';
        }
        // UAX #31 takes the Pattern_Syntax characters out of ID_Start, and
        // U+2E2F VERTICAL TILDE is the only letter among them. The JDK's own
        // test predates that and still answers yes.
        return codePoint != VERTICAL_TILDE && Character.isUnicodeIdentifierStart(codePoint);
    }

    /**
     * ES2015 11.6.1 IdentifierPart: ID_Continue, the two JavaScript adds, and
     * the two zero width joiners.
     */
    private static boolean isIdentifierPart(final int codePoint) {
        if (codePoint < 128) {
            return codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z'
                    || codePoint >= '0' && codePoint <= '9' || codePoint == '$' || codePoint == '_';
        }
        return codePoint == 0x200C || codePoint == 0x200D
                || codePoint != VERTICAL_TILDE && Character.isUnicodeIdentifierPart(codePoint);
    }

    /** The one letter UAX #31 excludes from identifiers for being Pattern_Syntax. */
    private static final int VERTICAL_TILDE = 0x2E2F;

    /**
     * Compare two identifiers (in content) for equality.
     *
     * @param aStart  Start of first identifier.
     * @param aLength Length of first identifier.
     * @param bStart  Start of second identifier.
     * @param bLength Length of second identifier.
     * @return True if equal.
     */
    private boolean identifierEqual(final int aStart, final int aLength, final int bStart, final int bLength) {
        if (aLength == bLength) {
            for (int i = 0; i < aLength; i++) {
                if (content[aStart + i] != content[bStart + i]) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Detect if a line starts with a marker identifier.
     *
     * @param identStart  Start of identifier.
     * @param identLength Length of identifier.
     * @return True if detected.
     */
    private boolean hasHereMarker(final int identStart, final int identLength) {
        // Skip any whitespace.
        skipWhitespace(false);

        return identifierEqual(identStart, identLength, position, scanIdentifier());
    }

    /**
     * Lexer to service edit strings.
     */
    private static class EditStringLexer extends Lexer {
        /** Type of string literals to emit. */
        final TokenType stringType;

        /*
         * Constructor.
         */

        EditStringLexer(final Lexer lexer, final TokenType stringType, final State stringState) {
            super(lexer, stringState);

            this.stringType = stringType;
        }

        /**
         * Lexify the contents of the string.
         */
        @Override
        public void lexify() {
            // Record start of string position.
            int stringStart = position;
            // Indicate that the priming first string has not been emitted.
            boolean primed = false;

            while (!atEOF()) {
                // Honour escapes (should be well formed.)
                if (ch0 == '\\' && stringType == ESCSTRING) {
                    skip(2);

                    continue;
                }

                // If start of expression.
                if (ch0 == '$' && ch1 == '{') {
                    if (!primed || stringStart != position) {
                        if (primed) {
                            add(ADD, stringStart, stringStart + 1);
                        }

                        add(stringType, stringStart, position);
                        primed = true;
                    }

                    // Skip ${
                    skip(2);

                    // Save expression state.
                    final State expressionState = saveState();

                    // Start with one open brace.
                    int braceCount = 1;

                    // Scan for the rest of the string.
                    while (!atEOF()) {
                        // If closing brace.
                        if (ch0 == '}') {
                            // Break only only if matching brace.
                            if (--braceCount == 0) {
                                break;
                            }
                        } else if (ch0 == '{') {
                            // Bump up the brace count.
                            braceCount++;
                        }

                        // Skip to next character.
                        skip(1);
                    }

                    // If braces don't match then report an error.
                    if (braceCount != 0) {
                        error(Lexer.message("edit.string.missing.brace"), LBRACE, expressionState.position - 1, 1);
                    }

                    // Mark end of expression.
                    expressionState.setLimit(position);
                    // Skip closing brace.
                    skip(1);

                    // Start next string.
                    stringStart = position;

                    // Concatenate expression.
                    add(ADD, expressionState.position, expressionState.position + 1);
                    add(LPAREN, expressionState.position, expressionState.position + 1);

                    // Scan expression.
                    final Lexer lexer = new Lexer(this, expressionState);
                    lexer.lexify();

                    // Close out expression parenthesis.
                    add(RPAREN, position - 1, position);

                    continue;
                }

                // Next character in string.
                skip(1);
            }

            // If there is any unemitted string portion.
            if (stringStart != limit) {
                // Concatenate remaining string.
                if (primed) {
                    add(ADD, stringStart, 1);
                }

                add(stringType, stringStart, limit);
            }
        }

    }

    /**
     * Edit string for nested expressions.
     *
     * @param stringType  Type of string literals to emit.
     * @param stringState State of lexer at start of string.
     */
    private void editString(final TokenType stringType, final State stringState) {
        // Use special lexer to scan string.
        final EditStringLexer lexer = new EditStringLexer(this, stringType, stringState);
        lexer.lexify();

        // Need to keep lexer informed.
        last = stringType;
    }

    /**
     * Scan over a here string.
     *
     * @return TRUE if is a here string.
     */
    private boolean scanHereString(final LineInfoReceiver lir, final State oldState) {
        assert ch0 == '<' && ch1 == '<';
        if (scripting) {
            // Record beginning of here string.
            final State saved = saveState();

            // << or <<<
            final boolean excludeLastEOL = ch2 != '<';

            if (excludeLastEOL) {
                skip(2);
            } else {
                skip(3);
            }

            // Scan identifier. It might be quoted, indicating that no string editing should take place.
            final char quoteChar = ch0;
            final boolean noStringEditing = quoteChar == '"' || quoteChar == '\'';
            if (noStringEditing) {
                skip(1);
            }
            final int identStart = position;
            final int identLength = scanIdentifier();
            if (noStringEditing) {
                if (ch0 != quoteChar) {
                    error(Lexer.message("here.non.matching.delimiter"), last, position, position);
                    restoreState(saved);
                    return false;
                }
                skip(1);
            }

            // Check for identifier.
            if (identLength == 0) {
                // Treat as shift.
                restoreState(saved);

                return false;
            }

            // Record rest of line.
            final State restState = saveState();
            // keep line number updated
            int lastLine = line;

            skipLine(false);
            lastLine++;
            int lastLinePosition = position;
            restState.setLimit(position);

            if (oldState.position > position) {
                restoreState(oldState);
                skipLine(false);
            }

            // Record beginning of string.
            final State stringState = saveState();
            int stringEnd = position;

            // Hunt down marker.
            while (!atEOF()) {
                // Skip any whitespace.
                skipWhitespace(false);

                //handle trailing blank lines
                lastLinePosition = position;
                stringEnd = position;

                if (hasHereMarker(identStart, identLength)) {
                    break;
                }

                skipLine(false);
                lastLine++;
                lastLinePosition = position;
                stringEnd = position;
            }

            // notify last line information
            lir.lineInfo(lastLine, lastLinePosition);

            // Record end of string.
            stringState.setLimit(stringEnd);

            // If marker is missing.
            if (stringState.isEmpty() || atEOF()) {
                error(Lexer.message("here.missing.end.marker", source.getString(identStart, identLength)), last, position, position);
                restoreState(saved);

                return false;
            }

            // Remove last end of line if specified.
            if (excludeLastEOL) {
                // Handles \n.
                if (content[stringEnd - 1] == '\n') {
                    stringEnd--;
                }

                // Handles \r and \r\n.
                if (content[stringEnd - 1] == '\r') {
                    stringEnd--;
                }

                // Update end of string.
                stringState.setLimit(stringEnd);
            }

            // Edit string if appropriate.
            if (!noStringEditing && !stringState.isEmpty()) {
                editString(STRING, stringState);
            } else {
                // Add here string.
                add(STRING, stringState.position, stringState.limit);
            }

            // Scan rest of original line.
            final Lexer restLexer = new Lexer(this, restState);

            restLexer.lexify();

            return true;
        }

        return false;
    }

    /**
     * Breaks source content down into lex units, adding tokens to the token
     * stream. The routine scans until the stream buffer is full. Can be called
     * repeatedly until EOF is detected.
     */
    public void lexify() {
        while (!stream.isFull() || nested) {
            // Skip over whitespace.
            skipWhitespace(true);

            // Detect end of file.
            if (atEOF()) {
                if (!nested) {
                    // Add an EOF token at the end.
                    add(EOF, position);
                }

                break;
            }

            // Check for comments. Note that we don't scan for regexp and other literals here as
            // we may not have enough context to distinguish them from similar looking operators.
            // Instead we break on ambiguous operators below and let the parser decide.
            if (ch0 == '/' && skipComments()) {
                continue;
            }

            if (scripting && ch0 == '#' && skipComments()) {
                continue;
            }

            // B.1.1's HTML-like comments. An open comment runs to the end of
            // the line wherever it stands; a close comment only where nothing
            // but whitespace and same-line comments precedes it.
            if (annexB && (ch0 == '<' || ch0 == '-') && skipHTMLComment()) {
                continue;
            }

            // TokenType for lookup of delimiter or operator.
            TokenType type;

            if (ch0 == '.' && convertDigit(ch1, 10) != -1) {
                // '.' followed by digit.
                // Scan and add a number.
                scanNumber();
            } else if ((type = TokenLookup.lookupOperator(ch0, ch1, ch2, ch3)) != null) {
                if (templateExpressionOpenBraces > 0) {
                    if (type == LBRACE) {
                        templateExpressionOpenBraces++;
                    } else if (type == RBRACE) {
                        if (--templateExpressionOpenBraces == 0) {
                            break;
                        }
                    }
                }

                // Get the number of characters in the token.
                final int typeLength = type.getLength();
                // Skip that many characters.
                skip(typeLength);
                // Add operator token.
                add(type, position - typeLength);
                // Some operator tokens also mark the beginning of regexp, XML, or here string literals.
                // We break to let the parser decide what it is.
                if (canStartLiteral(type)) {
                    break;
                } else if (type == LBRACE && pauseOnNextLeftBrace) {
                    pauseOnNextLeftBrace = false;
                    break;
                }
            } else if (isIdentifierStart(codePointHere()) || ch0 == '\\' && ch1 == 'u') {
                // Scan and add identifier or keyword.
                scanIdentifierOrKeyword();
            } else if (isStringDelimiter(ch0)) {
                // Scan and add a string.
                scanString(true);
            } else if (Character.isDigit(ch0)) {
                // Scan and add a number.
                scanNumber();
            } else if (isTemplateDelimiter(ch0)) {
                // Backquote is a template literal. It used to mean "run this as a
                // shell command" under -scripting; ECMAScript 2015 gives the
                // character to template literals, and that wins.
                scanTemplate();
            } else {
                // Don't recognize this character.
                skip(1);
                add(ERROR, position - 1);
            }
        }
    }

    /**
     * Return value of token given its token descriptor.
     *
     * @param token  Token descriptor.
     * @return JavaScript value.
     */
    Object getValueOf(final long token, final boolean strict) {
        final int start = Token.descPosition(token);
        final int len   = Token.descLength(token);

        switch (Token.descType(token)) {
        case DECIMAL:
            return Lexer.valueOf(source.getString(start, len), 10); // number
        case HEXADECIMAL:
            return Lexer.valueOf(source.getString(start + 2, len - 2), 16); // number
        case OCTAL_LEGACY:
            return Lexer.valueOf(source.getString(start, len), 8); // number
        case NON_OCTAL_DECIMAL:
            return Lexer.valueOf(source.getString(start, len), 10); // number
        case OCTAL:
            return Lexer.valueOf(source.getString(start + 2, len - 2), 8); // number
        case BINARY_NUMBER:
            return Lexer.valueOf(source.getString(start + 2, len - 2), 2); // number
        case FLOATING:
            final String str   = source.getString(start, len);
            final double value = Double.parseDouble(str);
            if (str.indexOf('.') != -1) {
                return value; //number
            }
            //anything without an explicit decimal point is still subject to a
            //"representable as int or long" check. Then the programmer does not
            //explicitly code something as a double. For example new Color(int, int, int)
            //and new Color(float, float, float) will get ambiguous for cases like
            //new Color(1.0, 1.5, 1.5) if we don't respect the decimal point.
            //yet we don't want e.g. 1e6 to be a double unnecessarily
            if (JSType.isStrictlyRepresentableAsInt(value)) {
                return (int)value;
            }
            return value;
        case STRING:
        case DIRECTIVE_COMMENT:
            return source.getString(start, len); // String
        case ESCSTRING:
            return valueOfString(start, len, strict); // String
        case IDENT:
            return valueOfIdent(start, len); // String
        case REGEX:
            return valueOfPattern(start, len); // RegexToken::LexerToken
        case TEMPLATE:
        case TEMPLATE_HEAD:
        case TEMPLATE_MIDDLE:
        case TEMPLATE_TAIL:
            return valueOfString(start, len, true); // String
        case XML:
            return valueOfXML(start, len); // XMLToken::LexerToken
        default:
            break;
        }

        return null;
    }

    /**
     * ES2018 template literal revision: the cooked value of a <em>tagged</em>
     * template string part, or {@code null} (meaning the cooked value is
     * {@code undefined}) when the part contains an invalid escape sequence.
     *
     * A tagged template tolerates an escape an untagged template rejects - the
     * raw text is still available - so this catches the {@link ParserException}
     * {@link #valueOfString} would throw and reports it as an absent cooked
     * value instead. Untagged templates keep using the strict path and still
     * fail on an invalid escape.
     *
     * @param token template string token
     * @return the cooked string, or null if an escape made it invalid
     */
    public String valueOfTaggedTemplateString(final long token) {
        final int start = Token.descPosition(token);
        final int len   = Token.descLength(token);
        final int savePosition = position;
        try {
            return valueOfString(start, len, true);
        } catch (final ParserException e) {
            return null;
        } finally {
            reset(savePosition);
        }
    }

    /**
     * Get the raw string value of a template literal string part.
     *
     * @param token template string token
     * @return raw string
     */
    public String valueOfRawString(final long token) {
        final int start  = Token.descPosition(token);
        final int length = Token.descLength(token);

        // Save the current position.
        final int savePosition = position;
        // Calculate the end position.
        final int end = start + length;
        // Reset to beginning of string.
        reset(start);

        // Buffer for recording characters.
        final StringBuilder sb = new StringBuilder(length);

        // Scan until end of string.
        while (position < end) {
            if (ch0 == '\r') {
                // Convert CR-LF or CR to LF line terminator.
                sb.append('\n');
                skip(ch1 == '\n' ? 2 : 1);
            } else {
                // Add regular character.
                sb.append(ch0);
                skip(1);
            }
        }

        // Restore position.
        reset(savePosition);

        return sb.toString();
    }

    /**
     * Get the correctly localized error message for a given message id format arguments
     * @param msgId message id
     * @param args  format arguments
     * @return message
     */
    protected static String message(final String msgId, final String... args) {
        return ECMAErrors.getMessage("lexer.error." + msgId, args);
    }

    /**
     * Generate a runtime exception
     *
     * @param message       error message
     * @param type          token type
     * @param start         start position of lexed error
     * @param length        length of lexed error
     * @throws ParserException  unconditionally
     */
    protected void error(final String message, final TokenType type, final int start, final int length) throws ParserException {
        final long token     = Token.toDesc(type, start, length);
        final int  pos       = Token.descPosition(token);
        final int  lineNum   = source.getLine(pos);
        final int  columnNum = source.getColumn(pos);
        final String formatted = ErrorManager.format(message, source, lineNum, columnNum, token);
        throw new ParserException(JSErrorType.SYNTAX_ERROR, formatted, source, lineNum, columnNum, token);
    }

    /**
     * Helper class for Lexer tokens, e.g XML or RegExp tokens.
     * This is the abstract superclass
     */
    public static abstract class LexerToken implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String expression;

        /**
         * Constructor
         * @param expression token expression
         */
        protected LexerToken(final String expression) {
            this.expression = expression;
        }

        /**
         * Get the expression
         * @return expression
         */
        public String getExpression() {
            return expression;
        }
    }

    /**
     * Temporary container for regular expressions.
     */
    public static class RegexToken extends LexerToken {
        private static final long serialVersionUID = 1L;

        /** Options. */
        private final String options;

        /**
         * Constructor.
         *
         * @param expression  regexp expression
         * @param options     regexp options
         */
        public RegexToken(final String expression, final String options) {
            super(expression);
            this.options = options;
        }

        /**
         * Get regexp options
         * @return options
         */
        public String getOptions() {
            return options;
        }

        @Override
        public String toString() {
            return '/' + getExpression() + '/' + options;
        }
    }

    /**
     * Temporary container for XML expression.
     */
    public static class XMLToken extends LexerToken {
        private static final long serialVersionUID = 1L;

        /**
         * Constructor.
         *
         * @param expression  XML expression
         */
        public XMLToken(final String expression) {
            super(expression);
        }
    }
}
