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

package org.monflabs.nashorn.debugger.ui.model;

import java.util.Map;

/**
 * A value living in the engine, as the protocol describes it (a CDP
 * {@code RemoteObject}). A primitive carries its {@link #value}; an object
 * carries an {@link #objectId} its properties can be fetched with - but only
 * until the next resume, when the server releases the whole pause's objects.
 *
 * @param type the CDP type: {@code "object"}, {@code "function"}, {@code "string"},
 *        {@code "number"}, {@code "boolean"}, {@code "symbol"}, {@code "undefined"}, {@code "bigint"}
 * @param subtype the subtype for an object: {@code "array"}, {@code "null"}, {@code "error"}, ..., or null
 * @param className the object's class name, or null
 * @param description a human-readable rendering the server supplies for objects
 * @param value the value itself, for a primitive returned by value
 * @param unserializable a token for a value JSON cannot carry ({@code NaN}, {@code Infinity}, ...), or null
 * @param objectId the id to fetch properties with, for an object, or null
 */
public record RemoteValue(String type, String subtype, String className, String description,
                          Object value, String unserializable, String objectId) {

    /**
     * Reads a value from a CDP {@code RemoteObject} map.
     * @param map the map, or null
     * @return the value, or null when the map was null
     */
    public static RemoteValue of(final Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new RemoteValue(
                str(map.get("type")),
                str(map.get("subtype")),
                str(map.get("className")),
                str(map.get("description")),
                map.get("value"),
                str(map.get("unserializableValue")),
                str(map.get("objectId")));
    }

    private static String str(final Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Whether this value has properties worth expanding. */
    public boolean expandable() {
        return objectId != null && ("object".equals(type) || "function".equals(type)) && !"null".equals(subtype);
    }

    /**
     * A one-line rendering, as a console or a tree cell shows it: a string in
     * quotes, a function by its description's first line, an object by its
     * description, a primitive by its value.
     * @return the text
     */
    public String display() {
        if (type == null) {
            return "undefined";
        }
        switch (type) {
        case "string":
            return '"' + String.valueOf(value) + '"';
        case "undefined":
            return "undefined";
        case "function": {
            final String d = description != null ? description : "function";
            final int newline = d.indexOf('\n');
            return newline < 0 ? d : d.substring(0, newline) + " …";
        }
        case "object":
            if ("null".equals(subtype)) {
                return "null";
            }
            return description != null ? description : (className != null ? className : "Object");
        default:
            if (unserializable != null) {
                return unserializable;
            }
            if (value != null) {
                return String.valueOf(value);
            }
            return description != null ? description : type;
        }
    }
}
