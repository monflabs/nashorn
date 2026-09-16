# Non-standard functions

The engine's language is **ECMAScript 2026** (plus [Annex B](annex-b.md)). Everything a conforming
ECMAScript host must provide is there; this page catalogues what the engine adds *on top* — the
functions and objects a script can call that are **not** part of the language standard. It is the
architectural map; the [built-in globals reference](../reference/builtins.md) has the per-function
detail, and the Java-interop surface has its own [guide](../guide/connecting-with-java.md).

Two things shape the list:

- Most extras hang off the **global object** (`Global`, in `internal.objects`). They are all
  `NOT_ENUMERABLE`, so `for…in` over the global never sees them. Since 2026.1.0 most are *not*
  installed when the global is initialised: they come from the
  [nashorn library](../libraries/nashorn.md), and a bare engine has none of them.
- A few are **methods grafted onto standard built-ins** (`Object`, `Error`) rather than globals.

What is deliberately *not* counted here as a "Nashorn function": **Annex B** built-ins
(`escape`, the `String.prototype` markup helpers, `RegExp.$1`, …) — those are in the spec's own
Annex B — and **`console`**, which is a host/web API rather than a language feature (see the end).

## Global functions, from the nashorn library

`NashornLibrary` installs these into every global of an engine that was given it
(`Global.installNashornExtensions`). An engine that was not has none of them, and a script calling
`print` on one gets a `ReferenceError`:

| Function | What it does |
| --- | --- |
| `print(…)` | Write arguments to stdout, space-separated (newline unless `--print-no-newline`). |
| `load(source)` | Evaluate another script in the current global — file path, URL, or `{name, script}` object. |
| `loadWithNewGlobal(source, …)` | Like `load`, but in a fresh global (realm isolation). |
| `readLine([prompt])` | Read a line from stdin. |
| `readFully(file)` | Read a whole file into a string. |

`readLine`/`readFully` were installed only under the removed scripting mode before 2026.1.0, and are
ordinary members of the library now (`IOFunctions` holds the handles).

**`exit([code])` and `quit([code])`** — `System.exit` with the given code — are *not* in the library.
A script embedded in an application has no business ending the JVM, so `Global.addExitBuiltins`
installs them and only the shell does, on any run. `Global.addShellBuiltins` adds `input`/`evalinput`
at the interactive prompt alone.

The scripting-mode globals that went — `echo`, `$OPTIONS`, `$ENV`, `$ARG`, `$EXEC` and the `$OUT`/`$ERR`/`$EXIT` result holders —
are gone along with the mode itself; `samples/exec.js` shows how to run a process over
`ProcessBuilder`. See [built-in globals](../reference/builtins.md#always-present) for the argument
shapes.

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

All of it (and the objects below) comes from the [java library](../libraries/java.md); an engine not given it has none of it. Full treatment in
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

## Property-lookup hooks

Not provided by the engine but *called* by it when a script defines them — the pre-`Proxy` catch-alls:

- **`__noSuchProperty__(name)`** — invoked when a missing property is read.
- **`__noSuchMethod__(name, …args)`** — invoked when a missing method is called.

## How many

Counting only the genuinely Nashorn-specific **functions**: 5 from the nashorn library (`print`,
`load`, `loadWithNewGlobal`, `readLine`, `readFully`), 2 more at the shell prompt (`exit`/`quit`),
14 `Java.*` methods and 5 extension methods on `Object`/`Error` — roughly **25**, alongside the
interop objects (`Java`, `JavaImporter`, `Packages` and the six package roots, all still installed by
default), `JSAdapter` (in the library) and the two lookup hooks.

Only the Java-interop set is on a bare engine now. Everything else above is either a library someone
contributed or a prompt someone is sitting at.

## Adjacent, but not counted

- **Annex B** (on by default, `--annexB` toggles): `escape`/`unescape`, `RegExp.$1…$9`, the
  `String.prototype` HTML-wrapper methods, and the rest — standard, in the spec's Annex B. See
  [Annex B](annex-b.md).
- **`console`** (`console.log`, `warn`, `error`, `info`, `debug`, `trace`, `dir`, `dirxml`, `table`,
  `assert`, …): a host/web API from `NativeConsole`, not ECMAScript and not a Nashorn language
  extension. It exists **only when the engine was built with `--debugger`** (or `--inspect`).
- **`Debug`**: an internal diagnostics object, installed only when the JVM was started with
  `-Dnashorn.debug=true`, for engine debugging rather than for scripts.
- The **[standard libraries](../libraries/overview.md)** (`setTimeout`, `fetch`, `atob`/`btoa`, …):
  host APIs an embedder opts into, shipped in the same artifact but installed by the builder.
