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
 * The ES2022 private name of a class element ({@code #x}).
 *
 * A private name is not a property key: it is neither a string nor a symbol, it
 * is never enumerated, reflected, or reachable through a prototype, and it keys a
 * hidden per-object slot rather than an ordinary property. Each occurrence in a
 * class body denotes one private name, made fresh each time the class is
 * evaluated (so two evaluations of the same class body do not share names), and
 * its identity - not its description - is what a lookup matches on. A read of a
 * private name that the object does not carry is a TypeError, which is how the
 * language expresses that the private element is not present ("brand check").
 *
 * @see ScriptObject#getPrivate ScriptObject.getPrivate/setPrivate/hasPrivate/definePrivate
 */
public final class PrivateName {
    /** The written name, including the {@code #}, for error messages only. */
    private final String description;

    /**
     * Creates a fresh private name.
     *
     * @param description the written name (e.g. {@code #x}), for diagnostics
     */
    public PrivateName(final String description) {
        this.description = description;
    }

    /**
     * The written name, including the {@code #}. Two private names may share a
     * description and still be distinct; identity is what matters.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
