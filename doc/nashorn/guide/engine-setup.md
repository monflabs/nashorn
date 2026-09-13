# Creating the engine

There are two entry points. The difference is what the engine can *do* — the API you run scripts
against afterwards is the same `javax.script` either way (see [Using the engine](using-the-engine.md)).

- **`NashornScriptEngineBuilder`** (`org.monflabs.nashorn.api.scripting`) — the fork's own builder,
  and the one to reach for. It is the type-safe way to set options, class loading and filtering,
  **[script libraries](../extending/script-libraries.md)** and **[module loaders](../extending/module-loaders.md)** —
  and the only way to register a module loader. (Options, a loader, a filter and libraries can also
  go to the deprecated factory overloads below, as raw strings and varargs.)
- **`javax.script`** — `new ScriptEngineManager().getEngineByName("nashorn-monflabs")`, the standard
  JSR-223 lookup, for **simple script evaluation**. It returns a *bare* engine: the defaults, and no
  libraries or module loaders.

Use `getEngineByName` for a quick eval; use the builder for anything that needs configuration or that
runs scripts using libraries or `import`.

## The builder

```java
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .annexB(false)                                   // the named options...
        .strict(true)
        .option("--class-cache-size=100")                // ...and any other, as on the command line
        .classLoader(myClassLoader)
        .classFilter(name -> name.startsWith("com.example."))
        .library(myLibrary, anotherLibrary)              // script libraries - contributed explicitly, none by default
        .moduleLoader(new PathModuleLoader(scriptsDir))  // where import finds its modules
        .build();
```

A builder starts with **no options** (what the shell runs with), adds what it is told in order — a
later setting of the same option wins, as on a command line — and `build()` validates the options
the way the command line would, throwing `IllegalArgumentException` for one it does not know. The
named methods cover the engine's configuration:

| Area | Methods |
| --- | --- |
| The language | `annexB`, `strict`, `syntaxExtensions`, `typedArrays` |
| Asynchrony | `eventLoop` — off by default; everything that waits needs it |
| The Java side | `java(false)` for the bluntest sandbox, `classPath`, `modulePath(path, modules…)`, `classLoader`, `classFilter` |
| Compilation | `optimisticTypes`, `lazyCompilation`, `classCacheSize`, `persistentCodeCache` |
| The environment scripts see | `timeZone`, `locale`, `globalPerEngine` |
| Debugging | `dumpStackOnError`, `debugger`, `inspect(hostAndPort, waitForClient)` |
| Extensions | `library(…)`, `moduleLoader(…)` |

`option(...)` takes anything else — the diagnostic switches, `--log`, the `--print-*` family — in its
command-line spelling. A builder can be reused, and every `build()` is a new engine with its own
compiled-code cache and globals.

- The **class loader** is what scripts see when they reach for Java classes (`Java.type`,
  `Packages`). By default it is the current thread's context class loader.
