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

package org.openjdk.nashorn.internal.runtime;

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.openjdk.nashorn.internal.ir.Module;
import org.openjdk.nashorn.internal.objects.Global;

/**
 * A source text module record (ECMAScript 2015, 15.2.1.15).
 *
 * A module is compiled as one function whose body is not a program: its top
 * level declarations go into a scope of its own rather than into the global
 * object, and that scope is the module's environment. The function hands it over
 * as its first act, through {@link ScriptRuntime#MODULE_SCOPE}, which is the one
 * moment at which the imports can be installed - after the scope exists and
 * before any of the body has run.
 *
 * <p>An import is installed as an accessor that reads the exporting module's
 * scope, so the binding is live in the way the specification requires: a
 * function reassigning an exported variable is seen by everyone who imported it.
 * Nothing is copied.
 *
 * <p>Two things are simpler here than in the specification. Instantiation and
 * evaluation are one pass rather than two, so a cycle sees a module whose body
 * has started but not finished - which is what the specification says happens
 * for a cycle's back edge anyway, only reached by a different route. And an
 * export that names nothing is reported when it is read rather than when the
 * graph is linked.
 */
public final class ModuleRecord {
    /** Where a module says which scope is its own, while its body is starting. */
    private static final ThreadLocal<ModuleRecord> STARTING = new ThreadLocal<>();

    private enum State {
        /** compiled, not yet run */
        NEW,
        /** its body is running, or one of its dependencies is */
        EVALUATING,
        /** its body has finished */
        EVALUATED
    }

    private final String name;
    private final Module module;
    private final ScriptFunction body;
    private final Global global;

    private State state = State.NEW;
    private ScriptObject environment;

    ModuleRecord(final String name, final Module module, final ScriptFunction body, final Global global) {
        this.name = name;
        this.module = module;
        this.body = body;
        this.global = global;
    }

    /** The name this module was loaded under, for error messages. */
    public String getName() {
        return name;
    }

    /**
     * Runs the module's body, and everything it depends on first.
     *
     * @return the module itself, once its body has finished
     */
    public ModuleRecord evaluate() {
        if (state != State.NEW) {
            // already run, or being run further down the same stack: a cycle
            return this;
        }
        state = State.EVALUATING;

        for (final String requested : module.getRequestedModules()) {
            dependency(requested).evaluate();
        }

        final ModuleRecord previous = STARTING.get();
        STARTING.set(this);
        try {
            ScriptRuntime.apply(body, global);
        } finally {
            STARTING.set(previous);
        }
        state = State.EVALUATED;
        return this;
    }

    /**
     * Called from the module's own body once its scope exists.
     *
     * @param scope the module's environment
     */
    static void starting(final ScriptObject scope) {
        final ModuleRecord record = STARTING.get();
        if (record != null) {
            record.bind(scope);
        }
    }

    private void bind(final ScriptObject scope) {
        this.environment = scope;
        for (final Module.ImportEntry entry : module.getImportEntries()) {
            final ModuleRecord from = dependency(entry.getModuleRequest().getName());
            final String local = entry.getLocalName().getName();
            final String imported = entry.getImportName().getName();

            if (Module.STAR_NAME.equals(imported)) {
                scope.set(local, from.namespace(), 0);
            } else {
                // ES2015 15.2.1.16.4 step 12.b: an indirect binding, not a copy
                scope.addOwnProperty(local, Property.NOT_WRITABLE | Property.NOT_ENUMERABLE,
                        ScriptFunction.createBuiltin(local, ModuleBinding.reader(from, imported)), null);
            }
        }
    }

    /**
     * The value an importing module sees for one of this module's exports.
     *
     * @param exportName the name as exported
     * @return the current value of the binding behind it
     */
    public Object read(final String exportName) {
        for (final Module.ExportEntry entry : module.getLocalExportEntries()) {
            if (exportName.equals(entry.getExportName().getName())) {
                return local(entry.getLocalName().getName());
            }
        }
        for (final Module.ExportEntry entry : module.getIndirectExportEntries()) {
            if (exportName.equals(entry.getExportName().getName())) {
                return dependency(entry.getModuleRequest().getName())
                        .read(entry.getImportName().getName());
            }
        }
        for (final Module.ExportEntry entry : module.getStarExportEntries()) {
            final ModuleRecord from = dependency(entry.getModuleRequest().getName());
            if (from.exportNames().contains(exportName)) {
                return from.read(exportName);
            }
        }
        throw syntaxError("module.export.not.found", exportName, name);
    }

    /** Every name this module exports, including the ones it re-exports. */
    public Set<String> exportNames() {
        final Set<String> names = new LinkedHashSet<>();
        for (final Module.ExportEntry entry : module.getLocalExportEntries()) {
            names.add(entry.getExportName().getName());
        }
        for (final Module.ExportEntry entry : module.getIndirectExportEntries()) {
            names.add(entry.getExportName().getName());
        }
        for (final Module.ExportEntry entry : module.getStarExportEntries()) {
            names.addAll(dependency(entry.getModuleRequest().getName()).exportNames());
        }
        return names;
    }

    /**
     * The module namespace object (ES2015 9.4.6), which {@code import * as ns}
     * binds. Its properties read the exports as they stand.
     */
    public ScriptObject namespace() {
        final List<String> sorted = new ArrayList<>(exportNames());
        // 9.4.6.11: a namespace object's keys are sorted
        sorted.sort(null);
        return new ModuleNamespace(this, sorted);
    }

    private Object local(final String localName) {
        if (environment == null) {
            // reached through a cycle before this module's body started
            return ScriptRuntime.UNDEFINED;
        }
        return environment.get(localName);
    }

    private ModuleRecord dependency(final String specifier) {
        final ModuleRecord loaded = Context.getContext().loadModule(specifier, this);
        if (loaded == null) {
            throw typeError("module.not.found", specifier, name);
        }
        return loaded;
    }
}
