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
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.monflabs.nashorn.tools.Shell;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Script libraries: contributed as services or explicitly, installed into
 * every global an engine makes - globals, scripts, then the initializer.
 */
public class ScriptLibraryTest {

    /** A function implemented in Java, handed out as a global - coercing its argument as the language would. */
    private static final JSObject AREA = new AbstractJSObject() {
        @Override
        public Object call(final Object thiz, final Object... args) {
            final double r = ScriptUtils.toNumber(args.length == 0 ? ScriptUtils.undefined() : args[0]);
            return Math.PI * r * r;
        }

        @Override
        public boolean isFunction() {
            return true;
        }
    };

    /** The guide's example: a Java function and a Java object among the globals, a script building on them. */
    private static final ScriptLibrary GEOMETRY;
    static {
        final Map<String, Object> globals = new LinkedHashMap<>();
        globals.put("TAU", 2 * Math.PI);
        globals.put("area", AREA);
        globals.put("clock", java.time.Clock.systemUTC());
        GEOMETRY = ScriptLibrary.of("geometry", globals,
                Script.of("geometry.js", "function circumference(r) { return TAU * r; }\n"
                        + "var shapes = { circle: function (r) { return { radius: r, area: area(r), circumference: circumference(r) }; } };\n"
                        + "var unit = shapes.circle(1);"),
                Script.of("more.js", "function diameter(r) { return 2 * r; }"));
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

    /** A class path holding only a service registration of {@link TestScriptLibrary}, and a loader over it. */
    private Path services;
    private URLClassLoader loader;
    /** The same for a provider with no name. */
    private Path namelessServices;
    private URLClassLoader namelessLoader;

    @BeforeClass
    public void registerTheTestLibraries() throws IOException {
        services = register(TestScriptLibrary.class);
        loader = new URLClassLoader(new URL[] { services.toUri().toURL() }, ScriptLibraryTest.class.getClassLoader());
        namelessServices = register(NamelessScriptLibrary.class);
        namelessLoader = new URLClassLoader(new URL[] { namelessServices.toUri().toURL() }, ScriptLibraryTest.class.getClassLoader());
    }

    private static Path register(final Class<? extends ScriptLibrary> provider) throws IOException {
        final Path dir = Files.createTempDirectory("script-library-services");
        final Path file = dir.resolve("META-INF").resolve("services").resolve(ScriptLibrary.class.getName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, provider.getName() + "\n", StandardCharsets.UTF_8);
        return dir;
    }

    @AfterClass
    public void unregister() throws IOException {
        loader.close();
        namelessLoader.close();
        for (final Path dir : List.of(services, namelessServices)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private ScriptEngine discovering(final String... args) {
        return new NashornScriptEngineFactory().getScriptEngine(args, loader, null, List.of());
    }

    // -- globals and scripts ------------------------------------------------------------

    @Test
    public void anExplicitLibraryContributesGlobalsAndScripts() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        assertEquals(engine.eval("circumference(1)"), 2 * Math.PI);
        assertEquals(((Number)engine.eval("diameter(21)")).intValue(), 42);
        assertEquals(engine.eval("typeof TAU"), "number");
        assertEquals(engine.eval("unit.radius"), 1);
    }

    @Test
    public void aJavaFunctionAmongTheGlobalsIsCallableFromScriptAndFromTheLibrarysOwnScripts() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        assertEquals(engine.eval("typeof area"), "function");
        assertEquals(engine.eval("area(2)"), Math.PI * 4);
        // geometry.js called it while the library was installed
        assertEquals(engine.eval("unit.area"), Math.PI);
        assertEquals(engine.eval("shapes.circle(3).area"), Math.PI * 9);
    }

    @Test
    public void aJavaFunctionCoercesItsArgumentsAsTheLanguageWould() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        assertEquals(engine.eval("area('2')"), Math.PI * 4);
        assertEquals(engine.eval("area(true)"), Math.PI);
        assertEquals(engine.eval("area({ valueOf: function () { return 3; } })"), Math.PI * 9);
        assertEquals(engine.eval("area([2])"), Math.PI * 4);
        assertEquals(engine.eval("area(null)"), 0.0);
        assertEquals(engine.eval("isNaN(area())"), true);
        assertEquals(engine.eval("isNaN(area(undefined))"), true);
        assertEquals(engine.eval("isNaN(area('abc'))"), true);
        assertEquals(engine.eval("area(2, 'ignored')"), Math.PI * 4);
    }

    @Test
    public void convertFollowsTheLanguageForNull() {
        assertEquals(ScriptUtils.convert(null, double.class), 0.0);
        assertEquals(ScriptUtils.convert(null, int.class), 0);
        assertEquals(ScriptUtils.convert(null, long.class), 0L);
        assertEquals(ScriptUtils.convert(null, boolean.class), false);
        assertEquals(ScriptUtils.convert(null, char.class), (char)0);
        assertEquals(ScriptUtils.convert(null, String.class), null);
        assertEquals(ScriptUtils.convert(null, Double.class), null);
    }

