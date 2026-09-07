Changes from upstream Nashorn
=============================

This engine is a fork of [OpenJDK Nashorn](https://github.com/openjdk/nashorn),
branched at its **15.7** release (the last upstream tag; the pristine tree is kept
on the `openjdk-original` branch). This page summarises how the fork differs from
that baseline: the language it implements, the coordinates and packaging, the
public API, the standard libraries, the command-line flags, and what was removed.

For the exact conformance picture see [CONFORMANCE.md](CONFORMANCE.md); for the
release-by-release history see [../CHANGELOG.md](../CHANGELOG.md); for how to use
any of it, the fork's own guide is in [nashorn/](nashorn/README.md).


## Language: ES5.1 → ECMAScript 2022

Upstream standalone Nashorn is essentially **ECMAScript 5.1**, with a handful of
ES6 features hidden behind `--language=es6`. This fork implements
**[ECMAScript 2022](https://262.ecma-international.org/13.0/) (ES13) as the only
language mode**, with **Annex B** behind a flag. There is no ES5 mode and no
`isES6()` gating.

Added on top of the ES5.1 baseline:

- **ES2015 (ES6):** classes, generators, destructuring, rest/spread, `super`,
  `new.target`, template literals, `let`/`const` and block scoping, arrow
  functions, `for…of`, computed properties, default parameters, `Symbol` and the
  well-known symbols, the `Map`/`Set`/`WeakMap`/`WeakSet` family, `Proxy`,
  `Reflect`, `Promise`, the `%TypedArray%` hierarchy, and **ES modules**.
- **ES2016:** the `**` exponentiation operator and `Array.prototype.includes`.
- **ES2017:** `async`/`await`, `Object.values`/`entries`/`getOwnPropertyDescriptors`,
  `String.prototype.padStart`/`padEnd`, trailing commas in parameter and argument
  lists, `SharedArrayBuffer` and `Atomics`.
- **ES2018:** object rest/spread (`{...o}`, `const {a, ...rest} = o`), async
  iteration (`async function*`, `for await…of`, `Symbol.asyncIterator`,
  async-from-sync adaptation), `Promise.prototype.finally`, the template-literal
  revision (invalid escapes in tagged templates yield `undefined` cooked rather
  than a `SyntaxError`), and the RegExp additions — the `s` (dotAll) flag, named
  capture groups (`(?<name>)`, `\k<name>`, the `.groups` object, `$<name>`
  replacement), lookbehind assertions, and Unicode property escapes (`\p{…}`,
  `\P{…}` under `/u`).
- **ES2019:** `Array.prototype.flat` and `flatMap` (both added to
  `Array.prototype[@@unscopables]`), `Object.fromEntries`,
  `String.prototype.trimStart`/`trimEnd` (with the Annex B `trimLeft`/`trimRight`
  as the same function objects), `Symbol.prototype.description` (nullable —
  `Symbol().description` is `undefined`), optional catch binding (`catch {}` with
  no parameter), the now-normative guaranteed-stable `Array.prototype.sort`, the
  JSON superset (unescaped U+2028/U+2029 allowed in string literals), well-formed
  `JSON.stringify` (lone surrogates escaped as `\uXXXX`), and the
  `Function.prototype.toString` revision (verbatim source).
- **ES2020:** nullish coalescing (`??`), optional chaining (`?.`, `?.[]`, `?.()`),
  `String.prototype.matchAll` and `Symbol.matchAll`, `export * as ns from`,
  dynamic `import()`, `import.meta`, `globalThis`, `Promise.allSettled`, the
  specified `for`-`in` enumeration order, and **BigInt** — the primitive (`1n`
  literals, `typeof "bigint"`, the operators, `BigInt.asIntN`/`asUintN`), the
  `BigInt64Array`/`BigUint64Array` typed arrays, and the big-64 `DataView`
  accessors and `Atomics` operations.
- **ES2021:** `String.prototype.replaceAll`, `Promise.any` with `AggregateError`,
  the logical assignment operators (`&&=`, `||=`, `??=`), numeric separators
  (`1_000`, in every numeric literal including BigInt), and `WeakRef` /
  `FinalizationRegistry` (native over `java.lang.ref`, the registry's cleanup
  callbacks posted to the realm's event loop).
- **ES2022:** `Array`/`String`/`%TypedArray%`.prototype.`at`, `Object.hasOwn`, the
  `cause` option on every `Error` constructor, the RegExp `d` flag (`hasIndices`,
  with `.indices` match offsets), **class fields** (public and static) with
  **static initializer blocks**, **private class members** (`#x` fields, methods
  and accessors, their static forms, and the ergonomic `#x in obj` brand check,
  held in a per-object slot invisible to reflection), and **top-level `await`**
  (`await`/`for await` at a module's top level, module evaluation made asynchronous
  through the ES2022 async-evaluation algorithm).
- **Annex B** (web-compatibility): block-level function-declaration hoisting,
  `<!--` line comments, `escape`/`unescape`, `String.prototype.anchor` and kin,
  `__proto__`, and the legacy `RegExp.$1…` properties. On by default; a single
  flag removes all of it (see below).

Deliberate exclusions (see CONFORMANCE.md): **proper tail calls** and **ECMA-402
(`intl402`)**.


## Coordinates, module, engine name, versioning

| | Upstream | This fork |
| --- | --- | --- |
| Maven artifact | `org.openjdk.nashorn:nashorn-core` | `org.monflabs.nashorn:nashorn-core` |
| Java packages | `org.openjdk.nashorn.*` | `org.monflabs.nashorn.*` |
| JPMS module | `org.openjdk.nashorn` | `org.monflabs.nashorn` |
| JSR-223 engine name | `nashorn`, plus `js`/`JavaScript`/`ECMAScript` | `nashorn-monflabs` only (the generic `js`/`JavaScript`/`ECMAScript` names are not registered) |
| Version scheme | JDK-derived `15.x` | ECMAScript-year semver, e.g. `2018.0.0` |

The rename lets this artifact **coexist on one class or module path with an
upstream `nashorn-core`** — different module, different packages. The engine
deliberately does **not** register as plain `nashorn`, so `getEngineByName("nashorn")`
still resolves to the official library when both are present; use
`getEngineByName("nashorn-monflabs")` for this one.

The `--release 25`-compiled binary requires **JDK 25 or newer** at build and run
time. (Upstream 15.x targets Java 11.)


## Build and runtime

- **Maven, not Ant.** The whole in-JDK make/jtreg build is gone; the reactor is
  the Maven modules listed below.
- **No third-party dependencies.** `nashorn-core` has none. Bytecode generation
  was ported from bundled ASM to the JDK's **`java.lang.classfile`** API
  (JEP 484); the Joni regexp backend and the V8 double-conversion port remain
  bundled.
- **`ScopedValue`** replaces the thread-local "current global".
- **Security Manager support was removed** (upstream removed it in 15.7; the fork
  keeps no `doPrivileged`/`AccessController` patterns).


## New public API

All under `org.monflabs.nashorn.api`.

### `api.scripting`

- **`NashornScriptEngineBuilder`** — a fluent replacement for the option-taking
  `NashornScriptEngineFactory.getScriptEngine(...)` overloads (now deprecated).
  Typed methods for every engine option — `annexB`, `strict`, `scripting`,
  `debugger`, `inspect`, `java`, `syntaxExtensions`, `typedArrays`,
  `optimisticTypes`, `lazyCompilation`, `classCacheSize`, `persistentCodeCache`,
  `globalPerEngine`, `timeZone`, `locale`, `classPath`, `modulePath`,
  `dumpStackOnError` — plus `classLoader`, `classFilter`, `library`,
  `moduleLoader`, and a raw `option(...)` escape hatch. Libraries and module
  loaders are contributed only through `library(...)` / `moduleLoader(...)`;
  there is no discovery.
- **`ScriptLibrary`** — a bundle of Java globals and top-level scripts the
  engine installs into *every* global it creates. Contributed only by handing it
  to the builder's `library(...)` (there is no discovery and no option); an
  `initialize(JSObject global)` hook runs per realm.
- **`EventLoop`** — the realm's job queue exposed to Java: `queueMicrotask`,
  `schedule` with a cancellable `Timer`, `pending()`, `post`. `eval` returns when
  the script is idle, not merely when its synchronous code finishes.
- **`ScriptUtils`** — greatly expanded with the language's abstract operations for
  Java code a script calls (`typeOf`, `toNumber`/`toString`/`toPrimitive`,
  `isCallable`, `strictEquals`/`looseEquals`/`sameValue`, `requireObjectCoercible`,
  `typeError`/`rangeError`/`error`, …). `convert(null, primitiveType)` now follows
  the language (0/`false`) instead of returning `null`.

### `api.modules` (new package)

Pluggable ES module loading, consumed with ordinary `import`. A chain of
`ModuleLoader`s is registered on the engine (builder `moduleLoader`), each asked in
order, first non-null wins. Ships `PathModuleLoader` (files under a root),
`ResourceModuleLoader` (class-path resources), `JavaModuleLoader` (modules whose
exports are Java values, `default` included), and the `Module` value type.

### `api.debugger` (new package)

A protocol-neutral debugging API behind `--debugger`: `Debugger.of(engine)`,
breakpoints, stepping, frames, scopes, values, termination, a `console` bridge,
`clearScripts()` (drop the parsed-script and context registry — a CDP
`Runtime.executionContextsCleared` — for a host reusing one engine across runs),
and a passive **`TraceListener`** (statements and completion values without
pausing). `DebuggerFrontend` is the service the engine looks up for `--inspect`.

### `api.tree`

The public parser AST gained the post-ES6 syntax: `**` is `Kind.EXPONENT`, `**=`
is `EXPONENT_ASSIGNMENT`, and `await` is a `UnaryTree` of kind `AWAIT`. The
parser's `--es6-module` option is now a parser-only flag (it no longer implies a
`--language` value).


## Standard libraries (now in `nashorn-core`)

The engine ships what a script expects from its host beyond the language as
`ScriptLibrary` classes in `nashorn-core`. Neither is installed automatically -
hand the one you want to the builder's `library(...)`; a bare engine has neither:

- **`host`** — WHATWG `setTimeout`/`clearTimeout`/`setInterval`/`clearInterval`
  on the event loop, `queueMicrotask`, and forgiving `atob`/`btoa`.
- **`fetch`** — `fetch`, `Headers`, `Request`, `Response` over the JDK's
  `HttpClient` (`Headers`/`Request`/`Response` are real `@ScriptClass` built-ins).

See [nashorn/libraries/overview.md](nashorn/libraries/overview.md).

### Node compatibility (new, experimental)

An **experimental** `node` module resolver - the separate **`nashorn-node`** artifact (published, but
version-locked to `nashorn-core`), provided as a convenience and an example - answers
`import fs from "fs"` (or `"node:fs"`) with a Java
implementation of Node's **`fs`** module - synchronous, error-first callback, and `fs.promises`
forms over `java.nio.file`, with `Stats`, `Dirent`, `fs.constants` and Node error codes. It is
registered on the engine builder explicitly - `.moduleLoader(new NodeModuleLoader())` - and consulted
before the filesystem; a plain `nashorn-core` without it resolves none of these specifiers.
`import { Buffer } from "node:buffer"`
gives Node's `Buffer` - a `Uint8Array` subclass with Node's encodings and numeric accessors - and a
binary `fs` read yields a `Uint8Array`; `import os from "os"` gives system information (`platform`,
`arch`, `cpus`, `totalmem`, `hostname`, `networkInterfaces`, ...); and `import path from "path"`
gives path-string manipulation (`join`, `resolve`, `normalize`, `parse`, ..., with `path.posix` and
`path.win32` both always present). See
[nashorn/libraries/node.md](nashorn/libraries/node.md).


## New reactor modules

| Module | Artifact | Published | What |
| --- | --- | --- | --- |
| `core` | `nashorn-core` | yes | the engine + standard libraries |
| `debugger` | `nashorn-debugger` | yes | the Chrome DevTools Protocol server (`--inspect`) |
| `debugger-ui` | `nashorn-debugger-ui` | no | an embeddable Swing debugger (a CDP client) |
| `node` | `nashorn-node` | yes | experimental Node-compat module resolver (`fs`, `buffer`, `os`, `path`); version-locked to core |
| `shell` | — | no | the `jjs` REPL |
| `playground` | — | no | a Swing sample browser / editor / console |
| `buildtools/nasgen` | — | no | the build-time bytecode tool |


## Command-line and engine-option changes

**Added**

- `--annexB` / `--annexB=false` — toggle the whole of Annex B (on by default).
- `--debugger` — emit statement/frame hooks and keep variables in scope objects,
  for a debugger to attach to.
- `--inspect` / `--inspect-brk` — serve the Chrome DevTools Protocol (implies
  `--debugger`; `-brk` waits for a client and pauses at the first statement).
- `--es6-module` — parser-API-only, enables module parsing.

**Removed**

- `--language` — it only ever accepted `es6` and did nothing (the engine is ES2022
  unconditionally). `--language=es5` (an ES5-only mode) no longer exists.
- `--libraries` — libraries are no longer discovered or selected by option;
  hand each one to the builder's `library(...)` instead.
- `--function-statement-error` / `--function-statement-warning` — block-level
  function declarations are simply legal now.
- The `-scripting` **backquote process extension** — the backquote belongs to
  template literals since ES2015. (The `$EXEC` function itself is retained.)

**Changed**

- `--no-typed-arrays` now *removes* the typed-array globals (as `--no-java` removes
  Java's), instead of leaving them as properties holding `null`.

The full option table is in [nashorn/reference/options.md](nashorn/reference/options.md).


## Deprecations

- The `NashornScriptEngineFactory.getScriptEngine(...)` overloads that took
  options, a class loader, a class filter, or libraries are **deprecated** (since
  `2017.0.0`) in favour of `NashornScriptEngineBuilder`. The no-argument
  `getScriptEngine()` remains the `javax.script` entry point.
