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
package org.monflabs.nashorn.test.tools;

import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.ClassFilter;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.libs.JavaLibrary;
import org.monflabs.nashorn.libs.NashornLibrary;

/**
 * Engines for the tests that want the globals a bare engine no longer has.
 *
 * Since 2026.1.0 an engine is exactly ECMAScript: {@code print} and {@code load}
 * come from {@link NashornLibrary}, and {@code Java}, {@code Packages} and the
 * package roots from {@link JavaLibrary}. Most of this suite predates that and is
 * written in the dialect an engine used to have, so rather than repeat two
 * {@code library(...)} arguments in a hundred places, those tests ask for an
 * engine here.
 *
 * A test whose subject <em>is</em> the new default builds its own bare engine and
 * does not come through this class.
 */
public final class TestEngines {

    /** The two libraries an engine used to have built in, for the factory overloads that take a list. */
    public static final java.util.List<ScriptLibrary> LIBS =
            java.util.List.of(new NashornLibrary(), new JavaLibrary());

    private TestEngines() {
    }

    /** An engine with Nashorn's own globals and Java access. */
    public static ScriptEngine full(final String... options) {
        return builder(options).build();
    }

    /** The same, with a class filter over the Java access. */
    public static ScriptEngine full(final ClassFilter filter, final String... options) {
        return builder(options).classFilter(filter).build();
    }

    /** The same, plus libraries of the test's own. */
    public static ScriptEngine fullWith(final ScriptLibrary... extra) {
        final ScriptLibrary[] all = new ScriptLibrary[extra.length + 2];
        all[0] = new NashornLibrary();
        all[1] = new JavaLibrary();
        System.arraycopy(extra, 0, all, 2, extra.length);
        return new NashornScriptEngineBuilder().library(all).build();
    }

    private static NashornScriptEngineBuilder builder(final String... options) {
        final NashornScriptEngineBuilder builder = new NashornScriptEngineBuilder()
                .library(new NashornLibrary(), new JavaLibrary());
        return options.length == 0 ? builder : builder.option(options);
    }
}
