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


package org.monflabs.nashorn.playground;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.DebugScript;
import org.monflabs.nashorn.api.debugger.DebugValues;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.api.debugger.ScriptTerminated;
import org.monflabs.nashorn.api.debugger.TraceListener;
import org.monflabs.nashorn.api.scripting.NashornException;
import org.monflabs.nashorn.api.modules.Module;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.libs.HostLibrary;
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.modules.node.NodeModuleLoader;
import org.monflabs.nashorn.debugger.CdpServer;

/**
 * Runs samples on a worker thread, one at a time, in an engine kept per set
 * of options: every run gets a fresh global (a new {@link ScriptContext}); the
 * engine - and the debugger and server attached to it - lives on across runs.
 * Always compiled with {@code --debugger}, which is what lets Stop end a
 * script that would otherwise never return, and what a client attaches to.
 */
public final class ScriptRunner {

    /** Where a run's output goes. Called on the worker thread. */
    public interface Console {
        /** Standard output. */
        void out(String text);
        /** Error output. */
        void err(String text);
        /** A statement's value, next to its line, in the echo mode. */
        void valueAtLine(int line, String text);
        /**
         * A statement is about to run, in the echo mode: a console that mirrors
         * the script line by line moves to that line first, so what the statement
         * prints lands beside it.
         * @param line the statement's line, zero based
         */
        default void statementAt(final int line) {
        }
    }

    /** What happened to a run. Called on the worker thread. */
    public interface Listener {
        /** A run started. */
        void started();
        /** A run ended. */
        void finished(Result result);
    }

    /**
     * How a run ended.
     * @param millis how long it took
     * @param failure what it threw, or null
     * @param terminated whether Stop ended it
     */
    public record Result(long millis, Throwable failure, boolean terminated) {
        /** Whether the run completed normally. */
        public boolean ok() {
            return failure == null && !terminated;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "playground-script");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<Run> current = new AtomicReference<>();
    /** The running sample's sibling files, which its imports resolve to. */
    private volatile Map<String, String> currentFiles = Map.of();
    private ScriptEngine engine;
    private List<String> engineOptions;
    private CdpServer.Handle debugServer;
    private volatile boolean pauseOnNextRun;

    private static final class Run {
        final Thread thread = Thread.currentThread();
        final ScriptEngine engine;
        volatile boolean stopRequested;
        Run(final ScriptEngine engine) {
            this.engine = engine;
        }
    }

    /**
     * Runs a script; a run already queued and not started is dropped.
     *
     * @param sample the sample the script comes from (its options and files)
     * @param source the script text, as edited
     * @param echo whether to evaluate statement by statement and report values
     * @param console where output goes
     * @param listener who to tell
     * @return the run, cancellable while it waits
     */
    public Future<?> run(final Sample sample, final String source, final boolean echo, final Console console, final Listener listener) {
        return worker.submit(() -> execute(sample, source, echo, console, listener));
    }

    /**
     * Stops the running script, if any: the debugger pauses it at its next
     * statement and ends it there; a script blocked in Java is interrupted.
     */
    public void stop() {
        final Run run = current.get();
        if (run == null) {
            return;
        }
        run.stopRequested = true;
        final Debugger debugger = Debugger.of(run.engine);
        debugger.addListener(new DebugListener() {
            @Override
            public void paused(final PausedEvent event) {
                debugger.removeListener(this);
                event.terminate();
            }
        });
        debugger.pause();
        run.thread.interrupt();
    }

    /** Whether a script is running. */
    public boolean isRunning() {
        return current.get() != null;
    }

    /**
     * Starts or stops the Chrome DevTools Protocol server on the engine.
     * @param on whether to serve
     * @param port the port; 0 for any
     * @return the WebSocket url to attach to, or null when off
     * @throws IOException if the port cannot be bound
     */
    public synchronized String debugInChrome(final boolean on, final int port) throws IOException {
        if (!on) {
            if (debugServer != null) {
                debugServer.close();
                debugServer = null;
            }
            return null;
        }
        if (debugServer == null) {
            debugServer = CdpServer.open(Debugger.of(engine(List.of())), InspectOptions.parse(Integer.toString(port), false));
        }
        return debugServer.webSocketUrl();
    }

    /**
     * The Chrome DevTools Protocol server's WebSocket url, or null when the
     * server is off. Lets an in-process client find the same endpoint the
     * status line shows, without disturbing the server.
     * @return the {@code ws://...} url, or null
     */
    public synchronized String debugUrl() {
        return debugServer == null ? null : debugServer.webSocketUrl();
    }

