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

package org.monflabs.nashorn.debugger.ui.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.monflabs.nashorn.debugger.ui.cdp.CdpConnection;
import org.monflabs.nashorn.debugger.ui.cdp.CdpException;
import org.monflabs.nashorn.debugger.ui.cdp.Json;

/**
 * The debugger's state and the protocol logic over a {@link CdpConnection}: it
 * tracks scripts, breakpoints, the paused call stack and the console, drives
 * the wire, and tells {@link SessionListener}s what changed. It is not itself a
 * UI - the UI is a set of listeners - so it can be tested headless against a
 * real engine and server.
 *
 * <p><b>Threading.</b> All state lives on a single {@link Executor} handed to
 * the constructor (the EDT in the application, a same-thread executor in
 * tests); every public method must be called on it, every listener callback
 * runs on it, and results of the connection's calls are marshalled back onto
 * it. The connection's own callbacks - events, close - arrive on a WebSocket
 * thread and are trampolined onto the executor at once. Nothing else touches
 * this object.
 *
 * <p><b>CDP invariants it honours.</b> Breakpoints are set by url, never by
 * script id, so they survive the playground re-parsing {@code main.js} on every
 * run; the server resets a client's breakpoints on disconnect, so every attach
 * re-arms them. Object ids and call-frame ids live only until the next resume,
 * so a {@code pauseSerial} is captured with each fetch and stale results are
 * dropped. A detach while paused resumes first, or the script would hang with
 * no client to release it.
 */
public final class DebugSession {

    /** The session's lifecycle. */
    public enum State {
        /** Not connected. */
        DETACHED,
        /** Connecting and enabling the domains. */
        CONNECTING,
        /** Connected and the script is running (or idle, waiting to). */
        RUNNING,
        /** Connected and the script is paused. */
        PAUSED
    }

    /** What a UI listens to. Every method has a default; all run on the session's executor. */
    public interface SessionListener {
        /** The state changed. @param state the new state */
        default void stateChanged(State state) { }
        /** A script was parsed (or replayed on attach). @param script the script */
        default void scriptAdded(ScriptInfo script) { }
        /** The engine paused. @param pause where and why */
        default void paused(PauseState pause) { }
        /** The engine resumed. */
        default void resumed() { }
        /** The selected frame changed. @param index the frame index */
        default void frameSelected(int index) { }
        /** The breakpoint set changed. @param breakpoints the current breakpoints */
        default void breakpointsChanged(List<Breakpoint> breakpoints) { }
        /** A console line arrived. @param entry the line */
        default void consoleEntry(ConsoleEntry entry) { }
        /** The connection ended. @param reason why */
        default void connectionClosed(String reason) { }
    }

    private final Executor ui;
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, ScriptInfo> scriptsById = new LinkedHashMap<>();
    private final Map<String, String> sourceCache = new LinkedHashMap<>();
    // breakpoints keyed by url:line, the client-owned identity
    private final Map<String, Breakpoint> breakpoints = new LinkedHashMap<>();
    private final List<String> watches = new ArrayList<>();

    private CdpConnection connection;
    private State state = State.DETACHED;
    private String attachedUrl;
    private PauseState pauseState;
    private int selectedFrame;
    private int pauseSerial;
    private String pauseOnExceptions = "none";
    private boolean breakpointsActive = true;

    /**
     * Creates a session.
     * @param ui the executor that owns all state and runs all callbacks
     */
    public DebugSession(final Executor ui) {
        this.ui = ui;
    }

    // ---- listeners ----

    /** Adds a listener. @param listener the listener */
    public void addListener(final SessionListener listener) {
        listeners.add(listener);
    }

    /** Removes a listener. @param listener the listener */
    public void removeListener(final SessionListener listener) {
        listeners.remove(listener);
    }

    // ---- lifecycle ----

    /**
     * Attaches to a server. Idempotent per url while connected; a different url
     * detaches first.
     * @param wsUrl the {@code ws://...} url
     */
    public void attach(final String wsUrl) {
        if (state != State.DETACHED) {
            if (wsUrl.equals(attachedUrl)) {
                return;
            }
            detach();
        }
        attachedUrl = wsUrl;
        setState(State.CONNECTING);
        CdpConnection.connect(wsUrl, new ConnectionListener()).whenComplete((conn, error) ->
                run(() -> {
                    if (error != null) {
                        final String message = error.getCause() instanceof CdpException cdp ? cdp.getMessage() : String.valueOf(error.getMessage());
                        onClosed(message);
                        return;
                    }
                    connection = conn;
                    enableAndArm();
                }));
    }

