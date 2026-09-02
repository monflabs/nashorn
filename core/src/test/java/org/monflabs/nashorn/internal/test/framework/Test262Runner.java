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

package org.monflabs.nashorn.internal.test.framework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.ErrorManager;
import org.monflabs.nashorn.internal.runtime.ModuleRecord;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Source;
import org.monflabs.nashorn.internal.runtime.options.Options;

/**
 * Runs the ECMAScript 2017 slice of a modern test262 checkout.
 *
 * This is a separate runner from {@link ParallelTestRunner} because the modern
 * suite is driven by YAML frontmatter rather than by Nashorn's own {@code @test}
 * comment tags: a test declares how many times it must run (strict, sloppy or
 * both), which harness files it needs, and - if it is a negative test - exactly
 * which error it must fail with and in which phase. The old runner has none of
 * that; it reads an expected-error regex and never checks it.
 *
 * <p>Results are compared against an expectations file so that the run fails on
 * an unexpected failure <em>and</em> on an unexpected pass. Conformance can then
 * only ever move forwards.
 *
 * <p>System properties:
 * <dl>
 *   <dt>test262.suite.dir</dt><dd>root of the test262 checkout (required)</dd>
 *   <dt>test262.expectations</dt><dd>known-failure file (required)</dd>
 *   <dt>test262.include</dt><dd>path substring, to run a slice while developing</dd>
 *   <dt>test262.threads</dt><dd>worker count, defaults to the CPU count</dd>
 *   <dt>test262.write.expectations</dt><dd>rewrite the expectations file from this run</dd>
 * </dl>
 */
public final class Test262Runner {
    private static final String ASYNC_COMPLETE = "Test262:AsyncTestComplete";

    /** Harness files every non-raw test gets. */
    private static final List<String> DEFAULT_HARNESS = List.of("assert.js", "sta.js");

    /**
     * How long one execution may take. A handful of tests loop forever on an
     * engine that is missing the feature they exercise, and an async test whose
     * promise never settles would otherwise hang the whole run.
     */
    private static final long TIMEOUT_SECONDS = Long.getLong("test262.timeout.seconds", 40L);

    /** The name the host object's bootstrap is compiled under. */
    private static final String HOST_OBJECT_NAME = "<$262>";

    /** The host object test262 expects, as source, evaluated into every realm. */
    private static final String HOST_OBJECT =
            "var HOST = Java.type('org.monflabs.nashorn.internal.test.framework.Test262Host');"
            + "var $262 = {"
            + "  global: this,"
            + "  evalScript: function (source) { return HOST.evalScript(String(source)); },"
            + "  gc: function () { java.lang.System.gc(); },"
            + "  detachArrayBuffer: function (buffer) {"
            + "    Java.type('org.monflabs.nashorn.internal.test.framework.Test262Host').detachArrayBuffer(buffer);"
            + "  },"
            + "  agent: {"
            + "    start: function (source) { HOST.agentStart(String(source)); },"
            + "    broadcast: function (sab) { HOST.agentBroadcast(sab); },"
            + "    getReport: function () { return HOST.agentGetReport(); },"
            + "    sleep: function (ms) { HOST.agentSleep(ms); },"
            + "    monotonicNow: function () { return HOST.agentMonotonicNow(); }"
            + "  },"
            + "  createRealm: function () {"
            + "    return loadWithNewGlobal({ name: 'realm', script: "
            + "      \"var HOST = Java.type('org.monflabs.nashorn.internal.test.framework.Test262Host');\""
            + "      + \"var $262 = { global: this, evalScript: function (s) { return HOST.evalScript(String(s)); },\""
            + "      + \" gc: function () {}, detachArrayBuffer: function (b) { Java.type('org.monflabs.nashorn.internal.test.framework.Test262Host').detachArrayBuffer(b); },\""
            + "      + \" createRealm: function () { throw new Error('nested createRealm is not supported'); } }; $262\""
            + "    });"
            + "  }"
            + "};";

