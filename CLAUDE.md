# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Standalone OpenJDK Nashorn — a JavaScript (ECMAScript 5.1 + parts of ES6) engine written in Java that compiles JS to JVM bytecode and links call sites with `invokedynamic` via Dynalink (`jdk.dynalink`). It was extracted from the JDK (removed in Java 15) and is published to Maven Central as `org.openjdk.nashorn:nashorn-core`. Packages were renamed from `jdk.nashorn.*` to `org.openjdk.nashorn.*`, and the module from `jdk.scripting.nashorn` to `org.openjdk.nashorn` — old Oracle docs still use the old names.

Two JPMS modules live under `src/`:
- `org.openjdk.nashorn` — the engine (`nashorn.jar`). Requires `jdk.dynalink`, `jdk.unsupported`, and the ASM modules (downloaded from Maven Central by the build, version pinned in `make/nashorn/build.xml`).
- `org.openjdk.nashorn.shell` — the `jjs` REPL (`jjs.jar`). Depends on JDK-internal `jdk.internal.le`/`jdk.internal.ed`, so it only builds against JDKs that still expose them.

Compiled with `release=11` (`nashorn.target.java.version` in `make/nashorn/project.properties`); CI tests on Java 11 and 21.

## Build and test

Ant is the build system, and all targets must be run from `make/nashorn`:

```
cd make/nashorn
ant jar                # downloads ASM deps, compiles, runs nasgen, builds nashorn.jar + jjs.jar
ant clean
ant test               # full suite: javadoc + TestNG suite in BOTH pessimistic and optimistic modes
ant test-pessimistic   # one mode only (faster)
ant test-optimistic
ant test-parallel      # ParallelTestRunner over the .js script tests
ant javadoc
ant artifacts          # jar + sources jar + javadoc jar + resolved POM for a release
ant run                # runs the shell on src/sample/nashorn/test.js
ant debug              # same, with --print-code --print-symbols --verify-code
```

Build output goes to `build/nashorn/` at the repo root (`dist/` for jars, `test/reports/` for TestNG reports).

External suites must be cloned first (they are not vendored):

```
ant get-test262        # clones tc39/test262 (es5-tests branch) into test/nashorn/script/external
ant test262-parallel   # the ECMA-262 suite; CI runs this
ant get-octane / get-sunspider / ant octane / ant sunspider   # benchmarks
```

### Running a subset

- **A single Java test class:** `ant test-pessimistic -Dtest.class=org/openjdk/nashorn/api/scripting/test/ScopeTest` — the value is a path prefix under `build/nashorn/test/classes`, matched as `${test.class}*`. Caveat: the `-test` target carries `unless="test.class"` (`make/nashorn/build.xml:392`), so this combination can silently run nothing; check the TestNG output actually lists tests before trusting a green run.
- **A subset of `.js` script tests:** the framework selects files by *filename suffix*, so `ant test-pessimistic -Dtest-sys-prop.test.js.includes=JDK-8006304.js` runs just that script. Ant properties prefixed `test-sys-prop.` are stripped of the prefix and passed as system properties to the test JVM — that is the general mechanism for every `test.js.*` knob in `project.properties`.

## Architecture

### Pipeline

`Source` → `parser/` (Lexer, Parser) → `ir/` AST → `codegen/` → JVM bytecode → `runtime/` + `runtime/linker/`.

- **`internal/parser`** — hand-written lexer/parser producing the internal IR. `JSONParser` and the regexp parsers live nearby (`runtime/regexp`, with a bundled Joni backend selectable via `-Dnashorn.regexp.impl=joni`).
- **`internal/ir`** — immutable AST nodes; transformations use visitors (`ir/visitor/`) and return new trees. `LexicalContext` tracks the enclosing block/function chain during traversal.
- **`internal/codegen`** — an ordered list of `CompilationPhase` objects (constant folding → `Lower` → apply specialization → splitting → program points → symbol assignment → scope depths → optimistic type assignment → local variable type calculation → bytecode generation → install), driven by `Compiler`. `CodeGenerator`/`MethodEmitter`/`ClassEmitter` sit on ASM. `Splitter`/`SplitIntoFunctions` exist because JVM methods have a 64KB limit.
- **Optimistic typing** is the reason for much of the complexity: code is compiled assuming narrow (int/long/double) types, and an `UnwarrantedOptimismException` triggers deoptimization and recompilation via `RewriteException` and `RecompilableScriptFunctionData`. The whole test suite is therefore run twice (optimistic and pessimistic) — a change that passes one mode may fail the other.
- **`internal/runtime`** — `Context` (per-engine compilation/loading state, class cache), `ScriptObject` (the JS object model, backed by `PropertyMap`/`Property`/`AccessorProperty` — an inline-cache-friendly hidden-class scheme), `ScriptFunction`, `JSType` (all ECMA type coercions), `ScriptRuntime`. `Global` (in `internal/objects`) is the per-context global object and is thread/context state, distinct from `Context`.
- **`internal/objects`** — the built-ins (`NativeArray`, `NativeString`, `NativeDate`, …) plus `Global`.
- **`internal/runtime/linker`** — Dynalink integration: `Bootstrap` is the `invokedynamic` bootstrap; the `*Linker` classes decide how a call site links to script objects, Java beans, JSObjects, primitives. `JavaAdapterFactory`/`JavaAdapterBytecodeGenerator` generate adapter classes for `Java.extend` and SAM conversion.
- **`internal/scripts`** — `JO`/`JS`/`JD` are templates; concrete property-holding structure classes (`JO4`, `JO8`, …) are generated at runtime by `ObjectClassGenerator` and loaded by `StructureLoader`.

