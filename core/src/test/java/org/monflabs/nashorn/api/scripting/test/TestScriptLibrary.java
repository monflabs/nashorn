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

package org.monflabs.nashorn.api.scripting.test;

import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;

/**
 * A library registered as a service on the test class path, so that every
 * engine the tests create discovers it.
 */
public class TestScriptLibrary implements ScriptLibrary {
    /** How many globals the library has been installed into. */
    public static volatile int installations;

    @Override
    public String name() {
        return "testlib";
    }

    @Override
    public Map<String, Object> globals() {
        return Map.of("testlibVersion", "1.0");
    }

    @Override
    public List<Script> scripts() {
        installations++;
        return List.of(Script.of("testlib.js", "function testlibGreet(who) { return 'hello ' + who + ' from testlib ' + testlibVersion; }"));
    }
}