    private void enableAndArm() {
        connection.call("Runtime.enable", null);
        connection.call("Debugger.enable", null).whenComplete((result, error) -> run(() -> {
            if (error != null) {
                onClosed(String.valueOf(error.getMessage()));
                return;
            }
            // re-arm what the server forgot on the last disconnect
            for (final Breakpoint bp : new ArrayList<>(breakpoints.values())) {
                if (bp.enabled()) {
                    armOnServer(bp);
                }
            }
            if (!"none".equals(pauseOnExceptions)) {
                connection.call("Debugger.setPauseOnExceptions", Json.object("state", pauseOnExceptions));
            }
            if (!breakpointsActive) {
                connection.call("Debugger.setBreakpointsActive", Json.object("active", false));
            }
            setState(State.RUNNING);
        }));
    }

    /** Detaches: resumes first if paused, then closes the connection. */
    public void detach() {
        if (connection == null) {
            return;
        }
        if (state == State.PAUSED) {
            connection.call("Debugger.resume", null);
        }
        final CdpConnection closing = connection;
        connection = null;
        closing.close();
        forgetServerState();
        attachedUrl = null;
        setState(State.DETACHED);
    }

    /** Detaches and releases everything; the session is done. */
    public void close() {
        detach();
        listeners.clear();
    }

    // ---- execution control ----

    /** Resumes a paused engine. */
    public void resume() {
        send("Debugger.resume", null);
    }

    /** Pauses a running engine at its next statement. */
    public void pause() {
        send("Debugger.pause", null);
    }

    /** Steps over the current line. */
    public void stepOver() {
        send("Debugger.stepOver", null);
    }

    /** Steps into a call on the current line. */
    public void stepInto() {
        send("Debugger.stepInto", null);
    }

    /** Steps out of the current function. */
    public void stepOut() {
        send("Debugger.stepOut", null);
    }

    // ---- breakpoints ----

    /**
     * Adds or removes the breakpoint at a position.
     * @param url the script url
     * @param line the line, zero based
     */
    public void toggleBreakpoint(final String url, final int line) {
        final String key = url + ":" + line;
        if (breakpoints.containsKey(key)) {
            removeBreakpoint(url, line);
        } else {
            final Breakpoint bp = new Breakpoint(url, line, null, true, null, List.of());
            breakpoints.put(key, bp);
            armOnServer(bp);
            fireBreakpoints();
        }
    }

    /**
     * Removes the breakpoint at a position, if any.
     * @param url the script url
     * @param line the line, zero based
     */
    public void removeBreakpoint(final String url, final int line) {
        final Breakpoint bp = breakpoints.remove(url + ":" + line);
        if (bp != null) {
            disarmOnServer(bp);
            fireBreakpoints();
        }
    }

    /**
     * Sets or clears a breakpoint's condition.
     * @param url the script url
     * @param line the line, zero based
     * @param condition the condition, or null to clear it
     */
    public void setBreakpointCondition(final String url, final int line, final String condition) {
        final Breakpoint bp = breakpoints.get(url + ":" + line);
        if (bp == null) {
            return;
        }
        disarmOnServer(bp);
        final Breakpoint updated = bp.withCondition(condition == null || condition.isEmpty() ? null : condition);
        breakpoints.put(updated.key(), updated);
        if (updated.enabled()) {
            armOnServer(updated);
        }
        fireBreakpoints();
    }

    /**
     * Enables or disables a breakpoint without forgetting it.
     * @param url the script url
     * @param line the line, zero based
     * @param on whether to arm it
     */
    public void setBreakpointEnabled(final String url, final int line, final boolean on) {
        final Breakpoint bp = breakpoints.get(url + ":" + line);
        if (bp == null || bp.enabled() == on) {
            return;
        }
        final Breakpoint updated = bp.withEnabled(on);
        breakpoints.put(updated.key(), updated);
        if (on) {
            armOnServer(updated);
        } else {
            disarmOnServer(updated);
        }
        fireBreakpoints();
    }

    /**
     * Arms or disarms all breakpoints at once (Chrome's "deactivate breakpoints").
     * @param active whether breakpoints pause
     */
    public void setBreakpointsActive(final boolean active) {
        breakpointsActive = active;
        send("Debugger.setBreakpointsActive", Json.object("active", active));
    }

    /**
     * Chooses when to pause on exceptions.
     * @param mode {@code "none"}, {@code "uncaught"} or {@code "all"}
     */
    public void setPauseOnExceptions(final String mode) {
        pauseOnExceptions = mode;
        send("Debugger.setPauseOnExceptions", Json.object("state", mode));
    }

