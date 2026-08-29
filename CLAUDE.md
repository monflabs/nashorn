# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Standalone OpenJDK Nashorn — a JavaScript engine written in Java that compiles JS to JVM bytecode and links call sites with `invokedynamic` via Dynalink (`jdk.dynalink`).

This fork implements **ECMAScript 2017, as the only language mode**, and passes the ES2017 slice of
`tc39/test262` in full - the expectations file is empty. There is no ES5 mode and no `isES6()` gating: `--language` is still accepted (`es6` only) purely so existing command lines keep working. Classes, generators, destructuring, rest/spread, `super`, `new.target`, Proxy, Reflect, Promise, the well-known symbols, the `%TypedArray%` hierarchy and modules are all implemented; what remains of `Lower.throwNotImplementedYet` is a destructuring assignment written somewhere the desugaring does not reach. So are the two editions after ES2015: `**`, `Array.prototype.includes`, `Object.values`/`entries`/`getOwnPropertyDescriptors`, `String.prototype.padStart`/`padEnd`, trailing commas in parameter and argument lists, async functions, `SharedArrayBuffer` and `Atomics`. Two deliberate exclusions: proper tail calls, and Annex B (so a block-level function declaration is scoped to its block and is *not* hoisted the way browsers do). The `-scripting` backquote exec extension and `$EXEC` were removed when ES2015 claimed the backquote for template literals. It was extracted from the JDK (removed in Java 15) and is published to Maven Central as `org.openjdk.nashorn:nashorn-core`. Packages were renamed from `jdk.nashorn.*` to `org.openjdk.nashorn.*`, and the module from `jdk.scripting.nashorn` to `org.openjdk.nashorn` — old Oracle docs still use the old names.

This fork (`monflabs/nashorn`) has migrated from the original Ant build to Maven; the Ant files and the leftover in-JDK make/jtreg trees are gone. That means merges from upstream `openjdk/nashorn` no longer apply cleanly to build files.

Compiled with `release=25`; CI tests on Java 25. A JDK 25+ is required to *build*, not just to target: nasgen and the `test262`/`benchmark`/`run` profiles fork `${java.home}/bin/java`, i.e. the JVM Maven itself runs on, so maven-enforcer-plugin requires it up front. A JDK toolchain would not work — exec-maven-plugin ignores toolchains whenever `executable` is set, which it is.

JEP 486 (permanent Security Manager disablement) removed `@CallerSensitive` from `AccessController.doPrivileged` and `Thread.getContextClassLoader`. The two script tests that pin Dynalink's caller-sensitive handling (`JDK-8010946-2.js`, `JDK-8020809.js`) were retargeted at `Class.forName` and `AccessibleObject.setAccessible`, which are still caller sensitive. `AccessibleObject.setAccessible(boolean)` is, as of JDK 25, the *only* overridable caller-sensitive instance method in `java.base` — worth knowing before touching `JavaAdapterBytecodeGenerator`.

## Reactor layout

| Module | Artifact | Published |
| --- | --- | --- |
| `buildtools/nasgen` | `nashorn-nasgen` | no — build-time bytecode tool |
| `core` | `nashorn-core` | **yes** — the engine |
| `shell` | `nashorn-shell` | no — the `jjs` REPL |

`shell` reaches into JDK-internal `jdk.internal.le` / `jdk.internal.ed` via `--add-exports`, so it constrains which JDKs can build the reactor. It is the piece most likely to break on a future JDK.

## Build and test

Run from the repository root:

```
mvn package            # build all three modules
mvn verify             # + the full suite, in BOTH optimistic and pessimistic modes
mvn -pl core test      # core tests only
mvn javadoc:javadoc    # the two public API packages
```

Profiles: `-Pfetch-externals` (clone test262/octane into `core/src/test/scripts/external`), `-Ptest262`, `-Ptest-parallel`, `-Pbenchmark`, `-Psunspider`, `-Pcoverage` (JaCoCo), `-Prun` / `-Pdebug` (run a `samples/` script through the engine), `-Prelease`.

SunSpider is not fetched by `-Pfetch-externals`: the Ant build checked it out of `svn.webkit.org`, which no longer exists. Drop a copy at `core/src/test/scripts/external/sunspider` to use `-Psunspider`.

### Running a subset

```
mvn -pl core test -Dtest.js.includes=JDK-8006304.js          # one script test (suffix match)
mvn -pl core test -Dtest=ScopeTest                           # one Java test class
mvn -pl core test -Dsurefire.failIfNoSpecifiedTests=false    # when narrowing across both executions
```

Script-test selection is by *filename suffix* (`TestFinder` does `endsWith`), not a glob.

## Bytecode generation

