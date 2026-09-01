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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.referenceError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.monflabs.nashorn.internal.ir.Module;
import org.monflabs.nashorn.internal.objects.Global;

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
 * <p>Linking and evaluation are separate passes. {@link #link} loads the whole
 * graph and resolves every import and every re-export against it, so a name that
 * is exported by nobody, or by two modules at once, is a SyntaxError before any
 * module body has run - which is what the specification means by an error at
 * instantiation. Evaluation is then the depth-first walk of the same graph.
 */
public final class ModuleRecord {
    /** Where a module says which scope is its own, while its body is starting. */
    private static final ThreadLocal<ModuleRecord> STARTING = new ThreadLocal<>();

    /** What ResolveExport answers when a name is exported by two modules at once. */
    private static final Binding AMBIGUOUS = new Binding(null, null);

    /** What a re-exported namespace object is bound as, rather than by name. */
    private static final String NAMESPACE = "*namespace*";

    /**
     * A name in the module that holds it (15.2.1.16.3).
     *
     * @param module where the binding lives
     * @param name   what it is called there
     */
    private record Binding(ModuleRecord module, String name) {
    }

    private enum State {
        /** compiled, not yet linked */
        NEW,
        /** its dependencies are being loaded, or it is on the stack of a link that is */
        LINKING,
        /** linked, not yet run */
        LINKED,
        /** its body is running, or one of its dependencies is */
        EVALUATING,
        /** its body has finished */
        EVALUATED
    }

    private final String name;
    private final Module module;                          // null for a values-backed module
    private final ScriptFunction body;
    private final Global global;
    private final java.util.Map<String, Object> values;   // a pure-Java module's exports; null for script
    private final Object origin;                          // what the loader that made this stored

    private State state = State.NEW;
    private ScriptObject environment;
    private ScriptObject namespace;

    /** What this module's specifiers resolved to: link, bind and evaluate all ask, the loaders answer once. */
    private final java.util.Map<String, ModuleRecord> dependencies = new java.util.HashMap<>();

    ModuleRecord(final String name, final Module module, final ScriptFunction body, final Global global, final Object origin) {
        this.name = name;
        this.module = module;
        this.body = body;
        this.global = global;
        this.values = null;
        this.origin = origin;
    }

    /** A module whose exports are Java values: nothing to link, nothing to run. */
    ModuleRecord(final String name, final java.util.Map<String, Object> values, final Global global) {
        this.name = name;
        this.module = null;
        this.body = null;
        this.global = global;
        this.values = values;
        this.origin = null;
    }

    /** The public view of this module, handed to loaders as the referrer. */
    org.monflabs.nashorn.api.modules.Module moduleView() {
        return org.monflabs.nashorn.api.modules.Module.referrer(name, origin);
    }

    /** The name this module was loaded under, for error messages. */
    public String getName() {
        return name;
    }

    /**
     * Loads everything this module depends on and resolves every name it names.
     *
     * ES2015 15.2.1.16.4: an import of a name nothing exports, or of one two
     * modules export under the same name, is a SyntaxError - and it is one
     * before any of the graph runs, which is why this is a pass of its own. A
     * dependency that will not parse is reported here too, for the same reason.
     *
     * @return the module itself
     */
    public ModuleRecord link() {
        if (values != null) {
            state = State.LINKED;
            return this;
        }
        if (state != State.NEW) {
            // already linked, or on the stack of a link that is: a cycle
            return this;
        }
        state = State.LINKING;

        for (final String requested : module.getRequestedModules()) {
            dependency(requested).link();
        }

        for (final Module.ExportEntry entry : module.getIndirectExportEntries()) {
            required(resolveExport(entry.getExportName().getName(), new HashSet<>()),
                    entry.getExportName().getName());
        }
        for (final Module.ImportEntry entry : module.getImportEntries()) {
            final String imported = entry.getImportName().getName();
            if (!Module.STAR_NAME.equals(imported)) {
                required(dependency(entry.getModuleRequest().getName())
                        .resolveExport(imported, new HashSet<>()), imported);
            }
        }

        state = State.LINKED;
        return this;
    }

    private void required(final Binding resolution, final String exportName) {
        if (resolution == null) {
            throw syntaxError("module.export.not.found", exportName, name);
        }
        if (resolution == AMBIGUOUS) {
            throw syntaxError("module.export.ambiguous", exportName, name);
        }
    }

    /**
     * ES2015 15.2.1.16.3 ResolveExport: which binding, in which module, one of
     * this module's export names stands for.
     *
     * @param exportName the name as exported
     * @param resolving  the (module, name) pairs already being resolved, which is
     *                   how a cycle ends rather than repeating
     * @return the binding, null if nothing exports the name, or {@link #AMBIGUOUS}
     */
    private Binding resolveExport(final String exportName, final Set<String> resolving) {
        if (values != null) {
            return values.containsKey(exportName) ? new Binding(this, exportName) : null;
        }
        if (!resolving.add(name + "\u0000" + exportName)) {
            // this module is already being asked the same question further up
            // the stack: it is a cycle, and answering again would not end
            return null;
        }

        for (final Module.ExportEntry entry : module.getLocalExportEntries()) {
            if (exportName.equals(entry.getExportName().getName())) {
                final String local = entry.getLocalName().getName();
                final Module.ImportEntry namespaceImport = namespaceImportOf(local);
                if (namespaceImport != null) {
                    // "import * as ns; export {ns}" re-exports the namespace
                    // object of the module it came from, and names that module
                    // rather than this one - so two modules re-exporting the
                    // same namespace agree about it instead of clashing
                    return new Binding(dependency(namespaceImport.getModuleRequest().getName()), NAMESPACE);
                }
                return new Binding(this, local);
            }
        }
        for (final Module.ExportEntry entry : module.getIndirectExportEntries()) {
            if (exportName.equals(entry.getExportName().getName())) {
                return dependency(entry.getModuleRequest().getName())
                        .resolveExport(entry.getImportName().getName(), resolving);
            }
        }
        if (Module.DEFAULT_NAME.equals(exportName)) {
            // 15.2.1.16.3 step 5: export * never carries a default
            return null;
        }

        Binding star = null;
        for (final Module.ExportEntry entry : module.getStarExportEntries()) {
            final Binding resolution = dependency(entry.getModuleRequest().getName())
                    .resolveExport(exportName, resolving);
            if (resolution == AMBIGUOUS) {
                return AMBIGUOUS;
            }
            if (resolution != null) {
                if (star == null) {
                    star = resolution;
                } else if (star.module() != resolution.module() || !star.name().equals(resolution.name())) {
                    return AMBIGUOUS;
                }
            }
        }
        return star;
    }

    /**
     * Runs the module's body, and everything it depends on first.
     *
     * @return the module itself, once its body has finished
     */
    public ModuleRecord evaluate() {
        if (values != null) {
            state = State.EVALUATED;
            return this;
        }
        if (state == State.NEW) {
            link();
        }
        if (state != State.LINKED) {
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
            ScriptRuntime.apply(body, ScriptRuntime.UNDEFINED);
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
                // 15.2.1.16.4 step 12.a: an immutable binding, like every import.
                // The name is already declared, so the property is modified
                // where it stands rather than added again.
                final Property property = scope.getMap().findProperty(local);
                if (property != null) {
                    scope.modifyOwnProperty(property, property.getFlags() | Property.NOT_WRITABLE);
                }
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
        final Binding resolution = resolveExport(exportName, new HashSet<>());
        if (resolution == null || resolution == AMBIGUOUS) {
            throw syntaxError("module.export.not.found", exportName, name);
        }
        if (NAMESPACE.equals(resolution.name())) {
            return resolution.module().namespace();
        }
        return resolution.module().local(resolution.name());
    }

    /**
     * ES2015 15.2.1.16.2 GetExportedNames: every name this module exports,
     * including the ones it re-exports.
     *
     * @return the names, in the order the specification collects them
     */
    public Set<String> exportNames() {
        return exportNames(new HashSet<>());
    }

    private Set<String> exportNames(final Set<String> visited) {
        final Set<String> names = new LinkedHashSet<>();
        if (values != null) {
            names.addAll(values.keySet());
            return names;
        }
        if (!visited.add(name)) {
            // a cycle of export * declarations, which the specification ends by
            // answering with nothing rather than by going round again
            return names;
        }
        for (final Module.ExportEntry entry : module.getLocalExportEntries()) {
            names.add(entry.getExportName().getName());
        }
        for (final Module.ExportEntry entry : module.getIndirectExportEntries()) {
            names.add(entry.getExportName().getName());
        }
        for (final Module.ExportEntry entry : module.getStarExportEntries()) {
            for (final String starred : dependency(entry.getModuleRequest().getName()).exportNames(visited)) {
                if (!Module.DEFAULT_NAME.equals(starred)) {
                    names.add(starred);
                }
            }
        }
        return names;
    }

    /**
     * The module namespace object (ES2015 9.4.6), which {@code import * as ns}
     * binds. Its properties read the exports as they stand.
     *
     * <p>A name two modules export at once is left out rather than reported: it
     * is only an error where it is named, which the namespace object does not do.
     * The same object is answered every time, because 15.2.1.18 makes a module's
     * namespace its own and equality between two imports of it is observable.
     *
     * @return the namespace object
     */
    public ScriptObject namespace() {
        if (namespace == null) {
            final List<String> sorted = new ArrayList<>();
            for (final String exported : exportNames()) {
                if (resolveExport(exported, new HashSet<>()) != AMBIGUOUS) {
                    sorted.add(exported);
                }
            }
            // 9.4.6.11: a namespace object's keys are sorted
            sorted.sort(null);
            namespace = new ModuleNamespace(this, sorted);
        }
        return namespace;
    }

    /** The namespace import that binds a name here, if that is what binds it. */
    private Module.ImportEntry namespaceImportOf(final String localName) {
        for (final Module.ImportEntry entry : module.getImportEntries()) {
            if (localName.equals(entry.getLocalName().getName())
                    && Module.STAR_NAME.equals(entry.getImportName().getName())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The value of one of this module's own bindings.
     *
     * ES2015 8.1.1.1.6 GetBindingValue: a binding that has not been initialised
     * yet is a ReferenceError to read, and an import is a read of the exporting
     * module's binding rather than a copy of it - so an import read before the
     * module it came from has run, whether through a cycle or through a
     * namespace object, is one too.
     *
     * @param localName the name in this module
     * @return its value
     */
    private Object local(final String localName) {
        if (values != null) {
            return values.get(localName);
        }
        if (environment == null) {
            // reached through a cycle before this module's body started, so
            // nothing it declares has been initialised
            throw referenceError("not.defined", localName);
        }
        final FindProperty found = environment.findProperty(localName, false);
        if (found == null) {
            return ScriptRuntime.UNDEFINED;
        }
        if (found.getProperty().needsDeclaration()) {
            throw referenceError("not.defined", localName);
        }
        return found.getObjectValue();
    }

    private ModuleRecord dependency(final String specifier) {
        ModuleRecord loaded = dependencies.get(specifier);
        if (loaded == null) {
            loaded = Context.getContext().loadModule(specifier, this);
            if (loaded == null) {
                throw typeError("module.not.found", specifier, name);
            }
            dependencies.put(specifier, loaded);
        }
        return loaded;
    }
}
