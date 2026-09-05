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

package org.monflabs.nashorn.internal.runtime.regexp;

import java.util.regex.MatchResult;
import org.monflabs.nashorn.internal.runtime.BitVector;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.ParserException;

/**
 * This is the base class for representing a parsed regular expression.
 *
 * Instances of this class are created by a {@link RegExpFactory}.
 */
public abstract class RegExp {

    /** Pattern string. */
    private final String source;

    /** Global search flag for this regexp.*/
    private boolean global;

    /** Case insensitive flag for this regexp */
    private boolean ignoreCase;

    /** Multi-line flag for this regexp */
    private boolean multiline;

    /** Is this regexp sticky, matching only at lastIndex? */
    private boolean sticky;

    /** Is this regexp in unicode mode? */
    private boolean unicode;

    /** ES2018 dotAll flag: does {@code .} match line terminators too? */
    private boolean dotAll;

    /** BitVector that keeps track of groups in negative lookahead */
    protected BitVector groupsInNegativeLookahead;

    /** ES2018 named capture groups: name to 1-based index, in source order; empty if none. */
    private java.util.Map<String, Integer> groupNames = java.util.Collections.emptyMap();

    /**
     * Constructor.
     *
     * @param source the source string
     * @param flags the flags string
     */
    protected RegExp(final String source, final String flags) {
        this.source = source.isEmpty() ? "(?:)" : source;
        for (int i = 0; i < flags.length(); i++) {
            final char ch = flags.charAt(i);
            switch (ch) {
            case 'g':
                if (this.global) {
                    throwParserException("repeated.flag", "g");
                }
                this.global = true;
                break;
            case 'i':
                if (this.ignoreCase) {
                    throwParserException("repeated.flag", "i");
                }
                this.ignoreCase = true;
                break;
            case 'm':
                if (this.multiline) {
                    throwParserException("repeated.flag", "m");
                }
                this.multiline = true;
                break;
            case 'y':
                if (this.sticky) {
                    throwParserException("repeated.flag", "y");
                }
                this.sticky = true;
                break;
            case 'u':
                if (this.unicode) {
                    throwParserException("repeated.flag", "u");
                }
                this.unicode = true;
                break;
            case 's':
                if (this.dotAll) {
                    throwParserException("repeated.flag", "s");
                }
                this.dotAll = true;
                break;
            default:
                throwParserException("unsupported.flag", Character.toString(ch));
            }
        }
    }

    /**
     * Get the source pattern of this regular expression.
     *
     * @return the source string
     */
    public String getSource() {
        return source;
    }

    /**
     * Set the global flag of this regular expression to {@code global}.
     *
     * @param global the new global flag
     */
    public void setGlobal(final boolean global) {
        this.global = global;
    }

    /**
     * Get the global flag of this regular expression.
     *
     * @return the global flag
     */
    public boolean isGlobal() {
        return global;
    }

    /**
     * Get the ignore-case flag of this regular expression.
     *
     * @return the ignore-case flag
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Get the multiline flag of this regular expression.
     *
     * @return the multiline flag
     */
    /**
     * Whether this regexp is sticky: it matches only at lastIndex, and does not
     * search forward from there.
     *
     * @return true if the y flag was given
     */
    public boolean isSticky() {
        return sticky;
    }

    /**
     * Whether this regexp is in unicode mode, where the pattern and the input
     * are read as code points rather than as code units.
     *
     * @return true if the u flag was given
     */
    public boolean isUnicode() {
        return unicode;
    }

    public boolean isMultiline() {
        return multiline;
    }

    /**
     * Whether this regexp has the ES2018 dotAll ({@code s}) flag, where
     * {@code .} matches any character including line terminators.
     *
     * @return true if the s flag was given
     */
    public boolean isDotAll() {
        return dotAll;
    }

    /**
     * Get a bitset indicating which of the groups in this regular expression are inside a negative lookahead.
     *
     * @return the groups-in-negative-lookahead bitset
     */
    public BitVector getGroupsInNegativeLookahead() {
        return groupsInNegativeLookahead;
    }

    /**
     * The ES2018 named capture groups of this regexp: group name to its 1-based
     * capture index, in source order. Empty when the pattern has none.
     *
     * @return the named-group map
     */
    public java.util.Map<String, Integer> getGroupNames() {
        return groupNames;
    }

    /**
     * Record the named capture groups discovered while scanning the pattern.
     *
     * @param groupNames name to 1-based index
     */
    protected void setGroupNames(final java.util.Map<String, Integer> groupNames) {
        if (groupNames != null && !groupNames.isEmpty()) {
            this.groupNames = groupNames;
        }
    }

    /**
     * Match this regular expression against {@code str}, starting at index {@code start}
     * and return a {@link MatchResult} with the result.
     *
     * @param str the string
     * @return the matcher
     */
    public abstract RegExpMatcher match(String str);

    /**
     * Throw a regexp parser exception.
     *
     * @param key the message key
     * @param str string argument
     * @throws org.monflabs.nashorn.internal.runtime.ParserException unconditionally
     */
    protected static void throwParserException(final String key, final String str) throws ParserException {
        throw new ParserException(ECMAErrors.getMessage("parser.error.regex." + key, str));
    }
}
