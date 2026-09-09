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
 * Why a thread paused.
 *
 * @since 2017.0.0
 */
public enum PauseReason {
    /** A breakpoint was hit. */
    BREAKPOINT,
    /** A step completed. */
    STEP,
    /** {@link Debugger#pause()} was called. */
    DEBUG_COMMAND,
    /** An exception was thrown. */
    EXCEPTION,
    /** The first statement of a script, after {@link Debugger#pauseOnStart()}. */
    START,
    /** A {@code debugger} statement was reached. */
    DEBUGGER_STATEMENT,
    /** Something else. */
    OTHER
}
