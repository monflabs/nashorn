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

package org.monflabs.nashorn.libs.node.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The Node {@code os} module from {@code import os from "os"}: the synchronous
 * system-information functions and values.
 */
@SuppressWarnings("javadoc")
public class NodeOsTest {
    private ScriptEngine engine;

    @BeforeMethod
    public void setUp() {
        engine = new NashornScriptEngineBuilder().build();
    }

    private Object r(final String expr) throws Exception {
        final Object ns = engine.eval("import os from 'os';\nexport const r = (" + expr + ");\n");
        return ((JSObject)ns).getMember("r");
    }

    @Test
    public void platformArchTypeAndPaths() throws Exception {
        assertTrue(java.util.List.of("darwin", "linux", "win32", "sunos", "aix", "freebsd", "openbsd").contains(r("os.platform()")),
                "unexpected platform " + r("os.platform()"));
        assertTrue(java.util.List.of("x64", "arm64", "ia32", "arm", "ppc64", "s390x").contains(r("os.arch()"))
                || ((String)r("os.arch()")).length() > 0);
        assertTrue(((String)r("os.type()")).length() > 0);
        assertTrue(((String)r("os.homedir()")).length() > 0);
        assertTrue(((String)r("os.tmpdir()")).length() > 0);
        assertTrue(((String)r("os.hostname()")).length() > 0);
        assertTrue(java.util.List.of("LE", "BE").contains(r("os.endianness()")));
        assertEquals(r("os.EOL"), System.lineSeparator());
    }

    @Test
    public void memoryCpusAndParallelism() throws Exception {
        assertEquals(r("os.availableParallelism()"), Runtime.getRuntime().availableProcessors());
        assertTrue(((Number)r("os.totalmem()")).doubleValue() > 0, "totalmem should be positive");
        assertTrue(((Number)r("os.freemem()")).doubleValue() >= 0);
        assertTrue(((Number)r("os.uptime()")).doubleValue() >= 0);
        assertEquals(r("os.cpus().length"), Runtime.getRuntime().availableProcessors());
        assertTrue((Boolean)r("(function(){ var c = os.cpus()[0]; return typeof c.model === 'string' && typeof c.times.idle === 'number'; })()"));
        assertEquals(r("os.loadavg().length"), 3);
    }

    @Test
    public void userInfoNetworkAndConstants() throws Exception {
        assertTrue(((String)r("os.userInfo().username")).length() > 0);
        assertTrue(((String)r("os.userInfo().homedir")).length() > 0);
        assertEquals(r("typeof os.networkInterfaces()"), "object");
        assertEquals(((Number)r("os.constants.signals.SIGINT")).intValue(), 2);
        assertEquals(((Number)r("os.constants.signals.SIGKILL")).intValue(), 9);
        assertEquals(((Number)r("os.constants.priority.PRIORITY_NORMAL")).intValue(), 0);
    }

    @Test
    public void namedImportAndNodeScheme() throws Exception {
        final Object ns = engine.eval("import { platform, EOL } from 'node:os';\nexport const p = platform();\nexport const e = (typeof EOL === 'string');\n");
        assertTrue(((String)((JSObject)ns).getMember("p")).length() > 0);
        assertEquals(((JSObject)ns).getMember("e"), Boolean.TRUE);
    }
}
