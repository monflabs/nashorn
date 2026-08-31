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

package org.monflabs.nashorn.api.debugger.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Breakpoint;
import org.monflabs.nashorn.api.debugger.BreakpointRequest;
import org.monflabs.nashorn.api.debugger.ConsoleEvent;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.DebugProperty;
import org.monflabs.nashorn.api.debugger.DebugScope;
import org.monflabs.nashorn.api.debugger.DebugScope.ScopeType;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.ExceptionEvent;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.api.debugger.PauseOnExceptions;
import org.monflabs.nashorn.api.debugger.PauseReason;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The debugger API, driven headlessly: a script runs on a worker thread, the
 * test plays the frontend from the TestNG thread. Every wait has a timeout so
 * that a pause that never comes fails the test rather than hanging the run.
 */
@SuppressWarnings("javadoc")
public class DebuggerTest {
    private static final long TIMEOUT = 20;

    private ScriptEngine engine;
    private Debugger debugger;
    private ExecutorService worker;
    private final BlockingQueue<PausedEvent> pauses = new LinkedBlockingQueue<>();
    private final List<DebugScript> parsed = new CopyOnWriteArrayList<>();
    private final List<String> resolved = new CopyOnWriteArrayList<>();
    private final List<ConsoleEvent> consoleCalls = new CopyOnWriteArrayList<>();
    private final List<ExceptionEvent> escaped = new CopyOnWriteArrayList<>();
    private int resumedCount;

    @BeforeMethod
    public void setUp() {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        debugger = Debugger.of(engine);
        worker = Executors.newSingleThreadExecutor();
        pauses.clear();
        parsed.clear();
        resolved.clear();
        consoleCalls.clear();
        escaped.clear();
        resumedCount = 0;
        debugger.addListener(new DebugListener() {
            @Override public void scriptParsed(final DebugScript script) { parsed.add(script); }
            @Override public void breakpointResolved(final Breakpoint bp, final Location l) { resolved.add(bp.id() + "@" + l.line() + ":" + l.column()); }
            @Override public void paused(final PausedEvent event) { pauses.add(event); }
            @Override public void resumed(final PausedEvent event) { resumedCount++; }
            @Override public void consoleCalled(final ConsoleEvent event) { consoleCalls.add(event); }
            @Override public void exceptionThrown(final ExceptionEvent event) { escaped.add(event); }
        });
    }

    @AfterMethod
    public void tearDown() {
        debugger.close();
        worker.shutdownNow();
    }

    // -- helpers --------------------------------------------------------------

    private Future<Object> run(final String fileName, final String... lines) {
        engine.put(ScriptEngine.FILENAME, fileName);
        final String source = String.join("\n", lines);
        return worker.submit(() -> engine.eval(source));
    }

    private PausedEvent awaitPause() throws InterruptedException {
        final PausedEvent event = pauses.poll(TIMEOUT, TimeUnit.SECONDS);
        assertNotNull(event, "no pause within " + TIMEOUT + "s");
        return event;
    }

    /** The script's result; a number comes back as a Double, and is compared as an int. */
    private static Object await(final Future<Object> future) throws Exception {
        final Object value = future.get(TIMEOUT, TimeUnit.SECONDS);
        return value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && Math.abs(n.doubleValue()) < Integer.MAX_VALUE
                ? Integer.valueOf(n.intValue()) : value;
    }

    private Breakpoint breakpointAt(final String fileName, final int line) {
        return debugger.setBreakpoint(new BreakpointRequest(null, ".*/" + fileName.replace(".", "\\."), null, line, -1, null));
    }

    private static String at(final PausedEvent event) {
        final DebugFrame top = event.frames().get(0);
        return top.functionName() + "@" + top.location().line();
    }

    private static List<ScopeType> scopeTypes(final DebugFrame frame) {
        final List<ScopeType> types = new ArrayList<>();
        for (final DebugScope scope : frame.scopes()) {
            types.add(scope.type());
        }
        return types;
    }

