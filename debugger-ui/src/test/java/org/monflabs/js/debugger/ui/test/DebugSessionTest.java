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

package org.monflabs.js.debugger.ui.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.debugger.CdpServer;
import org.monflabs.js.debugger.ui.model.Breakpoint;
import org.monflabs.js.debugger.ui.model.ConsoleEntry;
import org.monflabs.js.debugger.ui.model.DebugSession;
import org.monflabs.js.debugger.ui.model.PauseState;
import org.monflabs.js.debugger.ui.model.PropertyEntry;
import org.monflabs.js.debugger.ui.model.RemoteValue;
import org.monflabs.js.debugger.ui.model.ScriptInfo;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The session against a real engine and server: attach, breakpoints, pauses,
 * frames, scopes, evaluation, stepping, the staleness guard, reattach, the
 * console and watches.
 */
@SuppressWarnings({"javadoc", "deprecation"})
public class DebugSessionTest {
    private static final long TIMEOUT = 20;

    private ScriptEngine engine;
    private CdpServer.Handle server;
    private ExecutorService worker;   // runs the script (a pause blocks it)
    private PumpExecutor ui;          // the session's single thread, driven by the test
    private DebugSession session;
    private Recorder recorder;

    /** An executor that queues tasks for the test thread to pump - the "EDT" here. */
    private static final class PumpExecutor implements Executor {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        @Override
        public void execute(final Runnable task) {
            tasks.add(task);
        }
        void pump() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    /** Records what the session tells its listener. */
    private static final class Recorder implements DebugSession.SessionListener {
        final List<ScriptInfo> scripts = new ArrayList<>();
        final List<ConsoleEntry> console = new ArrayList<>();
        volatile PauseState lastPause;
        volatile int resumeCount;
        volatile List<Breakpoint> breakpoints = List.of();
        volatile String closedReason;
        volatile DebugSession.State state = DebugSession.State.DETACHED;

        volatile int cleared;

        @Override public void stateChanged(final DebugSession.State s) { state = s; }
        @Override public void scriptAdded(final ScriptInfo s) { scripts.add(s); }
        @Override public void scriptsCleared() { cleared++; scripts.clear(); }
        @Override public void paused(final PauseState p) { lastPause = p; }
        @Override public void resumed() { resumeCount++; lastPause = null; }
        @Override public void breakpointsChanged(final List<Breakpoint> b) { breakpoints = b; }
        @Override public void consoleEntry(final ConsoleEntry e) { console.add(e); }
        @Override public void connectionClosed(final String r) { closedReason = r; }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        server = CdpServer.open(Debugger.of(engine), InspectOptions.parse("127.0.0.1:0", false));
        worker = Executors.newSingleThreadExecutor();
        ui = new PumpExecutor();
        session = new DebugSession(ui);
        recorder = new Recorder();
        session.addListener(recorder);
    }

    @AfterMethod
    public void tearDown() {
        session.close();
        ui.pump();
        server.close();
        worker.shutdownNow();
        Debugger.of(engine).close();
    }

    // ---- driving helpers ----

