# Built-in globals

Beyond the objects ECMAScript 2026 defines, Nashorn's global carries a small set of its own
functions and properties. This page lists all of them; the Java-access globals (`Java`, `Packages`,
`JavaImporter` and the package roots) have a [page of their own](../guide/connecting-with-java.md).

## Always present

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
`load("nashorn:parser.js")` for the script-side [parser](../guide/parser-api.md).

### `loadWithNewGlobal(source, args...)`

Like `load`, but the script runs in a **fresh global** — new built-ins, new everything — and any
extra arguments become its `arguments`. The return value crosses back as a mirror. This is the
script-side way to get [realm isolation](../guide/concurrency.md).

### `exit([code])` and `quit([code])`

Two names for the same function: exit the JVM via `System.exit`, default code 0.

### `__FILE__`, `__DIR__`, `__LINE__`

The current script's file name, its directory, and the current line number. Not writable and not
enumerable.

### `globalThis` and `arguments`

`globalThis` is the standard self-reference. `arguments` appears as a global property only when the
engine was given script arguments (`Shell script.js -- a b c`).

### `readLine` and `readFully`

Two host I/O extensions, on every realm:

| Global | What it is |
| --- | --- |
| `readLine([prompt])` | Read one line from standard input, optionally printing a prompt first. |
| `readFully(file)` | Read a whole file into a string. A non-file argument is a `TypeError`. |

## Present only in some engines

| Global | When | What it is |
| --- | --- | --- |
| `console` | the engine was built with `--debugger` (or `--inspect`) | The `console` object a debugger front end expects: `log`, `info`, `debug`, `trace`, `warn`, `error`, `dir`, `dirxml`, `table`, `assert`, `count`/`countReset`, `time`/`timeEnd`/`timeLog`, `group`/`groupCollapsed`/`groupEnd` and `clear`. Without the option the engine has no `console` at all. |
| `Debug` | the JVM was started with `-Dnashorn.debug=true` | Engine-internal introspection (`Debug.map`, `Debug.dumpCounters`, …), used by the engine's own tests. Not an API. |

The [standard libraries](../libraries/overview.md) add more — `setTimeout`, `fetch`, `atob`/`btoa` —
but only when an embedder hands them to the builder; a bare engine has none of them.

## Present only at the shell prompt

The interactive shell adds `input`/`evalinput` (multi-line input helpers). They belong to the REPL,
not the engine: scripts should not rely on them. See [The shell](shell.md).

## Annex B built-ins

`escape`, `unescape`, `String.prototype.substr` and its thirteen markup siblings,
`Object.prototype.__proto__` and the `__defineGetter__` family, `Date.prototype.getYear`/`setYear`/
`toGMTString`, and `RegExp.prototype.compile` are present because [Annex B](conformance.md) is on by
default. `--annexB=false` removes every one of them.
