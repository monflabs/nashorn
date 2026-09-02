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
 * Serves modules contributed in pure Java: a registry of names, each mapped
 * to its exports - {@code "default"} for the default export. Exactly the
 * registered names are answered; everything else is null.
 *
 * <pre>{@code
 * new NashornScriptEngineBuilder()
 *     .moduleLoader(new JavaModuleLoader()
 *         .add("math", Map.of(
 *             "TAU", 2 * Math.PI,
 *             "add", addFunction,
 *             "default", mathObject)))     // what `import math from "math"` binds
 *     .build();
 * // script side:  import math, { TAU, add } from "math";
 * }</pre>
 *
 * <p>A module without a {@code "default"} entry has no default export, and a
 * default import of it fails at link time, as it would against a script
 * module that never wrote {@code export default}.
 *
 * @since 2017.0.0
 */
public final class JavaModuleLoader implements ModuleLoader {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    /** An empty registry. */
    public JavaModuleLoader() {
    }

    /**
     * Registers a module.
     *
     * @param name the name imports use
     * @param exports the exports, by name; {@code "default"} for the default export
     * @return this
     */
    public JavaModuleLoader add(final String name, final Map<String, Object> exports) {
        modules.put(Objects.requireNonNull(name, "name"), Module.values(name, exports));
        return this;
    }

    @Override
    public Module load(final String specifier, final Module referrer) {
        return modules.get(specifier);
    }

    @Override
    public String toString() {
        return "JavaModuleLoader" + modules.keySet();
    }
}
