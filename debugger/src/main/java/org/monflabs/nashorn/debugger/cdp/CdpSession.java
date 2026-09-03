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

package org.monflabs.nashorn.debugger.cdp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.monflabs.nashorn.api.debugger.Breakpoint;
import org.monflabs.nashorn.api.debugger.ConsoleEvent;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.DebugScope;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.ExceptionEvent;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.api.debugger.PauseReason;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.debugger.json.Json;
import org.monflabs.nashorn.debugger.ws.WebSocketConnection;

/**
 * One client's conversation: requests in, responses and events out. Requests
 * are handled on the connection's reader thread; whatever must touch script
 * objects while a thread is paused is handed to that thread through the
 * pause, and while nothing is paused runs on the reader thread with the
 * realm bound.
 */
public final class CdpSession implements DebugListener {
    private final Debugger debugger;
    private final WebSocketConnection connection;
    private final Runnable onRunIfWaiting;
    private final String uniqueId;
    final RemoteObjects objects;
    private final DebuggerDomain debuggerDomain;
    private final RuntimeDomain runtimeDomain;

    volatile boolean debuggerEnabled;
    volatile boolean runtimeEnabled;
    private volatile PausedEvent pause;
    private volatile int pauseSerial;

    /**
     * Creates a session.
     * @param debugger the debugger
     * @param connection the connection
     * @param uniqueId the server's id, used in the ids handed to the client
     * @param onRunIfWaiting what to do when the client asks execution to proceed
     */
    public CdpSession(final Debugger debugger, final WebSocketConnection connection, final String uniqueId, final Runnable onRunIfWaiting) {
        this.debugger = debugger;
        this.connection = connection;
        this.uniqueId = uniqueId;
        this.onRunIfWaiting = onRunIfWaiting;
        this.objects = new RemoteObjects(debugger.values());
        this.debuggerDomain = new DebuggerDomain(this);
        this.runtimeDomain = new RuntimeDomain(this);
    }

    Debugger debugger() {
        return debugger;
    }

    String uniqueId() {
        return uniqueId;
    }

    /**
     * Runs the session until the connection closes.
     */
    public void run() {
        debugger.addListener(this);
        try {
            connection.run(this::onMessage);
        } catch (final IOException e) {
            // the client went away
        } finally {
            debugger.removeListener(this);
            final PausedEvent current = pause;
            if (current != null) {
                current.resume();
            }
            debuggerDomain.detach();
            objects.clear();
        }
    }

    private void onMessage(final String text) {
        Object id = null;
        try {
            final Object parsed;
            try {
                parsed = Json.parse(text);
            } catch (final IllegalArgumentException e) {
                throw new CdpError(CdpError.PARSE_ERROR, "Message must be valid JSON");
            }
            if (!(parsed instanceof Map<?, ?> message)) {
                throw new CdpError(CdpError.INVALID_REQUEST, "Message must be an object");
            }
            id = message.get("id");
            final Object method = message.get("method");
            if (!(method instanceof String name)) {
                throw new CdpError(CdpError.INVALID_REQUEST, "Message must have string 'method' property");
            }
            final Map<String, Object> result = dispatch(name, new Params(message.get("params")));
            if (id != null) {
                send(Json.object("id", id, "result", result == null ? Json.object() : result));
            }
        } catch (final CdpError e) {
            if (id != null) {
                send(Json.object("id", id, "error", Json.object("code", (long)e.code(), "message", e.getMessage())));
            }
        } catch (final RuntimeException | Error e) {
            if (id != null) {
                send(Json.object("id", id, "error", Json.object("code", (long)CdpError.SERVER_ERROR, "message", String.valueOf(e))));
            }
        }
    }

    private Map<String, Object> dispatch(final String method, final Params params) throws CdpError {
        final int dot = method.indexOf('.');
        final String domain = dot < 0 ? method : method.substring(0, dot);
        final String name = dot < 0 ? "" : method.substring(dot + 1);
        try {
            switch (domain) {
            case "Debugger":
                return debuggerDomain.handle(name, params);
            case "Runtime":
                return runtimeDomain.handle(name, params);
            default:
                throw new CdpError(CdpError.METHOD_NOT_FOUND, "'" + method + "' wasn't found");
            }
        } catch (final CdpError e) {
            throw e;
        } catch (final Exception e) {
            throw new CdpError(CdpError.SERVER_ERROR, String.valueOf(e.getMessage() == null ? e : e.getMessage()));
        }
    }