    private static String describe(final Debugger debugger, final PausedEvent event, final Object value) throws Exception {
        return event.call(() -> debugger.values().description(value));
    }

    private static DebugProperty property(final Debugger debugger, final PausedEvent event, final Object object, final String name) throws Exception {
        return event.call(() -> {
            for (final DebugProperty p : debugger.values().ownProperties(object, true, true)) {
                if (p.name().equals(name)) {
                    return p;
                }
            }
            return null;
        });
    }

    // -- tests ----------------------------------------------------------------

    @Test
    public void engineWithoutTheOptionHasNoDebugger() {
        final ScriptEngine plain = new NashornScriptEngineFactory().getScriptEngine();
        try {
            Debugger.of(plain);
            fail("expected an IllegalStateException");
        } catch (final IllegalStateException e) {
            assertTrue(e.getMessage().contains("--debugger"), e.getMessage());
        }
    }

    @Test
    public void pendingBreakpointResolvesAndPauses() throws Exception {
        final Breakpoint bp = breakpointAt("bp.js", 2);
        assertTrue(bp.locations().isEmpty(), "pending until the script arrives");

        final Future<Object> result = run("bp.js",
                "function f(a) {",       // 0
                "  var b = a + 1;",      // 1
                "  return b * 2;",       // 2
                "}",                     // 3
                "f(20);");               // 4

        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.BREAKPOINT);
        assertEquals(event.hitBreakpoints(), List.of(bp.id()));
        assertEquals(bp.locations().size(), 1);
        assertEquals(bp.locations().get(0).line(), 2);
        assertEquals(resolved, List.of(bp.id() + "@2:2"));
        assertEquals(parsed.size(), 1);
        assertTrue(parsed.get(0).url().endsWith("/bp.js"), parsed.get(0).url());

        final List<DebugFrame> frames = event.frames();
        assertEquals(frames.size(), 2, "f and the program");
        assertEquals(at(event), "f@2");
        assertEquals(frames.get(1).functionName(), "");
        assertEquals(frames.get(1).location().line(), 4);
        assertEquals(frames.get(0).functionLocation().line(), 0);

