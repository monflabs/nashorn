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

package org.monflabs.js.debugger.ui.model;

import java.util.List;

/**
 * A breakpoint, as the client remembers it. Its identity is {@code url:line} -
 * the client owns that and re-arms it on every attach - while {@link #serverId}
 * and {@link #resolvedLines} are what the server most recently answered and are
 * dropped on disconnect.
 *
 * @param url the script url the breakpoint is on
 * @param line the requested line, zero based
 * @param condition an expression that must be truthy to pause, or null
 * @param enabled whether it is armed
 * @param serverId the server's breakpoint id, or null when not armed
 * @param resolvedLines the lines the server resolved it to, zero based (may be empty until resolved)
 */
public record Breakpoint(String url, int line, String condition, boolean enabled,
                         String serverId, List<Integer> resolvedLines) {
    /** The client-side identity: {@code url:line}. */
    public String key() {
        return url + ":" + line;
    }

    /** A label for the breakpoint list. */
    public String label() {
        final int slash = url.lastIndexOf('/');
        final String file = slash < 0 ? url : url.substring(slash + 1);
        return file + ":" + (line + 1) + (condition == null || condition.isEmpty() ? "" : " if " + condition);
    }

    /** This breakpoint with new server-side resolution. */
    public Breakpoint resolvedAs(final String newServerId, final List<Integer> lines) {
        return new Breakpoint(url, line, condition, enabled, newServerId, lines);
    }

    /** This breakpoint with a new condition. */
    public Breakpoint withCondition(final String newCondition) {
        return new Breakpoint(url, line, newCondition, enabled, serverId, resolvedLines);
    }

    /** This breakpoint enabled or disabled. */
    public Breakpoint withEnabled(final boolean on) {
        return new Breakpoint(url, line, condition, on, serverId, resolvedLines);
    }
}
