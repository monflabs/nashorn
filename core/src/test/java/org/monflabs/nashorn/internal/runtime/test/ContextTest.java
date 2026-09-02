/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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

package org.monflabs.nashorn.internal.runtime.test;

import static org.monflabs.nashorn.internal.runtime.Source.sourceFor;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import java.util.Map;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ErrorManager;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Source;
import org.monflabs.nashorn.internal.runtime.options.Options;
import org.testng.annotations.Test;

/**
 * Basic Context API tests.
 *
 * @test
 * @modules org.monflabs.nashorn/org.monflabs.nashorn.internal.runtime
 *          org.monflabs.nashorn/org.monflabs.nashorn.internal.runtime.options
 *          org.monflabs.nashorn/org.monflabs.nashorn.internal.objects
 * @run testng org.monflabs.nashorn.internal.runtime.test.ContextTest
 */
@SuppressWarnings("javadoc")
public class ContextTest {
    // basic context eval test
    @Test
    public void evalTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors, Thread.currentThread().getContextClassLoader());
        Context.runWithGlobal(cx.createGlobal(), () -> {
            String code = "22 + 10";
            assertTrue(32.0 == ((Number)(eval(cx, "<evalTest>", code))).doubleValue());

            code = "obj = { js: 'nashorn' }; obj.js";
            assertEquals(eval(cx, "<evalTest2>", code), "nashorn");
        });
    }

    // Make sure trying to compile an invalid script returns null - see JDK-8046215.
    @Test
    public void compileErrorTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors, Thread.currentThread().getContextClassLoader());
        Context.runWithGlobal(cx.createGlobal(), () -> {
            final ScriptFunction script = cx.compileScript(sourceFor("<evalCompileErrorTest>", "*/"), Context.getGlobal());
            if (script != null) {
                fail("Invalid script compiled without errors");
            }
            if (errors.getNumberOfErrors() != 1) {
                fail("Wrong number of errors: " + errors.getNumberOfErrors());
            }
        });
    }

    // basic check for JS reflection access - java.util.Map-like access on ScriptObject
    @Test
    public void reflectionTest() {
        final Options options = new Options("");
        final ErrorManager errors = new ErrorManager();
        final Context cx = new Context(options, errors, Thread.currentThread().getContextClassLoader());
        final boolean strict = cx.getEnv()._strict;
        Context.runWithGlobal(cx.createGlobal(), () -> {
            final String code = "var obj = { x: 344, y: 42 }";
            eval(cx, "<reflectionTest>", code);

            final Object obj = Context.getGlobal().get("obj");

            assertTrue(obj instanceof ScriptObject);

            final ScriptObject sobj = (ScriptObject)obj;
            int count = 0;
            for (final Map.Entry<?, ?> ex : sobj.entrySet()) {
                final Object key = ex.getKey();
                if (key.equals("x")) {
                    assertTrue(ex.getValue() instanceof Number);
                    assertTrue(344.0 == ((Number)ex.getValue()).doubleValue());

                    count++;
                } else if (key.equals("y")) {
                    assertTrue(ex.getValue() instanceof Number);
                    assertTrue(42.0 == ((Number)ex.getValue()).doubleValue());

                    count++;
                }
            }
            assertEquals(count, 2);
            assertEquals(sobj.size(), 2);

            // add property
            sobj.put("zee", "hello", strict);
            assertEquals(sobj.get("zee"), "hello");
            assertEquals(sobj.size(), 3);
        });
    }

    private static Object eval(final Context cx, final String name, final String code) {
        final Source source = sourceFor(name, code);
        final ScriptObject global = Context.getGlobal();
        final ScriptFunction func = cx.compileScript(source, global);
        return func != null ? ScriptRuntime.apply(func, global) : null;
    }
}