    /** How many executions one engine serves before it is thrown away and rebuilt. */
    private static final int EXECUTIONS_PER_ENGINE =
            Integer.getInteger("test262.executions.per.engine", 250);

    private final Path suiteRoot;
    private final Path harnessDir;
    private final ConcurrentMap<String, Source> harnessSources = new ConcurrentHashMap<>();

    private Test262Runner(final Path suiteRoot) {
        this.suiteRoot  = suiteRoot;
        this.harnessDir = suiteRoot.resolve("harness");
    }

    /**
     * One execution of one test file. A test with no strictness flag produces
     * two of these.
     */
    /**
     * How long one execution is given.
     *
     * A test that starts agents waits for threads of its own, which takes
     * longer than anything else here even with the machine to itself.
     */
    private static long timeoutFor(final Variant variant) {
        return startsAgents(variant) ? TIMEOUT_SECONDS * 3 : TIMEOUT_SECONDS;
    }

    /** Whether a test starts agents of its own, which are threads. */
    private static boolean startsAgents(final Variant variant) {
        return variant.frontmatter() != null && variant.frontmatter().getFeatures().contains("Atomics");
    }

    private record Variant(Path file, Test262Frontmatter frontmatter, boolean strict) {
        String id(final Path root) {
            return root.relativize(file).toString().replace('\\', '/') + (strict ? " (strict)" : " (sloppy)");
        }
    }

    private sealed interface Result {
        record Pass() implements Result {}
        record Fail(String reason) implements Result {}
    }

    /**
     * Entry point.
     *
     * @param args unused; configuration comes from system properties
     * @throws Exception if the suite cannot be read
     */
    public static void main(final String[] args) throws Exception {
        final Path suite = Path.of(required("test262.suite.dir"));
        final Path expectationsFile = Path.of(required("test262.expectations"));
        final String include = System.getProperty("test262.include", "");
        final int threads = Integer.getInteger("test262.threads", Runtime.getRuntime().availableProcessors());

        final Test262Runner runner = new Test262Runner(suite);
        final List<Variant> variants = runner.discover(include);

        final int shards = Integer.getInteger("test262.shards", DEFAULT_SHARDS);
        final int shard = Integer.getInteger("test262.shard", -1);

        if (shard < 0 && shards > 1) {
            System.out.printf("test262: %d executions from %s, in %d processes%n",
                    variants.size(), suite, shards);
            System.exit(runShards(shards, expectationsFile));
        }

        final List<Variant> mine = shard < 0 ? variants : slice(variants, shard, shards);
        if (shard < 0) {
            System.out.printf("test262: %d executions from %s%n", mine.size(), suite);
        }

        // A test that starts agents waits for them by spinning on a word they
        // share, so it needs a core to spare: a pool of those starves the very
        // agents they are waiting for, and one that took ten seconds on its own
        // has taken two minutes in company. They go last, one at a time.
        final List<Variant> spinners = mine.stream().filter(Test262Runner::startsAgents).toList();
        final List<Variant> rest = mine.stream().filter(v -> !startsAgents(v)).toList();

        final Map<String, String> results = new java.util.HashMap<>(runner.runAll(rest, threads));
        if (!spinners.isEmpty()) {
            results.putAll(runner.runAll(spinners, 1));
        }
        if (shard >= 0) {
            // a shard reports its failures to the parent rather than judging them
            writeFailures(results, Path.of(required("test262.shard.output")));
            System.exit(0);
        }
        final int exitCode = report(results, expectationsFile);
        System.exit(exitCode);
    }

    /**
     * How many processes the run is split across.
     *
     * One JVM cannot see the whole suite through any more. A generator's body
     * runs on its own thread, and a body that loops without ever yielding - which
     * this suite contains - can never be asked to stop, so it holds its realm for
     * as long as the process lives. The heap fills somewhere past forty thousand
     * executions and a worker dies, which used to show up only as a conformance
     * number that moved by thousands between runs. Splitting the run bounds what
     * any one process has to hold.
     */
    private static final int DEFAULT_SHARDS = Integer.getInteger("test262.default.shards", 4);

