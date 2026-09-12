# The shell

Nashorn ships one command-line entry point: **`org.monflabs.nashorn.tools.Shell`**, in
`nashorn-core`. It runs script files, reads a script from standard input, and — given no file —
drops into a small read-eval-print loop.

It accepts every [command-line option](options.md), and `--` separates engine options from script
arguments.

## Running a script

The shell lives in `nashorn-core`, so this works with nothing but the published artifact:

```bash
java -cp nashorn-core-2026.0.0.jar org.monflabs.nashorn.tools.Shell script.js
java -cp nashorn-core-2026.0.0.jar org.monflabs.nashorn.tools.Shell script.js -- arg1 arg2
```

Exit codes: `0` success, `100` command-line error, `101` compilation error, `102` runtime error,
`103` I/O error, `104` internal error.

To debug a script, add `nashorn-debugger` and `--inspect` or `--inspect-brk` — see
[Debugging scripts](../guide/debugging.md):

```bash
java -cp nashorn-core-2026.0.0.jar:nashorn-debugger-2026.0.0.jar org.monflabs.nashorn.tools.Shell --inspect-brk script.js
```

## The prompt

With no script file the shell reads one line at a time from standard input and prints the value of
each:

```text
nashorn> println("Hello, World!")
Hello, World!
nashorn> arguments.join(", ")     // after: Shell -- a b c
a, b, c
nashorn> quit()
```

Two extra globals exist only at the prompt: `input(endMarker, prompt)` reads lines until the end
marker, and `evalinput(endMarker, prompt)` evaluates what it read. Line editing, history and tab
completion are **not** provided — wrap the command in `rlwrap` if you want them.

### The standard libraries

Unlike the embeddable engine — which installs nothing unless the builder's `library(...)` is called —
the shell installs the [standard libraries](../libraries/overview.md) into its engine **by default**,
so `setTimeout`/`clearTimeout`/`setInterval`, `queueMicrotask`, `atob`/`btoa` and `fetch`/`Headers`/
`Request`/`Response` are there at the prompt and in a script it runs. This is a shell-only switch,
not an engine option: pass **`--std-libraries=false`** (or `--no-std-libraries`) for a bare shell with
none of them — the right setting for reproducing the plain engine's environment.

Because those libraries need it, the shell also turns the
[event loop](../libraries/overview.md#the-event-loop) on by default (it is off in a bare engine), so
`Promise`, `async`/`await`, the timers and `fetch` all work; `--no-std-libraries` turns it back off,
and an explicit `--event-loop` (or `--event-loop=false`) on the command line overrides either way.

```text
nashorn> typeof setTimeout          // "function"
```

## Host I/O

Two Nashorn extensions are on the global object of every realm, and are what shell scripts reach for
most: `readFully(path)` returns a file's whole contents as a string, and `readLine(prompt)` reads one
line from standard input.

## The `-fx` launcher

`Shell -fx script.js` launches the script as a JavaFX application — the script body runs in `start()`
with the primary `$STAGE` available — on JDKs that bundle JavaFX. The
[`samples/`](../../../samples/ ':ignore') directory has a dozen `-fx` examples
(`colorfulcircles.js`, `fxml_example.js`, …).

## Shebang scripts

A script file whose first line is `#!/path/to/launcher <options>` is run in *shebang mode*: it is
treated as the only script file, and everything after it on the command line becomes a script
argument. Write a one-line wrapper that execs `java -cp nashorn-core.jar
org.monflabs.nashorn.tools.Shell "$@"` and point the shebang at it.
