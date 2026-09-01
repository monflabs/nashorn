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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornException;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The language's abstract operations on ScriptUtils, against the language's
 * own tables - with the values a script hands a Java function: primitives,
 * null, undefined, mirrors.
 */
public class ScriptUtilsTest {
    private ScriptEngine engine;
    private Object undefined;
    private Object valueOf3;
    private Object array2;
    private Object date;
    private Object symbol;
    private Object consString;
    private Object function;

    @BeforeClass
    public void values() throws ScriptException {
        engine = new NashornScriptEngineFactory().getScriptEngine();
        undefined = ScriptUtils.undefined();
        valueOf3 = engine.eval("({ valueOf: function () { return 3; }, toString: function () { return 'three'; } })");
        array2 = engine.eval("[2]");
        date = engine.eval("new Date(0)");
        symbol = engine.eval("Symbol('s')");
        consString = engine.eval("var left = 'ab'; left + 'cd' + left.length");
        function = engine.eval("(function () {})");
    }

    @Test
    public void typeOfAnswersLikeTheOperator() {
        assertEquals(ScriptUtils.typeOf(undefined), "undefined");
        assertEquals(ScriptUtils.typeOf(null), "object");
        assertEquals(ScriptUtils.typeOf(true), "boolean");
        assertEquals(ScriptUtils.typeOf(1), "number");
        assertEquals(ScriptUtils.typeOf(1.5), "number");
        assertEquals(ScriptUtils.typeOf("s"), "string");
        assertEquals(ScriptUtils.typeOf(consString), "string");
        assertEquals(ScriptUtils.typeOf(symbol), "symbol");
        assertEquals(ScriptUtils.typeOf(valueOf3), "object");
        assertEquals(ScriptUtils.typeOf(array2), "object");
        assertEquals(ScriptUtils.typeOf(function), "function");
    }

