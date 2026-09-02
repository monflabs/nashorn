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

package org.monflabs.nashorn.api.modules.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.modules.JavaModuleLoader;
import org.monflabs.nashorn.api.modules.Module;
import org.monflabs.nashorn.api.modules.ModuleLoader;
import org.monflabs.nashorn.api.modules.PathModuleLoader;
import org.monflabs.nashorn.api.modules.ResourceModuleLoader;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.monflabs.nashorn.tools.Shell;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Pluggable module loaders, consumed through the language's own import
 * syntax: the chain, the three built-in loaders, and eval running a module.
 */
public class ModuleLoaderTest {
    private Path dir;

    @BeforeClass
    public void files() throws IOException {
        dir = Files.createTempDirectory("module-loader-test");
        Files.writeString(dir.resolve("counter.js"), "export let count = 0;\nexport function increment() { count++; }\nexport default 'the counter';\n");
        Files.writeString(dir.resolve("app.js"), "import theDefault, { count, increment } from './counter.js';\nincrement();\nexport { count, theDefault };\n");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub").resolve("inner.js"), "import { count } from '../counter.js';\nexport const seen = 'inner sees ' + count;\n");
    }

    @AfterClass
    public void cleanup() throws IOException {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    private ScriptEngine engine(final ModuleLoader... loaders) {
        return new NashornScriptEngineBuilder().moduleLoader(loaders).build();
    }

    // -- eval runs a module -------------------------------------------------------------

    @Test
    public void evalDetectsAndRunsAModule() throws ScriptException {
        final ScriptEngine e = engine(new PathModuleLoader(dir));
        final Object namespace = e.eval("import { count, theDefault } from 'app.js';\nvar seen = theDefault + ':' + count;\nexport { seen };");
        assertTrue(namespace instanceof ScriptObjectMirror, String.valueOf(namespace));
        assertEquals(((ScriptObjectMirror)namespace).getMember("seen"), "the counter:1");
    }

    @Test
    public void aPlainScriptIsUnaffectedAndABadScriptReportsTheScriptError() throws ScriptException {
        final ScriptEngine e = engine(new PathModuleLoader(dir));
        assertEquals(e.eval("1 + 1"), 2);
        try {
            e.eval("var x = ;");
            fail("expected the script's parse error");
        } catch (final ScriptException expected) {
            assertTrue(expected.getMessage().contains("Expected"), expected.getMessage());
        }
        // module syntax with a bad body: reported as a script error, since neither goal parses
        try {
            e.eval("import { x } from 'app.js'; var y = ;");
            fail("expected a parse error");
        } catch (final ScriptException expected) {
            assertTrue(expected.getMessage().contains("Expected"), expected.getMessage());
        }
    }

    @Test
    public void moduleScopeStaysOutOfTheGlobal() throws ScriptException {
        final ScriptEngine e = engine(new PathModuleLoader(dir));
        e.eval("import { count } from 'app.js';\nvar inModule = count;");
        assertEquals(e.eval("typeof inModule + ' ' + typeof count"), "undefined undefined");
    }

    // -- the chain ----------------------------------------------------------------------

    @Test
    public void theFirstLoaderThatAnswersWins() throws ScriptException {
        final List<String> asked = new ArrayList<>();
        final ModuleLoader recording = (specifier, referrer) -> {
            asked.add("first:" + specifier);
            return null;
        };
        final ModuleLoader second = (specifier, referrer) -> specifier.equals("mod")
                ? Module.source("chain:mod", "export const from = 'second';") : null;
        final ModuleLoader third = (specifier, referrer) -> Module.source("chain:" + specifier, "export const from = 'third';");
        final ScriptEngine e = engine(recording, second, third);
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval("import { from } from 'mod'; export { from };");
        assertEquals(ns.getMember("from"), "second");
        assertEquals(asked, List.of("first:mod"));
        final ScriptObjectMirror other = (ScriptObjectMirror)e.eval("import { from } from 'other'; export { from };");
        assertEquals(other.getMember("from"), "third");
    }

    @Test
    public void noLoaderAnsweringIsATypeErrorNamingTheSpecifier() {
        final ScriptEngine e = engine((specifier, referrer) -> null);
        try {
            e.eval("import { x } from 'nowhere';");
            fail("expected the resolution error");
        } catch (final ScriptException expected) {
            assertTrue(expected.getMessage().contains("nowhere"), expected.getMessage());
        }
    }

    @Test
    public void registeringALoaderReplacesTheDefaultFilesystem() {
        final ScriptEngine e = engine(new JavaModuleLoader().add("math", Map.of("x", 1)));
        try {
            e.eval("import { count } from '" + dir.resolve("app.js") + "';");
            fail("expected the resolution error");
        } catch (final ScriptException expected) {
            assertTrue(expected.getMessage().contains("app.js"), expected.getMessage());
        }
    }

    // -- PathModuleLoader ---------------------------------------------------------------

    @Test
    public void pathsResolveAgainstRootAndReferrer() throws ScriptException {
        final ScriptEngine e = engine(new PathModuleLoader(dir));
        // bare against the root, ./ and ../ against the importer, absolute as itself
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval(
                "import { seen } from 'sub/inner.js';\nimport { count } from '" + dir.resolve("counter.js") + "';\nexport { seen, count };");
        assertEquals(ns.getMember("seen"), "inner sees 0");
        assertEquals(ns.getMember("count"), 0);
    }

    @Test
    public void aModuleLoadsOncePerRealm() throws ScriptException {
        final ScriptEngine e = engine(new PathModuleLoader(dir));
        // app.js increments the shared counter; a second import sees the same instance
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval(
                "import { count as viaApp } from 'app.js';\nimport { count as direct } from 'counter.js';\nexport const both = viaApp + ':' + direct;\nexport { both as b };");
        assertEquals(ns.getMember("both"), "1:1");
        // a fresh bindings is a fresh realm: the counter starts over
        final Object fresh = e.eval("import { count } from 'app.js'; export { count };", e.createBindings());
        assertEquals(((Number)((ScriptObjectMirror)fresh).getMember("count")).intValue(), 1);
    }

    @Test
    public void aMissingFileIsNullNotAnError() {
        final PathModuleLoader loader = new PathModuleLoader(dir);
        assertEquals(loader.load("no-such.js", null), null);
        assertEquals(loader.load("../../../outside.js", Module.referrer("x", "not a path")), null);
    }

    // -- ResourceModuleLoader -----------------------------------------------------------

    @Test
    public void resourcesResolveUnderTheRootAndRelativeAmongThemselves() throws ScriptException {
        final ScriptEngine e = engine(new ResourceModuleLoader(ModuleLoaderTest.class, "/modulefixtures"));
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval(
                "import { origin, helper } from 'lib.js';\nexport const all = origin + ' ' + helper();\nexport { all as a };");
        assertEquals(ns.getMember("all"), "resource helper+deep");
    }

    @Test
    public void resourceNamesCarryTheClasspathPrefix() {
        final ResourceModuleLoader loader = new ResourceModuleLoader(ModuleLoaderTest.class, "modulefixtures");
        final Module lib = loader.load("lib.js", null);
        assertEquals(lib.name(), "classpath:/modulefixtures/lib.js");
        assertEquals(loader.load("no-such.js", null), null);
        // never above the root
        assertEquals(loader.load("../outside.js", lib), null);
    }

    // -- JavaModuleLoader ---------------------------------------------------------------

    @Test
    public void aPureJavaModuleIsImportable() throws ScriptException {
        final JSObject add = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return ScriptUtils.toNumber(args[0]) + ScriptUtils.toNumber(args[1]);
            }
        };
        final ScriptEngine e = engine(new JavaModuleLoader()
                .add("math", Map.of("TAU", 2 * Math.PI, "add", add, "default", "the math module")));
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval(
                "import theDefault, { TAU, add } from 'math';\nimport * as math from 'math';\n"
                + "export const sum = add(TAU, 1);\nexport const name = theDefault;\nexport const keys = Object.keys(math).join();");
        assertEquals(ns.getMember("sum"), 2 * Math.PI + 1);
        assertEquals(ns.getMember("name"), "the math module");
        assertEquals(ns.getMember("keys"), "TAU,add,default");
    }

    @Test
    public void eachRealmGetsItsOwnNamespaceOverTheSameValues() throws ScriptException {
        final ScriptEngine e = engine(new JavaModuleLoader().add("math", Map.of("TAU", 2 * Math.PI)));
        final Object one = e.eval("import * as m from 'math'; export const ns = m; export { ns as n };");
        final Object two = e.eval("import * as m from 'math'; export const ns = m; export { ns as n };", e.createBindings());
        final Object nsOne = ((ScriptObjectMirror)one).getMember("ns");
        final Object nsTwo = ((ScriptObjectMirror)two).getMember("ns");
        assertNotSame(((ScriptObjectMirror)nsOne).to(Object.class), ((ScriptObjectMirror)nsTwo).to(Object.class));
        assertEquals(((ScriptObjectMirror)nsOne).getMember("TAU"), ((ScriptObjectMirror)nsTwo).getMember("TAU"));
    }

    // -- across loaders -----------------------------------------------------------------

    @Test
    public void aGraphMayCrossLoaders() throws ScriptException, IOException {
        Files.writeString(dir.resolve("bridge.js"), "import { origin } from 'lib.js';\nimport { TAU } from 'math';\nexport const crossed = origin + ' ' + (TAU > 6);\n");
        final ScriptEngine e = engine(
                new PathModuleLoader(dir),
                new ResourceModuleLoader(ModuleLoaderTest.class, "/modulefixtures"),
                new JavaModuleLoader().add("math", Map.of("TAU", 2 * Math.PI)));
        final ScriptObjectMirror ns = (ScriptObjectMirror)e.eval("import { crossed } from 'bridge.js'; export { crossed };");
        assertEquals(ns.getMember("crossed"), "resource true");
    }

    // -- the shell ----------------------------------------------------------------------

    @Test
    public void jjsRunsAnImportBearingFile() throws IOException {
        final Path entry = dir.resolve("entry.js");
        Files.writeString(entry, "import theDefault, { count } from './counter.js';\nprint(theDefault + ' ' + count);\n");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int exit = Shell.main(System.in, out, err, new String[] { entry.toString() });
        assertEquals(exit, 0, err.toString(StandardCharsets.UTF_8));
        assertEquals(out.toString(StandardCharsets.UTF_8).trim(), "the counter 0");
    }
}
