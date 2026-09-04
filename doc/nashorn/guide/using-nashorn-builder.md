# Using the engine: NashornScriptEngineBuilder

`NashornScriptEngineBuilder` (`org.monflabs.nashorn.api.scripting`) is the fork's own way to create
an engine, and the one to reach for as soon as a script needs more than the language itself. The
plain [`getEngineByName`](using-javax-script.md) lookup gives a *bare* engine that can configure
nothing; the builder is the type-safe way to set:

- engine **[options](../reference/options.md)** — strict mode, sandboxing, the time zone, the debugger…;
- **[script libraries](../extending/script-libraries.md)** — `fetch`, timers, or your own values
  installed into every realm (there is no discovery, so a bare engine has none);
- **[module loaders](../extending/module-loaders.md)** — where `import` resolves its specifiers;
  these the builder alone can register.

Options and libraries can also be passed to the deprecated
[factory overloads](#the-deprecated-factory-overloads) below, but as raw strings and varargs rather
than checked methods.

What it produces is an ordinary `javax.script.ScriptEngine`. So this page covers **building** the
engine and using what the builder unlocks; for **running** scripts against it — `eval`, variables,
`Invocable`, the scope model, `ScriptObjectMirror`, errors — see
[Using the engine: javax.script](using-javax-script.md), which applies unchanged. For the full list
of options and every builder method, see [Creating the engine](engine-setup.md).

## Building an engine

```java
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;

ScriptEngine engine = new NashornScriptEngineBuilder().build();
engine.eval("print('Hello, World')");
```

A builder starts with **no options** (what `jjs` runs with), adds what it is told in order — a later
setting of the same option wins, as on a command line — and `build()` validates them the way the
command line would, throwing `IllegalArgumentException` for one it does not know. A builder can be
reused, and every `build()` is a **new engine** with its own compiled-code cache and globals.

?> Unlike the [no-argument factory engine](using-javax-script.md#getting-an-engine), a builder engine
does **not** set `-doe`. Call `dumpStackOnError(true)` while developing if you want the Java stack on
a script error.

## Options

Each option has a typed builder method; `option(...)` takes any other in its command-line spelling.
The two are interchangeable — `java(false)` and `option("--no-java")` build the same engine.

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .strict(true)                        // -strict
        .annexB(false)                       // --annexB=false — the ECMAScript standard alone
        .java(false)                         // --no-java — the bluntest sandbox
        .locale(Locale.US)
        .timeZone(TimeZone.getTimeZone("UTC"))
        .optimisticTypes(true)               // better steady state for long-running scripts
        .option("--class-cache-size=100")    // anything without a named method
        .build();
```

Options are fixed at construction: there is no per-`eval` switch, so two configurations means two
engines — which coexist cleanly, each its own [Context](../internals/contexts-globals.md) with its
own cache. The [options reference](../reference/options.md) and the tables in
[Creating the engine](engine-setup.md#engine-options) cover every one.

## Script libraries

A [script library](../extending/script-libraries.md) is a bundle of globals the engine installs into
**every** global it creates. Nothing is installed automatically — hand each one to `library(...)`; a
bare engine (including one from `getEngineByName`) has none, not even the standard `host` and
`fetch`:

```java
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .library(new HostLibrary(),      // setTimeout/clearTimeout, queueMicrotask, atob/btoa
                 new FetchLibrary())     // fetch, Headers, Request, Response
        .build();

engine.eval("setTimeout(() => print('tick'), 10)");
```

These libraries put an **event loop** behind the engine: `eval` returns when the script is *idle*
(its timers and microtasks have drained), not merely when its synchronous code finishes — see
[Overview and the event loop](../libraries/overview.md). A script that leaves an interval running
keeps `eval` from returning, so clear it in the same evaluation.

## Module loaders and import

By default `import` resolves against the filesystem. Register a
[module loader](../extending/module-loaders.md) to change that — files under a root, class-path
resources, Java values, or the [Node modules](../libraries/node.md). Loaders are consulted in the
order given, first answer wins; registering any loader replaces the default filesystem resolution.

```java
import org.monflabs.nashorn.api.modules.PathModuleLoader;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .moduleLoader(new PathModuleLoader(scriptsDir))   // import resolves under scriptsDir
        .build();

// scriptsDir/greet.mjs:  export function hi(n) { return 'hi ' + n; }
engine.eval("import { hi } from 'greet.mjs'; print(hi('there'))");
```

The [Node resolver](../libraries/node.md) is the experimental `nashorn-node` artifact, registered the
same way — `.moduleLoader(new NodeModuleLoader())` — after which `import fs from 'fs'` and its kin
resolve.

## Class loading and filtering

The builder also sets the loader scripts reach Java through and the filter that gates it:

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .classLoader(myClassLoader)                              // default: the context loader
        .classFilter(name -> name.startsWith("com.example."))    // which classes scripts may see
        .build();
```

`classFilter` is consulted before any Java class becomes visible to a script; combine it with
`java(false)` for a belt-and-braces sandbox. See
[Custom objects and extensions](custom-objects.md#classfilter).

## Running the engine

From here on there is nothing builder-specific. `build()` returned a `javax.script.ScriptEngine`, so
`eval`, `engine.put`/`get`, `Compilable`, `Invocable`, `getInterface`, the
[scope model](using-javax-script.md#the-scope-model),
[`ScriptObjectMirror`](using-javax-script.md#scriptobjectmirror) and
[error handling](using-javax-script.md#errors) all work exactly as on the
[javax.script page](using-javax-script.md). The only difference the builder made was deciding *what
the engine can do* before you ran anything against it.

## The deprecated factory overloads

Before the builder, `NashornScriptEngineFactory` had `getScriptEngine(...)` overloads taking options,
a class loader, a `ClassFilter` or libraries directly:

```java
ScriptEngine strict = new NashornScriptEngineFactory().getScriptEngine("-strict", "--no-java");
```

Those are **deprecated** (since `2017.0.0`) in favour of the builder and kept only for
compatibility — and note they cannot register a **module loader**, which the builder alone can. Use
the builder for new code.
