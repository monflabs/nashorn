# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Standalone OpenJDK Nashorn — a JavaScript (ECMAScript 5.1 + parts of ES6) engine written in Java that compiles JS to JVM bytecode and links call sites with `invokedynamic` via Dynalink (`jdk.dynalink`). It was extracted from the JDK (removed in Java 15) and is published to Maven Central as `org.openjdk.nashorn:nashorn-core`. Packages were renamed from `jdk.nashorn.*` to `org.openjdk.nashorn.*`, and the module from `jdk.scripting.nashorn` to `org.openjdk.nashorn` — old Oracle docs still use the old names.

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
- **`internal/codegen`** — an ordered list of `CompilationPhase` objects (constant folding → `Lower` → apply specialization → splitting → program points → symbol assignment → scope depths → optimistic type assignment → local variable type calculation → bytecode generation → install), driven by `Compiler`. `CodeGenerator`/`MethodEmitter`/`ClassEmitter` sit on ASM. `Splitter`/`SplitIntoFunctions` exist because JVM methods have a 64KB limit.
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
- **Script tests** in `core/src/test/scripts/**`. Each `.js` opts in through a comment-header annotation parsed by `TestFinder`: `@test`, `@test/fail`, `@test/compile-error`, `@run`, `@run/fail`, `@subtest`, `@option`, `@argument`, `@fork`, `@runif`. An unannotated file under a test root is reported as an "orphan" and fails the suite.
- A test with a sibling `<name>.js.EXPECTED` has its stdout diffed against it.

Three things about the test setup are easy to break:

1. **`.js.EXPECTED` files embed the test's own relative path** in stack traces (`at f (src/test/scripts/basic/Foo.js:33)`). Moving the script tree, or changing surefire's working directory away from the `core` module, invalidates ~56 of them.
2. **`test.js.roots` must stay relative.** `TestHelper` strips `test.root.dir` (`src/test`) from each test path to mirror its output under `target/test`, and throws if the path doesn't start with that prefix.
3. Tests run with assertions on (`-ea -esa`) and `-Duser.language=tr` deliberately, to catch locale-sensitive case conversions. Don't "fix" the Turkish locale.

The two script tests that assert on the shell module's own descriptor live in `shell/src/test/scripts/basic` and run in the `shell` module — core cannot resolve the shell module without a dependency cycle. That module reuses core's test framework straight off disk (`core/target/test/classes`).

## Conventions

- OpenJDK project rules still apply (`.jcheck/conf`): commits titled `<JBS-bug-id>: <synopsis>`, one reviewer, whitespace checked on `.java`. Every source file carries the GPLv2+Classpath-exception header — new files need it too.
- Compilation runs with `-Xlint:all`; keep new code warning-free.
- Releases: bump the version across the reactor (`mvn versions:set`), add a `CHANGELOG.md` entry, then `mvn -Prelease deploy`. Only `nashorn-core` is deployed.
- Security Manager support was removed in 15.7 — do not reintroduce `doPrivileged`/`AccessController` patterns.
- `doc/nashorn/DEVELOPER_README` documents the internal `-Dnashorn.*` properties and the `--log=<subsystem>:<level>` loggers (codegen, fields, compiler, …). Use those when debugging code generation rather than adding print statements.
