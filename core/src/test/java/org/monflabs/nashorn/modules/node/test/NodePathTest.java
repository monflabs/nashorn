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

package org.monflabs.nashorn.modules.node.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The Node {@code path} module from {@code import path from "path"}: the pure
 * string operations, tested against both the {@code posix} and {@code win32}
 * flavours so the results do not depend on the host operating system.
 */
@SuppressWarnings("javadoc")
public class NodePathTest {
    private ScriptEngine engine;

    @BeforeMethod
    public void setUp() {
        engine = new NashornScriptEngineBuilder().build();
    }

    /** Evaluate {@code expr} with {@code path} imported and return the result. */
    private Object r(final String expr) throws Exception {
        final Object ns = engine.eval("import path from 'path';\nexport const r = (" + expr + ");\n");
        return ((JSObject) ns).getMember("r");
    }

    @Test
    public void separatorsAndDelimiters() throws Exception {
        assertEquals(r("path.posix.sep"), "/");
        assertEquals(r("path.win32.sep"), "\\");
        assertEquals(r("path.posix.delimiter"), ":");
        assertEquals(r("path.win32.delimiter"), ";");
        // Flavours cross-link and self-reference, as in Node.
        assertEquals(r("path.posix.win32.sep"), "\\");
        assertEquals(r("path.win32.posix.sep"), "/");
    }

    @Test
    public void basename() throws Exception {
        assertEquals(r("path.posix.basename('/foo/bar/baz/quux.html')"), "quux.html");
        assertEquals(r("path.posix.basename('/foo/bar/baz/quux.html', '.html')"), "quux");
        assertEquals(r("path.posix.basename('/foo/bar/')"), "bar");
        assertEquals(r("path.posix.basename('/')"), "");
        assertEquals(r("path.win32.basename('C:\\\\foo\\\\bar.txt')"), "bar.txt");
    }

    @Test
    public void dirname() throws Exception {
        assertEquals(r("path.posix.dirname('/foo/bar/baz/quux')"), "/foo/bar/baz");
        assertEquals(r("path.posix.dirname('/foo')"), "/");
        assertEquals(r("path.posix.dirname('foo')"), ".");
        assertEquals(r("path.win32.dirname('C:\\\\foo\\\\bar\\\\baz')"), "C:\\foo\\bar");
        assertEquals(r("path.win32.dirname('C:\\\\foo')"), "C:\\");
    }

    @Test
    public void extname() throws Exception {
        assertEquals(r("path.posix.extname('index.html')"), ".html");
        assertEquals(r("path.posix.extname('index.coffee.md')"), ".md");
        assertEquals(r("path.posix.extname('index.')"), ".");
        assertEquals(r("path.posix.extname('index')"), "");
        assertEquals(r("path.posix.extname('.index')"), "");
        assertEquals(r("path.posix.extname('.index.md')"), ".md");
    }

    @Test
    public void normalize() throws Exception {
        assertEquals(r("path.posix.normalize('/foo/bar//baz/asdf/quux/..')"), "/foo/bar/baz/asdf");
        assertEquals(r("path.posix.normalize('foo/bar/../baz')"), "foo/baz");
        assertEquals(r("path.posix.normalize('./foo/bar/')"), "foo/bar/");
        assertEquals(r("path.posix.normalize('')"), ".");
        assertEquals(r("path.win32.normalize('C:\\\\temp\\\\\\\\foo\\\\bar\\\\..\\\\')"), "C:\\temp\\foo\\");
        assertEquals(r("path.win32.normalize('C:////temp\\\\\\\\/\\\\/\\\\/foo/bar')"), "C:\\temp\\foo\\bar");
    }

    @Test
    public void join() throws Exception {
        assertEquals(r("path.posix.join('/foo', 'bar', 'baz/asdf', 'quux', '..')"), "/foo/bar/baz/asdf");
        assertEquals(r("path.posix.join('foo', '', 'bar')"), "foo/bar");
        assertEquals(r("path.posix.join()"), ".");
        assertEquals(r("path.win32.join('foo', 'bar', 'baz\\\\asdf', 'quux', '..')"), "foo\\bar\\baz\\asdf");
        assertEquals(r("path.win32.join('C:\\\\', 'foo', 'bar')"), "C:\\foo\\bar");
    }

    @Test
    public void isAbsolute() throws Exception {
        assertTrue((Boolean) r("path.posix.isAbsolute('/foo/bar')"));
        assertFalse((Boolean) r("path.posix.isAbsolute('qux/')"));
        assertFalse((Boolean) r("path.posix.isAbsolute('.')"));
        assertTrue((Boolean) r("path.win32.isAbsolute('C:\\\\foo\\\\bar')"));
        assertTrue((Boolean) r("path.win32.isAbsolute('\\\\\\\\server\\\\share')"));
        assertFalse((Boolean) r("path.win32.isAbsolute('C:foo')"));
    }

