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

package org.openjdk.nashorn.internal.performance;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ErrorManager;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Source;
import org.openjdk.nashorn.internal.runtime.options.Options;

/**
 * Measures the things the ES2015 work could plausibly slow down, and writes the
 * numbers where a later run can compare against them.
 *
 * Octane, which is what {@code -Pbenchmark} runs, is pure ES5: it is the guard
 * against regressing existing hot paths, and it is blind to the cost of the new
 * features themselves. So this measures three separate things:
 *
 * <ul>
 *   <li><b>compile</b> - time to compile a large script. Desugaring classes,
 *       destructuring and generators adds AST passes, and this is where that
 *       shows up. Nothing measured it before; the one class that did
 *       ({@code PerformanceWrapper}) referenced Ant-era paths and threw.</li>
 *   <li><b>startup</b> - time to build a Context and a Global. Every new builtin
 *       adds properties to the global object, and the test runner alone creates
 *       one Global per test.</li>
 *   <li><b>run</b> - throughput of small scripts, including ES6 constructs as
 *       they land, from {@code src/test/scripts/perf}.</li>
 * </ul>
 *
 * Usage:
 * <pre>
 *   PerfBenchmark record  &lt;out.json&gt;                 measure and write
 *   PerfBenchmark compare &lt;baseline.json&gt; &lt;new.json&gt;  fail on regression
 * </pre>
 */
public final class PerfBenchmark {
    /**
     * How much each measurement may worsen before the comparison fails.
     *
     * These are not targets, they are measured. Five recordings of byte-identical
     * code were compared pairwise, and each band is roughly the observed spread
     * with margin:
     *
     * <pre>
     *   run.instanceof        1.0%       run.protochain      13.5%
     *   run.toprimitive       2.0%       startup.50globals   15.2%
     *   run.concat            6.6%       compile.pdfjs       24.5%
     *   run.properties       11.2%
     * </pre>
     *
     * The spread differs by an order of magnitude between metrics, so a single
     * number would either flake on compile time or go blind on instanceof. The
     * useful part is that the two tightest metrics are exactly the two the plan
     * flags as most at risk - {@code @@hasInstance} on every {@code instanceof},
     * {@code @@toPrimitive} on every coercion - so the gate is sharp where it
     * needs to be and merely a cliff detector elsewhere.
     *
     * A cliff detector is still worth having: the regressions these phases
     * actually threaten - a symbol lookup per operation, a lost inline cache -
     * cost multiples, not percents.
     *
     * For a considered decision on a small difference, re-run the comparison on
     * an idle machine with an explicit tolerance:
     * {@code -Dperf.tolerance=0.02}.
     */
    private static final Map<String, Double> TOLERANCES = Map.of(
            "run.instanceof.ms",     0.05,
            "run.toprimitive.ms",    0.05,
            "run.concat.ms",         0.12,
            "run.properties.ms",     0.20,
            "run.protochain.ms",     0.20,
            "startup.50globals.ms",  0.25,
            "compile.pdfjs.ms",      0.30);

    /** Applied to any metric not in {@link #TOLERANCES} - i.e. newly added ones. */
    private static final double DEFAULT_TOLERANCE = 0.20;

    /** Overrides every band at once, for a deliberate high-precision comparison. */
    private static final String TOLERANCE_OVERRIDE = System.getProperty("perf.tolerance");

    private static final int WARMUP = Integer.getInteger("perf.warmup", 8);
    private static final int ITERATIONS = Integer.getInteger("perf.iterations", 15);

    /**
     * How many JVMs to measure in, taking the median result of each metric.
     *
     * In-process warmup is not enough on its own. The JIT's inlining decisions
     * depend on the order profiles happen to arrive in, so two JVMs running
     * byte-identical code can settle into steady states that differ by 15%, and
     * no number of iterations inside one of them will close that gap. Measuring
     * in several JVMs does close it, which is why JMH forks by default too.
     *
     * Must be odd, so the median is a measurement rather than an average of two.
     */
    private static final int FORKS = Integer.getInteger("perf.forks", 3);

