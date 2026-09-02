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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jdk.dynalink.beans.StaticClass;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugProperty;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.internal.objects.ArrayBufferView;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.objects.NativeArrayBuffer;
import org.monflabs.nashorn.internal.objects.NativeDataView;
import org.monflabs.nashorn.internal.objects.NativeDate;
import org.monflabs.nashorn.internal.objects.NativeGenerator;
import org.monflabs.nashorn.internal.objects.NativeMap;
import org.monflabs.nashorn.internal.objects.NativePromise;
import org.monflabs.nashorn.internal.objects.NativeProxy;
import org.monflabs.nashorn.internal.objects.NativeRegExp;
import org.monflabs.nashorn.internal.objects.NativeSet;
import org.monflabs.nashorn.internal.objects.NativeWeakMap;
import org.monflabs.nashorn.internal.objects.NativeWeakSet;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyDescriptor;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Symbol;
import org.monflabs.nashorn.internal.runtime.Undefined;

/**
 * The value model over the engine's objects.
 */
final class DebugValuesImpl implements DebugValues {
    private static final int DESCRIPTION_LIMIT = 200;

    private final DebuggerImpl debugger;

    DebugValuesImpl(final DebuggerImpl debugger) {
        this.debugger = debugger;
    }

    @Override
    public String type(final Object value) {
        if (value == null || value instanceof ScriptObject || value instanceof ScriptScopeView) {
            return value instanceof ScriptFunction ? "function" : "object";
        }
        if (value instanceof Undefined) {
            return "undefined";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof CharSequence) {
            return "string";
        }
        if (value instanceof Symbol) {
            return "symbol";
        }
        return "object";
    }

    @Override
    public String subtype(final Object value) {
        if (value == null) {
            return "null";
        }
        if (isJavaArrayLike(value)) {
            return "array";
        }
        if (!(value instanceof ScriptObject so) || value instanceof ScriptFunction) {
            return null;
        }
        if (so instanceof NativeProxy) {
            return "proxy";
        }
        if (so.isArray()) {
            return "array";
        }
        if (so instanceof NativeRegExp) {
            return "regexp";
        }
        if (so instanceof NativeDate) {
            return "date";
        }
        if (so instanceof NativeMap) {
            return "map";
        }
        if (so instanceof NativeSet) {
            return "set";
        }
        if (so instanceof NativeWeakMap) {
            return "weakmap";
        }
        if (so instanceof NativeWeakSet) {
            return "weakset";
        }
        if (isError(so)) {
            return "error";
        }
        if (so instanceof NativePromise) {
            return "promise";
        }
        if (so instanceof ArrayBufferView) {
            return "typedarray";
        }
        if (so instanceof NativeArrayBuffer) {
            return "arraybuffer";
        }
        if (so instanceof NativeDataView) {
            return "dataview";
        }
        if (so instanceof NativeGenerator) {
            return "generator";
        }
        return null;
    }

    private static boolean isJavaArrayLike(final Object value) {
        return value != null && (value.getClass().isArray() || value instanceof List);
    }

    @Override
    public String className(final Object value) {
        if (value instanceof ScriptFunction) {
            return "Function";
        }
        if (value instanceof ScriptScopeView) {
            return "Object";
        }
        if (value instanceof ScriptObject so) {
            return so.getClassName();
        }
        if (value == null || isPrimitive(value)) {
            return null;
        }
        if (value instanceof StaticClass sc) {
            return "JavaClass";
        }
        return javaName(value.getClass());
    }

    /** A Java class's name as a script would write it: simple, with [] for arrays. */
    private static String javaName(final Class<?> clazz) {
        if (clazz.isArray()) {
            return javaName(clazz.getComponentType()) + "[]";
        }
        final String simple = clazz.getSimpleName();
        return simple.isEmpty() ? clazz.getName() : simple;
    }

