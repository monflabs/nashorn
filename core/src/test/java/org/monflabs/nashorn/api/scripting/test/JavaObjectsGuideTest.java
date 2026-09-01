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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The examples of the guide page "Objects from Java", run as written there:
 * the page's claims about what a script sees are these assertions.
 */
@SuppressWarnings("deprecation")   // the factory overloads stay tested for compatibility
public class JavaObjectsGuideTest {

    /** A value object: data properties, a computed one, a write that validates, conversions. */
    static final class Point extends AbstractJSObject {
        double x, y;

        Point(final double x, final double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Object getMember(final String name) {
            switch (name) {
            case "x": return x;
            case "y": return y;
            case "length": return Math.hypot(x, y);
            default: return ScriptUtils.undefined();
            }
        }

        @Override
        public void setMember(final String name, final Object value) {
            switch (name) {
            case "x": x = ScriptUtils.toNumber(value); break;
            case "y": y = ScriptUtils.toNumber(value); break;
            default: throw ScriptUtils.typeError("Point has no property " + name);
            }
        }

        @Override
        public boolean hasMember(final String name) {
            return Set.of("x", "y", "length").contains(name);
        }

        @Override
        public Set<String> keySet() {
            return new LinkedHashSet<>(List.of("x", "y"));
        }

        @Override
        public String getClassName() {
            return "Point";
        }

        @Override
        public Object getDefaultValue(final Class<?> hint) {
            return hint == Number.class ? Math.hypot(x, y) : "Point(" + x + ", " + y + ")";
        }
    }

    /** A function: coerce as the language would, refuse with a script error. */
    static final class Area extends AbstractJSObject {
        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            final double r = ScriptUtils.toNumber(args.length == 0 ? ScriptUtils.undefined() : args[0]);
            if (r < 0) {
                throw ScriptUtils.rangeError("radius must not be negative: " + ScriptUtils.toString(args[0]));
            }
            return Math.PI * r * r;
        }
    }

    /** A class: a constructor answering new and instanceof, methods shared by the instances. */
    static final class Counter extends AbstractJSObject {
        static final JSObject INCREMENT = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                if (!(thiz instanceof Counter self)) {
                    throw ScriptUtils.typeError("increment called on a non-Counter");
                }
                self.count += args.length == 0 ? 1 : ScriptUtils.toInt32(args[0]);
                return self.count;
            }
        };
        int count;

        @Override
        public Object getMember(final String name) {
            switch (name) {
            case "count": return count;
            case "increment": return INCREMENT;
            default: return ScriptUtils.undefined();
            }
        }

        @Override
        public String getClassName() {
            return "Counter";
        }

        static final JSObject CONSTRUCTOR = new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object newObject(final Object... args) {
                final Counter counter = new Counter();
                counter.count = args.length == 0 ? 0 : ScriptUtils.toInt32(args[0]);
                return counter;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                throw ScriptUtils.typeError("Counter requires 'new'");
            }

            @Override
            public boolean isInstance(final Object instance) {
                return instance instanceof Counter;
            }

            @Override
            public Object getMember(final String name) {
                return "name".equals(name) ? "Counter" : super.getMember(name);
            }
        };
    }

    /** A catch-all: answers any name. */
    static final class Config extends AbstractJSObject {
        final Map<String, String> values;

        Config(final Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Object getMember(final String name) {
            return values.getOrDefault(name, "<" + name + " is not set>");
        }

        @Override
        public boolean hasMember(final String name) {
            return values.containsKey(name);
        }

        @Override
        public Set<String> keySet() {
            return values.keySet();
        }
    }

    /** An array-like: slots, a length, isArray. */
    static final class Bytes extends AbstractJSObject {
        final byte[] bytes;

        Bytes(final byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean isArray() {
            return true;
        }

        @Override
        public boolean hasSlot(final int index) {
            return index >= 0 && index < bytes.length;
        }

        @Override
        public Object getSlot(final int index) {
            return hasSlot(index) ? bytes[index] & 0xFF : ScriptUtils.undefined();
        }

        @Override
        public void setSlot(final int index, final Object value) {
            if (hasSlot(index)) {
                bytes[index] = (byte)ScriptUtils.toInt32(value);
            }
        }

        @Override
        public Object getMember(final String name) {
            return "length".equals(name) ? bytes.length : ScriptUtils.undefined();
        }
    }

    private ScriptEngine engine;

    @BeforeClass
    public void engine() {
        final Map<String, String> config = new LinkedHashMap<>();
        config.put("host", "example.org");
        config.put("port", "8080");
        final ScriptLibrary examples = ScriptLibrary.of("examples", Map.of(
                "area", new Area(), "Counter", Counter.CONSTRUCTOR, "config", new Config(config), "bytes", new Bytes(new byte[] { 1, 2, (byte)255 })));
        engine = new NashornScriptEngineFactory().getScriptEngine(examples);
        engine.put("p", new Point(3, 4));
    }

    @Test
    public void aValueObject() throws ScriptException {
        assertEquals(engine.eval("p.x + ',' + p.y + ',' + p.length"), "3,4,5");
        assertEquals(engine.eval("p.x = 6; p.length"), Math.hypot(6, 4));
        assertEquals(engine.eval("['x' in p, 'z' in p, Object.keys(p).join()].join(' ')"), "true false x,y");
        assertEquals(engine.eval("String(p) + ' | ' + (+p)"), "Point(6.0, 4.0) | " + Math.hypot(6, 4));
        assertEquals(engine.eval("try { p.z = 1; } catch (e) { e.name + ': ' + e.message }"), "TypeError: Point has no property z");
    }

    @Test
    public void aFunction() throws ScriptException {
        assertEquals(engine.eval("area(2)"), Math.PI * 4);
        assertEquals(engine.eval("area('1')"), Math.PI);
        assertEquals(engine.eval("isNaN(area())"), true);
        assertEquals(engine.eval("try { area(-1); } catch (e) { e.name + ': ' + e.message }"), "RangeError: radius must not be negative: -1");
    }

    @Test
    public void aClassWithInstanceof() throws ScriptException {
        assertEquals(((Number)engine.eval("var c = new Counter(40); c.increment(); c.increment(1); c.count")).intValue(), 42);
        assertEquals(engine.eval("[c instanceof Counter, ({}) instanceof Counter, Counter.name, typeof Counter].join(' ')"), "true false Counter function");
        assertEquals(engine.eval("try { Counter(); } catch (e) { e.name }"), "TypeError");
        // and what a JSObject class does not give
        assertEquals(engine.eval("[String(Object.getPrototypeOf(c)), typeof c.increment.call === 'function', typeof c.hasOwnProperty].join(' ')"), "null false undefined");
    }

    @Test
    public void aCatchAll() throws ScriptException {
        assertEquals(engine.eval("config.host + ':' + config.port"), "example.org:8080");
        assertEquals(engine.eval("config.timeout"), "<timeout is not set>");
        assertEquals(engine.eval("Object.keys(config).join()"), "host,port");
    }

    @Test
    public void anArrayLike() throws ScriptException {
        assertEquals(engine.eval("[bytes.length, bytes[2], Array.isArray(bytes)].join(' ')"), "3 255 true");
        assertEquals(engine.eval("bytes[0] = 9; bytes[0] + ' ' + typeof bytes[5]"), "9 undefined");
        // the Array.prototype generics do not see a JSObject as array-like
        assertEquals(engine.eval("Array.prototype.join.call(bytes, '-')"), "");
    }
}