    /** Set on the children, so they measure instead of forking again. */
    private static final String CHILD_PROPERTY = "perf.fork.child";

    private PerfBenchmark() {
    }

    /**
     * Entry point.
     *
     * @param args {@code record <file>} or {@code compare <baseline> <new>}
     * @throws IOException if a file cannot be read or written
     */
    public static void main(final String[] args) throws IOException {
        if (args.length == 2 && "record".equals(args[0])) {
            final Map<String, Double> results = Boolean.getBoolean(CHILD_PROPERTY) || FORKS <= 1
                    ? measure()
                    : measureAcrossForks();
            write(Path.of(args[1]), results);
            results.forEach((k, v) -> System.out.printf("  %-40s %10.2f%n", k, v));
            System.out.println("wrote " + args[1]);
        } else if (args.length == 3 && "compare".equals(args[0])) {
            System.exit(compare(read(Path.of(args[1])), read(Path.of(args[2]))));
        } else if (args.length >= 3 && "merge".equals(args[0])) {
            final List<Path> inputs = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                inputs.add(Path.of(args[i]));
            }
            final Map<String, Double> merged = merge(inputs);
            write(Path.of(args[1]), merged);
            merged.forEach((k, v) -> System.out.printf("  %-40s %10.2f%n", k, v));
        } else {
            System.err.println("usage: PerfBenchmark record <out.json>"
                    + " | compare <baseline.json> <new.json>"
                    + " | merge <out.json> <in.json>...");
            System.exit(2);
        }
    }

    /**
     * The median of each metric across several recordings.
     *
     * Used both to combine this process's own forks and, by perf-gate.sh, to
     * combine interleaved rounds after the fact.
     *
     * @param files recordings to combine
     * @return the per-metric medians
     * @throws IOException if a recording cannot be read
     */
    private static Map<String, Double> merge(final List<Path> files) throws IOException {
        final Map<String, List<Double>> samples = new LinkedHashMap<>();
        for (final Path file : files) {
            read(file).forEach((metric, value) ->
                    samples.computeIfAbsent(metric, k -> new ArrayList<>()).add(value));
        }
        final Map<String, Double> merged = new LinkedHashMap<>();
        samples.forEach((metric, values) -> {
            java.util.Collections.sort(values);
            merged.put(metric, values.get(values.size() / 2));
        });
        return merged;
    }

    /**
     * Measures in {@link #FORKS} separate JVMs and takes the median of each metric.
     *
     * Median across forks, not minimum - the opposite of the choice inside a fork,
     * and for a reason. Within one JVM every disturbance costs time, so the fastest
     * sample is the truest. Across JVMs a sample can also come out spuriously
     * <em>fast</em>: the first process launched after a build runs on a boosted CPU
     * that has not yet settled, and measured 15% under its neighbours here. A
     * minimum would seize on exactly that outlier and bake it into the baseline,
     * making every later run look like a regression. The median discards it.
     */
    private static Map<String, Double> measureAcrossForks() throws IOException {
        final List<Path> recordings = new ArrayList<>();
        try {
            for (int fork = 0; fork < FORKS; fork++) {
                System.out.printf("fork %d/%d%n", fork + 1, FORKS);
                final Path out = Files.createTempFile("nashorn-perf-fork", ".json");
                recordings.add(out);
                runFork(out);
            }
            final Map<String, Double> merged = merge(recordings);
            if (merged.isEmpty()) {
                throw new IOException("every fork failed to produce measurements");
            }
            return merged;
        } finally {
            for (final Path recording : recordings) {
                Files.deleteIfExists(recording);
            }
        }
    }

    /** Runs this class in a child JVM, inheriting our classpath and perf.* settings. */
    private static void runFork(final Path out) throws IOException {
        final List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-cp", System.getProperty("java.class.path"),
                // Pin the heap and the collector. Left to ergonomics, G1 sizes
                // itself from the machine and resizes as it goes, so an
                // allocation-heavy benchmark lands in a different steady state
                // from one JVM to the next - concat.js swung 15% purely on this.
                "-Xms1g", "-Xmx1g", "-XX:+UseG1GC",
                "-D" + CHILD_PROPERTY + "=true"));
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith("perf.") && !name.equals(CHILD_PROPERTY))
                .forEach(name -> command.add("-D" + name + "=" + System.getProperty(name)));
        command.addAll(List.of(PerfBenchmark.class.getName(), "record", out.toString()));

        try {
            final Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            final int status = process.waitFor();
            if (status != 0) {
                throw new IOException("perf fork exited with status " + status);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for perf fork", e);
        }
    }

    /** Runs every measurement. Lower is better for times, higher for rates. */
    private static Map<String, Double> measure() throws IOException {
        final Map<String, Double> results = new LinkedHashMap<>();

        // one Global is under a millisecond, which is mostly timer and JIT noise;
        // fifty is a stable number, and fifty is also what a test run creates in
        // the blink of an eye
        results.put("startup.50globals.ms", best(() -> {
            final long start = System.nanoTime();
            final Context context = newContext();
            for (int i = 0; i < 50; i++) {
                context.createGlobal();
            }
            return (System.nanoTime() - start) / 1e6;
        }));

        final Path large = Path.of("src/test/scripts/external/octane/pdfjs.js");
        if (!Files.exists(large)) {
            // Say so. A quietly absent metric is how a comparison ends up
            // reporting "no regressions" about something it never measured.
            System.err.println("note: " + large + " missing, skipping compile benchmark"
                    + " (run: mvn -Pfetch-externals -pl core generate-test-resources)");
        } else {
            final String source = Files.readString(large);
            results.put("compile.pdfjs.ms", best(() -> {
                final Context context = newContext();
                final Global global = context.createGlobal();
                final Global old = Context.getGlobal();
                Context.setGlobal(global);
                try {
                    final long start = System.nanoTime();
                    context.compileScript(Source.sourceFor("pdfjs.js", source), global);
                    return (System.nanoTime() - start) / 1e6;
                } finally {
                    Context.setGlobal(old);
                }
            }));
        }

        for (final Path script : perfScripts()) {
            final String name = script.getFileName().toString().replace(".js", "");
            final String source = Files.readString(script);
            if (!parses(source)) {
                // A benchmark for a feature this engine does not have yet. The
                // base revision of a comparison is an older engine, and a metric
                // it cannot produce is not a metric that regressed - the
                // comparison walks the baseline's own keys, so this drops out of
                // it rather than failing it.
                System.err.println("note: " + name + " needs a feature this engine lacks, skipping");
                continue;
            }
            results.put("run." + name + ".ms", timeScript(name, source));
        }
        return results;
    }

    /** Whether this engine can compile a benchmark at all. */
    private static boolean parses(final String source) {
        final Context context = newContext();
        final Global global = context.createGlobal();
        final Global old = Context.getGlobal();
        Context.setGlobal(global);
        try {
            return context.compileScript(Source.sourceFor("<probe>", source), global) != null;
        } catch (final RuntimeException e) {
            return false;
        } finally {
            Context.setGlobal(old);
        }
    }

    private static List<Path> perfScripts() throws IOException {
        final Path dir = Path.of("src/test/scripts/perf");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        final List<Path> scripts = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".js")).sorted().forEach(scripts::add);
        }
        return scripts;
    }

    /**
     * Steady-state execution time of one script.
     *
     * The script is compiled once and then run repeatedly in the same realm, so
     * that the JIT reaches steady state - which is the thing worth measuring and
     * the thing a regression would show up in. Compiling per iteration instead
     * makes the number swing by tens of percent between identical runs.
     */
    private static double timeScript(final String name, final String source) {
        final Context context = newContext();
        final Global global = context.createGlobal();
        final Global old = Context.getGlobal();
        Context.setGlobal(global);
        try {
            final ScriptFunction script = context.compileScript(Source.sourceFor(name, source), global);
            return best(() -> {
                final long start = System.nanoTime();
                ScriptRuntime.apply(script, global);
                return (System.nanoTime() - start) / 1e6;
            });
        } finally {
            Context.setGlobal(old);
        }
    }

    private static Context newContext() {
        final PrintWriter sink = new PrintWriter(new StringWriter());
        final Options options = new Options("nashorn", sink);
        options.process(new String[] { "--language=es6" });
        return new Context(options, new ErrorManager(sink), sink, sink,
                Thread.currentThread().getContextClassLoader());
    }

    /**
     * The fastest of several timed runs, after warmup.
     *
     * Minimum rather than mean or median on purpose: every source of noise here
     * - GC, scheduling, another process on the machine - only ever makes a run
     * slower, so the fastest observation is the closest to the engine's actual
     * cost and by far the most reproducible.
     */
    private static double best(final Measurement measurement) {
        for (int i = 0; i < WARMUP; i++) {
            measurement.run();
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ITERATIONS; i++) {
            best = Math.min(best, measurement.run());
        }
        return best;
    }

    private interface Measurement {
        double run();
    }

    /**
     * Compares two runs. Every metric here is a duration, so bigger is worse.
     *
     * @return 0 when nothing regressed beyond tolerance
     */
    private static int compare(final Map<String, Double> baseline, final Map<String, Double> current) {
        int regressions = 0;
        System.out.printf("%-40s %12s %12s %9s%n", "metric", "baseline", "current", "change");
        for (final Map.Entry<String, Double> entry : new TreeMap<>(baseline).entrySet()) {
            final Double now = current.get(entry.getKey());
            if (now == null) {
                // Counts as a failure, not a note. A measurement that did not
                // happen is not a measurement that passed - letting it through
                // is how a perf gate rots into decoration.
                System.out.printf("%-40s %12.2f %12s  MISSING%n",
                        entry.getKey(), entry.getValue(), "-");
                regressions++;
                continue;
            }
            final double before = entry.getValue();
            final double ratio = (now - before) / before;
            final double tolerance = toleranceFor(entry.getKey());
            final boolean regressed = ratio > tolerance;
            if (regressed) {
                regressions++;
            }
            System.out.printf("%-40s %12.2f %12.2f %+8.1f%%  (+/-%2.0f%%) %s%n",
                    entry.getKey(), before, now, ratio * 100, tolerance * 100,
                    regressed ? "REGRESSION" : "");
        }
        if (regressions > 0) {
            System.out.printf("%n%d metric(s) regressed beyond tolerance%n", regressions);
        }
        return regressions == 0 ? 0 : 1;
    }

    /** The band for one metric - see {@link #TOLERANCES}. */
    private static double toleranceFor(final String metric) {
        if (TOLERANCE_OVERRIDE != null) {
            return Double.parseDouble(TOLERANCE_OVERRIDE);
        }
        return TOLERANCES.getOrDefault(metric, DEFAULT_TOLERANCE);
    }

    // A flat string->number map; not worth a JSON dependency in a test scope.

    private static void write(final Path file, final Map<String, Double> results) throws IOException {
        final List<String> lines = new ArrayList<>();
        lines.add("{");
        final List<String> entries = new ArrayList<>();
        results.forEach((k, v) -> entries.add(String.format("  \"%s\": %.4f", k, v)));
        lines.add(String.join(",\n", entries));
        lines.add("}");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines);
    }

    private static Map<String, Double> read(final Path file) throws IOException {
        final Map<String, Double> results = new LinkedHashMap<>();
        for (final String line : Files.readAllLines(file)) {
            final String trimmed = line.strip().replaceAll(",$", "");
            final int colon = trimmed.indexOf("\":");
            if (!trimmed.startsWith("\"") || colon < 0) {
                continue;
            }
            results.put(trimmed.substring(1, colon), Double.parseDouble(trimmed.substring(colon + 2).strip()));
        }
        return results;
    }
}
