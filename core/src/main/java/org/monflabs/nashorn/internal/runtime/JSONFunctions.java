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

package org.monflabs.nashorn.internal.runtime;

import java.lang.invoke.MethodHandle;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.objects.NativeArray;
import org.monflabs.nashorn.internal.parser.JSONParser;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayIndex;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities used by "JSON" object implementation.
 */
public final class JSONFunctions {
    private JSONFunctions() {}

    private static final Object REVIVER_INVOKER = new Object();

    private static MethodHandle getREVIVER_INVOKER() {
        return Context.getGlobal().getDynamicInvoker(REVIVER_INVOKER, () -> Bootstrap.createDynamicCallInvoker(Object.class,
            Object.class, Object.class, String.class, Object.class));
    }

    /**
     * Returns JSON-compatible quoted version of the given string.
     *
     * @param str String to be quoted
     * @return JSON-compatible quoted string
     */
    public static String quote(final String str) {
        return JSONParser.quote(str);
    }

    /**
     * Parses the given JSON text string and returns object representation.
     *
     * @param text JSON text to be parsed
     * @param reviver  optional value: function that takes two parameters (key, value)
     * @return Object representation of JSON text given
     */
    public static Object parse(final Object text, final Object reviver) {
        final String     str    = JSType.toString(text);
        final Global     global = Context.getGlobal();
        final boolean    dualFields = ((ScriptObject) global).useDualFields();
        final JSONParser parser = new JSONParser(str, global, dualFields);
        final Object     value;

        try {
            value = parser.parse();
        } catch (final ParserException e) {
            throw ECMAErrors.syntaxError(e, "invalid.json", e.getMessage());
        }

        return applyReviver(global, value, reviver);
    }

    // -- Internals only below this point

    // parse helpers

    // apply 'reviver' function if available
    private static Object applyReviver(final Global global, final Object unfiltered, final Object reviver) {
        if (Bootstrap.isCallable(reviver)) {
            final ScriptObject root = global.newObject();
            root.addOwnProperty("", Property.WRITABLE_ENUMERABLE_CONFIGURABLE, unfiltered);
            return walk(root, "", reviver);
        }
        return unfiltered;
    }

    // This is the abstract "Walk" operation from the spec.
    private static Object walk(final ScriptObject holder, final Object name, final Object reviver) {
        final Object val = holder.get(name);
        if (val instanceof ScriptObject valueObj) {
            // 24.3.1.1 asks IsArray, which sees through however many proxies
            // stand in the way, and reads the length as an ordinary property
            if (NativeArray.isArray(null, valueObj)) {
                final long length = JSType.toUint32(valueObj.get("length"));
                for (long i = 0; i < length; i++) {
                    final String key = Long.toString(i);
                    final Object newElement = walk(valueObj, key, reviver);

                    if (newElement == ScriptRuntime.UNDEFINED) {
                        valueObj.delete(key, false);
                    } else {
                        createDataProperty(valueObj, key, newElement);
                    }
                }
            } else {
                final String[] keys = valueObj.getOwnKeys(false);
                for (final String key : keys) {
                    final Object newElement = walk(valueObj, key, reviver);

                    if (newElement == ScriptRuntime.UNDEFINED) {
                        valueObj.delete(key, false);
                    } else {
                        createDataProperty(valueObj, key, newElement);
                    }
                }
            }
        }

        try {
             // Object.class, ScriptFunction.class, ScriptObject.class, String.class, Object.class);
             return getREVIVER_INVOKER().invokeExact(reviver, (Object)holder, JSType.toString(name), val);
        } catch(Error|RuntimeException t) {
            throw t;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * ES2017 24.3.1.1 step 2: what the reviver answered is written with
     * CreateDataProperty, whose failure is not an error - a property the object
     * will not redefine is simply left as it was - while an object that refuses
     * by throwing still throws.
     */
    private static void createDataProperty(final ScriptObject sobj, final String name, final Object value) {
        final ScriptObject descriptor = Global.newEmptyInstance();
        descriptor.set(PropertyDescriptor.VALUE, value, 0);
        descriptor.set(PropertyDescriptor.WRITABLE, true, 0);
        descriptor.set(PropertyDescriptor.ENUMERABLE, true, 0);
        descriptor.set(PropertyDescriptor.CONFIGURABLE, true, 0);
        sobj.defineOwnProperty(name, descriptor, false);
    }

}