    /** Makes the next run - and only it - pause at its first statement, for a client that is attached. */
    public void pauseOnNextRun(final boolean pause) {
        pauseOnNextRun = pause;
    }

    /**
     * Drops the debugger's record of parsed scripts on the current engine, so a
     * debugger that attaches now sees a clean Sources list rather than replaying
     * the previous snippet's scripts on {@code Debugger.enable}. Call it before
     * (re)opening a debugger on a snippet. Breakpoints survive.
     */
    public synchronized void clearDebugScripts() {
        if (engine != null) {
            Debugger.of(engine).clearScripts();
        }
    }

    /** Shuts the worker and the server down. */
    public void close() {
        stop();
        try {
            debugInChrome(false, 0);
        } catch (final IOException ignored) {
            // closing
        }
        worker.shutdownNow();
    }

    /**
     * The engine for a set of options, made when the options change. The
     * debugger option is always on; the server, if any, stays attached.
     */
    private synchronized ScriptEngine engine(final List<String> options) {
        if (engine == null || !options.equals(engineOptions)) {
            final boolean serving = debugServer != null;
            if (serving) {
                debugServer.close();
                debugServer = null;
            }
            engine = new NashornScriptEngineBuilder()
                    .debugger(true)
                    .dumpStackOnError(true)
                    // the standard libraries and the Node modules are contributed
                    // explicitly - the engine discovers nothing on its own
                    .library(new HostLibrary(), new FetchLibrary())
                    .moduleLoader(new NodeModuleLoader())
                    // a sample's imports resolve to its own sibling files: main.js
                    // may be a module, and 'import x from "./data.js"' finds the tab.
                    // NodeModuleLoader above answers the bare Node specifiers first
                    // and passes everything else through to here.
                    .moduleLoader((specifier, referrer) -> {
                        final String clean = specifier.startsWith("./") ? specifier.substring(2) : specifier;
                        final String text = currentFiles.get(clean);
                        return text == null ? null : Module.source("sample:" + clean, text);
                    })
                    .option(options.toArray(new String[0]))
                    .build();
            engineOptions = options;
            if (serving) {
                try {
                    debugInChrome(true, InspectOptions.DEFAULT_PORT);
                } catch (final IOException ignored) {
                    // the panel shows the server as off
                }
            }
        }
        return engine;
    }

    private void execute(final Sample sample, final String source, final boolean echo, final Console console, final Listener listener) {
        final ScriptEngine eng = engine(sample.options());
        final Run run = new Run(eng);
        current.set(run);
        listener.started();
        final long start = System.nanoTime();
        Throwable failure = null;
        try {
            // Each run is independent - a fresh global - so drop the scripts the
            // previous runs left in the debugger's registry before this one parses.
            // A connected debugger (the built-in panel or Chrome) clears its Sources
            // view without the connection dropping; breakpoints survive by url.
            Debugger.of(eng).clearScripts();
            final ScriptContext context = new SimpleScriptContext();
            context.setBindings(eng.createBindings(), ScriptContext.ENGINE_SCOPE);
            context.setWriter(new PrintWriter(new ConsoleWriter(console, false), true));
            context.setErrorWriter(new PrintWriter(new ConsoleWriter(console, true), true));
            context.setAttribute(ScriptEngine.FILENAME, sample.fileName(), ScriptContext.ENGINE_SCOPE);
            context.setAttribute("snippet", new SnippetFiles(sample.files()), ScriptContext.ENGINE_SCOPE);
            currentFiles = sample.files();
            eng.setContext(context);
            // a stable url per file, so a debugger's breakpoints survive a re-run:
            // without it the engine mints a fresh nashorn://script/<id>/... every eval
            // and a by-url breakpoint never re-resolves. Appended, it does not shift
            // the real code's line numbers.
            final String toEval = withStableUrl(sample.fileName(), source);
            if (pauseOnNextRun) {
                pauseOnNextRun = false;   // one shot: the Debug button arms it per run
                Debugger.of(eng).pauseOnStart();
            }
            if (echo) {
                // one eval of the untouched source: the engine's trace hooks
                // announce each statement and each completion value, so nothing
                // is rewritten and a debugging client sees one plain script
                final Debugger debugger = Debugger.of(eng);
                final TraceListener trace = new TraceListener() {
                    @Override
                    public void statementReached(final DebugScript script, final int line, final int column, final int depth) {
                        if (depth == 1 && script.name().equals(sample.fileName())) {
                            console.statementAt(line);
                        }
                    }

                    @Override
                    public void completionValue(final DebugScript script, final int line, final Object value) {
                        if (script.name().equals(sample.fileName())) {
                            final String text = format(debugger, value);
                            if (text != null) {
                                console.valueAtLine(line, text);
                            }
                        }
                    }
                };
                debugger.addTraceListener(trace);
                try {
                    eng.eval(toEval);
                } finally {
                    debugger.removeTraceListener(trace);
                }
            } else {
                eng.eval(toEval);
            }
        } catch (final Throwable t) {
            failure = t;
        }
        finish(run, listener, start, failure);
    }

