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

package org.monflabs.nashorn.internal.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * Installs the script libraries a context applies into a global. Libraries are
 * contributed imperatively - the embedder hands them to the engine builder's
 * {@code library(...)} - never discovered; there is no service lookup and no
 * option to select among them.
 */
final class ScriptLibraries {
    private ScriptLibraries() {
    }

    /**
     * The libraries a context applies, in the order they run: exactly the ones
     * the embedder handed over, deduplicated by name so a later library replaces
     * an earlier one of the same name.
     * @param explicit the libraries the embedder passed (may be null)
     */
    static List<ScriptLibrary> resolve(final List<ScriptLibrary> explicit) {
        if (explicit == null || explicit.isEmpty()) {
            return List.of();
        }
        final Map<String, ScriptLibrary> byName = new LinkedHashMap<>();
        for (final ScriptLibrary library : explicit) {
            final String name = library.name();
            if (name == null) {
                throw new IllegalStateException("script library " + library.getClass().getName() + " has no name");
            }
            byName.put(name, library);
        }
        return List.copyOf(byName.values());
    }

    /**
     * Defines the libraries' globals, runs their scripts and calls their
     * initializers in a global that has its built-ins, one library after the
     * other. The current realm must be the global's.
     */
    static void install(final Context context, final Global global, final List<ScriptLibrary> libraries) {
        if (libraries.isEmpty()) {
            return;
        }
        final JSObject mirror = (JSObject)ScriptObjectMirror.wrap(global, global);
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
                stage = "initialize";
                library.initialize(mirror);
            } catch (final RuntimeException e) {
                throw new IllegalStateException("script library '" + library.name() + "' failed in " + stage + ": " + e.getMessage(), e);
            }
        }
    }
}