    void send(final Map<String, Object> message) {
        try {
            connection.send(Json.write(message));
        } catch (final IOException e) {
            connection.close(1011, "cannot send");
        }
    }

    void sendEvent(final String method, final Map<String, Object> params) {
        send(Json.object("method", method, "params", params));
    }

    void runIfWaitingForDebugger() {
        onRunIfWaiting.run();
    }

    // -- the pause ------------------------------------------------------------

    PausedEvent pause() {
        return pause;
    }

    PausedEvent requirePause() throws CdpError {
        final PausedEvent current = pause;
        if (current == null || current.isResumed()) {
            throw CdpError.notPaused();
        }
        return current;
    }

    String callFrameId(final int index) {
        return index + "." + pauseSerial;
    }

    DebugFrame frame(final String callFrameId) throws CdpError {
        final PausedEvent current = requirePause();
        final int dot = callFrameId.indexOf('.');
        try {
            final int index = Integer.parseInt(dot < 0 ? callFrameId : callFrameId.substring(0, dot));
            if (dot >= 0 && Integer.parseInt(callFrameId.substring(dot + 1)) != pauseSerial) {
                throw CdpError.notPaused();
            }
            return current.frames().get(index);
        } catch (final NumberFormatException | IndexOutOfBoundsException e) {
            throw CdpError.invalidParams("callFrameId " + callFrameId);
        }
    }

    /**
     * Runs an operation where it may touch the context's objects: on the
     * paused thread when there is one, else here with the realm bound.
     */
    <T> T inContext(final ExecutionContext context, final Callable<T> operation) throws Exception {
        final PausedEvent current = pause;
        if (current != null && !current.isResumed()) {
            return current.call(operation);
        }
        final ExecutionContext ctx = context != null ? context : defaultContext();
        if (ctx == null) {
            return operation.call();
        }
        return debugger.call(ctx, operation);
    }

    ExecutionContext defaultContext() {
        final PausedEvent current = pause;
        if (current != null && current.context() != null) {
            return current.context();
        }
        final List<ExecutionContext> contexts = debugger.executionContexts();
        return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
    }

    ExecutionContext context(final int id) throws CdpError {
        for (final ExecutionContext ctx : debugger.executionContexts()) {
            if (ctx.id() == id) {
                return ctx;
            }
        }
        throw CdpError.invalidParams("unknown executionContextId " + id);
    }

    // -- events from the debugger ----------------------------------------------

    static Map<String, Object> contextDescription(final ExecutionContext ctx, final String uniqueId) {
        return Json.object("id", (long)ctx.id(), "origin", "", "name", ctx.name(),
                "uniqueId", uniqueId + "." + ctx.id(), "auxData", Json.object("isDefault", true));
    }

    @Override
    public void executionContextCreated(final ExecutionContext context) {
        if (runtimeEnabled) {
            sendEvent("Runtime.executionContextCreated", Json.object("context", contextDescription(context, uniqueId)));
        }
    }

    @Override
    public void executionContextsCleared() {
        if (runtimeEnabled) {
            sendEvent("Runtime.executionContextsCleared", Json.object());
        }
    }

    static Map<String, Object> scriptParsedParams(final DebugScript script) {
        final Map<String, Object> params = Json.object(
                "scriptId", script.id(),
                "url", script.url(),
                "startLine", 0L, "startColumn", 0L,
                "endLine", (long)script.endLine(), "endColumn", (long)script.endColumn(),
                "executionContextId", (long)(script.context() == null ? 0 : script.context().id()),
                "hash", script.hash(),
                "isModule", script.isModule(),
                "length", (long)script.length(),
                "hasSourceURL", false);
        return params;
    }

    @Override
    public void scriptParsed(final DebugScript script) {
        if (debuggerEnabled) {
            sendEvent("Debugger.scriptParsed", scriptParsedParams(script));
        }
    }

    @Override
    public void breakpointResolved(final Breakpoint breakpoint, final Location location) {
        if (debuggerEnabled) {
            sendEvent("Debugger.breakpointResolved", Json.object("breakpointId", breakpoint.id(), "location", RemoteObjects.location(location)));
        }
    }

    private volatile boolean terminateOnPause;

    /** Runtime.terminateExecution while running: the next pause ends the script instead of reporting. */
    void terminateOnNextPause() {
        terminateOnPause = true;
    }

