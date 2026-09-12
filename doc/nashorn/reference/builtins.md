# Built-in globals

Beyond the objects ECMAScript 2019 defines, Nashorn's global carries a small set of its own
functions and properties. This page lists all of them; the Java-access globals (`Java`, `Packages`,
`JavaImporter` and the package roots) have a [page of their own](../guide/connecting-with-java.md).

## Always present

### `print(arg...)` and `println(arg...)`

Write their arguments to standard output, space-separated. `print` appends a newline (it is the same
function as `println`) unless the engine was started with `--print-no-newline`, in which case the
two differ in exactly that.

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

## Present only at the shell prompt

The interactive shell adds `input`/`evalinput` (multi-line input helpers). They belong to the REPL,
not the engine: scripts should not rely on them. See [The shell](shell.md).

## Annex B built-ins

`escape`, `unescape`, `String.prototype.substr` and its thirteen markup siblings,
`Object.prototype.__proto__` and the `__defineGetter__` family, `Date.prototype.getYear`/`setYear`/
`toGMTString`, and `RegExp.prototype.compile` are present because [Annex B](conformance.md) is on by
default. `--annexB=false` removes every one of them.
