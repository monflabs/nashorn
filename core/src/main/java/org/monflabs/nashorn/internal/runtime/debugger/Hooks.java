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

package org.monflabs.nashorn.internal.runtime.debugger;

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.api.debugger.PauseOnExceptions;
import org.monflabs.nashorn.api.debugger.PauseReason;
import org.monflabs.nashorn.api.debugger.ScriptTerminated;
import org.monflabs.nashorn.internal.codegen.CompilerConstants;
import org.monflabs.nashorn.internal.codegen.CompilerConstants.Call;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * The debugger's hooks: what the code generator calls into from a script
 * compiled with {@code --debugger}. Each hook is an {@code invokedynamic}
 * bootstrapped here, so the site binds once to the objects it needs - its
 * statement's entry, its function's source - and thereafter costs a call.
 *
 * <p>The entry and exit hooks keep the thread's shadow stack whatever the
 * debugger is doing, so that the frames are right when someone does look. The
 * statement hook records the frame's position and then, unless something is
 * {@link #interesting}, returns.
 */
public final class Hooks {
    /** Bootstrap for the statement hook: {@code (ScriptObject scope, Object this)V} with the line and column as constants. */
    public static final Call STMT_BOOTSTRAP = staticCallNoLookup(Hooks.class, "stmt",
            CallSite.class, Lookup.class, String.class, MethodType.class, int.class, int.class);
    /** Bootstrap for the function entry hook: {@code (ScriptObject scope, Object this, ScriptFunction callee)V} with the name, line and column as constants. */
    public static final Call ENTER_BOOTSTRAP = staticCallNoLookup(Hooks.class, "enter",
            CallSite.class, Lookup.class, String.class, MethodType.class, String.class, int.class, int.class);
    /** Bootstrap for the normal exit hook: {@code ()V}. */
    public static final Call EXIT_BOOTSTRAP = staticCallNoLookup(Hooks.class, "exit",
            CallSite.class, Lookup.class, String.class, MethodType.class);
    /** Bootstrap for the exceptional exit hook: {@code (Throwable)V}. */
    public static final Call EXIT_THROW_BOOTSTRAP = staticCallNoLookup(Hooks.class, "exitThrow",
            CallSite.class, Lookup.class, String.class, MethodType.class);
    /** Bootstrap for the completion-value hook: {@code (Object)V} with the line as a constant. */
    public static final Call COMPLETION_BOOTSTRAP = staticCallNoLookup(Hooks.class, "completion",
            CallSite.class, Lookup.class, String.class, MethodType.class, int.class);

    /**
     * Whether any debugger has a reason to stop a thread: a breakpoint, a
     * pending step, a pause request, an exception mode. Read by every
     * statement hook; written by {@link DebuggerImpl#recomputeInteresting()}.
     */
    static volatile boolean interesting;

    /**
     * Whether any debugger has a listener: what a {@code debugger} statement
     * asks before it pauses, in every engine, so it must be one read.
     */
    static volatile boolean attached;

    private static final MethodHandle STATEMENT;
    private static final MethodHandle ENTER;
    private static final MethodHandle EXIT;
    private static final MethodHandle EXIT_THROW;
    private static final MethodHandle COMPLETION;

    static {
        try {
            final Lookup lookup = MethodHandles.lookup();
            STATEMENT = lookup.findStatic(Hooks.class, "statement",
                    MethodType.methodType(void.class, ScriptInfo.Entry.class, ScriptObject.class, Object.class));
            ENTER = lookup.findStatic(Hooks.class, "enter",
                    MethodType.methodType(void.class, Source.class, String.class, int.class, int.class,
                            ScriptObject.class, Object.class, ScriptFunction.class));
            EXIT = lookup.findStatic(Hooks.class, "exit", MethodType.methodType(void.class));
            EXIT_THROW = lookup.findStatic(Hooks.class, "exitThrow", MethodType.methodType(void.class, Throwable.class));
            COMPLETION = lookup.findStatic(Hooks.class, "completion",
                    MethodType.methodType(void.class, Source.class, int.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Hooks() {
    }

    // -- bootstraps -------------------------------------------------------

    /**
     * Bootstraps a statement hook.
     * @param lookup the script class's lookup
     * @param name the site name
     * @param type the site type
     * @param line the statement's line, zero based
     * @param column the statement's column, zero based
     * @return the site
     */
    public static CallSite stmt(final Lookup lookup, final String name, final MethodType type, final int line, final int column) {
        final ScriptInfo.Entry entry = ScriptInfo.of(sourceOf(lookup)).add(line, column);
        return new ConstantCallSite(MethodHandles.insertArguments(STATEMENT, 0, entry).asType(type));
    }

    /**
     * Bootstraps a completion-value hook.
     * @param lookup the script class's lookup
     * @param name the site name
     * @param type the site type
     * @param line the statement's line, zero based
     * @return the site
     */
    public static CallSite completion(final Lookup lookup, final String name, final MethodType type, final int line) {
        return new ConstantCallSite(MethodHandles.insertArguments(COMPLETION, 0, sourceOf(lookup), line).asType(type));
    }

    /**
     * A program-level expression statement stored its completion value.
     */
    private static void completion(final Source source, final int line, final Object value) {
        if (!interesting) {
            return;
        }
        final ShadowStack stack = ShadowStack.current();
        if (stack.top() == null || stack.inCommand) {
            return;
        }
        final DebuggerImpl debugger = DebuggerImpl.current();
        if (debugger != null) {
            debugger.fireCompletion(stack, source, line, value);
        }
    }

    /**
     * Bootstraps a function entry hook.
     * @param lookup the script class's lookup
     * @param name the site name
     * @param type the site type
     * @param functionName the function's name, decoded; empty when anonymous
     * @param line the function's line, zero based
     * @param column the function's column, zero based
     * @return the site
     */
    public static CallSite enter(final Lookup lookup, final String name, final MethodType type,
            final String functionName, final int line, final int column) {
        return new ConstantCallSite(MethodHandles.insertArguments(ENTER, 0, sourceOf(lookup), functionName, line, column).asType(type));
    }

    /**
     * Bootstraps a normal exit hook.
     * @param lookup the script class's lookup
     * @param name the site name
     * @param type the site type
     * @return the site
     */
    public static CallSite exit(final Lookup lookup, final String name, final MethodType type) {
        return new ConstantCallSite(EXIT.asType(type));
    }

    /**
     * Bootstraps an exceptional exit hook.
     * @param lookup the script class's lookup
     * @param name the site name
     * @param type the site type
     * @return the site
     */
    public static CallSite exitThrow(final Lookup lookup, final String name, final MethodType type) {
        return new ConstantCallSite(EXIT_THROW.asType(type));
    }

    private static Source sourceOf(final Lookup lookup) {
        try {
            return (Source)lookup.findStaticGetter(lookup.lookupClass(), CompilerConstants.SOURCE.symbolName(), Source.class).invoke();
        } catch (final Throwable t) {
            throw new IllegalStateException("no source on " + lookup.lookupClass(), t);
        }
    }

    // -- targets ----------------------------------------------------------

    private static void enter(final Source source, final String functionName, final int line, final int column,
            final ScriptObject scope, final Object self, final ScriptFunction callee) {
        ShadowStack.current().push(new Frame(source, functionName, line, column, callee, self, scope));
    }

    private static void exit() {
        final ShadowStack stack = ShadowStack.current();
        stack.pop();
        if (stack.depth() == 0) {
            stack.clearStep();
            stack.terminating = false;
        }
    }

    private static void exitThrow(final Throwable t) {
        final ShadowStack stack = ShadowStack.current();
        final Frame frame = stack.top();
        if (frame != null && stack.depth() == 1 && t instanceof ECMAException e && !stack.inCommand) {
            final DebuggerImpl debugger = DebuggerImpl.current();
            if (debugger != null) {
                escaping(stack, debugger, e);
            }
        }
        stack.pop();
        if (stack.depth() == 0) {
            stack.clearStep();
            stack.pausedThrown = null;
            stack.terminating = false;
        }
    }

    /**
     * An exception is about to leave the outermost script frame of the thread.
     */
    private static void escaping(final ShadowStack stack, final DebuggerImpl debugger, final ECMAException e) {
        final Object thrown = e.getThrown();
        final PauseOnExceptions mode = debugger.pauseOnExceptions;
        if (mode != PauseOnExceptions.NONE && !debugger.skipAllPauses && thrown != stack.pausedThrown) {
            stack.pausedThrown = thrown;
            pause(stack, debugger, PauseReason.EXCEPTION, null, thrown);
        }
        debugger.fireExceptionThrown(stack, e);
    }

    private static void statement(final ScriptInfo.Entry site, final ScriptObject scope, final Object self) {
        final ShadowStack stack = ShadowStack.current();
        final Frame frame = stack.top();
        if (frame != null) {
            frame.site = site;
            frame.scope = scope;
            frame.self = self;
        }
        if (stack.terminating && !stack.inCommand) {
            throw new ScriptTerminated();
        }
        if (!interesting || frame == null || stack.inCommand) {
            return;
        }
        final DebuggerImpl debugger = DebuggerImpl.current();
        if (debugger == null || debugger.skipAllPauses) {
            return;
        }

        debugger.fireStatementTrace(stack, frame, site);

        PauseReason reason = null;
        List<String> hits = null;

        if (debugger.consumePauseOnStart()) {
            reason = PauseReason.START;
        }

        final BreakpointImpl[] breakpoints = site.breakpoints;
        if (breakpoints != null && debugger.breakpointsActive) {
            for (final BreakpointImpl breakpoint : breakpoints) {
                if (breakpoint.debugger == debugger && breakpoint.holds(stack, frame)) {
                    if (hits == null) {
                        hits = new ArrayList<>(2);
                    }
                    hits.add(breakpoint.id());
                }
            }
            if (hits != null) {
                reason = PauseReason.BREAKPOINT;
            }
        }

        if (reason == null && stack.step != ShadowStack.StepMode.NONE) {
            final int depth = stack.depth();
            final boolean stop = switch (stack.step) {
                case INTO -> true;
                case OVER -> depth <= stack.stepDepth;
                case OUT -> depth < stack.stepDepth;
                default -> false;
            };
            if (stop) {
                reason = PauseReason.STEP;
            }
        }

        if (reason == null && debugger.consumePauseRequest()) {
            reason = PauseReason.DEBUG_COMMAND;
        }

        if (reason != null) {
            pause(stack, debugger, reason, hits, null);
        }
    }

    /**
     * A {@code debugger} statement: pauses if a debugger is listening, and is
     * nothing otherwise, as the specification allows.
     */
    public static void debuggerStatement() {
        if (!attached) {
            return;
        }
        final ShadowStack stack = ShadowStack.current();
        if (stack.top() == null || stack.inCommand) {
            return;
        }
        final DebuggerImpl debugger = DebuggerImpl.current();
        if (debugger == null || debugger.skipAllPauses || !debugger.hasListeners()) {
            return;
        }
        pause(stack, debugger, PauseReason.OTHER, null, null);
    }

    /**
     * A script threw, or the runtime made an error for it. Pauses at the
     * throw site when the debugger asks for every exception.
     *
     * @param thrown the thrown value
     */
    public static void exceptionThrown(final Object thrown) {
        if (!interesting) {
            return;
        }
        final ShadowStack stack = ShadowStack.current();
        if (stack.top() == null || stack.inCommand) {
            return;
        }
        final DebuggerImpl debugger = DebuggerImpl.current();
        if (debugger == null || debugger.skipAllPauses) {
            return;
        }
        final PauseOnExceptions mode = debugger.pauseOnExceptions;
        if (mode == PauseOnExceptions.ALL || mode == PauseOnExceptions.CAUGHT) {
            stack.pausedThrown = thrown;
            pause(stack, debugger, PauseReason.EXCEPTION, null, thrown);
        }
    }

    /**
     * Pauses the current thread until a resuming command arrives, running the
     * commands it is handed meanwhile.
     */
    static void pause(final ShadowStack stack, final DebuggerImpl debugger, final PauseReason reason,
            final List<String> hits, final Object exception) {
        stack.clearStep();
        final PausedEventImpl event = new PausedEventImpl(debugger, stack, reason, hits, exception);
        stack.paused = event;
        debugger.pauseStarted(event);
        try {
            debugger.firePaused(event);
            while (!event.isResumed()) {
                final Runnable command;
                try {
                    command = event.take();
                } catch (final InterruptedException e) {
                    event.resume();
                    Thread.currentThread().interrupt();
                    break;
                }
                stack.inCommand = true;
                try {
                    command.run();
                } finally {
                    stack.inCommand = false;
                }
            }
        } finally {
            stack.paused = null;
            event.drain();
            debugger.pauseEnded(event);
            debugger.fireResumed(event);
        }
        if (stack.terminating) {
            throw new ScriptTerminated();
        }
    }
}
