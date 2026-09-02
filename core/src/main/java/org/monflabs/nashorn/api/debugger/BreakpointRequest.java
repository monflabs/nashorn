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

package org.monflabs.nashorn.api.debugger;

/**
 * Where a breakpoint goes. Exactly one of {@code url}, {@code urlRegex} and
 * {@code scriptId} names the script; the location is the first statement at
 * or after the line and column.
 *
 * @param url the script's exact url, or null
 * @param urlRegex a regular expression the url must match, or null
 * @param scriptId a script id, or null
 * @param line the line, zero based
 * @param column the column, zero based; negative for any column on the line
 * @param condition an expression that must be truthy for the breakpoint to pause, or null
 * @since 2017.0.0
 */
public record BreakpointRequest(String url, String urlRegex, String scriptId, int line, int column, String condition) {

    /**
     * A breakpoint by url and line.
     * @param url the script's url
     * @param line the line, zero based
     * @return the request
     */
    public static BreakpointRequest at(final String url, final int line) {
        return new BreakpointRequest(url, null, null, line, -1, null);
    }
}
