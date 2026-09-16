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
package org.monflabs.nashorn.libs;

import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * Nashorn's own additions to the language, as a library.
 *
 * None of these are ECMAScript. They are what Nashorn has always put on the
 * global object beyond the specification, and they are now opt-in, the way
 * {@code fetch} and the timers are: a bare engine has none of them, and an
 * embedder that wants them hands this to the builder.
 *
 * <pre>
 * ScriptEngine engine = new NashornScriptEngineBuilder()
 *         .library(new NashornLibrary())
 *         .build();
 * engine.eval("print('hello')");
 * </pre>
 *
 * What it installs, on every realm of that engine:
 *
 * <ul>
 *   <li>{@code print(arg...)} - space-separated to standard output, with a
 *       trailing newline unless {@code --print-no-newline} says otherwise.</li>
 *   <li>{@code load(source)} and {@code loadWithNewGlobal(source, args...)} -
 *       evaluate another script, in this realm or a fresh one.</li>
 *   <li>{@code readLine([prompt])} and {@code readFully(file)} - the two host
 *       I/O functions that outlived scripting mode.</li>
 *   <li>{@code JSAdapter} - the dynamic object with {@code __get__},
 *       {@code __put__} and {@code __call__} traps.</li>
 *   <li>{@code __FILE__}, {@code __DIR__}, {@code __LINE__} - where the reading
 *       script is.</li>
 * </ul>
 *
 * Two things are deliberately elsewhere. <b>Java access</b> - {@code Java},
 * {@code Packages}, {@code JavaImporter} and the package roots - stays on the
 * global by default and has {@code --no-java} to remove it, a switch that
 * predates this and that embedders already use to sandbox a script.
 * <b>{@code exit} and {@code quit}</b> call {@code System.exit}, which is no
 * business of a script embedded in an application, so they belong to the shell
 * and are installed only at its prompt.
 *
 * @since 2026.1.0
 */
public final class NashornLibrary implements ScriptLibrary {

    @Override
    public String name() {
        return "nashorn";
    }

    @Override
    public void initialize(final JSObject global) {
        Global.instance().installNashornExtensions();
    }
}
