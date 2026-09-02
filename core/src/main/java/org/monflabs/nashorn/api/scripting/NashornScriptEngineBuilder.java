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

package org.monflabs.nashorn.api.scripting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.monflabs.nashorn.api.modules.ModuleLoader;
import java.util.TimeZone;
import javax.script.ScriptEngine;

/**
 * Builds a script engine, one choice at a time: options, in their command-line
 * spelling or by name; the class loader scripts reach Java through; a class
 * filter; script libraries.
 *
 * <pre>{@code
 * ScriptEngine engine = new NashornScriptEngineBuilder()
 *         .annexB(false)
 *         .strict(true)
 *         .classLoader(myLoader)
 *         .classFilter(name -> name.startsWith("com.example."))
 *         .library(geometry)
 *         .build();
 * }</pre>
 *
 * <p>A builder starts with no options at all - what {@code jjs} runs with -
 * and adds what it is told, in order; a later setting of the same option wins,
 * as on a command line. The named methods cover the engine's configuration;
 * {@link #option(String...)} takes anything else - the diagnostic switches,
 * {@code --log}, the {@code --print-*} family - in its command-line spelling.
 * {@link #build()} validates the options as the command
 * line would and throws {@link IllegalArgumentException} for one it does not
 * know. A builder can be reused: every {@code build()} makes a new engine
 * with its own compiled-code cache and globals.
 *
 * <p>This replaces the {@code getScriptEngine} overloads of
 * {@link NashornScriptEngineFactory}, which stay for compatibility; the
 * factory's no-argument {@code getScriptEngine()} remains the
 * {@code javax.script} entry point and is not going anywhere.
 *
 * @since 2017.0.0
 */
public final class NashornScriptEngineBuilder {
    private final List<String> options = new ArrayList<>();
    private final List<ScriptLibrary> libraries = new ArrayList<>();
    private final List<ModuleLoader> moduleLoaders = new ArrayList<>();
    private ClassLoader classLoader;
    private ClassFilter classFilter;

    /** A builder with no options. */
    public NashornScriptEngineBuilder() {
    }

    // -- options ------------------------------------------------------------------------

    /**
     * Adds options in their command-line spelling - {@code "--annexB=false"},
     * {@code "-strict"}, {@code "--libraries=host"} - for anything the named
     * methods below do not cover; the {@code Options} reference lists them all.
     *
     * @param options the options, in order
     * @return this
     */
    public NashornScriptEngineBuilder option(final String... options) {
        for (final String option : Objects.requireNonNull(options, "options")) {
            this.options.add(Objects.requireNonNull(option, "option"));
        }
        return this;
    }

    /**
     * Whether Annex B - the web's legacy: {@code escape}, {@code __proto__},
     * block-level function hoisting and the rest - is implemented. On by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder annexB(final boolean enabled) {
        return option("--annexB=" + enabled);
    }

    /**
     * Whether every script runs in strict mode, as if it began with
     * {@code "use strict"}. Off by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder strict(final boolean enabled) {
        return option("-strict=" + enabled);
    }

    /**
     * Whether scripting mode - {@code #} comments, {@code ${expression}} in
     * double-quoted strings, heredocs, {@code $ENV} - is on. Off by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder scripting(final boolean enabled) {
        return option("-scripting=" + enabled);
    }

    /**
     * Whether a script error carries the Java stack trace of its origin
     * ({@code -doe}, dump on error), a help while developing. Off by default
     * here; the factory's no-argument engine turns it on.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder dumpStackOnError(final boolean enabled) {
        return option("-doe=" + enabled);
    }

    /**
     * Whether scripts are compiled with the debugger's hooks, so that a
     * debugger can attach through {@code Debugger.of(engine)} or a frontend;
     * costs some speed. Off by default; implied by {@link #inspect}.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder debugger(final boolean enabled) {
        return option("--debugger=" + enabled);
    }

    /**
     * Listens for a Chrome DevTools Protocol client - Chrome DevTools, VS Code
     * - the way {@code node --inspect} does; needs the {@code nashorn-debugger}
     * artifact. Implies the debugger.
     *
     * @param hostAndPort {@code "[host:]port"}, or {@code "9229"} for the default host
     * @param waitForClient whether to pause at the first statement until a client attaches ({@code --inspect-brk})
     * @return this
     */
    public NashornScriptEngineBuilder inspect(final String hostAndPort, final boolean waitForClient) {
        return option((waitForClient ? "--inspect-brk=" : "--inspect=") + Objects.requireNonNull(hostAndPort, "hostAndPort"));
    }