Everything that writes bytecode uses the JDK's `java.lang.classfile` (JEP 484); there is no ASM dependency, and `nashorn-core` has no dependencies at all. Two things about the port are worth knowing before touching the emitters:

- **`CodeBuffer` records a method, it does not stream it.** `java.lang.classfile` only hands out a `CodeBuilder` inside the callback that builds one method, while `CodeGenerator` has several methods open at once (a nested function is emitted while its enclosing function still is). So `MethodEmitter` appends `Consumer<CodeBuilder>` operations to a `CodeBuffer`, and `ClassEmitter.toByteArray()` replays them. Labels are the reason it cannot record `CodeElement`s directly: a classfile label belongs to the builder that made it, so `codegen.Label`s are mapped to real ones at write time.
- **Stack maps come from a `ClassHierarchyResolver`,** not from a `getCommonSuperClass` override. Classes that cannot be loaded (the compile unit itself, the runtime-generated structure classes) are answered from their package name: anything in Nashorn's `scripts` or `objects` package is reported as a ScriptObject subtype, everything else as Object. That reproduces what the old ASM override did.

Descriptors are `ClassDesc`/`MethodTypeDesc`, parsed once and cached (`Type.classDesc`, `CompilerConstants.classDesc`/`methodType`, `Call`'s lazily parsed signature) - parsing them per emission is measurably slower. `Call` must not parse eagerly in its constructor: these are static fields of classes that are still initializing, and resolving a Nashorn type loads a class.

## nasgen — read before touching `internal/objects`

`buildtools/nasgen` rewrites the `@ScriptClass`-annotated classes in `org.openjdk.nashorn.internal.objects`, generating the `$Constructor` / `$Prototype` classes and property maps that make them behave as JS built-ins. It runs at `process-classes` in `core/pom.xml`.

**Classes compiled by an IDE alone are not runnable.** Worse, if nasgen silently no-ops the build still *succeeds* and every built-in is missing at runtime. After changing anything under `internal/objects`, build through Maven and sanity-check:

```
javap -cp core/target/nashorn-core-*.jar 'org.openjdk.nashorn.internal.objects.NativeArray$Constructor'
```

javac writes to `target/classes-raw`; nasgen reads that and writes into `target/classes`. The two are kept separate deliberately — nasgen is not idempotent, so it must never see its own output. (The in-JDK make had the same hazard and guarded it with a `_the.nasgen.run` marker file.) Do not "simplify" this by pointing nasgen's input and output at the same directory.

## Architecture

### Pipeline

`Source` → `parser/` (Lexer, Parser) → `ir/` AST → `codegen/` → JVM bytecode → `runtime/` + `runtime/linker/`.

- **`internal/parser`** — hand-written lexer/parser producing the internal IR. `JSONParser` and the regexp parsers live nearby (`runtime/regexp`, with a bundled Joni backend selectable via `-Dnashorn.regexp.impl=joni`).
- **`internal/ir`** — immutable AST nodes; transformations use visitors (`ir/visitor/`) and return new trees. `LexicalContext` tracks the enclosing block/function chain during traversal.
- **`internal/codegen`** — an ordered list of `CompilationPhase` objects (constant folding → `Lower` → apply specialization → splitting → program points → symbol assignment → scope depths → optimistic type assignment → local variable type calculation → bytecode generation → install), driven by `Compiler`. `CodeGenerator`/`MethodEmitter`/`ClassEmitter` sit on `java.lang.classfile`. `Splitter`/`SplitIntoFunctions` exist because JVM methods have a 64KB limit.
- **Optimistic typing** is the reason for much of the complexity: code is compiled assuming narrow (int/long/double) types, and an `UnwarrantedOptimismException` triggers deoptimization and recompilation via `RewriteException` and `RecompilableScriptFunctionData`. The suite therefore runs twice; a change can pass one mode and fail the other.
- **`internal/runtime`** — `Context` (per-engine compilation/loading state, class cache), `ScriptObject` (the JS object model, backed by `PropertyMap`/`Property`/`AccessorProperty` — an inline-cache-friendly hidden-class scheme), `ScriptFunction`, `JSType` (all ECMA type coercions), `ScriptRuntime`. `Global` (in `internal/objects`) is the per-context global object, distinct from `Context`.
- **`internal/objects`** — the built-ins (`NativeArray`, `NativeString`, `NativeDate`, …) plus `Global`.
- **`internal/runtime/linker`** — Dynalink integration: `Bootstrap` is the `invokedynamic` bootstrap; the `*Linker` classes decide how a call site links to script objects, Java beans, JSObjects, primitives. `JavaAdapterFactory`/`JavaAdapterBytecodeGenerator` generate adapter classes for `Java.extend` and SAM conversion.
- **`internal/scripts`** — `JO`/`JS`/`JD` are templates; concrete property-holding structure classes (`JO4`, `JO8`, …) are generated at runtime by `ObjectClassGenerator` and loaded by `StructureLoader`.

### Public API surface

- `api/scripting` — JSR-223 (`NashornScriptEngine`, `NashornScriptEngineFactory`, `ScriptObjectMirror`, `JSObject`, `ClassFilter`).
- `api/tree` — the parser API (`Parser`, `*Tree`/`*TreeImpl` pairs), a public AST distinct from `internal/ir`.
- `api/linker` — `NashornLinkerExporter`.
- `tools/Shell` — the engine-side CLI entry point.

Only `api.scripting` and `api.tree` are unconditionally exported by `module-info.java`; `internal.runtime`, `internal.objects`, and `tools` are qualified exports to the shell module. The service registrations exist twice on purpose: `provides` in `module-info.java` for module mode, and `core/src/main/resources/META-INF/services/*` for classpath mode. Both are needed.

## Test suites

- **Java/TestNG tests** in `core/src/test/java`, mirroring the main packages with a `test` sub-package.
- **test262 (ES2017 slice)** — `mvn -Ptest262 verify`, driven by `Test262Runner`, not by `TestFinder`/`ParallelTestRunner`. Read the note below before touching it.
- **Script tests** in `core/src/test/scripts/**`. Each `.js` opts in through a comment-header annotation parsed by `TestFinder`: `@test`, `@test/fail`, `@test/compile-error`, `@run`, `@run/fail`, `@subtest`, `@option`, `@argument`, `@fork`, `@runif`. An unannotated file under a test root is reported as an "orphan" and fails the suite.
- A test with a sibling `<name>.js.EXPECTED` has its stdout diffed against it.

Three things about the test setup are easy to break:

1. **`.js.EXPECTED` files embed the test's own relative path** in stack traces (`at f (src/test/scripts/basic/Foo.js:33)`). Moving the script tree, or changing surefire's working directory away from the `core` module, invalidates ~56 of them.
2. **`test.js.roots` must stay relative.** `TestHelper` strips `test.root.dir` (`src/test`) from each test path to mirror its output under `target/test`, and throws if the path doesn't start with that prefix.
3. Tests run with assertions on (`-ea -esa`) and `-Duser.language=tr` deliberately, to catch locale-sensitive case conversions. Don't "fix" the Turkish locale.

The two script tests that assert on the shell module's own descriptor live in `shell/src/test/scripts/basic` and run in the `shell` module — core cannot resolve the shell module without a dependency cycle. That module reuses core's test framework straight off disk (`core/target/test/classes`).

## test262 and the ES2017 conformance target

test262 has **no branch or tag for any edition** — only the frozen `es5-tests` branch and `main`, which
tracks the current draft spec. So the suite is **pinned by commit** (`nashorn.test262.commit` in
`core/pom.xml`) and the ES2017 slice is selected out of it at runtime.

The selector (`Test262Selector`) is a **deny** rule: a test is in scope unless its `features:` name
something that postdates ES2017. That is deliberate — an allow rule keyed on feature tags drops the
~15,000 untagged tests covering the ES5.1 core as the later editions amended it, and those count.
`es6id:` alone and `features:` alone each miss thousands of tests in opposite directions, which is why
neither is used as the primary rule. `tail-call-optimization` is excluded by decision; `intl402`,
`staging`, `annexB` and the async-generator directories (ECMAScript 2018) by directory. Three lists name
the rest one file at a time - `LATER_SYNTAX` for a test in scope whose body is written with syntax that
is not, `LATER_FEATURES` for one about something later that declares nothing, `LATER_UNICODE` for one
keyed to a Unicode version newer than the JDK's - because a rule that skipped anything unparseable would
hide real failures. A test flagged `CanBlockIsFalse` is for a host whose main agent cannot be suspended,
which this one can, so it is not selected either.

`Test262Runner` differs from the old runner in ways that matter:

- A test with neither `onlyStrict` nor `noStrict` **runs twice**, strict and sloppy. The old runner had one
  global strict switch and skipped such tests entirely.
- `negative: {phase, type}` is **verified**, both the phase and the error constructor. The old runner read
  an expected-error regex and never checked it, so negative tests passed on the wrong error.
- `includes:` actually loads harness files. The old `test262.js` shim's `$INCLUDE` was dead code.
- Each execution gets a **fresh Global** and a **40s timeout** on its own thread - three times that for a
  test that starts agents, which waits on threads of its own. The timeout is not optional: some tests hand
  Map/Set an adversarial iterator that Nashorn never terminates, and without it a wedged worker hangs the
  whole run.
- Results are diffed against `core/src/test/resources/test262-expectations.txt`, and the run fails on an
  unexpected **pass** as well as an unexpected failure, so conformance only moves forwards. **The file is
  empty**: every selected execution passes, so any failure at all fails the build, and a new entry in it
  is a regression rather than a note. Regenerate with
  `-Dnashorn.test262.write.expectations=true`; narrow a run with
  `-Dnashorn.test262.include=/built-ins/Math/`. Regenerate through Maven, never by running
  `Test262Runner` directly: the Maven run sets the Turkish locale on purpose, to catch a case
  conversion in the engine that forgot to name one. The locale a *script* sees is a separate
  thing - `toLocaleUpperCase` answers for the host's - and the runner sets that to en-US,
  because the suite is written for a host where "i" grows no dot.

`doc/CONFORMANCE.md` records what the four exclusions actually contain, measured rather than assumed:
Annex B is 1,086 files of which 336 already pass, and 635 of the 750 failures are the one B.3.3 rule -
a block-level function declaration leaking a var binding into the enclosing scope - which this fork
deliberately does not do. Tail calls are 35 files and nothing else depends on them. Regenerate any of
those numbers by taking the entry out of `EXCLUDED_DIRS` (or putting `tail-call-optimization` into
`FEATURES`), running with `-Dnashorn.test262.include=`, then restoring the selector **and rebuilding the
test classes** - a patched selector left in `core/target/test/classes` silently widens the next run.

snakeyaml is pinned at 2.4 because 1.6 (the Ant-era pin) rejects 283 in-scope frontmatter blocks with
"special characters are not allowed".

## Performance gate

`buildtools/perf-gate.sh [base-ref]` compares the working tree against a base
revision — by default the `perf-baseline` tag, which marks the last revision
measured before the ES2015 work began. It runs in CI on every push.

Nothing about the measurement is checked in, and that is deliberate. Absolute
milliseconds do not carry from a laptop to a shared runner, so the script builds
*both* revisions and measures them on the same machine, in the same run. The base
revision supplies only the engine; the harness
(`core/src/test/java/.../performance/PerfBenchmark.java`) and the benchmark
scripts (`core/src/test/scripts/perf/`, deliberately outside `test.js.roots` so
the orphan finder ignores them) always come from the working tree, so the base
revision need not contain them.

Four things in the harness exist because measuring this badly is easy, and each
was put there after watching identical code report a regression:

1. **Steady state.** A script is compiled once and run repeatedly in one realm.
   Compiling per iteration swung results by 30%.
2. **Minimum within a JVM, median across JVMs.** Inside one process every
   disturbance costs time, so the fastest sample is the truest. Across processes
   a sample can be spuriously *fast* — the first JVM after a build runs on a
   boosted CPU — and a minimum would enshrine that outlier.
3. **Interleaving.** perf-gate.sh alternates base and head round by round.
   Measured one side then the other, the first side was slower on all seven
   metrics, by up to 17%.
4. **A pinned heap** (`-Xms1g -Xmx1g -XX:+UseG1GC`) in every measurement JVM.
   Left to ergonomics, G1 sizes itself differently per process and the
   allocation-heavy benchmarks moved 15% on that alone.

Tolerances are per metric and measured, not chosen — see the table in
`PerfBenchmark`. They range from 5% on `run.instanceof` to 30% on
`compile.pdfjs`, and the gate has been checked in both directions: no false
positive across ten pairings of identical code, and it catches a +5% regression
on `instanceof` and +6% on `toprimitive` — the two hot paths ES2015 well-known
symbols put most at risk. A missing metric counts as a failure, so a measurement
that silently did not happen cannot pass.

For a considered judgement on a small difference, re-run on an idle machine with
an explicit band: `-Dperf.tolerance=0.02`.

## Conventions

- OpenJDK project rules still apply (`.jcheck/conf`): commits titled `<JBS-bug-id>: <synopsis>`, one reviewer, whitespace checked on `.java`. Every source file carries the GPLv2+Classpath-exception header — new files need it too.
- Compilation runs with `-Xlint:all`; keep new code warning-free.
- Releases: bump the version across the reactor (`mvn versions:set`), add a `CHANGELOG.md` entry, then `mvn -Prelease deploy`. Only `nashorn-core` is deployed.
- Security Manager support was removed in 15.7 — do not reintroduce `doPrivileged`/`AccessController` patterns.
- `doc/nashorn/DEVELOPER_README` documents the internal `-Dnashorn.*` properties and the `--log=<subsystem>:<level>` loggers (codegen, fields, compiler, …). Use those when debugging code generation rather than adding print statements.
