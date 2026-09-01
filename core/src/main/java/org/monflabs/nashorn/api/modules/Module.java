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

package org.monflabs.nashorn.api.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a {@link ModuleLoader} answers with: a script module - a name and its
 * source text - or a pure-Java module, whose exports are Java values.
 *
 * <p>The {@link #name()} is the module's canonical identity: the key of the
 * once-per-realm registry - two loads answering the same name are one module -
 * and what stack traces show. Loaders keep their names disjoint: an absolute
 * file path, a {@code classpath:} URL, a registered name.
 *
 * @since 2017.0.0
 */
public final class Module {
    private final String name;
    private final String text;
    private final Map<String, Object> exports;
    private final Object origin;

    private Module(final String name, final String text, final Map<String, Object> exports, final Object origin) {
        this.name = Objects.requireNonNull(name, "name");
        this.text = text;
        this.exports = exports;
        this.origin = origin;
    }

    /**
     * A script module.
     *
     * @param name the canonical name
     * @param text the source
     * @return the module
     */
    public static Module source(final String name, final String text) {
        return source(name, text, null);
    }

    /**
     * A script module with a resolution context of the loader's own, handed
     * back as the {@link #origin()} of the referrer when this module's
     * imports are resolved.
     *
     * @param name the canonical name
     * @param text the source
     * @param origin anything the loader wants to remember - a path, a resource prefix
     * @return the module
     */
    public static Module source(final String name, final String text, final Object origin) {
        return new Module(name, Objects.requireNonNull(text, "text"), null, origin);
    }

    /**
     * A pure-Java module: its exports are these values, the {@code "default"}
     * key being the default export. The values are fixed - imports of them are
     * not live bindings - and shared by every realm, each of which gets its
     * own namespace object over them.
     *
     * @param name the canonical name
     * @param exports the exports, by name; the iteration order is kept
     * @return the module
     */
    public static Module values(final String name, final Map<String, Object> exports) {
        return new Module(name, null, new LinkedHashMap<>(Objects.requireNonNull(exports, "exports")), null);
    }

    /**
     * The engine-made view of an importing module, handed to loaders as the
     * referrer; loaders read it and never build one.
     *
     * @param name the referrer's canonical name
     * @param origin what the loader that made it stored
     * @return the view
     */
    public static Module referrer(final String name, final Object origin) {
        return new Module(name, null, null, origin);
    }

    /** The canonical name. @return the name */
    public String name() {
        return name;
    }

    /** The source of a script module; null for a values module or a referrer view. @return the text */
    public String text() {
        return text;
    }

    /** The exports of a values module; null for a script module. @return the exports */
    public Map<String, Object> exports() {
        return exports;
    }

    /** What the loader stored when it made this module; null unless it did. @return the origin */
    public Object origin() {
        return origin;
    }

    @Override
    public String toString() {
        return "Module " + name;
    }
}