    /**
     * Which of the script libraries registered as services apply, by name;
     * none if no name is given. All of them by default. Libraries added with
     * {@link #library} apply regardless.
     *
     * @param names the names, e.g. {@code "host"}
     * @return this
     */
    public NashornScriptEngineBuilder discoveredLibraries(final String... names) {
        return option("--libraries=" + (names.length == 0 ? "none" : String.join(",", names)));
    }

    /**
     * Whether scripts may reach Java at all: with {@code false} (the
     * {@code --no-java} option), {@code Java}, {@code Packages} and the
     * package globals are gone, and {@code Java.type} with them - the
     * bluntest sandbox, next to {@link #classFilter} for a finer one.
     * Java access is on by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder java(final boolean enabled) {
        return option("--no-java=" + !enabled);
    }

    /**
     * Whether Nashorn's own syntax extensions - {@code for each},
     * conditional catch, expression closures - are accepted
     * ({@code --no-syntax-extensions} off). On by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder syntaxExtensions(final boolean enabled) {
        return option("--no-syntax-extensions=" + !enabled);
    }

    /**
     * Whether the typed arrays - {@code ArrayBuffer}, {@code Uint8Array} and
     * the rest - are present ({@code --no-typed-arrays} off). On by default,
     * and part of ES2015; turning them off is for hosts that must not expose
     * them.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder typedArrays(final boolean enabled) {
        return option("--no-typed-arrays=" + !enabled);
    }

    /**
     * Whether code is compiled optimistically - narrow types assumed and
     * deoptimized when proven wrong - which runs hot code faster and warms up
     * slower. Off by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder optimisticTypes(final boolean enabled) {
        return option("--optimistic-types=" + enabled);
    }

    /**
     * Whether functions are compiled when first called rather than with the
     * script. On by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder lazyCompilation(final boolean enabled) {
        return option("--lazy-compilation=" + enabled);
    }

    /**
     * Whether compiled classes are cached on disk across processes
     * ({@code --persistent-code-cache}), keyed by source and configuration,
     * in the directory the {@code nashorn.persistent.code.cache} system
     * property names. Off by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder persistentCodeCache(final boolean enabled) {
        return option("--persistent-code-cache=" + enabled);
    }

    /**
     * How many compiled scripts the engine's class cache holds. 50 by default.
     *
     * @param size the size; 0 disables the cache
     * @return this
     */
    public NashornScriptEngineBuilder classCacheSize(final int size) {
        return option("--class-cache-size=" + size);
    }

    /**
     * Whether the engine has one global for all bindings instead of one per
     * bindings ({@code --global-per-engine}) - the pre-JSR-223 behaviour some
     * embeddings rely on. Off by default.
     *
     * @param enabled whether
     * @return this
     */
    public NashornScriptEngineBuilder globalPerEngine(final boolean enabled) {
        return option("--global-per-engine=" + enabled);
    }

    /**
     * The time zone scripts see - what {@code new Date()} and its local
     * getters answer with. The host's by default.
     *
     * @param timeZone the zone
     * @return this
     */
    public NashornScriptEngineBuilder timeZone(final TimeZone timeZone) {
        return option("-timezone=" + Objects.requireNonNull(timeZone, "timeZone").getID());
    }

    /**
     * The locale scripts see - what {@code toLocaleString} and its kin answer
     * with. The host's by default.
     *
     * @param locale the locale
     * @return this
     */
    public NashornScriptEngineBuilder locale(final Locale locale) {
        return option("--locale=" + Objects.requireNonNull(locale, "locale").toLanguageTag());
    }

