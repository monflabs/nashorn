# Building

Everything is Maven, from the repository root. The Ant build and the in-JDK
`make`/jtreg trees are gone, so a merge from upstream `openjdk/nashorn` no longer
applies to any build file.

## What you need

**A JDK 25 or newer, to build and not merely to target.** nasgen and the
`test262`/`benchmark`/`run` profiles fork `${java.home}/bin/java` — the JVM Maven
itself runs on — so maven-enforcer-plugin checks the version up front and fails
early rather than half way through. A JDK *toolchain* does not help:
exec-maven-plugin ignores toolchains whenever `executable` is set, which it is.

`.mvn/jvm.config` passes `--enable-native-access=ALL-UNNAMED` and
`--sun-misc-unsafe-memory-access=allow` to Maven's own JVM. That is about Maven
3.8's bundled `jansi` and `guava` tripping JDK 25's warnings, nothing in this
project; both flags need JDK 23+, which the enforcer already requires.

## The commands

```bash
mvn package            # build the whole reactor
mvn verify             # + the full suite, in BOTH optimistic and pessimistic modes
mvn -pl core test      # core tests only
mvn javadoc:javadoc    # the public API javadoc
```

Narrowing a test run:

```bash
mvn -pl core test -Dtest.js.includes=JDK-8006304.js        # one script test
mvn -pl core test -Dtest=ScopeTest                         # one Java test class
mvn -pl core test -Dsurefire.failIfNoSpecifiedTests=false   # narrowing across both executions
```

Script-test selection is by **filename suffix** (`TestFinder` does `endsWith`),
not a glob.

## The reactor

Six modules. Three are published to Maven Central; the rest build locally only.

| Module | Artifact | Published |
| --- | --- | --- |
| `core` | `nashorn-core` | **yes** — the engine |
| `debugger` | `nashorn-debugger` | **yes** — the Chrome DevTools Protocol server |
| `node` | `nashorn-node` | **yes** — experimental Node modules; version-locked to core |
| `buildtools/nasgen` | `nashorn-nasgen` | no — build-time bytecode tool |
| `debugger-ui` | `nashorn-debugger-ui` | no — embeddable Swing debugger |
| `playground` | `nashorn-playground` | no — Swing sample browser (shaded `-all` jar) |

Which modules publish is decided in the poms: the parent declares the
`central-publishing-maven-plugin` as a build extension that **publishes by
default**, so the parent POM and `core`/`debugger`/`node` go to Central, while
`nasgen`, `debugger-ui` and `playground` opt out with `skipPublishing=true`. The
parent POM has to be published because the three library POMs name it as their
`<parent>` — leave it in the published set.

## Profiles

| Profile | What it does |
| --- | --- |
| `-Pfetch-externals` | Clones test262 and octane into `core/src/test/scripts/external` |
| `-Ptest262` | Runs the ES2026 slice of test262 (see [Conformance](../reference/conformance.md)) |
| `-Ptest-parallel` | The script suite through `ParallelTestRunner` |
| `-Pbenchmark` / `-Psunspider` | The benchmark suites |
| `-Pcoverage` | JaCoCo |
| `-Prun` / `-Pdebug` | Run a `samples/` script through the engine |
| `-Prelease` | Attaches sources and javadoc jars, GPG-signs — see [Releasing](releasing.md) |

SunSpider is **not** fetched by `-Pfetch-externals`: the Ant build checked it out
of `svn.webkit.org`, which no longer exists. Drop a copy at
`core/src/test/scripts/external/sunspider` to use `-Psunspider`.

## nasgen: the one hazard worth knowing

[nasgen](../internals/nasgen.md) rewrites the `@ScriptClass` classes in
`internal/objects`, generating the `$Constructor`/`$Prototype` classes and
property maps that make them behave as built-ins. It runs at `process-classes`.

**Classes compiled by an IDE alone are not runnable**, and if nasgen silently
no-ops the build still *succeeds* while every built-in is missing at run time.
After changing anything under `internal/objects`, build through Maven and check:

```bash
javap -cp core/target/nashorn-core-*.jar \
  'org.monflabs.nashorn.internal.objects.NativeArray$Constructor'
```

javac writes to `target/classes-raw`; nasgen reads that and writes into
`target/classes`. The two are separate on purpose — nasgen is not idempotent, so
it must never see its own output. Do not point its input and output at the same
directory.

One related trap: **a stale `target/` after checking out another revision** leaves
the engine silently broken. `mvn clean` when switching revisions.

## Before you commit

```bash
.claude/skills/copyright-headers/check-headers.sh     # staged + unstaged changes
```

Every file gains a copyright header, and which header depends on whether the file
descends from OpenJDK Nashorn or was written for this fork. The checker
classifies each changed file against the `openjdk-original` branch and fails on a
mismatch. Compilation also runs with `-Xlint:all`; keep new code warning-free.

Commits are titled `<JBS-bug-id>: <synopsis>`, per the OpenJDK rules still in
`.jcheck/conf`.

## Checking performance locally

```bash
./buildtools/perf-gate.sh              # against the perf-baseline tag
./buildtools/perf-gate.sh <base-ref>   # against any revision
```

It builds *both* revisions and measures them on the same machine in one run,
interleaved, because absolute milliseconds do not carry between machines. The
same script runs in [CI](workflows.md#the-performance-gate). For a considered
judgement on a small difference, re-run on an idle machine with an explicit band:
`-Dperf.tolerance=0.02`. Before trusting a single run, check nothing else is
loading the machine — a Time Machine backup once turned every metric into a 2x
"regression".