    /** The variants this shard is responsible for. */
    private static List<Variant> slice(final List<Variant> variants, final int shard, final int shards) {
        final int size = (variants.size() + shards - 1) / shards;
        final int from = Math.min(shard * size, variants.size());
        return variants.subList(from, Math.min(from + size, variants.size()));
    }

    /** Runs each shard in a child JVM and judges the merged result. */
    private static int runShards(final int shards, final Path expectationsFile) throws Exception {
        final Map<String, String> merged = new java.util.TreeMap<>();

        for (int shard = 0; shard < shards; shard++) {
            final Path output = Files.createTempFile("test262-shard", ".txt");
            try {
                final List<String> command = new java.util.ArrayList<>();
                command.add(ProcessHandle.current().info().command().orElse("java"));
                // The suite runs on a module path with a long list of exports, so
                // the child needs this JVM's own arguments; a bare classpath
                // leaves it unable to load the engine at all.
                command.addAll(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
                command.add("-cp");
                command.add(System.getProperty("java.class.path"));
                System.getProperties().stringPropertyNames().stream()
                        .filter(name -> name.startsWith("test262.") || name.startsWith("nashorn."))
                        .forEach(name -> command.add("-D" + name + "=" + System.getProperty(name)));
                command.add("-Dtest262.shard=" + shard);
                command.add("-Dtest262.shards=" + shards);
                command.add("-Dtest262.shard.output=" + output);
                command.add(Test262Runner.class.getName());

                System.out.printf("  shard %d/%d%n", shard + 1, shards);
                System.out.flush();
                final Process process = new ProcessBuilder(command).inheritIO().start();
                if (process.waitFor() != 0) {
                    throw new IllegalStateException("shard " + shard + " did not finish");
                }
                for (final String line : Files.readAllLines(output)) {
                    final int split = line.indexOf(" # ");
                    if (split > 0) {
                        merged.put(line.substring(0, split), line.substring(split + 3));
                    }
                }
            } finally {
                Files.deleteIfExists(output);
            }
        }
        return report(merged, expectationsFile);
    }

    /** A shard's failures, one per line, for the parent to merge. */
    private static void writeFailures(final Map<String, String> failures, final Path file) throws IOException {
        final List<String> lines = new java.util.ArrayList<>();
        failures.forEach((id, reason) -> lines.add(id + " # " + reason));
        Files.write(file, lines);
    }

    private static String required(final String property) {
        final String value = System.getProperty(property);
        if (value == null) {
            throw new IllegalStateException("-D" + property + " must be set");
        }
        return value;
    }

    /** Walks the suite, keeps what is in scope, and expands strict/sloppy variants. */
    private List<Variant> discover(final String include) throws IOException {
        final List<Variant> variants = new ArrayList<>();
        try (Stream<Path> files = Files.walk(suiteRoot.resolve("test"))) {
            files.filter(p -> p.toString().endsWith(".js"))
                 .filter(p -> include.isEmpty() || p.toString().contains(include))
                 .sorted()
                 .forEach(file -> {
                     final Test262Frontmatter frontmatter;
                     try {
                         frontmatter = Test262Frontmatter.parse(Files.readString(file));
                     } catch (final IOException e) {
                         throw new UncheckedIOException(e);
                     } catch (final RuntimeException e) {
                         // an unparseable header is a suite problem, not an engine one
                         System.err.println("skipping (bad frontmatter): " + file + " - " + e);
                         return;
                     }
                     if (!Test262Selector.isInScope(suiteRoot, file, frontmatter)) {
                         return;
                     }
                     if (frontmatter == null) {
                         variants.add(new Variant(file, null, false));
                         return;
                     }
                     if (frontmatter.runsSloppy()) {
                         variants.add(new Variant(file, frontmatter, false));
                     }
                     if (frontmatter.runsStrict()) {
                         variants.add(new Variant(file, frontmatter, true));
                     }
                 });
        }
        return variants;
    }

    private Map<String, String> runAll(final List<Variant> variants, final int threads) throws InterruptedException {
        final ConcurrentMap<String, String> failures = new ConcurrentHashMap<>();
        final AtomicInteger done = new AtomicInteger();
        // Counted separately from the progress tally so that a worker dying -
        // of an OutOfMemoryError, say - cannot pass for a run in which those
        // tests simply happened to pass.
        final AtomicInteger completed = new AtomicInteger();
        final List<Thread> workers = new ArrayList<>();
        final int chunk = (variants.size() + threads - 1) / Math.max(threads, 1);

        for (int t = 0; t < threads; t++) {
            final int from = t * chunk;
            final int to = Math.min(from + chunk, variants.size());
            if (from >= to) {
                break;
            }
            final Thread worker = new Thread(() -> {
                Sandbox sandbox = new Sandbox();
                int sinceRecycled = 0;
                for (final Variant variant : variants.subList(from, to)) {
                  // Nothing in here may escape: a worker that dies takes the rest
                  // of its chunk with it, and tests that never ran are
                  // indistinguishable from tests that passed. The tally is taken
                  // in the finally, so it counts each variant exactly once
                  // whichever way it ended.
                  try {
                    if (++sinceRecycled > EXECUTIONS_PER_ENGINE) {
                        // A worker's engine accumulates: compiled classes in its
                        // class cache, and a parked thread for every generator a
                        // test left mid-flight. Left alone for the whole run the
                        // later tests start timing out and exhausting the heap,
                        // and which ones do moves from run to run - so the suite
                        // stops being a ratchet at all. Recycling bounds it.
                        sandbox.discard();
                        sandbox = null;
                        // Give the collector a chance before building the next
                        // one. What accumulates is not reachable - a discarded
                        // engine's compiled classes, and the parked thread of
                        // every generator a test left mid-flight, which only a
                        // Cleaner can release - so without this the heap grows
                        // until a worker dies and takes the rest of its chunk
                        // with it.
                        System.gc();
                        sandbox = new Sandbox();
                        sinceRecycled = 0;
                    }
                    final Result result;
                    try {
                        result = sandbox.run(variant);
                    } catch (final TimeoutException e) {
                        // the engine thread is wedged; abandon it and start a clean one
                        failures.put(variant.id(suiteRoot), "timed out after " + timeoutFor(variant) + "s");
                        sandbox.discard();
                        sandbox = new Sandbox();
                        sinceRecycled = 0;
                        continue;
                    } catch (final Throwable t2) {
                        failures.put(variant.id(suiteRoot), "runner error: " + t2);
                        continue;
                    }
                    if (result instanceof Result.Fail fail) {
                        failures.put(variant.id(suiteRoot), fail.reason());
                    }
                    final int n = done.incrementAndGet();
                    if (n % 2000 == 0) {
                        System.out.println("  " + n + "/" + variants.size() + " executions, " + failures.size() + " failing");
                        System.out.flush();
                    }
                  } catch (final Throwable fatal) {
                    // Most often an OutOfMemoryError while building a fresh
                    // engine. Record it against this test and carry on with a
                    // clean one rather than losing the remainder of the chunk.
                    failures.putIfAbsent(variant.id(suiteRoot), "runner error: " + fatal);
                    try {
                        sandbox.discard();
                    } catch (final Throwable ignored) {
                        // discarding a broken sandbox may fail too
                    }
                    sandbox = new Sandbox();
                    sinceRecycled = 0;
                  } finally {
                    completed.incrementAndGet();
                  }
                }
                sandbox.discard();
            }, "test262-" + t);
            workers.add(worker);
            worker.start();
        }
        for (final Thread worker : workers) {
            worker.join();
        }

        if (completed.get() != variants.size()) {
            throw new IllegalStateException(String.format(
                    "the run did not finish: %d of %d executions produced a verdict. "
                    + "A test that never ran is indistinguishable from one that passed, so "
                    + "the result cannot be compared against the expectations.",
                    completed.get(), variants.size()));
        }
        return failures;
    }

    /**
     * An {@link Engine} on a thread of its own, so that a test which never
     * returns can be abandoned without taking the run down with it.
     */
    private final class Sandbox {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "test262-engine");
            thread.setDaemon(true);
            return thread;
        });
        private final Engine engine = new Engine();

        Result run(final Variant variant) throws Exception {
            final Future<Result> future = executor.submit(() -> engine.run(variant));
            try {
                return future.get(timeoutFor(variant), TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        }

        void discard() {
            executor.shutdownNow();
        }
    }

    /** A Nashorn context, reused across the tests one worker thread owns. */
    private final class Engine {
        private final Context context;
        private final ErrorManager errors;
        private final SwappableStream out = new SwappableStream();
        private final SwappableStream err = new SwappableStream();

        Engine() {
            final PrintWriter outWriter = new PrintWriter(out, true);
            final PrintWriter errWriter = new PrintWriter(err, true);
            final Options options = new Options("nashorn", errWriter);
            // The run is made with a Turkish default locale on purpose, to
            // catch a case conversion in the engine that forgot to name one.
            // The locale a script sees is a different thing: toLocaleUpperCase
            // answers for the host's, and the suite is written for a host whose
            // is not one where "i" has a dot when it grows.
            // Libraries are never discovered now, and this runner passes none,
            // so the conformance globals stay pristine without an option.
            options.process(new String[] { "--class-cache-size=50",
                    "--locale=en-US" });
            this.errors = new ErrorManager(errWriter);
            // negative tests are expected to produce parse errors by the thousand;
            // the default limit of 100 would abort the run
            this.errors.setLimit(0);
            this.context = new Context(options, errors, outWriter, errWriter,
                    Thread.currentThread().getContextClassLoader());
        }

        Result run(final Variant variant) throws IOException {
            final Test262Frontmatter fm = variant.frontmatter();
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            out.setDelegate(stdout);
            err.setDelegate(stderr);

            final int errorsBefore = errors.getNumberOfErrors();
            // a fresh realm per execution: tests mutate the global freely
            final Global global = context.createGlobal();
            try {
                return Context.callWithGlobal(global, () -> {

                if (fm != null && !fm.isRaw()) {
                    installHostObject(global);
                    for (final String harness : DEFAULT_HARNESS) {
                        loadHarness(global, harness);
                    }
                    if (fm.isAsync()) {
                        loadHarness(global, "doneprintHandle.js");
                    }
                    for (final String include : fm.getIncludes()) {
                        loadHarness(global, include);
                    }
                }

                return runTest(variant, global, errorsBefore, stdout, stderr);
                });
            } finally {
                context.getOut().flush();
                context.getErr().flush();
                // A generator this test left suspended holds its function, and
                // through it this whole realm, so without this the run retains a
                // realm for every generator it starts and eventually dies of it.
                global.abandonGenerators();
            }
        }

        private Result runTest(final Variant variant, final Global global, final int errorsBefore,
                final ByteArrayOutputStream stdout, final ByteArrayOutputStream stderr) throws IOException {
            final Test262Frontmatter fm = variant.frontmatter();
            final String body = Files.readString(variant.file());
            final String source = variant.strict() ? "\"use strict\";\n" + body : body;
            final boolean negative = fm != null && fm.isNegative();
            final String expectedType = negative ? fm.getNegativeType() : null;
            final boolean expectParseFailure = negative && !"runtime".equals(fm.getNegativePhase());

            final boolean module = fm != null && fm.isModule();

            ScriptFunction script = null;
            ModuleRecord moduleRecord = null;
            String parseError = null;
            try {
                if (module) {
                    // linking is a pass of its own, before anything runs, so a
                    // resolution error arrives here rather than at evaluation
                    final ModuleRecord loaded = context.loadModule(variant.file().toString(), null);
                    if (loaded != null) {
                        // assigned only once linking has succeeded, so that a
                        // resolution error reads as the failure it is
                        loaded.link();
                    }
                    moduleRecord = loaded;
                } else {
                    script = context.compileScript(
                            Source.sourceFor(variant.file().toString(), source), global);
                }
            } catch (final Throwable t) {
                parseError = describe(t);
            }
            if ((module ? moduleRecord == null : script == null) || errors.getNumberOfErrors() > errorsBefore) {
                if (parseError == null) {
                    parseError = stderr.toString(StandardCharsets.UTF_8).trim();
                }
                if (expectParseFailure) {
                    // Nashorn reports every early error as a SyntaxError
                    return "SyntaxError".equals(expectedType)
                        ? new Result.Pass()
                        : new Result.Fail("expected " + expectedType + " at "
                                + fm.getNegativePhase() + ", got a parse error: " + firstLine(parseError));
                }
                return new Result.Fail("unexpected parse error: " + firstLine(parseError));
            }
            if (expectParseFailure) {
                return new Result.Fail("expected " + expectedType + " at " + fm.getNegativePhase()
                        + ", but it parsed");
            }

            try {
                if (module) {
                    moduleRecord.evaluate();
                } else {
                    ScriptRuntime.apply(script, global);
                }
            } catch (final Throwable t) {
                final String thrownType = typeOf(t);
                if (negative) {
                    return expectedType.equals(thrownType)
                        ? new Result.Pass()
                        : new Result.Fail("expected " + expectedType + ", got " + thrownType + ": " + describe(t));
                }
                return new Result.Fail(describe(t));
            }

            if (negative) {
                return new Result.Fail("expected " + expectedType + ", but the test completed");
            }
            if (fm != null && fm.isAsync()) {
                final String printed = stdout.toString(StandardCharsets.UTF_8);
                return printed.contains(ASYNC_COMPLETE)
                    ? new Result.Pass()
                    : new Result.Fail("async test did not complete: " + firstLine(printed.trim()));
            }
            return new Result.Pass();
        }

        /**
         * Installs $262, the object test262 expects its host to provide.
         *
         * evalScript evaluates a Script, which only the host can do: an eval,
         * direct or not, would give the source a lexical environment of its own
         * rather than the global one a script's declarations belong to.
         * createRealm builds a fresh realm and hands back its own $262, which
         * loadWithNewGlobal is exactly the right shape for. detachArrayBuffer
         * needs engine support that does not exist yet and says so rather than
         * failing obscurely.
         */
        private void installHostObject(final Global global) {
            // agents belong to the execution that started them, and one that
            // ran on is not a report the next execution should be able to see
            Test262Host.agentReset();
            final Source source = harnessSources.computeIfAbsent(HOST_OBJECT_NAME,
                    n -> Source.sourceFor(n, HOST_OBJECT));
            final ScriptFunction install = context.compileScript(source, global);
            if (install != null) {
                ScriptRuntime.apply(install, global);
            }
        }

        private void loadHarness(final Global global, final String name) {
            final Source source = harnessSources.computeIfAbsent(name, n -> {
                try {
                    return Source.sourceFor(n, Files.readString(harnessDir.resolve(n)));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            final ScriptFunction harness = context.compileScript(source, global);
            if (harness == null) {
                throw new IllegalStateException("harness failed to compile: " + name);
            }
            ScriptRuntime.apply(harness, global);
        }
    }

    /** The constructor name of a thrown JavaScript value, or the Java class name. */
    private static String typeOf(final Throwable t) {
        if (t instanceof ECMAException ecma && ecma.getThrown() instanceof ScriptObject thrown) {
            final Object name = thrown.get("name");
            if (!isUndefined(name)) {
                return name.toString();
            }
            // Test262Error has no name property of its own, so what it is called
            // has to come from what made it
            final Object constructor = thrown.get("constructor");
            if (constructor instanceof ScriptFunction made && !made.getName().isEmpty()) {
                return made.getName();
            }
        }
        return t.getClass().getSimpleName();
    }

    /**
     * A readable one-liner for a thrown value. For a JavaScript error that means
     * its name and message - {@code getMessage()} on the wrapper just yields
     * "[object Object]".
     */
    private static String describe(final Throwable t) {
        if (t instanceof ECMAException ecma && ecma.getThrown() instanceof ScriptObject thrown) {
            final Object message = thrown.get("message");
            return firstLine(typeOf(t) + (isUndefined(message) ? "" : ": " + message));
        }
        final String message = t.getMessage();
        return message == null ? t.toString() : firstLine(message);
    }

    private static boolean isUndefined(final Object value) {
        return value == null || value instanceof org.monflabs.nashorn.internal.runtime.Undefined;
    }

    /**
     * The first line of a message, trimmed and reduced to printable ASCII.
     *
     * Reasons end up in a checked-in file that people read in diffs, and a JS
     * test suite throws messages containing anything at all - including lone
     * surrogates, which are not even encodable.
     */
    private static String firstLine(final String s) {
        final int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl);
        if (line.length() > 160) {
            line = line.substring(0, 160) + "...";
        }
        final StringBuilder clean = new StringBuilder(line.length());
        line.chars().forEach(c -> clean.append(c >= 0x20 && c < 0x7f ? (char)c : '?'));
        return clean.toString();
    }

    /** Compares this run against the expectations file and prints the verdict. */
    private static int report(final Map<String, String> failures, final Path expectationsFile) throws IOException {
        final Set<String> expected = Files.exists(expectationsFile)
            ? new TreeSet<>(Files.readAllLines(expectationsFile).stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .map(l -> l.split(" {2,}#")[0].strip())
                .toList())
            : Set.of();

        if (Boolean.getBoolean("test262.write.expectations")) {
            // Paths only. Why a test fails is triage information that changes
            // whenever a message is reworded; keeping it here would churn the
            // diff of a file whose whole purpose is to show what changed.
            final List<String> lines = new ArrayList<>();
            lines.add("# test262 ES2017 slice: known failures. Every line is work to do.");
            lines.add("# Regenerate with -Dtest262.write.expectations=true.");
            lines.add("# Reasons for the current run are written to target/test262-failures.txt.");
            lines.addAll(new TreeSet<>(failures.keySet()));
            Files.write(expectationsFile, lines);
            writeReasons(failures);
            System.out.println("wrote " + failures.size() + " expectations to " + expectationsFile);
            return 0;
        }
        writeReasons(failures);

        final Set<String> unexpectedFailures = new TreeSet<>(failures.keySet());
        unexpectedFailures.removeAll(expected);
        final Set<String> unexpectedPasses = new TreeSet<>(expected);
        unexpectedPasses.removeAll(failures.keySet());

        System.out.printf("%nfailing: %d   expected to fail: %d%n", failures.size(), expected.size());
        print("REGRESSION - newly failing", unexpectedFailures, failures);
        print("PROGRESSION - now passing, remove from the expectations file", unexpectedPasses, Map.of());

        return unexpectedFailures.isEmpty() && unexpectedPasses.isEmpty() ? 0 : 1;
    }

    /** The triage view: same failures, with their reasons, regenerated every run. */
    private static void writeReasons(final Map<String, String> failures) throws IOException {
        final Path report = Path.of("target", "test262-failures.txt");
        Files.createDirectories(report.getParent());
        final List<String> lines = new ArrayList<>();
        new TreeMap<>(failures).forEach((id, reason) -> lines.add(id + "  # " + reason));
        Files.write(report, lines);
    }

    private static void print(final String heading, final Set<String> ids, final Map<String, String> reasons) {
        if (ids.isEmpty()) {
            return;
        }
        System.out.printf("%n%s (%d):%n", heading, ids.size());
        ids.stream().sorted(Comparator.naturalOrder()).limit(50)
           .forEach(id -> System.out.println("  " + id + (reasons.containsKey(id) ? "  # " + reasons.get(id) : "")));
        if (ids.size() > 50) {
            System.out.printf("  … and %d more%n", ids.size() - 50);
        }
    }

    /** Lets one Context write to a different buffer for each test it runs. */
    private static final class SwappableStream extends OutputStream {
        private volatile OutputStream delegate = OutputStream.nullOutputStream();

        void setDelegate(final OutputStream stream) {
            this.delegate = stream;
        }

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
    }
}
