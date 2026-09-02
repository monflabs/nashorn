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
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * What the debugger knows about one source, independently of any engine: the
 * positions a statement hook can fire at. Filled from the parse tree when the
 * source is compiled and topped up by the hook bootstraps, so that a statement
 * the desugaring produced is breakable too.
 */
public final class ScriptInfo {

    /**
     * A breakable position. Identity matters: the statement hook is bound to
     * its entry, and a breakpoint is attached to the entries it resolved to.
     */
    public static final class Entry {
        /** The line, zero based. */
        public final int line;
        /** The column, zero based. */
        public final int column;
        /** The breakpoints resolved to this entry, or null. Written under the debugger's lock. */
        volatile BreakpointImpl[] breakpoints;

        Entry(final int line, final int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return line + ":" + column;
        }
    }

    private final ConcurrentSkipListMap<Long, Entry> entries = new ConcurrentSkipListMap<>();

    /**
     * The info of a source, made on first demand.
     * @param source the source
     * @return its info
     */
    public static ScriptInfo of(final Source source) {
        ScriptInfo info = source.getDebugInfo();
        if (info == null) {
            synchronized (source) {
                info = source.getDebugInfo();
                if (info == null) {
                    info = new ScriptInfo();
                    source.setDebugInfo(info);
                }
            }
        }
        return info;
    }

    private static long key(final int line, final int column) {
        return ((long)line << 32) | (column & 0xffffffffL);
    }

    /**
     * Adds a position, or returns the entry it already has.
     * @param line the line, zero based
     * @param column the column, zero based
     * @return the entry
     */
    public Entry add(final int line, final int column) {
        return entries.computeIfAbsent(key(line, column), k -> new Entry(line, column));
    }

    /**
     * The first entry at or after a position.
     * @param line the line, zero based
     * @param column the column, zero based
     * @return the entry, or null when there is none
     */
    public Entry ceiling(final int line, final int column) {
        final Map.Entry<Long, Entry> e = entries.ceilingEntry(key(line, Math.max(column, 0)));
        return e == null ? null : e.getValue();
    }

    /**
     * The entries within a range, in order.
     * @param startLine first line
     * @param startColumn first column
     * @param endLine last line, inclusive; negative for no bound
     * @param endColumn last column on the last line, exclusive
     * @return the entries
     */
    public List<Entry> range(final int startLine, final int startColumn, final int endLine, final int endColumn) {
        final long from = key(startLine, Math.max(startColumn, 0));
        final long to = endLine < 0 ? Long.MAX_VALUE : key(endLine, endColumn < 0 ? Integer.MAX_VALUE : endColumn);
        return new ArrayList<>(entries.subMap(from, true, to, false).values());
    }

    /**
     * Whether the source has any breakable position at all.
     * @return true if it does
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
