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

package com.example.nashorn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;
import org.monflabs.nashorn.modules.node.NodeModuleLoader;
import org.junit.jupiter.api.Test;

/**
 * Verifies the three published jars resolve from the local repository and work:
 * nashorn-core (the engine + JSR-223 + the standard libraries), nashorn-node
 * (the Node module resolver) and nashorn-debugger (the debugging API over core).
 */
class NashornArtifactsTest {

    @Test
    void coreEngineIsDiscoverableAndRunsEs2017() throws Exception {
        // nashorn-core, via plain JSR-223 discovery from the jar on the class path
        final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn-monflabs");
        assertNotNull(engine, "the JSR-223 factory should be discovered from nashorn-core");
        assertEquals(3, ((Number) engine.eval("1 + 2")).intValue());
        assertEquals("--x", engine.eval("'x'.padStart(3, '-')"));           // ES2017
        assertEquals("has2=true", engine.eval("(() => `has2=${[1,2,3].includes(2)}`)()")); // arrow + template + includes
    }

    @Test
    void standardLibrariesInstallThroughTheBuilder() throws Exception {
        // the host + fetch libraries ship in nashorn-core, contributed explicitly
        final ScriptEngine engine = new NashornScriptEngineBuilder()
                .library(new HostLibrary(), new FetchLibrary())
                .build();
        assertEquals("function", engine.eval("typeof setTimeout"));
        assertEquals("function", engine.eval("typeof fetch"));
        // and a bare engine has neither
        assertEquals("undefined", new NashornScriptEngineBuilder().build().eval("typeof setTimeout"));
    }

    @Test
    void nodeModulesResolveThroughTheResolver() throws Exception {
        // nashorn-node: registered on the builder, reached by import
        final ScriptEngine engine = new NashornScriptEngineBuilder()
                .moduleLoader(new NodeModuleLoader())
                .build();
        final Object ns = engine.eval(
                "import path from 'path';\n"
              + "import os from 'os';\n"
              + "import { Buffer } from 'buffer';\n"
              + "export const base = path.basename('/a/b/c.js');\n"
              + "export const hex  = Buffer.from('AB', 'utf8').toString('hex');\n"
              + "export const arch = typeof os.arch();\n");
        assertEquals("c.js", ((JSObject) ns).getMember("base"));
        assertEquals("4142", ((JSObject) ns).getMember("hex"));
        assertEquals("string", ((JSObject) ns).getMember("arch"));
    }

    @Test
    void debuggerAttachesToTheEngine() throws Exception {
        // nashorn-debugger links against core: --debugger, then observe a scriptParsed
        final ScriptEngine engine = new NashornScriptEngineBuilder().debugger(true).build();
        final Debugger debugger = Debugger.of(engine);
        assertNotNull(debugger, "Debugger.of should return the engine's debugger under --debugger");
        final List<String> parsed = new CopyOnWriteArrayList<>();
        debugger.addListener(new DebugListener() {
            @Override public void scriptParsed(final DebugScript script) { parsed.add(script.url()); }
        });
        engine.eval("var x = 40 + 2; x;");
        assertTrue(!parsed.isEmpty(), "the debugger should have seen the script compile");
        assertTrue(!debugger.scripts().isEmpty());
    }
}