    @Override
    public String description(final Object value) {
        if (value instanceof ScriptFunction fn) {
            return truncate(fn.toSource());
        }
        if (value instanceof ScriptScopeView) {
            return "Script";
        }
        if (value instanceof ScriptObject so) {
            if (so.isArray()) {
                return "Array(" + arrayLength(so) + ")";
            }
            if (isError(so)) {
                return truncate(errorDescription(so));
            }
            if (so instanceof NativeDate || so instanceof NativeRegExp) {
                return safeString(so);
            }
            return so.getClassName();
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Undefined || value instanceof Symbol) {
            return JSType.toString(value);
        }
        if (value instanceof StaticClass sc) {
            return "[JavaClass " + sc.getRepresentedClass().getName() + "]";
        }
        if (value.getClass().isArray()) {
            return javaName(value.getClass().getComponentType()) + "[" + Array.getLength(value) + "]";
        }
        if (value instanceof Collection<?> c) {
            return javaName(value.getClass()) + "(" + c.size() + ")";
        }
        if (value instanceof Map<?, ?> m) {
            return javaName(value.getClass()) + "(" + m.size() + ")";
        }
        try {
            return truncate(String.valueOf(value));
        } catch (final RuntimeException e) {
            return javaName(value.getClass());
        }
    }

    /** Every error class - Error, TypeError, ... - is a class of its own, and all of them answer "Error" here. */
    private static boolean isError(final ScriptObject so) {
        return "Error".equals(so.getClassName());
    }

    private static String errorDescription(final ScriptObject error) {
        final Object stack = error.get("stack");
        if (stack instanceof CharSequence s && !s.isEmpty()) {
            return s.toString();
        }
        final Object name = error.get("name");
        final Object message = error.get("message");
        return (name == ScriptRuntime.UNDEFINED ? "Error" : JSType.toString(name))
                + (message == ScriptRuntime.UNDEFINED ? "" : ": " + JSType.toString(message));
    }

    private static String safeString(final ScriptObject so) {
        try {
            return JSType.toString(so);
        } catch (final RuntimeException e) {
            return so.getClassName();
        }
    }

    private static String truncate(final String s) {
        return s.length() <= DESCRIPTION_LIMIT ? s : s.substring(0, DESCRIPTION_LIMIT) + "\u2026";
    }

    @Override
    public boolean isPrimitive(final Object value) {
        if (value instanceof ScriptScopeView) {
            return false;
        }
        return value == null || value instanceof Undefined || value instanceof Boolean || value instanceof Number
                || value instanceof CharSequence || value instanceof Symbol;
    }

    @Override
    public Object toJava(final Object value) {
        if (value instanceof CharSequence cs && !(value instanceof String)) {
            return cs.toString();
        }
        if (value instanceof Number n && !(value instanceof Double)) {
            return n.doubleValue();
        }
        return value;
    }

    @Override
    public String unserializable(final Object value) {
        if (value instanceof Number n) {
            final double d = n.doubleValue();
            if (Double.isNaN(d)) {
                return "NaN";
            }
            if (d == Double.POSITIVE_INFINITY) {
                return "Infinity";
            }
            if (d == Double.NEGATIVE_INFINITY) {
                return "-Infinity";
            }
            if (d == 0 && 1 / d < 0) {
                return "-0";
            }
        }
        return null;
    }

