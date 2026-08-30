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

package org.monflabs.nashorn.internal.runtime;

/**
 * Whether any script has installed one of the well-known symbols that would
 * change the meaning of an operator or a builtin.
 *
 * ECMAScript 2015 lets a program redefine {@code instanceof}, coercion to a
 * primitive, and how {@code concat} treats a value, by giving an object a
 * {@code Symbol.hasInstance}, {@code Symbol.toPrimitive} or
 * {@code Symbol.isConcatSpreadable} property, and to redirect
 * {@code String.prototype.match}, {@code replace}, {@code search} and
 * {@code split} with the matching symbol. Consulting them unconditionally
 * would put a symbol lookup on paths that run constantly, so each is checked
 * only after a program has stored that symbol on something - which almost no
 * program ever does.
 *
 * The flags are one-way: once set they stay set, since the property could have
 * been copied elsewhere or the object could still be reachable. They are
 * deliberately global rather than per-Global; the alternative is a switch point
 * per realm, which costs more than it saves for a flag that is almost always
 * false and, once true, stays true.
 */
public final class WellKnownSymbols {
    private static volatile boolean hasInstance;
    private static volatile boolean toPrimitive;
    private static volatile boolean isConcatSpreadable;
    private static volatile boolean toStringTag;
    private static volatile boolean stringMethods;

    private WellKnownSymbols() {
    }

    /**
     * Records that a symbol has been stored as a property key, so the paths that
     * would honour it must start looking.
     *
     * @param key the property key being stored
     */
    public static void note(final Object key) {
        if (!(key instanceof Symbol symbol)) {
            return;
        }
        switch (symbol.getName()) {
            case "Symbol.hasInstance" -> hasInstance = true;
            case "Symbol.toPrimitive" -> toPrimitive = true;
            case "Symbol.isConcatSpreadable" -> isConcatSpreadable = true;
            case "Symbol.toStringTag" -> toStringTag = true;
            case "Symbol.match", "Symbol.replace", "Symbol.search", "Symbol.split" -> stringMethods = true;
            default -> { }
        }
    }

    /** @return whether Symbol.hasInstance has ever been installed */
    public static boolean hasInstanceInstalled() {
        return hasInstance;
    }

    /** @return whether Symbol.toPrimitive has ever been installed */
    public static boolean toPrimitiveInstalled() {
        return toPrimitive;
    }

    /** @return whether Symbol.isConcatSpreadable has ever been installed */
    public static boolean isConcatSpreadableInstalled() {
        return isConcatSpreadable;
    }

    /** @return whether Symbol.toStringTag has ever been installed */
    public static boolean toStringTagInstalled() {
        return toStringTag;
    }

    /**
     * Whether any of Symbol.match, replace, search or split has ever been
     * installed, which is what lets an object other than a regular expression
     * decide what String.prototype.match and its three siblings do (ES2015
     * 21.1.3.11, .14, .15, .17).
     *
     * The four share one flag: they appear together, and the only thing the flag
     * decides is whether those four methods look for a symbol at all.
     *
     * @return whether any of the four has been installed
     */
    public static boolean stringMethodsInstalled() {
        return stringMethods;
    }
}
