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

package org.monflabs.nashorn.api.debugger;

/**
 * A property as a debugger shows it.
 *
 * @param name the name, for display
 * @param key the property key: a {@link String}, or a symbol engine object
 * @param value the value, an engine object, or null for an accessor property
 * @param getter the getter, or null
 * @param setter the setter, or null
 * @param writable whether a data property is writable
 * @param enumerable whether the property is enumerable
 * @param configurable whether the property is configurable
 * @param isOwn whether the property is the object's own
 * @param wasThrown whether reading the value threw, in which case {@code value} is what was thrown
 * @since 2017.0.0
 */
public record DebugProperty(String name, Object key, Object value, Object getter, Object setter,
        boolean writable, boolean enumerable, boolean configurable, boolean isOwn, boolean wasThrown) {
}
