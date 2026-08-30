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

**Specifier resolution is file-based and literal**: a specifier is resolved as a path relative to
the directory of the importing module (absolute paths as themselves), with no search path, no
`node_modules`, no URL loading, and **no extension guessing** — `import "./counter.js"` names a
real, readable file, extension included. Bare specifiers (`import "lodash"`) do not resolve.

## Running modules — the honest part

!> There is currently **no user-facing entry point** for modules: no `jjs` flag, no `loadModule`
builtin, and nothing on `javax.script`. `engine.eval` and `jjs` always treat their input as a
*script*, so a file containing `export` will not parse there. The only way to evaluate a module
today is the engine's internal API, which is unexported and carries no compatibility promise.

That said, here is exactly how it is done, because it is done — the engine's own tests run this way.
The internal `Context` API is opened with `--add-exports`:

```text
--add-exports org.openjdk.nashorn/org.openjdk.nashorn.internal.runtime=ALL-UNNAMED
--add-exports org.openjdk.nashorn/org.openjdk.nashorn.internal.runtime.options=ALL-UNNAMED
--add-exports org.openjdk.nashorn/org.openjdk.nashorn.internal.objects=ALL-UNNAMED
```

```java
import java.io.File;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ErrorManager;
import org.openjdk.nashorn.internal.runtime.ModuleRecord;
import org.openjdk.nashorn.internal.runtime.Source;
import org.openjdk.nashorn.internal.runtime.options.Options;

Options options = new Options("nashorn");
options.process(new String[0]);
Context context = new Context(options, new ErrorManager(),
        Thread.currentThread().getContextClassLoader());

Global global = context.createGlobal();
Context.runWithGlobal(global, () -> {           // establishes the realm for the call
    ModuleRecord main = context.evaluateModule(
            Source.sourceFor("main.js", new File("main.js")));
    Object count = main.read("count");          // read an export from Java
});
```

`evaluateModule` is `loadModule(...).link().evaluate()` — the record it returns also offers
`exportNames()` and `namespace()` (the module's namespace object). Working fixtures live in
`core/src/test/scripts/modules/`, driven by
`core/src/test/java/org/openjdk/nashorn/internal/runtime/test/ModuleTest.java`.

## Meanwhile, in scripts

Until modules get a public door, the practical structuring tools for script code are
[`load`](../reference/builtins.md#loadsource) — same global, think `#include` — and
`loadWithNewGlobal` for isolation. Neither gives you live bindings or module scope; both are honest
about being what they are.

How the module machinery works inside — the `MODULE_SCOPE` prologue, the record state machine,
getter-only import accessors, cycle handling — is on the
[module internals page](../internals/modules.md).
