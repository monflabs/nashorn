# Module loaders

Where an `import` finds its modules is an extension point: the engine holds a **chain of module
loaders** (`org.monflabs.nashorn.api.modules`), registered when the engine is built, and every
specifier is offered to each loader in order — the first that answers wins. This page is for the
developer *providing* modules: the contract, the built-in loaders as templates, and modules whose
exports are pure Java. For the consumption side — writing modules, `import`/`export`, running
them — see [ES modules](../guide/modules.md) in the user's guide.

## The contract

```java
@FunctionalInterface
public interface ModuleLoader {
    Module load(String specifier, Module referrer);   // null = "not mine", the chain moves on
}
```

- **`specifier`** is the text between the quotes of the `import`, untouched.
- **`referrer`** is the module the import was written in — null for an entry module. A loader
  resolves a relative specifier (`./x`, `../x`) against a referrer it recognises as **its own**,
  by the `origin()` it stored when it made that module, and returns null for a foreign one so the
  loader that owns the referrer gets its turn.
- The answer is a **`Module`**: `Module.source(name, text)` or `Module.source(name, text, origin)`
  for a script module, `Module.values(name, exports)` for a pure-Java one.
- **Names are canonical identity.** `Module.name()` keys the once-per-realm registry — two answers
  with one name are one module instance, live bindings and all — and is what stack traces show.
  Keep a loader's names disjoint from every other loader's: an absolute file path, a
  `classpath:/…` URL, a registered name.
- A specifier **no loader answers** is a link-time `TypeError` naming it and its importer — before
  anything runs, as the specification wants. A loader that *has* the module but cannot read it
  should throw (e.g. `UncheckedIOException`) rather than return null: null means "ask someone else".
- The chain is consulted **once per (importing module, specifier)** — resolutions are memoized on
  the importer — so a loader may do real work (I/O, computation) without being hammered.

Registration is engine-level, on the [builder](../guide/engine-setup.md#the-builder):

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .moduleLoader(loaderA, loaderB, loaderC)     // chain order
        .build();
```

With no loader registered, the default is filesystem resolution relative to the importing module;
registering **any** loader replaces it — add a `PathModuleLoader` to keep filesystem access.

## The built-in loaders

Three ship with the engine, and double as templates:

| Loader | Serves | Resolution | Canonical name |
| --- | --- | --- | --- |
| `PathModuleLoader(Path root)` | files | bare/entry specifiers against `root`; `./`, `../` against the importer's directory; absolute paths as themselves; literal names, no extension guessing | the absolute normalised path |
| `ResourceModuleLoader(Class or ClassLoader, String root)` | class-path resources | specifiers under `root` (`a` → `/com/example/modules/a`, literally); `./` among resources, never above the root | `classpath:/<path>` |
| `JavaModuleLoader().add(name, exports)` | pure-Java modules | exact registered names only | the registered name |

`ResourceModuleLoader` is how a **jar ships modules**: put them under a resource folder, register
the loader over it, and `import { x } from "a"` needs no files on disk.

## Modules in pure Java

`Module.values(name, exports)` is a module with no script behind it: the map's entries are the
named exports, the `"default"` key the default export.

```java
JSObject add = /* an AbstractJSObject function - see Objects from Java */;
new NashornScriptEngineBuilder()
        .moduleLoader(new JavaModuleLoader()
                .add("math", Map.of("TAU", 2 * Math.PI, "add", add)))
        .build();
```

```js
import theDefault, { TAU, add } from "math";
import * as math from "math";
```

The values are **fixed** — imports of them are not live bindings, and every realm sees the same
Java objects, each realm getting its own namespace object over them. For behaviour, export
[`JSObject` functions](java-objects.md), which work in any realm; for values that must vary per
realm, a script module (whose body runs per realm) over a Java core is the shape.

## Writing your own

A loader is one method, so a map-backed one is a lambda — this is the playground's Modules sample,
where the loader is even written as a *script* function, the interface converting like any SAM:

```java
Map<String, String> sources = Map.of(
        "counter.js", "export let count = 0; export function increment() { count++; }",
        "app.js", "import { count, increment } from 'counter.js'; increment(); export { count };");

ModuleLoader inMemory = (specifier, referrer) -> {
    String clean = specifier.startsWith("./") ? specifier.substring(2) : specifier;
    String text = sources.get(clean);
    return text == null ? null : Module.source("memory:" + clean, text);
};
```

The habits that keep a loader honest, all enforced by the engine's own tests
(`ModuleLoaderTest`):

1. **Null only for "not mine".** Missing is null; unreadable is an exception.
2. **Own your referrers.** Store an `origin` on the modules you make; resolve `./x` only when the
   referrer's origin is yours; otherwise null.
3. **Canonical, prefixed names** so no two loaders ever collide in the registry.
4. **No extension magic** unless your domain defines it — say what a name means, and mean it.

The [playground](../guide/playground.md)'s *Nashorn extensions → Module loaders* sample runs all
of this: a script-function loader, a `ResourceModuleLoader` over the sample's own resources, a
Java module, the chain's order, and the unresolvable-specifier error. The engine-side machinery —
how a loaded module becomes a `ModuleRecord`, links and evaluates, and how the values-backed
records answer exports — is on the [module internals page](../internals/modules.md).
