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
 * A passive observer of execution: statements as they are reached, and the
 * completion value of each program-level expression statement. Nothing
 * pauses; the stream coexists with breakpoints, stepping and an attached
 * protocol client.
 *
 * <p>Both callbacks run on the script's own thread, with its realm bound and
 * further hooks suppressed for their duration. Do not call back into script
 * directly; read values through {@link Debugger#values()}, whose
 * {@code callFunction} and {@code evaluateWith} are safe here.
 *
 * @since 2017.0.0
 */
public interface TraceListener {

    /**
     * A statement is about to run.
     *
     * @param script the script it belongs to
     * @param line the statement's line, zero based
     * @param column the statement's column, zero based
     * @param depth the number of script frames on the stack: 1 for a
     *        top-level statement of a program or module, more inside calls
     */
    default void statementReached(final DebugScript script, final int line, final int column, final int depth) {
    }

    /**
     * A program-level expression statement just produced its completion value
     * - what {@code eval} would return if the program ended here.
     *
     * @param script the script
     * @param line the statement's line, zero based
     * @param value the value, in the engine's own representation
     */
    default void completionValue(final DebugScript script, final int line, final Object value) {
    }
}