- The **[`ClassFilter`](custom-objects.md#classfilter)** is consulted before any Java class becomes
  visible to a script — one method, `exposeToScripts(String className)`.
- The **options** are the same strings as the [command line](../reference/options.md).
- The **[script libraries](../extending/script-libraries.md)** are bundles of globals and scripts
  installed into every global the engine creates. There is no discovery — a bare engine has none, so
  the builder (or a deprecated factory overload) is the only way to add them.

A library and the event loop usually travel together:

```java
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .eventLoop(true)                                 // timers and fetch need somewhere to run
        .library(new HostLibrary(), new FetchLibrary())  // adds setTimeout, fetch, ...
        .build();
engine.eval("setTimeout(() => print('tick'), 10)");      // needs both lines above
```

?> Both are needed. The library supplies the function; the
[event loop](../libraries/overview.md#the-event-loop) runs what it schedules. Without
`.eventLoop(true)` the call throws a `TypeError` saying so, rather than quietly scheduling work
nothing would run.

## The javax.script route, and its options

`new ScriptEngineManager().getEngineByName("nashorn-monflabs")` returns a working engine with the
defaults — and **no way to pass options**: the JSR-223 lookup takes a name and nothing else. (The
name must be `nashorn-monflabs`; the engine does not answer to the generic `js`/`JavaScript`/
`ECMAScript`.) For evaluating scripts and nothing more, that is all
you need.

To pass an option while staying on the factory rather than the builder, `NashornScriptEngineFactory`
has argument-taking `getScriptEngine` overloads that take the option strings directly:

```java
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;

ScriptEngine strict = new NashornScriptEngineFactory().getScriptEngine("-strict", "--no-java");
```

Those overloads (options, a class loader, a `ClassFilter`, libraries, in every combination) are
**deprecated** in favour of the builder and kept only for compatibility — and note they still cannot
register a **module loader**, which the builder alone can. So in practice: `getEngineByName` for the
defaults, and `NashornScriptEngineBuilder` the moment you need an option, a library, or `import`. The
plain no-argument `getScriptEngine()` and every `getEngineByName(...)` stay the JSR-223 entry points
and give the defaults (the no-argument factory engine additionally sets `-doe`).

!> The factory's no-argument engine has `-doe` (dump the Java stack on a script error) on; a
builder starts without it — call `dumpStackOnError(true)` while developing if you want it.

Options are fixed at engine construction. There is no per-`eval` or per-context option switch; two
configurations means two engines, and two engines coexist cleanly in one process (each is its own
[Context](../internals/contexts-globals.md), with its own compiled-code cache).

## Engine options

Every configuration choice the engine offers, by its builder method and its command-line spelling —
the two are interchangeable, `option("--no-java")` and `java(false)` build the same engine. The
[options reference](../reference/options.md) has the diagnostic switches (`--log`, `--print-*`,
tracing) that stay with `option(...)`.

**The language and its extras**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `annexB(boolean)` | `--annexB` | on | Annex B, the web's legacy: `escape`, `__proto__`, block-level function hoisting, `<!--` comments. Off for the ECMAScript standard alone. |
| `strict(boolean)` | `-strict` | off | Every script runs as strict-mode code. |
| `syntaxExtensions(boolean)` | `--no-syntax-extensions` | on | Nashorn's own syntax: `for each`, conditional catch, expression closures. Off refuses them. |
| `typedArrays(boolean)` | `--no-typed-arrays` | on | Whether `ArrayBuffer`, the typed arrays, `DataView`, `SharedArrayBuffer` and `Atomics` exist. |

**The Java side**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `java(boolean)` | `--no-java` | on | Whether scripts may touch Java at all: off removes `Java`, `Packages`, `JavaImporter` and the package roots — the bluntest sandbox. Combine with a `classFilter` for belt and braces. |
| `classFilter(filter)` | — | none | Which Java classes a script may see, one name at a time. |
| `classLoader(loader)` | — | context loader | The loader scripts reach Java through. |
| `classPath(path)` | `-classpath` | none | A class path of the engine's own, on top of the application loader. |
| `modulePath(path, modules...)` | `--module-path` + `--add-modules` | none | A module layer of the engine's own; the modules to resolve are required. |

**Compilation and performance**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `optimisticTypes(boolean)` | `--optimistic-types` | on | Narrow types assumed and deoptimized when wrong: better steady state for long-running, compute-heavy scripts, slower warmup. Turn it off for a run-once script. |
| `lazyCompilation(boolean)` | `--lazy-compilation` | on | Functions compile on first call rather than with the script. Optimistic types need it: turning it off also turns them off unless they were asked for explicitly, which is an error. |
| `classCacheSize(int)` | `--class-cache-size` | 50 | How many compiled scripts the engine's class cache holds; 0 disables it. |
| `persistentCodeCache(boolean)` | `--persistent-code-cache` | off | Compiled classes cached on disk across processes, keyed by source and configuration. |

**The environment scripts see**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `timeZone(TimeZone)` | `-timezone` | the JVM's | What `new Date()` and the local getters answer with. Pin it rather than inheriting the host's. |
| `locale(Locale)` | `--locale` | the JVM's | What `toLocaleString` and its kin answer with. |
| `globalPerEngine(boolean)` | `--global-per-engine` | off | One global shared by all bindings instead of one per bindings — see [the scope model](using-the-engine.md#the-scope-model). |

**Asynchrony**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `eventLoop(boolean)` | `--event-loop` | **off** | Whether the realm has an [event loop](../libraries/overview.md#the-event-loop). With it off, every capability that would need one — `Promise`, `async`/`await`, async generators, the timers, `queueMicrotask`, `fetch` — throws a `TypeError` the moment it is used. Turn it on to run asynchronous code; leave it off for a purely synchronous embedder. With it on, `eval` returns when the script is *idle*, not merely when its synchronous code finishes. |

**Extensions**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `library(libraries...)` | — | none | Which [script libraries](../extending/script-libraries.md) to install into every global. Contributed explicitly; a bare engine has none, including the standard `host` and `fetch`. |
| `moduleLoader(loaders...)` | — | filesystem | Where `import` finds its modules: a [chain of loaders](../extending/module-loaders.md), first answer wins. Registering any loader replaces the default filesystem resolution. |

**Debugging**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `dumpStackOnError(boolean)` | `-doe` | off (builder), on (no-argument factory engine) | A script error also dumps the Java stack of its origin. |
| `debugger(boolean)` | `--debugger` | off | Scripts compile with the [debugger's](debugging.md) hooks, so a client can attach; costs some speed. |
| `inspect(hostAndPort, wait)` | `--inspect` / `--inspect-brk` | off | Listen for a Chrome DevTools Protocol client; `wait` pauses at the first statement until one attaches. Implies the debugger. |

## Making the most of an engine

Nashorn compiles to bytecode and links call sites as they run, so nearly all of its cost is paid the
first time. Reusing the right things is what turns that into throughput.

**Reuse the engine.** Every `build()` makes a new engine with its own
[Context](../internals/contexts-globals.md): its own class loaders, its own compiled-class cache, its
own linker. Two engines share nothing but the JVM-wide structure classes. One long-lived engine per
configuration is the shape to aim for; a fresh engine per request throws away every compilation.

**Reuse compiled scripts.** A compiled class belongs to the *engine*, not to a realm, and one
`CompiledScript` runs against as many realms as you like:

```java
CompiledScript compiled = ((Compilable) engine).compile(source);   // once
compiled.eval(contextA);                                           // many
compiled.eval(contextB);
```

Even without `Compilable`, evaluating the same `Source` twice is a cache hit — the engine's class
cache is keyed by source (`--class-cache-size`, 50 by default), so re-running a script the engine has
seen costs no compilation. `--persistent-code-cache` extends that across **processes**, which is what
to reach for when startup time rather than steady state is the problem.

**Let the code get hot.** [Optimistic typing](../internals/optimistic-typing.md) is on by default: the
first runs deoptimise and recompile, and the steady state afterwards is several times faster. That
bargain only pays for code that runs more than once. For a script evaluated once and discarded,
`.optimisticTypes(false)` skips the recompiles and starts faster.

**Reuse bindings.** A fresh `Bindings` means a fresh realm — a complete set of built-ins — because
the engine associates one global with each bindings object (the
[scope model](using-the-engine.md#the-scope-model)). Built-ins are created lazily, so a realm is
cheaper than it sounds, but not free. Create bindings deliberately and hold on to them, rather than
calling `createBindings()` per request.

### Sharing a realm

Two evaluations can share one realm, and so share state. Three ways, in increasing order of
bluntness:

1. **Pass the same `Bindings` object.** The realm is stored *in* the bindings, so any
   `ScriptContext` whose `ENGINE_SCOPE` is that same object evaluates in the same realm. This is the
   ordinary way to keep state across evaluations.
2. **Hand the realm to another bindings.** The realm travels as the value under the reserved key
   `NashornScriptEngine.NASHORN_GLOBAL` — and `createBindings()` returns a `ScriptObjectMirror` that
   *is* the realm. So a plain `SimpleBindings` can be pointed at an existing one:

   ```java
   Bindings shared = engine.createBindings();          // or engine.getBindings(ENGINE_SCOPE)
   Bindings other  = new SimpleBindings();
   other.put(NashornScriptEngine.NASHORN_GLOBAL, shared);
   // a context whose ENGINE_SCOPE is `other` now evaluates in `shared`'s realm
   ```

3. **`globalPerEngine(true)`** (`--global-per-engine`) collapses the model: one realm for the whole
   engine, whatever bindings are passed. Use it when you want JSR-223's bindings plumbing out of the
   picture entirely.

!> A realm is **single-threaded**. Sharing one is how you share *state*, not how you get parallelism:
two threads evaluating against one realm at the same time is a data race, and nothing detects it. For
concurrency, give each thread its own realm and share the compiled code — see
[Threads and concurrency](concurrency.md).

## Engine metadata

The factory answers the standard JSR-223 queries:

| Query | Answer |
| --- | --- |
| `getNames()` | `nashorn-monflabs`, `Nashorn-Monflabs` — **and nothing else** |
| `getMimeTypes()` | `application/javascript`, `application/ecmascript`, `text/javascript`, `text/ecmascript` |
| `getExtensions()` | `js` |
| `getEngineName()` | `OpenJDK-Monflabs` |
| `getLanguageName()` | `ECMAScript` |
| `getParameter("THREADING")` | `null` — no thread-safety promise; see [Threads and concurrency](concurrency.md) |

The restricted name list is deliberate. `js`, `JavaScript` and `ECMAScript` belong to *any*
JavaScript engine, and plain `nashorn` to the official library, so keeping this engine's names to
its own means a `getEngineByName` lookup never resolves here by accident, and an application with
several engines always gets the one it asked for. The MIME types and the `js` extension are
unchanged, so `getEngineByMimeType` and `getEngineByExtension` still find it — only the *names* are
restricted.

Every engine created by one factory shares nothing with its siblings except the factory object
itself; engine instances are independent.

## Predefined script globals

Inside any script run through `javax.script`, two extra non-writable globals exist: `context` — the
current `javax.script.ScriptContext` — and `engine` — the `ScriptEngine` running the script. They
let a script reach back into the embedding:

```java
engine.eval("print(context.getAttribute('unknownVariable'))");  // null, not an error
```
