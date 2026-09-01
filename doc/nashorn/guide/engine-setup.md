# Creating the engine

`ScriptEngineManager.getEngineByName("nashorn-monflabs")` is all most embedders need. When you want
control — options, class loading, class filtering, script libraries — build the engine yourself with
`org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder`.

## The builder

```java
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .annexB(false)                                   // the named options...
        .strict(true)
        .option("--class-cache-size=100")                // ...and any other, as on the command line
        .classLoader(myClassLoader)
        .classFilter(name -> name.startsWith("com.example."))
        .library(myLibrary, anotherLibrary)              // script libraries
        .discoveredLibraries("host")                     // which registered ones apply; none if no name
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
`inspect(hostAndPort, waitForClient)`) and `discoveredLibraries`; `option(...)` takes anything
else - the diagnostic switches, `--log`, the `--print-*` family - in its command-line spelling. A builder can be reused, and every
`build()` is a new engine with its own compiled-code cache and globals.

The `javax.script` route still exists, of course — `new ScriptEngineManager().getEngineByName("nashorn-monflabs")`
or `new NashornScriptEngineFactory().getScriptEngine()` — and gives the defaults plus `-doe`. The
factory's other `getScriptEngine` overloads (options, class loader, filter, libraries, in every
combination) are **deprecated** in favour of the builder and kept for compatibility.

- The **class loader** is what scripts see when they reach for Java classes (`Java.type`,
  `Packages`). By default it is the current thread's context class loader.
- The **[`ClassFilter`](custom-objects.md#classfilter)** is consulted before any Java class becomes
  visible to a script — one method, `exposeToScripts(String className)`.
- The **options** are the same strings as the [command line](../reference/options.md).
- The **[script libraries](../extending/script-libraries.md)** are bundles of globals and scripts installed into
  every global the engine creates; the ones passed here join those discovered as services.

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
| `discoveredLibraries(names...)` | `--libraries` | all | Which [standard and registered libraries](../extending/script-libraries.md) apply; none if no name is given. |
| `moduleLoader(loaders...)` | — | filesystem | Where `import` finds its [modules](modules.md#module-loaders): a chain, first answer wins. Registering any loader replaces the default filesystem resolution. |

**Debugging**

| Builder | Option | Default | What it decides |
| --- | --- | --- | --- |
| `dumpStackOnError(boolean)` | `-doe` | off (builder), on (no-argument factory engine) | A script error also dumps the Java stack of its origin. |
| `debugger(boolean)` | `--debugger` | off | Scripts compile with the [debugger's](debugging.md) hooks, so a client can attach; costs some speed. |
| `inspect(hostAndPort, wait)` | `--inspect` / `--inspect-brk` | off | Listen for a Chrome DevTools Protocol client; `wait` pauses at the first statement until one attaches. Implies the debugger. |

## Engine metadata

The factory answers the standard JSR-223 queries: names `nashorn-monflabs`/`Nashorn-Monflabs`,
`js`/`JS`, `javascript`/`JavaScript`, `ecmascript`/`ECMAScript`; MIME types
`application/javascript`, `application/ecmascript`, `text/javascript`, `text/ecmascript`;
extension `js`. The engine deliberately does **not** answer to plain `nashorn`: that name belongs
to the official Nashorn library, and keeping the two lookups distinct means an application with
both on its class path always gets the engine it asked for. The
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
