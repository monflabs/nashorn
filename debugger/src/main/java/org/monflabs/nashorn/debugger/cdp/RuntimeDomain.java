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

package org.monflabs.nashorn.debugger.cdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugProperty;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.debugger.json.Json;

/**
 * The {@code Runtime} domain.
 */
final class RuntimeDomain {
    private final CdpSession session;

    RuntimeDomain(final CdpSession session) {
        this.session = session;
    }

    private Debugger debugger() {
        return session.debugger();
    }

    Map<String, Object> handle(final String method, final Params params) throws Exception {
        switch (method) {
        case "enable":
            session.runtimeEnabled = true;
            for (final ExecutionContext ctx : debugger().executionContexts()) {
                session.sendEvent("Runtime.executionContextCreated", Json.object("context", CdpSession.contextDescription(ctx, session.uniqueId())));
            }
            return null;
        case "disable":
            session.runtimeEnabled = false;
            return null;
        case "evaluate": {
            final String expression = params.string("expression");
            final String group = params.string("objectGroup", null);
            final boolean byValue = params.bool("returnByValue", false);
            final boolean preview = params.bool("generatePreview", false);
            final ExecutionContext ctx = params.has("contextId") ? session.context(params.integer("contextId")) : session.defaultContext();
            final PausedEvent pause = session.pause();
            return session.inContext(ctx, () -> {
                try {
                    final Object value;
                    if (pause != null && !pause.isResumed() && !pause.frames().isEmpty()) {
                        value = pause.frames().get(0).evaluate(expression);
                    } else if (ctx != null) {
                        value = debugger().values().evaluateWith(ctx, expression, null);
                    } else {
                        throw new CdpError(CdpError.SERVER_ERROR, "no execution context yet");
                    }
                    return Json.object("result", session.objects.remoteObject(value, group, byValue, preview));
                } catch (final DebugException e) {
                    return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
                            "exceptionDetails", session.objects.exceptionDetails(e, group, null, ctx == null ? 0 : ctx.id()));
                }
            });
        }
        case "callFunctionOn": {
            final String declaration = params.string("functionDeclaration");
            final String group = params.string("objectGroup", null);
            final boolean byValue = params.bool("returnByValue", false);
            final boolean preview = params.bool("generatePreview", false);
            final Object target = params.has("objectId") ? session.objects.get(params.string("objectId")) : null;
            final ExecutionContext ctx = params.has("executionContextId") ? session.context(params.integer("executionContextId")) : session.defaultContext();
            final List<?> rawArguments = params.list("arguments");
            return session.inContext(ctx, () -> {
                try {
                    final DebugValues values = debugger().values();
                    final Object function = values.evaluateWith(ctx, "(" + declaration + ")", null);
                    final Object[] args = new Object[rawArguments.size()];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = argument(session, new Params(rawArguments.get(i)));
                    }
                    final Object result = values.callFunction(function, target, args);
                    return Json.object("result", session.objects.remoteObject(result, group, byValue, preview));
                } catch (final DebugException e) {
                    return Json.object("result", session.objects.remoteObject(e.thrown(), group, false, false),
                            "exceptionDetails", session.objects.exceptionDetails(e, group, null, ctx == null ? 0 : ctx.id()));
                }
            });
        }
        case "getProperties": {
            final Object object = session.objects.get(params.string("objectId"));
            final boolean own = params.bool("ownProperties", false);
            final boolean accessorsOnly = params.bool("accessorPropertiesOnly", false);
            final boolean preview = params.bool("generatePreview", false);
            final boolean nonIndexed = params.bool("nonIndexedPropertiesOnly", false);
            final String group = params.string("objectGroup", null);
            return session.inContext(null, () -> {
                final DebugValues values = debugger().values();
                final List<Object> result = new ArrayList<>();
                for (final DebugProperty p : values.ownProperties(object, true, !nonIndexed)) {
                    if (accessorsOnly && p.getter() == null && p.setter() == null) {
                        continue;
                    }
                    result.add(session.objects.propertyDescriptor(p, group, preview));
                }
                final Map<String, Object> response = Json.object("result", result);
                if (own && !accessorsOnly) {
                    final List<Object> internal = new ArrayList<>();
                    for (final DebugProperty p : values.internalProperties(object)) {
                        if (p.value() != null) {
                            internal.add(session.objects.internalPropertyDescriptor(p, group));
                        }
                    }
                    response.put("internalProperties", internal);
                }
                return response;
            });
        }
        case "releaseObject":
            session.objects.release(params.string("objectId"));
            return null;
        case "releaseObjectGroup":
            session.objects.releaseGroup(params.string("objectGroup"));
            return null;
        case "runIfWaitingForDebugger":
            session.runIfWaitingForDebugger();
            return null;
        case "getIsolateId":
            return Json.object("id", session.uniqueId());
        case "getHeapUsage": {
            final Runtime rt = Runtime.getRuntime();
            return Json.object("usedSize", (double)(rt.totalMemory() - rt.freeMemory()), "totalSize", (double)rt.totalMemory());
        }
        case "compileScript":
            // parse-only compilation is not offered; the client evaluates instead
            return Json.object();
        case "globalLexicalScopeNames":
            return Json.object("names", List.of());
        case "terminateExecution": {
            final PausedEvent pause = session.pause();
            if (pause != null && !pause.isResumed()) {
                pause.terminate();
            } else {
                debugger().pause();
                session.terminateOnNextPause();
            }
            return null;
        }
        case "discardConsoleEntries", "setCustomObjectFormatterEnabled", "setMaxCallStackSizeToCapture",
             "setAsyncCallStackDepth", "addBinding", "removeBinding":
            return null;
        default:
            throw new CdpError(CdpError.METHOD_NOT_FOUND, "'Runtime." + method + "' wasn't found");
        }
    }

    /** A {@code Runtime.CallArgument} as an engine value. */
    static Object argument(final CdpSession session, final Params argument) throws CdpError {
        if (argument.has("objectId")) {
            return session.objects.get(argument.string("objectId"));
        }
        if (argument.has("unserializableValue")) {
            return switch (argument.string("unserializableValue")) {
                case "NaN" -> Double.NaN;
                case "Infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity" -> Double.NEGATIVE_INFINITY;
                case "-0" -> -0.0d;
                default -> throw CdpError.invalidParams("unserializableValue");
            };
        }
        final Object value = argument.raw("value");
        if (value == null) {
            return argument.has("value") || argument.raw("value") == null && hasKey(argument) ? null : DebugValues.UNDEFINED;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return value; // a String, a Boolean; a Map or List is passed as such
    }

    private static boolean hasKey(final Params argument) {
        return argument.raw("value") == null && argument.has("value");
    }
}
