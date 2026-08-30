# Creating the engine

`ScriptEngineManager.getEngineByName("nashorn")` is all most embedders need. When you want control —
options, class loading, class filtering — instantiate the factory yourself:
`org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory`.

## The factory's overloads

```java
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

NashornScriptEngineFactory factory = new NashornScriptEngineFactory();

ScriptEngine plain      = factory.getScriptEngine();
ScriptEngine withLoader = factory.getScriptEngine(myClassLoader);
ScriptEngine filtered   = factory.getScriptEngine(myClassFilter);
ScriptEngine optioned   = factory.getScriptEngine("--annexB=false", "-strict");
ScriptEngine both       = factory.getScriptEngine(options, myClassLoader);
ScriptEngine all        = factory.getScriptEngine(options, myClassLoader, myClassFilter);
```

- The **class loader** is what scripts see when they reach for Java classes (`Java.type`,
  `Packages`). By default it is the current thread's context class loader.
- The **[`ClassFilter`](custom-objects.md#classfilter)** is consulted before any Java class becomes
  visible to a script — one method, `exposeToScripts(String className)`.
- The **options** are the same strings as the [command line](../reference/options.md).

!> The no-argument factory methods use a default option set of `{"-doe"}` (dump stack traces on
error). Passing your own `String... args` **replaces** that default rather than adding to it — if
you still want the stack traces, include `-doe` in your list.

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

The factory answers the standard JSR-223 queries: names `nashorn`/`Nashorn`, `js`/`JS`,
`javascript`/`JavaScript`, `ecmascript`/`ECMAScript`; MIME types `application/javascript`,
`application/ecmascript`, `text/javascript`, `text/ecmascript`; extension `js`. The
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
