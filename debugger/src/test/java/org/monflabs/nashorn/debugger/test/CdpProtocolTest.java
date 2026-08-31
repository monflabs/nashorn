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

package org.monflabs.nashorn.debugger.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.debugger.CdpServer;
import org.monflabs.nashorn.debugger.json.Json;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The protocol, as a client speaks it: enable, breakpoints, pauses, frames,
 * evaluation, properties, stepping, exceptions, the console.
 */
@SuppressWarnings({ "javadoc", "unchecked" })
public class CdpProtocolTest {
    private ScriptEngine engine;
    private CdpServer.Handle server;
    private CdpClient client;
    private ExecutorService worker;

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        server = CdpServer.open(Debugger.of(engine), InspectOptions.parse("127.0.0.1:0", false));
        client = new CdpClient(server.webSocketUrl());
        worker = Executors.newSingleThreadExecutor();
        client.call("Runtime.enable");
        client.call("Debugger.enable");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        client.close();
        server.close();
        worker.shutdownNow();
        Debugger.of(engine).close();
    }

    private Future<Object> run(final String fileName, final String... lines) {
        engine.put(ScriptEngine.FILENAME, fileName);
        return worker.submit(() -> engine.eval(String.join("\n", lines)));
    }

    private static Map<String, Object> map(final Object o) {
        return (Map<String, Object>)o;
    }

    private static List<Object> list(final Object o) {
        return (List<Object>)o;
    }

    private static Map<String, Object> topFrame(final Map<String, Object> paused) {
        return map(list(paused.get("callFrames")).get(0));
    }

    private static long line(final Map<String, Object> frame) {
        return ((Number)map(frame.get("location")).get("lineNumber")).longValue();
    }

    @Test
    public void enableReportsContextsAndScripts() throws Exception {
        final Map<String, Object> ctx = map(client.event("Runtime.executionContextCreated").get("context"));
        assertEquals(ctx.get("name"), "nashorn");
        run("first.js", "1;").get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
        final Map<String, Object> script = client.event("Debugger.scriptParsed");
        assertTrue(String.valueOf(script.get("url")).endsWith("/first.js"), String.valueOf(script.get("url")));
        assertEquals(script.get("startLine"), 0L);
        assertEquals(client.call("Debugger.getScriptSource", "scriptId", script.get("scriptId")).get("scriptSource"), "1;");
    }

    @Test
    public void breakpointPauseFramesScopesEvaluateResume() throws Exception {
        final Map<String, Object> set = client.call("Debugger.setBreakpointByUrl", "lineNumber", 2L, "urlRegex", ".*/bp\\.js");
        assertEquals(list(set.get("locations")).size(), 0, "pending");
        final String breakpointId = (String)set.get("breakpointId");

        final Future<Object> result = run("bp.js",
                "function f(a) {",
                "  var b = a + 1;",
                "  return b * 2;",
                "}",
                "f(20);");
        final Map<String, Object> resolved = client.event("Debugger.breakpointResolved");
        assertEquals(resolved.get("breakpointId"), breakpointId);
        assertEquals(map(resolved.get("location")).get("lineNumber"), 2L);

        final Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "other");
        assertEquals(list(paused.get("hitBreakpoints")), List.of(breakpointId));
        final List<Object> frames = list(paused.get("callFrames"));
        assertEquals(frames.size(), 2);
        final Map<String, Object> top = map(frames.get(0));
        assertEquals(top.get("functionName"), "f");
        assertEquals(line(top), 2L);
        final List<Object> scopes = list(top.get("scopeChain"));
        assertEquals(map(scopes.get(0)).get("type"), "local");
        assertEquals(map(scopes.get(scopes.size() - 1)).get("type"), "global");
        assertEquals(map(top.get("this")).get("type"), "object");

        // the local scope's properties
        final String localId = (String)map(map(scopes.get(0)).get("object")).get("objectId");
        final Map<String, Object> props = client.call("Runtime.getProperties", "objectId", localId, "ownProperties", true);
        boolean sawB = false;
        for (final Object p : list(props.get("result"))) {
            if ("b".equals(map(p).get("name"))) {
                sawB = true;
                assertEquals(map(map(p).get("value")).get("value"), 21L);
            }
        }
        assertTrue(sawB, props.toString());

        // evaluate on the frame
        final Map<String, Object> eval = client.call("Debugger.evaluateOnCallFrame", "callFrameId", top.get("callFrameId"), "expression", "b * 10");
        assertEquals(map(eval.get("result")).get("value"), 210L);
        final Map<String, Object> failing = client.call("Debugger.evaluateOnCallFrame", "callFrameId", top.get("callFrameId"), "expression", "nope.x");
        assertNotNull(failing.get("exceptionDetails"));
        assertEquals(map(map(failing.get("exceptionDetails")).get("exception")).get("subtype"), "error");

        client.call("Debugger.resume");
        client.event("Debugger.resumed");
        assertEquals(((Number)result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 42);

        try {
            client.call("Debugger.resume");
            fail("expected an error while not paused");
        } catch (final CdpClient.CdpFailure e) {
            assertEquals(e.code, -32000);
        }
    }

    @Test
    public void stepping() throws Exception {
        client.call("Debugger.setBreakpointByUrl", "lineNumber", 4L, "urlRegex", ".*/step\\.js");
        final Future<Object> result = run("step.js",
                "function inner(v) {",              // 0
                "  var r = v + 1;",                 // 1
                "  return r;",                      // 2
                "}",                                // 3
                "var x = inner(1);",                // 4
                "x = inner(x);",                    // 5
                "x;");                              // 6
        Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 4L);
        client.call("Debugger.stepInto");
        paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "step");
        assertEquals(topFrame(paused).get("functionName"), "inner");
        assertEquals(line(topFrame(paused)), 1L);
        client.call("Debugger.stepOut");
        paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 5L);
        client.call("Debugger.stepOver");
        paused = client.event("Debugger.paused");
        assertEquals(line(topFrame(paused)), 6L);
        client.call("Debugger.resume");
        assertEquals(((Number)result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS)).intValue(), 3);
    }

    @Test
    public void pauseOnExceptionsAndExceptionThrown() throws Exception {
        client.call("Debugger.setPauseOnExceptions", "state", "all");
        final Future<Object> result = run("exc.js",
                "function thrower() { throw new TypeError('boom'); }",
                "try { thrower(); } catch (e) { e.message; }");
        final Map<String, Object> paused = client.event("Debugger.paused");
        assertEquals(paused.get("reason"), "exception");
        assertEquals(map(paused.get("data")).get("subtype"), "error");
        assertTrue(String.valueOf(map(paused.get("data")).get("description")).startsWith("TypeError: boom"));
        client.call("Debugger.resume");
        assertEquals(result.get(CdpClient.TIMEOUT, TimeUnit.SECONDS), "boom");

        client.call("Debugger.setPauseOnExceptions", "state", "none");
        final Future<Object> escaping = run("uncaught.js", "function bad() { throw new RangeError('out'); }", "bad();");
        final Map<String, Object> thrown = client.event("Runtime.exceptionThrown");
        final Map<String, Object> details = map(thrown.get("exceptionDetails"));
        assertEquals(details.get("text"), "Uncaught");
        assertTrue(String.valueOf(map(details.get("exception")).get("description")).startsWith("RangeError: out"));
        try {
            escaping.get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
            fail("the script throws");
        } catch (final java.util.concurrent.ExecutionException expected) {
            // as it should
        }
    }

    @Test
    public void runtimeEvaluateAndConsole() throws Exception {
        run("console.js", "var o = { a: 1, b: [1, 2, 3] };", "console.log('hello', o);", "console.error('bad');").get(CdpClient.TIMEOUT, TimeUnit.SECONDS);
        final Map<String, Object> log = client.event("Runtime.consoleAPICalled");
        assertEquals(log.get("type"), "log");
        final List<Object> args = list(log.get("args"));
        assertEquals(map(args.get(0)).get("value"), "hello");
        assertEquals(map(args.get(1)).get("type"), "object");
        assertNotNull(map(args.get(1)).get("preview"));
        assertEquals(client.event("Runtime.consoleAPICalled").get("type"), "error");

        final Map<String, Object> evaluated = client.call("Runtime.evaluate", "expression", "o.b", "generatePreview", true);
        final Map<String, Object> value = map(evaluated.get("result"));
        assertEquals(value.get("subtype"), "array");
        assertEquals(value.get("description"), "Array(3)");
        final Map<String, Object> props = client.call("Runtime.getProperties", "objectId", value.get("objectId"), "ownProperties", true);
        assertEquals(list(props.get("result")).size(), 4, "three elements and length");
        assertEquals(map(list(props.get("internalProperties")).get(0)).get("name"), "[[Prototype]]");

        final Map<String, Object> called = client.call("Runtime.callFunctionOn", "functionDeclaration", "function (n) { return this.a + n; }",
                "objectId", map(client.call("Runtime.evaluate", "expression", "o").get("result")).get("objectId"),
                "arguments", List.of(Json.object("value", 41L)));
        assertEquals(map(called.get("result")).get("value"), 42L);

        final Map<String, Object> nan = map(client.call("Runtime.evaluate", "expression", "NaN").get("result"));
        assertEquals(nan.get("unserializableValue"), "NaN");
        final Map<String, Object> byValue = client.call("Runtime.evaluate", "expression", "o", "returnByValue", true);
        assertEquals(map(map(byValue.get("result")).get("value")).get("a"), 1L);

        client.call("Runtime.releaseObjectGroup", "objectGroup", "console");
        assertTrue(map(client.call("Runtime.getHeapUsage")).containsKey("usedSize"));
        assertNotNull(client.call("Runtime.getIsolateId").get("id"));
    }

    @Test
    public void unknownMethodsDoNotEndTheSession() throws Exception {
        try {
            client.call("Profiler.enable");
            fail("expected -32601");
        } catch (final CdpClient.CdpFailure e) {
            assertEquals(e.code, -32601);
        }
        assertEquals(client.call("Debugger.setAsyncCallStackDepth", "maxDepth", 32L), Map.of(), "accepted as a no-op");
        client.sendRaw("this is not json");
        client.sendRaw("{\"id\": 77}");
        final Map<String, Object> response = client.rawResponse();
        assertEquals(response.get("id"), 77L);
        assertEquals(map(response.get("error")).get("code"), -32600L);
        assertEquals(map(client.call("Runtime.evaluate", "expression", "6 * 7").get("result")).get("value"), 42L, "still alive");
    }
}
