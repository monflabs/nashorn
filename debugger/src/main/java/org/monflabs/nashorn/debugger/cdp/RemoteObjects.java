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

package org.monflabs.nashorn.debugger.cdp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugProperty;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.debugger.json.Json;

/**
 * The objects a session has handed out by id, and how values are written
 * as {@code Runtime.RemoteObject}s. Serializing an object reads it, so the
 * serializing methods run on the thread that owns the object.
 */
final class RemoteObjects {
    private static final int CAPACITY = 20000;
    private static final int PREVIEW_PROPERTIES = 5;

    private record Held(Object value, String group) {}

    private final DebugValues values;
    private final Map<String, Held> objects = new LinkedHashMap<>(256, 0.75f, false) {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Held> eldest) {
            return size() > CAPACITY;
        }
    };
    private final Map<String, List<String>> groups = new HashMap<>();
    private long next = 1;

    RemoteObjects(final DebugValues values) {
        this.values = values;
    }

    synchronized String register(final Object value, final String group) {
        final String id = Long.toString(next++);
        objects.put(id, new Held(value, group));
        if (group != null) {
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(id);
        }
        return id;
    }

    synchronized Object get(final String id) throws CdpError {
        final Held entry = objects.get(id);
        if (entry == null) {
            throw new CdpError(CdpError.SERVER_ERROR, "Could not find object with given id");
        }
        return entry.value();
    }

    synchronized void release(final String id) {
        objects.remove(id);
    }

    synchronized void releaseGroup(final String group) {
        final List<String> ids = groups.remove(group);
        if (ids != null) {
            ids.forEach(objects::remove);
        }
    }

    synchronized void clear() {
        objects.clear();
        groups.clear();
    }

    /** Drops the objects of a pause: the ones registered in the "backtrace" group. */
    synchronized void releaseBacktrace() {
        for (final Iterator<Map.Entry<String, Held>> it = objects.entrySet().iterator(); it.hasNext();) {
            if ("backtrace".equals(it.next().getValue().group())) {
                it.remove();
            }
        }
        groups.remove("backtrace");
    }

    // -- serialization ------------------------------------------------------

    Map<String, Object> remoteObject(final Object value, final String group, final boolean byValue, final boolean preview) {
        final String type = values.type(value);
        final Map<String, Object> object = Json.object("type", type);
        switch (type) {
        case "undefined":
            return object;
        case "string":
            object.put("value", values.toJava(value));
            return object;
        case "boolean":
            object.put("value", value);
            return object;
        case "number": {
            final String unserializable = values.unserializable(value);
            if (unserializable != null) {
                object.put("unserializableValue", unserializable);
                object.put("description", unserializable);
            } else {
                final double d = ((Number)value).doubleValue();
                object.put("value", d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object)Long.valueOf((long)d) : (Object)Double.valueOf(d));
                object.put("description", values.description(value));
            }
            return object;
        }
        case "symbol":
            object.put("description", values.description(value));
            object.put("objectId", register(value, group));
            return object;
        case "function":
            object.put("className", "Function");
            object.put("description", values.description(value));
            object.put("objectId", register(value, group));
            return object;
        default:
            break;
        }
        final String subtype = values.subtype(value);
        if ("null".equals(subtype)) {
            object.put("subtype", "null");
            object.put("value", null);
            return object;
        }
        if (subtype != null) {
            object.put("subtype", subtype);
        }
        final String className = values.className(value);
        if (className != null) {
            object.put("className", className);
        }
        object.put("description", values.description(value));
        if (byValue) {
            object.put("value", toJson(value, 0));
            return object;
        }
        object.put("objectId", register(value, group));
        if (preview) {
            object.put("preview", preview(value, subtype));
        }
        return object;
    }

    private Object toJson(final Object value, final int depth) {
        if (values.isPrimitive(value)) {
            final String unserializable = values.unserializable(value);
            return unserializable != null ? null : values.toJava(value);
        }
        if (depth > 5 || "function".equals(values.type(value))) {
            return null;
        }
        if ("array".equals(values.subtype(value))) {
            final List<Object> list = new ArrayList<>();
            for (final DebugProperty p : values.ownProperties(value, false, true)) {
                if (isIndex(p.name())) {
                    list.add(toJson(p.value(), depth + 1));
                }
            }
            return list;
        }
        final Map<String, Object> map = Json.object();
        for (final DebugProperty p : values.ownProperties(value, false, true)) {
            if (p.getter() == null && p.setter() == null) {
                map.put(p.name(), toJson(p.value(), depth + 1));
            }
        }
        return map;
    }

    private static boolean isIndex(final String name) {
        return !name.isEmpty() && name.chars().allMatch(Character::isDigit);
    }

    private Map<String, Object> preview(final Object value, final String subtype) {
        final Map<String, Object> preview = Json.object("type", "object");
        if (subtype != null) {
            preview.put("subtype", subtype);
        }
        preview.put("description", values.description(value));
        final List<Object> properties = new ArrayList<>();
        boolean overflow = false;
        for (final DebugProperty p : values.ownProperties(value, false, true)) {
            if (properties.size() >= PREVIEW_PROPERTIES) {
                overflow = true;
                break;
            }
            final Map<String, Object> pp = Json.object("name", p.name());
            if (p.getter() != null || p.setter() != null) {
                pp.put("type", "accessor");
            } else {
                final Object v = p.value();
                final String t = values.type(v);
                pp.put("type", t);
                final String s = values.subtype(v);
                if (s != null) {
                    pp.put("subtype", s);
                }
                pp.put("value", shortDescription(v));
            }
            properties.add(pp);
        }
        preview.put("overflow", overflow);
        preview.put("properties", properties);
        return preview;
    }

    private String shortDescription(final Object v) {
        final String d = values.description(v);
        return d.length() > 100 ? d.substring(0, 100) + "\u2026" : d;
    }

    Map<String, Object> propertyDescriptor(final DebugProperty p, final String group, final boolean preview) {
        final Map<String, Object> descriptor = Json.object("name", p.name());
        if (p.getter() != null || p.setter() != null) {
            descriptor.put("get", p.getter() == null ? Json.object("type", "undefined") : remoteObject(p.getter(), group, false, false));
            descriptor.put("set", p.setter() == null ? Json.object("type", "undefined") : remoteObject(p.setter(), group, false, false));
        } else {
            descriptor.put("value", remoteObject(p.value(), group, false, preview));
            descriptor.put("writable", p.writable());
        }
        descriptor.put("configurable", p.configurable());
        descriptor.put("enumerable", p.enumerable());
        descriptor.put("isOwn", p.isOwn());
        if (p.wasThrown()) {
            descriptor.put("wasThrown", true);
        }
        if (!(p.key() instanceof String)) {
            descriptor.put("symbol", remoteObject(p.key(), group, false, false));
        }
        return descriptor;
    }

    Map<String, Object> internalPropertyDescriptor(final DebugProperty p, final String group) {
        return Json.object("name", p.name(), "value", remoteObject(p.value(), group, false, false));
    }

    Map<String, Object> exceptionDetails(final DebugException e, final String group, final Location location, final int contextId) {
        final Map<String, Object> details = Json.object(
                "exceptionId", next++,
                "text", e.thrown() == null ? String.valueOf(e.getMessage()) : "Uncaught",
                "lineNumber", location == null ? 0L : (long)location.line(),
                "columnNumber", location == null ? 0L : (long)location.column());
        if (location != null) {
            details.put("scriptId", location.script().id());
            details.put("url", location.script().url());
        }
        if (e.thrown() != null) {
            details.put("exception", remoteObject(e.thrown(), group, false, false));
        }
        if (contextId > 0) {
            details.put("executionContextId", (long)contextId);
        }
        return details;
    }

    static Map<String, Object> location(final Location location) {
        return Json.object("scriptId", location.script().id(), "lineNumber", (long)location.line(), "columnNumber", (long)location.column());
    }
}
