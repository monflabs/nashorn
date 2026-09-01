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

package org.monflabs.nashorn.internal.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * WHATWG Fetch: the Headers class. Installed by the fetch standard library,
 * not by the language: lower-case names, several values a name joined with
 * ", ", iteration in name order.
 */
@ScriptClass("Headers")
public final class NativeHeaders extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private static final Pattern NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    private final TreeMap<String, List<String>> map = new TreeMap<>();

    private NativeHeaders(final Global global) {
        super(global.getHeadersPrototype(), $nasgenmap$);
    }

    /**
     * new Headers(init): from another Headers, an array of [name, value] pairs, or an object.
     *
     * @param isNew whether called with new
     * @param self  self
     * @param init  the init
     * @return the headers
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object init) {
        if (!isNew) {
            throw ECMAErrors.typeError("constructor.requires.new", "Headers");
        }
        final NativeHeaders headers = new NativeHeaders(Global.instance());
        headers.fill(init);
        return headers;
    }

    static NativeHeaders empty(final Global global) {
        return new NativeHeaders(global);
    }

    private void fill(final Object init) {
        if (init instanceof NativeHeaders other) {
            other.map.forEach((name, values) -> map.put(name, new ArrayList<>(values)));
        } else if (init instanceof ScriptObject object && object.isArray()) {
            final int count = JSType.toInt32(object.getLength());
            for (int i = 0; i < count; i++) {
                if (!(object.get(i) instanceof ScriptObject pair) || !pair.isArray() || JSType.toInt32(pair.getLength()) != 2) {
                    throw typeError("Headers: an init array holds [name, value] pairs");
                }
                append(pair.get(0), pair.get(1));
            }
        } else if (init instanceof ScriptObject object) {
            for (final String name : object.getOwnKeys(false)) {
                append(name, object.get(name));
            }
        } else if (init instanceof JSObject object) {
            for (final String name : object.keySet()) {
                append(name, object.getMember(name));
            }
        } else if (!JSType.nullOrUndefined(init)) {
            throw typeError("Headers: cannot make headers from " + JSType.of(init).typeName());
        }
    }

    private static ECMAException typeError(final String message) {
        return new ECMAException(Global.instance().newTypeError(message), null);
    }

    private static String name(final Object name) {
        final String text = JSType.toString(name);
        if (!NAME.matcher(text).matches()) {
            throw typeError("Headers: invalid header name \"" + text + "\"");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static String value(final Object value) {
        return JSType.toString(value).strip();
    }

    private static NativeHeaders self(final Object self, final String method) {
        if (!(self instanceof NativeHeaders headers)) {
            throw typeError("Headers." + method + ": the receiver is not a Headers");
        }
        return headers;
    }

    void append(final Object name, final Object value) {
        map.computeIfAbsent(name(name), k -> new ArrayList<>()).add(value(value));
    }

    /** The joined value of a name, or null. */
    String value(final String name) {
        final List<String> values = map.get(name);
        return values == null ? null : String.join(", ", values);
    }

    /** name, joined value - in name order. */
    List<String[]> rows() {
        final List<String[]> rows = new ArrayList<>();
        map.forEach((name, values) -> rows.add(new String[] { name, String.join(", ", values) }));
        return rows;
    }

    NativeHeaders copy(final Global global) {
        final NativeHeaders copy = new NativeHeaders(global);
        copy.fill(this);
        return copy;
    }

    /**
     * Adds a value.
     * @param self self
     * @param name the name
     * @param value the value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static void append(final Object self, final Object name, final Object value) {
        self(self, "append").append(name, value);
    }

    /**
     * Replaces the values of a name with one.
     * @param self self
     * @param name the name
     * @param value the value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static void set(final Object self, final Object name, final Object value) {
        self(self, "set").map.put(name(name), new ArrayList<>(List.of(value(value))));
    }

    /**
     * The values of a name, joined; null if there are none.
     * @param self self
     * @param name the name
     * @return the value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object get(final Object self, final Object name) {
        return self(self, "get").value(name(name));
    }

    /**
     * Whether a name has a value.
     * @param self self
     * @param name the name
     * @return true if so
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean has(final Object self, final Object name) {
        return self(self, "has").map.containsKey(name(name));
    }

    /**
     * Removes a name.
     * @param self self
     * @param name the name
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static void delete(final Object self, final Object name) {
        self(self, "delete").map.remove(name(name));
    }

    /**
     * Calls back with (value, name, headers) for each name, in name order.
     * @param self self
     * @param callback the function
     * @param thisArg its this
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static void forEach(final Object self, final Object callback, final Object thisArg) {
        final NativeHeaders headers = self(self, "forEach");
        if (!Bootstrap.isCallable(callback)) {
            throw typeError("Headers.forEach: the argument is not a function");
        }
        for (final String[] row : headers.rows()) {
            ScriptRuntime.call(callback, thisArg, new Object[] { row[1], row[0], headers });
        }
    }

    /**
     * An iterator over [name, value] pairs; also what Symbol.iterator names.
     * @param self self
     * @return the iterator
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return new HeadersIterator(self(self, "entries").rows(), AbstractIterator.IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     * An iterator over the names.
     * @param self self
     * @return the iterator
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object keys(final Object self) {
        return new HeadersIterator(self(self, "keys").rows(), AbstractIterator.IterationKind.KEY, Global.instance());
    }

    /**
     * An iterator over the values.
     * @param self self
     * @return the iterator
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return new HeadersIterator(self(self, "values").rows(), AbstractIterator.IterationKind.VALUE, Global.instance());
    }

    /** Headers.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Headers";

    @Override
    public String getClassName() {
        return "Headers";
    }

    /** For the transport: name, joined value pairs. */
    public Map<String, List<String>> asMap() {
        final Map<String, List<String>> copy = new TreeMap<>();
        map.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return copy;
    }
}
