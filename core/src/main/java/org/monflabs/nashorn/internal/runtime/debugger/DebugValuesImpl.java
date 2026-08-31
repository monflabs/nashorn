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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.util.ArrayList;
import java.util.List;
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
        if (value == null || value instanceof ScriptObject) {
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

    @Override
    public String className(final Object value) {
        if (value instanceof ScriptFunction) {
            return "Function";
        }
        if (value instanceof ScriptObject so) {
            return so.getClassName();
        }
        if (value == null || isPrimitive(value)) {
            return null;
        }
        return value.getClass().getSimpleName();
    }

    @Override
    public String description(final Object value) {
        if (value instanceof ScriptFunction fn) {
            return truncate(fn.toSource());
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
        return String.valueOf(value);
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
        if (!(object instanceof ScriptObject so)) {
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
        if (object instanceof ScriptObject so) {
            final ScriptObject proto = so.getProto();
            properties.add(new DebugProperty("[[Prototype]]", "[[Prototype]]", proto, null, null, false, false, false, true, false));
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
        return -1;
    }

    @Override
    public void setProperty(final Object object, final Object key, final Object value) {
        if (object instanceof ScriptObject so) {
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
