# Getting started

Nashorn is published to Maven Central as a single, dependency-free artifact:

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

It requires **JDK 25 or later**, at build and at run time. Releases up to 15.7 were published by the
upstream project as `org.openjdk.nashorn:nashorn-core` and target Java 11 — this fork continues that
line under new coordinates, with the Java packages renamed to match
(`org.openjdk.nashorn.*` → `org.monflabs.nashorn.*`).

## Class path or module path

The engine is a JPMS module named `org.monflabs.nashorn` with no dependencies of its own. Both
placements work:

- **Module path** (preferred): the module exports only its API packages
  (`org.monflabs.nashorn.api.scripting`, `api.tree`, `api.debugger`, `api.modules` and
  `org.monflabs.nashorn.libs`), so internals stay sealed and the service registration flows through
  `provides javax.script.ScriptEngineFactory`.
- **Class path**: the same registration is duplicated in `META-INF/services`, so
  `ScriptEngineManager` discovery works there too.

?> Because the module and packages carry this fork's own name, this artifact **can share a class
path or a module path** with an upstream `org.openjdk.nashorn:nashorn-core` jar — different modules,
different packages. Both register with `javax.script`, so when both are present pick by engine name
rather than relying on discovery order.

## Hello, world

The engine registers with `javax.script` under only its own name — `nashorn-monflabs` (and the
`Nashorn-Monflabs` casing). It deliberately does **not** register under the generic `js`,
`JavaScript` or `ECMAScript`, nor under plain `nashorn` — so a `getEngineByName` lookup never
resolves here by accident and never shadows another JavaScript engine on the path (the official
Nashorn library included):

```java
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class EvalScript {
    public static void main(final String[] args) throws Exception {
        // create a script engine manager
        final ScriptEngineManager factory = new ScriptEngineManager();
        // create a JavaScript engine
        final ScriptEngine engine = factory.getEngineByName("nashorn-monflabs");
        // evaluate JavaScript code from String
        engine.eval("print('Hello, World')");
    }
}
```

Compile, run, and `Hello, World` appears — the exception handling is elided here; `eval` throws
`javax.script.ScriptException` on script errors.

## Two ways to create an engine

`Hello, world` above uses **`javax.script`** — `ScriptEngineManager.getEngineByName(...)`. That is
the right choice for **simply evaluating scripts**: it is the standard JSR-223 entry point, needs no
Nashorn-specific imports, and gives a working engine with the default configuration.

That engine is deliberately **bare**, though: the defaults, and — importantly — **no script
libraries and no module loaders**. When a script needs more than the language itself, build the
engine with the fork's own **`NashornScriptEngineBuilder`** instead — the fluent, type-safe way to
configure:

- **engine [options](../reference/options.md)** — strict mode, sandboxing, the time zone, the
  debugger, the [event loop](../libraries/overview.md#the-event-loop) (off by default —
  `.eventLoop(true)` to run `Promise`, `async`/`await`, timers and `fetch`)…;
- **[script libraries](../extending/script-libraries.md)** — the `host` timers and `fetch`, or your
  own values installed into every realm (there is no discovery, so a bare engine has none);
- **[module loaders](../extending/module-loaders.md)** — resolving `import` to files, class-path
  resources, Java values, or the [Node modules](../libraries/node.md).

`getEngineByName` configures none of these. Options *can* also be passed as raw `--option` strings
(and libraries as varargs) to the deprecated `NashornScriptEngineFactory.getScriptEngine(...)`
overloads — but the builder's typed methods are checked at compile time, and it alone can register a
module loader.

```java
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .strict(true)                                    // an engine option
        .eventLoop(true)                                 // timers and fetch need somewhere to run
        .library(new HostLibrary(), new FetchLibrary())  // adds setTimeout, fetch, ...
        .build();
engine.eval("setTimeout(() => print('tick'), 10)");      // needs both lines above
```

