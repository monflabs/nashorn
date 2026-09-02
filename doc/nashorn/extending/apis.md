# Extension APIs

Everything a script can do beyond the language comes from Java: the objects and functions an
embedder hands it, the libraries installed into every global, the linkers that decide how a call
site binds to a Java type, the debugger a frontend attaches to. This page is the inventory of the
public API those extensions are built from — what each piece is for, and where it is documented —
followed by the line between what is public and what is not.

The public surface is exactly the packages `module-info.java` exports unconditionally:
`org.monflabs.nashorn.api.scripting`, `org.monflabs.nashorn.api.tree` and
`org.monflabs.nashorn.api.debugger` - plus one hook that is the JDK's rather than the engine's,
Dynalink's `GuardingDynamicLinkerExporter`. Everything under `internal` is the engine's own business.

## Handing scripts values and functions

| API | What it is for | Read |
| --- | --- | --- |
| `engine.put(name, object)`, `Bindings` | The simplest extension: any Java object becomes a global, and scripts use its methods and fields through the interop. | [Connecting with Java](../guide/connecting-with-java.md) |
| **`JSObject`**, **`AbstractJSObject`** | An object or a function *implemented in Java* that a script treats as native: `getMember`/`setMember` for properties, `call`/`newObject` for calls, `isFunction`, `isInstance`, `getDefaultValue` for conversions. The way to write `area(r)`, a constructor or a proxy-like object in Java - and the line between it and the internal `ScriptObject`. | [Objects from Java](java-objects.md), [Custom objects](../guide/custom-objects.md) |
| **`ScriptObjectMirror`** | A script object in Java hands - what `eval` returns and what a Java function receives as an argument: `getMember`, `setMember`, `callMember`, `eval(source)`, `keySet`, `to(Class)`, `isArray`, `isFunction`, and `freeze`/`seal`. The global an engine hands out as its engine scope is one too. | [Using the engine](../guide/using-the-engine.md) |
| **`ScriptUtils`** | The language's abstract operations for the values a script passes - `typeOf`, `toNumber`, `toString`, `toBoolean`, `strictEquals`, `requireObjectCoercible`, `typeError(msg)` and the rest - plus `wrap`/`unwrap` between mirrors and engine objects, `convert(value, type)`, `makeSynchronizedFunction`, `format` (the engine's `sprintf`) and `parse` (the AST as JSON). | [Script values in Java hands](script-libraries.md#script-values-in-java-hands) |
| **`NashornException`** | A script error seen from Java: `getEcmaError()` for the thrown value, `getFileName`/`getLineNumber`/`getColumnNumber`, `getScriptStackString` for the script's own stack. What `ScriptException.getCause()` is, and what `typeError(...)` makes. | [Using the engine](../guide/using-the-engine.md) |
| **`URLReader`** | A script source from a URL that keeps its name, so stack traces and `__FILE__` say where the code came from. | [Using the engine](../guide/using-the-engine.md) |
| **`api.modules`**: `ModuleLoader`, `Module`, `PathModuleLoader`, `ResourceModuleLoader`, `JavaModuleLoader` | Where `import` finds its modules: a chain of loaders on the builder, first answer wins - files under a root, class-path resources, modules whose exports are Java values, or one of your own (`load(specifier, referrer)`, null to pass). | [Module loaders](module-loaders.md) |
| **`EventLoop`** | The realm's event loop, for Java that gives scripts asynchronous behaviour: `queueMicrotask`, `schedule(task, delay)` for a timer, `pending()` for an operation completing on another thread, `post`. Tasks run on the script's thread with the realm bound; `eval` returns when the loop is idle. What the `host` and `fetch` libraries are built on. | [Standard libraries](../libraries/overview.md#the-event-loop) |

## Installing extensions everywhere

| API | What it is for | Read |
| --- | --- | --- |
| **`ScriptLibrary`** | A named bundle of Java globals, scripts and an initializer, installed into *every* global an engine creates - the default context's, each `createBindings()`, a `loadWithNewGlobal`, the shell's. Discovered as a `ServiceLoader` provider or passed to the builder's `library(...)`; `initialize(global)` reaches into the global to extend prototypes from Java. | [Script libraries](script-libraries.md) |
| `ScriptLibrary.Script` | A script of a library: `of(name, text)`, `ofResource(Class, path)`, `ofUrl(url)`. | [Script libraries](script-libraries.md) |
| `--libraries` | Which discovered libraries apply: `all`, `none`, or names; explicit ones always do. | [Options](../reference/options.md) |

## Shaping the engine

| API | What it is for | Read |
| --- | --- | --- |
| **`NashornScriptEngineBuilder`** | Building an engine one choice at a time: options by name or in command-line spelling, the application class loader, a `ClassFilter`, script libraries, which discovered libraries apply. (`NashornScriptEngineFactory`'s overloads for the same are deprecated; its no-argument `getScriptEngine()` stays the `javax.script` entry point.) | [Creating the engine](../guide/engine-setup.md) |
| **`ClassFilter`** | One method, `exposeToScripts(className)`: which Java classes a script may reach through `Java.type` and the package globals. The sandboxing hook. | [Custom objects](../guide/custom-objects.md#classfilter) |
| `-classpath`, `--module-path`, `--add-modules` | Where scripts find Java classes - a class path or module layer of the engine's own, on top of the application's loader. | [Options](../reference/options.md) |
| **`jdk.dynalink.linker.GuardingDynamicLinkerExporter`** | A Dynalink linker of your own, registered as a service, which the engine picks up: how call sites link to *your* Java types - property access on a domain object, calls on a custom callable - ahead of the default bean linking. A JDK API (`jdk.dynalink`), not the engine's; the engine's own `api.linker.NashornLinkerExporter` is how *it* exports its linkers, not a hook. | [Dynalink custom linkers](../guide/dynalink-linkers.md) |

## Tooling hooks

| API | What it is for | Read |
| --- | --- | --- |
| **`api.tree.Parser`** | The parser as a public AST - `CompilationUnitTree`, the `*Tree` interfaces, `SimpleTreeVisitorES6` - for linters, rewriters and analysers that must not depend on internals. | [The parser API](../guide/parser-api.md) |
| **`api.debugger.Debugger`** | Programmatic control of the debugger an engine runs with `--debugger`: breakpoints, pause, step, terminate, frames, scopes, values, and a passive `TraceListener` stream of statements and completion values. `Debugger.of(engine)`. | [Debugging scripts](../guide/debugging.md#the-api) |
| **`api.debugger.DebuggerFrontend`** | A service the engine looks up for `--inspect`: the Chrome DevTools Protocol server is one; a frontend speaking another protocol would be another. | [The debugger](../internals/debugger.md) |

## Script-side extension points

Some of the engine's extensibility is reachable from the script side and needs no Java at all:

- **`Java.extend`** and **`Java.super`** - subclass a Java class or implement an interface from script;
  an object literal supplies the overrides. **`Java.type`**, **`JavaImporter`**, **`Java.to`/`Java.from`**
  cross the boundary the other way.
- **`JSAdapter`**, **`__noSuchProperty__`**, **`__noSuchMethod__`** - an object that answers for
  properties and methods it does not have; ES2015's **`Proxy`** and **`Reflect`** are the standard form.
- **`Object.bindProperties`** - one object's properties as live views of another's, Java beans included:
  `Object.bindProperties(this, Java.type("java.lang.Math"))` is the script-side "import static".
- **`load(...)`** with a path, a URL, `classpath:` or `nashorn:` - the same global; `loadWithNewGlobal`
  for isolation.

All of these are in [Connecting with Java](../guide/connecting-with-java.md),
[Custom objects](../guide/custom-objects.md) and the [built-ins reference](../reference/builtins.md),
and the playground's *Nashorn extensions* category runs one sample per mechanism.

## What is not an extension point

`org.monflabs.nashorn.internal.*` - `Context`, `Global`, `JSType`, `ScriptObject`, `ScriptRuntime` -
is reachable on the class path, and on a module path it is not exported at all (only the shell module
sees it). None of it carries a compatibility promise; `ScriptObject` in particular is for extensions
that ship inside the engine, as the standard libraries do ([why](java-objects.md#why-scriptobject-is-internal)).
The things an extension is tempted to take from it have public homes:

| Instead of | Use |
| --- | --- |
| `JSType.toNumber` and its siblings | `ScriptUtils.toNumber`, `toString`, `toBoolean`, ... |
| `ScriptRuntime.UNDEFINED` | `ScriptUtils.undefined()` and `isUndefined` |
| `ECMAErrors.typeError` / `Global.newTypeError` | `ScriptUtils.typeError(message)`, `rangeError(message)` |
| `Context.eval` into a global | `ScriptLibrary.initialize(global)` with `global.eval(source)`, or `engine.eval(source, bindings)` |
| `Global.put` on a fresh global | `ScriptLibrary.globals()`, or `initialize(global)` and `setMember` |
| `Context.evaluateModule` | the one gap: modules have [no public entry point yet](../guide/modules.md) |

If an extension needs something from `internal` that this table does not cover, that is the shape of
the next public API, not a reason to reach past the boundary.