        event.resume();
        assertEquals(await(result), 42);
        assertTrue(event.isResumed());
        assertEquals(resumedCount, 1);
    }

    @Test
    public void possibleBreakpointsCoverFunctionsNotYetCompiled() throws Exception {
        await(run("locations.js",
                "var x = 1;",                 // 0
                "function never() {",         // 1
                "  var y = 2;",               // 2
                "  return y;",                // 3
                "}",                          // 4
                "x;"));                       // 5
        final DebugScript script = parsed.get(0);
        final List<String> locations = new ArrayList<>();
        for (final Location l : script.possibleBreakpoints(0, 0, -1, -1)) {
            locations.add(l.line() + ":" + l.column());
        }
        assertEquals(locations, List.of("0:0", "2:2", "3:2", "5:0"));
        assertEquals(script.endLine(), 5);
        assertEquals(script.source().length(), script.length());
    }

    @Test
    public void breakpointInLazilyCompiledFunctionHits() throws Exception {
        final Breakpoint bp = breakpointAt("lazy.js", 3);
        final Future<Object> result = run("lazy.js",
                "function outer(n) {",
                "  function inner(m) {",
                "    var k = m;",
                "    return k + 1;",
                "  }",
                "  return inner(n);",
                "}",
                "outer(1);");
        final PausedEvent event = awaitPause();
        assertEquals(at(event), "inner@3");
        assertEquals(event.hitBreakpoints(), List.of(bp.id()));
        assertEquals(event.frames().size(), 3);
        event.resume();
        assertEquals(await(result), 2);
    }

    @Test
    public void breakpointSlidesToTheNextStatement() throws Exception {
        final Breakpoint bp = breakpointAt("slide.js", 1);
        final Future<Object> result = run("slide.js",
                "function f() {",
                "",                          // 1: nothing here
                "  return 7;",               // 2
                "}",
                "f();");
        final PausedEvent event = awaitPause();
        assertEquals(bp.locations().get(0).line(), 2);
        assertEquals(at(event), "f@2");
        event.resume();
        assertEquals(await(result), 7);
    }

    @Test
    public void conditionDecidesWhetherToPause() throws Exception {
        debugger.setBreakpoint(new BreakpointRequest(null, ".*/cond\\.js", null, 1, -1, "i === 3"));
        final Future<Object> result = run("cond.js",
                "for (var i = 0; i < 6; i++) {",
                "  var last = i;",
                "}",
                "last;");
        final PausedEvent event = awaitPause();
        assertEquals(describe(debugger, event, event.frames().get(0).evaluate("i")), "3");
        event.resume();
        assertEquals(await(result), 5);
        assertTrue(pauses.isEmpty(), "only one iteration satisfies the condition");
    }

    @Test
    public void removedAndDeactivatedBreakpointsDoNotPause() throws Exception {
        final Breakpoint bp = breakpointAt("off.js", 0);
        debugger.removeBreakpoint(bp.id());
        assertEquals(await(run("off.js", "1 + 1;")), 2);
        assertTrue(pauses.isEmpty());

        breakpointAt("off2.js", 0);
        debugger.setBreakpointsActive(false);
        assertEquals(await(run("off2.js", "2 + 2;")), 4);
        assertTrue(pauses.isEmpty());
    }

    @Test
    public void pauseStopsARunningLoop() throws Exception {
        final Future<Object> result = run("loop.js",
                "var n = 0;",
                "while (n < 200000000) { n++; if (n === 1000) java.lang.Thread.sleep(200); }",
                "n;");
        Thread.sleep(50);
        debugger.pause();
        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.DEBUG_COMMAND);
        assertEquals(event.frames().get(0).location().line(), 1);
        event.frames().get(0).evaluate("n = 300000000");
        event.resume();
        assertTrue(((Number)await(result)).doubleValue() >= 300000000, "the loop ended on the value written while paused");
    }

    @Test
    public void pauseOnStartStopsAtTheFirstStatement() throws Exception {
        debugger.pauseOnStart();
        final Future<Object> result = run("start.js", "var a = 1;", "a + 1;");
        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.START);
        assertEquals(event.frames().get(0).location().line(), 0);
        event.resume();
        assertEquals(await(result), 2);
    }

    @Test
    public void stepping() throws Exception {
        breakpointAt("step.js", 5);
        final Future<Object> result = run("step.js",
                "function inner(v) {",              // 0
                "  var r = v + 1;",                 // 1
                "  return r;",                      // 2
                "}",                                // 3
                "function mid(v) {",                // 4
                "  var q = inner(v);",              // 5
                "  q = inner(q);",                  // 6
                "  return q;",                      // 7
                "}",                                // 8
                "var out = 0;",                     // 9
                "for (var i = 0; i < 2; i++) {",   // 10
                "  out = mid(out);",                // 11
                "}",                                // 12
                "out;");                            // 13
        PausedEvent event = awaitPause();
        assertEquals(at(event), "mid@5");
        event.stepInto();
        event = awaitPause();
        assertEquals(event.reason(), PauseReason.STEP);
        assertEquals(at(event), "inner@1");
        assertEquals(event.frames().size(), 3);
        event.stepOver();
        event = awaitPause();
        assertEquals(at(event), "inner@2");
        event.stepOut();
        event = awaitPause();
        assertEquals(at(event), "mid@6");
        event.stepOver();
        event = awaitPause();
        assertEquals(at(event), "mid@7", "step over does not enter the call");
        event.stepOver();
        event = awaitPause();
        assertEquals(at(event), "@10", "returning lands at the loop header, the caller's next statement");
        event.stepOut();
        event = awaitPause();
        assertEquals(event.reason(), PauseReason.BREAKPOINT, "a breakpoint met while stepping out wins");
        assertEquals(at(event), "mid@5");
        event.resume();
        assertEquals(await(result), 4);
    }

    @Test
    public void scopesAndThis() throws Exception {
        breakpointAt("scopes.js", 9);
        final Future<Object> result = run("scopes.js",
                "var g = 'global';",                                  // 0
                "function outer(p) {",                                // 1
                "  var o = p + 1;",                                   // 2
                "  return function inner(q) {",                       // 3
                "    let b = q * 2;",                                 // 4
                "    try { throw 'c'; } catch (c) {",                 // 5
                "      with ({ w: 9 }) {",                            // 6
                "        {",                                          // 7
                "          let deep = b + o;",                        // 8
                "          return deep;",                             // 9
                "        }",
                "      }",
                "    }",
                "  };",
                "}",
                "var obj = { m: outer(1) };",
                "obj.m(3);");
        final PausedEvent event = awaitPause();
        final DebugFrame top = event.frames().get(0);
        assertEquals(top.functionName(), "inner");
        final List<ScopeType> types = scopeTypes(top);
        assertEquals(types.get(0), ScopeType.BLOCK, types.toString());
        assertTrue(types.contains(ScopeType.WITH), types.toString());
        assertTrue(types.contains(ScopeType.LOCAL), types.toString());
        assertTrue(types.contains(ScopeType.CLOSURE), types.toString());
        assertEquals(types.get(types.size() - 1), ScopeType.GLOBAL, types.toString());

        // every binding is in some scope of the right kind, whatever the exact
        // block structure the compiler chose
        final java.util.Map<ScopeType, java.util.Map<String, String>> seen = new java.util.EnumMap<>(ScopeType.class);
        for (final DebugScope scope : top.scopes()) {
            if (scope.type() == ScopeType.GLOBAL) {
                continue;
            }
            final java.util.Map<String, String> values = seen.computeIfAbsent(scope.type(), k -> new java.util.LinkedHashMap<>());
            for (final DebugProperty p : event.call(() -> debugger.values().ownProperties(scope.object(), true, true))) {
                values.put(p.name(), describe(debugger, event, p.value()));
            }
            if (scope.type() == ScopeType.LOCAL) {
                assertEquals(scope.name(), "inner");
            }
        }
        assertEquals(seen.get(ScopeType.BLOCK).get("deep"), "8", seen.toString());
        assertEquals(seen.get(ScopeType.BLOCK).get("c"), "c", seen.toString());
        assertEquals(seen.get(ScopeType.WITH).get("w"), "9", seen.toString());
        assertEquals(seen.get(ScopeType.LOCAL).get("q"), "3", seen.toString());
        assertTrue(seen.get(ScopeType.LOCAL).containsKey("arguments"), seen.toString());
        assertEquals(seen.get(ScopeType.CLOSURE).get("o"), "2", seen.toString());
        assertEquals(describe(debugger, event, top.evaluate("this === obj")), "true");
        assertEquals(describe(debugger, event, top.evaluate("g + '!' + o + b + c + w")), "global!26c9");
        event.resume();
        assertEquals(await(result), 8);
    }

    @Test
    public void evaluateReadsAndWritesAndReportsErrors() throws Exception {
        breakpointAt("eval.js", 2);
        final Future<Object> result = run("eval.js",
                "function f() {",
                "  var v = 10;",
                "  return v;",
                "}",
                "f();");
        final PausedEvent event = awaitPause();
        final DebugFrame top = event.frames().get(0);
        assertEquals(describe(debugger, event, top.evaluate("v + 1")), "11");
        top.evaluate("v = 99");
        try {
            top.evaluate("nope.x");
            fail("expected a DebugException");
        } catch (final DebugException e) {
            assertTrue(e.getMessage().contains("nope"), e.getMessage());
            assertNotNull(e.thrown());
        }
        try {
            top.evaluate("var (");
            fail("expected a DebugException for a syntax error");
        } catch (final DebugException e) {
            assertTrue(e.getMessage().contains("SyntaxError"), e.getMessage());
        }
        event.resume();
        assertEquals(await(result), 99);
    }

    @Test
    public void evaluateWhileRunning() throws Exception {
        await(run("running.js", "var shared = 5;"));
        final Object value = debugger.evaluate(debugger.executionContexts().get(0), "shared * 2");
        assertEquals(debugger.values().description(value), "10");
    }

    @Test
    public void pauseOnAllExceptionsStopsAtTheThrow() throws Exception {
        debugger.setPauseOnExceptions(PauseOnExceptions.ALL);
        final Future<Object> result = run("exc.js",
                "function thrower() { throw new TypeError('boom'); }",
                "function catcher() { try { thrower(); } catch (x) { return 'caught ' + x.message; } }",
                "catcher();");
        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.EXCEPTION);
        assertEquals(at(event), "thrower@0");
        assertTrue(describe(debugger, event, event.exception()).startsWith("TypeError: boom"), "an error describes itself by its stack");
        event.resume();
        assertEquals(await(result), "caught boom");
        assertTrue(escaped.isEmpty(), "nothing escaped");
    }

    @Test
    public void pauseOnUncaughtExceptionsStopsOnlyForOneThatEscapes() throws Exception {
        debugger.setPauseOnExceptions(PauseOnExceptions.UNCAUGHT);
        assertEquals(await(run("caught.js",
                "function thrower() { throw new RangeError('inner'); }",
                "(function () { try { thrower(); } catch (x) { return 'caught'; } })();")), "caught");
        assertTrue(pauses.isEmpty(), "a caught exception does not pause");

        final Future<Object> result = run("uncaught.js",
                "function thrower2() { throw new RangeError('outer'); }",
                "thrower2();");
        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.EXCEPTION);
        assertEquals(event.frames().size(), 1, "paused where the exception escapes the outermost frame");
        event.resume();
        try {
            await(result);
            fail("expected the script to throw");
        } catch (final java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause().getMessage().contains("outer"), e.getCause().getMessage());
        }
        assertEquals(escaped.size(), 1);
        assertTrue(escaped.get(0).message().contains("RangeError: outer"), escaped.get(0).message());
    }

    @Test
    public void consoleCallsAreReportedAndPrinted() throws Exception {
        final java.io.StringWriter out = new java.io.StringWriter();
        final java.io.StringWriter err = new java.io.StringWriter();
        engine.getContext().setWriter(out);
        engine.getContext().setErrorWriter(err);
        await(run("console.js",
                "console.log('a', 1, {});",
                "console.warn('w');",
                "console.error('e');",
                "console.assert(true, 'not shown');",
                "console.assert(false, 'shown');",
                "console.count(); console.count(); console.count('x');"));
        assertEquals(out.toString().replace("\r", ""), "a 1 [object Object]\ndefault: 1\ndefault: 2\nx: 1\n");
        assertEquals(err.toString().replace("\r", ""), "w\ne\nAssertion failed: shown\n");
        final List<String> types = new ArrayList<>();
        for (final ConsoleEvent c : consoleCalls) {
            types.add(c.type());
        }
        assertEquals(types, List.of("log", "warning", "error", "assert", "count", "count", "count"));
        assertEquals(consoleCalls.get(0).arguments().size(), 3);
        assertEquals(consoleCalls.get(1).location().line(), 1);
    }

    @Test
    public void breakpointInsideAGeneratorBody() throws Exception {
        breakpointAt("gen.js", 1);
        final Future<Object> result = run("gen.js",
                "function* g() {",
                "  yield 1;",
                "  yield 2;",
                "}",
                "var it = g(); it.next().value + it.next().value;");
        final PausedEvent event = awaitPause();
        assertEquals(at(event), "g@1");
        assertTrue(event.thread().isVirtual(), "a generator body runs on a virtual thread");
        assertEquals(scopeTypes(event.frames().get(0)), List.of(ScopeType.LOCAL, ScopeType.SCRIPT, ScopeType.GLOBAL));
        event.resume();
        assertEquals(await(result), 3);
    }

    @Test
    public void valuesModel() throws Exception {
        breakpointAt("values.js", 17);
        final Future<Object> result = run("values.js",
                "var arr = [1, 2, 3];",
                "var date = new Date(0);",
                "var re = /a+/g;",
                "var map = new Map([[1, 2]]);",
                "var err = new Error('bad');",
                "var fn = function named(a) { return a; };",
                "var sym = Symbol('s');",
                "var obj = { plain: 1, get acc() { return 2; } };",
                "Object.defineProperty(obj, 'hidden', { value: 3, enumerable: false });",
                "var nan = NaN, negZero = -0, inf = Infinity;",
                "var str = 'text', cons = str + str;",
                "var nothing = null, undef;",
                "var jarr = Java.to([7, 8], 'int[]');",
                "var jlist = new java.util.ArrayList(); jlist.add('x'); jlist.add('y');",
                "var jmap = new java.util.HashMap(); jmap.put('k', 5);",
                "var jbean = new java.awt.Point(3, 4);",
                "var jclass = java.lang.Integer;",
                "1;");
        final PausedEvent event = awaitPause();
        final DebugValues v = debugger.values();
        final DebugFrame top = event.frames().get(0);
        final Object arr = top.evaluate("arr");
        event.call(() -> {
            assertEquals(v.type(arr), "object");
            assertEquals(v.subtype(arr), "array");
            assertEquals(v.className(arr), "Array");
            assertEquals(v.description(arr), "Array(3)");
            assertEquals(v.arrayLength(arr), 3);
            assertEquals(v.subtype(top.evaluate("date")), "date");
            assertEquals(v.subtype(top.evaluate("re")), "regexp");
            assertEquals(v.subtype(top.evaluate("map")), "map");
            final Object err = top.evaluate("err");
            assertEquals(v.subtype(err), "error");
            assertTrue(v.description(err).startsWith("Error: bad"), v.description(err));
            final Object fn = top.evaluate("fn");
            assertEquals(v.type(fn), "function");
            assertEquals(v.className(fn), "Function");
            assertTrue(v.description(fn).startsWith("function named(a)"), v.description(fn));
            assertEquals(v.type(top.evaluate("sym")), "symbol");
            assertEquals(v.type(top.evaluate("str")), "string");
            assertEquals(v.toJava(top.evaluate("cons")), "texttext");
            assertEquals(v.unserializable(top.evaluate("nan")), "NaN");
            assertEquals(v.unserializable(top.evaluate("negZero")), "-0");
            assertEquals(v.unserializable(top.evaluate("inf")), "Infinity");
            assertNull(v.unserializable(top.evaluate("1.5")));
            assertEquals(v.type(top.evaluate("nothing")), "object");
            assertEquals(v.subtype(top.evaluate("nothing")), "null");
            assertEquals(v.type(top.evaluate("undef")), "undefined");
            assertEquals(v.description(top.evaluate("undef")), "undefined");
            assertTrue(v.isPrimitive(top.evaluate("undef")));
            assertFalse(v.isPrimitive(arr));

            final Object obj = top.evaluate("obj");
            final List<String> names = new ArrayList<>();
            for (final DebugProperty p : v.ownProperties(obj, true, true)) {
                names.add(p.name() + (p.getter() != null ? "(get)" : "") + (p.enumerable() ? "" : "(hidden)"));
            }
            assertEquals(names, List.of("plain", "acc(get)", "hidden(hidden)"));
            assertEquals(v.ownProperties(obj, false, true).size(), 2);
            assertEquals(v.ownProperties(arr, false, false).size(), 0, "indexed left out");
            assertEquals(v.ownProperties(arr, false, true).size(), 3);
            assertEquals(v.internalProperties(obj).get(0).name(), "[[Prototype]]");
            assertEquals(v.prototype(obj), top.evaluate("Object.prototype"));
            assertEquals(v.description(v.callFunction(fn, null, "arg")), "arg");

            // Java objects, as a script sees them
            final Object jarr = top.evaluate("jarr");
            assertEquals(v.type(jarr), "object");
            assertEquals(v.subtype(jarr), "array");
            assertEquals(v.className(jarr), "int[]");
            assertEquals(v.description(jarr), "int[2]");
            assertEquals(v.arrayLength(jarr), 2);
            assertEquals(v.ownProperties(jarr, true, true).size(), 3, "two elements and length");
            assertEquals(v.description(v.ownProperties(jarr, false, true).get(1).value()), "8");
            final Object jlist = top.evaluate("jlist");
            assertEquals(v.subtype(jlist), "array");
            assertEquals(v.description(jlist), "ArrayList(2)");
            assertEquals(v.ownProperties(jlist, false, true).size(), 2);
            final Object jmap = top.evaluate("jmap");
            assertNull(v.subtype(jmap));
            assertEquals(v.ownProperties(jmap, true, true).get(0).name(), "k");
            final Object jbean = top.evaluate("jbean");
            final List<String> beanNames = new ArrayList<>();
            for (final DebugProperty p : v.ownProperties(jbean, true, true)) {
                beanNames.add(p.name() + "=" + v.description(p.value()));
            }
            assertTrue(beanNames.contains("x=3"), beanNames.toString());
            assertTrue(beanNames.contains("location=java.awt.Point[x=3,y=4]"), beanNames.toString());
            assertEquals(v.internalProperties(jbean).get(0).value(), "java.awt.Point");
            final Object jclass = top.evaluate("jclass");
            assertEquals(v.className(jclass), "JavaClass");
            assertEquals(v.description(jclass), "[JavaClass java.lang.Integer]");
            boolean sawMaxValue = false;
            for (final DebugProperty p : v.ownProperties(jclass, true, true)) {
                sawMaxValue |= p.name().equals("MAX_VALUE");
            }
            assertTrue(sawMaxValue);
            return null;
        });
        event.resume();
        assertEquals(await(result), 1);
    }

    @Test
    public void programFrameShowsTheScriptScopeFirst() throws Exception {
        breakpointAt("top.js", 2);
        final Future<Object> result = run("top.js",
                "var mine = 1;",
                "function helper() { return mine; }",
                "var later = helper();",
                "later;");
        final PausedEvent event = awaitPause();
        final DebugFrame top = event.frames().get(0);
        assertEquals(scopeTypes(top), List.of(ScopeType.SCRIPT, ScopeType.GLOBAL));
        final DebugScope script = top.scopes().get(0);
        final List<String> names = new ArrayList<>();
        for (final DebugProperty p : event.call(() -> debugger.values().ownProperties(script.object(), true, true))) {
            names.add(p.name());
        }
        java.util.Collections.sort(names);
        assertEquals(names, List.of("helper", "later", "mine"), "the script's own declarations, no built-ins");
        assertEquals(event.call(() -> debugger.values().description(script.object())), "Script");
        event.call(() -> {
            debugger.values().setProperty(script.object(), "mine", 41);
            return null;
        });
        event.resume();
        assertEquals(await(result), 41, "a write to the script scope reaches the global");
    }

    @Test
    public void debuggerStatementPausesWhenListened() throws Exception {
        final Future<Object> result = run("stmt.js",
                "var a = 1;",
                "debugger;",
                "a + 1;");
        final PausedEvent event = awaitPause();
        assertEquals(event.reason(), PauseReason.OTHER);
        assertEquals(event.frames().get(0).location().line(), 1);
        event.resume();
        assertEquals(await(result), 2);

        // and is nothing without a listener - the plain engine runs through it
        final ScriptEngine plain = new NashornScriptEngineFactory().getScriptEngine();
        assertEquals(((Number)plain.eval("var b = 2; debugger; b * 2")).intValue(), 4);
    }

    @Test
    public void closeResumesAndForgets() throws Exception {
        breakpointAt("close.js", 0);
        final Future<Object> result = run("close.js", "var z = 3;", "z;");
        final PausedEvent event = awaitPause();
        debugger.close();
        assertEquals(await(result), 3);
        assertTrue(event.isResumed());
        assertEquals(await(run("close.js", "var z = 4;", "z;")), 4, "the breakpoint is gone");
    }
}