    /**
     * The script with a stable {@code //# sourceURL} appended, unless it already
     * declares one: the engine honours it as the script's url, so a debugger's
     * by-url breakpoints re-resolve across runs. Appended as the last line, it
     * leaves every real line number where it was.
     * @param fileName the sample's file name
     * @param source the script
     * @return the script to evaluate
     */
    private static String withStableUrl(final String fileName, final String source) {
        if (source.contains("sourceURL=")) {
            return source;
        }
        final String separator = source.isEmpty() || source.endsWith("\n") ? "" : "\n";
        return source + separator + "//# sourceURL=playground:///" + fileName + "\n";
    }

    /**
     * The echo mode's value formatting, on the script thread inside the trace
     * callback, where the debugger's evaluate is safe: nothing for undefined
     * and null (a statement run for its effect logs no value), a function as
     * its first line, a string or an object as JSON when it stringifies, and
     * anything else as the debugger describes it.
     */
    private static String format(final Debugger debugger, final Object value) {
        if (value == null) {
            return null;
        }
        final DebugValues values = debugger.values();
        final String type = values.type(value);
        switch (type) {
        case "undefined":
            return null;
        case "function": {
            final String text = values.description(value);
            final int newline = text.indexOf('\n');
            return newline < 0 ? text : text.substring(0, newline) + " \u2026";
        }
        case "string":
        case "object": {
            final List<ExecutionContext> contexts = debugger.executionContexts();
            if (!contexts.isEmpty()) {
                try {
                    final Object json = values.evaluateWith(contexts.get(contexts.size() - 1), "JSON.stringify(this)", value);
                    if (json instanceof CharSequence text) {
                        return text.toString();
                    }
                } catch (final DebugException | RuntimeException unserializable) {
                    // cyclic or host-only: fall through to the description
                }
            }
            return values.description(value);
        }
        default:
            return values.description(value);
        }
    }

    /** Reports how the run ended; always called exactly once per run. */
    private Object finish(final Run run, final Listener listener, final long start, final Throwable failure) {
        Thread.interrupted();
        current.set(null);
        final boolean terminated = run.stopRequested || isTerminated(failure);
        listener.finished(new Result((System.nanoTime() - start) / 1_000_000, terminated ? null : failure, terminated));
        return null;
    }

    private static boolean isTerminated(final Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ScriptTerminated) {
                return true;
            }
        }
        return false;
    }

    /**
     * The message a failure deserves: the script's own stack for a script
     * error, the message for anything else.
     * @param failure the failure
     * @return the text
     */
    public static String describe(final Throwable failure) {
        Throwable t = failure;
        while (t instanceof ScriptException se && se.getCause() != null) {
            t = se.getCause();
        }
        if (t instanceof NashornException ne) {
            final String stack = NashornException.getScriptStackString(ne);
            return ne.getMessage() + (stack.isEmpty() ? "" : "\n" + stack);
        }
        return t instanceof ScriptException || t instanceof IllegalArgumentException ? t.getMessage() : String.valueOf(t);
    }

    /** What the {@code snippet} binding offers a script: the sample's other files. */
    public static final class SnippetFiles {
        private final Map<String, String> files;

        SnippetFiles(final Map<String, String> files) {
            this.files = files;
        }

        /**
         * The text of a sibling file of the sample.
         * @param name the file name
         * @return its text
         */
        public String text(final String name) {
            final String text = files.get(name);
            if (text == null) {
                throw new IllegalArgumentException("no file named " + name + " in this sample; there are " + files.keySet());
            }
            return text;
        }

        /** The names of the sample's other files. */
        public String[] names() {
            return files.keySet().toArray(new String[0]);
        }
    }

    private static final class ConsoleWriter extends Writer {
        private final Console console;
        private final boolean error;

        ConsoleWriter(final Console console, final boolean error) {
            this.console = console;
            this.error = error;
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) {
            final String text = new String(cbuf, off, len);
            if (error) {
                console.err(text);
            } else {
                console.out(text);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
