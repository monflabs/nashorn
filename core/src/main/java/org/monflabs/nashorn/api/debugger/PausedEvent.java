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

import java.util.List;
import java.util.concurrent.Callable;

/**
 * A thread paused in script. The thread stays paused until one of the
 * resuming methods is called - from any thread - and meanwhile runs whatever
 * {@link #call} is handed, so that script objects are only ever touched by the
 * thread that owns them.
 *
 * @since 2017.0.0
 */
public interface PausedEvent {

    /**
     * The paused thread.
     * @return the thread
     */
    Thread thread();

    /**
     * Why it paused.
     * @return the reason
     */
    PauseReason reason();

    /**
     * The call frames, innermost first.
     * @return the frames
     */
    List<DebugFrame> frames();

    /**
     * The breakpoints that caused the pause, if any.
     * @return the breakpoint ids
     */
    List<String> hitBreakpoints();

    /**
     * The exception being thrown, for a pause on an exception; otherwise null.
     * @return the thrown value, an engine object
     */
    Object exception();

    /**
     * The context the thread is executing in.
     * @return the context
     */
    ExecutionContext context();

    /**
     * Whether the thread has been resumed.
     * @return true once resumed
     */
    boolean isResumed();

    /**
     * Resumes execution.
     */
    void resume();

    /**
     * Resumes until the next statement, entering calls.
     */
    void stepInto();

    /**
     * Resumes until the next statement in this frame or a caller's.
     */
    void stepOver();

    /**
     * Resumes until the next statement in a caller's frame.
     */
    void stepOut();

    /**
     * Ends the script: the paused thread resumes by throwing
     * {@link ScriptTerminated}, and keeps throwing it at every statement it
     * reaches until nothing of the script is left on its stack, so that a
     * {@code catch} in the script cannot keep it running.
     */
    void terminate();

    /**
     * Runs an operation on the paused thread and returns its result. Called
     * on the paused thread itself, it runs inline.
     *
     * @param operation what to run
     * @param <T> the result type
     * @return the result
     * @throws IllegalStateException if the thread has been resumed
     * @throws Exception whatever the operation threw
     */
    <T> T call(Callable<T> operation) throws Exception;
}
