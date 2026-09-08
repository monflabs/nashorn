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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.referenceError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
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

    /** Set while a module body runs its instantiation pass rather than its evaluation. */
    private static final ThreadLocal<Boolean> INSTANTIATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

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
        /** its instantiation pass is running, or one of its dependencies' is */
        INSTANTIATING,
        /** its scope exists and its declarations are hoisted, not yet evaluated */
        INSTANTIATED,
        /** its body is running, or one of its dependencies is */
        EVALUATING,
        /** ES2022: its synchronous part has run; it is waiting on an await (its own or a dependency's) */
        EVALUATING_ASYNC,
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
    private ScriptObject importMeta;
    private RuntimeException evaluationError;

    // ES2022 asynchronous module evaluation (16.2.1.5) bookkeeping.
    /** Assigns the order asynchronous modules become async, which is the order they later run in. */
    private static final java.util.concurrent.atomic.AtomicLong ASYNC_EVALUATION_ORDER = new java.util.concurrent.atomic.AtomicLong(1);
    private boolean asyncEvaluation;
    private long asyncEvaluationOrder;
    private int dfsIndex;
    private int dfsAncestorIndex;
    private int pendingAsyncDependencies;
    private ModuleRecord cycleRoot;
    private final java.util.List<ModuleRecord> asyncParentModules = new ArrayList<>();
    private org.monflabs.nashorn.internal.objects.NativePromise topLevelCapability;
    private boolean topLevelRejected;
    private Object topLevelRejectReason;

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
                final ModuleRecord dep = dependency(entry.getModuleRequest().getName());
                if (Module.STAR_NAME.equals(entry.getImportName().getName())) {
                    // ES2020 "export * as ns from "mod"": the name resolves to the
                    // whole namespace object of the re-exported module.
                    return new Binding(dep, NAMESPACE);
                }
                return dep.resolveExport(entry.getImportName().getName(), resolving);
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
     * The first of a module's two passes: brings the whole dependency graph, this
     * module last, to the point where every module's scope exists and its
     * declarations are hoisted, but nothing of any body has evaluated yet.
     *
     * ES2015 gave a module a scope of its own and let a cyclic dependency read a
     * name across the cycle. A single pass cannot honour both: run the bodies in
     * dependency order and a module up the cycle reads a name whose module has not
     * run, so its scope does not yet exist ({@code verify-dfs}). So the body runs
     * twice. This pass runs it with {@link #INSTANTIATING} set, so the compiled
     * body stops after its declarations (a {@link ScriptRuntime#MODULE_INSTANTIATING}
     * guard the code generator plants for a module): the scope is made, the imports
     * bound, and the function declarations hoisted, which is all a name read across
     * the cycle needs. {@link #evaluate()} then runs the same bodies for real.
     *
     * The scope this pass makes is transient - {@link #evaluate()} makes a fresh
     * one - and matters only in the window before a module's own evaluation, which
     * is exactly the window a cycle reads it in. Every binding is read through the
     * record ({@link #local}, {@link ModuleBinding}, {@link ModuleNamespace}), so a
     * read resolves against whichever scope is current when it happens.
     *
     * @return the module itself, once its graph is instantiated
     */
    private ModuleRecord instantiate() {
        if (values != null) {
            // a pure-Java module has no body to hoist; its exports are ready
            return this;
        }
        if (state == State.NEW) {
            link();
        }
        if (state != State.LINKED) {
            // already instantiated, or on the stack of one that is: a cycle
            return this;
        }
        state = State.INSTANTIATING;

        for (final String requested : module.getRequestedModules()) {
            dependency(requested).instantiate();
        }

        final ModuleRecord previous = STARTING.get();
        final Boolean wasInstantiating = INSTANTIATING.get();
        STARTING.set(this);
        INSTANTIATING.set(Boolean.TRUE);
        try {
            ScriptRuntime.apply(body, ScriptRuntime.UNDEFINED);
        } finally {
            INSTANTIATING.set(wasInstantiating);
            STARTING.set(previous);
        }

        state = State.INSTANTIATED;
        return this;
    }

    /**
     * Runs the module's body, and everything it depends on first (ES2022
     * 16.2.1.5.2 Evaluate). The graph may contain top-level await, so evaluation
     * is asynchronous under the hood; this drives it to completion on the realm's
     * event loop and re-throws the module's evaluation error once it settles, so
     * an embedder keeps the synchronous contract it had before top-level await.
     *
     * @return the module itself, once the graph has finished evaluating
     */
    public ModuleRecord evaluate() {
        JobQueue.enterScript();
        try {
            evaluateToPromise();
        } finally {
            if (JobQueue.exitScriptShouldDrain()) {
                global.getJobQueue().drain();
            }
        }
        if (evaluationError != null) {
            throw evaluationError;
        }
        if (topLevelRejected) {
            throw ECMAException.create(topLevelRejectReason, null, -1, -1);
        }
        return this;
    }

    /**
     * ES2022 16.2.1.5.2 Evaluate without draining: links and instantiates the
     * graph (synchronously - the instantiation pass only declares, it does not
     * await) and starts the asynchronous evaluation, returning the promise that
     * settles when the whole graph is done. Dynamic import chains on this.
     *
     * @return the top-level evaluation promise
     */
    org.monflabs.nashorn.internal.objects.NativePromise evaluateToPromise() {
        if (values != null) {
            state = State.EVALUATED;
            final org.monflabs.nashorn.internal.objects.NativePromise settled = org.monflabs.nashorn.internal.objects.NativePromise.newAsyncPromise(global);
            org.monflabs.nashorn.internal.objects.NativePromise.resolveAsyncPromise(settled, ScriptRuntime.UNDEFINED);
            return settled;
        }
        if (state == State.NEW) {
            link();
        }
        if (state == State.LINKED) {
            // instantiate the whole graph before any body evaluates, so a cyclic
            // dependency can read a name whose module has not run yet
            try {
                instantiate();
            } catch (final RuntimeException | Error e) {
                state = State.EVALUATED;
                evaluationError = e instanceof RuntimeException re ? re : new RuntimeException(e);
                final org.monflabs.nashorn.internal.objects.NativePromise rejected = org.monflabs.nashorn.internal.objects.NativePromise.newAsyncPromise(global);
                org.monflabs.nashorn.internal.objects.NativePromise.rejectAsyncPromise(rejected, errorValue(e));
                return rejected;
            }
        }
        ModuleRecord module = this;
        if (module.state == State.EVALUATING_ASYNC || module.state == State.EVALUATED) {
            // a re-evaluation resolves against the cycle root, as the spec does
            module = module.cycleRoot != null ? module.cycleRoot : module;
        }
        if (module.topLevelCapability != null) {
            return module.topLevelCapability;
        }
        final Deque<ModuleRecord> stack = new ArrayDeque<>();
        final org.monflabs.nashorn.internal.objects.NativePromise capability = org.monflabs.nashorn.internal.objects.NativePromise.newAsyncPromise(global);
        module.topLevelCapability = capability;
        // Capture the settlement so evaluate() can re-throw a rejection as the
        // embedder's synchronous error - and so the rejection counts as handled.
        final ModuleRecord root = module;
        org.monflabs.nashorn.internal.objects.NativePromise.await(global, capability, value -> { },
                reason -> { root.topLevelRejected = true; root.topLevelRejectReason = reason; });
        try {
            module.innerModuleEvaluation(stack, 0);
        } catch (final RuntimeException err) {
            for (final ModuleRecord m : stack) {
                m.state = State.EVALUATED;
                m.evaluationError = err;
            }
            evaluationError = err;
            org.monflabs.nashorn.internal.objects.NativePromise.rejectAsyncPromise(capability, errorValue(err));
            return capability;
        }
        if (!module.asyncEvaluation) {
            org.monflabs.nashorn.internal.objects.NativePromise.resolveAsyncPromise(capability, ScriptRuntime.UNDEFINED);
        }
        return capability;
    }

    /** Whether this module awaits at its own top level, making its evaluation asynchronous. */
    private boolean hasTopLevelAwait() {
        return module != null && module.hasTopLevelAwait();
    }

    /**
     * ES2022 16.2.1.5.2.1 InnerModuleEvaluation: the depth-first walk that runs
     * each module after its dependencies, tracking strongly-connected components
     * so a cycle evaluates as one unit and an asynchronous dependency defers the
     * modules waiting on it.
     */
    private int innerModuleEvaluation(final Deque<ModuleRecord> stack, final int indexIn) {
        int index = indexIn;
        if (values != null) {
            // a pure-Java module has no body and no dependencies: it is evaluated
            // the moment it is reached, and is its own (trivial) cycle root
            if (state != State.EVALUATED) {
                state = State.EVALUATED;
                cycleRoot = this;
            }
            return index;
        }
        if (state == State.EVALUATING_ASYNC || state == State.EVALUATED) {
            if (evaluationError == null) {
                return index;
            }
            throw evaluationError;
        }
        if (state == State.EVALUATING) {
            return index;
        }
        state = State.EVALUATING;
        dfsIndex = index;
        dfsAncestorIndex = index;
        pendingAsyncDependencies = 0;
        index++;
        stack.addLast(this);

        for (final String requested : module.getRequestedModules()) {
            ModuleRecord required = dependency(requested);
            index = required.innerModuleEvaluation(stack, index);
            if (required.state == State.EVALUATING) {
                dfsAncestorIndex = Math.min(dfsAncestorIndex, required.dfsAncestorIndex);
            } else {
                // evaluating-async or evaluated: what this module waits on is the
                // dependency's cycle, named by its root.
                required = required.cycleRoot != null ? required.cycleRoot : required;
            }
            // ES2023 InnerModuleEvaluation 16.2.1.5.3 step 11.c.vi (with the 2025
            // erratum): a dependency that will settle asynchronously is a pending
            // async dependency this module waits on - not only a finished cycle's
            // evaluating-async root, but a member of this very cycle that is still
            // evaluating and already known to be async. Counting the latter is
            // what makes a cyclic async dependency run to completion before its
            // waiter's body, rather than interleaving. One fully evaluated is done
            // and adds nothing, or the waiter would never be released.
            if (required.asyncEvaluation && required.state != State.EVALUATED) {
                pendingAsyncDependencies++;
                required.asyncParentModules.add(this);
            }
        }

        if (pendingAsyncDependencies > 0 || hasTopLevelAwait()) {
            asyncEvaluation = true;
            asyncEvaluationOrder = ASYNC_EVALUATION_ORDER.getAndIncrement();
            if (pendingAsyncDependencies == 0) {
                executeAsyncModule();
            }
        } else {
            runBody();
        }

        if (dfsAncestorIndex == dfsIndex) {
            ModuleRecord m;
            do {
                m = stack.removeLast();
                m.state = m.asyncEvaluation ? State.EVALUATING_ASYNC : State.EVALUATED;
                m.cycleRoot = this;
            } while (m != this);
        }
        return index;
    }

    /** Runs the module body synchronously on the current thread, with its STARTING binding set. */
    private void runBody() {
        final ModuleRecord previous = STARTING.get();
        STARTING.set(this);
        try {
            ScriptRuntime.apply(body, ScriptRuntime.UNDEFINED);
        } finally {
            STARTING.set(previous);
        }
    }

    /**
     * ES2022 ExecuteAsyncModule: runs an asynchronous module's body. One with a
     * top-level await runs on its own thread so the await can suspend it; one that
     * is asynchronous only because a dependency is has no await, so it runs
     * synchronously and reports completion as a job, matching a resolved
     * capability's reaction order.
     */
    private void executeAsyncModule() {
        if (hasTopLevelAwait()) {
            final Object bodyPromise = AsyncSupport.start(body, ScriptRuntime.UNDEFINED, ScriptRuntime.EMPTY_ARRAY,
                    global, () -> { STARTING.set(this); INSTANTIATING.set(Boolean.FALSE); });
            org.monflabs.nashorn.internal.objects.NativePromise.await(global, bodyPromise,
                    value -> asyncModuleExecutionFulfilled(),
                    reason -> asyncModuleExecutionRejected(reason));
        } else {
            RuntimeException failure = null;
            try {
                runBody();
            } catch (final RuntimeException err) {
                failure = err;
            }
            final RuntimeException thrown = failure;
            global.getJobQueue().enqueue(() -> {
                if (thrown != null) {
                    asyncModuleExecutionRejected(errorValue(thrown));
                } else {
                    asyncModuleExecutionFulfilled();
                }
            });
        }
    }

    /**
     * ES2022 AsyncModuleExecutionFulfilled: this module's body has finished, so it
     * is evaluated; every ancestor whose last pending dependency this was may now
     * run, in dependency order.
     */
    private void asyncModuleExecutionFulfilled() {
        if (state == State.EVALUATED) {
            return; // already errored
        }
        state = State.EVALUATED;
        if (topLevelCapability != null) {
            org.monflabs.nashorn.internal.objects.NativePromise.resolveAsyncPromise(topLevelCapability, ScriptRuntime.UNDEFINED);
        }
        final List<ModuleRecord> execList = new ArrayList<>();
        gatherAvailableAncestors(execList);
        execList.sort(Comparator.comparingLong(m -> m.asyncEvaluationOrder));
        for (final ModuleRecord m : execList) {
            if (m.state == State.EVALUATED) {
                continue; // errored
            }
            if (m.hasTopLevelAwait()) {
                m.executeAsyncModule();
            } else {
                try {
                    m.runBody();
                } catch (final RuntimeException err) {
                    m.asyncModuleExecutionRejected(errorValue(err));
                    continue;
                }
                m.state = State.EVALUATED;
                if (m.topLevelCapability != null) {
                    org.monflabs.nashorn.internal.objects.NativePromise.resolveAsyncPromise(m.topLevelCapability, ScriptRuntime.UNDEFINED);
                }
            }
        }
    }

    /**
     * ES2022 GatherAvailableAncestors: decrements each async parent's pending
     * count and collects those that have become ready, descending through
     * synchronous ones whose completion is immediate.
     */
    private void gatherAvailableAncestors(final List<ModuleRecord> execList) {
        for (final ModuleRecord parent : asyncParentModules) {
            if (execList.contains(parent)) {
                continue;
            }
            if (parent.cycleRoot != null && parent.cycleRoot.evaluationError != null) {
                continue;
            }
            parent.pendingAsyncDependencies--;
            if (parent.pendingAsyncDependencies == 0) {
                execList.add(parent);
                if (!parent.hasTopLevelAwait()) {
                    parent.gatherAvailableAncestors(execList);
                }
            }
        }
    }

    /**
     * ES2022 AsyncModuleExecutionRejected: this module's evaluation threw; it and
     * every module waiting on it are permanently errored with the same reason.
     */
    private void asyncModuleExecutionRejected(final Object reason) {
        if (state == State.EVALUATED) {
            return; // already settled
        }
        state = State.EVALUATED;
        evaluationError = ECMAException.create(reason, null, -1, -1);
        // 16.2.1.5.2.4 steps 9-10: reject this module's own top-level capability
        // *before* recursing into its async parents, so a graph settles its
        // rejections leaf-to-root (a child's dynamic import() rejects ahead of
        // its parent's).
        if (topLevelCapability != null) {
            org.monflabs.nashorn.internal.objects.NativePromise.rejectAsyncPromise(topLevelCapability, reason);
        }
        for (final ModuleRecord parent : asyncParentModules) {
            parent.asyncModuleExecutionRejected(reason);
        }
    }

    /** The JavaScript value to settle a promise with for a Java throwable from evaluation. */
    private Object errorValue(final Throwable t) {
        if (t instanceof ECMAException ee) {
            return ee.getThrown();
        }
        if (t instanceof ParserException pe) {
            return ECMAErrors.asEcmaException(global, pe).getThrown();
        }
        return t == null ? ScriptRuntime.UNDEFINED : String.valueOf(t.getMessage());
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

    /**
     * Whether the module body now running is doing so for its instantiation pass
     * rather than its evaluation. The compiled body asks this, through
     * {@link ScriptRuntime#MODULE_INSTANTIATING}, to know whether to stop after
     * its declarations.
     *
     * @return true if the current thread is inside {@link #instantiate()}
     */
    static boolean isInstantiating() {
        return INSTANTIATING.get();
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

    /**
     * ES2020 the {@code import.meta} object for this module: an ordinary object,
     * host-populated, created once and shared by every {@code import.meta} in the
     * module. It carries {@code url}, the module's origin.
     *
     * @return the import.meta object
     */
    public ScriptObject getImportMeta() {
        if (importMeta == null) {
            importMeta = global.newObject();
            importMeta.set("url", origin == null ? name : origin.toString(), 0);
        }
        return importMeta;
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
