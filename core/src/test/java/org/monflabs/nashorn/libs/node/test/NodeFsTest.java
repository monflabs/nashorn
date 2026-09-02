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

import java.nio.file.Files;
import java.nio.file.Path;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The Node {@code fs} module resolved by {@code import fs from "fs"}: the
 * synchronous, callback and promise forms over a temp directory. Each script is
 * an ES module, so its results are read back from the exported namespace.
 */
@SuppressWarnings("javadoc")
public class NodeFsTest {
    private ScriptEngine engine;
    private Path dir;

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineBuilder().build();
        dir = Files.createTempDirectory("nodefs");
        engine.put("DIR", dir.toString().replace("\\", "/"));
    }

    /** Evaluates a module and returns member {@code key} of its namespace. */
    private Object export(final String body, final String key) throws Exception {
        final Object ns = engine.eval("import fs from 'fs';\nexport var box = {};\n" + body);
        return ((JSObject)ns).getMember("box") instanceof JSObject b ? b.getMember(key) : null;
    }

    @Test
    public void syncReadWriteExistsReaddirMkdirStat() throws Exception {
        engine.eval("import fs from 'fs';\n"
           + "fs.writeFileSync(DIR + '/a.txt', 'hello');\n"
           + "if (fs.readFileSync(DIR + '/a.txt', 'utf8') !== 'hello') throw 'read';\n"
           + "if (!fs.existsSync(DIR + '/a.txt')) throw 'exists';\n"
           + "if (fs.existsSync(DIR + '/nope')) throw 'notexists';\n"
           + "fs.mkdirSync(DIR + '/sub');\n"
           + "fs.writeFileSync(DIR + '/sub/b.txt', 'x');\n"
           + "var names = fs.readdirSync(DIR).sort();\n"
           + "if (names.indexOf('a.txt') < 0 || names.indexOf('sub') < 0) throw 'readdir ' + names;\n"
           + "if (typeof names.forEach !== 'function') throw 'not a JS array';\n"
           + "var st = fs.statSync(DIR + '/sub');\n"
           + "if (!st.isDirectory() || st.isFile()) throw 'stat';\n"
           + "var fst = fs.statSync(DIR + '/a.txt');\n"
           + "if (!fst.isFile() || fst.size !== 5) throw 'stat size ' + fst.size;\n"
           + "fs.appendFileSync(DIR + '/a.txt', '!');\n"
           + "if (fs.readFileSync(DIR + '/a.txt', 'utf8') !== 'hello!') throw 'append';\n"
           + "fs.unlinkSync(DIR + '/sub/b.txt');\n"
           + "if (fs.existsSync(DIR + '/sub/b.txt')) throw 'unlink';\n"
           + "fs.rmSync(DIR + '/sub', { recursive: true });\n"
           + "if (fs.existsSync(DIR + '/sub')) throw 'rm';\n");
    }

    @Test
    public void syncErrorHasNodeCode() throws Exception {
        assertEquals(export("try { fs.readFileSync(DIR + '/missing'); } catch (e) { box.code = e.code; }\n", "code"), "ENOENT");
    }

    @Test
    public void asyncCallbackForm() throws Exception {
        engine.eval("import fs from 'fs';\nfs.writeFileSync(DIR + '/c.txt', 'async');");
        assertEquals(export("fs.readFile(DIR + '/c.txt', 'utf8', function (err, data) { box.data = data; });\n", "data"), "async");
    }

    @Test
    public void asyncCallbackReceivesError() throws Exception {
        assertEquals(export("fs.readFile(DIR + '/missing', function (err, data) { box.code = err && err.code; });\n", "code"), "ENOENT");
    }

    @Test
    public void promisesForm() throws Exception {
        engine.eval("import fs from 'fs';\nfs.writeFileSync(DIR + '/p.txt', 'promised');");
        assertEquals(export("fs.promises.readFile(DIR + '/p.txt', 'utf8').then(function (d) { box.data = d; });\n", "data"), "promised");
    }

    @Test
    public void namedImportAndNodeScheme() throws Exception {
        engine.eval("import { writeFileSync, readFileSync } from 'node:fs';\n"
           + "writeFileSync(DIR + '/n.txt', 'named');\n"
           + "if (readFileSync(DIR + '/n.txt', 'utf8') !== 'named') throw 'named import';\n");
    }
}
