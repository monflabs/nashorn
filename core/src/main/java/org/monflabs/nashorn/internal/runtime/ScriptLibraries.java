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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * Finds the script libraries a context applies, and installs them into a
 * global: the service providers its class loader offers, filtered by the
 * {@code --libraries} option, then the ones the embedder handed over, which
 * are not filtered and replace a discovered library of the same name.
 */
final class ScriptLibraries {
    private ScriptLibraries() {
    }

    /**
     * The libraries a context applies, in the order they run.
     * @param selection the {@code --libraries} option: {@code all}, {@code none}, or names
     * @param explicit the libraries the embedder passed
     * @param loader the loader providers are looked up through
     */
    static List<ScriptLibrary> resolve(final String selection, final List<ScriptLibrary> explicit, final ClassLoader loader) {
        final Map<String, ScriptLibrary> byName = new LinkedHashMap<>();
        final String choice = selection == null ? "all" : selection.trim();
        if (!choice.equals("none")) {
            final Set<String> wanted = choice.equals("all") ? null : Set.of(choice.split("\\s*,\\s*"));
            final ClassLoader through = loader != null ? loader : Thread.currentThread().getContextClassLoader();
            for (final ScriptLibrary library : ServiceLoader.load(ScriptLibrary.class, through)) {
                final String name = library.name();
                if (name == null) {
                    throw new IllegalStateException("script library " + library.getClass().getName() + " has no name");
                }
                if (wanted == null || wanted.contains(name)) {
                    byName.putIfAbsent(name, library);
                }
            }
        }
        for (final ScriptLibrary library : explicit) {
            byName.put(library.name(), library);
        }
        return List.copyOf(byName.values());
    }

    /**
     * Defines the libraries' globals and runs their scripts in a global that
     * has its built-ins. The current realm must be the global's.
     */
    static void install(final Context context, final Global global, final List<ScriptLibrary> libraries) {
        for (final ScriptLibrary library : libraries) {
            String stage = "its globals";
            try {
                for (final Map.Entry<String, Object> entry : library.globals().entrySet()) {
                    global.put(entry.getKey(), entry.getValue(), false);
                }
                for (final ScriptLibrary.Script script : library.scripts()) {
                    stage = script.name();
                    context.evaluateSource(Source.sourceFor(script.name(), script.text()), global, global);
                }
            } catch (final RuntimeException e) {
                throw new IllegalStateException("script library '" + library.name() + "' failed in " + stage + ": " + e.getMessage(), e);
            }
        }
    }

    /** A list that tolerates a null argument from an older caller. */
    static List<ScriptLibrary> listOf(final List<ScriptLibrary> libraries) {
        return libraries == null ? List.of() : new ArrayList<>(libraries);
    }
}
