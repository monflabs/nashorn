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

package org.monflabs.nashorn.internal.runtime.regexp;

import java.util.Map;
import java.util.Set;

/**
 * Translates an ECMAScript 2018 Unicode property escape - the {@code ...} of a
 * {@code \p{...}} / {@code \P{...}} - into the form the JDK's
 * {@code java.util.regex} engine accepts. A unicode pattern is always compiled
 * with that engine, so this is the one target.
 *
 * <p>ECMAScript writes a property escape as {@code \p{LoneName}} (a
 * General_Category value or a binary property name) or {@code \p{Name=Value}}
 * (General_Category, Script or Script_Extensions), with the Unicode aliases for
 * all of them. The JDK engine reads General_Category as its one- or two-letter
 * code, Script as {@code script=Name}, and a set of binary properties as
 * {@code IsName}. General_Category value aliases are mapped here; Script value
 * aliases the JDK resolves itself.
 *
 * <p>What the JDK engine cannot express - Script_Extensions, and the binary
 * properties it does not carry (Emoji and its kin, Dash, Math, Diacritic, and
 * the rest) - is rejected here, which surfaces as a SyntaxError. Those escapes
 * would need the Unicode Character Database bundled to answer for; see
 * doc/CONFORMANCE.md.
 */
final class UnicodeProperty {

    private UnicodeProperty() {
    }

    /** General_Category long names and aliases to the JDK's one/two-letter code. */
    private static final Map<String, String> GENERAL_CATEGORY = Map.ofEntries(
            Map.entry("Cased_Letter", "LC"), Map.entry("LC", "LC"),
            Map.entry("Close_Punctuation", "Pe"), Map.entry("Connector_Punctuation", "Pc"),
            Map.entry("Control", "Cc"), Map.entry("cntrl", "Cc"),
            Map.entry("Currency_Symbol", "Sc"),
            Map.entry("Dash_Punctuation", "Pd"), Map.entry("Decimal_Number", "Nd"), Map.entry("digit", "Nd"),
            Map.entry("Enclosing_Mark", "Me"), Map.entry("Final_Punctuation", "Pf"),
            Map.entry("Format", "Cf"), Map.entry("Initial_Punctuation", "Pi"),
            Map.entry("Letter", "L"), Map.entry("Letter_Number", "Nl"),
            Map.entry("Line_Separator", "Zl"), Map.entry("Lowercase_Letter", "Ll"),
            Map.entry("Mark", "M"), Map.entry("Combining_Mark", "M"),
            Map.entry("Math_Symbol", "Sm"), Map.entry("Modifier_Letter", "Lm"),
            Map.entry("Modifier_Symbol", "Sk"), Map.entry("Nonspacing_Mark", "Mn"),
            Map.entry("Number", "N"), Map.entry("Open_Punctuation", "Ps"),
            Map.entry("Other", "C"), Map.entry("Other_Letter", "Lo"),
            Map.entry("Other_Number", "No"), Map.entry("Other_Punctuation", "Po"),
            Map.entry("Other_Symbol", "So"), Map.entry("Paragraph_Separator", "Zp"),
            Map.entry("Private_Use", "Co"), Map.entry("Punctuation", "P"), Map.entry("punct", "P"),
            Map.entry("Separator", "Z"), Map.entry("Space_Separator", "Zs"),
            Map.entry("Spacing_Mark", "Mc"), Map.entry("Surrogate", "Cs"),
            Map.entry("Symbol", "S"), Map.entry("Titlecase_Letter", "Lt"),
            Map.entry("Unassigned", "Cn"), Map.entry("Uppercase_Letter", "Lu"));

    /** The one/two-letter General_Category codes, which the JDK takes as they are. */
    private static final Set<String> GENERAL_CATEGORY_CODES = Set.of(
            "C", "Cc", "Cf", "Cn", "Co", "Cs",
            "L", "LC", "Ll", "Lm", "Lo", "Lt", "Lu",
            "M", "Mc", "Me", "Mn",
            "N", "Nd", "Nl", "No",
            "P", "Pc", "Pd", "Pe", "Pf", "Pi", "Po", "Ps",
            "S", "Sc", "Sk", "Sm", "So",
            "Z", "Zl", "Zp", "Zs");

    /**
     * The binary properties the JDK engine answers for, as ECMAScript name (and
     * alias) to the JDK {@code Is...} spelling.
     */
    private static final Map<String, String> BINARY = Map.ofEntries(
            Map.entry("Alphabetic", "IsAlphabetic"), Map.entry("Alpha", "IsAlphabetic"),
            Map.entry("Assigned", "IsAssigned"),
            Map.entry("Hex_Digit", "IsHex_Digit"), Map.entry("Hex", "IsHex_Digit"),
            Map.entry("Ideographic", "IsIdeographic"), Map.entry("Ideo", "IsIdeographic"),
            Map.entry("Join_Control", "IsJoin_Control"), Map.entry("Join_C", "IsJoin_Control"),
            Map.entry("Lowercase", "IsLowercase"), Map.entry("Lower", "IsLowercase"),
            Map.entry("Noncharacter_Code_Point", "IsNoncharacter_Code_Point"), Map.entry("NChar", "IsNoncharacter_Code_Point"),
            Map.entry("Uppercase", "IsUppercase"), Map.entry("Upper", "IsUppercase"),
            Map.entry("White_Space", "IsWhite_Space"), Map.entry("space", "IsWhite_Space"));

    /**
     * Translate an ECMAScript property escape body to the JDK property body.
     *
     * @param raw the text between the braces of {@code \p{...}}
     * @return the body the JDK engine accepts, for {@code \p{...}}
     * @throws RuntimeException if the property cannot be expressed with the JDK engine
     */
    static String toJavaProperty(final String raw) {
        final int eq = raw.indexOf('=');
        if (eq >= 0) {
            // No trimming: ECMAScript matches property names and values exactly,
            // so surrounding whitespace (loose matching) must be rejected.
            final String name = raw.substring(0, eq);
            final String value = raw.substring(eq + 1);
            switch (name) {
            case "General_Category":
            case "gc":
                return generalCategory(value);
            case "Script":
            case "sc":
                return "script=" + value; // the JDK resolves Script value aliases
            case "Script_Extensions":
            case "scx":
                throw new RuntimeException("Script_Extensions is not supported");
            default:
                throw new RuntimeException("Unknown Unicode property name: " + name);
            }
        }
        // A lone name is a General_Category value or a binary property (exact,
        // no loose matching).
        final String value = raw;
        if (GENERAL_CATEGORY_CODES.contains(value)) {
            return value;
        }
        final String gc = GENERAL_CATEGORY.get(value);
        if (gc != null) {
            return gc;
        }
        final String bin = BINARY.get(value);
        if (bin != null) {
            return bin;
        }
        throw new RuntimeException("Unsupported Unicode property: " + value);
    }

    private static String generalCategory(final String value) {
        if (GENERAL_CATEGORY_CODES.contains(value)) {
            return value;
        }
        final String gc = GENERAL_CATEGORY.get(value);
        if (gc == null) {
            throw new RuntimeException("Unknown General_Category value: " + value);
        }
        return gc;
    }
}