    @Test
    public void theTypeTests() {
        assertTrue(ScriptUtils.isUndefined(undefined));
        assertFalse(ScriptUtils.isUndefined(null));
        assertTrue(ScriptUtils.isNullOrUndefined(null));
        assertTrue(ScriptUtils.isNullOrUndefined(undefined));
        assertFalse(ScriptUtils.isNullOrUndefined(0));
        assertTrue(ScriptUtils.isString("s"));
        assertTrue(ScriptUtils.isString(consString));
        final Object cons = new org.monflabs.nashorn.internal.runtime.ConsString("ab", "cd");   // what concatenation makes inside the engine
        assertFalse(cons instanceof String);
        assertTrue(ScriptUtils.isString(cons));
        assertEquals(ScriptUtils.toString(cons), "abcd");
        assertEquals(ScriptUtils.typeOf(cons), "string");
        assertFalse(ScriptUtils.isString(new StringBuilder("a CharSequence is not a script string")));
        assertFalse(ScriptUtils.isString(1));
        assertTrue(ScriptUtils.isNumber(1));
        assertTrue(ScriptUtils.isNumber(1.5));
        assertFalse(ScriptUtils.isNumber(1L), "a Java long is a Java object to a script");
        assertEquals(ScriptUtils.toNumber(1L), 1.0);
        assertFalse(ScriptUtils.isNumber("1"));
        assertTrue(ScriptUtils.isPrimitive(undefined));
        assertTrue(ScriptUtils.isPrimitive(null));
        assertTrue(ScriptUtils.isPrimitive("s"));
        assertTrue(ScriptUtils.isPrimitive(symbol));
        assertFalse(ScriptUtils.isPrimitive(valueOf3));
        assertFalse(ScriptUtils.isPrimitive(function));
        assertTrue(ScriptUtils.isCallable(function));
        assertFalse(ScriptUtils.isCallable(valueOf3));
        assertFalse(ScriptUtils.isCallable("s"));
        assertTrue(ScriptUtils.isCallable(new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }
        }));
        assertSame(ScriptUtils.undefined(), ScriptUtils.undefined());
    }

    @Test
    public void toNumberFollowsTheTable() {
        assertEquals(ScriptUtils.toNumber(2), 2.0);
        assertEquals(ScriptUtils.toNumber(2.5), 2.5);
        assertEquals(ScriptUtils.toNumber("2"), 2.0);
        assertEquals(ScriptUtils.toNumber("  0x10 "), 16.0);
        assertEquals(ScriptUtils.toNumber(""), 0.0);
        assertTrue(Double.isNaN(ScriptUtils.toNumber("abc")));
        assertEquals(ScriptUtils.toNumber(true), 1.0);
        assertEquals(ScriptUtils.toNumber(false), 0.0);
        assertEquals(ScriptUtils.toNumber(null), 0.0);
        assertTrue(Double.isNaN(ScriptUtils.toNumber(undefined)));
        assertEquals(ScriptUtils.toNumber(valueOf3), 3.0);
        assertEquals(ScriptUtils.toNumber(array2), 2.0);
        assertEquals(ScriptUtils.toNumber(date), 0.0);
        assertEquals(ScriptUtils.toNumber(consString), Double.NaN);
    }

    @Test
    public void theIntegerConversions() {
        assertEquals(ScriptUtils.toInt32(3.9), 3);
        assertEquals(ScriptUtils.toInt32(-3.9), -3);
        assertEquals(ScriptUtils.toInt32(4294967296.0 + 5), 5);
        assertEquals(ScriptUtils.toInt32(2147483648.0), -2147483648);
        assertEquals(ScriptUtils.toInt32(Double.NaN), 0);
        assertEquals(ScriptUtils.toInt32(Double.POSITIVE_INFINITY), 0);
        assertEquals(ScriptUtils.toInt32("12"), 12);
        assertEquals(ScriptUtils.toUint32(-1), 4294967295L);
        assertEquals(ScriptUtils.toUint32("7"), 7L);
        assertEquals(ScriptUtils.toUint16(65536 + 3), 3);
        assertEquals(ScriptUtils.toUint16(-1), 65535);
        assertEquals(ScriptUtils.toLong(3.9), 3L);
        assertEquals(ScriptUtils.toLong("-2.5"), -2L);
        assertEquals(ScriptUtils.toLong(Double.NaN), 0L);
        assertEquals(ScriptUtils.toLong(1e30), Long.MAX_VALUE);
        assertEquals(ScriptUtils.toLong(valueOf3), 3L);
    }

    @Test
    public void toBooleanIsTruthiness() {
        for (final Object falsy : new Object[] { false, 0, -0.0, Double.NaN, "", null, undefined }) {
            assertFalse(ScriptUtils.toBoolean(falsy), String.valueOf(falsy));
        }
        for (final Object truthy : new Object[] { true, 1, -1, "0", "false", " ", valueOf3, array2, function, consString }) {
            assertTrue(ScriptUtils.toBoolean(truthy), String.valueOf(truthy));
        }
    }

    @Test
    public void toStringIsTheLanguages() {
        assertEquals(ScriptUtils.toString(undefined), "undefined");
        assertEquals(ScriptUtils.toString(null), "null");
        assertEquals(ScriptUtils.toString(true), "true");
        assertEquals(ScriptUtils.toString(1), "1");
        assertEquals(ScriptUtils.toString(1.0), "1");
        assertEquals(ScriptUtils.toString(1.5), "1.5");
        assertEquals(ScriptUtils.toString(1e21), "1e+21");
        assertEquals(ScriptUtils.toString(consString), "abcd2");
        assertEquals(ScriptUtils.toString(valueOf3), "three");
        assertEquals(ScriptUtils.toString(array2), "2");
    }

    @Test
    public void theTypeErrorCasesNeedTheCallersRealm() throws ScriptException {
        // a symbol converts to neither a number nor a string, and null is not object-coercible:
        // each is a TypeError, which the engine makes in the current realm - so from where a script called us
        final JSObject probe = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                final String op = ScriptUtils.toString(args[0]);
                switch (op) {
                case "number": return ScriptUtils.toNumber(args[1]);
                case "string": return ScriptUtils.toString(args[1]);
                default: return ScriptUtils.requireObjectCoercible(args[1]);
                }
            }
        };
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine(ScriptLibrary.of("p", Map.of("probe", probe)));
        assertEquals(e.eval("try { probe('number', Symbol('s')); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("try { probe('string', Symbol('s')); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("try { probe('coercible', null); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("try { probe('coercible', undefined); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("probe('coercible', 'ok')"), "ok");
        // with no realm bound, the same calls cannot make the error: they say so rather than failing obscurely
        try {
            ScriptUtils.requireObjectCoercible(null);
            fail("expected a failure");
        } catch (final IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("realm"), expected.getMessage());
        }
    }

    @Test
    public void toPrimitiveHonoursTheHint() {
        assertEquals(ScriptUtils.toPrimitive(valueOf3), 3);
        assertEquals(ScriptUtils.toPrimitive(valueOf3, Number.class), 3);
        assertEquals(ScriptUtils.toPrimitive(valueOf3, String.class), "three");
        assertEquals(ScriptUtils.toPrimitive("s"), "s");
        assertEquals(ScriptUtils.toPrimitive(null), null);
        assertSame(ScriptUtils.toPrimitive(undefined), undefined);
        // a Date prefers a string with no hint
        assertTrue(ScriptUtils.toPrimitive(date) instanceof CharSequence);
        assertEquals(((Number)ScriptUtils.toPrimitive(date, Number.class)).doubleValue(), 0.0);
    }

    @Test
    public void toObjectWrapsAPrimitiveInTheCallersRealm() throws ScriptException {
        // from where a script called us, so that the wrapper has a realm
        final JSObject probe = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                final JSObject object = ScriptUtils.toObject(args[0]);
                return ScriptUtils.typeOf(object) + ":" + object.getMember("length") + ":" + ((JSObject)object.getMember("toUpperCase")).call(object);
            }
        };
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine(ScriptLibrary.of("p", Map.of("probe", probe)));
        assertEquals(e.eval("probe('abc')"), "object:3:ABC");
        assertEquals(e.eval("typeof probe('abc')"), "string");
        assertEquals(e.eval("try { probe(null); } catch (x) { x.name }"), "TypeError");
        // an object is itself
        final JSObject wrapped = ScriptUtils.toObject(valueOf3);
        assertSame(wrapped, valueOf3);
    }

    @Test
    public void theEqualities() throws ScriptException {
        assertTrue(ScriptUtils.strictEquals(1, 1.0));
        assertFalse(ScriptUtils.strictEquals(1, "1"));
        assertFalse(ScriptUtils.strictEquals(Double.NaN, Double.NaN));
        assertTrue(ScriptUtils.strictEquals(0.0, -0.0));
        assertFalse(ScriptUtils.strictEquals(null, undefined));
        assertTrue(ScriptUtils.strictEquals("ab" + "cd2", consString));
        assertTrue(ScriptUtils.looseEquals(1, "1"));
        assertTrue(ScriptUtils.looseEquals(null, undefined));
        assertTrue(ScriptUtils.looseEquals(valueOf3, 3));
        assertTrue(ScriptUtils.looseEquals(array2, "2"));
        assertFalse(ScriptUtils.looseEquals(0, null));
        assertTrue(ScriptUtils.sameValue(Double.NaN, Double.NaN));
        assertFalse(ScriptUtils.sameValue(0.0, -0.0));
        assertTrue(ScriptUtils.sameValueZero(0.0, -0.0));
        assertTrue(ScriptUtils.sameValueZero(Double.NaN, Double.NaN));
        // objects by identity, mirrors of one object being one object
        final Object again = engine.eval("left"); // a different value
        assertFalse(ScriptUtils.strictEquals(valueOf3, again));
        engine.put("o", valueOf3);
        final Object sameObject = engine.eval("o");
        assertNotSame(sameObject, valueOf3, "a mirror is minted per crossing");
        assertTrue(ScriptUtils.strictEquals(valueOf3, sameObject));
        assertTrue(ScriptUtils.sameValue(valueOf3, sameObject));
        assertTrue(ScriptUtils.looseEquals(valueOf3, sameObject));
        assertFalse(ScriptUtils.strictEquals(valueOf3, array2));
    }

    @Test
    public void requireObjectCoercibleAndTheErrors() throws ScriptException {
        assertEquals(ScriptUtils.requireObjectCoercible("s"), "s");
        assertEquals(ScriptUtils.requireObjectCoercible(0), 0);
        // errors made from Java reach the script as its own
        final JSObject checked = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                final double r = ScriptUtils.toNumber(args.length == 0 ? ScriptUtils.undefined() : args[0]);
                if (Double.isNaN(r)) {
                    throw ScriptUtils.typeError("radius must be a number");
                }
                if (r < 0) {
                    throw ScriptUtils.rangeError("radius must not be negative: " + ScriptUtils.toString(args[0]));
                }
                return Math.PI * r * r;
            }
        };
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine(ScriptLibrary.of("p", Map.of("area", checked)));
        assertEquals(e.eval("area(1)"), Math.PI);
        assertEquals(e.eval("try { area('x'); } catch (x) { x instanceof TypeError ? x.message : 'not a TypeError' }"), "radius must be a number");
        assertEquals(e.eval("try { area(-1); } catch (x) { x instanceof RangeError ? x.message : 'not a RangeError' }"), "radius must not be negative: -1");
        assertEquals(e.eval("try { area(); } catch (x) { x.name }"), "TypeError");
    }

    @Test
    public void objectKeysOfAJSObjectAreItsKeySet() throws ScriptException {
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine();
        e.put("bag", new AbstractJSObject() {
            @Override
            public java.util.Set<String> keySet() {
                return new java.util.LinkedHashSet<>(java.util.List.of("b", "a"));
            }

            @Override
            public Object getMember(final String name) {
                return name.length();
            }
        });
        assertEquals(e.eval("Object.keys(bag).join()"), "b,a");
    }

    @Test
    public void mirrorsConvertWithoutARealmBound() throws InterruptedException {
        final double[] result = new double[1];
        final String[] text = new String[1];
        final Thread t = new Thread(() -> {
            result[0] = ScriptUtils.toNumber(valueOf3);
            text[0] = ScriptUtils.toString(array2);
        });
        t.start();
        t.join();
        assertEquals(result[0], 3.0);
        assertEquals(text[0], "2");
    }

    @Test
    public void mirrorsAreWhatAJavaFunctionReceives() throws ScriptException {
        final Class<?>[] seen = new Class<?>[1];
        final JSObject probe = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                seen[0] = args[0].getClass();
                return ScriptUtils.toNumber(args[0]);
            }
        };
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine(ScriptLibrary.of("p", Map.of("probe", probe)));
        assertEquals(e.eval("probe({ valueOf: function () { return 9; } })"), 9.0);
        assertEquals(seen[0], ScriptObjectMirror.class);
    }
}
