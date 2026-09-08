/*
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.runtime.regexp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import org.monflabs.nashorn.internal.parser.Lexer;
import org.monflabs.nashorn.internal.parser.Scanner;
import org.monflabs.nashorn.internal.runtime.BitVector;

/**
 * Scan a JavaScript regexp, converting to Java regex if necessary.
 *
 */
final class RegExpScanner extends Scanner {

    /**
     * String builder used to rewrite the pattern for the currently used regexp factory.
     */
    private final StringBuilder sb;

    /** Expected token table */
    private final Map<Character, Integer> expected = new HashMap<>();

    /** Capturing parenthesis that have been found so far. */
    private final List<Capture> caps = new LinkedList<>();

    /** Forward references to capturing parenthesis to be resolved later.*/
    private final LinkedList<Integer> forwardReferences = new LinkedList<>();

    /** Current level of zero-width negative lookahead assertions. */
    private int negLookaheadLevel;

    /** Sequential id of current top-level zero-width negative lookahead assertion. */
    private int negLookaheadGroup;

    /** Are we currently inside a character class? */
    private boolean inCharClass = false;

    /**
     * ES2018 named capture groups: the (decoded) group name to the 1-based
     * capture indices bearing it (more than one only for a name repeated
     * across disjoint alternatives), in source order. Empty unless the pattern uses {@code
     * (?<name>...)}.
     */
    private final java.util.LinkedHashMap<String, java.util.List<Integer>> namedGroups = new java.util.LinkedHashMap<>();

    /**
     * Every named group in the whole pattern to its 1-based capture index,
     * computed up front so a {@code \k<name>} that references a group defined
     * later resolves, and so {@code \k} is known to be a named backreference
     * (ES2018) rather than, under Annex B, a legacy identity escape.
     */
    private final java.util.Map<String, java.util.List<Integer>> namedGroupIndices;

    /** Whether the whole pattern contains at least one named group. */
    private final boolean hasNamedGroups;

    /** Are we currently inside a negated character class? */
    private boolean inNegativeClass = false;

    private static final String NON_IDENT_ESCAPES = "$^*+(){}[]|\\.?-";

    /** ES2015 21.2.1 SyntaxCharacter, the only things a unicode pattern may escape. */
    private static final String SYNTAX_CHARACTERS = "^$\\.*+?()[]{}|";

    private static class Capture {
        /** Zero-width negative lookaheads enclosing the capture. */
        private final int negLookaheadLevel;
        /** Sequential id of top-level negative lookaheads containing the capture. */
        private  final int negLookaheadGroup;

        Capture(final int negLookaheadGroup, final int negLookaheadLevel) {
            this.negLookaheadGroup = negLookaheadGroup;
            this.negLookaheadLevel = negLookaheadLevel;
        }

        /**
         * Returns true if this Capture can be referenced from the position specified by the
         * group and level parameters. This is the case if either the group is not within
         * a negative lookahead, or the position of the referrer is in the same negative lookahead.
         *
         * @param group current negative lookahead group
         * @param level current negative lokahead level
         * @return true if this capture group can be referenced from the given position
         */
        boolean canBeReferencedFrom(final int group, final int level) {
            return this.negLookaheadLevel == 0 || (group == this.negLookaheadGroup && level >= this.negLookaheadLevel);
        }

    }

    /**
     * Constructor
     * @param string the JavaScript regexp to parse
     */
    /**
     * Whether the pattern was written with the unicode flag, which ES2015
     * 21.2.1 gives a stricter grammar: what the ordinary grammar tolerates for
     * the sake of the web - an escape before an arbitrary character, an octal
     * escape, a brace that is not a quantifier - is an error here.
     */
    private final boolean unicode;

    /**
     * Whether ECMA-262 Annex B's extensions to the pattern grammar are
     * recognised: B.1.4 keeps the legacy octal escape, lets a character class
     * hold a dash beside a class escape, and lets an assertion be quantified.
     */
    private final boolean annexB;

    /**
     * Whether the pattern was written with the ES2024 unicodeSets ({@code v})
     * flag, which replaces the character-class grammar with the class-set one:
     * nested classes, the union/intersection/difference operators and string
     * literals. Implies {@link #unicode}.
     */
    private final boolean unicodeSets;

    /** Whether the assertion just read was a lookahead, which B.1.4 lets a quantifier follow. */
    private boolean quantifiableAssertion;

    private RegExpScanner(final String string, final boolean unicode, final boolean unicodeSets,
            final boolean annexB) {
        super(string);
        this.unicode = unicode;
        this.unicodeSets = unicodeSets;
        this.annexB = annexB;
        this.namedGroupIndices = collectNamedGroups(string);
        this.hasNamedGroups = !namedGroupIndices.isEmpty();
        sb = new StringBuilder(limit);
        reset(0);
        expected.put(']', 0);
        expected.put('}', 0);
    }

