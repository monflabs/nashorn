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
import javax.script.ScriptEngine;

/**
 * The debugger of one engine. Obtained with {@link #of(ScriptEngine)} for an
 * engine created with the {@code --debugger} option; every method is safe to
 * call from any thread.
 *
 * @since 2017.0.0
 */
public interface Debugger {

    /**
     * Returns the debugger of a Nashorn script engine.
     *
     * @param engine an engine created with {@code --debugger}
     * @return its debugger
     * @throws IllegalStateException if the engine is not a Nashorn engine or was created without {@code --debugger}
     */
    static Debugger of(final ScriptEngine engine) {
        return org.monflabs.nashorn.internal.runtime.debugger.DebuggerImpl.of(engine);
    }

    /**
     * Adds a listener.
     * @param listener the listener
     */
    void addListener(DebugListener listener);

    /**
     * Removes a listener.
     * @param listener the listener
     */
    void removeListener(DebugListener listener);

    /**
     * Adds a trace listener: a passive stream of statements and completion
     * values, pausing nothing. Tracing keeps the statement hooks fully
     * engaged while any trace listener is registered, which costs speed;
     * remove the listener when done.
     *
     * @param listener the listener
     * @since 2017.0.0
     */
    void addTraceListener(TraceListener listener);

    /**
     * Removes a trace listener.
     * @param listener the listener
     * @since 2017.0.0
     */
    void removeTraceListener(TraceListener listener);

    /**
     * The execution contexts - one per global object the engine has created.
     * @return the contexts, in creation order
     */
    List<ExecutionContext> executionContexts();

    /**
     * The scripts the engine has compiled so far.
     * @return the scripts, in compilation order
     */
    List<DebugScript> scripts();

    /**
     * Sets a breakpoint. A breakpoint whose url names a script not yet compiled
     * stays pending - its {@link Breakpoint#locations()} is empty - and resolves
     * when that script arrives, which {@link DebugListener#breakpointResolved} reports.
     *
     * @param request where and under which condition
     * @return the breakpoint
     */
    Breakpoint setBreakpoint(BreakpointRequest request);

    /**
     * Removes a breakpoint.
     * @param breakpointId the id of the breakpoint
     */
    void removeBreakpoint(String breakpointId);

    /**
     * Activates or deactivates all breakpoints without removing them.
     * @param active whether breakpoints pause
     */
    void setBreakpointsActive(boolean active);

    /**
     * Makes the debugger skip every pause - breakpoints, steps, exceptions -
     * or honour them again.
     * @param skip whether to skip all pauses
     */
    void setSkipAllPauses(boolean skip);

    /**
     * Sets which exceptions pause execution.
     * @param mode the mode
     */
    void setPauseOnExceptions(PauseOnExceptions mode);

    /**
     * Pauses at the next statement any script thread reaches.
     */
    void pause();

    /**
     * Pauses at the first statement of the next script that starts executing,
     * which is how a debugger that was waited for gets to see the beginning.
     */
    void pauseOnStart();

    /**
     * Evaluates an expression in a context while nothing is paused. The
     * expression runs on the calling thread with the context's realm bound;
     * running it while a script thread is executing in that realm is a race
     * the caller accepts.
     *
     * @param context the context to evaluate in
     * @param expression the expression
     * @return the value
     * @throws DebugException if the expression throws or does not parse
     */
    Object evaluate(ExecutionContext context, String expression) throws DebugException;

    /**
     * Runs an operation on the calling thread with a context's realm bound, so
     * that it may read the context's objects while no thread is paused. The
     * same race as {@link #evaluate} applies.
     *
     * @param context the context
     * @param operation what to run
     * @param <T> the result type
     * @return the result
     * @throws Exception whatever the operation threw
     */
    <T> T call(ExecutionContext context, java.util.concurrent.Callable<T> operation) throws Exception;

    /**
     * The value model, for interpreting the objects the other methods hand out.
     * @return the value helpers
     */
    DebugValues values();

    /**
     * Resumes every paused thread, removes every breakpoint and detaches every
     * listener. The engine keeps running; a new listener may attach later.
     */
    void close();
}
