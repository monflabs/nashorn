/*
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.runtime.regexp;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.UNICODE_CASE;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.monflabs.nashorn.internal.runtime.ParserException;

/**
 * Default regular expression implementation based on java.util.regex package.
 *
 * Note that this class is not thread-safe as it stores the current match result
 * and the string being matched in instance fields.
 */
public class JdkRegExp extends RegExp {

    /** Java regexp pattern to use for match. We compile to one of these */
    private Pattern pattern;

    /**
     * Construct a Regular expression from the given {@code source} and {@code flags} strings.
     *
     * @param source RegExp source string
     * @param flags RegExp flag string
     * @throws ParserException if flags is invalid or source string has syntax error.
     */
    public JdkRegExp(final String source, final String flags) throws ParserException {
        super(source, flags);

        int intFlags = 0;

        if (isIgnoreCase()) {
            intFlags |= CASE_INSENSITIVE;
            // ES2015 21.2.2.8.2 Canonicalize folds by the full Unicode rules
            // only under the unicode flag: without it, a character outside
            // Latin-1 that folds into it stays where it is, so the Kelvin sign
            // is not a k
            if (isUnicode()) {
                intFlags |= UNICODE_CASE;
            }
        }
        if (isMultiline()) {
            intFlags |= MULTILINE;
        }

        try {
            RegExpScanner parsed;

            try {
                parsed = RegExpScanner.scan(source, isUnicode(), RegExpFactory.annexBEnabled());
            } catch (final PatternSyntaxException e) {
                // refine the exception with a better syntax error, if this
                // passes, just rethrow what we have
                Pattern.compile(source, intFlags);
                throw e;
            }

            if (parsed != null) {
                final String javaPattern = isUnicode() && isIgnoreCase()
                        ? withUnicodeFolds(parsed.getJavaPattern())
                        : parsed.getJavaPattern();
                this.pattern = Pattern.compile(javaPattern, intFlags);
                this.groupsInNegativeLookahead = parsed.getGroupsInNegativeLookahead();
            }
        } catch (final PatternSyntaxException e2) {
            throwParserException("syntax", e2.getMessage());
        } catch (StackOverflowError e3) {
            throw new RuntimeException(e3);
        }
    }


    /**
     * The pairs 22.2.2.9 folds together and a case mapping does not.
     *
     * Each of these has a simple case folding to the other that its uppercase
     * does not give, because its full folding is more than one character:
     * CaseFolding.txt folds 1E9E to 00DF, 1FD3 to 0390, 1FE3 to 03B0 and FB05
     * to FB06. java.util.regex canonicalizes by case mapping, so it relates
     * neither member of a pair to the other however UNICODE_CASE is set.
     */
    private static final char[][] UNICODE_FOLD_PAIRS = {
        {'\u00df', '\u1e9e'},
        {'\u0390', '\u1fd3'},
        {'\u03b0', '\u1fe3'},
        {'\ufb05', '\ufb06'},
    };

    /** The character a /ui pattern folds this one to, or 0 if a case mapping already gives it. */
    private static char foldPartner(final int value) {
        for (final char[] pair : UNICODE_FOLD_PAIRS) {
            if (pair[0] == value) {
                return pair[1];
            }
            if (pair[1] == value) {
                return pair[0];
            }
        }
        return 0;
    }

    /**
     * Writes the foldings {@link #UNICODE_FOLD_PAIRS} names into a pattern.
     *
     * A character of a /ui pattern that folds to another one has to match that
     * other one as well. Outside a character class it becomes a class holding
     * both, which is still one atom for a quantifier that follows. Inside one
     * the partner is added as a further member, at the end, where it does not
     * disturb a range: 22.2.2.7.2 canonicalizes every character a class covers,
     * so a range that covers one of these characters matches its partner too.
     *
     * @param javaPattern the pattern the scanner produced
     * @return it, with the pairs written out
     */
    private static String withUnicodeFolds(final String javaPattern) {
        final StringBuilder folded = new StringBuilder(javaPattern.length());
        final StringBuilder extras = new StringBuilder();
        boolean inClass = false;
        int i = 0;

        while (i < javaPattern.length()) {
            final int length = atomLength(javaPattern, i);

            if (length == 0) {
                final char ch = javaPattern.charAt(i);
                if (ch == '[' && !inClass) {
                    inClass = true;
                } else if (ch == ']' && inClass) {
                    inClass = false;
                    folded.append(extras);
                    extras.setLength(0);
                }
                folded.append(ch);
                i++;
                continue;
            }

            final int value = atomValue(javaPattern, i, length);
            final int rangeEnd = inClass ? rangeEnd(javaPattern, i + length) : 0;
            if (rangeEnd > 0) {
                // a range: copied as it stands, with the partner of anything it
                // covers added to the class
                final int to = atomValue(javaPattern, i + length + 1, rangeEnd);
                folded.append(javaPattern, i, i + length + 1 + rangeEnd);
                for (final char[] pair : UNICODE_FOLD_PAIRS) {
                    for (final char member : pair) {
                        if (member >= value && member <= to) {
                            escaped(extras, member == pair[0] ? pair[1] : pair[0]);
                        }
                    }
                }
                i += length + 1 + rangeEnd;
                continue;
            }

            final char partner = value < 0 ? 0 : foldPartner(value);
            if (partner == 0) {
                folded.append(javaPattern, i, i + length);
            } else if (inClass) {
                folded.append(javaPattern, i, i + length);
                escaped(extras, partner);
            } else {
                folded.append('[');
                escaped(folded, (char)value);
                escaped(folded, partner);
                folded.append(']');
            }
            i += length;
        }
        return folded.toString();
    }

