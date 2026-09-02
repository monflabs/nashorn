# Standard libraries

ECMAScript defines the language and its built-in objects; what a script expects from its *host* —
timers, `fetch`, Base64 — comes from the platform. The standard libraries are this engine's
platform: [script libraries](../extending/script-libraries.md), shipped inside `nashorn-core`
itself, that give every engine the host functions scripts written for browsers or Node.js reach
for — built the way the language's own objects are, and installed into every global an engine
creates unless told otherwise.

| Library | Provides | Page |
| --- | --- | --- |
| `host` | `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `queueMicrotask`, `atob`, `btoa` | [host](host.md) |
| `fetch` | `fetch`, `Headers`, `Request`, `Response` | [fetch](fetch.md) |

The engine also ships a **[Node module resolver](node.md)**: `import fs from 'fs'` reaches a built-in
`fs` module (synchronous, callback and promise forms). Unlike the libraries above it is reached by
`import` rather than as a global.

## Getting them

The classes are part of `nashorn-core`, but nothing is installed automatically: you contribute each
library you want to the engine builder. A bare engine has neither `host` nor `fetch` — which is also
how an embedder sandboxing scripts leaves `fetch` out: by simply not adding it.

```java
import org.monflabs.nashorn.libs.HostLibrary;
import org.monflabs.nashorn.libs.FetchLibrary;

// both
ScriptEngine engine = new NashornScriptEngineBuilder().library(new HostLibrary(), new FetchLibrary()).build();
// the timers without fetch
ScriptEngine restricted = new NashornScriptEngineBuilder().library(new HostLibrary()).build();
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

The libraries are `ScriptLibrary` implementations in `org.monflabs.nashorn.libs`, built with the
engine's own machinery: the host functions are built-in `ScriptFunction`s over a Java `switch`, and
`Headers`, `Request` and `Response` are `@ScriptClass` classes that nasgen turns into real
prototypes with accessor properties and symbol-keyed methods — exactly what the language's `Map`
or `Promise` are. That machinery is internal, which a library shipped with the engine may use and
a third-party one may not; for the public route see the
[script libraries](../extending/script-libraries.md) page and the
[extension APIs](../extending/apis.md) inventory cover what they use.
