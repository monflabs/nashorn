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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Breakpoint;
import org.monflabs.nashorn.api.debugger.BreakpointRequest;
import org.monflabs.nashorn.api.debugger.ConsoleEvent;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.ExceptionEvent;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.api.debugger.PauseOnExceptions;
import org.monflabs.nashorn.api.scripting.NashornException;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.ParserException;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * The debugger of one {@link Context}: its scripts, contexts, breakpoints and
 * listeners, and the switches the hooks consult.
 */
public final class DebuggerImpl implements Debugger {
    private static final List<WeakReference<DebuggerImpl>> ALL = new CopyOnWriteArrayList<>();

    private final Context context;
    private final List<DebugListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Source, DebugScriptImpl> scripts = new LinkedHashMap<>();
    private final Map<Global, ExecutionContextImpl> contexts = new WeakHashMap<>();
    private final List<ExecutionContextImpl> contextList = new CopyOnWriteArrayList<>();
    private final Map<String, BreakpointImpl> breakpoints = new LinkedHashMap<>();
    private final List<PausedEventImpl> pauses = new CopyOnWriteArrayList<>();
    private final DebugValuesImpl values = new DebugValuesImpl(this);
    private final AtomicBoolean pauseRequested = new AtomicBoolean();
    private final AtomicBoolean pauseOnStart = new AtomicBoolean();
    private final AtomicInteger stepping = new AtomicInteger();
    private int nextScriptId = 1;
    private int nextContextId = 1;
    private int nextBreakpointId = 1;

    volatile boolean breakpointsActive = true;
    volatile boolean skipAllPauses;
    volatile PauseOnExceptions pauseOnExceptions = PauseOnExceptions.NONE;

    /**
     * Creates the debugger of a context.
     * @param context the context
     */
    public DebuggerImpl(final Context context) {
        this.context = context;
        ALL.add(new WeakReference<>(this));
    }

    /**
     * The debugger of a script engine.
     * @param engine the engine
     * @return the debugger
     * @throws IllegalStateException if the engine is not Nashorn's or has no debugger
     */
    public static Debugger of(final ScriptEngine engine) {
        final Context ctx = Context.getContext(engine);
        if (ctx == null) {
            throw new IllegalStateException("not a Nashorn script engine, or its global has not been created yet");
        }
        final DebuggerImpl debugger = ctx.getDebugger();
        if (debugger == null) {
            throw new IllegalStateException("the engine was created without --debugger");
        }
        return debugger;
    }

    /** The debugger of the context the current thread is running in, or null. */
    static DebuggerImpl current() {
        final Global global = Context.getGlobal();
        return global == null ? null : Context.getContext().getDebugger();
    }

    Context context() {
        return context;
    }

    // -- registry ---------------------------------------------------------

    /**
     * A global object has been created and initialized.
     * @param global the global
     */
    public void globalCreated(final Global global) {
        final ExecutionContextImpl ctx;
        synchronized (this) {
            ctx = new ExecutionContextImpl(nextContextId++, global);
            contexts.put(global, ctx);
            contextList.add(ctx);
        }
        for (final DebugListener l : listeners) {
            l.executionContextCreated(ctx);
        }
    }

    ExecutionContextImpl currentContext() {
        final Global global = Context.getGlobal();
        return global == null ? null : contextFor(global);
    }

    synchronized ExecutionContextImpl contextFor(final Global global) {
        return contexts.get(global);
    }

    /**
     * A source has been compiled, or found compiled. Registers it once.
     * @param source the source
     * @param module whether it is a module
     */
    public void scriptCompiled(final Source source, final boolean module) {
        final DebugScriptImpl script;
        final List<Runnable> resolved = new ArrayList<>();
        synchronized (this) {
            if (scripts.containsKey(source)) {
                return;
            }
            script = new DebugScriptImpl(nextScriptId++, source, ScriptInfo.of(source), currentContext(), module);
            scripts.put(source, script);
            for (final BreakpointImpl bp : breakpoints.values()) {
                if (bp.matches(script)) {
                    final Location location = bp.resolve(script);
                    if (location != null) {
                        resolved.add(() -> fireBreakpointResolved(bp, location));
                    }
                }
            }
        }
        for (final DebugListener l : listeners) {
            l.scriptParsed(script);
        }
        resolved.forEach(Runnable::run);
    }

