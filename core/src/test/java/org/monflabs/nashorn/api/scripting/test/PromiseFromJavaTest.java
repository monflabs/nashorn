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

import static org.testng.Assert.assertEquals;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * Java code makes a promise the way a script does: new Promise(executor),
 * with an executor implemented in Java.
 */
public class PromiseFromJavaTest {

    private static JSObject function(final java.util.function.BiFunction<Object, Object[], Object> body) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return body.apply(thiz, args);
            }
        };
    }

    @Test
    public void aJavaExecutorIsCallable() throws ScriptException {
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine();
        final Object[] settlers = new Object[2];
        final JSObject promiseCtor = (JSObject)e.eval("Promise");
        final Object promise = promiseCtor.newObject(function((thiz, args) -> {
            settlers[0] = args[0];
            settlers[1] = args[1];
            return null;
        }));
        e.put("p", promise);
        e.eval("var got = 'pending'; p.then(function (v) { got = 'resolved ' + v; });");
        assertEquals(e.eval("got"), "pending");
        ((JSObject)settlers[0]).call(null, "from Java");
        e.eval("0");   // a turn of the loop
        assertEquals(e.eval("got"), "resolved from Java");
        // and from script, with a Java executor handed in
        e.put("executor", function((thiz, args) -> {
            ((JSObject)args[1]).call(null, "no");
            return null;
        }));
        e.eval("var r; new Promise(executor).catch(function (x) { r = 'rejected ' + x; });");
        assertEquals(e.eval("r"), "rejected no");
        assertEquals(e.eval("try { new Promise({}); } catch (x) { x.name }"), "TypeError");
    }
}
