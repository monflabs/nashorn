# ES modules

Nashorn implements the ECMAScript 2015 module system in full — `import`, `export`, default and named
exports, `export *`, namespace imports, live bindings, cycles — and its behaviour is exercised by
the module slice of the official conformance suite. What it does **not** yet have is a public way to
run one. This page covers both halves honestly.

## Writing modules

The syntax and the semantics are the standard ones:

```js
// counter.js
export let count = 0;
export function increment() { count++; }
export default "the default";
```

```js
// main.js
import theDefault, { count, increment } from "./counter.js";
import * as counter from "./counter.js";

increment();
print(count);            // 1 — a live binding, not a copy
print(theDefault);       // the default
print(counter.count);    // 1
```

The guarantees you can rely on:

- **Live bindings** — an imported name reads the exporting module's current value; it is not copied
  at import time, and assigning to it is an error.
- **A module runs once per realm** — every import of the same resolved file gets the same module
  instance; its top-level code has run exactly once.
- **Module scope** — top-level `var`, `let` and `function` declarations belong to the module, not
  the global object.
- **Link before run** — the whole import graph is resolved before any module body executes, so a
  missing or ambiguous export is an error up front, not halfway through.
- **Cycles work**, with the standard temporal-dead-zone behaviour for bindings read too early.

With the default (no loaders registered), **specifier resolution is file-based and literal**: a specifier is resolved as a path relative to
the directory of the importing module (absolute paths as themselves), with no search path, no
`node_modules`, no URL loading, and **no extension guessing** — `import "./counter.js"` names a
real, readable file, extension included. Bare specifiers (`import "lodash"`) do not resolve.

## Running modules

A source handed to `eval` (or a file handed to `jjs`) that parses as a **module** runs as one:
`import` and `export` are reserved words, so a module is never a valid script, and the engine
re-parses on that failure. The completion value is the module's namespace object, so exports are
one `getMember` away:

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .moduleLoader(new PathModuleLoader(Path.of("scripts")))
        .build();

ScriptObjectMirror ns = (ScriptObjectMirror) engine.eval(
        "import { count, increment } from 'counter.js';\n" +
        "increment();\n" +
        "export { count };");
ns.getMember("count");    // 1 - a live binding
```

A plain script is unaffected (one parse, as always), and a source that parses as neither reports
the script's own error. `Compilable` stays script-only.

## Module loaders

Where an `import`'s specifier comes from is the engine's **module-loading chain**
(`org.monflabs.nashorn.api.modules`), registered on the builder: every loader is asked in order,
the first that answers wins, and a loader that does not have the module returns null. With no
loader registered, the default is the filesystem behaviour above — a specifier as a path relative
to the importing module; registering any loader replaces it (add a `PathModuleLoader` to keep
filesystem access).

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .moduleLoader(
            new PathModuleLoader(Path.of("scripts")),                          // files under a root
            new ResourceModuleLoader(MyApp.class, "/com/example/modules"),     // class-path resources
            new JavaModuleLoader()                                             // modules in pure Java
                .add("math", Map.of("TAU", 2 * Math.PI, "add", addFunction)))
        .build();
```

- **`PathModuleLoader(root)`** — bare and entry specifiers against the root, `./x`/`../x` against
  the importing module's directory, absolute paths as themselves. Literal names, no extension
  guessing; canonical name = the absolute path.
- **`ResourceModuleLoader(anchor | loader, root)`** — resources under the root (`a` →
  `/com/example/modules/a`), `./` resolved among resources and never above the root; canonical
  name = `classpath:/<path>`, so a resource module and a file module never collide.
- **`JavaModuleLoader`** — modules whose exports are Java values, `"default"` for the default
  export: `import { TAU, add } from "math"` with no script behind it. The values are fixed (no
  live bindings — export `JSObject` functions for behaviour); each realm gets its own namespace
  object over them.
- **Your own** — `ModuleLoader` is one method, `load(specifier, referrer)`: return
  `Module.source(name, text)` or `Module.values(name, exports)`, or null to pass. A loader
  resolves `./x` against a referrer whose `origin()` it recognises as its own, and passes on a
  foreign one. Names are the once-per-realm registry key: same name, same module instance.

A specifier no loader answers is a `TypeError` naming it and the importing module, at link time —
before anything runs, as the specification wants.

Working through the internal API - `Context.evaluateModule`, `ModuleRecord` - remains possible on
the class path and is what the engine's own tests use, but it is no longer the only door.

## Meanwhile, in scripts

Until modules get a public door, the practical structuring tools for script code are
[`load`](../reference/builtins.md#loadsource) — same global, think `#include` — and
`loadWithNewGlobal` for isolation. Neither gives you live bindings or module scope; both are honest
about being what they are.

How the module machinery works inside — the `MODULE_SCOPE` prologue, the record state machine,
getter-only import accessors, cycle handling — is on the
[module internals page](../internals/modules.md).