    /** Pumps the ui queue until a condition holds or the timeout elapses. */
    private void pumpUntil(final java.util.function.BooleanSupplier done) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        while (System.nanoTime() < deadline) {
            ui.pump();
            if (done.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        ui.pump();
        if (!done.getAsBoolean()) {
            throw new AssertionError("condition not met within " + TIMEOUT + "s");
        }
    }

    private void attach() {
        session.attach(server.webSocketUrl());
        pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
    }

    private CompletableFuture<Object> runScript(final String fileName, final String source) {
        engine.put(ScriptEngine.FILENAME, fileName);
        final CompletableFuture<Object> done = new CompletableFuture<>();
        worker.submit(() -> {
            try {
                done.complete(engine.eval(source));
            } catch (final Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    private <T> T await(final CompletableFuture<T> future) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        while (System.nanoTime() < deadline) {
            ui.pump();
            if (future.isDone()) {
                return future.getNow(null);
            }
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("future not done within " + TIMEOUT + "s");
    }

    // ---- tests ----

    @Test
    public void attachReplaysScripts() {
        runScript("first.js", "1 + 1;");
        attach();
        // after attach, Debugger.enable replays the parsed script
        pumpUntil(() -> recorder.scripts.stream().anyMatch(s -> s.url().endsWith("/first.js")));
        assertTrue(recorder.scripts.stream().anyMatch(s -> s.url().endsWith("/first.js")));
    }

    @Test
    public void clearScriptsDropsScriptsButKeepsBreakpointsAndConnection() {
        attach();
        session.toggleBreakpoint("file:///work/keep.js", 1);
        ui.pump();
        runScript("gone.js", "1 + 1;");
        pumpUntil(() -> !session.scripts().isEmpty());
        assertTrue(recorder.scripts.stream().anyMatch(s -> s.url().endsWith("/gone.js")));
        final int before = recorder.cleared;

        // the host clears the engine's script registry - no disconnect
        Debugger.of(engine).clearScripts();

        pumpUntil(() -> recorder.cleared > before);
        assertTrue(session.scripts().isEmpty(), "scripts should be cleared");
        assertEquals(session.state(), DebugSession.State.RUNNING, "connection stays up");
        // the breakpoint survives - it is client-owned and re-resolves as scripts parse
        assertTrue(session.breakpoints().stream().anyMatch(b -> "file:///work/keep.js".equals(b.url()) && b.line() == 1),
                "breakpoint should survive the clear");
    }

    @Test
    public void reattachingAfterAClearDoesNotReplayTheOldScripts() {
        // debug one snippet, then detach - like closing the debugger window
        attach();
        runScript("old.js", "1 + 1;");
        pumpUntil(() -> !session.scripts().isEmpty());
        session.detach();
        pumpUntil(() -> session.state() == DebugSession.State.DETACHED);

        // the host clears the engine's registry before reopening on another snippet
        Debugger.of(engine).clearScripts();

        // reattaching replays nothing - the Sources do not show the previous snippet
        session.attach(server.webSocketUrl());
        pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
        assertTrue(session.scripts().isEmpty(), "the previous snippet's script must not be replayed");
    }

    @Test
    public void reattachingWithoutAClearReplaysTheScripts() {
        // debugging the same sample again (no clear): the engine still holds its
        // scripts, so Debugger.enable replays them - the Sources are not empty
        attach();
        runScript("keep.js", "1 + 1;");
        pumpUntil(() -> !session.scripts().isEmpty());
        session.detach();
        pumpUntil(() -> session.state() == DebugSession.State.DETACHED);

        session.attach(server.webSocketUrl());
        pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
        pumpUntil(() -> session.scripts().stream().anyMatch(s -> s.url().endsWith("/keep.js")));
        assertTrue(session.scripts().stream().anyMatch(s -> s.url().endsWith("/keep.js")),
                "the script must be replayed on reattach");
    }

    @Test
    public void aBreakpointSetBeforeTheScriptHitsAfterRun() {
        attach();
        // the script does not exist yet; set the breakpoint by url
        final String url = "file:///work/loop.js";
        session.toggleBreakpoint(url, 1);
        ui.pump();
        engine.put(ScriptEngine.FILENAME, "loop.js");
        // give the url the shape the engine will report (file:///.../loop.js won't match a bare name)
        // so run with a matching url via the FILENAME the server turns into a url
        final CompletableFuture<Object> done = runScriptAtUrl(url, "var a = 1;\na = a + 1;\na;");
        pumpUntil(() -> recorder.lastPause != null);
        assertEquals(session.state(), DebugSession.State.PAUSED);
        assertEquals(recorder.lastPause.frames().get(0).line(), 1);
        session.resume();
        await(done);
    }

    /**
     * Runs a script that reports a stable url matching a by-url breakpoint: an
     * appended {@code //# sourceURL} directive fixes the url across runs (the
     * engine otherwise mints a fresh {@code nashorn://script/<id>/...} per eval)
     * without shifting the real code's line numbers.
     */
    private CompletableFuture<Object> runScriptAtUrl(final String url, final String source) {
        engine.put(ScriptEngine.FILENAME, "script.js");
        final String withUrl = source + "\n//# sourceURL=" + url + "\n";
        final CompletableFuture<Object> done = new CompletableFuture<>();
        worker.submit(() -> {
            try {
                done.complete(engine.eval(withUrl));
            } catch (final Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }

    @Test
    public void framesScopesEvaluateAndProperties() {
        attach();
        final String url = "file:///work/vars.js";
        session.toggleBreakpoint(url, 2);
        ui.pump();
        final CompletableFuture<Object> done = runScriptAtUrl(url, "var x = 41;\nvar y = { a: 1 };\ny;");
        pumpUntil(() -> recorder.lastPause != null);

        // evaluate on the frame sees the local
        final RemoteValue x = await(session.evaluate("x + 1"));
        assertEquals(((Number)x.value()).intValue(), 42);

        // the scope chain has a local scope with an object we can read
        final RemoteValue localScope = recorder.lastPause.frames().get(0).scopeChain().get(0).object();
        assertNotNull(localScope.objectId());
        final List<PropertyEntry> props = await(session.properties(localScope.objectId()));
        assertTrue(props.stream().anyMatch(p -> p.name().equals("x")), "scope had: " + props);

        session.resume();
        await(done);
    }

    @Test
    public void stalePropertyResultsAreDroppedAfterResume() {
        attach();
        final String url = "file:///work/stale.js";
        session.toggleBreakpoint(url, 1);
        ui.pump();
        final CompletableFuture<Object> done = runScriptAtUrl(url, "var o = { a: 1 };\no;");
        pumpUntil(() -> recorder.lastPause != null);
        final RemoteValue scope = recorder.lastPause.frames().get(0).scopeChain().get(0).object();
        // resume first, then ask for properties captured against the old pause serial
        session.resume();
        await(done);
        pumpUntil(() -> session.state() == DebugSession.State.RUNNING);
        final List<PropertyEntry> props = await(session.properties(scope.objectId()));
        assertTrue(props.isEmpty(), "expected the stale fetch to be dropped, got " + props);
    }

    @Test
    public void steppingAdvancesLineByLine() {
        attach();
        final String url = "file:///work/step.js";
        session.toggleBreakpoint(url, 0);
        ui.pump();
        final CompletableFuture<Object> done = runScriptAtUrl(url, "var a = 1;\nvar b = 2;\nvar c = 3;\nc;");
        pumpUntil(() -> recorder.lastPause != null);
        assertEquals(recorder.lastPause.frames().get(0).line(), 0);
        session.stepOver();
        pumpUntil(() -> recorder.lastPause != null && recorder.lastPause.frames().get(0).line() == 1);
        session.stepOver();
        pumpUntil(() -> recorder.lastPause != null && recorder.lastPause.frames().get(0).line() == 2);
        session.resume();
        await(done);
    }

    @Test
    public void detachAndReattachReArmsBreakpoints() {
        attach();
        final String url = "file:///work/rearm.js";
        session.toggleBreakpoint(url, 1);
        ui.pump();
        // first run hits
        CompletableFuture<Object> done = runScriptAtUrl(url, "var a = 1;\na = a + 1;\na;");
        pumpUntil(() -> recorder.lastPause != null);
        session.resume();
        await(done);
        pumpUntil(() -> session.state() == DebugSession.State.RUNNING);

        session.detach();
        ui.pump();
        assertEquals(session.state(), DebugSession.State.DETACHED);
        // the breakpoint definition survives
        assertEquals(session.breakpoints().size(), 1);

        recorder.lastPause = null;
        attach();  // re-arms
        done = runScriptAtUrl(url, "var a = 1;\na = a + 1;\na;");
        pumpUntil(() -> recorder.lastPause != null);
        assertEquals(recorder.lastPause.frames().get(0).line(), 1);
        session.resume();
        await(done);
    }

    @Test
    public void detachWhilePausedResumesTheScript() {
        attach();
        final String url = "file:///work/hang.js";
        session.toggleBreakpoint(url, 0);
        ui.pump();
        final CompletableFuture<Object> done = runScriptAtUrl(url, "var a = 1;\na;");
        pumpUntil(() -> recorder.lastPause != null);
        assertEquals(session.state(), DebugSession.State.PAUSED);
        // detach must resume first, or the worker thread would hang forever
        session.detach();
        ui.pump();
        await(done);   // completes only if the script was released
    }

    @Test
    public void consoleLogReachesTheConsole() {
        attach();
        runScriptAtUrl("file:///work/log.js", "console.log('hello', 42);");
        pumpUntil(() -> !recorder.console.isEmpty());
        assertEquals(recorder.console.get(0).kind(), ConsoleEntry.Kind.LOG);
        assertEquals(recorder.console.get(0).text(), "hello 42");
    }

    @Test
    public void printReachesTheConsole() {
        attach();
        runScriptAtUrl("file:///work/print.js", "print('from print', 7);");
        pumpUntil(() -> !recorder.console.isEmpty());
        assertEquals(recorder.console.get(0).kind(), ConsoleEntry.Kind.LOG);
        assertEquals(recorder.console.get(0).text(), "from print 7");
    }

    @Test
    public void watchesAreRememberedInOrder() {
        session.addWatch("a + b");
        session.addWatch("c");
        session.addWatch("a + b");   // duplicate ignored
        assertEquals(session.watches(), List.of("a + b", "c"));
        session.removeWatch("c");
        assertEquals(session.watches(), List.of("a + b"));
    }
}
