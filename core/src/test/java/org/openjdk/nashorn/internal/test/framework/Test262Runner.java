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

package org.openjdk.nashorn.internal.test.framework;

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
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ECMAException;
import org.openjdk.nashorn.internal.runtime.ErrorManager;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Source;
import org.openjdk.nashorn.internal.runtime.options.Options;

/**
 * Runs the ECMAScript 2015 slice of a modern test262 checkout.
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
    private static final long TIMEOUT_SECONDS = Long.getLong("test262.timeout.seconds", 20L);

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
        System.out.printf("test262: %d executions from %s%n", variants.size(), suite);

        final Map<String, String> results = runner.runAll(variants, threads);
        final int exitCode = report(results, expectationsFile);
        System.exit(exitCode);
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
                for (final Variant variant : variants.subList(from, to)) {
                    final Result result;
                    try {
                        result = sandbox.run(variant);
                    } catch (final TimeoutException e) {
                        // the engine thread is wedged; abandon it and start a clean one
                        failures.put(variant.id(suiteRoot), "timed out after " + TIMEOUT_SECONDS + "s");
                        sandbox.discard();
                        sandbox = new Sandbox();
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
                }
                sandbox.discard();
            }, "test262-" + t);
            workers.add(worker);
            worker.start();
        }
        for (final Thread worker : workers) {
            worker.join();
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
                return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
            options.process(new String[] { "--language=es6", "--class-cache-size=50" });
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

            final Global oldGlobal = Context.getGlobal();
            final int errorsBefore = errors.getNumberOfErrors();
            try {
                // a fresh realm per execution: tests mutate the global freely
                final Global global = context.createGlobal();
                Context.setGlobal(global);

                if (fm != null && !fm.isRaw()) {
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
            } finally {
                context.getOut().flush();
                context.getErr().flush();
                Context.setGlobal(oldGlobal);
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

            ScriptFunction script = null;
            String parseError = null;
            try {
                script = context.compileScript(
                        Source.sourceFor(variant.file().toString(), source), global);
            } catch (final Throwable t) {
                parseError = describe(t);
            }
            if (script == null || errors.getNumberOfErrors() > errorsBefore) {
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
                ScriptRuntime.apply(script, global);
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
        return value == null || value instanceof org.openjdk.nashorn.internal.runtime.Undefined;
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
            lines.add("# test262 ES2015 slice: known failures. Every line is work to do.");
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
