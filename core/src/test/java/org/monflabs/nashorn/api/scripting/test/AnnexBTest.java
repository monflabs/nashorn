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
package org.monflabs.nashorn.api.scripting.test;

import static org.testng.Assert.assertEquals;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * ECMA-262 Annex B is per engine, and two engines that disagree about it share a
 * process without contaminating one another.
 *
 * The built-ins Annex B adds are written into the property maps nasgen builds,
 * so an engine without them starts from the shared shape and takes them away
 * again. That is a shape divergence between two globals of one process, and the
 * question a script cannot ask is whether the two stay apart.
 */
@SuppressWarnings({"javadoc", "deprecation"})   // the factory overloads stay tested for compatibility
public class AnnexBTest {

    private static ScriptEngine engine(final String... options) {
        return new NashornScriptEngineFactory().getScriptEngine(options);
    }

    @Test
    public void defaultEngineHasAnnexB() throws ScriptException {
        final ScriptEngine e = engine();
        assertEquals(e.eval("typeof escape"), "function");
        assertEquals(e.eval("typeof ''.anchor"), "function");
        assertEquals(e.eval("(function () { { function f() {} } return typeof f; })()"), "function");
    }

    @Test
    public void engineWithoutAnnexBHasNoneOfIt() throws ScriptException {
        final ScriptEngine e = engine("--annexB=false");
        assertEquals(e.eval("typeof escape"), "undefined");
        assertEquals(e.eval("typeof ''.anchor"), "undefined");
        assertEquals(e.eval("typeof Date.prototype.getYear"), "undefined");
        assertEquals(e.eval("typeof RegExp.prototype.compile"), "undefined");
        assertEquals(e.eval("Object.prototype.hasOwnProperty('__proto__')"), Boolean.FALSE);
        assertEquals(e.eval("(function () { { function f() {} } return typeof f; })()"), "undefined");
    }

    @Test
    public void twoEnginesDoNotContaminateEachOther() throws ScriptException {
        final ScriptEngine with = engine();
        final ScriptEngine without = engine("--annexB=false");

        // interleaved, and each asked twice: a shape or a switch point shared
        // between the two would show up on the second pass
        for (int i = 0; i < 2; i++) {
            assertEquals(with.eval("typeof escape"), "function");
            assertEquals(without.eval("typeof escape"), "undefined");
            assertEquals(with.eval("typeof ''.blink"), "function");
            assertEquals(without.eval("typeof ''.blink"), "undefined");
            assertEquals(with.eval("typeof Date.prototype.toGMTString"), "function");
            assertEquals(without.eval("typeof Date.prototype.toGMTString"), "undefined");
        }
    }

    @Test
    public void theEngineWithAnnexBIsUnhurtByOneWithout() throws ScriptException {
        // the global without Annex B is built first, so that the maps it derives
        // are the ones already in hand when the other is built
        final ScriptEngine without = engine("--annexB=false");
        assertEquals(without.eval("typeof unescape"), "undefined");

        final ScriptEngine with = engine();
        assertEquals(with.eval("typeof unescape"), "function");
        assertEquals(with.eval("unescape(escape('a b'))"), "a b");
    }
}