    private void armOnServer(final Breakpoint bp) {
        if (connection == null) {
            return;
        }
        final Map<String, Object> params = Json.object("url", bp.url(), "lineNumber", bp.line());
        if (bp.condition() != null) {
            params.put("condition", bp.condition());
        }
        connection.call("Debugger.setBreakpointByUrl", params).whenComplete((result, error) -> run(() -> {
            if (error != null || result == null) {
                return;
            }
            final Breakpoint current = breakpoints.get(bp.key());
            if (current == null) {
                return;
            }
            breakpoints.put(bp.key(), current.resolvedAs(str(result.get("breakpointId")), resolvedLines(result)));
            fireBreakpoints();
        }));
    }

    private void disarmOnServer(final Breakpoint bp) {
        if (connection != null && bp.serverId() != null) {
            connection.call("Debugger.removeBreakpoint", Json.object("breakpointId", bp.serverId()));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> resolvedLines(final Map<String, Object> result) {
        final Object locations = result.get("locations");
        final List<Integer> lines = new ArrayList<>();
        if (locations instanceof List<?> list) {
            for (final Object loc : list) {
                lines.add(((Number)((Map<String, Object>)loc).get("lineNumber")).intValue());
            }
        }
        return lines;
    }

    // ---- frames, values, source ----

    /**
     * Selects a call frame; scopes and evaluation then use it.
     * @param index the frame index
     */
    public void selectFrame(final int index) {
        if (pauseState != null && index >= 0 && index < pauseState.frames().size()) {
            selectedFrame = index;
            for (final SessionListener l : listeners) {
                l.frameSelected(index);
            }
        }
    }

    /**
     * Evaluates an expression: on the selected frame when paused, in the global
     * context when running.
     * @param expression the expression
     * @return a future for the resulting value
     */
    public CompletableFuture<RemoteValue> evaluate(final String expression) {
        if (connection == null) {
            return CompletableFuture.failedFuture(new CdpException(CdpException.TRANSPORT_CLOSED, "not connected"));
        }
        final int serial = pauseSerial;
        final CompletableFuture<Map<String, Object>> call;
        if (state == State.PAUSED && pauseState != null && selectedFrame < pauseState.frames().size()) {
            call = connection.call("Debugger.evaluateOnCallFrame", Json.object(
                    "callFrameId", pauseState.frames().get(selectedFrame).callFrameId(),
                    "expression", expression,
                    "objectGroup", "console",
                    "generatePreview", Boolean.TRUE));
        } else {
            call = connection.call("Runtime.evaluate", Json.object(
                    "expression", expression,
                    "objectGroup", "console",
                    "generatePreview", Boolean.TRUE));
        }
        final CompletableFuture<RemoteValue> result = new CompletableFuture<>();
        call.whenComplete((response, error) -> run(() -> {
            if (error != null) {
                result.completeExceptionally(error instanceof Exception e ? e : new RuntimeException(error));
            } else if (state == State.PAUSED && serial != pauseSerial) {
                result.completeExceptionally(new CdpException(CdpException.TRANSPORT_CLOSED, "stale: the engine resumed"));
            } else {
                result.complete(RemoteValue.of(mapOf(response.get("result"))));
            }
        }));
        return result;
    }

    /**
     * Fetches an object's own properties.
     * @param objectId the object's id, from a {@link RemoteValue}
     * @return a future for the properties; empty if the id has gone stale
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<PropertyEntry>> properties(final String objectId) {
        if (connection == null || objectId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        final int serial = pauseSerial;
        final CompletableFuture<List<PropertyEntry>> result = new CompletableFuture<>();
        connection.call("Runtime.getProperties", Json.object(
                "objectId", objectId,
                "ownProperties", Boolean.TRUE,
                "generatePreview", Boolean.FALSE)).whenComplete((response, error) -> run(() -> {
            if (error != null || serial != pauseSerial) {
                result.complete(List.of());
                return;
            }
            final List<PropertyEntry> entries = new ArrayList<>();
            final Object result0 = response.get("result");
            if (result0 instanceof List<?> list) {
                for (final Object o : list) {
                    final Map<String, Object> pd = (Map<String, Object>)o;
                    final RemoteValue value = RemoteValue.of(mapOf(pd.get("value")));
                    entries.add(new PropertyEntry(
                            str(pd.get("name")),
                            value,
                            truthy(pd.get("isOwn")),
                            truthy(pd.get("enumerable")),
                            truthy(pd.get("wasThrown"))));
                }
            }
            result.complete(entries);
        }));
        return result;
    }

    /**
     * Fetches a script's source, cached for the connection.
     * @param scriptId the script id
     * @return a future for the source
     */
    public CompletableFuture<String> scriptSource(final String scriptId) {
        final String cached = sourceCache.get(scriptId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (connection == null) {
            return CompletableFuture.completedFuture("");
        }
        final CompletableFuture<String> result = new CompletableFuture<>();
        connection.call("Debugger.getScriptSource", Json.object("scriptId", scriptId)).whenComplete((response, error) -> run(() -> {
            final String source = error != null || response == null ? "" : str(response.get("scriptSource"));
            if (error == null) {
                sourceCache.put(scriptId, source == null ? "" : source);
            }
            result.complete(source == null ? "" : source);
        }));
        return result;
    }

    // ---- watches ----

    /** Adds a watch expression, re-evaluated at each pause. @param expression the expression */
    public void addWatch(final String expression) {
        if (!watches.contains(expression)) {
            watches.add(expression);
        }
    }

    /** Removes a watch expression. @param expression the expression */
    public void removeWatch(final String expression) {
        watches.remove(expression);
    }

    /** The current watch expressions, in order. @return the expressions */
    public List<String> watches() {
        return List.copyOf(watches);
    }

    // ---- getters ----

    /** The current state. @return the state */
    public State state() {
        return state;
    }

    /** The current pause, or null when not paused. @return the pause */
    public PauseState pauseState() {
        return pauseState;
    }

    /** The selected frame index. @return the index */
    public int selectedFrame() {
        return selectedFrame;
    }

    /** The scripts known, newest last. @return the scripts */
    public List<ScriptInfo> scripts() {
        return List.copyOf(scriptsById.values());
    }

    /** The breakpoints, in insertion order. @return the breakpoints */
    public List<Breakpoint> breakpoints() {
        return List.copyOf(breakpoints.values());
    }

    /** The breakpoints on a given url. @param url the url @return its breakpoints */
    public List<Breakpoint> breakpointsFor(final String url) {
        final List<Breakpoint> result = new ArrayList<>();
        for (final Breakpoint bp : breakpoints.values()) {
            if (bp.url().equals(url)) {
                result.add(bp);
            }
        }
        return result;
    }

    /** The url currently attached, or null. @return the url */
    public String attachedUrl() {
        return attachedUrl;
    }

    // ---- event handling ----

    private final class ConnectionListener implements CdpConnection.Listener {
        @Override
        public void onEvent(final String method, final Map<String, Object> params) {
            run(() -> handleEvent(method, params));
        }

        @Override
        public void onClosed(final String reason) {
            run(() -> DebugSession.this.onClosed(reason));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(final String method, final Map<String, Object> params) {
        switch (method) {
        case "Debugger.scriptParsed": {
            final ScriptInfo script = new ScriptInfo(
                    str(params.get("scriptId")),
                    str(params.get("url")),
                    intOf(params.get("endLine")),
                    truthy(params.get("isModule")));
            scriptsById.put(script.scriptId(), script);
            for (final SessionListener l : listeners) {
                l.scriptAdded(script);
            }
            break;
        }
        case "Debugger.breakpointResolved": {
            final String serverId = str(params.get("breakpointId"));
            final Map<String, Object> location = mapOf(params.get("location"));
            for (final Map.Entry<String, Breakpoint> e : breakpoints.entrySet()) {
                if (serverId.equals(e.getValue().serverId()) && location != null) {
                    final List<Integer> lines = new ArrayList<>(e.getValue().resolvedLines());
                    lines.add(intOf(location.get("lineNumber")));
                    e.setValue(e.getValue().resolvedAs(serverId, lines));
                    fireBreakpoints();
                    break;
                }
            }
            break;
        }
        case "Debugger.paused": {
            pauseSerial++;
            pauseState = readPause(params);
            selectedFrame = 0;
            setState(State.PAUSED);
            for (final SessionListener l : listeners) {
                l.paused(pauseState);
            }
            break;
        }
        case "Debugger.resumed": {
            pauseSerial++;
            pauseState = null;
            setState(State.RUNNING);
            for (final SessionListener l : listeners) {
                l.resumed();
            }
            break;
        }
        case "Runtime.consoleAPICalled": {
            final Object args = params.get("args");
            final StringBuilder text = new StringBuilder();
            if (args instanceof List<?> list) {
                for (final Object a : list) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    final RemoteValue value = RemoteValue.of((Map<String, Object>)a);
                    text.append(value == null ? "undefined" : consoleText(value));
                }
            }
            final boolean error = "error".equals(params.get("type")) || "warning".equals(params.get("type"));
            fireConsole(new ConsoleEntry(error ? ConsoleEntry.Kind.ERROR : ConsoleEntry.Kind.LOG, text.toString()));
            break;
        }
        case "Runtime.exceptionThrown": {
            final Map<String, Object> details = mapOf(params.get("exceptionDetails"));
            String text = "Uncaught";
            if (details != null) {
                final RemoteValue exception = RemoteValue.of(mapOf(details.get("exception")));
                text = exception != null ? exception.display() : str(details.get("text"));
            }
            fireConsole(new ConsoleEntry(ConsoleEntry.Kind.ERROR, text));
            break;
        }
        default:
            break;
        }
    }

    /** A console arg's text: a bare string without the display quotes, else display(). */
    private static String consoleText(final RemoteValue value) {
        if ("string".equals(value.type())) {
            return String.valueOf(value.value());
        }
        return value.display();
    }

    @SuppressWarnings("unchecked")
    private PauseState readPause(final Map<String, Object> params) {
        final List<CallFrame> frames = new ArrayList<>();
        final Object callFrames = params.get("callFrames");
        if (callFrames instanceof List<?> list) {
            for (final Object o : list) {
                frames.add(readFrame((Map<String, Object>)o));
            }
        }
        final List<String> hit = new ArrayList<>();
        if (params.get("hitBreakpoints") instanceof List<?> list) {
            for (final Object id : list) {
                hit.add(String.valueOf(id));
            }
        }
        final RemoteValue exception = RemoteValue.of(mapOf(params.get("data")));
        return new PauseState(str(params.get("reason")), frames, hit, exception);
    }

    @SuppressWarnings("unchecked")
    private CallFrame readFrame(final Map<String, Object> frame) {
        final Map<String, Object> location = mapOf(frame.get("location"));
        final List<Scope> scopes = new ArrayList<>();
        if (frame.get("scopeChain") instanceof List<?> list) {
            for (final Object o : list) {
                final Map<String, Object> s = (Map<String, Object>)o;
                scopes.add(new Scope(str(s.get("type")), str(s.get("name")), RemoteValue.of(mapOf(s.get("object")))));
            }
        }
        return new CallFrame(
                str(frame.get("callFrameId")),
                str(frame.get("functionName")),
                str(frame.get("url")),
                location == null ? null : str(location.get("scriptId")),
                location == null ? 0 : intOf(location.get("lineNumber")),
                location == null ? 0 : intOf(location.get("columnNumber")),
                scopes,
                RemoteValue.of(mapOf(frame.get("this"))));
    }

    private void onClosed(final String reason) {
        connection = null;
        forgetServerState();
        final boolean wasConnected = state != State.DETACHED;
        attachedUrl = null;
        setState(State.DETACHED);
        if (wasConnected) {
            for (final SessionListener l : listeners) {
                l.connectionClosed(reason);
            }
        }
    }

    /** Drops everything the server owns; keeps the client's breakpoint definitions. */
    private void forgetServerState() {
        scriptsById.clear();
        sourceCache.clear();
        pauseState = null;
        selectedFrame = 0;
        // keep breakpoint identities and conditions; drop server ids and resolutions
        for (final Map.Entry<String, Breakpoint> e : breakpoints.entrySet()) {
            e.setValue(e.getValue().resolvedAs(null, List.of()));
        }
    }

    // ---- helpers ----

    private void send(final String method, final Map<String, Object> params) {
        if (connection != null) {
            connection.call(method, params);
        }
    }

    private void setState(final State newState) {
        if (state != newState) {
            state = newState;
            for (final SessionListener l : listeners) {
                l.stateChanged(newState);
            }
        }
    }

    private void fireBreakpoints() {
        final List<Breakpoint> snapshot = List.copyOf(breakpoints.values());
        for (final SessionListener l : listeners) {
            l.breakpointsChanged(snapshot);
        }
    }

    private void fireConsole(final ConsoleEntry entry) {
        for (final SessionListener l : listeners) {
            l.consoleEntry(entry);
        }
    }

    private void run(final Runnable action) {
        ui.execute(action);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(final Object o) {
        return o instanceof Map ? (Map<String, Object>)o : null;
    }

    private static String str(final Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(final Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static boolean truthy(final Object o) {
        return Boolean.TRUE.equals(o);
    }
}
