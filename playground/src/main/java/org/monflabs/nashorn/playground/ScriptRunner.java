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
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.monflabs.nashorn.api.debugger.DebugListener;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.debugger.PausedEvent;
import org.monflabs.nashorn.api.debugger.ScriptTerminated;
import org.monflabs.nashorn.api.scripting.NashornException;
import org.monflabs.nashorn.api.modules.Module;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
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

        /**
         * A print whose source line is known, in the echo mode: inside a loop
         * or a block, the line the call was written on rather than the
         * statement's first. Falls back to plain output.
         * @param line the call's line, zero based
         * @param text one printed line, without its newline
         */
        default void printAtLine(final int line, final String text) {
            out(text + "\n");
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

    /**
     * The echo mode's script-side helpers: the value formatter, and print and
     * console.log rerouted through __echoPrint with the line they were called
     * on - found from an Error's stack, relative to the statement being
     * evaluated (__stmtLine..__stmtEnd, maintained per statement), so output
     * from inside a loop or block lands beside its own line rather than the
     * statement's first.
     */
    private static final String FORMAT_PRELUDE = """
            function __playground_format(v) {
              if (typeof v === 'string') return JSON.stringify(v);
              if (typeof v === 'function') { var s = String(v); var i = s.indexOf('\\n'); return i < 0 ? s : s.substring(0, i) + ' \u2026'; }
              if (v !== null && typeof v === 'object') { try { var j = JSON.stringify(v); if (j !== undefined) return j; } catch (e) {} }
              return String(v);
            }
            (function (global) {
              var realPrint = global.print;
              function callerLine() {
                try { throw new Error(); } catch (e) {
                  var frames = String(e.stack).split('\\n');
                  // [Error, callerLine, the wrapper, the caller]
                  var m = frames.length > 3 ? frames[3].match(/:(\\d+)\\)?\\s*$/) : null;
                  if (!m) return -1;
                  var line = global.__stmtLine + Number(m[1]) - 1;
                  return line >= global.__stmtLine && line <= global.__stmtEnd ? line : -1;
                }
              }
              function join(args) {
                return Array.prototype.map.call(args, function (v) { return typeof v === 'symbol' ? v.toString() : String(v); }).join(' ');
              }
              global.print = function () {
                var line = callerLine();
                if (line < 0) { return realPrint.apply(null, arguments); }
                __echoPrint.accept(line, join(arguments));
              };
              if (typeof global.console === 'object') {
                var realLog = global.console.log;
                global.console.log = function () {
                  var line = callerLine();
                  if (line < 0) { return realLog.apply(global.console, arguments); }
                  __echoPrint.accept(line, join(arguments));
                };
              }
            })(this);
            """;

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

    /** Makes the next run pause at its first statement, for a client that is attached. */
    public void pauseOnNextRun(final boolean pause) {
        pauseOnNextRun = pause;
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
                    // a sample's imports resolve to its own sibling files: main.js
                    // may be a module, and 'import x from "./data.js"' finds the tab
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
            final ScriptContext context = new SimpleScriptContext();
            context.setBindings(eng.createBindings(), ScriptContext.ENGINE_SCOPE);
            context.setWriter(new PrintWriter(new ConsoleWriter(console, false), true));
            context.setErrorWriter(new PrintWriter(new ConsoleWriter(console, true), true));
            context.setAttribute(ScriptEngine.FILENAME, sample.fileName(), ScriptContext.ENGINE_SCOPE);
            context.setAttribute("snippet", new SnippetFiles(sample.files()), ScriptContext.ENGINE_SCOPE);
            currentFiles = sample.files();
            eng.setContext(context);
            if (pauseOnNextRun) {
                Debugger.of(eng).pauseOnStart();
            }
            if (echo) {
                // a module does not split into script statements: run it whole,
                // module detection and all, with no per-statement values
                final java.util.List<StatementSplitter.Statement> statements;
                try {
                    statements = StatementSplitter.split(sample.fileName(), source, sample.options());
                } catch (final IllegalArgumentException notAScript) {
                    eng.eval(source);
                    finish(run, listener, start, null);
                    return;
                }
                context.setAttribute("__echoPrint",
                        (java.util.function.BiConsumer<Object, Object>)(line, text) ->
                                console.printAtLine(((Number)line).intValue(), String.valueOf(text)),
                        ScriptContext.ENGINE_SCOPE);
                eng.eval(FORMAT_PRELUDE);
                for (final StatementSplitter.Statement statement : statements) {
                    if (run.stopRequested) {
                        break;
                    }
                    if (!statement.declaration()) {
                        console.statementAt(statement.line());
                    }
                    context.setAttribute("__stmtLine", statement.line(), ScriptContext.ENGINE_SCOPE);
                    context.setAttribute("__stmtEnd", statement.line() + (int)statement.text().chars().filter(c -> c == '\n').count(), ScriptContext.ENGINE_SCOPE);
                    final Object value = eng.eval(statement.text());
                    if (value != null && !statement.declaration()) {
                        console.valueAtLine(statement.line(), String.valueOf(((Invocable)eng).invokeFunction("__playground_format", value)));
                    }
                }
            } else {
                eng.eval(source);
            }
        } catch (final Throwable t) {
            failure = t;
        }
        finish(run, listener, start, failure);
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
