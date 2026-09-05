# Non-standard functions

The engine's language is **ECMAScript 2018** (plus [Annex B](annex-b.md)). Everything a conforming
ECMAScript host must provide is there; this page catalogues what the engine adds *on top* — the
functions and objects a script can call that are **not** part of the language standard. It is the
architectural map; the [built-in globals reference](../reference/builtins.md) has the per-function
detail, and the Java-interop surface has its own [guide](../guide/connecting-with-java.md).

Three things shape the list:

- Most extras hang off the **global object** (`Global`, in `internal.objects`), installed when the
  global is initialised. They are all `NOT_ENUMERABLE`, so `for…in` over the global never sees them.
- A second tier is added **only in scripting mode** (`-scripting`), by `initScripting`.
- A few are **methods grafted onto standard built-ins** (`Object`, `Error`) rather than globals.

What is deliberately *not* counted here as a "Nashorn function": **Annex B** built-ins
(`escape`, the `String.prototype` markup helpers, `RegExp.$1`, …) — those are in the spec's own
Annex B — and **`console`**, which is a host/web API rather than a language feature (see the end).

## Always-present global functions

Installed on every global, in any mode:

| Function | What it does |
| --- | --- |
| `print(…)` / `echo` | Write arguments to stdout, space-separated (newline unless `--print-no-newline`); `echo` is the same function object. |
| `load(source)` | Evaluate another script in the current global — file path, URL, or `{name, script}` object. |
| `loadWithNewGlobal(source, …)` | Like `load`, but in a fresh global (realm isolation). |
| `exit([code])` / `quit([code])` | `System.exit` with the given code (default 0). |

(`print`/`echo` and the `load` pair are the ones a plain, non-scripting engine exposes beyond the
language; `exit`/`quit` too.) See [built-in globals](../reference/builtins.md#always-present) for the
argument shapes.

## The `Java` interop object

The single most Nashorn-specific object. Its methods bridge the two type systems — 14 of them:

| Method | Purpose |
| --- | --- |
| `Java.type(name)` | Resolve a Java class/array type to a constructor-like type object. |
| `Java.typeName(type)` | The Java name of a type object. |
| `Java.extend(types…)` | Subclass a Java class / implement interfaces from script. |
| `Java.super(obj)` | The `super`-bound view of a `Java.extend` instance. |
| `Java.from(javaArrayOrList)` | Copy a Java array/`List` into a JS array. |
| `Java.to(jsArray, type)` | Convert a JS array to a Java array or collection. |
| `Java.isType(obj)` / `Java.isJavaObject(obj)` | Type predicates for Java values. |
| `Java.isJavaMethod` / `Java.isJavaFunction` | Predicates for Java (bound) methods and functional values. |
| `Java.isScriptObject` / `Java.isScriptFunction` | Predicates for script values seen from Java. |
| `Java.asJSONCompatible(obj)` | A JSON-friendly view of a value. |
| `Java.synchronized(fn, obj)` | A function wrapper that synchronises on `obj`. |

All of it (and the objects below) disappears under `--no-java`. Full treatment in
[Connecting with Java](../guide/connecting-with-java.md).

## Java-access globals

Not functions, but non-standard globals that make Java reachable:

- **`Packages`** and the package roots **`java`, `javax`, `javafx`, `com`, `edu`, `org`** — namespace
  objects you drill through to a class.
- **`JavaImporter`** — a constructor for a scope object that imports Java packages (used with `with`).
- **`JSAdapter`** — a constructor for an object whose property access is intercepted by script traps
  (a pre-`Proxy` Nashorn mechanism).

## Extension methods on standard built-ins

Grafted onto objects the standard already defines:

| Member | Kind | What it adds |
| --- | --- | --- |
| `Object.bindProperties(target, source)` | function | Mirror a source object's (or Java bean's) properties onto a target. |
| `Object.setIndexedPropertiesToExternalArrayData(obj, buf)` | function | Back an object's indexed elements with a `ByteBuffer`. |
| `Error.captureStackTrace(err)` | function | Attach a `stack` to an error, V8-style. |
| `Error.prototype.getStackTrace()` | function | The script stack as an array of frames. |
| `Error.prototype.printStackTrace()` | function | Print the Java-side stack of the error. |
| `Error.prototype` `stack`, `lineNumber`, `columnNumber`, `fileName` | accessors | Where the error was thrown (not functions, but non-standard). |

## Scripting-mode globals (`-scripting`)

`initScripting` adds these only when the engine runs with `-scripting`:

| Global | What it is |
| --- | --- |
| `readLine([prompt])` | Read a line from stdin. |
| `readFully(file)` | Read a whole file into a string. |
| `$OPTIONS` | An object mirroring the engine's option settings. |
| `$ENV` | The process environment (plus `$ENV.PWD`). |
| `$ARG` | A synonym for `arguments`. |
| `$EXEC(cmd[, input])` | Run a command in a separate process; returns its stdout. |
| `$OUT`, `$ERR`, `$EXIT` | The stdout, stderr and exit code of the last `$EXEC`. |

> `$EXEC` is a **function**; the old backquote-exec *syntax* (`` `cmd` ``) is **not** reinstated —
> the backquote is a template literal in this fork. Call `$EXEC("…")` explicitly. It is implemented
> natively (`ScriptingFunctions.exec` over `CommandExecutor`); a non-zero exit throws a `RangeError`.

## Property-lookup hooks

Not provided by the engine but *called* by it when a script defines them — the pre-`Proxy` catch-alls:

- **`__noSuchProperty__(name)`** — invoked when a missing property is read.
- **`__noSuchMethod__(name, …args)`** — invoked when a missing method is called.

## How many

Counting only the genuinely Nashorn-specific **functions**: 4 always-present globals, 14 `Java.*`
methods, 5 extension methods on `Object`/`Error`, and 7 scripting-mode globals (`readLine`,
`readFully`, `$EXEC`, plus `$OPTIONS`/`$ENV`/`$ARG` and the `$OUT`/`$ERR`/`$EXIT` result holders) —
roughly **30**, alongside the interop objects (`Java`, `JavaImporter`, `JSAdapter`, `Packages` and the
six package roots) and the two lookup hooks.

## Adjacent, but not counted

- **Annex B** (on by default, `--annexB` toggles): `escape`/`unescape`, `RegExp.$1…$9`, the
  `String.prototype` HTML-wrapper methods, and the rest — standard, in the spec's Annex B. See
  [Annex B](annex-b.md).
- **`console`** (`console.log`, `warn`, `error`, `info`, `debug`, `trace`, `dir`, `dirxml`, `table`,
  `assert`, …): a host/web API from `NativeConsole`, not ECMAScript and not a Nashorn language
  extension.
- **`Debug`**: an internal diagnostics object, present for engine debugging rather than for scripts.
