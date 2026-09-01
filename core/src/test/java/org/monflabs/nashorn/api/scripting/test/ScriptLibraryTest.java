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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptLibrary.Script;
import org.monflabs.nashorn.tools.Shell;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Script libraries: contributed as services or explicitly, installed into
 * every global an engine makes.
 */
public class ScriptLibraryTest {
    /** A class path holding only a service registration of {@link TestScriptLibrary}. */
    private Path services;
    /** A loader over it, so that only the engines built with it discover the library. */
    private URLClassLoader loader;

    @BeforeClass
    public void registerTheTestLibrary() throws IOException {
        services = Files.createTempDirectory("script-library-services");
        final Path file = services.resolve("META-INF").resolve("services").resolve(ScriptLibrary.class.getName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, TestScriptLibrary.class.getName() + "\n", StandardCharsets.UTF_8);
        loader = new URLClassLoader(new URL[] { services.toUri().toURL() }, ScriptLibraryTest.class.getClassLoader());
    }

    @AfterClass
    public void unregister() throws IOException {
        loader.close();
        Files.walk(services).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    private ScriptEngine discovering(final String... args) {
        return new NashornScriptEngineFactory().getScriptEngine(args, loader, null, List.of());
    }

    private static final ScriptLibrary GEOMETRY = ScriptLibrary.of("geometry",
            Map.of("TAU", 2 * Math.PI, "twice", new AbstractJSObject() {
                @Override
                public Object call(final Object thiz, final Object... args) {
                    return ((Number)args[0]).doubleValue() * 2;
                }

                @Override
                public boolean isFunction() {
                    return true;
                }
            }),
            Script.of("geometry.js", "function circumference(r) { return TAU * r; }\nvar unit = { radius: 1 };"),
            Script.of("more.js", "function diameter(r) { return twice(r); }"));

    @Test
    public void anExplicitLibraryContributesGlobalsAndScripts() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        assertEquals(engine.eval("circumference(1)"), 2 * Math.PI);
        assertEquals(engine.eval("diameter(21)"), 42.0);
        assertEquals(engine.eval("unit.radius"), 1);
        assertEquals(engine.eval("typeof TAU"), "number");
    }

