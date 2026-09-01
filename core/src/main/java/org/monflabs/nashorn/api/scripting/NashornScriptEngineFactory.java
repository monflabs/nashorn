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

package org.monflabs.nashorn.api.scripting;

import java.util.List;
import java.util.Objects;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import org.monflabs.nashorn.internal.parser.JSONParser;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.Version;

/**
 * JSR-223 compliant script engine factory for Nashorn. The engine answers for:
 * <ul>
 * <li>names {@code "nashorn-monflabs"}, {@code "Nashorn-Monflabs"}, {@code "js"}, {@code "JS"}, {@code "JavaScript"},
 * {@code "javascript"}, {@code "ECMAScript"}, and {@code "ecmascript"} - the fork's own name rather
 * than plain {@code "nashorn"}, so that this engine and the official Nashorn library can be looked
 * up unambiguously when both are present;</li>
 * <li>MIME types {@code "application/javascript"}, {@code "application/ecmascript"}, {@code "text/javascript"}, and
 * {@code "text/ecmascript"};</li>
 * <li>as well as for the extension {@code "js"}.</li>
 * </ul>
 * Programs executing in engines created using {@link #getScriptEngine(String[])} will have the passed arguments
 * accessible as a global variable named {@code "arguments"}.
 *
 * @since 1.8u40
 *
 * <p>To build an engine with options, a class loader, a class filter or script
 * libraries, use {@link NashornScriptEngineBuilder}; the {@code getScriptEngine}
 * overloads that took those are deprecated and kept for compatibility. The
 * no-argument {@link #getScriptEngine()} is the {@code javax.script} entry point.
 */
public final class NashornScriptEngineFactory implements ScriptEngineFactory {
    public NashornScriptEngineFactory() {
    }

    @Override
    public String getEngineName() {
        return "OpenJDK-Monflabs";
    }

    @Override
    public String getEngineVersion() {
        return Version.version();
    }

    @Override
    public List<String> getExtensions() {
        return extensions;
    }

    @Override
    public String getLanguageName() {
        return "ECMAScript";
    }

    @Override
    public String getLanguageVersion() {
        return "ECMA - 262 Edition 5.1";
    }

    @Override
    public String getMethodCallSyntax(final String obj, final String method, final String... args) {
        final StringBuilder sb = new StringBuilder().
            append(Objects.requireNonNull(obj)).append('.').
            append(Objects.requireNonNull(method)).append('(');
        final int len = args.length;

        if (len > 0) {
            sb.append(Objects.requireNonNull(args[0]));
        }
        for (int i = 1; i < len; i++) {
            sb.append(',').append(Objects.requireNonNull(args[i]));
        }
        sb.append(')');

        return sb.toString();
    }

    @Override
    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    @Override
    public List<String> getNames() {
        return names;
    }

    @Override
    public String getOutputStatement(final String toDisplay) {
        return "print(" + JSONParser.quote(toDisplay) + ")";
    }

    @Override
    public Object getParameter(final String key) {
        switch (key) {
        case ScriptEngine.NAME:
            return "javascript";
        case ScriptEngine.ENGINE:
            return getEngineName();
        case ScriptEngine.ENGINE_VERSION:
            return getEngineVersion();
        case ScriptEngine.LANGUAGE:
            return getLanguageName();
        case ScriptEngine.LANGUAGE_VERSION:
            return getLanguageVersion();
        case "THREADING":
            // The engine implementation is not thread-safe. Can't be
            // used to execute scripts concurrently on multiple threads.
            return null;
        default:
            return null;
        }
    }

    @Override
    public String getProgram(final String... statements) {
        Objects.requireNonNull(statements);
        final StringBuilder sb = new StringBuilder();

        for (final String statement : statements) {
            sb.append(Objects.requireNonNull(statement)).append(';');
        }

        return sb.toString();
    }

    // default options passed to Nashorn script engine
    private static final String[] DEFAULT_OPTIONS = new String[] { "-doe" };

    @Override
    public ScriptEngine getScriptEngine() {
        try {
            return new NashornScriptEngine(this, DEFAULT_OPTIONS, getAppClassLoader(), null, List.of());
        } catch (final RuntimeException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw e;
        }
    }