?> Both are needed. The library supplies the function; the [event loop](../libraries/overview.md#the-event-loop)
runs what it schedules. Without `.eventLoop(true)` the call throws a `TypeError` saying so, rather
than quietly scheduling work nothing would run.

`build()` returns an ordinary `javax.script.ScriptEngine`, so everything in
[Using the engine](using-the-engine.md) works the same either way — the builder only decides *what
the engine can do* before you run anything against it.

**Rule of thumb:** `getEngineByName` for a quick eval; **`NashornScriptEngineBuilder` for anything
real** — and always when scripts use libraries or `import`. [Creating the engine](engine-setup.md)
covers the choice and every option in full.

## What language you get

The engine speaks **ECMAScript 2026** ([ECMA-262, 17th edition](https://262.ecma-international.org/17.0/)),
whole. There is no ES5 mode and no version switch — the `--language` option has been removed — so
every edition below is simply the language:

| Edition | What it brought |
| --- | --- |
| **ES2015** | `let`/`const`, arrow functions, classes, generators, destructuring, rest/spread, `for…of`, template literals, symbols, `Map`/`Set`, `Proxy`, `Reflect`, `Promise`, typed arrays, modules |
| **ES2016–17** | `**`, `Array.prototype.includes`, `Object.values`/`entries`, `padStart`/`padEnd`, `async`/`await`, `SharedArrayBuffer`, `Atomics` |
| **ES2018** | object rest/spread (`{...o}`), async iteration (`async function*`, `for await`), `Promise.prototype.finally`, and the RegExp upgrades: the `s` (dotAll) flag, named capture groups, lookbehind, `\p{…}` property escapes |
| **ES2019** | `flat`/`flatMap`, `Object.fromEntries`, `trimStart`/`trimEnd`, `Symbol.prototype.description`, optional catch binding (`catch {}`), stable `sort`, the JSON superset, well-formed `JSON.stringify` |
| **ES2020** | `??`, `?.`, `matchAll`, `export * as ns from`, dynamic `import()`, `import.meta`, `globalThis`, `Promise.allSettled`, **BigInt** (with the big-64 typed arrays and `DataView`/`Atomics` operations) |
| **ES2021** | `replaceAll`, `Promise.any`/`AggregateError`, logical assignment (`&&=`, `\|\|=`, `??=`), numeric separators (`1_000`), `WeakRef`/`FinalizationRegistry` |
| **ES2022** | `at`, `Object.hasOwn`, the `cause` option on `Error`, the RegExp `d` flag, **class fields**, **static initializer blocks**, **private class members** (`#x`, including `#x in obj`), **top-level `await`** |
| **ES2023** | `findLast`/`findLastIndex`, `toReversed`/`toSorted`/`toSpliced`/`with`, the hashbang line (`#!`), non-registered symbols as weak keys |
| **ES2024** | `Object.groupBy`/`Map.groupBy`, `Promise.withResolvers`, `isWellFormed`/`toWellFormed`, resizable `ArrayBuffer` and growable `SharedArrayBuffer`, `Atomics.waitAsync`, the RegExp `v` flag |
| **ES2025** | **iterator helpers**, the **`Set` methods**, **`Float16Array`**, `RegExp.escape`, `Promise.try`, RegExp pattern modifiers `(?ims-ims:…)`, duplicate named capture groups, **import attributes** with JSON modules |
| **ES2026** | `Error.isError`, `Math.sumPrecise`, the **upsert** methods (`getOrInsert`/`getOrInsertComputed`), `Iterator.concat`, **`Uint8Array` base64/hex**, **JSON source access** with `JSON.rawJSON`, **`Array.fromAsync`** |

**Annex B**, the web-compatibility annex (`escape`, `__proto__`, HTML-like comments, block-function
hoisting…), is on by default and removed entirely by
[`--annexB=false`](../reference/options.md).

Deliberately out: proper tail calls and ECMA-402 (internationalization). Temporal, explicit resource
management (`using`), `Atomics.pause` and import defer are ES2027 and not implemented.

Two things older Nashorn documentation promises are gone from this fork: the **backquote-exec
syntax** (the backquote now belongs to template literals) along with the rest of scripting mode, and
everything to do with the Security
Manager, which the JDK itself removed.

## Where next

- [Creating the engine](engine-setup.md) — the builder, options, class filtering, script libraries.
- [Using the engine](using-the-engine.md) — getting an engine, configuring it with the builder,
  evaluating, invoking, bindings and scopes.
- [Connecting with Java](connecting-with-java.md) — the `Java` object and everything interop.
- Running scripts from the command line — [The shell](../reference/shell.md).
- Trying things out interactively — [the playground](playground.md), a sample browser built by the
  reactor with the whole language and every extension as runnable, editable samples.
- Pausing and stepping through scripts — [debugging](debugging.md) with Chrome DevTools or VS Code.