### nasgen (important)

`make/nashorn/buildtools/nasgen` is a build-time bytecode post-processor. Classes in `internal/objects` annotated with `@ScriptClass`, `@Function`, `@Property`, `@Getter`, `@Setter`, `@Constructor`, `@SpecializedFunction` are rewritten to add the `$Constructor`/`$Prototype` inner classes and property maps that make them behave as JS built-ins. It runs as part of `ant jar-nashorn`, after `compile-nashorn`.

Consequence: **classes compiled by an IDE alone are not runnable.** After touching anything in `internal/objects` (or adding a built-in), go through `ant jar`, not a bare recompile. `ant run-nasgen-eclipse` in `build-nasgen.xml` exists for IDE workflows.

### Public API surface

- `api/scripting` — JSR-223 (`NashornScriptEngine`, `NashornScriptEngineFactory`, `ScriptObjectMirror`, `JSObject`, `ClassFilter`). Registered as a `ScriptEngineFactory` service in the jar manifest.
- `api/tree` — the parser API (`Parser`, `*Tree`/`*TreeImpl` pairs) exposing a public AST distinct from `internal/ir`.
- `api/linker` — `NashornLinkerExporter`, exported as a `GuardingDynamicLinkerExporter` service.
- `tools/Shell` — the engine-side CLI entry point.

Only `api.scripting` and `api.tree` are unconditionally exported by `module-info.java`; `internal.runtime`, `internal.objects`, and `tools` are qualified exports. Tests get at internals through the long `--add-exports`/`--add-opens` lists in `project.properties` (`test.module.imports.*`) — a new internal test package usually means adding an export there.

## Test suites

- **Java/TestNG tests** live in `test/nashorn/src`, mirroring the main package layout with a `test` sub-package (e.g. `internal/runtime/test`, `api/scripting/test`). They are split into two jars, `nashorn-internal-tests.jar` and `nashorn-api-tests.jar`.
- **Script tests** live in `test/nashorn/script/**`. Each `.js` file is opted in through a comment-header annotation parsed by `TestFinder`: `@test`, `@test/fail`, `@test/compile-error`, `@run`, `@run/fail`, `@subtest`, `@option`, `@argument`, `@fork`, `@runif`. A file with no annotation is reported as an "orphan" and fails the suite, so new script files under those directories must be annotated (or excluded).
- Output comparison: a test with a sibling `<name>.js.EXPECTED` file has its stdout diffed against it. Regenerating an EXPECTED file is the normal way to accept an intentional output change.
- `test/nashorn/script/currently-failing` and `test/nashorn/script/external` are excluded from the default run (`test-sys-prop.test.js.exclude.dir`).
- Tests run with assertions on (`-ea -esa`) and with Turkish locale/`-Duser.language=tr` deliberately, to catch locale-sensitive case conversions.

## Conventions

- OpenJDK project rules apply (`.jcheck/conf`): commits from the OpenJDK workflow are titled `<JBS-bug-id>: <synopsis>`, one reviewer required, and whitespace is checked on `.java` files. Every source file carries the GPLv2+Classpath-exception header — new files need it too.
- Compilation runs with `-Xlint:all`; keep new code warning-free (a past release was dedicated to clearing warnings on Java 21).
- Releases: bump `nashorn.version`/`nashorn.fullversion` in `make/nashorn/project.properties`, add an entry to `CHANGELOG.md`, then `ant artifacts`. `make/nashorn/nashorn-core.pom` is the template (tokens `NASHORN_VERSION`/`ASM_VERSION` substituted at build time).
- Security Manager support was removed in 15.7 — do not reintroduce `doPrivileged`/`AccessController` patterns.
- `doc/nashorn/DEVELOPER_README` documents the internal `-Dnashorn.*` system properties and the `--log=<subsystem>:<level>` loggers (codegen, fields, compiler, …). Reach for those when debugging code generation rather than adding print statements.
