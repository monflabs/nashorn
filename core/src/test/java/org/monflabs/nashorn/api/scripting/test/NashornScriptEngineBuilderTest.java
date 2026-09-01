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

package org.monflabs.nashorn.api.scripting.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.testng.annotations.Test;

/**
 * The builder: every choice, alone and together.
 */
public class NashornScriptEngineBuilderTest {

    @Test
    public void aBareBuilderMakesAnEngineWithNoOptions() throws ScriptException {
        final NashornScriptEngineBuilder builder = new NashornScriptEngineBuilder();
        assertEquals(builder.options(), List.of());
        final ScriptEngine engine = builder.build();
        assertEquals(engine.eval("1 + 1"), 2);
        assertEquals(engine.eval("typeof escape"), "function");        // Annex B on
        assertEquals(engine.eval("typeof setTimeout"), "function");    // the standard libraries discovered
        assertSame(engine.getFactory().getClass(), NashornScriptEngineFactory.class);
        assertEquals(engine.getFactory().getEngineName(), new NashornScriptEngineFactory().getEngineName());
    }

    @Test
    public void theNamedOptions() throws ScriptException {
        assertEquals(new NashornScriptEngineBuilder().annexB(false).build().eval("typeof escape"), "undefined");
        assertEquals(new NashornScriptEngineBuilder().strict(true).build().eval("try { undeclared = 1; 'assigned' } catch (e) { e.name }"), "ReferenceError");
        assertEquals(new NashornScriptEngineBuilder().scripting(true).build().eval("var x = <<EOS\nheredoc\nEOS\nx.trim()"), "heredoc");
        assertEquals(new NashornScriptEngineBuilder().discoveredLibraries().build().eval("typeof setTimeout + typeof fetch"), "undefinedundefined");
        assertEquals(new NashornScriptEngineBuilder().discoveredLibraries("host").build().eval("typeof setTimeout + ' ' + typeof fetch"), "function undefined");
        assertEquals(new NashornScriptEngineBuilder().debugger(true).build().eval("typeof console"), "object");
        assertEquals(new NashornScriptEngineBuilder().dumpStackOnError(true).options(), List.of("-doe=true"));
        assertEquals(new NashornScriptEngineBuilder().inspect("9229", true).options(), List.of("--inspect-brk=9229"));
        assertEquals(new NashornScriptEngineBuilder().inspect("127.0.0.1:0", false).options(), List.of("--inspect=127.0.0.1:0"));
    }

    @Test
    public void optionsAreCommandLineSpellingsAndTheLastOneWins() throws ScriptException {
        final NashornScriptEngineBuilder builder = new NashornScriptEngineBuilder().option("--annexB=false", "-strict").annexB(true);
        assertEquals(builder.options(), List.of("--annexB=false", "-strict", "--annexB=true"));
        assertEquals(builder.build().eval("typeof escape"), "function");
        try {
            new NashornScriptEngineBuilder().option("--no-such-option").build();
            fail("expected a refusal");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no-such-option"), expected.getMessage());
        }
    }

