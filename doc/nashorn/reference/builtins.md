# Built-in globals

Beyond the objects ECMAScript 2026 defines, Nashorn has a small set of globals of its own. This page
lists all of them.

!> **They are a library, not a default, since 2026.1.0.** A bare engine has `print`, `load`,
`JSAdapter` and the rest *no more* than it has `fetch` or `setTimeout`: an embedder that wants them
contributes [`NashornLibrary`](../libraries/nashorn.md) to the builder. The shell and the playground
install it for you. Java access is the exception — `Java`, `Packages`, `JavaImporter` and the package
roots are a library of their own, [`java`](../libraries/java.md), described on a
[page of its own](../guide/connecting-with-java.md).

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .library(new NashornLibrary())
        .build();
engine.eval("print('hello')");
```

## In the nashorn library

### `print(arg...)`

Writes its arguments to standard output, space-separated, followed by a newline — unless the engine
was started with `--print-no-newline`, in which case it writes no trailing newline. There is no
separate `println` global.

### `load(source)`

Evaluates another script in the caller's global scope and returns its completion value. The argument
can be:

- a file path — `load("util.js")`;
- a URL string — `load("https://example.com/lib.js")`;
- an object with `name` and `script` properties — `load({ name: "generated", script: "1 + 1" })` —
  which is how you evaluate constructed source while keeping a useful name in stack traces.

Called *on* another object — `someScope.load(...)` via `call`/`apply` — it evaluates the script with
that object on the scope chain, like `with (someScope) eval(src)`. A handful of engine-supplied
scripts are addressable with the `nashorn:` pseudo-scheme, notably
`load("nashorn:mozilla_compat.js")` for the old Rhino compatibility layer and
`load("nashorn:parser.js")` for the script-side [parser](../internals/parser-api.md).

### `loadWithNewGlobal(source, args...)`

Like `load`, but the script runs in a **fresh global** — new built-ins, new everything — and any
extra arguments become its `arguments`. The return value crosses back as a mirror. This is the
script-side way to get [realm isolation](../guide/concurrency.md).

### `__FILE__`, `__DIR__`, `__LINE__`

The current script's file name, its directory, and the current line number. Not writable and not
enumerable.

### `JSAdapter`

A dynamic object whose property access is handled by script: `__get__`, `__put__`, `__call__`,
`__has__`, `__delete__` and `__getIds__` traps on the object passed to the constructor. It predates
`Proxy` by years and `Proxy` is the standard answer today; `JSAdapter` remains for the scripts
written against it. See [Custom objects](../guide/custom-objects.md).

### `readLine` and `readFully`

Two host I/O extensions, on every realm:

| Global | What it is |
| --- | --- |
| `readLine([prompt])` | Read one line from standard input, optionally printing a prompt first. |
| `readFully(file)` | Read a whole file into a string. A non-file argument is a `TypeError`. |

## Always present

### `globalThis` and `arguments`

These two are **not** part of the library: `globalThis` is the standard self-reference and is always
there, and `arguments` appears as a global property only when the engine was given script arguments
(`Shell script.js -- a b c`).

## Present only in some engines

| Global | When | What it is |
| --- | --- | --- |
| `console` | the engine was built with `--debugger` (or `--inspect`) | The `console` object a debugger front end expects: `log`, `info`, `debug`, `trace`, `warn`, `error`, `dir`, `dirxml`, `table`, `assert`, `count`/`countReset`, `time`/`timeEnd`/`timeLog`, `group`/`groupCollapsed`/`groupEnd` and `clear`. Without the option the engine has no `console` at all. |
| `Debug` | the JVM was started with `-Dnashorn.debug=true` | Engine-internal introspection (`Debug.map`, `Debug.dumpCounters`, …), used by the engine's own tests. Not an API. |

The [standard libraries](../libraries/overview.md) add more — `setTimeout`, `fetch`, `atob`/`btoa` —
but only when an embedder hands them to the builder; a bare engine has none of them.

## Present only in the shell

**`exit([code])` and `quit([code])`** — `System.exit` with the given code — are installed by the
shell on **any** run, a script file as much as the prompt, because choosing an exit code is what a
command line does. They call `System.exit`, which is no business of a script embedded in an
application, so they are in no library.

**`input`/`evalinput`** (multi-line input helpers, the second evaluating what it read) are installed
only at the **interactive prompt**. Scripts should not rely on them. See [The shell](shell.md).

## Annex B built-ins

`escape`, `unescape`, `String.prototype.substr` and its thirteen markup siblings,
`Object.prototype.__proto__` and the `__defineGetter__` family, `Date.prototype.getYear`/`setYear`/
`toGMTString`, and `RegExp.prototype.compile` are present because [Annex B](conformance.md) is on by
default. `--annexB=false` removes every one of them.