    @Test
    public void everyNewGlobalGetsTheLibrary() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        final Bindings fresh = engine.createBindings();
        assertEquals(engine.eval("circumference(2)", fresh), 4 * Math.PI);
        final ScriptContext context = new SimpleScriptContext();
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        assertEquals(engine.eval("diameter(1)", context), 2.0);
        // and one made by a script
        assertEquals(engine.eval("loadWithNewGlobal({ name: 'x.js', script: 'circumference(0.5)' })"), Math.PI);
        // each global has its own copy: a change in one is not seen in another
        engine.eval("unit.radius = 5", fresh);
        assertEquals(engine.eval("unit.radius"), 1);
    }

    @Test
    public void aLibraryIsDiscoveredAsAService() throws ScriptException {
        final ScriptEngine engine = discovering("-doe");
        assertEquals(engine.eval("testlibGreet('world')"), "hello world from testlib 1.0");
        // an engine whose loader has no registration does not get it
        assertEquals(new NashornScriptEngineFactory().getScriptEngine().eval("typeof testlibGreet"), "undefined");
    }

    @Test
    public void theLibrariesOptionSelectsDiscoveredLibraries() throws ScriptException {
        assertEquals(discovering("--libraries=none").eval("typeof testlibGreet"), "undefined");
        assertEquals(discovering("--libraries=other,another").eval("typeof testlibGreet"), "undefined");
        assertEquals(discovering("--libraries=testlib").eval("typeof testlibGreet"), "function");
        assertEquals(discovering("--libraries=all").eval("typeof testlibGreet"), "function");
        assertEquals(discovering("--libraries=other, testlib").eval("typeof testlibGreet"), "function");
        // an explicit library is not subject to the option
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(new String[] { "--libraries=none" }, loader, null, List.of(GEOMETRY));
        assertEquals(engine.eval("typeof circumference"), "function");
        assertEquals(engine.eval("typeof testlibGreet"), "undefined");
    }

    @Test
    public void anExplicitLibraryReplacesADiscoveredOneOfTheSameName() throws ScriptException {
        final ScriptLibrary override = ScriptLibrary.of("testlib", Map.of(), Script.of("mine.js", "function testlibGreet() { return 'overridden'; }"));
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(new String[] { "-doe" }, loader, null, List.of(override));
        assertEquals(engine.eval("testlibGreet('x')"), "overridden");
        assertEquals(engine.eval("typeof testlibVersion"), "undefined");
    }

    @Test
    public void librariesRunInOrderGlobalsFirst() throws ScriptException {
        final ScriptLibrary first = ScriptLibrary.of("first", Map.of("base", 10), Script.of("a.js", "var fromFirst = base + 1;"));
        final ScriptLibrary second = ScriptLibrary.of("second", Map.of(), Script.of("b.js", "var fromSecond = fromFirst + 1;"));
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(first, second);
        assertEquals(((Number)engine.eval("fromSecond")).intValue(), 12);
    }

    /** A library that adds a method to a built-in prototype, from Java. */
    private static final ScriptLibrary STRINGS = new ScriptLibrary() {
        @Override
        public String name() {
            return "strings";
        }

        @Override
        public List<Script> scripts() {
            return List.of(Script.of("strings.js", "var stringsLoaded = true;"));
        }

        @Override
        public void initialize(final JSObject global) {
            final JSObject prototype = (JSObject)((JSObject)global.getMember("String")).getMember("prototype");
            prototype.setMember("capitalize", new AbstractJSObject() {
                @Override
                public Object call(final Object thiz, final Object... args) {
                    final String s = String.valueOf(thiz);
                    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
                }

                @Override
                public boolean isFunction() {
                    return true;
                }
            });
            // the library's own scripts have run by now
            global.setMember("stringsReady", global.getMember("stringsLoaded"));
        }
    };

    @Test
    public void initializeReachesIntoTheGlobalFromJava() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(STRINGS);
        assertEquals(engine.eval("'nashorn'.capitalize()"), "Nashorn");
        assertEquals(engine.eval("''.capitalize()"), "");
        assertEquals(engine.eval("typeof String.prototype.capitalize"), "function");
        assertEquals(engine.eval("stringsReady"), true);
        // in every global, each with its own String.prototype
        assertEquals(engine.eval("'other'.capitalize()", engine.createBindings()), "Other");
        assertEquals(engine.eval("loadWithNewGlobal({ name: 'n.js', script: \"'new'.capitalize()\" })"), "New");
        // a library that comes later sees it
        final ScriptLibrary user = ScriptLibrary.of("user", Map.of(), Script.of("user.js", "var greeting = 'hello'.capitalize();"));
        assertEquals(new NashornScriptEngineFactory().getScriptEngine(STRINGS, user).eval("greeting"), "Hello");
    }

    @Test
    public void aFailingInitializerFailsEngineCreationNamingItself() {
        final ScriptLibrary broken = new ScriptLibrary() {
            @Override
            public String name() {
                return "brokeninit";
            }

            @Override
            public void initialize(final JSObject global) {
                throw new IllegalArgumentException("not today");
            }
        };
        try {
            new NashornScriptEngineFactory().getScriptEngine(broken);
            fail("expected the engine creation to fail");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("'brokeninit'"), e.getMessage());
            assertTrue(e.getMessage().contains("initialize"), e.getMessage());
            assertTrue(e.getMessage().contains("not today"), e.getMessage());
        }
    }

    @Test
    public void aFailingLibraryFailsEngineCreationNamingItself() {
        final ScriptLibrary broken = ScriptLibrary.of("broken", Map.of(), Script.of("broken.js", "throw new Error('no good');"));
        try {
            new NashornScriptEngineFactory().getScriptEngine(broken);
            fail("expected the engine creation to fail");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("'broken'"), e.getMessage());
            assertTrue(e.getMessage().contains("broken.js"), e.getMessage());
            assertTrue(e.getMessage().contains("no good"), e.getMessage());
        }
    }

    @Test
    public void scriptsCanComeFromResources() throws ScriptException {
        final Script script = Script.ofResource(ScriptLibraryTest.class, "/META-INF/MANIFEST.MF");
        assertTrue(script.name().endsWith("MANIFEST.MF"));
        assertFalse(script.text().isEmpty());
        try {
            Script.ofResource(ScriptLibraryTest.class, "/no/such/resource.js");
            fail("expected a failure");
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("/no/such/resource.js"));
        }
    }

    @Test
    public void theShellGetsDiscoveredLibrariesToo() throws IOException {
        final Path script = Files.createTempFile("libtest", ".js");
        try {
            Files.writeString(script, "print(testlibGreet('jjs'));", StandardCharsets.UTF_8);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            final int exit = Shell.main(System.in, out, err, new String[] { "-cp", services.toString(), script.toString() });
            assertEquals(exit, 0, err.toString(StandardCharsets.UTF_8));
            assertEquals(out.toString(StandardCharsets.UTF_8).trim(), "hello jjs from testlib 1.0");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    public void aLibraryIsInstalledOncePerGlobal() throws ScriptException {
        final int before = TestScriptLibrary.installations;
        final ScriptEngine engine = discovering("-doe");
        assertEquals(TestScriptLibrary.installations, before + 1);
        engine.createBindings();
        engine.createBindings();
        assertEquals(TestScriptLibrary.installations, before + 3);
    }
}