    @Test
    public void theEngineShapingOptions() throws ScriptException {
        // no Java at all: the bluntest sandbox
        final ScriptEngine sandboxed = new NashornScriptEngineBuilder().java(false).discoveredLibraries().build();
        assertEquals(sandboxed.eval("[typeof Java, typeof Packages, typeof java, typeof javax].join(' ')"), "undefined undefined undefined undefined");
        assertEquals(new NashornScriptEngineBuilder().java(true).build().eval("typeof Java"), "object");
        // Nashorn's own syntax refused without the extensions
        final ScriptEngine noExtensions = new NashornScriptEngineBuilder().syntaxExtensions(false).build();
        try {
            noExtensions.eval("for each (var x in [1]) {}");
            fail("expected a syntax error");
        } catch (final ScriptException expected) {
            // as configured
        }
        assertEquals(((Number)new NashornScriptEngineBuilder().syntaxExtensions(true).build().eval("var s = 0; for each (var x in [1, 2]) { s += x; } s")).intValue(), 3);
        // typed arrays removable
        assertEquals(new NashornScriptEngineBuilder().typedArrays(false).build().eval("typeof Uint8Array"), "undefined");
        assertEquals(new NashornScriptEngineBuilder().typedArrays(true).build().eval("new Uint8Array(2).length"), 2);
        // compilation modes still evaluate
        assertEquals(new NashornScriptEngineBuilder().optimisticTypes(true).build().eval("(function f(n) { return n < 2 ? 1 : n * f(n - 1); })(5)"), 120);
        assertEquals(new NashornScriptEngineBuilder().lazyCompilation(false).classCacheSize(0).build().eval("6 * 7"), 42);
        // one global for every bindings
        final ScriptEngine oneGlobal = new NashornScriptEngineBuilder().globalPerEngine(true).build();
        oneGlobal.eval("var shared = 'seen'");
        assertEquals(oneGlobal.eval("shared", oneGlobal.createBindings()), "seen");
        // the zone and locale scripts see
        final ScriptEngine utc = new NashornScriptEngineBuilder().timeZone(java.util.TimeZone.getTimeZone("UTC")).locale(java.util.Locale.CANADA_FRENCH).build();
        assertEquals(((Number)utc.eval("new Date(0).getTimezoneOffset()")).intValue(), 0);
        assertEquals(utc.eval("new Date(0).getUTCFullYear() + '/' + new Date(0).getFullYear()"), "1970/1970");
        // the spellings of the rest
        assertEquals(new NashornScriptEngineBuilder().persistentCodeCache(true).classPath("lib/a.jar").options(),
                List.of("--persistent-code-cache=true", "-classpath=lib/a.jar"));
        assertEquals(new NashornScriptEngineBuilder().modulePath("mods", "com.example.api", "com.example.impl").options(),
                List.of("--module-path=mods", "--add-modules=com.example.api,com.example.impl"));
        try {
            new NashornScriptEngineBuilder().modulePath("mods");
            fail("expected a refusal");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("module"), expected.getMessage());
        }
    }

    @Test
    public void classLoaderFilterAndLibraries() throws Exception {
        final ScriptLibrary geometry = ScriptLibrary.of("geometry", Map.of("TAU", 2 * Math.PI), ScriptLibrary.Script.of("g.js", "function circumference(r) { return TAU * r; }"));
        try (URLClassLoader loader = new URLClassLoader(new URL[0], NashornScriptEngineBuilderTest.class.getClassLoader())) {
            final ScriptEngine engine = new NashornScriptEngineBuilder()
                    .classLoader(loader)
                    .classFilter(name -> !name.startsWith("java.io."))
                    .library(geometry)
                    .discoveredLibraries()
                    .build();
            assertEquals(engine.eval("circumference(1)"), 2 * Math.PI);
            assertEquals(engine.eval("typeof setTimeout"), "undefined");
            assertEquals(engine.eval("typeof java.util.ArrayList"), "function");
            // a filtered class is not there at all: the Java ClassNotFoundException, not a script error
            assertEquals(engine.eval("try { Java.type('java.io.File'); 'visible' } catch (e) { e instanceof java.lang.ClassNotFoundException }"), true);
            assertEquals(engine.eval("java.lang.Thread.currentThread().getContextClassLoader() !== null"), true);
        }
    }

    @Test
    public void aBuilderIsReusableAndEveryBuildIsANewEngine() throws ScriptException {
        final NashornScriptEngineBuilder builder = new NashornScriptEngineBuilder().discoveredLibraries();
        final ScriptEngine one = builder.build();
        final ScriptEngine two = builder.build();
        assertNotSame(one, two);
        one.eval("var shared = 1");
        assertEquals(two.eval("typeof shared"), "undefined");
    }

    @Test
    public void nullsAreRefused() {
        for (final Runnable call : List.<Runnable>of(
                () -> new NashornScriptEngineBuilder().option((String[])null),
                () -> new NashornScriptEngineBuilder().option("-strict", null),
                () -> new NashornScriptEngineBuilder().classLoader(null),
                () -> new NashornScriptEngineBuilder().classFilter(null),
                () -> new NashornScriptEngineBuilder().library((ScriptLibrary)null),
                () -> new NashornScriptEngineBuilder().inspect(null, false))) {
            try {
                call.run();
                fail("expected a NullPointerException");
            } catch (final NullPointerException expected) {
                // as documented
            }
        }
    }
}
