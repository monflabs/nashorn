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
 * Receives what the debugger sees. Every method has an empty default, so a
 * listener implements only what it wants. {@link #paused} runs on the thread
 * that paused, before it blocks; everything else runs on the thread that
 * caused the event.
 *
 * @since 2017.0.0
 */
public interface DebugListener {

    /**
     * A global object was created.
     * @param context the new context
     */
    default void executionContextCreated(final ExecutionContext context) {}

    /**
     * A script was compiled and can be executed and broken in.
     * @param script the script
     */
    default void scriptParsed(final DebugScript script) {}

    /**
     * A pending breakpoint found its script.
     * @param breakpoint the breakpoint
     * @param location where it landed
     */
    default void breakpointResolved(final Breakpoint breakpoint, final Location location) {}

    /**
     * A thread paused. Called on that thread, which blocks after the call
     * until the event is resumed.
     * @param event the pause
     */
    default void paused(final PausedEvent event) {}

    /**
     * A paused thread resumed.
     * @param event the pause that ended
     */
    default void resumed(final PausedEvent event) {}

    /**
     * An exception escaped the outermost script frame of a thread.
     * @param event the exception
     */
    default void exceptionThrown(final ExceptionEvent event) {}

    /**
     * A script called a {@code console} function.
     * @param event the call
     */
    default void consoleCalled(final ConsoleEvent event) {}
}