    /**
     * A class path of the engine's own for scripts to load Java classes from,
     * on top of the {@linkplain #classLoader application class loader} - the
     * {@code -classpath} option.
     *
     * @param classPath the path, in the platform's path syntax
     * @return this
     */
    public NashornScriptEngineBuilder classPath(final String classPath) {
        return option("-classpath=" + Objects.requireNonNull(classPath, "classPath"));
    }

    /**
     * A module path of the engine's own: scripts reach the named modules'
     * exported packages - {@code --module-path} with {@code --add-modules}.
     *
     * @param modulePath the path, in the platform's path syntax
     * @param moduleNames the modules to resolve; at least one
     * @return this
     */
    public NashornScriptEngineBuilder modulePath(final String modulePath, final String... moduleNames) {
        Objects.requireNonNull(modulePath, "modulePath");
        if (Objects.requireNonNull(moduleNames, "moduleNames").length == 0) {
            throw new IllegalArgumentException("a module path needs the modules to resolve: pass their names");
        }
        return option("--module-path=" + modulePath, "--add-modules=" + String.join(",", moduleNames));
    }

    // -- the Java side ------------------------------------------------------------------

    /**
     * The class loader scripts reach Java classes through - {@code Java.type},
     * the package globals - and script libraries are discovered through. The
     * current thread's context class loader by default.
     *
     * @param classLoader the loader
     * @return this
     */
    public NashornScriptEngineBuilder classLoader(final ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        return this;
    }

    /**
     * A filter consulted before any Java class becomes visible to a script -
     * the sandboxing hook. None by default. {@code ClassFilter} has one method,
     * so a lambda over the class name will do.
     *
     * @param classFilter the filter
     * @return this
     */
    public NashornScriptEngineBuilder classFilter(final ClassFilter classFilter) {
        this.classFilter = Objects.requireNonNull(classFilter, "classFilter");
        return this;
    }

    /**
     * Script libraries to install into every global the engine creates, besides
     * the ones discovered as services; one that shares a discovered library's
     * name replaces it.
     *
     * @param libraries the libraries, in the order they apply
     * @return this
     */
    public NashornScriptEngineBuilder library(final ScriptLibrary... libraries) {
        for (final ScriptLibrary library : Objects.requireNonNull(libraries, "libraries")) {
            this.libraries.add(Objects.requireNonNull(library, "library"));
        }
        return this;
    }

    /**
     * Module loaders for the engine's {@code import} statements, asked in this
     * order, the first that loads a module winning. With none registered,
     * imports resolve as filesystem paths relative to the importing module;
     * registering any loader replaces that - add a
     * {@link org.monflabs.nashorn.api.modules.PathModuleLoader} to keep
     * filesystem access.
     *
     * @param loaders the loaders, in chain order
     * @return this
     * @since 2017.0.0
     */
    public NashornScriptEngineBuilder moduleLoader(final ModuleLoader... loaders) {
        for (final ModuleLoader loader : Objects.requireNonNull(loaders, "loaders")) {
            this.moduleLoaders.add(Objects.requireNonNull(loader, "loader"));
        }
        return this;
    }

    // -- building -----------------------------------------------------------------------

    /**
     * The options as they stand, in command-line spelling and order.
     *
     * @return the options
     */
    public List<String> options() {
        return List.copyOf(options);
    }

    /**
     * Builds the engine.
     *
     * @return a new engine
     * @throws IllegalArgumentException for an option the engine does not know, or a malformed one
     */
    public ScriptEngine build() {
        final ClassLoader loader = classLoader != null ? classLoader : NashornScriptEngineFactory.getAppClassLoader();
        return new NashornScriptEngine(NashornScriptEngineFactory.shared(), options.toArray(new String[0]), loader, classFilter, List.copyOf(libraries), List.copyOf(moduleLoaders));
    }

    @Override
    public String toString() {
        return "NashornScriptEngineBuilder" + options + (libraries.isEmpty() ? "" : " libraries " + libraries);
    }
}
