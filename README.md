# Nashorn Monflabs Engine

> **This is a fork.** It descends from OpenJDK Nashorn
> ([openjdk/nashorn](https://github.com/openjdk/nashorn), version 15.7) and is
> **not affiliated with or endorsed by Oracle or the OpenJDK project**. It is
> distributed under the GNU General Public License, version 2 only, with the
> Classpath Exception where individual source files say so — the same terms as
> the JDK. The engine reports itself as `OpenJDK-Monflabs`. Upstream published up
> to 15.7 as
> [`org.openjdk.nashorn:nashorn-core`](https://search.maven.org/artifact/org.openjdk.nashorn/nashorn-core/15.7/jar);
> this fork publishes under its own coordinates so the two can coexist on a class
> path *and* on a module path.

**An ECMAScript 2026 engine for the JVM.** Nashorn compiles JavaScript to JVM
bytecode and links call sites with `invokedynamic`. It is written in Java, has no
dependencies of its own, and embeds through `javax.script`.

```xml
<dependency>
  <groupId>org.monflabs.nashorn</groupId>
  <artifactId>nashorn-core</artifactId>
  <version>2026.0.0</version>
</dependency>
```

```groovy
implementation 'org.monflabs.nashorn:nashorn-core:2026.0.0'
```

Requires **JDK 25 or newer**, at build and at run time. See the
[change log](CHANGELOG.md) for what each release brought.

## Hello, world

```java
ScriptEngine engine = new NashornScriptEngineBuilder().build();
engine.eval("""
    const greet = name => `Hello, ${name}!`;
    print(greet('world'));
    """);
```

The builder (`org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder`) is
the configuration entry point: options, class loader, class filter, script
libraries, module loaders. For a plain engine,
`new ScriptEngineManager().getEngineByName("nashorn-monflabs")` works too — the
fork registers under its own name only, never plain `nashorn`, so a JSR-223 lookup
never resolves here by accident. There is also a command-line
[shell](doc/nashorn/reference/shell.md) in the same artifact.

## What it can do

| | |
| --- | --- |
| **The whole language** | ECMAScript 2026 (ECMA-262, 17th edition) and its Annex B, measured against `tc39/test262`. Two documented exclusions: proper tail calls and ECMA-402. [Details](#the-language) |
| **Java interop** | The reason to run JavaScript on the JVM. `Java.type`, `Java.extend`/`Java.super` to subclass a class or implement an interface from script, `JavaImporter`, bean property and overload resolution, a script function passed anywhere a SAM interface is wanted, and arrays and collections converted in both directions. [Guide](doc/nashorn/guide/connecting-with-java.md) |
| **Embedding** | `javax.script` with `Compilable` and `Invocable`, a typed [builder](doc/nashorn/guide/engine-setup.md) for everything else, `ScriptObjectMirror` and `JSObject` at the boundary, and one realm per `Bindings` so threads and tenants stay apart. |
| **Asynchrony** | An optional [event loop](doc/nashorn/libraries/overview.md#the-event-loop) per realm — off by default, so a synchronous embedder pays nothing. With it on, `eval` returns when the script is *idle*. Generators, `async` functions and async generators suspend on **virtual threads** rather than a compiler-built state machine, so `try`/`finally`, labelled breaks and deoptimisation work inside them unchanged. |
| **Extending the engine** | [Script libraries](doc/nashorn/extending/script-libraries.md) install globals, scripts and prototype extensions into every realm, from Java or from JavaScript. [Module loaders](doc/nashorn/extending/module-loaders.md) decide where `import` looks: a directory, class-path resources, Java values, or your own. Both are handed to the builder — nothing is auto-discovered. |
| **Host libraries** | The functions every JavaScript host has and ECMAScript does not define: [timers](doc/nashorn/libraries/host.md), `queueMicrotask`, `atob`/`btoa`, and [`fetch`](doc/nashorn/libraries/fetch.md) with `Headers`/`Request`/`Response`. |
| **Node modules** | An experimental [resolver](doc/nashorn/libraries/node.md) for `fs`, `buffer`, `os` and `path`, reached as `import fs from 'fs'`. |
| **Debugging** | A [Chrome DevTools Protocol](doc/nashorn/guide/debugging.md) server: `--inspect` and attach Chrome or VS Code, exactly as with Node. Breakpoints, stepping, scopes, watches, frame evaluation. |
| **An embeddable debugger UI** | A Swing panel laid out like the DevTools Sources view, for putting a debugger inside your own Java application. It speaks the same protocol over an **in-process channel** — no port, no socket, nothing reachable from outside the JVM. |
| **Sandboxing** | A [`ClassFilter`](doc/nashorn/guide/custom-objects.md#classfilter) decides class by class what a script may reach; `--no-java` removes the Java bridge entirely; the engine can be given a class path or module layer of its own. |
| **Tooling** | A public AST — the [parser API](doc/nashorn/internals/parser-api.md) — for linters, rewriters and analysers that must not depend on internals. |
| **Performance** | Scripts compile to JVM bytecode and link through `invokedynamic`; no interpreter tier. [Optimistic typing](doc/nashorn/internals/optimistic-typing.md) is on by default, and a [perf gate](doc/nashorn/internals/performance.md) runs on every push. |
| **The playground** | A Swing sample browser with an editor, a console and the debugger a click away — the whole feature set as runnable, editable samples. Build the jar, run it locally. [More](doc/nashorn/guide/playground.md) |
| **Documentation** | A [complete, current doc site](doc/nashorn/README.md): user's guide, extension guide, standard libraries, engine internals, and the option and built-in reference. |

Two practical properties worth knowing: `nashorn-core` has **no dependencies at
all** — it generates bytecode with the JDK's own `java.lang.classfile` — and
because its packages and module carry this fork's name, it **coexists with an
upstream `org.openjdk.nashorn` jar** on a class path and on a module path alike.

### What was removed

**Scripting mode and `jjs` are gone** (2026.1.0) — the `-scripting` option,
heredocs, `#` comments, `${expr}` in double-quoted strings, and the
`$EXEC`/`$ENV`/`$OPTIONS`/`$ARG` globals. Shell scripting is a niche that `sh`,
Node and Python have taken, and ECMAScript covers what remains: a template literal
is a heredoc with interpolation, `String.raw` is one without, `//` is a comment
everywhere. `readLine` and `readFully` survive as ordinary globals, and
`samples/exec.js` shows how to run a process over `ProcessBuilder`.

## The language

**ECMAScript 2026** ([ECMA-262, 17th edition](https://262.ecma-international.org/17.0/))
is the only language mode — there is no ES5 mode and no version switch. Everything
below is simply the language:

| Edition | What it brought |
| --- | --- |
| **ES2015** | `let`/`const`, arrow functions, classes, generators, destructuring, rest/spread, `for…of`, template literals, symbols, `Map`/`Set`, Proxy, Reflect, Promise, typed arrays, modules |
| **ES2016–17** | `**`, `Array.prototype.includes`, `Object.values`/`entries`, `padStart`/`padEnd`, async functions, `SharedArrayBuffer`, `Atomics` |
| **ES2018** | object rest/spread, async iteration (`for await`), `Promise.prototype.finally`, RegExp `s` flag, named groups, lookbehind, `\p{…}` escapes |
| **ES2019** | `flat`/`flatMap`, `Object.fromEntries`, `trimStart`/`trimEnd`, optional catch binding, stable `sort` |
| **ES2020** | `??`, `?.`, `matchAll`, dynamic `import()`, `import.meta`, `globalThis`, **BigInt** |
| **ES2021** | `replaceAll`, `Promise.any`, logical assignment, numeric separators, `WeakRef`/`FinalizationRegistry` |
| **ES2022** | `at`, `Object.hasOwn`, error `cause`, RegExp `d` flag, **class fields**, static blocks, **private members** (`#x`), **top-level `await`** |
| **ES2023** | `findLast`/`findLastIndex`, `toReversed`/`toSorted`/`toSpliced`/`with`, hashbang grammar, symbols as weak keys |
| **ES2024** | `Object.groupBy`/`Map.groupBy`, `Promise.withResolvers`, `isWellFormed`, resizable `ArrayBuffer`, `Atomics.waitAsync`, RegExp `v` flag |
| **ES2025** | **iterator helpers**, the **`Set` methods**, **`Float16Array`**, `RegExp.escape`, `Promise.try`, RegExp pattern modifiers, duplicate named groups, **import attributes** and JSON modules |
| **ES2026** | `Error.isError`, `Math.sumPrecise`, the **upsert** methods, `Iterator.concat`, **`Uint8Array` base64/hex**, **JSON source access** with `JSON.rawJSON`, **`Array.fromAsync`** |

**Annex B** — the additional features for web browsers (`escape`, `__proto__`,
`String.prototype.substr` and its markup siblings, HTML-like comments, block
function hoisting) — is implemented and **on by default**; `--annexB=false`
removes all of it, for a host that wants the standard alone.

Deliberately **not** implemented: proper tail calls, and ECMA-402
(internationalization). Temporal, explicit resource management (`using`),
`Atomics.pause` and import defer belong to ES2027 and are out of scope for now.

Beyond the specification, the engine ships optional
[standard libraries](doc/nashorn/libraries/overview.md) — timers,
`queueMicrotask`, `atob`/`btoa`, `fetch` — an
[event loop](doc/nashorn/libraries/overview.md#the-event-loop), a
[Chrome DevTools Protocol debugger](doc/nashorn/guide/debugging.md), and an
experimental [Node module resolver](doc/nashorn/libraries/node.md)
(`fs`, `buffer`, `os`, `path`).

## Documentation

- **[The documentation site](doc/nashorn/README.md)** — user's guide (embedding,
  Java interop, [script libraries](doc/nashorn/extending/script-libraries.md),
  [modules](doc/nashorn/guide/modules.md),
  [debugging with Chrome DevTools or VS Code](doc/nashorn/guide/debugging.md)), a
  technical guide to the engine's internals, and the
  [option](doc/nashorn/reference/options.md) and
  [built-in](doc/nashorn/reference/builtins.md) reference.
- **[JavaDoc](https://www.javadoc.io/doc/org.monflabs.nashorn/nashorn-core)** — the
  public API.
- **[Changes from upstream](doc/CHANGES-FROM-UPSTREAM.md)** — the language this
  fork adds, the new APIs, the flags that changed.
- **[Conformance](doc/CONFORMANCE.md)** — what `test262` says, and what the
  exclusions actually contain.
- **[Performance](doc/nashorn/internals/performance.md)** and the
  [catalogue of optimizations over 15.7](doc/nashorn/reference/optimizations.md).

To try the engine interactively, build and run
[the playground](doc/nashorn/guide/playground.md):

```
mvn -pl playground -am package
java -jar playground/target/nashorn-playground-2026.0.0-all.jar
```

## Versioning

This fork uses [semantic versioning](https://semver.org/) — `MAJOR.MINOR.PATCH` —
with one twist: **the major number is the ECMAScript specification year** the
engine implements, not a sequential number. So `2026.0.0` targets
[ECMAScript 2026](https://262.ecma-international.org/17.0/) (ES17), just as
`2018.0.0` targeted ECMAScript 2018. Minor and patch increment as usual for
backward-compatible features and fixes within that target; adopting a later
edition moves the major number to that edition's year. This replaces the upstream
`15.x` scheme, which tracked the JDK release Nashorn was extracted from rather
than the language it implements.

## Running on the module path

Nashorn is a JPMS module (`org.monflabs.nashorn`) with no dependencies — it
generates bytecode with the JDK's own `java.lang.classfile` API — so put it on your
application's module path, add it to a module layer, or otherwise configure it as a
module.

The fork is compiled with `--release 25`. Earlier `nashorn-core` releases on Maven
Central target Java 11; use one of those on an older JDK. Java 14 and earlier also
ship a built-in Nashorn —
[this page](https://github.com/szegedi/nashorn/wiki/Using-Nashorn-with-different-Java-versions)
covers using the standalone engine when both are present.

## Building from source

Nashorn builds with Maven and requires a JDK 25 or newer: several build steps fork
the JVM Maven itself runs on, so a toolchain pointing elsewhere is not enough. From
the repository root:

```
mvn package      # builds core/target/nashorn-core-<version>.jar
mvn verify       # + the full test suite, in both optimistic and pessimistic typing modes
```

Always build through Maven rather than compiling the sources directly: a build-time
bytecode post-processor (`nasgen`) finishes the built-in classes, and the engine
does not work without it.

The reactor has six modules:

| Module | Artifact | Published |
| --- | --- | --- |
| `core` | `nashorn-core` | **yes** — the engine, including the standard libraries |
| `debugger` | `nashorn-debugger` | **yes** — the Chrome DevTools Protocol server |
| `node` | `nashorn-node` | **yes** — experimental Node module resolver |
| `buildtools/nasgen` | `nashorn-nasgen` | no — build-time bytecode tool |
| `debugger-ui` | `nashorn-debugger-ui` | no — an embeddable Swing debugger |
| `playground` | `nashorn-playground` | no — a Swing sample browser |

### Conformance suite

To run the [official ECMA-262 conformance suite](https://github.com/tc39/test262),
fetch it once and then run it:

```
mvn -Pfetch-externals -pl core generate-test-resources
mvn -Ptest262 -DskipTests verify
```

test262 has no branch for any edition, so the suite is pinned by commit and the
ES2026 slice is selected out of it: a test counts unless it needs a feature that
postdates ES2026. The run is compared against a checked-in expectations file and
fails on an unexpected **pass** as well as an unexpected failure, so conformance
only moves forwards. Of the ~78,600 selected executions, none fail with the engine's
defaults, and 8 fail without optimistic types — all of them the carried-over Annex B
indirect-eval cases.
[doc/CONFORMANCE.md](doc/CONFORMANCE.md) measures the exclusions and says what
Annex B covers on either side of its flag.

### Other profiles

`-Pbenchmark` and `-Psunspider` for the benchmarks, `-Pcoverage` for a JaCoCo
report, `-Prun` to execute a sample script through the engine, and `-Prelease` to
build the signed artifacts for publication.

Cutting a release, and the version-numbering rules that go with it, are
[Building and releasing](doc/nashorn/project/building-and-releasing.md).

## Contributing

Nashorn is a project under the charter of the OpenJDK, governed by the
[OpenJDK Bylaws](https://openjdk.java.net/bylaws); the project membership is on the
[OpenJDK Census](https://openjdk.java.net/census#nashorn). Patches and involvement
from individual contributors or companies are welcome. First-time contributors to
an OpenJDK project should review the rules on
[becoming a Contributor](https://openjdk.java.net/bylaws#contributor) and sign the
[Oracle Contributor Agreement](https://www.oracle.com/technetwork/community/oca-486395.html)
(OCA).

### Issue tracking

If you think you have found a bug, first check that you are testing against the
latest version — it may already be fixed. If not, search the
[issues list](https://bugs.openjdk.java.net/browse/JDK-8255842?jql=project%3DJDK%20AND%20component%3Dcore-libs%20AND%20Subcomponent%3Djdk.nashorn)
in the Java Bug System (JBS) for a similar issue.
[bugreport.java.com](https://bugreport.java.com/) has more on where and how to
report one: use component "Core Libraries" and subcomponent "jdk.nashorn".

### Discussion

Discussion of Nashorn development happens on the
[nashorn-dev](https://mail.openjdk.java.net/mailman/listinfo/nashorn-dev)
mailing list.
