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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A library of script-visible extensions, contributed to every global an
 * engine creates - the default context's, each {@code createBindings()}, a
 * {@code loadWithNewGlobal} - before any script runs in it: Java values to
 * define as globals, and scripts to evaluate there.
 *
 * <p>A library reaches an engine in one of two ways. Registered as a
 * {@link java.util.ServiceLoader} provider - a {@code provides} clause in a
 * module descriptor, or a {@code META-INF/services/org.monflabs.nashorn.api.scripting.ScriptLibrary}
 * entry on the class path - it is discovered by every engine whose class
 * loader can see it, subject to the {@code --libraries} option. Passed to
 * {@link NashornScriptEngineBuilder#library(ScriptLibrary...)}, it applies to
 * that engine whatever the option says, and replaces a discovered library of
 * the same {@link #name()}.
 *
 * <p>In each global the {@link #globals()} are defined first, then the
 * {@link #scripts()} run in order, so a script may build on the Java values,
 * and then {@link #initialize(JSObject)} is called with the global itself,
 * for what is easier done from Java than declared - a method on an existing
 * prototype, say. Libraries go one after the other, so a library sees the
 * ones before it complete. Every global gets its own installation: what a
 * script declares in one is not shared with another. A library that fails -
 * a script that throws, a resource that cannot be read - fails the creation
 * of the engine or global with an exception naming the library.
 *
 * <p>Implement the interface, or describe the library and let
 * {@link #of(String, Map, Script...)} do it:
 * <pre>{@code
 * ScriptLibrary geometry = ScriptLibrary.of("geometry",
 *     Map.of("TAU", 2 * Math.PI),
 *     Script.ofResource(Geometry.class, "geometry.js"));
 * ScriptEngine engine = new NashornScriptEngineBuilder().library(geometry).build();
 * }</pre>
 *
 * @since 2017.0.0
 */
public interface ScriptLibrary {

    /**
     * The library's name: unique among the libraries an engine sees, the
     * handle the {@code --libraries} option selects by, and what an error
     * names.
     * @return the name
     */
    String name();

    /**
     * Java values to define as properties of the global, by name, before
     * the scripts run. Any Java object will do - the script sees it through
     * the ordinary Java interop - and so will a {@link JSObject}.
     * @return the globals, possibly empty
     */
    default Map<String, Object> globals() {
        return Map.of();
    }

    /**
     * Scripts to evaluate in the global, in this order, after the globals
     * are defined. Each runs as a program at the global's top level: its
     * {@code var} and function declarations become the global's properties.
     * @return the scripts, possibly empty
     */
    default List<Script> scripts() {
        return List.of();
    }

    /**
     * Called in each new global once this library's globals are defined and
     * its scripts have run, with the global object itself - the same
     * {@link JSObject} a {@code ScriptEngine} hands out as its engine scope.
     * Reach into it to change what is already there: add a method to a
     * built-in prototype, wrap a function the scripts declared, read a
     * setting. {@code global.eval(source)} evaluates in the global too.
     *
     * <p>Does nothing by default.
     *
     * @param global the global, as a mirror bound to its own realm
     */
    default void initialize(final JSObject global) {
    }

    /**
     * A script of a library: its text, and the name stack traces and
     * error messages show it under.
     *
     * @param name the script's name, as {@code __FILE__} and a stack trace see it
     * @param text the source
     */
    record Script(String name, String text) {
        /**
         * Validates the parts.
         * @param name the script's name
         * @param text the source
         */
        public Script {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(text, "text");
        }

        /**
         * A script from its text.
         * @param name the script's name
         * @param text the source
         * @return the script
         */
        public static Script of(final String name, final String text) {
            return new Script(name, text);
        }

        /**
         * A script read, as UTF-8, from a resource next to a class - the
         * usual home of a library packaged in a jar.
         * @param anchor the class the resource is resolved against, as {@link Class#getResourceAsStream(String)} does
         * @param path the resource path, relative to the class's package or absolute with a leading slash
         * @return the script, named after the path
         * @throws IllegalArgumentException if there is no such resource, or it cannot be read
         */
        public static Script ofResource(final Class<?> anchor, final String path) {
            try (InputStream in = anchor.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalArgumentException("no resource " + path + " next to " + anchor.getName());
                }
                return new Script(path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (final IOException e) {
                throw new IllegalArgumentException("cannot read resource " + path + " next to " + anchor.getName(), e);
            }
        }

        /**
         * A script read, as UTF-8, from a URL.
         * @param url the location
         * @return the script, named after the URL
         * @throws IllegalArgumentException if it cannot be read
         */
        public static Script ofUrl(final URL url) {
            try (InputStream in = url.openStream()) {
                return new Script(url.toString(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (final IOException e) {
                throw new IllegalArgumentException("cannot read " + url, e);
            }
        }
    }

    /**
     * A library described rather than implemented: these globals, then these
     * scripts.
     * @param name the library's name
     * @param globals the Java values to define, by name; the iteration order is kept
     * @param scripts the scripts, in the order they run
     * @return the library
     */
    static ScriptLibrary of(final String name, final Map<String, Object> globals, final Script... scripts) {
        Objects.requireNonNull(name, "name");
        final Map<String, Object> values = new LinkedHashMap<>(Objects.requireNonNull(globals, "globals"));
        final List<Script> sources = List.of(Objects.requireNonNull(scripts, "scripts"));
        return new ScriptLibrary() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Object> globals() {
                return values;
            }

            @Override
            public List<Script> scripts() {
                return sources;
            }

            @Override
            public String toString() {
                return "ScriptLibrary " + name;
            }
        };
    }
}
