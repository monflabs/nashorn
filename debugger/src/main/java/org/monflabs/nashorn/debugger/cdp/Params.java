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

package org.monflabs.nashorn.debugger.cdp;

import java.util.List;
import java.util.Map;

/**
 * Typed access to a request's parameters.
 */
final class Params {
    private final Map<String, Object> map;

    Params(final Object params) {
        this.map = params instanceof Map<?, ?> m ? castMap(m) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Map<?, ?> m) {
        return (Map<String, Object>)m;
    }

    boolean has(final String name) {
        return map.get(name) != null;
    }

    Object raw(final String name) {
        return map.get(name);
    }

    String string(final String name) throws CdpError {
        final Object v = map.get(name);
        if (v == null) {
            throw CdpError.invalidParams(name + " is required");
        }
        return v.toString();
    }

    String string(final String name, final String fallback) {
        final Object v = map.get(name);
        return v == null ? fallback : v.toString();
    }

    int integer(final String name) throws CdpError {
        final Object v = map.get(name);
        if (!(v instanceof Number n)) {
            throw CdpError.invalidParams(name + " must be an integer");
        }
        return n.intValue();
    }

    int integer(final String name, final int fallback) {
        final Object v = map.get(name);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    boolean bool(final String name, final boolean fallback) {
        final Object v = map.get(name);
        return v instanceof Boolean b ? b : fallback;
    }

    Params object(final String name) throws CdpError {
        final Object v = map.get(name);
        if (!(v instanceof Map)) {
            throw CdpError.invalidParams(name + " must be an object");
        }
        return new Params(v);
    }

    Params objectOrNull(final String name) {
        final Object v = map.get(name);
        return v instanceof Map ? new Params(v) : null;
    }

    List<?> list(final String name) {
        final Object v = map.get(name);
        return v instanceof List<?> l ? l : List.of();
    }
}