    @Test
    public void resolveIsAbsoluteAndCollapses() throws Exception {
        // resolve consults the cwd, so assert the shape rather than an exact string.
        assertTrue((Boolean) r("path.posix.isAbsolute(path.posix.resolve('foo/bar', './baz'))"));
        assertEquals(r("path.posix.resolve('/foo/bar', './baz')"), "/foo/bar/baz");
        assertEquals(r("path.posix.resolve('/foo/bar', '/tmp/file/')"), "/tmp/file");
        assertEquals(r("path.win32.resolve('C:\\\\foo\\\\bar', '.\\\\baz')"), "C:\\foo\\bar\\baz");
    }

    @Test
    public void relative() throws Exception {
        assertEquals(r("path.posix.relative('/data/orandea/test/aaa', '/data/orandea/impl/bbb')"),
                "../../impl/bbb");
        assertEquals(r("path.posix.relative('/a/b/c', '/a/b/c')"), "");
        assertEquals(r("path.win32.relative('C:\\\\orandea\\\\test\\\\aaa', 'C:\\\\orandea\\\\impl\\\\bbb')"),
                "..\\..\\impl\\bbb");
    }

    @Test
    public void parseAndFormatRoundTripPosix() throws Exception {
        assertEquals(r("path.posix.parse('/home/user/dir/file.txt').root"), "/");
        assertEquals(r("path.posix.parse('/home/user/dir/file.txt').dir"), "/home/user/dir");
        assertEquals(r("path.posix.parse('/home/user/dir/file.txt').base"), "file.txt");
        assertEquals(r("path.posix.parse('/home/user/dir/file.txt').ext"), ".txt");
        assertEquals(r("path.posix.parse('/home/user/dir/file.txt').name"), "file");
        assertEquals(r("path.posix.format({ dir: '/home/user/dir', base: 'file.txt' })"),
                "/home/user/dir/file.txt");
        assertEquals(r("path.posix.format({ root: '/', name: 'file', ext: '.txt' })"), "/file.txt");
    }

    @Test
    public void parseWin32() throws Exception {
        assertEquals(r("path.win32.parse('C:\\\\path\\\\dir\\\\file.txt').root"), "C:\\");
        assertEquals(r("path.win32.parse('C:\\\\path\\\\dir\\\\file.txt').dir"), "C:\\path\\dir");
        assertEquals(r("path.win32.parse('C:\\\\path\\\\dir\\\\file.txt').base"), "file.txt");
        assertEquals(r("path.win32.parse('C:\\\\path\\\\dir\\\\file.txt').ext"), ".txt");
        assertEquals(r("path.win32.parse('C:\\\\path\\\\dir\\\\file.txt').name"), "file");
        assertEquals(r("path.win32.format({ dir: 'C:\\\\path\\\\dir', base: 'file.txt' })"),
                "C:\\path\\dir\\file.txt");
    }

    @Test
    public void toNamespacedPath() throws Exception {
        // posix is an identity; win32 wraps a resolved drive path.
        assertEquals(r("path.posix.toNamespacedPath('/foo/bar')"), "/foo/bar");
        assertEquals(r("path.win32.toNamespacedPath('C:\\\\foo\\\\bar')"), "\\\\?\\C:\\foo\\bar");
    }

    @Test
    public void defaultMatchesTheHostFlavour() throws Exception {
        final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        assertEquals(r("path.sep"), windows ? "\\" : "/");
        // The bare named export is the host flavour's function.
        assertEquals(r("path.basename('/a/b/c.js')"), windows ? "/a/b/c.js" : "c.js");
    }

    @Test
    public void namedImportsResolveToHostFlavour() throws Exception {
        final Object ns = engine.eval(
                "import { join, sep } from 'path';\n"
              + "export const j = join('a', 'b', 'c');\n"
              + "export const s = sep;\n");
        final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        assertEquals(((JSObject) ns).getMember("s"), windows ? "\\" : "/");
        assertEquals(((JSObject) ns).getMember("j"), windows ? "a\\b\\c" : "a/b/c");
    }

    @Test
    public void nodePrefixSpecifierWorks() throws Exception {
        final Object ns = engine.eval(
                "import path from 'node:path';\nexport const r = path.posix.basename('/x/y.js');\n");
        assertEquals(((JSObject) ns).getMember("r"), "y.js");
    }
}