    @Override
    public List<DebugProperty> ownProperties(final Object object, final boolean includeNonEnumerable, final boolean includeIndexed) {
        final List<DebugProperty> properties = new ArrayList<>();
        if (object instanceof ScriptScopeView view) {
            for (final DebugProperty p : ownProperties(view.global(), includeNonEnumerable, includeIndexed)) {
                if (view.isScriptProperty(p.key())) {
                    properties.add(p);
                }
            }
            return properties;
        }
        if (!(object instanceof ScriptObject so)) {
            if (object != null && !isPrimitive(object)) {
                javaProperties(object, includeNonEnumerable, includeIndexed, properties);
            }
            return properties;
        }
        for (final Object key : so.getOwnKeysAndSymbols(includeNonEnumerable)) {
            if (!includeIndexed && key instanceof String s && isArrayIndex(s)) {
                continue;
            }
            final String name = key instanceof Symbol ? key.toString() : (String)key;
            try {
                final Object descriptor = so.getOwnPropertyDescriptor(key);
                if (!(descriptor instanceof PropertyDescriptor pd)) {
                    continue;
                }
                if (pd.type() == PropertyDescriptor.ACCESSOR) {
                    properties.add(new DebugProperty(name, key, null, pd.getGetter(), pd.getSetter(),
                            false, pd.isEnumerable(), pd.isConfigurable(), true, false));
                } else {
                    properties.add(new DebugProperty(name, key, pd.getValue(), null, null,
                            pd.isWritable(), pd.isEnumerable(), pd.isConfigurable(), true, false));
                }
            } catch (final ECMAException e) {
                // a let or const binding read before its declaration ran: a
                // debugger shows the ReferenceError where the value would be
                properties.add(new DebugProperty(name, key, e.getThrown(), null, null, false, true, false, true, true));
            }
        }
        return properties;
    }

