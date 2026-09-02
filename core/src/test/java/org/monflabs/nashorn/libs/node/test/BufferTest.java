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
 * The Node {@code Buffer} from {@code import { Buffer } from "buffer"}: a
 * {@code Uint8Array} subclass with Node's encodings and numeric accessors, and
 * {@code fs} returning a {@code Uint8Array} for a binary read.
 */
@SuppressWarnings("javadoc")
public class BufferTest {
    private ScriptEngine engine;

    @BeforeMethod
    public void setUp() {
        engine = new NashornScriptEngineBuilder().build();
    }

    /** Evaluates a module that imports Buffer and exports {@code r = <expr>}; returns r. */
    private Object r(final String expr) throws Exception {
        final Object ns = engine.eval("import { Buffer } from 'buffer';\nexport const r = (" + expr + ");\n");
        return ((JSObject)ns).getMember("r");
    }

    @Test
    public void subclassOfUint8Array() throws Exception {
        assertEquals(r("typeof Buffer"), "function");
        assertEquals(r("Buffer.from('abc') instanceof Uint8Array"), Boolean.TRUE);
        assertEquals(r("Buffer.isBuffer(Buffer.from('abc'))"), Boolean.TRUE);
        assertEquals(r("Buffer.isBuffer(new Uint8Array(3))"), Boolean.FALSE);
        assertEquals(r("Buffer.from('abc').length"), 3);
        assertEquals(r("Buffer.from('abc')[0]"), 97);
    }

    @Test
    public void encodings() throws Exception {
        assertEquals(r("Buffer.from('hello').toString('hex')"), "68656c6c6f");
        assertEquals(r("Buffer.from('68656c6c6f', 'hex').toString('utf8')"), "hello");
        assertEquals(r("Buffer.from('hello world').toString('base64')"), "aGVsbG8gd29ybGQ=");
        assertEquals(r("Buffer.from('aGVsbG8gd29ybGQ=', 'base64').toString()"), "hello world");
        assertEquals(r("Buffer.from('café – 😀').toString('utf8')"), "café – 😀");
        assertEquals(r("Buffer.byteLength('café')"), 5);
        assertEquals(r("Buffer.from('AB', 'latin1').toString('latin1')"), "AB");
    }

    @Test
    public void numericAccessors() throws Exception {
        assertEquals(r("(function(){ var b = Buffer.alloc(4); b.writeUInt32BE(0x01020304, 0); return b.toString('hex'); })()"), "01020304");
        assertEquals(r("(function(){ var b = Buffer.alloc(4); b.writeUInt32LE(0x01020304, 0); return b.toString('hex'); })()"), "04030201");
        assertEquals(((Number)r("Buffer.from([0,1]).readUInt16BE(0)")).intValue(), 1);
        assertEquals(((Number)r("Buffer.from([1,0]).readUInt16LE(0)")).intValue(), 1);
        assertEquals(r("(function(){ var b = Buffer.alloc(8); b.writeDoubleLE(1.5, 0); return b.readDoubleLE(0); })()"), 1.5);
    }

    @Test
    public void sliceCopyConcatFillEquals() throws Exception {
        assertEquals(r("Buffer.concat([Buffer.from('foo'), Buffer.from('bar')]).toString()"), "foobar");
        assertEquals(r("Buffer.from('hello').slice(1, 3).toString()"), "el");
        assertEquals(r("Buffer.alloc(3, 0x61).toString()"), "aaa");
        assertEquals(r("Buffer.from('abc').equals(Buffer.from('abc'))"), Boolean.TRUE);
        assertEquals(r("(function(){ var t = Buffer.alloc(5); Buffer.from('hi').copy(t, 1); return t.toString('latin1', 1, 3); })()"), "hi");
        assertEquals(((Number)r("Buffer.from('hello').indexOf('ll')")).intValue(), 2);
    }

    @Test
    public void fsBinaryReadReturnsAUint8Array() throws Exception {
        final Path dir = Files.createTempDirectory("bufread");
        engine.put("DIR", dir.toString().replace("\\", "/"));
        final Object ns = engine.eval("import fs from 'fs';\nimport { Buffer } from 'buffer';\n"
           + "fs.writeFileSync(DIR + '/b.bin', Buffer.from([104, 105]));\n"       // 'hi'
           + "var bytes = fs.readFileSync(DIR + '/b.bin');\n"                      // no encoding -> Uint8Array
           + "export const isU8 = bytes instanceof Uint8Array;\n"
           + "export const len = bytes.length;\n"
           + "export const hex = Buffer.from(bytes).toString('hex');\n");         // upgrade to Buffer
        assertEquals(((JSObject)ns).getMember("isU8"), Boolean.TRUE);
        assertEquals(((JSObject)ns).getMember("len"), 2);
        assertEquals(((JSObject)ns).getMember("hex"), "6869");
    }
}