    /**
     * Pre-scan the whole pattern for its named groups, mapping each (decoded)
     * name to the 1-based capture indices bearing it. Capturing groups are counted in source
     * order - a plain {@code (} and a named {@code (?<name>} each take an index,
     * while {@code (?:}, lookahead and lookbehind do not. Escapes and character
     * classes are respected. The name -> indices map lets a forward
     * {@code \k<name>} resolve and {@code .groups} be built.
     *
     * <p>This is also where the ES2025 duplicate-name rule is enforced: a name
     * may be borne by several groups only if no two of them can match in the
     * same attempt, i.e. they are in different alternatives of some disjunction.
     * Each open group (and the top level) is a frame carrying the names of its
     * current alternative and the union over its earlier alternatives; a
     * {@code |} starts a fresh alternative, a {@code )} folds a closed group's
     * names into the enclosing alternative, and a name that lands in an
     * alternative already holding it is two definitions in one alternative - a
     * SyntaxError.
     */
    private static java.util.Map<String, java.util.List<Integer>> collectNamedGroups(final String s) {
        final java.util.LinkedHashMap<String, java.util.List<Integer>> map = new java.util.LinkedHashMap<>();
        final java.util.ArrayDeque<java.util.Set<String>> altNames = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<java.util.Set<String>> groupNames = new java.util.ArrayDeque<>();
        altNames.push(new java.util.HashSet<>());
        groupNames.push(new java.util.HashSet<>());
        int index = 0;
        boolean escaped = false;
        boolean inClass = false;
        int i = 0;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                i++;
            } else if (c == '\\') {
                escaped = true;
                i++;
            } else if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                i++;
            } else if (c == '[') {
                inClass = true;
                i++;
            } else if (c == '|') {
                // a new alternative in the innermost open group: the names seen
                // so far join the union and the fresh alternative starts empty
                groupNames.peek().addAll(altNames.peek());
                altNames.peek().clear();
                i++;
            } else if (c == ')') {
                if (altNames.size() > 1) {
                    // close the innermost group: its names (over all its
                    // alternatives) belong to the enclosing alternative,
                    // concatenated with whatever else that alternative holds
                    final java.util.Set<String> closedAlt = altNames.pop();
                    final java.util.Set<String> closedAll = groupNames.pop();
                    closedAll.addAll(closedAlt);
                    for (final String n : closedAll) {
                        if (!altNames.peek().add(n)) {
                            throw new RuntimeException("Duplicate capture group name: " + n);
                        }
                    }
                }
                // an unbalanced ')' is left for the main scan to reject
                i++;
            } else if (c == '(') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '?') {
                    final char c2 = i + 2 < s.length() ? s.charAt(i + 2) : '\0';
                    final char c3 = i + 3 < s.length() ? s.charAt(i + 3) : '\0';
                    if (c2 == '<' && c3 != '=' && c3 != '!') {
                        // (?<name> - a capturing group; read the name
                        index++;
                        final StringBuilder name = new StringBuilder();
                        int j = i + 3;
                        while (j < s.length() && s.charAt(j) != '>') {
                            if (s.charAt(j) == '\\' && j + 1 < s.length() && s.charAt(j + 1) == 'u') {
                                j += 2;
                                if (j < s.length() && s.charAt(j) == '{') {
                                    j++;
                                    int v = 0;
                                    while (j < s.length() && Character.digit(s.charAt(j), 16) != -1) {
                                        v = (v << 4) | Character.digit(s.charAt(j), 16);
                                        j++;
                                    }
                                    if (j < s.length() && s.charAt(j) == '}') {
                                        j++;
                                    }
                                    name.appendCodePoint(v);
                                } else {
                                    int v = 0;
                                    for (int k = 0; k < 4 && j < s.length() && Character.digit(s.charAt(j), 16) != -1; k++) {
                                        v = (v << 4) | Character.digit(s.charAt(j), 16);
                                        j++;
                                    }
                                    name.appendCodePoint(v);
                                }
                            } else {
                                name.append(s.charAt(j));
                                j++;
                            }
                        }
                        final String nameStr = name.toString();
                        // the name belongs to the enclosing alternative
                        if (!altNames.peek().add(nameStr)) {
                            throw new RuntimeException("Duplicate capture group name: " + nameStr);
                        }
                        map.computeIfAbsent(nameStr, k -> new java.util.ArrayList<>()).add(index);
                        i = j; // at '>' or end
                    }
                    // (?:, (?=, (?!, (?<=, (?<!, (?ims: are non-capturing
                    altNames.push(new java.util.HashSet<>());
                    groupNames.push(new java.util.HashSet<>());
                    i++;
                } else {
                    index++; // plain capturing group
                    altNames.push(new java.util.HashSet<>());
                    groupNames.push(new java.util.HashSet<>());
                    i++;
                }
            } else {
                i++;
            }
        }
        return map;
    }

    /**
     * Read a RegExpIdentifierName up to the closing {@code >} (which is
     * consumed), decoding {@code \\u} escapes so that {@code (?<\\u0061>)} and
     * {@code (?<a>)} are the same name. The name is validated as a
     * RegExpIdentifierName. The decoded name is returned.
     */
    private String scanGroupName() {
        final StringBuilder name = new StringBuilder();
        while (!atEOF() && ch0 != '>') {
            if (ch0 == '\\' && ch1 == 'u') {
                skip(2);
                final int cp;
                if (ch0 == '{') {
                    skip(1);
                    int v = 0;
                    boolean any = false;
                    while (isHexDigit(ch0)) {
                        v = (v << 4) | Character.digit(ch0, 16);
                        any = true;
                        skip(1);
                    }
                    if (!any || ch0 != '}' || v > Character.MAX_CODE_POINT) {
                        throw new RuntimeException("Invalid group name");
                    }
                    skip(1);
                    cp = v;
                } else {
                    int v = 0;
                    for (int i = 0; i < 4; i++) {
                        if (!isHexDigit(ch0)) {
                            throw new RuntimeException("Invalid group name");
                        }
                        v = (v << 4) | Character.digit(ch0, 16);
                        skip(1);
                    }
                    cp = v;
                }
                name.appendCodePoint(cp);
            } else {
                name.appendCodePoint(Character.codePointAt(new char[] { ch0, ch1 }, 0));
                skip(Character.isHighSurrogate(ch0) && Character.isLowSurrogate(ch1) ? 2 : 1);
            }
        }
        if (ch0 != '>' || name.length() == 0) {
            throw new RuntimeException("Invalid or empty group name");
        }
        skip(1); // consume '>'
        final String result = name.toString();
        validateGroupName(result);
        return result;
    }

    /**
     * A RegExpIdentifierName: the first code point is an IdentifierStart (or
     * {@code $}/{@code _}), the rest IdentifierPart (or {@code $}/{@code _} or a
     * zero-width joiner). Anything else is a syntax error.
     */
    private static void validateGroupName(final String name) {
        final int first = name.codePointAt(0);
        if (!(first == '$' || first == '_' || Character.isUnicodeIdentifierStart(first))) {
            throw new RuntimeException("Invalid group name start: " + name);
        }
        int i = Character.charCount(first);
        while (i < name.length()) {
            final int cp = name.codePointAt(i);
            if (!(cp == '$' || cp == '_' || cp == 0x200C || cp == 0x200D || Character.isUnicodeIdentifierPart(cp))) {
                throw new RuntimeException("Invalid group name character: " + name);
            }
            i += Character.charCount(cp);
        }
    }

    private void processForwardReferences() {

        final Iterator<Integer> iterator = forwardReferences.descendingIterator();
        while (iterator.hasNext()) {
            final int pos = iterator.next();
            final int num = iterator.next();
            if (num > caps.size()) {
                if (unicode) {
                    throw new RuntimeException("Reference to a group that is not there in unicode pattern");
                }
                // Non-existing backreference. If the number begins with a valid octal convert it to
                // Unicode escape and append the rest to a literal character sequence.
                final StringBuilder buffer = new StringBuilder();
                octalOrLiteral(Integer.toString(num), buffer);
                sb.insert(pos, buffer);
            }
        }

        forwardReferences.clear();
    }

    /**
     * Scan a JavaScript regexp string returning a Java safe regex string.
     *
     * @param string
     *            JavaScript regexp string.
     * @return Java safe regex string.
     */
    public static RegExpScanner scan(final String string) {
        return scan(string, false, false, RegExpFactory.annexBEnabled());
    }

    /**
     * Scan a JavaScript regexp string returning a Java safe regex string.
     *
     * @param string  JavaScript regexp string.
     * @param unicode whether it was written with the unicode (u or v) flag
     * @param annexB  whether Annex B's extensions to the grammar are recognised
     * @return Java safe regex string.
     */
    public static RegExpScanner scan(final String string, final boolean unicode, final boolean annexB) {
        return scan(string, unicode, false, annexB);
    }

    /**
     * Scan a JavaScript regexp string returning a Java safe regex string.
     *
     * @param string      JavaScript regexp string.
     * @param unicode     whether it is in unicode (code-point) mode - u or v
     * @param unicodeSets whether it was written with the ES2024 v flag
     * @param annexB      whether Annex B's extensions to the grammar are recognised
     * @return Java safe regex string.
     */
    public static RegExpScanner scan(final String string, final boolean unicode, final boolean unicodeSets,
            final boolean annexB) {
        final RegExpScanner scanner;
        try {
            // the constructor pre-scans for named groups and can reject a
            // duplicate name; that is a syntax error like any other
            scanner = new RegExpScanner(string, unicode, unicodeSets, annexB);
        } catch (final RuntimeException e) {
            throw new PatternSyntaxException(e.getMessage(), string, 0);
        }

        try {
            scanner.disjunction();
        } catch (final Exception e) {
            throw new PatternSyntaxException(e.getMessage(), string, scanner.position);
        }

        // Throw syntax error unless we parsed the entire JavaScript regexp without syntax errors
        if (scanner.position != string.length()) {
            final String p = scanner.getStringBuilder().toString();
            throw new PatternSyntaxException(string, p, p.length() + 1);
        }

        // A forward reference is only known to be one once the whole pattern has
        // been read, so this waits for the scan to finish - but what it rejects
        // it rejects as a syntax error, like everything the scan itself rejects
        try {
            scanner.processForwardReferences();
        } catch (final RuntimeException e) {
            throw new PatternSyntaxException(e.getMessage(), string, scanner.position);
        }

        return scanner;
    }

    /**
     * The ES2018 named capture groups of this pattern: the (decoded) group name
     * to its 1-based capture index, in source order. Empty if there are none.
     *
     * @return the named-group map
     */
    java.util.Map<String, java.util.List<Integer>> getNamedGroups() {
        return namedGroups;
    }

    final StringBuilder getStringBuilder() {
        return sb;
    }

    String getJavaPattern() {
        return sb.toString();
    }

    BitVector getGroupsInNegativeLookahead() {
        BitVector vec = null;
        for (int i = 0; i < caps.size(); i++) {
            final Capture cap = caps.get(i);
            if (cap.negLookaheadLevel > 0) {
                if (vec == null) {
                    vec = new BitVector(caps.size() + 1);
                }
                vec.set(i + 1);
            }
        }
        return vec;
    }

    /**
     * Commit n characters to the builder and to a given token
     * @param n     Number of characters.
     * @return Committed token
     */
    private boolean commit(final int n) {
        switch (n) {
        case 1:
            sb.append(ch0);
            skip(1);
            break;
        case 2:
            sb.append(ch0);
            sb.append(ch1);
            skip(2);
            break;
        case 3:
            sb.append(ch0);
            sb.append(ch1);
            sb.append(ch2);
            skip(3);
            break;
        default:
            assert false : "Should not reach here";
        }

        return true;
    }

    /**
     * Restart the buffers back at an earlier position.
     *
     * @param startIn
     *            Position in the input stream.
     * @param startOut
     *            Position in the output stream.
     */
    private void restart(final int startIn, final int startOut) {
        reset(startIn);
        sb.setLength(startOut);
    }

    private void push(final char ch) {
        expected.put(ch, expected.get(ch) + 1);
    }

    private void pop(final char ch) {
        expected.put(ch, Math.min(0, expected.get(ch) - 1));
    }

    /*
     * Recursive descent tokenizer starts below.
     */

    /*
     * Disjunction ::
     *      Alternative
     *      Alternative | Disjunction
     */
    private void disjunction() {
        while (true) {
            alternative();

            if (ch0 == '|') {
                commit(1);
            } else {
                break;
            }
        }
    }

    /*
     * Alternative ::
     *      [empty]
     *      Alternative Term
     */
    private void alternative() {
        while (term()) {
            // do nothing
        }
    }

    /*
     * Term ::
     *      Assertion
     *      Atom
     *      Atom Quantifier
     */
    private boolean term() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (assertion()) {
            if (annexB && !unicode && quantifiableAssertion) {
                // Term :: QuantifiableAssertion Quantifier. The quantifier is
                // optional here only because the assertion is a term on its own
                quantifier(startOut);
            }
            return true;
        }

        if (atom()) {
            quantifier(startOut);
            return true;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * Assertion ::
     *      ^
     *      $
     *      \b
     *      \B
     *      ( ? = Disjunction )
     *      ( ? ! Disjunction )
     */
    private boolean assertion() {
        final int startIn  = position;
        final int startOut = sb.length();
        quantifiableAssertion = false;

        switch (ch0) {
        case '^':
        case '$':
            return commit(1);

        case '\\':
            if (ch1 == 'b' || ch1 == 'B') {
                return commit(2);
            }
            break;

        case '(':
            if (ch1 != '?') {
                break;
            }
            // ES2018 lookbehind: (?<=...) and (?<!...). Both JDK and Joni accept
            // the syntax verbatim; a lookbehind is not a QuantifiableAssertion.
            if (ch2 == '<' && (ch3 == '=' || ch3 == '!')) {
                commit(3); // (?<
                commit(1); // = or !
                disjunction();
                if (ch0 == ')') {
                    return commit(1);
                }
                break;
            }
            if (ch2 != '=' && ch2 != '!') {
                break;
            }
            final boolean isNegativeLookahead = (ch2 == '!');
            commit(3);

            if (isNegativeLookahead) {
                if (negLookaheadLevel == 0) {
                    negLookaheadGroup++;
                }
                negLookaheadLevel++;
            }
            disjunction();
            if (isNegativeLookahead) {
                negLookaheadLevel--;
            }

            if (ch0 == ')') {
                // B.1.4's QuantifiableAssertion is the lookahead pair and
                // nothing else: ^, $ and \b may not be quantified even there
                quantifiableAssertion = true;
                return commit(1);
            }
            break;

        default:
            break;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * Quantifier ::
     *      QuantifierPrefix
     *      QuantifierPrefix ?
     */
    private boolean quantifier(final int atomStart) {
        if (quantifierPrefix(atomStart)) {
            if (ch0 == '?') {
                commit(1);
            }
            if (unmatchable) {
                // the whole term, lazy marker and all: repeating something that
                // is not nothing more times than a string can hold characters
                // matches nothing at all, and the library will not take a count
                // that large in any case
                unmatchable = false;
                sb.setLength(atomStart);
                sb.append("(?!)");
            }
            return true;
        }
        return false;
    }

    /** Set when the term being read cannot match, whatever follows the count. */
    private boolean unmatchable;

    /*
     * QuantifierPrefix ::
     *      *
     *      +
     *      ?
     *      { DecimalDigits }
     *      { DecimalDigits , }
     *      { DecimalDigits , DecimalDigits }
     */
    private boolean quantifierPrefix(final int atomStart) {
        final int startIn  = position;
        final int startOut = sb.length();

        switch (ch0) {
        case '*':
        case '+':
        case '?':
            return commit(1);

        case '{':
            commit(1);

            final int lowerAt = sb.length();
            if (!decimalDigits()) {
                break; // not a quantifier - back out
            }
            final boolean unreachableLower = beyondAnyString(lowerAt);
            push('}');

            int upperAt = -1;
            if (ch0 == ',') {
                commit(1);
                upperAt = sb.length();
                if (!decimalDigits()) {
                    upperAt = -1;
                }
            }

            if (ch0 == '}') {
                pop('}');
                commit(1);
                if (unreachableLower && cannotMatchEmpty(atomStart, startOut)) {
                    unmatchable = true;
                } else if (!unreachableLower && upperAt >= 0 && beyondAnyString(upperAt)) {
                    // an upper bound no string can reach is no upper bound
                    sb.setLength(upperAt);
                    sb.append('}');
                }
            } else {
                if (unicode) {
                    throw new RuntimeException("Incomplete quantifier in unicode pattern");
                }
                // Bad quantifier should be rejected but is accepted by all major engines
                restart(startIn, startOut);
                return false;
            }

            return true;

        default:
            break;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * Atom ::
     *      PatternCharacter
     *      .
     *      \ AtomEscape
     *      CharacterClass
     *      ( Disjunction )
     *      ( ? : Disjunction )
     *
     */
    private boolean atom() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (patternCharacter()) {
            return true;
        }

        if (ch0 == '.') {
            return commit(1);
        }

        if (ch0 == '\\') {
            commit(1);

            if (atomEscape()) {
                return true;
            }
        }

        if (characterClass()) {
            return true;
        }

        if (ch0 == '(') {
            // ES2018 named capture group (?<name>...). Lookbehind (?<=/(?<! is
            // handled in assertion() before we get here. The ES name may be
            // anything JDK's group-name grammar is not, so emit a mangled name
            // and record the ES name -> index for the .groups object.
            if (ch1 == '?' && ch2 == '<' && ch3 != '=' && ch3 != '!') {
                skip(3); // (?<
                final String name = scanGroupName();
                caps.add(new Capture(negLookaheadGroup, negLookaheadLevel));
                // ES2025 allows the same name on more than one group as long as
                // no two share an alternative; the caller-side validation of
                // that is left to the pre-scan, and .groups picks whichever
                // participated. Record every index under the name, keeping the
                // name's enumeration slot at its first (source-order) occurrence.
                namedGroups.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(caps.size());
                // Emit a plain numbered capture: .groups is built from the
                // name -> index map, and \k<name> is emitted as a numbered
                // backreference - so the backend never sees an ES group name
                // (JDK's name grammar is narrower) and forward references work.
                sb.append('(');
                disjunction();
                if (ch0 == ')') {
                    return commit(1);
                }
                restart(startIn, startOut);
                return false;
            }

            // ES2025 pattern modifiers: (?ims-ims:...), (?ims:...), (?-ims:...).
            // i/m/s map straight onto java.util.regex's inline-flag groups.
            if (ch1 == '?' && (ch2 == 'i' || ch2 == 'm' || ch2 == 's' || ch2 == '-')) {
                commit(1); // (
                modifierGroup();
                disjunction();
                if (ch0 == ')') {
                    return commit(1);
                }
                restart(startIn, startOut);
                return false;
            }

            commit(1);
            if (ch0 == '?' && ch1 == ':') {
                commit(2);
            } else {
                caps.add(new Capture(negLookaheadGroup, negLookaheadLevel));
            }

            disjunction();

            if (ch0 == ')') {
                commit(1);
                return true;
            }
        }

        restart(startIn, startOut);
        return false;
    }

    /**
     * ES2025 pattern-modifier header {@code ?ims-ims:}. On entry {@code ch0} is
     * {@code ?}; on return the {@code :} has been consumed. Only {@code i}, {@code m}
     * and {@code s} are allowed, a flag may not repeat within the added or the
     * removed set, and no flag may be both added and removed. The header is
     * emitted verbatim - the JDK engine reads i/m/s inline flags the same way.
     */
    private void modifierGroup() {
        commit(1); // ?
        final boolean[] added = new boolean[128];
        final boolean[] removed = new boolean[128];
        int count = 0;
        while (ch0 == 'i' || ch0 == 'm' || ch0 == 's') {
            if (added[ch0]) {
                throw new RuntimeException("Repeated modifier '" + ch0 + "' in regexp");
            }
            added[ch0] = true;
            count++;
            commit(1);
        }
        if (ch0 == '-') {
            // an empty removal set after the dash is allowed - (?s-:...) adds s
            // and removes nothing - so long as some flag is present overall
            commit(1);
            while (ch0 == 'i' || ch0 == 'm' || ch0 == 's') {
                if (removed[ch0] || added[ch0]) {
                    throw new RuntimeException("Repeated modifier '" + ch0 + "' in regexp");
                }
                removed[ch0] = true;
                count++;
                commit(1);
            }
        }
        if (count == 0) {
            throw new RuntimeException("Empty modifier in regexp");
        }
        if (ch0 != ':') {
            throw new RuntimeException("Invalid modifier in regexp");
        }
        commit(1); // :
    }

    /*
     * PatternCharacter ::
     *      SourceCharacter but not any of: ^$\.*+?()[]{}|
     */
    @SuppressWarnings("fallthrough")
    private boolean patternCharacter() {
        if (atEOF()) {
            return false;
        }

        switch (ch0) {
        case '^':
        case '$':
        case '\\':
        case '.':
        case '*':
        case '+':
        case '?':
        case '(':
        case ')':
        case '[':
        case '|':
            return false;

        case '}':
        case ']':
            final int n = expected.get(ch0);
            if (n != 0) {
                return false;
            }
            if (unicode) {
                // ES2015 21.2.1 leaves these out of ExtendedPatternCharacter
                throw new RuntimeException("Unmatched " + ch0 + " in unicode pattern");
            }

       case '{':
           // if not a valid quantifier escape curly brace to match itself
           // this ensures compatibility with other JS implementations. There is
           // no atom in front of it here, so nothing a count could be rewritten
           // against: the position it would start at is the one it is at.
           if (!quantifierPrefix(sb.length())) {
               if (unicode) {
                   throw new RuntimeException("Incomplete quantifier in unicode pattern");
               }
               sb.append('\\');
               return commit(1);
           }
           return false;

        default:
            return commit(1); // SOURCECHARACTER
        }
    }

    /*
     * AtomEscape ::
     *      DecimalEscape
     *      CharacterEscape
     *      CharacterClassEscape
     */
    private boolean atomEscape() {
        // Note that contrary to ES 5.1 spec we put identityEscape() last because it acts as a catch-all.
        // namedBackReference() sits before it so a \k<name> is not eaten as a literal k.
        return decimalEscape() || characterClassEscape() || characterEscape() || namedBackReference() || identityEscape();
    }

    /**
     * ES2018 named backreference {@code \k<name>}. The backslash has already
     * been emitted by the caller, so {@code ch0} is {@code k}. Only a pattern
     * that contains a named group treats {@code \k} this way; otherwise it is a
     * legacy identity escape (Annex B) and this returns false.
     */
    private boolean namedBackReference() {
        if (!hasNamedGroups || ch0 != 'k') {
            return false;
        }
        if (ch1 != '<') {
            throw new RuntimeException("\\k not followed by a group name");
        }
        skip(2); // k<
        final String name = scanGroupName();
        final java.util.List<Integer> indexList = namedGroupIndices.get(name);
        if (indexList == null) {
            throw new RuntimeException("Reference to undefined group name " + name);
        }
        // The backslash is already emitted by the caller. Emit a numbered
        // backreference, following decimalEscape() exactly: a forward reference
        // (or one to a capture in a negative lookahead) is always undefined, so
        // it is omitted from the output and matches empty. When the name is
        // borne by several groups (only possible in disjoint alternatives), the
        // first is emitted - at most one participates in any match, and a
        // backreference to a non-participating group matches empty regardless.
        final int index = indexList.get(0);
        if (index <= caps.size()) {
            final Capture capture = caps.get(index - 1);
            if (!capture.canBeReferencedFrom(negLookaheadGroup, negLookaheadLevel)) {
                sb.setLength(sb.length() - 1);
            } else {
                sb.append(index);
            }
        } else {
            sb.setLength(sb.length() - 1);
        }
        return true;
    }

    /*
     * CharacterEscape ::
     *      ControlEscape
     *      c ControlLetter
     *      HexEscapeSequence
     *      UnicodeEscapeSequence
     *      IdentityEscape
     */
    private boolean characterEscape() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (controlEscape()) {
            return true;
        }

        if (ch0 == 'c') {
            commit(1);
            if (controlLetter()) {
                return true;
            }
            restart(startIn, startOut);
        }

        if (hexEscapeSequence() || unicodeEscapeSequence()) {
            return true;
        }

        restart(startIn, startOut);
        return false;
    }

    private boolean scanEscapeSequence(final char leader, final int length) {
        final int startIn  = position;
        final int startOut = sb.length();

        if (ch0 != leader) {
            return false;
        }

        commit(1);
        for (int i = 0; i < length; i++) {
            final char ch0l = Character.toLowerCase(ch0);
            if ((ch0l >= 'a' && ch0l <= 'f') || isDecimalDigit(ch0)) {
                commit(1);
            } else {
                restart(startIn, startOut);
                return false;
            }
        }

        return true;
    }

    private boolean hexEscapeSequence() {
        return scanEscapeSequence('x', 2);
    }

    private boolean unicodeEscapeSequence() {
        if (unicode && ch0 == 'u' && ch1 == '{') {
            return bracedCodePointEscape();
        }
        return scanEscapeSequence('u', 4);
    }

    /**
     * ES2015 21.2.1 RegExpUnicodeEscapeSequence: a braced escape naming a code
     * point outright. The JDK's engine spells the same thing with an x rather
     * than a u, so that is what is emitted; the value has to be a code point,
     * which is what makes an overlong one a syntax error rather than a literal
     * brace.
     */
    private boolean bracedCodePointEscape() {
        final int startIn = position;
        final int startOut = sb.length();

        commit(2);
        int value = 0;
        int digits = 0;
        while (isHexDigit(ch0)) {
            value = value * 16 + Character.digit(ch0, 16);
            digits++;
            if (value > Character.MAX_CODE_POINT) {
                throw new RuntimeException("Code point out of range in unicode pattern");
            }
            skip(1);
        }
        if (digits == 0 || ch0 != '}') {
            restart(startIn, startOut);
            throw new RuntimeException("Invalid unicode escape in unicode pattern");
        }
        skip(1);
        sb.setLength(startOut);
        // the backslash the caller committed is still there
        sb.append("x{").append(Integer.toHexString(value)).append('}');
        return true;
    }

    private static boolean isHexDigit(final char ch) {
        final char lower = Character.toLowerCase(ch);
        return lower >= 'a' && lower <= 'f' || lower >= '0' && lower <= '9';
    }

    /*
     * ControlEscape ::
     *      one of fnrtv
     */
    private boolean controlEscape() {
        switch (ch0) {
        case 'f':
        case 'n':
        case 'r':
        case 't':
        case 'v':
            return commit(1);

        default:
            return false;
        }
    }

    /*
     * ControlLetter ::
     *      one of abcdefghijklmnopqrstuvwxyz
     *      ABCDEFGHIJKLMNOPQRSTUVWXYZ
     */
    private boolean controlLetter() {
        // To match other engines we also accept '0'..'9' and '_' as control letters inside a character class.
        if ((ch0 >= 'A' && ch0 <= 'Z') || (ch0 >= 'a' && ch0 <= 'z')
                || (!unicode && inCharClass && (isDecimalDigit(ch0) || ch0 == '_'))) {
            // for some reason java regexps don't like control characters on the
            // form "\\ca".match([string with ascii 1 at char0]). Translating
            // them to unicode does it though.
            sb.setLength(sb.length() - 1);
            unicode(ch0 % 32, sb);
            skip(1);
            return true;
        }
        return false;
    }

    /**
     * Whether a repetition count written from {@code from} on is larger than
     * the number of characters any string can hold.
     *
     * A count that large can be neither matched nor, in the library behind
     * this, even compiled - it parses one into an int - so what a pattern
     * carrying one means has to be worked out here.
     */
    private boolean beyondAnyString(final int from) {
        int at = from;
        while (at < sb.length() - 1 && sb.charAt(at) == '0') {
            at++;
        }
        final String digits = sb.substring(at);
        return digits.length() > 10
                || digits.length() == 10 && digits.compareTo("2147483647") > 0;
    }

    /**
     * Whether the atom written between the two positions matches at least one
     * character.
     *
     * A group may match nothing at all, and repeating that any number of times
     * still matches nothing; a character, an escape or a class always takes
     * one, and repeating one of those past the length of any string matches
     * nothing that could be written down.
     */
    private boolean cannotMatchEmpty(final int atomStart, final int atomEnd) {
        return atomEnd > atomStart && sb.charAt(atomStart) != '(';
    }

    /*
     * IdentityEscape ::
     *      SourceCharacter but not IdentifierPart
     *      <ZWJ>  (200c)
     *      <ZWNJ> (200d)
     */
    private boolean identityEscape() {
        if (atEOF()) {
            throw new RuntimeException("\\ at end of pattern"); // will be converted to PatternSyntaxException
        }
        if (unicode && SYNTAX_CHARACTERS.indexOf(ch0) == -1 && ch0 != '/'
                && !(inCharClass && ch0 == '-')) {
            // ES2015 21.2.1: in unicode mode only a syntax character, a slash,
            // and a dash inside a class may be written with a backslash
            throw new RuntimeException("Invalid escape in unicode pattern");
        }
        // ES 5.1 A.7 requires "not IdentifierPart" here but all major engines accept any character here.
        if (ch0 == 'c') {
            sb.append('\\'); // Treat invalid \c control sequence as \\c
        } else if (NON_IDENT_ESCAPES.indexOf(ch0) == -1) {
            sb.setLength(sb.length() - 1);
        }
        return commit(1);
    }

    /*
     * DecimalEscape ::
     *      DecimalIntegerLiteral [lookahead DecimalDigit]
     */
    private boolean decimalEscape() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (ch0 == '0' && !isOctalDigit(ch1)) {
            if (unicode && isDecimalDigit(ch1)) {
                // 21.2.1 CharacterEscape :: 0 [lookahead not DecimalDigit]: the
                // unicode grammar has \0 standing alone, so \08 is not a NUL
                // followed by an eight the way the web-compatible grammar has it
                throw new RuntimeException("\\0 followed by a digit in unicode pattern");
            }
            skip(1);
            //  DecimalEscape :: 0. If i is zero, return the EscapeValue consisting of a <NUL> character (Unicodevalue0000);
            sb.append("\u0000");
            return true;
        }

        if (isDecimalDigit(ch0)) {
            if (unicode) {
                // ES2015 21.2.1 has no LegacyOctalEscapeSequence and no
                // reference to a group that is not there
                if (ch0 == '0') {
                    throw new RuntimeException("Octal escape in unicode pattern");
                }
                if (inCharClass) {
                    throw new RuntimeException("Backreference in a character class in unicode pattern");
                }
            }

            if (ch0 == '0') {
                if (!annexB) {
                    // without B.1.4's LegacyOctalEscapeSequence, 21.2.1 has \0
                    // standing alone and a digit may not follow it
                    throw new RuntimeException("Octal escape in pattern");
                }
                // B.1.4 bounds the sequence at three octal digits, so \0111 is
                // a tab and a one. Written out as a unicode escape either way:
                // the backends do not agree about \00 as an octal escape, and
                // one of them reads it as an empty backreference.
                int octalValue = 0;
                int digits = 0;
                while (isOctalDigit(ch0) && digits < 3) {
                    octalValue = octalValue * 8 + ch0 - '0';
                    digits++;
                    skip(1);
                }

                unicode(octalValue, sb);
            } else {
                // This should be a backreference, but could also be an octal escape or even a literal string.
                int decimalValue = 0;
                while (isDecimalDigit(ch0)) {
                    decimalValue = decimalValue * 10 + ch0 - '0';
                    skip(1);
                }

                if (inCharClass) {
                    // No backreferences in character classes. Encode as unicode escape or literal char sequence
                    sb.setLength(sb.length() - 1);
                    octalOrLiteral(Integer.toString(decimalValue), sb);

                } else if (decimalValue <= caps.size()) {
                    //  Captures inside a negative lookahead are undefined when referenced from the outside.
                    final Capture capture = caps.get(decimalValue - 1);
                    if (!capture.canBeReferencedFrom(negLookaheadGroup, negLookaheadLevel)) {
                        // Outside reference to capture in negative lookahead, omit from output buffer.
                        sb.setLength(sb.length() - 1);
                    } else {
                        // Append backreference to output buffer.
                        sb.append(decimalValue);
                    }
                } else {
                    // Forward references to a capture group are always undefined so we can omit it from the output buffer.
                    // However, if the target capture does not exist, we need to rewrite the reference as hex escape
                    // or literal string, so register the reference for later processing.
                    sb.setLength(sb.length() - 1);
                    forwardReferences.add(decimalValue);
                    forwardReferences.add(sb.length());
                }

            }
            return true;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * CharacterClassEscape ::
     *  one of dDsSwW
     */
    private boolean characterClassEscape() {
        switch (ch0) {
        case 'd': case 'D': case 's': case 'S': case 'w': case 'W':
            atomWasCharacterClass = true;
            break;
        default:
            break;
        }
        return characterClassEscape0();
    }

    /** Whether the class atom just read was one of \d, \s, \w or their negations. */
    private boolean atomWasCharacterClass;

    private boolean characterClassEscape0() {
        switch (ch0) {
        // java.util.regex requires translation of \s and \S to explicit character list
        case 's':
            if (RegExpFactory.usesJavaUtilRegex()) {
                sb.setLength(sb.length() - 1);
                // No nested class required if we already are inside a character class
                if (inCharClass) {
                    sb.append(Lexer.getWhitespaceRegExp());
                } else {
                    sb.append('[').append(Lexer.getWhitespaceRegExp()).append(']');
                }
                skip(1);
                return true;
            }
            return commit(1);
        case 'S':
            if (RegExpFactory.usesJavaUtilRegex()) {
                sb.setLength(sb.length() - 1);
                // In negative class we must use intersection to get double negation ("not anything else than space")
                sb.append(inNegativeClass ? "&&[" : "[^").append(Lexer.getWhitespaceRegExp()).append(']');
                skip(1);
                return true;
            }
            return commit(1);
        case 'd':
        case 'D':
        case 'w':
        case 'W':
            return commit(1);

        case 'p':
        case 'P':
            // ES2018 Unicode property escapes \p{...} / \P{...}, only under the
            // unicode flag (without it the web grammar reads \p as a literal p).
            if (unicode) {
                return unicodePropertyEscape();
            }
            return false;

        default:
            return false;
        }
    }

    /**
     * ES2018 21.2.2.7 \p{...}/\P{...}. The backslash is already emitted; ch0 is
     * {@code p} or {@code P}. The ES property (a General_Category or Script
     * value, or a binary property name, with their aliases) is translated to the
     * form the JDK engine - which a unicode pattern always uses - accepts.
     */
    private boolean unicodePropertyEscape() {
        final boolean negated = ch0 == 'P';
        skip(1); // p or P
        if (ch0 != '{') {
            throw new RuntimeException("\\" + (negated ? 'P' : 'p') + " not followed by a property");
        }
        skip(1); // {
        final StringBuilder raw = new StringBuilder();
        while (!atEOF() && ch0 != '}') {
            raw.append(ch0);
            skip(1);
        }
        if (ch0 != '}') {
            throw new RuntimeException("Unterminated \\p{...}");
        }
        skip(1); // }
        final String jdk = UnicodeProperty.toJavaProperty(raw.toString());
        // sb already holds the leading backslash
        sb.append(negated ? 'P' : 'p').append('{').append(jdk).append('}');
        return true;
    }

    /*
     * CharacterClass ::
     *      [ [lookahead {^}] ClassRanges ]
     *      [ ^ ClassRanges ]
     */
    private boolean characterClass() {
        if (unicodeSets) {
            return classSetClass();
        }
        final int startIn  = position;
        final int startOut = sb.length();

        if (ch0 == '[') {
            try {
                inCharClass = true;
                push(']');
                commit(1);

                if (ch0 == '^') {
                    inNegativeClass = true;
                    commit(1);
                }

                if (classRanges() && ch0 == ']') {
                    pop(']');
                    commit(1);

                    // Substitute empty character classes [] and [^] that never or always match
                    if (position == startIn + 2) {
                        sb.setLength(sb.length() - 1);
                        sb.append("^\\s\\S]");
                    } else if (position == startIn + 3 && inNegativeClass) {
                        sb.setLength(sb.length() - 2);
                        sb.append("\\s\\S]");
                    }

                    return true;
                }
            } finally {
                inCharClass = false;  // no nested character classes in JavaScript
                inNegativeClass = false;
            }
        }

        restart(startIn, startOut);
        return false;
    }

    // ------------------------------------------------------------------------
    // ES2024 v-flag (unicodeSets) character classes.
    //
    // The class-set grammar - nested classes, the union / intersection (&&) /
    // difference (--) operators and \q{...} string literals - is transcribed to
    // what java.util.regex accepts, which is close: nested classes and && are
    // native, so union and intersection are almost pass-through; only -- has to
    // become the "&& negated" idiom. Every ClassSetCharacter is rendered as
    // \x{...} so no metacharacter, surrogate or astral code point needs
    // special handling. Two things the JDK engine cannot express are held out
    // with a syntax error and listed as engine limits: a \q{...} whose
    // alternatives are not all single code points (a set that contains a
    // multi-character string), and \p{...} of strings (RGI_Emoji and its kin).
    // ------------------------------------------------------------------------

    /** A parsed v-mode class-set operand, rendered for the several places it can go. */
    private static final class SetOperand {
        /** Rendered to sit inside {@code [ ... ]} in a union or an intersection. */
        String union;
        /** The positive body to put inside {@code [^ ... ]} for a difference right side. */
        String body;
        /** Whether the operand was itself a negated nested class (flips difference to intersection). */
        boolean negated;
        /** Whether the operand denotes a set that may contain multi-character strings. */
        boolean mayStrings;
        /** The single code point when the operand is one character (so it can be a range end); else -1. */
        int rangeChar = -1;
    }

    private boolean classSetClass() {
        if (ch0 != '[') {
            return false;
        }
        final boolean prevInClass = inCharClass;
        final boolean prevNeg = inNegativeClass;
        try {
            inCharClass = true;
            skip(1); // '['
            boolean negated = false;
            if (ch0 == '^') {
                negated = true;
                skip(1);
            }
            inNegativeClass = negated;
            if (ch0 == ']') {
                // an empty class: [] never matches, [^] matches anything
                skip(1);
                sb.append(negated ? "[\\s\\S]" : "[^\\s\\S]");
                return true;
            }
            final SetOperand set = classSetExpression();
            if (ch0 != ']') {
                throw new RuntimeException("Unterminated character class in unicode pattern");
            }
            skip(1); // ']'
            if (negated && set.mayStrings) {
                throw new RuntimeException("A negated character class may not contain strings");
            }
            sb.append('[');
            if (negated) {
                sb.append('^');
            }
            sb.append(set.union);
            sb.append(']');
            return true;
        } finally {
            inCharClass = prevInClass;
            inNegativeClass = prevNeg;
        }
    }

    /**
     * ClassSetExpression: a first operand, then either a run of {@code &&}
     * intersections, a run of {@code --} differences, or a union of further
     * operands and ranges.
     */
    private SetOperand classSetExpression() {
        final SetOperand first = classSetOperandOrRange();

        if (ch0 == '&' && ch1 == '&') {
            final StringBuilder u = new StringBuilder(first.union);
            boolean mayStrings = first.mayStrings;
            while (ch0 == '&' && ch1 == '&') {
                skip(2);
                if (ch0 == '&') {
                    throw new RuntimeException("Invalid && operand in unicode pattern");
                }
                final SetOperand next = classSetOperand();
                // A && B keeps a string only if both sides can; approximate by
                // requiring both, which is all the held-out string cases need.
                mayStrings = mayStrings && next.mayStrings;
                u.append("&&").append(next.negated ? "[^" + next.body + "]" : next.union);
            }
            final SetOperand r = new SetOperand();
            r.union = u.toString();
            r.body = r.union;
            r.mayStrings = mayStrings;
            return r;
        }

        if (ch0 == '-' && ch1 == '-') {
            final StringBuilder u = new StringBuilder(first.union);
            while (ch0 == '-' && ch1 == '-') {
                skip(2);
                final SetOperand next = classSetOperand();
                // A -- B = A intersect not-B; a negated B is A intersect B
                u.append("&&").append(next.negated ? "[" + next.body + "]" : "[^" + next.body + "]");
            }
            final SetOperand r = new SetOperand();
            r.union = u.toString();
            r.body = r.union;
            r.mayStrings = first.mayStrings;
            return r;
        }

        // union
        final StringBuilder u = new StringBuilder(first.union);
        boolean mayStrings = first.mayStrings;
        while (ch0 != ']' && !atEOF() && !(ch0 == '&' && ch1 == '&') && !(ch0 == '-' && ch1 == '-')) {
            final SetOperand next = classSetOperandOrRange();
            u.append(next.union);
            mayStrings = mayStrings || next.mayStrings;
        }
        final SetOperand r = new SetOperand();
        r.union = u.toString();
        r.body = r.union;
        r.mayStrings = mayStrings;
        return r;
    }

    /** A union member: an operand, possibly the low end of a {@code x-y} range. */
    private SetOperand classSetOperandOrRange() {
        final SetOperand op = classSetOperand();
        // a range only between two single characters
        if (op.rangeChar >= 0 && ch0 == '-' && !(ch1 == '-') && ch1 != ']') {
            skip(1); // '-'
            final SetOperand hi = classSetOperand();
            if (hi.rangeChar < 0) {
                throw new RuntimeException("Invalid class set range in unicode pattern");
            }
            if (hi.rangeChar < op.rangeChar) {
                throw new RuntimeException("Range out of order in character class");
            }
            final SetOperand r = new SetOperand();
            r.union = "\\x{" + Integer.toHexString(op.rangeChar) + "}-\\x{" + Integer.toHexString(hi.rangeChar) + "}";
            r.body = r.union;
            return r;
        }
        return op;
    }

    /**
     * ClassSetOperand: a nested class, a {@code \q{...}} string disjunction, a
     * character-class escape, or a single ClassSetCharacter.
     */
    private SetOperand classSetOperand() {
        final SetOperand r = new SetOperand();
        r.rangeChar = -1;

        if (ch0 == '[') {
            // a nested class: parse it, then unwrap the [ ... ] it produced
            final int mark = sb.length();
            classSetClass();
            final String nested = sb.substring(mark);
            sb.setLength(mark);
            // nested is "[...]" or "[^...]"
            if (nested.length() >= 2 && nested.charAt(1) == '^') {
                r.negated = true;
                r.body = nested.substring(2, nested.length() - 1);
                r.union = nested;
            } else {
                r.body = nested.substring(1, nested.length() - 1);
                r.union = nested;
            }
            return r;
        }

        if (ch0 == '\\') {
            if (ch1 == 'q') {
                return classStringDisjunction();
            }
            skip(1); // '\'
            // character-class escapes keep their class meaning
            switch (ch0) {
            case 'd': case 'D': case 'w': case 'W':
                r.union = "\\" + ch0;
                r.body = r.union;
                skip(1);
                return r;
            case 's':
                r.union = Lexer.getWhitespaceRegExp();
                r.body = r.union;
                skip(1);
                return r;
            case 'S':
                // \S is not-whitespace: [^ws] in a union, and in a difference
                // right side it behaves as a negated operand over ws
                r.union = "[^" + Lexer.getWhitespaceRegExp() + "]";
                r.body = Lexer.getWhitespaceRegExp();
                r.negated = true;
                skip(1);
                return r;
            case 'p': case 'P': {
                final boolean neg = ch0 == 'P';
                final String prop = readPropertyEscapeVMode();
                r.union = (neg ? "\\P{" : "\\p{") + prop + "}";
                r.body = r.union;
                return r;
            }
            default: {
                final int cp = readClassEscapeChar();
                r.rangeChar = cp;
                r.union = "\\x{" + Integer.toHexString(cp) + "}";
                r.body = r.union;
                return r;
            }
            }
        }

        // a plain ClassSetCharacter, with the v-mode restrictions
        if (ch0 == ']') {
            throw new RuntimeException("Expected a class set operand in unicode pattern");
        }
        // ES2024 ClassSetSyntaxCharacter: these must be escaped in a v-mode class
        if (ch0 == '(' || ch0 == ')' || ch0 == '{' || ch0 == '}' || ch0 == '/' || ch0 == '|' || ch0 == '-') {
            throw new RuntimeException("Unescaped '" + ch0 + "' in a unicodeSets character class");
        }
        // ES2024 ClassSetReservedDoublePunctuator: a doubled punctuator is reserved
        if (ch0 == ch1 && isClassSetReservedPunctuator(ch0)) {
            throw new RuntimeException("Reserved double punctuator '" + ch0 + ch0
                    + "' in a unicodeSets character class");
        }
        final int cp = readSourceCodePoint();
        r.rangeChar = cp;
        r.union = "\\x{" + Integer.toHexString(cp) + "}";
        r.body = r.union;
        return r;
    }

    /** The punctuators ES2024 reserves when doubled inside a v-mode class. */
    private static boolean isClassSetReservedPunctuator(final char c) {
        switch (c) {
        case '&': case '-': case '!': case '#': case '%': case ',': case ':':
        case ';': case '<': case '=': case '>': case '@': case '`': case '~':
        case '$': case '*': case '+': case '.': case '?': case '^':
            return true;
        default:
            return false;
        }
    }

    /** ES2024 ClassStringDisjunction {@code \q{ a | bc | ... }}. */
    private SetOperand classStringDisjunction() {
        skip(2); // '\q'
        if (ch0 != '{') {
            throw new RuntimeException("\\q not followed by {");
        }
        skip(1); // '{'
        final java.util.List<Integer> singles = new java.util.ArrayList<>();
        boolean multi = false;
        StringBuilder current = new StringBuilder();
        int currentCount = 0;
        int firstCp = -1;
        for (;;) {
            if (atEOF()) {
                throw new RuntimeException("Unterminated \\q{...}");
            }
            if (ch0 == '}' || ch0 == '|') {
                if (currentCount == 1) {
                    singles.add(firstCp);
                } else {
                    multi = true; // empty string or a multi-character string
                }
                current = new StringBuilder();
                currentCount = 0;
                firstCp = -1;
                final boolean end = ch0 == '}';
                skip(1);
                if (end) {
                    break;
                }
                continue;
            }
            final int cp = ch0 == '\\' ? classStringEscapeChar() : readSourceCodePoint();
            if (currentCount == 0) {
                firstCp = cp;
            }
            currentCount++;
        }
        final SetOperand r = new SetOperand();
        r.rangeChar = -1;
        if (multi) {
            // a set that contains a multi-character (or empty) string: the JDK
            // engine cannot hold strings in a class - held out as an engine limit
            throw new RuntimeException("A \\q{...} string set with a multi-character string is not supported");
        }
        final StringBuilder chars = new StringBuilder();
        for (final int cp : singles) {
            chars.append("\\x{").append(Integer.toHexString(cp)).append('}');
        }
        r.union = chars.toString();
        r.body = r.union;
        return r;
    }

    /** Reads one code point of a \q{} class string, decoding an escape. */
    private int classStringEscapeChar() {
        skip(1); // '\'
        if (ch0 == 'p' || ch0 == 'P' || ch0 == 'd' || ch0 == 'D' || ch0 == 's' || ch0 == 'S'
                || ch0 == 'w' || ch0 == 'W' || ch0 == 'b' || ch0 == 'q') {
            throw new RuntimeException("A \\q{...} string set with a class escape is not supported");
        }
        return readClassEscapeChar();
    }

    /** Reads a \p{...}/\P{...} property name in v-mode, holding out properties of strings. */
    private String readPropertyEscapeVMode() {
        skip(1); // 'p' or 'P'
        if (ch0 != '{') {
            throw new RuntimeException("\\p not followed by a property");
        }
        skip(1); // '{'
        final StringBuilder raw = new StringBuilder();
        while (!atEOF() && ch0 != '}') {
            raw.append(ch0);
            skip(1);
        }
        if (ch0 != '}') {
            throw new RuntimeException("Unterminated \\p{...}");
        }
        skip(1); // '}'
        final String name = raw.toString();
        // properties of strings (RGI_Emoji and its kin) are sets of strings the
        // JDK engine cannot express - held out as an engine limit
        if (UnicodeProperty.isPropertyOfStrings(name)) {
            throw new RuntimeException("A \\p{...} property of strings is not supported");
        }
        return UnicodeProperty.toJavaProperty(name);
    }

    /** Reads one code point after a backslash inside a v-mode class (identity/character escape). */
    private int readClassEscapeChar() {
        // ch0 is the character after the backslash
        switch (ch0) {
        case 'n': skip(1); return '\n';
        case 'r': skip(1); return '\r';
        case 't': skip(1); return '\t';
        case 'f': skip(1); return '\f';
        case 'v': skip(1); return 0x0B;
        case '0': skip(1); return 0;
        case 'b': skip(1); return '\b';
        case 'c': {
            skip(1);
            if ((ch0 >= 'a' && ch0 <= 'z') || (ch0 >= 'A' && ch0 <= 'Z')) {
                final int c = ch0 % 32;
                skip(1);
                return c;
            }
            throw new RuntimeException("Invalid \\c escape in unicode pattern");
        }
        case 'x': {
            skip(1);
            int v = 0;
            for (int i = 0; i < 2; i++) {
                v = (v << 4) | hexValue(ch0);
                skip(1);
            }
            return v;
        }
        case 'u':
            return readUnicodeEscapeCodePoint();
        default:
            // identity escape of a ClassSetSyntaxCharacter or other allowed char
            final int cp = readSourceCodePoint();
            return cp;
        }
    }

    /** Reads a \\u escape (either \\uXXXX, a surrogate pair, or \\u{...}) as a code point. */
    private int readUnicodeEscapeCodePoint() {
        skip(1); // 'u'
        if (ch0 == '{') {
            skip(1);
            int v = 0;
            while (ch0 != '}') {
                v = (v << 4) | hexValue(ch0);
                skip(1);
            }
            skip(1); // '}'
            return v;
        }
        int hi = 0;
        for (int i = 0; i < 4; i++) {
            hi = (hi << 4) | hexValue(ch0);
            skip(1);
        }
        if (Character.isHighSurrogate((char) hi) && ch0 == '\\' && ch1 == 'u') {
            final int mark = position;
            skip(2);
            int lo = 0;
            for (int i = 0; i < 4; i++) {
                lo = (lo << 4) | hexValue(ch0);
                skip(1);
            }
            if (Character.isLowSurrogate((char) lo)) {
                return Character.toCodePoint((char) hi, (char) lo);
            }
            reset(mark);
        }
        return hi;
    }

    /** Reads one source code point (a surrogate pair counts as one). */
    private int readSourceCodePoint() {
        final char c = ch0;
        if (Character.isHighSurrogate(c) && Character.isLowSurrogate(ch1)) {
            final int cp = Character.toCodePoint(c, ch1);
            skip(2);
            return cp;
        }
        skip(1);
        return c;
    }

    private static int hexValue(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new RuntimeException("Invalid hex digit in unicode pattern");
    }

    /*
     * ClassRanges ::
     *      [empty]
     *      NonemptyClassRanges
     */
    private boolean classRanges() {
        nonemptyClassRanges();
        return true;
    }

    /*
     * NonemptyClassRanges ::
     *      ClassAtom
     *      ClassAtom NonemptyClassRangesNoDash
     *      ClassAtom - ClassAtom ClassRanges
     */
    private boolean nonemptyClassRanges() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (classAtom()) {
            final boolean lowerWasCharacterClass = atomWasCharacterClass;

            if (ch0 == '-') {
                commit(1);

                if (classAtom()) {
                    verifyClassRange(lowerWasCharacterClass);
                    if (classRanges()) {
                        return true;
                    }
                }
            }

            nonemptyClassRangesNoDash();

            return true;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * NonemptyClassRangesNoDash ::
     *      ClassAtom
     *      ClassAtomNoDash NonemptyClassRangesNoDash
     *      ClassAtomNoDash - ClassAtom ClassRanges
     */
    private boolean nonemptyClassRangesNoDash() {
        final int startIn  = position;
        final int startOut = sb.length();

        if (classAtomNoDash()) {
            final boolean lowerWasCharacterClass = atomWasCharacterClass;

            // need to check dash first, as for e.g. [a-b|c-d] will otherwise parse - as an atom
            if (ch0 == '-') {
               commit(1);

               if (classAtom()) {
                   verifyClassRange(lowerWasCharacterClass);
                   if (classRanges()) {
                       return true;
                   }
               }
               //fallthru
           }

            nonemptyClassRangesNoDash();
            return true; // still a class atom
        }

        if (classAtom()) {
            return true;
        }

        restart(startIn, startOut);
        return false;
    }

    /*
     * ClassAtom : - ClassAtomNoDash
     */
    private boolean classAtom() {
        atomWasCharacterClass = false;

        if (ch0 == '-') {
            return commit(1);
        }

        return classAtomNoDash();
    }

    /**
     * ES2015 21.2.1 NonemptyClassRanges: a range may not have {@code \d} and its
     * kind at either end. Annex B still allows it, and only outside a unicode
     * pattern.
     */
    private void verifyClassRange(final boolean lowerWasCharacterClass) {
        if (lowerWasCharacterClass || atomWasCharacterClass) {
            if (unicode) {
                throw new RuntimeException("Character class as a range boundary in unicode pattern");
            }
            // B.1.4.1.1 CharacterRangeOrUnion: a class escape cannot be the end
            // of a range, so the three of them are a union instead and the dash
            // is one of its members. The dash was committed as it stood, which
            // Java would read as a range; escaping it makes it the member it is
            escapeLastDash();
        }
    }

    /**
     * Turns the dash of what looked like a range into a literal member.
     *
     * The two atoms and the dash between them are already in the output, and
     * the dash is the only one of the three that has to change - so it is found
     * by walking back over what the upper atom emitted.
     */
    private void escapeLastDash() {
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) == '-' && (i == 0 || sb.charAt(i - 1) != '\\')) {
                sb.insert(i, '\\');
                return;
            }
        }
    }

    /*
     * ClassAtomNoDash ::
     *      SourceCharacter but not one of \ or ] or -
     *      \ ClassEscape
     */
    private boolean classAtomNoDash() {
        atomWasCharacterClass = false;
        if (atEOF()) {
            return false;
        }
        final int startIn  = position;
        final int startOut = sb.length();

        switch (ch0) {
        case ']':
        case '-':
            return false;

        case '[':
            // unescaped left square bracket - add escape
            sb.append('\\');
            return commit(1);

        case '\\':
            commit(1);
            if (classEscape()) {
                return true;
            }

            restart(startIn, startOut);
            return false;

        default:
            return commit(1);
        }
    }

    /*
     * ClassEscape ::
     *      DecimalEscape
     *      b
     *      CharacterEscape
     *      CharacterClassEscape
     */
    private boolean classEscape() {

        if (decimalEscape()) {
            return true;
        }

        if (ch0 == 'b') {
            sb.setLength(sb.length() - 1);
            sb.append('\b');
            skip(1);
            return true;
        }

        // Note that contrary to ES 5.1 spec we put identityEscape() last because it acts as a catch-all
        return characterEscape() || characterClassEscape() || identityEscape();
    }

    /*
     * DecimalDigits
     */
    private boolean decimalDigits() {
        if (!isDecimalDigit(ch0)) {
            return false;
        }

        while (isDecimalDigit(ch0)) {
            commit(1);
        }

        return true;
    }

    private static void unicode(final int value, final StringBuilder buffer) {
        final String hex = Integer.toHexString(value);
        buffer.append('u');
        buffer.append("0".repeat(Math.max(0, 4 - hex.length())));
        buffer.append(hex);
    }

    // Convert what would have been a backreference into a unicode escape, or a number literal, or both.
    private static void octalOrLiteral(final String numberLiteral, final StringBuilder buffer) {
        final int length = numberLiteral.length();
        int octalValue = 0;
        int pos = 0;
        // Maximum value for octal escape is 0377 (255) so we stop the loop at 32
        while (pos < length && octalValue < 0x20) {
            final char ch = numberLiteral.charAt(pos);
            if (isOctalDigit(ch)) {
                octalValue = octalValue * 8 + ch - '0';
            } else {
                break;
            }
            pos++;
        }
        if (octalValue > 0) {
            buffer.append('\\');
            unicode(octalValue, buffer);
            buffer.append(numberLiteral.substring(pos));
        } else {
            buffer.append(numberLiteral);
        }
    }

    private static boolean isOctalDigit(final char ch) {
        return ch >= '0' && ch <= '7';
    }

    private static boolean isDecimalDigit(final char ch) {
        return ch >= '0' && ch <= '9';
    }
}
