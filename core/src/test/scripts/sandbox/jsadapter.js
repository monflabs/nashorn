/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * Test that sandbox code can access jsadapter
 *
 * @test
 * @run
 * @security
 */

var mgr = new javax.script.ScriptEngineManager();
// JSAdapter is a nashorn-library global since 2026.1.0
var Builder = Java.type('org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder');
var NashornLibrary = Java.type('org.monflabs.nashorn.libs.NashornLibrary');
var engine = new Builder().library(new NashornLibrary()).build();
engine.eval("var v = new JSAdapter() {};");
