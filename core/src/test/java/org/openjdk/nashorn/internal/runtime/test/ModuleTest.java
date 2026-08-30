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

package org.openjdk.nashorn.internal.runtime.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import java.io.File;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ErrorManager;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.ModuleRecord;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Source;
import org.openjdk.nashorn.internal.runtime.options.Options;
import org.testng.annotations.Test;

/**
 * ECMAScript 2015 modules.
 *
 * @test
 * @modules org.openjdk.nashorn/org.openjdk.nashorn.internal.runtime
 *          org.openjdk.nashorn/org.openjdk.nashorn.internal.runtime.options
 *          org.openjdk.nashorn/org.openjdk.nashorn.internal.objects
 * @run testng org.openjdk.nashorn.internal.runtime.test.ModuleTest
 */
@SuppressWarnings("javadoc")
public class ModuleTest {
    private static final String MODULES = "src/test/scripts/modules/";

    private static Context newContext() {
        final Options options = new Options("nashorn");
        options.process(new String[0]);
        return new Context(options, new ErrorManager(),
                Thread.currentThread().getContextClassLoader());
    }

    private static ModuleRecord evaluate(final Context context, final String name) throws Exception {
        return context.evaluateModule(Source.sourceFor(MODULES + name, new File(MODULES + name)));
    }

    @Test
    public void importsAndExports() throws Exception {
        final Context context = newContext();
        final Global global = context.createGlobal();
        Context.runWithGlobal(global, () -> {
            final ModuleRecord main = evaluate(context, "main.js");
            final ScriptObject result = (ScriptObject)main.read("result");

            assertEquals(JSType.toString(result.get(0)), "default=the default");
            assertEquals(JSType.toString(result.get(1)), "NAME=counter");
            assertEquals(JSType.toString(result.get(2)), "renamed=counter");
            assertEquals(JSType.toString(result.get(3)), "before=0");
            // the binding is the exporting module's own, so both bumps are seen
            assertEquals(JSType.toString(result.get(4)), "after=2");
            assertEquals(JSType.toString(result.get(5)), "ns=NAME|bump|counter|default");
            assertEquals(JSType.toString(result.get(6)), "shared=true");
        });
    }

    @Test
    public void moduleBindingsAreNotGlobal() throws Exception {
        final Context context = newContext();
        final Global global = context.createGlobal();
        Context.runWithGlobal(global, () -> {
            evaluate(context, "main.js");
            // a module's top level declarations belong to the module
            assertSame(global.get("counter"), ScriptRuntime.UNDEFINED);
            assertSame(global.get("result"), ScriptRuntime.UNDEFINED);
        });
    }

    @Test
    public void oneRecordPerModule() throws Exception {
        final Context context = newContext();
        final Global global = context.createGlobal();
        Context.runWithGlobal(global, () -> {
            final ModuleRecord first = evaluate(context, "counter.js");
            final ModuleRecord again = evaluate(context, "counter.js");
            assertSame(first, again, "a module is loaded once per realm");

            final ModuleRecord other = evaluate(context, "reexport.js");
            assertNotSame(first, other);
        });
    }
}