    /**
     * Create a new Script engine initialized with the given class loader.
     *
     * @param appLoader class loader to be used as script "app" class loader.
     * @return newly created script engine.
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final ClassLoader appLoader) {
        return newEngine(DEFAULT_OPTIONS, appLoader, null);
    }

    /**
     * Create a new Script engine initialized with the given class filter.
     *
     * @param classFilter class filter to use.
     * @return newly created script engine.
     * @throws NullPointerException if {@code classFilter} is {@code null}
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final ClassFilter classFilter) {
        return newEngine(DEFAULT_OPTIONS, getAppClassLoader(), Objects.requireNonNull(classFilter));
    }

    /**
     * Create a new Script engine initialized with the given arguments.
     *
     * @param args arguments array passed to script engine.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} is {@code null}
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final String... args) {
        return newEngine(Objects.requireNonNull(args), getAppClassLoader(), null);
    }

    /**
     * Create a new Script engine initialized with the given arguments and the given class loader.
     *
     * @param args arguments array passed to script engine.
     * @param appLoader class loader to be used as script "app" class loader.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} is {@code null}
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final String[] args, final ClassLoader appLoader) {
        return newEngine(Objects.requireNonNull(args), appLoader, null);
    }

    /**
     * Create a new Script engine initialized with the given arguments, class loader and class filter.
     *
     * @param args arguments array passed to script engine.
     * @param appLoader class loader to be used as script "app" class loader.
     * @param classFilter class filter to use.
     * @return newly created script engine.
     * @throws NullPointerException if {@code args} or {@code classFilter} is {@code null}
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter) {
        return newEngine(Objects.requireNonNull(args), appLoader, Objects.requireNonNull(classFilter));
    }

    /**
     * Create a new Script engine with these script libraries, besides the ones
     * discovered as services. They apply to every global the engine creates.
     *
     * @param libraries the libraries
     * @return newly created script engine.
     * @throws NullPointerException if {@code libraries} or one of them is {@code null}
     * @since 2017.0.0
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final ScriptLibrary... libraries) {
        return newEngine(DEFAULT_OPTIONS, getAppClassLoader(), null, List.of(libraries));
    }

    /**
     * Create a new Script engine initialized with the given arguments and
     * these script libraries, besides the ones discovered as services.
     *
     * @param args arguments array passed to script engine.
     * @param libraries the libraries
     * @return newly created script engine.
     * @throws NullPointerException if {@code args}, {@code libraries} or one of the libraries is {@code null}
     * @since 2017.0.0
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final String[] args, final ScriptLibrary... libraries) {
        return newEngine(Objects.requireNonNull(args), getAppClassLoader(), null, List.of(libraries));
    }

    /**
     * Create a new Script engine initialized with the given arguments, class
     * loader, class filter and script libraries. The libraries apply to every
     * global the engine creates, whatever the {@code --libraries} option
     * says, and each replaces a discovered library of the same name.
     *
     * @param args arguments array passed to script engine.
     * @param appLoader class loader to be used as script "app" class loader; null for the default
     * @param classFilter class filter to use; null for none
     * @param libraries the libraries
     * @return newly created script engine.
     * @throws NullPointerException if {@code args}, {@code libraries} or one of the libraries is {@code null}
     * @since 2017.0.0
     * @deprecated use {@link NashornScriptEngineBuilder}, which takes the same choices one at a time
     */
    @Deprecated(since = "2017.0.0")
    public ScriptEngine getScriptEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter, final List<ScriptLibrary> libraries) {
        return newEngine(Objects.requireNonNull(args), appLoader != null ? appLoader : getAppClassLoader(), classFilter, List.copyOf(libraries));
    }

    private ScriptEngine newEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter) {
        return newEngine(args, appLoader, classFilter, List.of());
    }

    private ScriptEngine newEngine(final String[] args, final ClassLoader appLoader, final ClassFilter classFilter, final List<ScriptLibrary> libraries) {
        try {
            return new NashornScriptEngine(this, args, appLoader, classFilter, libraries);
        } catch (final RuntimeException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw e;
        }
    }

    // -- Internals only below this point

    private static final List<String> names = List.of(
        "nashorn-monflabs", "Nashorn-Monflabs",
        "js", "JS",
        "JavaScript", "javascript",
        "ECMAScript", "ecmascript"
    );

    private static final List<String> mimeTypes = List.of(
        "application/javascript",
        "application/ecmascript",
        "text/javascript",
        "text/ecmascript"
    );

    private static final List<String> extensions = List.of("js");

    /** The factory the builder's engines report; one is as good as another. */
    private static final NashornScriptEngineFactory SHARED = new NashornScriptEngineFactory();

    static NashornScriptEngineFactory shared() {
        return SHARED;
    }

    static ClassLoader getAppClassLoader() {
        // Revisit: script engine implementation needs the capability to
        // find the class loader of the context in which the script engine
        // is running so that classes will be found and loaded properly
        return Objects.requireNonNullElseGet(
            Thread.currentThread().getContextClassLoader(),
            NashornScriptEngineFactory.class::getClassLoader);
    }
}
