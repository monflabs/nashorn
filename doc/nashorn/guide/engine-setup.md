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

## Options worth setting deliberately

| Option | When |
| --- | --- |
| `-strict` | Everything the engine runs should be strict-mode code. |
| `--annexB=false` | You want the ECMAScript standard alone — no `escape`, no `__proto__`, no web-compat scoping. |
| `--no-java` | Scripts must not touch Java at all: removes `Java`, `Packages`, `JavaImporter` and the package roots. Combine with a `ClassFilter` that returns false if you want belt and braces. |
| `--global-per-engine` | All bindings of this engine should share one global — see [the scope model](using-the-engine.md#the-scope-model). |
| `-timezone`, `--locale` | Pin `Date` and `toLocaleString` behaviour rather than inheriting the JVM defaults. |
| `-ot` / `--optimistic-types` | Long-running, compute-heavy scripts: better steady state, slower warmup. |

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