    /** The length of the atom ending a range that starts here, or 0 if this is not a range. */
    private static int rangeEnd(final String pattern, final int at) {
        if (at >= pattern.length() || pattern.charAt(at) != '-' || at + 1 >= pattern.length()
                || pattern.charAt(at + 1) == ']') {
            return 0;
        }
        return atomLength(pattern, at + 1);
    }

    /** The length of the character atom at this position, or 0 if there is not one. */
    private static int atomLength(final String pattern, final int at) {
        final char ch = pattern.charAt(at);
        if (ch != '\\') {
            // a metacharacter is not an atom this rewrite has anything to say about
            return "[]-^$.*+?(){}|".indexOf(ch) == -1 ? 1 : 0;
        }
        if (at + 1 >= pattern.length()) {
            return 0;
        }
        final char kind = pattern.charAt(at + 1);
        if (kind == 'u' && at + 5 < pattern.length() && isHex(pattern, at + 2, 4)) {
            return 6;
        }
        if (kind == 'x' && at + 3 < pattern.length() && isHex(pattern, at + 2, 2)) {
            return 4;
        }
        if (kind == 'x' && at + 2 < pattern.length() && pattern.charAt(at + 2) == '{') {
            final int end = pattern.indexOf('}', at + 3);
            return end == -1 ? 0 : end - at + 1;
        }
        // some other escape: two characters that are not a character of ours
        return 0;
    }

    /** The code point the atom at this position stands for. */
    private static int atomValue(final String pattern, final int at, final int length) {
        if (length == 1) {
            return pattern.charAt(at);
        }
        final int from = pattern.charAt(at + 1) == 'u' ? at + 2 : at + 2 + (pattern.charAt(at + 2) == '{' ? 1 : 0);
        final int to = pattern.charAt(at + length - 1) == '}' ? at + length - 1 : at + length;
        try {
            return Integer.parseInt(pattern.substring(from, to), 16);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isHex(final String pattern, final int at, final int count) {
        for (int i = at; i < at + count; i++) {
            if (Character.digit(pattern.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }

    private static void escaped(final StringBuilder buffer, final char ch) {
        buffer.append(String.format("\\u%04x", (int)ch));
    }

    @Override
    public RegExpMatcher match(final String str) {
        if (pattern == null) {
            return null; // never matches or similar, e.g. a[]
        }

        return new DefaultMatcher(str);
    }

    class DefaultMatcher implements RegExpMatcher {
        final String input;
        final Matcher defaultMatcher;

        DefaultMatcher(final String input) {
            this.input = input;
            this.defaultMatcher = pattern.matcher(input);
        }

        @Override
        public boolean search(final int start) {
            return defaultMatcher.find(start);
        }

        @Override
        public String getInput() {
            return input;
        }

        @Override
        public int start() {
            return defaultMatcher.start();
        }

        @Override
        public int start(final int group) {
            return defaultMatcher.start(group);
        }

        @Override
        public int end() {
            return defaultMatcher.end();
        }

        @Override
        public int end(final int group) {
            return defaultMatcher.end(group);
        }

        @Override
        public String group() {
            return defaultMatcher.group();
        }

        @Override
        public String group(final int group) {
            return defaultMatcher.group(group);
        }

        @Override
        public int groupCount() {
            return defaultMatcher.groupCount();
        }
    }

}