    /**
     * A Java object as a script sees it through Dynalink: an array's or list's
     * elements, a map's entries, a JSObject's members, and otherwise its public
     * fields and bean properties - the getters called, since that is what
     * reading the property would do.
     */
    private void javaProperties(final Object object, final boolean includeNonEnumerable, final boolean includeIndexed,
            final List<DebugProperty> properties) {
        if (object instanceof JSObject js) {
            for (final String key : js.keySet()) {
                Object value;
                boolean thrown = false;
                try {
                    value = js.getMember(key);
                } catch (final RuntimeException e) {
                    value = e;
                    thrown = true;
                }
                properties.add(new DebugProperty(key, key, value, null, null, true, true, true, true, thrown));
            }
            return;
        }
        if (object instanceof StaticClass sc) {
            staticMembers(sc.getRepresentedClass(), properties);
            return;
        }
        final Class<?> clazz = object.getClass();
        if (clazz.isArray()) {
            final int length = Array.getLength(object);
            if (includeIndexed) {
                for (int i = 0; i < length; i++) {
                    properties.add(new DebugProperty(Integer.toString(i), Integer.toString(i), Array.get(object, i), null, null, true, true, false, true, false));
                }
            }
            if (includeNonEnumerable) {
                properties.add(new DebugProperty("length", "length", length, null, null, false, false, false, true, false));
            }
            return;
        }
        if (object instanceof List<?> list) {
            if (includeIndexed) {
                int i = 0;
                for (final Object element : list) {
                    properties.add(new DebugProperty(Integer.toString(i), Integer.toString(i), element, null, null, true, true, true, true, false));
                    i++;
                }
            }
            if (includeNonEnumerable) {
                properties.add(new DebugProperty("length", "length", list.size(), null, null, false, false, false, true, false));
            }
            return;
        }
        if (object instanceof Map<?, ?> map) {
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String name = String.valueOf(entry.getKey());
                properties.add(new DebugProperty(name, name, entry.getValue(), null, null, true, true, true, true, false));
            }
            return;
        }
        if (object instanceof Collection<?> collection) {
            if (includeIndexed) {
                int i = 0;
                for (final Object element : collection) {
                    properties.add(new DebugProperty(Integer.toString(i), Integer.toString(i), element, null, null, false, true, false, true, false));
                    i++;
                }
            }
            if (includeNonEnumerable) {
                properties.add(new DebugProperty("size", "size", collection.size(), null, null, false, false, false, true, false));
            }
            return;
        }
        beanProperties(object, clazz, properties);
    }

    private static void beanProperties(final Object object, final Class<?> clazz, final List<DebugProperty> properties) {
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (final Field field : clazz.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value;
            boolean thrown = false;
            try {
                value = field.get(object);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                value = e;
                thrown = true;
            }
            seen.add(field.getName());
            properties.add(new DebugProperty(field.getName(), field.getName(), value, null, null,
                    !Modifier.isFinal(field.getModifiers()), true, false, true, thrown));
        }
        for (final Method method : clazz.getMethods()) {
            final String property = beanProperty(method);
            if (property == null || !seen.add(property)) {
                continue;
            }
            Object value;
            boolean thrown = false;
            try {
                value = method.invoke(object);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                value = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
                thrown = true;
            }
            properties.add(new DebugProperty(property, property, value, null, null, false, true, false, true, thrown));
        }
    }

    private static void staticMembers(final Class<?> clazz, final List<DebugProperty> properties) {
        for (final Field field : clazz.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object value;
            boolean thrown = false;
            try {
                value = field.get(null);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                value = e;
                thrown = true;
            }
            properties.add(new DebugProperty(field.getName(), field.getName(), value, null, null,
                    !Modifier.isFinal(field.getModifiers()), true, false, true, thrown));
        }
    }

    /** The bean property a public no-argument getter defines, or null. */
    private static String beanProperty(final Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                || method.getDeclaringClass() == Object.class || method.getReturnType() == void.class) {
            return null;
        }
        final String name = method.getName();
        final String rest;
        if (name.startsWith("get") && name.length() > 3) {
            rest = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2 && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            rest = name.substring(2);
        } else {
            return null;
        }
        if (!Character.isUpperCase(rest.charAt(0))) {
            return null;
        }
        return rest.length() > 1 && Character.isUpperCase(rest.charAt(1)) ? rest : Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    private static boolean isArrayIndex(final String s) {
        if (s.isEmpty() || s.length() > 10) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return s.length() == 1 || s.charAt(0) != '0';
    }

    @Override
    public List<DebugProperty> internalProperties(final Object object) {
        final List<DebugProperty> properties = new ArrayList<>();
        if (object instanceof ScriptScopeView) {
            return properties;
        }
        if (object instanceof ScriptObject so) {
            final ScriptObject proto = so.getProto();
            properties.add(new DebugProperty("[[Prototype]]", "[[Prototype]]", proto, null, null, false, false, false, true, false));
        } else if (object != null && !isPrimitive(object)) {
            final Class<?> clazz = object instanceof StaticClass sc ? sc.getRepresentedClass() : object.getClass();
            properties.add(new DebugProperty("[[JavaClass]]", "[[JavaClass]]", clazz.getName(), null, null, false, false, false, true, false));
        }
        return properties;
    }

    @Override
    public Object prototype(final Object object) {
        return object instanceof ScriptObject so ? so.getProto() : null;
    }

    @Override
    public long arrayLength(final Object array) {
        if (array instanceof ScriptObject so && so.isArray()) {
            return so.getArray().length();
        }
        if (array != null && array.getClass().isArray()) {
            return Array.getLength(array);
        }
        if (array instanceof List<?> list) {
            return list.size();
        }
        return -1;
    }

    @Override
    public void setProperty(final Object object, final Object key, final Object value) {
        if (object instanceof ScriptScopeView view) {
            view.global().set(key, value, 0);
        } else if (object instanceof ScriptObject so) {
            so.set(key, value, 0);
        }
    }

    @Override
    public Object callFunction(final Object function, final Object thisValue, final Object... arguments) throws DebugException {
        if (!(function instanceof ScriptFunction fn)) {
            throw new DebugException("not a function: " + description(function), null, null);
        }
        try {
            return ScriptRuntime.apply(fn, thisValue == null ? ScriptRuntime.UNDEFINED : thisValue, arguments);
        } catch (final ECMAException e) {
            throw new DebugException(DebuggerImpl.messageOf(e), e.getThrown(), e);
        }
    }

    @Override
    public Object evaluateWith(final ExecutionContext context, final String expression, final Object thisValue) throws DebugException {
        final Global global = ((ExecutionContextImpl)context).globalObject();
        return Context.callWithGlobal(global, () -> DebuggerImpl.evalIn(global, global, expression, thisValue));
    }
}