    @Override
    public void paused(final PausedEvent event) {
        if (terminateOnPause) {
            terminateOnPause = false;
            event.terminate();
            return;
        }
        if (!debuggerEnabled) {
            return;
        }
        if (debuggerDomain.consumeTemporaryBreakpoint(event)) {
            // continueToLocation's own breakpoint, or another pause on the way
        }
        pause = event;
        pauseSerial++;
        // built here, on the paused thread, which owns the objects it describes
        final List<Object> frames = new ArrayList<>();
        int index = 0;
        for (final DebugFrame frame : event.frames()) {
            frames.add(callFrame(frame, index++));
        }
        final Map<String, Object> params = Json.object("callFrames", frames, "reason", reason(event.reason()), "hitBreakpoints", event.hitBreakpoints());
        if (event.reason() == PauseReason.EXCEPTION) {
            params.put("data", objects.remoteObject(event.exception(), "backtrace", false, false));
        }
        sendEvent("Debugger.paused", params);
    }

    private Map<String, Object> callFrame(final DebugFrame frame, final int index) {
        final List<Object> scopeChain = new ArrayList<>();
        for (final DebugScope scope : frame.scopes()) {
            final Map<String, Object> s = Json.object("type", scope.type().name().toLowerCase(java.util.Locale.ROOT),
                    "object", objects.remoteObject(scope.object(), "backtrace", false, false));
            if (scope.name() != null && !scope.name().isEmpty()) {
                s.put("name", scope.name());
            }
            scopeChain.add(s);
        }
        final Map<String, Object> callFrame = Json.object(
                "callFrameId", callFrameId(index),
                "functionName", frame.functionName(),
                "location", RemoteObjects.location(frame.location()),
                "url", frame.location().script().url(),
                "scopeChain", scopeChain,
                "this", objects.remoteObject(frame.thisValue(), "backtrace", false, false));
        if (frame.functionLocation() != null) {
            callFrame.put("functionLocation", RemoteObjects.location(frame.functionLocation()));
        }
        return callFrame;
    }

    private static String reason(final PauseReason reason) {
        return switch (reason) {
            case EXCEPTION -> "exception";
            case DEBUG_COMMAND -> "debugCommand";
            case STEP -> "step";
            default -> "other";
        };
    }

    @Override
    public void resumed(final PausedEvent event) {
        if (pause == event) {
            pause = null;
            objects.releaseBacktrace();
        }
        if (debuggerEnabled) {
            sendEvent("Debugger.resumed", Json.object());
        }
    }

    @Override
    public void exceptionThrown(final ExceptionEvent event) {
        if (!runtimeEnabled) {
            return;
        }
        final Map<String, Object> details = Json.object(
                "exceptionId", (long)System.identityHashCode(event),
                "text", "Uncaught",
                "lineNumber", event.location() == null ? 0L : (long)event.location().line(),
                "columnNumber", event.location() == null ? 0L : (long)event.location().column(),
                "exception", objects.remoteObject(event.thrown(), "console", false, false));
        if (event.location() != null) {
            details.put("scriptId", event.location().script().id());
            details.put("url", event.location().script().url());
        }
        if (event.context() != null) {
            details.put("executionContextId", (long)event.context().id());
        }
        if (!event.frames().isEmpty()) {
            details.put("stackTrace", stackTrace(event.frames()));
        }
        sendEvent("Runtime.exceptionThrown", Json.object("timestamp", (double)System.currentTimeMillis(), "exceptionDetails", details));
    }

    private static Map<String, Object> stackTrace(final List<DebugFrame> frames) {
        final List<Object> callFrames = new ArrayList<>();
        for (final DebugFrame frame : frames) {
            final Location l = frame.location();
            callFrames.add(Json.object("functionName", frame.functionName(), "scriptId", l.script().id(), "url", l.script().url(),
                    "lineNumber", (long)l.line(), "columnNumber", (long)l.column()));
        }
        return Json.object("callFrames", callFrames);
    }

    @Override
    public void consoleCalled(final ConsoleEvent event) {
        if (!runtimeEnabled) {
            return;
        }
        final List<Object> args = new ArrayList<>();
        for (final Object arg : event.arguments()) {
            args.add(objects.remoteObject(arg, "console", false, true));
        }
        final Map<String, Object> params = Json.object("type", event.type(), "args", args,
                "executionContextId", (long)(event.context() == null ? 0 : event.context().id()),
                "timestamp", (double)System.currentTimeMillis());
        if (event.location() != null) {
            params.put("stackTrace", Json.object("callFrames", List.of(Json.object("functionName", "", "scriptId", event.location().script().id(),
                    "url", event.location().script().url(), "lineNumber", (long)event.location().line(), "columnNumber", (long)event.location().column()))));
        }
        sendEvent("Runtime.consoleAPICalled", params);
    }
}