    synchronized DebugScriptImpl scriptFor(final Source source) {
        DebugScriptImpl script = scripts.get(source);
        if (script == null) {
            // a source the hooks know but compile never announced - register it now
            script = new DebugScriptImpl(nextScriptId++, source, ScriptInfo.of(source), currentContext(), false);
            scripts.put(source, script);
        }
        return script;
    }

    // -- Debugger ---------------------------------------------------------

    @Override
    public void addListener(final DebugListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(final DebugListener listener) {
        listeners.remove(listener);
    }

    @Override
    public List<ExecutionContext> executionContexts() {
        return Collections.unmodifiableList(new ArrayList<>(contextList));
    }

    @Override
    public synchronized List<DebugScript> scripts() {
        return List.copyOf(scripts.values());
    }

    @Override
    public Breakpoint setBreakpoint(final BreakpointRequest request) {
        final BreakpointImpl bp;
        final List<Runnable> resolved = new ArrayList<>();
        synchronized (this) {
            bp = new BreakpointImpl(this, Integer.toString(nextBreakpointId++), request);
            breakpoints.put(bp.id(), bp);
            for (final DebugScriptImpl script : scripts.values()) {
                if (bp.matches(script)) {
                    final Location location = bp.resolve(script);
                    if (location != null) {
                        resolved.add(() -> fireBreakpointResolved(bp, location));
                    }
                }
            }
        }
        recomputeInteresting();
        resolved.forEach(Runnable::run);
        return bp;
    }

    @Override
    public void removeBreakpoint(final String breakpointId) {
        synchronized (this) {
            final BreakpointImpl bp = breakpoints.remove(breakpointId);
            if (bp != null) {
                bp.detach();
            }
        }
        recomputeInteresting();
    }

    @Override
    public void setBreakpointsActive(final boolean active) {
        breakpointsActive = active;
        recomputeInteresting();
    }

    @Override
    public void setSkipAllPauses(final boolean skip) {
        skipAllPauses = skip;
    }

    @Override
    public void setPauseOnExceptions(final PauseOnExceptions mode) {
        pauseOnExceptions = mode == null ? PauseOnExceptions.NONE : mode;
        recomputeInteresting();
    }

    @Override
    public void pause() {
        pauseRequested.set(true);
        recomputeInteresting();
    }

    @Override
    public void pauseOnStart() {
        pauseOnStart.set(true);
        recomputeInteresting();
    }

    boolean consumePauseRequest() {
        return pauseRequested.get() && pauseRequested.compareAndSet(true, false);
    }

    boolean consumePauseOnStart() {
        return pauseOnStart.get() && pauseOnStart.compareAndSet(true, false);
    }

    @Override
    public Object evaluate(final ExecutionContext ctx, final String expression) throws DebugException {
        final Global global = ((ExecutionContextImpl)ctx).globalObject();
        return Context.callWithGlobal(global, () -> evalIn(global, global, expression, global));
    }

    @Override
    public <T> T call(final ExecutionContext ctx, final java.util.concurrent.Callable<T> operation) throws Exception {
        final Global global = ((ExecutionContextImpl)ctx).globalObject();
        return Context.<T, Exception>callWithGlobal(global, operation::call);
    }

    @Override
    public DebugValues values() {
        return values;
    }

    @Override
    public void close() {
        for (final PausedEventImpl pause : pauses) {
            pause.resume();
        }
        synchronized (this) {
            for (final BreakpointImpl bp : breakpoints.values()) {
                bp.detach();
            }
            breakpoints.clear();
        }
        listeners.clear();
        pauseRequested.set(false);
        pauseOnStart.set(false);
        pauseOnExceptions = PauseOnExceptions.NONE;
        skipAllPauses = false;
        recomputeInteresting();
    }

    // -- hooks' view --------------------------------------------------------

    void stepStarted() {
        stepping.incrementAndGet();
        recomputeInteresting();
    }

    void stepEnded() {
        stepping.updateAndGet(n -> n > 0 ? n - 1 : 0);
        recomputeInteresting();
    }

    void pauseStarted(final PausedEventImpl event) {
        pauses.add(event);
    }

    void pauseEnded(final PausedEventImpl event) {
        pauses.remove(event);
        recomputeInteresting();
    }

    private synchronized boolean isInteresting() {
        return pauseRequested.get() || pauseOnStart.get() || stepping.get() > 0
                || pauseOnExceptions != PauseOnExceptions.NONE
                || (breakpointsActive && !breakpoints.isEmpty());
    }

    /** Recomputes the flag every statement hook reads, over every debugger alive. */
    static void recomputeInteresting() {
        boolean any = false;
        for (final WeakReference<DebuggerImpl> ref : ALL) {
            final DebuggerImpl d = ref.get();
            if (d == null) {
                ALL.remove(ref);
            } else if (d.isInteresting()) {
                any = true;
            }
        }
        Hooks.interesting = any;
    }

    // -- events -------------------------------------------------------------

    void firePaused(final PausedEventImpl event) {
        for (final DebugListener l : listeners) {
            l.paused(event);
        }
    }

    void fireResumed(final PausedEventImpl event) {
        for (final DebugListener l : listeners) {
            l.resumed(event);
        }
    }

    private void fireBreakpointResolved(final BreakpointImpl bp, final Location location) {
        for (final DebugListener l : listeners) {
            l.breakpointResolved(bp, location);
        }
    }

    void fireExceptionThrown(final ShadowStack stack, final ECMAException e) {
        if (listeners.isEmpty()) {
            return;
        }
        final PausedEventImpl snapshot = new PausedEventImpl(this, stack, org.monflabs.nashorn.api.debugger.PauseReason.EXCEPTION, null, e.getThrown());
        final List<DebugFrame> frames = snapshot.frames();
        final Location location = frames.isEmpty() ? null : frames.get(0).location();
        final ExceptionEvent event = new ExceptionEvent(e.getThrown(), messageOf(e), location, frames, currentContext());
        for (final DebugListener l : listeners) {
            l.exceptionThrown(event);
        }
    }

    /**
     * A script called a console function.
     * @param type the function's name
     * @param arguments the arguments
     */
    public void consoleCalled(final String type, final Object[] arguments) {
        if (listeners.isEmpty()) {
            return;
        }
        final ShadowStack stack = ShadowStack.current();
        final Frame frame = stack.top();
        Location location = null;
        if (frame != null && frame.site != null) {
            location = new Location(scriptFor(frame.source), frame.site.line, frame.site.column);
        }
        final ConsoleEvent event = new ConsoleEvent(type, List.of(arguments), location, currentContext());
        for (final DebugListener l : listeners) {
            l.consoleCalled(event);
        }
    }

    void fireConsoleError(final String message, final ShadowStack stack) {
        consoleCalled("error", new Object[] { message });
    }

    // -- evaluation -----------------------------------------------------------

    /**
     * Evaluates an expression in a scope, on the calling thread, which must
     * have the global's realm bound.
     */
    static Object evalIn(final Global global, final ScriptObject scope, final String expression, final Object thisValue) throws DebugException {
        try {
            return Context.getContext().eval(scope, expression, thisValue == null ? global : thisValue, ScriptRuntime.UNDEFINED);
        } catch (final ECMAException e) {
            throw new DebugException(messageOf(e), e.getThrown(), e);
        } catch (final ParserException e) {
            throw new DebugException(e.getMessage(), null, e);
        } catch (final NashornException e) {
            throw new DebugException(e.getMessage(), e.getEcmaError(), e);
        }
    }

    static String messageOf(final ECMAException e) {
        final Object thrown = e.getThrown();
        if (thrown instanceof ScriptObject so) {
            final Object name = so.get("name");
            final Object message = so.get("message");
            if (message != ScriptRuntime.UNDEFINED) {
                return (name == ScriptRuntime.UNDEFINED ? "Error" : String.valueOf(name)) + ": " + message;
            }
        }
        return ScriptRuntime.safeToString(thrown);
    }
}
