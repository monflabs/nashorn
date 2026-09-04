# Creating the engine

There are two entry points. The difference is what the engine can *do* — the API you run scripts
against afterwards is the same `javax.script` either way (see [Using the engine](using-the-engine.md)).

- **`NashornScriptEngineBuilder`** (`org.monflabs.nashorn.api.scripting`) — the fork's own builder,
  and the one to reach for. It is the **only** way to set options, class loading and filtering,
  **[script libraries](../extending/script-libraries.md)** and **[module loaders](../extending/module-loaders.md)**.
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

A builder starts with **no options** (what `jjs` runs with), adds what it is told in order — a
later setting of the same option wins, as on a command line — and `build()` validates the options
the way the command line would, throwing `IllegalArgumentException` for one it does not know. The
named methods cover the engine's configuration: the language and its extras (`annexB`, `strict`,
`scripting`, `syntaxExtensions`, `typedArrays`), the Java side (`java(false)` for the bluntest
sandbox, `classPath`, `modulePath(path, modules...)`), compilation (`optimisticTypes`,
`lazyCompilation`, `classCacheSize`, `persistentCodeCache`), the environment scripts see
(`timeZone`, `locale`, `globalPerEngine`), debugging (`dumpStackOnError`, `debugger`,
`inspect(hostAndPort, waitForClient)`); `option(...)` takes anything
else - the diagnostic switches, `--log`, the `--print-*` family - in its command-line spelling. A builder can be reused, and every
`build()` is a new engine with its own compiled-code cache and globals.

- The **class loader** is what scripts see when they reach for Java classes (`Java.type`,
  `Packages`). By default it is the current thread's context class loader.
- The **[`ClassFilter`](custom-objects.md#classfilter)** is consulted before any Java class becomes
  visible to a script — one method, `exposeToScripts(String className)`.
- The **options** are the same strings as the [command line](../reference/options.md).
- The **[script libraries](../extending/script-libraries.md)** are bundles of globals and scripts
  installed into every global the engine creates. There is no discovery — a bare engine has none, so
  the builder (or a deprecated factory overload) is the only way to add them.

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
| `scripting(boolean)` | `-scripting` | off | Shell conveniences: `#` comments, `${expr}` in double-quoted strings, heredocs, `$ENV`. |
| `syntaxExtensions(boolean)` | `--no-syntax-extensions` | on | Nashorn's own syntax: `for each`, conditional catch, expression closures. Off refuses them. |
| `typedArrays(boolean)` | `--no-typed-arrays` | on | Whether `ArrayBuffer`, the typed arrays, `DataView`, `SharedArrayBuffer` and `Atomics` exist. |

**The Java side**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `java(boolean)` | `--no-java` | on | Whether scripts may touch Java at all: off removes `Java`, `Packages`, `JavaImporter` and the package roots — the bluntest sandbox. Combine with a `classFilter` for belt and braces. |
| `classFilter(filter)` | — | none | Which Java classes a script may see, one name at a time. |
| `classLoader(loader)` | — | context loader | The loader scripts reach Java through, and libraries are discovered through. |
| `classPath(path)` | `-classpath` | none | A class path of the engine's own, on top of the application loader. |
| `modulePath(path, modules...)` | `--module-path` + `--add-modules` | none | A module layer of the engine's own; the modules to resolve are required. |

**Compilation and performance**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `optimisticTypes(boolean)` | `--optimistic-types` | off | Narrow types assumed and deoptimized when wrong: better steady state for long-running, compute-heavy scripts, slower warmup. |
| `lazyCompilation(boolean)` | `--lazy-compilation` | on | Functions compile on first call rather than with the script. |
| `classCacheSize(int)` | `--class-cache-size` | 50 | How many compiled scripts the engine's class cache holds; 0 disables it. |
| `persistentCodeCache(boolean)` | `--persistent-code-cache` | off | Compiled classes cached on disk across processes, keyed by source and configuration. |

**The environment scripts see**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `timeZone(TimeZone)` | `-timezone` | the JVM's | What `new Date()` and the local getters answer with. Pin it rather than inheriting the host's. |
| `locale(Locale)` | `--locale` | the JVM's | What `toLocaleString` and its kin answer with. |
| `globalPerEngine(boolean)` | `--global-per-engine` | off | One global shared by all bindings instead of one per bindings — see [the scope model](using-the-engine.md#the-scope-model). |
| `library(libraries...)` | — | none | Which [script libraries](../extending/script-libraries.md) to install into every global. Contributed explicitly; a bare engine has none, including the standard `host` and `fetch`. |
| `moduleLoader(loaders...)` | — | filesystem | Where `import` finds its modules: a [chain of loaders](../extending/module-loaders.md), first answer wins. Registering any loader replaces the default filesystem resolution. |

**Debugging**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `dumpStackOnError(boolean)` | `-doe` | off (builder), on (no-argument factory engine) | A script error also dumps the Java stack of its origin. |
| `debugger(boolean)` | `--debugger` | off | Scripts compile with the [debugger's](debugging.md) hooks, so a client can attach; costs some speed. |
| `inspect(hostAndPort, wait)` | `--inspect` / `--inspect-brk` | off | Listen for a Chrome DevTools Protocol client; `wait` pauses at the first statement until one attaches. Implies the debugger. |

## Engine metadata

The factory answers the standard JSR-223 queries: names `nashorn-monflabs`/`Nashorn-Monflabs` only;
MIME types `application/javascript`, `application/ecmascript`, `text/javascript`, `text/ecmascript`;
extension `js`. It deliberately does **not** register under the generic names `js`, `JavaScript` or
`ECMAScript`, nor under plain `nashorn` — those belong to any JavaScript engine (and `nashorn` to
the official library), so keeping this engine's name to its own means a `getEngineByName` lookup
never resolves here by accident and an application with several engines always gets the one it asked
for. (The MIME types and the `js` extension are unchanged, so `getEngineByMimeType`/`getEngineByExtension`
still find it — only the *names* are restricted.) The
`THREADING` parameter returns `null` — the engine makes no thread-safety promise; see
[Threads and concurrency](concurrency.md).

Every engine created by one factory shares nothing with its siblings except the factory object
itself; engine instances are independent.

## Predefined script globals

Inside any script run through `javax.script`, two extra non-writable globals exist: `context` — the
current `javax.script.ScriptContext` — and `engine` — the `ScriptEngine` running the script. They
let a script reach back into the embedding:

```java
engine.eval("print(context.getAttribute('unknownVariable'))");  // null, not an error
```
