# Standard libraries

ECMAScript defines the language and its built-in objects; what a script expects from its *host* —
timers, `fetch`, Base64 — comes from the platform. `nashorn-libs` is this engine's platform: a set
of [script libraries](../extending/script-libraries.md) that give every engine the host functions
scripts written for browsers or Node.js reach for, implemented in Java on the engine's public API
and installed into every global an engine creates.

| Library | Provides | Page |
| --- | --- | --- |
| `host` | `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `queueMicrotask`, `atob`, `btoa` | [host](host.md) |
| `fetch` | `fetch`, `Headers`, `Request`, `Response` | [fetch](fetch.md) |

## Getting them

Add the artifact next to `nashorn-core`:

```xml
<dependency>
    <groupId>org.monflabs.nashorn</groupId>
    <artifactId>nashorn-libs</artifactId>
    <version>2017.0.0</version>
</dependency>
```

That is all: the libraries are registered as `ScriptLibrary` services (in the module descriptor
and in `META-INF/services`), so every engine whose class loader sees the jar has them — on the
class path, on the module path (`org.monflabs.nashorn.libs`), and in `jjs` with the jar on its
class path. Nothing to call, nothing to configure.

To choose, use `--libraries`: `--libraries=host` for the timers without `fetch`,
`--libraries=none` for a bare engine. Or hand a library to the factory yourself, which applies it
whatever the option says:

```java
ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(
        new String[] { "--libraries=none" }, new HostLibrary());
```

## The event loop

Timers and `fetch` need somewhere to run once the script's synchronous code has returned, and an
embedded engine has no event loop of its own — so the engine got one, behind the microtask queue
promise reactions already used. It runs when the JavaScript stack empties, as the outermost `eval`
or function call from Java unwinds: first the microtasks, then, for as long as a timer is waiting,
a task is posted or an operation is pending, it waits for the next of them, runs it, and runs the
microtasks it produced.

The consequence for an embedder is the one rule worth remembering: **`eval` returns when the script
is idle**, not merely when its synchronous code is done. A script that schedules nothing returns
exactly as it always did; one that sets a 300 ms timer returns after 300 ms; one that starts a
`fetch` returns once the response has been handled. An interval nobody clears keeps the `eval`
alive — like a Node.js process — until the host ends it by interrupting the thread, which the
playground's Stop button and the debugger's `terminate()` both do.

Everything runs on the script's own thread, with the realm bound: a timer callback or a promise
reaction never races the code that scheduled it. Java code that wants the same for its own
asynchronous work uses the public `EventLoop` API (`org.monflabs.nashorn.api.scripting`), which the
two libraries themselves are built on — `schedule` for a timer, `pending()` for an operation that
completes on another thread, `queueMicrotask`.

## Writing one of your own

The libraries are ordinary `ScriptLibrary` implementations — one Java class each, a function
object per entry with a `switch` on an enum, and for `fetch` a script half defining the classes over
a Java transport. They are a reasonable template for a library of your own; the
[script libraries](../extending/script-libraries.md) page and the
[extension APIs](../extending/apis.md) inventory cover what they use.