    @Test
    public void aJavaObjectAmongTheGlobalsIsUsedThroughTheInterop() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        assertEquals(engine.eval("clock.instant().getClass().getSimpleName()"), "Instant");
        assertEquals(engine.eval("Java.isJavaObject(clock)"), true);
    }

    @Test
    public void globalsAreDefinedBeforeTheScriptsAndInTheirOwnOrder() throws ScriptException {
        final Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("zeta", 1);
        ordered.put("alpha", 2);
        final ScriptLibrary library = ScriptLibrary.of("ordered", ordered,
                Script.of("o.js", "var seen = Object.keys(this).filter(function (k) { return k === 'zeta' || k === 'alpha'; }).join(',');"
                        + "var viaThis = this.zeta + this.alpha;"));
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(library);
        assertEquals(engine.eval("seen"), "zeta,alpha");
        assertEquals(((Number)engine.eval("viaThis")).intValue(), 3);
        assertEquals(library.globals().keySet().iterator().next(), "zeta");
    }

    @Test
    public void librariesRunOneAfterTheOther() throws ScriptException {
        final ScriptLibrary first = ScriptLibrary.of("first", Map.of("base", 10), Script.of("a.js", "var fromFirst = base + 1;"));
        final ScriptLibrary second = ScriptLibrary.of("second", Map.of(), Script.of("b.js", "var fromSecond = fromFirst + 1;"));
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(first, second);
        assertEquals(((Number)engine.eval("fromSecond")).intValue(), 12);
        // the other order fails, as it should: nothing has declared fromFirst yet
        try {
            new NashornScriptEngineFactory().getScriptEngine(second, first);
            fail("expected the engine creation to fail");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("'second'"), e.getMessage());
        }
    }

    // -- every global ------------------------------------------------------------------

    @Test
    public void everyNewGlobalGetsTheLibrary() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        final Bindings fresh = engine.createBindings();
        assertEquals(engine.eval("circumference(2)", fresh), 4 * Math.PI);
        final ScriptContext context = new SimpleScriptContext();
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        assertEquals(engine.eval("area(1)", context), Math.PI);
        // and one made by a script
        assertEquals(engine.eval("loadWithNewGlobal({ name: 'x.js', script: 'circumference(0.5)' })"), Math.PI);
        // each global has its own copy: a change in one is not seen in another
        engine.eval("unit.radius = 5", fresh);
        assertEquals(engine.eval("unit.radius"), 1);
    }

    @Test
    public void theSameLibraryServesSeveralEnginesIndependently() throws ScriptException {
        final ScriptEngine one = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        final ScriptEngine two = new NashornScriptEngineFactory().getScriptEngine(GEOMETRY);
        one.eval("unit.radius = 7; shapes.extra = true;");
        assertEquals(two.eval("unit.radius"), 1);
        assertEquals(two.eval("typeof shapes.extra"), "undefined");
        assertNotSame(one.eval("shapes"), two.eval("shapes"));
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

    // -- discovery ----------------------------------------------------------------------

    @Test
    public void aLibraryIsDiscoveredAsAService() throws ScriptException {
        final ScriptEngine engine = discovering("-doe");
        assertEquals(engine.eval("testlibGreet('world')"), "hello world from testlib 1.0");
        // an engine whose loader has no registration does not get it
        assertEquals(new NashornScriptEngineFactory().getScriptEngine().eval("typeof testlibGreet"), "undefined");
    }

    @Test
    public void discoveryUsesTheContextClassLoaderWhenNoneIsGiven() throws ScriptException {
        final ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            assertEquals(new NashornScriptEngineFactory().getScriptEngine().eval("typeof testlibGreet"), "function");
            assertEquals(new NashornScriptEngineFactory().getScriptEngine("-doe").eval("typeof testlibGreet"), "function");
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
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
    public void aDiscoveredLibraryWithoutANameIsRefused() {
        try {
            new NashornScriptEngineFactory().getScriptEngine(new String[] { "-doe" }, namelessLoader, null, List.of());
            fail("expected the engine creation to fail");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains(NamelessScriptLibrary.class.getName()), e.getMessage());
            assertTrue(e.getMessage().contains("no name"), e.getMessage());
        }
    }

    // -- initialize ---------------------------------------------------------------------

    @Test
    public void initializeReachesIntoTheGlobalFromJava() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(STRINGS);
        assertEquals(engine.eval("'nashorn'.capitalize()"), "Nashorn");
        assertEquals(engine.eval("''.capitalize()"), "");
        assertEquals(engine.eval("typeof String.prototype.capitalize"), "function");
        // in every global, each with its own String.prototype
        assertEquals(engine.eval("'other'.capitalize()", engine.createBindings()), "Other");
        assertEquals(engine.eval("loadWithNewGlobal({ name: 'n.js', script: \"'new'.capitalize()\" })"), "New");
    }

    @Test
    public void initializeRunsAfterTheLibrarysOwnScriptsAndBeforeTheNextLibrary() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(STRINGS);
        assertEquals(engine.eval("stringsReady"), true);
        final ScriptLibrary user = ScriptLibrary.of("user", Map.of(), Script.of("user.js", "var greeting = 'hello'.capitalize();"));
        assertEquals(new NashornScriptEngineFactory().getScriptEngine(STRINGS, user).eval("greeting"), "Hello");
    }

    @Test
    public void initializeCanEvaluateInTheGlobal() throws ScriptException {
        final ScriptLibrary evaluating = new ScriptLibrary() {
            @Override
            public String name() {
                return "evaluating";
            }

            @Override
            public void initialize(final JSObject global) {
                global.eval("var evaluated = 6 * 7;");
                global.setMember("fromJava", ((Number)global.getMember("evaluated")).intValue() + 1);
            }
        };
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(evaluating);
        assertEquals(((Number)engine.eval("evaluated")).intValue(), 42);
        assertEquals(((Number)engine.eval("fromJava")).intValue(), 43);
    }

    // -- failures -----------------------------------------------------------------------

    @Test
    public void aFailingScriptFailsEngineCreationNamingItself() {
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
    public void aScriptThatDoesNotParseFailsEngineCreationNamingItself() {
        final ScriptLibrary broken = ScriptLibrary.of("unparsable", Map.of(), Script.of("bad.js", "var x = ;"));
        try {
            new NashornScriptEngineFactory().getScriptEngine(broken);
            fail("expected the engine creation to fail");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("'unparsable'"), e.getMessage());
            assertTrue(e.getMessage().contains("bad.js"), e.getMessage());
        }
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

    // -- the building blocks --------------------------------------------------------------

    @Test
    public void scriptsCanComeFromResourcesAndUrls() throws IOException {
        final Script resource = Script.ofResource(ScriptLibraryTest.class, "/META-INF/MANIFEST.MF");
        assertTrue(resource.name().endsWith("MANIFEST.MF"));
        assertFalse(resource.text().isEmpty());
        try {
            Script.ofResource(ScriptLibraryTest.class, "/no/such/resource.js");
            fail("expected a failure");
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("/no/such/resource.js"), e.getMessage());
        }
        final Path file = Files.createTempFile("lib", ".js");
        try {
            Files.writeString(file, "var fromUrl = 'yes';", StandardCharsets.UTF_8);
            final Script url = Script.ofUrl(file.toUri().toURL());
            assertEquals(url.text(), "var fromUrl = 'yes';");
            assertTrue(url.name().endsWith(".js"));
        } finally {
            Files.deleteIfExists(file);
        }
        try {
            Script.ofUrl(Path.of("/no/such/file.js").toUri().toURL());
            fail("expected a failure");
        } catch (final IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("file.js"), e.getMessage());
        }
    }

    @Test
    public void theFactoriesValidateTheirArguments() {
        try {
            ScriptLibrary.of(null, Map.of());
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        try {
            ScriptLibrary.of("x", null);
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        try {
            ScriptLibrary.of("x", Map.of(), (Script[])null);
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        try {
            Script.of("x", null);
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        try {
            new NashornScriptEngineFactory().getScriptEngine((ScriptLibrary[])null);
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        try {
            new NashornScriptEngineFactory().getScriptEngine(new String[] { "-doe" }, (ScriptLibrary)null);
            fail("expected a NullPointerException");
        } catch (final NullPointerException expected) {
            // as documented
        }
        assertEquals(ScriptLibrary.of("named", Map.of()).name(), "named");
        assertTrue(ScriptLibrary.of("named", Map.of()).scripts().isEmpty());
        assertEquals(String.valueOf(ScriptLibrary.of("named", Map.of())), "ScriptLibrary named");
    }

    // -- the shell ------------------------------------------------------------------------

    @Test
    public void theShellGetsDiscoveredLibrariesToo() throws IOException {
        final Path script = Files.createTempFile("libtest", ".js");
        try {
            Files.writeString(script, "print(testlibGreet('jjs'));", StandardCharsets.UTF_8);
            assertEquals(shell("-cp", services.toString(), script.toString()), "hello jjs from testlib 1.0");
            Files.writeString(script, "print(typeof testlibGreet);", StandardCharsets.UTF_8);
            assertEquals(shell("-cp", services.toString(), "--libraries=none", script.toString()), "undefined");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private static String shell(final String... args) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int exit = Shell.main(System.in, out, err, args);
        assertEquals(exit, 0, err.toString(StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8).trim();
    }
}
