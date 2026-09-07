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

package org.monflabs.nashorn.internal.runtime;

/**
 * One ES2022 private element on an object: a private field's value, a private
 * method, or a private accessor pair. What kind it is decides how a private read
 * or write behaves - a field's value is read and written, a method is read but
 * not written, and an accessor runs its getter or setter.
 */
public final class PrivateElement {
    /** A private field: its value is held directly and is writable. */
    public static final int FIELD = 0;
    /** A private method: read gives the function, write is a TypeError. */
    public static final int METHOD = 1;
    /** A private accessor: read/write run the getter/setter. */
    public static final int ACCESSOR = 2;

    private final int kind;
    private Object value;                 // FIELD: the value; METHOD: the function
    private final ScriptFunction getter;  // ACCESSOR only
    private final ScriptFunction setter;  // ACCESSOR only

    private PrivateElement(final int kind, final Object value, final ScriptFunction getter, final ScriptFunction setter) {
        this.kind = kind;
        this.value = value;
        this.getter = getter;
        this.setter = setter;
    }

    /** A private field holding {@code value}. */
    public static PrivateElement field(final Object value) {
        return new PrivateElement(FIELD, value, null, null);
    }

    /** A private method that is {@code fn}. */
    public static PrivateElement method(final ScriptFunction fn) {
        return new PrivateElement(METHOD, fn, null, null);
    }

    /** A private accessor with the given getter and/or setter (either may be null). */
    public static PrivateElement accessor(final ScriptFunction getter, final ScriptFunction setter) {
        return new PrivateElement(ACCESSOR, null, getter, setter);
    }

    public int getKind() {
        return kind;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    public ScriptFunction getGetter() {
        return getter;
    }

    public ScriptFunction getSetter() {
        return setter;
    }

    /** Merges an accessor half into this one, so a get/set pair written apart share an element. */
    public PrivateElement withAccessorHalf(final PrivateElement other) {
        return new PrivateElement(ACCESSOR, null,
                getter != null ? getter : other.getter,
                setter != null ? setter : other.setter);
    }
}
