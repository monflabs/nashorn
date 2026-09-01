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

import java.util.List;

/**
 * A call frame of a paused thread. Valid until the thread resumes.
 *
 * @since 2017.0.0
 */
public interface DebugFrame {

    /**
     * The id, unique within the pause: the frame's index, innermost first.
     * @return the id
     */
    String id();

    /**
     * The function's name; empty for an anonymous function, {@code <program>} for a script body.
     * @return the name
     */
    String functionName();

    /**
     * Where the frame is: the statement it executes.
     * @return the location
     */
    Location location();

    /**
     * Where the function begins, or null if unknown.
     * @return the location
     */
    Location functionLocation();

    /**
     * The scopes visible from the frame, innermost first, ending with the global scope.
     * @return the scopes
     */
    List<DebugScope> scopes();

    /**
     * The receiver, an engine object.
     * @return the {@code this} value
     */
    Object thisValue();

    /**
     * Evaluates an expression as if written at the frame's location. Runs on
     * the paused thread.
     *
     * @param expression the expression
     * @return the value, an engine object
     * @throws DebugException if the expression throws or does not parse
     */
    Object evaluate(String expression) throws DebugException;
}
