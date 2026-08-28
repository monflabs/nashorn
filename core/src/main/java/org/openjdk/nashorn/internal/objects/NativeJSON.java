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

package org.openjdk.nashorn.internal.objects;

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openjdk.nashorn.api.scripting.JSObject;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.runtime.ConsString;
import org.openjdk.nashorn.internal.runtime.JSONFunctions;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;
import org.openjdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * ECMAScript 262 Edition 5, Section 15.12 The NativeJSON Object
 *
 */
@ScriptClass("JSON")
public final class NativeJSON extends ScriptObject {
    private static final Object TO_JSON = new Object();

    private static InvokeByName getTO_JSON() {
        return Global.instance().getInvokeByName(TO_JSON,
            () -> new InvokeByName("toJSON", ScriptObject.class, Object.class, Object.class)
        );
    }

    private static final Object JSOBJECT_INVOKER = new Object();

    private static MethodHandle getJSOBJECT_INVOKER() {
        return Global.instance().getDynamicInvoker(JSOBJECT_INVOKER,
            () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class)
        );
    }

    private static final Object REPLACER_INVOKER = new Object();

    private static MethodHandle getREPLACER_INVOKER() {
        return Global.instance().getDynamicInvoker(REPLACER_INVOKER,
            () -> Bootstrap.createDynamicCallInvoker(Object.class,Object.class, Object.class, Object.class, Object.class)
        );
    }

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeJSON() {
        // don't create me!!
        throw new UnsupportedOperationException();
    }

    /**
     * ECMA 15.12.2 parse ( text [ , reviver ] )
     *
     * @param self     self reference
     * @param text     a JSON formatted string
     * @param reviver  optional value: function that takes two parameters (key, value)
     *
     * @return an ECMA script value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object parse(final Object self, final Object text, final Object reviver) {
        return JSONFunctions.parse(text, reviver);
    }

    /**
     * ECMA 15.12.3 stringify ( value [ , replacer [ , space ] ] )
     *
     * @param self     self reference
     * @param value    ECMA script value (usually object or array)
     * @param replacer either a function or an array of strings and numbers
     * @param space    optional parameter - allows result to have whitespace injection
     *
     * @return a string in JSON format
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object stringify(final Object self, final Object value, final Object replacer, final Object space) {
        // The stringify method takes a value and an optional replacer, and an optional
        // space parameter, and returns a JSON text. The replacer can be a function
        // that can replace values, or an array of strings that will select the keys.

        // A default replacer method can be provided. Use of the space parameter can
        // produce text that is more easily readable.

        final StringifyState state = new StringifyState();

        // If there is a replacer, it must be a function or an array.
        if (Bootstrap.isCallable(replacer)) {
            state.replacerFunction = replacer;
        } else if (replacer instanceof ScriptObject list && NativeArray.isArray(null, replacer)) {
            // ES2015 24.3.2 step 4.b reads the length and then the elements, so
            // a getter on either is called and what it throws comes out here -
            // which is why this is not an ArrayLikeIterator over the object
            state.propertyList = new ArrayList<>();
            final long length = JSType.toUint32(list.get("length"));
            for (long i = 0; i < length; i++) {
                addReplacerName(state.propertyList, list.get(i));
            }
        } else if (isJSObjectArray(replacer) ||
                replacer instanceof Iterable ||
                (replacer != null && replacer.getClass().isArray())) {
            // a Java array or collection standing in for one, which Nashorn
            // lets through
            state.propertyList = new ArrayList<>();

            final Iterator<Object> iter = ArrayLikeIterator.arrayLikeIterator(replacer);

            while (iter.hasNext()) {
                addReplacerName(state.propertyList, iter.next());
            }
        }

        // If the space parameter is a number, make an indent
        // string containing that many spaces.

        String gap;

        // modifiable 'space' - parameter is final
        Object modSpace = space;
        if (modSpace instanceof NativeNumber) {
            modSpace = JSType.toNumber(JSType.toPrimitive(modSpace, Number.class));
        } else if (modSpace instanceof NativeString) {
            modSpace = JSType.toString(JSType.toPrimitive(modSpace, String.class));
        }

        if (modSpace instanceof Number) {
            final int indent = Math.min(10, JSType.toInteger(modSpace));
            if (indent < 1) {
                gap = "";
            } else {
                gap = " ".repeat(indent);
            }
        } else if (JSType.isString(modSpace)) {
            final String str = modSpace.toString();
            gap = str.substring(0, Math.min(10, str.length()));
        } else {
            gap = "";
        }

        state.gap = gap;

        final ScriptObject wrapper = Global.newEmptyInstance();
        // 24.3.2 step 9 is CreateDataProperty, not a set: what the replacer is
        // handed as its holder has the value as an own property of its own
        wrapper.addOwnProperty("", 0, value);

        return str("", wrapper, state);
    }

    /**
     * Adds one element of a replacer array to the list of names to serialize.
     *
     * ES2015 24.3.2 step 4.b: a string, a number, or an object wrapping one
     * names a property; anything else is passed over. A name already in the
     * list is passed over too, so a key repeated in the replacer is written
     * once.
     */
    private static void addReplacerName(final List<String> propertyList, final Object element) {
        final String item;
        if (element instanceof String || element instanceof ConsString) {
            item = element.toString();
        } else if (element instanceof Number || element instanceof NativeNumber
                || element instanceof NativeString) {
            item = JSType.toString(element);
        } else {
            return;
        }
        if (!propertyList.contains(item)) {
            propertyList.add(item);
        }
    }

    // -- Internals only below this point

    // stringify helpers.

    private static class StringifyState {
        final Map<Object, Object> stack = new IdentityHashMap<>();

        StringBuilder  indent = new StringBuilder();
        String         gap = "";
        List<String>   propertyList = null;
        Object         replacerFunction = null;
    }

    // Spec: The abstract operation Str(key, holder).
    private static Object str(final Object key, final Object holder, final StringifyState state) {
        assert holder instanceof ScriptObject || holder instanceof JSObject;

        Object value = getProperty(holder, key);
        // 24.3.12 step 8.a hands SerializeJSONProperty ToString(index), so what
        // a toJSON or a replacer is told an element is called is its name and
        // not its number. The lookup above still goes by the number.
        final Object name = key instanceof Integer ? JSType.toString(key) : key;
        try {
            if (value instanceof ScriptObject) {
                final InvokeByName toJSONInvoker = getTO_JSON();
                final ScriptObject svalue = (ScriptObject)value;
                final Object toJSON = toJSONInvoker.getGetter().invokeExact(svalue);
                if (Bootstrap.isCallable(toJSON)) {
                    value = toJSONInvoker.getInvoker().invokeExact(toJSON, svalue, name);
                }
            } else if (value instanceof JSObject) {
                final JSObject jsObj = (JSObject)value;
                final Object toJSON = jsObj.getMember("toJSON");
                if (Bootstrap.isCallable(toJSON)) {
                    value = getJSOBJECT_INVOKER().invokeExact(toJSON, value);
                }
            }

            if (state.replacerFunction != null) {
                value = getREPLACER_INVOKER().invokeExact(state.replacerFunction, holder, name, value);
            }
        } catch(Error|RuntimeException t) {
            throw t;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
        final boolean isObj = (value instanceof ScriptObject);
        if (isObj) {
            if (value instanceof NativeNumber) {
                value = JSType.toNumber(value);
            } else if (value instanceof NativeString) {
                value = JSType.toString(value);
            } else if (value instanceof NativeBoolean) {
                value = ((NativeBoolean)value).booleanValue();
            }
        }

        if (value == null) {
            return "null";
        } else if (Boolean.TRUE.equals(value)) {
            return "true";
        } else if (Boolean.FALSE.equals(value)) {
            return "false";
        }

        if (value instanceof String) {
            return JSONFunctions.quote((String)value);
        } else if (value instanceof ConsString) {
            return JSONFunctions.quote(value.toString());
        }

        if (value instanceof Number) {
            return JSType.isFinite(((Number)value).doubleValue()) ? JSType.toString(value) : "null";
        }

        final JSType type = JSType.of(value);
        if (type == JSType.OBJECT) {
            if (NativeArray.isArray(null, value) || isJSObjectArray(value)) {
                return JA(value, state);
            } else if (value instanceof ScriptObject || value instanceof JSObject) {
                return JO(value, state);
            }
        }

        return UNDEFINED;
    }

    // Spec: The abstract operation JO(value) serializes an object.
    private static String JO(final Object value, final StringifyState state) {
        assert value instanceof ScriptObject || value instanceof JSObject;

        if (state.stack.containsKey(value)) {
            throw typeError("JSON.stringify.cyclic");
        }

        state.stack.put(value, value);
        final StringBuilder stepback = new StringBuilder(state.indent.toString());
        state.indent.append(state.gap);

        final StringBuilder finalStr = new StringBuilder();
        final List<Object>  partial  = new ArrayList<>();
        final List<String>  k        = state.propertyList == null ?
                Arrays.asList(getOwnKeys(value)) : state.propertyList;

        for (final Object p : k) {
            final Object strP = str(p, value, state);

            if (strP != UNDEFINED) {
                final StringBuilder member = new StringBuilder();

                member.append(JSONFunctions.quote(p.toString())).append(':');
                if (!state.gap.isEmpty()) {
                    member.append(' ');
                }

                member.append(strP);
                partial.add(member);
            }
        }

        if (partial.isEmpty()) {
            finalStr.append("{}");
        } else {
            final int size = partial.size();
            int index = 0;
            if (state.gap.isEmpty()) {

                finalStr.append('{');

                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(',');
                    }
                    index++;
                }

            } else {
                finalStr.append("{\n");
                finalStr.append(state.indent);

                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(",\n");
                        finalStr.append(state.indent);
                    }
                    index++;
                }

                finalStr.append('\n');
                finalStr.append(stepback);
            }
            finalStr.append('}');
        }

        state.stack.remove(value);
        state.indent = stepback;

        return finalStr.toString();
    }

    // Spec: The abstract operation JA(value) serializes an array.
    private static Object JA(final Object value, final StringifyState state) {
        assert value instanceof ScriptObject || value instanceof JSObject;

        if (state.stack.containsKey(value)) {
            throw typeError("JSON.stringify.cyclic");
        }

        state.stack.put(value, value);
        final StringBuilder stepback = new StringBuilder(state.indent.toString());
        state.indent.append(state.gap);
        final List<Object> partial = new ArrayList<>();

        final int length = JSType.toInteger(getLength(value));
        int index = 0;

        while (index < length) {
            Object strP = str(index, value, state);
            if (strP == UNDEFINED) {
                strP = "null";
            }
            partial.add(strP);
            index++;
        }

        final StringBuilder finalStr = new StringBuilder();
        if (partial.isEmpty()) {
            finalStr.append("[]");
        } else {
            final int size = partial.size();
            index = 0;
            if (state.gap.isEmpty()) {
                finalStr.append('[');
                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(',');
                    }
                    index++;
                }

            } else {
                finalStr.append("[\n");
                finalStr.append(state.indent);
                for (final Object str : partial) {
                    finalStr.append(str);
                    if (index < size - 1) {
                        finalStr.append(",\n");
                        finalStr.append(state.indent);
                    }
                    index++;
                }

                finalStr.append('\n');
                finalStr.append(stepback);
            }
            finalStr.append(']');
        }

        state.stack.remove(value);
        state.indent = stepback;

        return finalStr.toString();
    }

    private static String[] getOwnKeys(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).getOwnKeys(false);
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).getOwnKeys(false);
        } else if (obj instanceof JSObject) {
            // No notion of "own keys" or "proto" for general JSObject! We just
            // return all keys of the object. This will be useful for POJOs
            // implementing JSObject interface.
            return ((JSObject)obj).keySet().toArray(new String[0]);
        } else {
            throw new AssertionError("should not reach here");
        }
    }

    private static Object getLength(final Object obj) {
        if (obj instanceof ScriptObject sobj) {
            // 24.3.12 step 6 reads it as an ordinary property, so a getter runs
            // and a proxy's trap is asked
            return sobj.get("length");
        } else if (obj instanceof JSObject) {
            return ((JSObject)obj).getMember("length");
        } else {
            throw new AssertionError("should not reach here");
        }
    }

    private static boolean isJSObjectArray(final Object obj) {
        return (obj instanceof JSObject) && ((JSObject)obj).isArray();
    }

    private static Object getProperty(final Object holder, final Object key) {
        if (holder instanceof ScriptObject) {
            return ((ScriptObject)holder).get(key);
        } else if (holder instanceof JSObject) {
            final JSObject jsObj = (JSObject)holder;
            if (key instanceof Integer) {
                return jsObj.getSlot((Integer)key);
            } else {
                return jsObj.getMember(Objects.toString(key));
            }
        } else {
            return new AssertionError("should not reach here");
        }
    }

    /**
     * ES2015 24.3.3 JSON [ @@toStringTag ].
     */
    @Property(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "JSON";

}
