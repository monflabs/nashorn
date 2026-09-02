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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * A compiled source as a debugger sees it.
 */
final class DebugScriptImpl implements DebugScript {
    private final String id;
    private final String url;
    final Source source;
    private final ScriptInfo info;
    private final ExecutionContextImpl context;
    private final boolean module;

    DebugScriptImpl(final int id, final Source source, final ScriptInfo info, final ExecutionContextImpl context, final boolean module) {
        this.id = Integer.toString(id);
        this.source = source;
        this.info = info;
        this.context = context;
        this.module = module;
        this.url = urlFor(source, id);
    }

    private static String urlFor(final Source source, final int id) {
        final String explicit = source.getExplicitURL();
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }
        if (source.getURL() != null) {
            return source.getURL().toExternalForm();
        }
        final java.io.File file = new java.io.File(source.getName());
        if (file.isFile()) {
            return file.toURI().toString();
        }
        final String name = source.getName();
        final StringBuilder sb = new StringBuilder("nashorn://script/").append(id).append('/');
        for (final char c : name.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public String name() {
        return source.getName();
    }

    @Override
    public String source() {
        return source.getString();
    }

    @Override
    public String hash() {
        return source.getDigest();
    }

    @Override
    public int endLine() {
        final char[] content = source.getContent();
        int lines = 0;
        for (final char c : content) {
            if (c == '\n') {
                lines++;
            }
        }
        return lines;
    }

    @Override
    public int endColumn() {
        final int length = source.getLength();
        return length == 0 ? 0 : source.getColumn(length - 1) + 1;
    }

    @Override
    public int length() {
        return source.getLength();
    }

    @Override
    public boolean isEval() {
        return source.isEvalCode();
    }

    @Override
    public boolean isModule() {
        return module;
    }

    @Override
    public ExecutionContext context() {
        return context;
    }

    ScriptInfo info() {
        return info;
    }

    @Override
    public List<Location> possibleBreakpoints(final int startLine, final int startColumn, final int endLine, final int endColumn) {
        final List<Location> locations = new ArrayList<>();
        for (final ScriptInfo.Entry entry : info.range(startLine, startColumn, endLine, endColumn)) {
            locations.add(new Location(this, entry.line, entry.column));
        }
        return locations;
    }

    @Override
    public String toString() {
        return "script " + id + " " + url;
    }
}
