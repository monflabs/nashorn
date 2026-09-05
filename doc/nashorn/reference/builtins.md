# Built-in globals

Beyond the objects ECMAScript 2018 defines, Nashorn's global carries a small set of its own
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
enumerable, and — unlike most of the extras on this page — available whether or not scripting mode
is on.

### `globalThis` and `arguments`

`globalThis` is the standard self-reference. `arguments` appears as a global property only when the
engine was given script arguments (`jjs script.js -- a b c`).

## Added by `-scripting`

Scripting mode adds the following (and syntax besides — see
[Scripting mode](../guide/scripting-mode.md)):

| Global | What it is |
| --- | --- |
| `readLine([prompt])` | Read one line from standard input, optionally printing a prompt first. |
| `readFully(file)` | Read a whole file into a string. |
| `echo(arg...)` | The same function object as `print`. |
| `$OPTIONS` | An object mirroring the engine's option settings — `$OPTIONS._scripting`, `$OPTIONS._annexB`, `$OPTIONS._timezone`, … |
| `$ENV` | The process environment as an object, plus `$ENV.PWD` from `user.dir`. |
| `$ARG` | A synonym for `arguments`. |
| `$EXEC(cmd[, input])` | Run a command in a separate process; returns its stdout. |
| `$OUT`, `$ERR`, `$EXIT` | The stdout, stderr and exit code of the last `$EXEC`. |

`$EXEC` takes either a command string (`$EXEC("ls -l")`) or an array of argument tokens, plus an
optional stdin string, and returns the command's standard output; it also leaves `$OUT`, `$ERR` and
`$EXIT` on the global. A non-zero exit throws a `RangeError`.

!> Only the **backquote-exec syntax** (`` `cmd` ``) was removed in this fork — ECMAScript claimed the
backquote for template literals. The `$EXEC` function itself is present (in scripting mode), so call
it explicitly rather than through backquotes.

## Present only in the `jjs` REPL

The interactive shell adds `input`/`evalinput` (multi-line input helpers) and the `history` and
`edit` objects. They belong to the REPL, not the engine: scripts should not rely on them. See
[jjs and the shell](jjs.md).

## Annex B built-ins

`escape`, `unescape`, `String.prototype.substr` and its thirteen markup siblings,
`Object.prototype.__proto__` and the `__defineGetter__` family, `Date.prototype.getYear`/`setYear`/
`toGMTString`, and `RegExp.prototype.compile` are present because [Annex B](conformance.md) is on by
default. `--annexB=false` removes every one of them.
