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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ParserException;
import org.monflabs.nashorn.internal.runtime.options.Options;

/**
 * Factory class for regular expressions. This class creates instances of {@link JdkRegExp}.
 * An alternative factory can be installed using the {@code nashorn.regexp.impl} system property.
 */
public class RegExpFactory {

    private final static RegExpFactory instance;

    private final static String JDK  = "jdk";
    private final static String JONI = "joni";

    /** Weak cache of already validated regexps - when reparsing, we don't, for example
     *  need to recompile (reverify) all regexps that have previously been parsed by this
     *  RegExpFactory in a previous compilation. This saves significant time in e.g. avatar
     *  startup
     */
    private static final Map<String, RegExp> REGEXP_CACHE =
            Collections.synchronizedMap(new WeakHashMap<String, RegExp>());

    static {
        final String impl = Options.getStringProperty("nashorn.regexp.impl", JONI);
        switch (impl) {
            case JONI:
                instance = new JoniRegExp.Factory();
                break;
            case JDK:
                instance = new RegExpFactory();
                break;
            default:
                instance = null;
                throw new InternalError("Unsupported RegExp factory: " + impl);
        }
    }

    /**
     * Creates a Regular expression from the given {@code pattern} and {@code flags} strings.
     *
     * @param pattern RegExp pattern string
     * @param flags   RegExp flags string
     * @return new RegExp
     * @throws ParserException if flags is invalid or pattern string has syntax error.
     */
    /**
     * Whether the engine compiling this pattern implements Annex B.
     *
     * The scanner is reached from static factory methods that carry no engine
     * with them, so the question is asked of the context the thread is running
     * in. Outside one - a pattern validated by a tool, say - Annex B applies,
     * which is the default.
     *
     * @return true if Annex B's pattern grammar is in force
     */
    public static boolean annexBEnabled() {
        final Context context = Context.getContextTrustedOrNull();
        return context == null || context.getEnv()._annexB;
    }

    public RegExp compile(final String pattern, final String flags) throws ParserException {
        return new JdkRegExp(pattern, flags);
    }

    /**
     * Whether a pattern uses ES2018 syntax the bundled Joni engine's JAVASCRIPT
     * flavour does not have - named groups / lookbehind ({@code (?<...}) and
     * named backreferences ({@code \k<...}) - and so must be compiled with the
     * JDK engine. Backslashes and character classes are respected so a literal
     * {@code (?<} inside {@code [...]} or after {@code \} is not mistaken for one.
     */
    private static boolean usesJdkOnlySyntax(final String pattern) {
        if (pattern == null) {
            return false;
        }
        boolean escaped = false;
        boolean inClass = false;
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (escaped) {
                if (c == 'k' && !inClass && i + 1 < pattern.length() && pattern.charAt(i + 1) == '<') {
                    return true; // \k<name>
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
            } else if (c == '[') {
                inClass = true;
            } else if (c == '(' && i + 2 < pattern.length() && pattern.charAt(i + 1) == '?' && pattern.charAt(i + 2) == '<') {
                return true; // (?<name>, (?<=, (?<!
            } else if (c == '(' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '?'
                    && isModifierGroupAt(pattern, i + 2)) {
                return true; // ES2025 (?ims-ims:...) - Joni has no inline flags
            }
        }
        return false;
    }

    /**
     * Whether {@code pattern} from index {@code j} (just past {@code (?}) is an
     * ES2025 pattern-modifier prefix: {@code [ims]*} then an optional
     * {@code -[ims]*}, with at least one flag, ending in {@code :}.
     */
    private static boolean isModifierGroupAt(final String pattern, final int j) {
        int i = j;
        int flags = 0;
        while (i < pattern.length() && "ims".indexOf(pattern.charAt(i)) >= 0) {
            i++;
            flags++;
        }
        if (i < pattern.length() && pattern.charAt(i) == '-') {
            i++;
            while (i < pattern.length() && "ims".indexOf(pattern.charAt(i)) >= 0) {
                i++;
                flags++;
            }
        }
        return flags > 0 && i < pattern.length() && pattern.charAt(i) == ':';
    }

    /**
     * Compile a regexp with the given {@code source} and {@code flags}.
     *
     * @param pattern RegExp pattern string
     * @param flags   flag string
     * @return new RegExp
     * @throws ParserException if invalid source or flags
     */
    public static RegExp create(final String pattern, final String flags) {
        // the flag decides what the pattern means, so two engines that disagree
        // about it must not be handed each other's compilations
        final String key = pattern + "/" + flags + (annexBEnabled() ? "/b" : "");
        RegExp regexp = REGEXP_CACHE.get(key);
        if (regexp == null) {
            // The bundled Joni engine works in UTF-16 code units and has no
            // notion of a code point, which is the whole of what the unicode
            // flag changes - an astral character is one atom, a class range may
            // cross the surrogate boundary, and case folding is the full Unicode
            // one. The JDK's engine is code point based, so a unicode pattern is
            // compiled with it whatever the configured factory is.
            // The bundled Joni uses the ES5-era JAVASCRIPT syntax, which has no
            // named groups or lookbehind; the JDK engine has both. So an ES2018
            // pattern that uses them is compiled with the JDK engine too - the
            // scanner emits JDK-compatible syntax for those constructs.
            regexp = (flags != null && (flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0))
                        || usesJdkOnlySyntax(pattern)
                    ? new JdkRegExp(pattern, flags)
                    : instance.compile(pattern, flags);
            REGEXP_CACHE.put(key, regexp);
        }
        return regexp;
    }

    /**
     * Validate a regexp with the given {@code source} and {@code flags}.
     *
     * @param pattern RegExp pattern string
     * @param flags  flag string
     *
     * @throws ParserException if invalid source or flags
     */
    public static void validate(final String pattern, final String flags) throws ParserException {
        create(pattern, flags);
    }

    /**
     * Returns true if the instance uses the JDK's {@code java.util.regex} package.
     *
     * @return true if instance uses JDK regex package
     */
    public static boolean usesJavaUtilRegex() {
        return instance != null && instance.getClass() == RegExpFactory.class;
    }
}
