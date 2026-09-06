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
 * One frame of a paused call stack.
 * @param callFrameId the server's id, valid only for this pause
 * @param functionName the function's name, or empty for an anonymous one
 * @param url the script's url
 * @param scriptId the script's id
 * @param line the current line, zero based
 * @param column the current column, zero based
 * @param scopeChain the scopes visible here, innermost first
 * @param thisObject the value of {@code this}
 */
public record CallFrame(String callFrameId, String functionName, String url, String scriptId,
                        int line, int column, List<Scope> scopeChain, RemoteValue thisObject) {
    /** A label for the call-stack list. */
    public String label() {
        final String name = functionName == null || functionName.isEmpty() ? "(anonymous)" : functionName;
        final int slash = url == null ? -1 : url.lastIndexOf('/');
        final String file = url == null ? "?" : (slash < 0 ? url : url.substring(slash + 1));
        return name + "  " + file + ":" + (line + 1);
    }
}
