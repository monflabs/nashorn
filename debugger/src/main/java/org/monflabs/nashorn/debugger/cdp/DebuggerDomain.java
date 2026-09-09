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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.debugger.Breakpoint;
import org.monflabs.nashorn.api.debugger.BreakpointRequest;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.DebugScope;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.api.debugger.PauseOnExceptions;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.debugger.json.Json;

/**
 * The {@code Debugger} domain.
 */
final class DebuggerDomain {
    private final CdpSession session;
    private final List<String> breakpointIds = new ArrayList<>();
    private volatile String temporaryBreakpoint;

    DebuggerDomain(final CdpSession session) {
        this.session = session;
    }

    private Debugger debugger() {
        return session.debugger();
    }

    Map<String, Object> handle(final String method, final Params params) throws Exception {
        switch (method) {
        case "enable": {
            session.debuggerEnabled = true;
            for (final DebugScript script : debugger().scripts()) {
                session.sendEvent("Debugger.scriptParsed", CdpSession.scriptParsedParams(script));
            }
            // A client attaching after a thread already paused (e.g. after
            // pauseOnStart froze the script) missed the fire-once paused event.
            // Replay it from the debugger's durable pause state, through the
            // session's own paused(), so it records the pause for resume and
            // evaluateOnCallFrame exactly as a live pause would. This event goes
            // on the wire before this enable response.
            final PausedEvent current = debugger().currentPause();
            if (current != null) {
                session.paused(current);
            }
            return Json.object("debuggerId", "nashorn-" + session.uniqueId());
        }
        case "disable":
            session.debuggerEnabled = false;
            detach();
            return null;
        case "setBreakpointByUrl": {
            final int line = params.integer("lineNumber");
            final int column = params.integer("columnNumber", -1);
            final String url = params.string("url", null);
            final String urlRegex = params.string("urlRegex", null);
            String scriptId = null;
            if (url == null && urlRegex == null) {
                final String hash = params.string("scriptHash", null);
                if (hash == null) {
                    throw CdpError.invalidParams("one of url, urlRegex or scriptHash is required");
                }
                for (final DebugScript script : debugger().scripts()) {
                    if (hash.equals(script.hash())) {
                        scriptId = script.id();
                    }
                }
                if (scriptId == null) {
                    throw CdpError.invalidParams("no script with hash " + hash);
                }
            }
            final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(url, urlRegex, scriptId, line, column, params.string("condition", null)));
            breakpointIds.add(bp.id());
            return Json.object("breakpointId", bp.id(), "locations", locations(bp.locations()));
        }
        case "setBreakpoint": {
            final Params location = params.object("location");
            final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(null, null, location.string("scriptId"),
                    location.integer("lineNumber"), location.integer("columnNumber", -1), params.string("condition", null)));
            breakpointIds.add(bp.id());
            if (bp.locations().isEmpty()) {
                debugger().removeBreakpoint(bp.id());
                throw new CdpError(CdpError.SERVER_ERROR, "Could not resolve breakpoint");
            }
            return Json.object("breakpointId", bp.id(), "actualLocation", RemoteObjects.location(bp.locations().get(0)));
        }
        case "removeBreakpoint": {
            final String id = params.string("breakpointId");
            breakpointIds.remove(id);
            debugger().removeBreakpoint(id);
            return null;
        }
        case "setBreakpointsActive":
            debugger().setBreakpointsActive(params.bool("active", true));
            return null;
        case "setSkipAllPauses":
            debugger().setSkipAllPauses(params.bool("skip", false));
            return null;
        case "getPossibleBreakpoints": {
            final Params start = params.object("start");
            final DebugScript script = script(start.string("scriptId"));
            final Params end = params.objectOrNull("end");
            final List<Object> found = new ArrayList<>();
            for (final Location l : script.possibleBreakpoints(start.integer("lineNumber"), start.integer("columnNumber", 0),
                    end == null ? -1 : end.integer("lineNumber"), end == null ? -1 : end.integer("columnNumber", -1))) {
                found.add(RemoteObjects.location(l));
            }
            return Json.object("locations", found);
        }
        case "getScriptSource":
            return Json.object("scriptSource", script(params.string("scriptId")).source());
        case "pause":
            debugger().pause();
            return null;
        case "resume":
            session.requirePause().resume();
            return null;
        case "stepInto":
            session.requirePause().stepInto();
            return null;
        case "stepOver":
            session.requirePause().stepOver();
            return null;
        case "stepOut":
            session.requirePause().stepOut();
            return null;
        case "continueToLocation": {
            final Params location = params.object("location");
            final PausedEvent pause = session.requirePause();
            final Breakpoint bp = debugger().setBreakpoint(new BreakpointRequest(null, null, location.string("scriptId"),
                    location.integer("lineNumber"), location.integer("columnNumber", -1), null));
            temporaryBreakpoint = bp.id();
            pause.resume();
            return null;
        }
        case "evaluateOnCallFrame": {
            final DebugFrame frame = session.frame(params.string("callFrameId"));
            final String expression = params.string("expression");
            final String group = params.string("objectGroup", null);
            final boolean byValue = params.bool("returnByValue", false);
            final boolean preview = params.bool("generatePreview", false);
            if (params.bool("throwOnSideEffect", false)) {
                throw new CdpError(CdpError.SERVER_ERROR, "throwOnSideEffect is not supported");
            }
            final PausedEvent pause = session.requirePause();
            return pause.call(() -> {
                try {
                    return Json.object("result", session.objects.remoteObject(frame.evaluate(expression), group, byValue, preview));
                } catch (final DebugException e) {
                    return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
                            "exceptionDetails", session.objects.exceptionDetails(e, group, frame.location(), pause.context() == null ? 0 : pause.context().id()));
                }
            });
        }
        case "setPauseOnExceptions": {
            final String state = params.string("state");
            debugger().setPauseOnExceptions(switch (state) {
                case "all" -> PauseOnExceptions.ALL;
                case "uncaught" -> PauseOnExceptions.UNCAUGHT;
                case "caught" -> PauseOnExceptions.CAUGHT;
                default -> PauseOnExceptions.NONE;
            });
            return null;
        }
        case "setVariableValue": {
            final DebugFrame frame = session.frame(params.string("callFrameId"));
            final int scopeNumber = params.integer("scopeNumber");
            final String name = params.string("variableName");
            final Params newValue = params.object("newValue");
            final PausedEvent pause = session.requirePause();
            pause.call(() -> {
                final List<DebugScope> scopes = frame.scopes();
                if (scopeNumber < 0 || scopeNumber >= scopes.size()) {
                    throw CdpError.invalidParams("scopeNumber " + scopeNumber);
                }
                debugger().values().setProperty(scopes.get(scopeNumber).object(), name, RuntimeDomain.argument(session, newValue));
                return null;
            });
            return null;
        }
        case "setAsyncCallStackDepth", "setBlackboxPatterns", "setBlackboxedRanges", "setBlackboxExecutionContexts",
             "restartFrame", "searchInContent", "setScriptSource", "setReturnValue", "getStackTrace",
             "setInstrumentationBreakpoint", "removeInstrumentationBreakpoint", "setBreakpointOnFunctionCall",
             "pauseOnAsyncCall", "getWasmBytecode", "disassembleWasmModule":
            return null;
        default:
            throw new CdpError(CdpError.METHOD_NOT_FOUND, "'Debugger." + method + "' wasn't found");
        }
    }

    private DebugScript script(final String id) throws CdpError {
        for (final DebugScript script : debugger().scripts()) {
            if (script.id().equals(id)) {
                return script;
            }
        }
        throw CdpError.invalidParams("No script for id: " + id);
    }

    private static List<Object> locations(final List<Location> locations) {
        final List<Object> list = new ArrayList<>();
        for (final Location l : locations) {
            list.add(RemoteObjects.location(l));
        }
        return list;
    }

    /** Removes continueToLocation's breakpoint once any pause happens; tells whether it was the cause. */
    boolean consumeTemporaryBreakpoint(final PausedEvent event) {
        final String id = temporaryBreakpoint;
        if (id == null) {
            return false;
        }
        temporaryBreakpoint = null;
        debugger().removeBreakpoint(id);
        return event.hitBreakpoints().contains(id);
    }

    /** Forgets the client's breakpoints and modes when it goes. */
    void detach() {
        for (final String id : breakpointIds) {
            debugger().removeBreakpoint(id);
        }
        breakpointIds.clear();
        final String temp = temporaryBreakpoint;
        if (temp != null) {
            temporaryBreakpoint = null;
            debugger().removeBreakpoint(temp);
        }
        debugger().setPauseOnExceptions(PauseOnExceptions.NONE);
        debugger().setSkipAllPauses(false);
        debugger().setBreakpointsActive(true);
    }
}
