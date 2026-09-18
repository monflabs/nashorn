# The playground

The reactor ships a small Swing application whose whole purpose is to let you try the engine:
a library of samples with an editor, a console, and the debugger a click away. It is an example
application rather than a library — an executable jar attached to each GitHub release, not a
Maven Central artifact.

## Getting it

**A ready-to-run jar is attached to every GitHub release** — nothing to build, and the only
requirement is a JDK 25 or newer:

```
# https://github.com/monflabs/nashorn/releases/latest
java -jar nashorn-playground-2026.0.0-all.jar
```

Download it from the
[latest release](https://github.com/monflabs/nashorn/releases/latest) (about 6 MB, everything
shaded in: the engine, the debugger, the Node modules and the UI), or go straight to
[nashorn-playground-2026.0.0-all.jar](https://github.com/monflabs/nashorn/releases/download/v2026.0.0/nashorn-playground-2026.0.0-all.jar).

Building it yourself works too, and is what to do when you want the playground to run *your*
engine changes:

```
mvn -pl playground -am package
java -jar playground/target/nashorn-playground-2026.0.0-all.jar
```

(`--dark` or `--light` forces the theme; by default it follows the system.)

## The samples

The tree on the left holds five categories, read straight out of the jar's resources:

* **Getting started** — `print` and `console`, values and types, functions and closures, the
  built-in objects, errors.
* **ECMAScript support** — one folder per edition, ES2015 through ES2026, with a sample per
  feature, plus an Annex B pair that is worth running twice: once as is, once with
  `// @option --annexB=false` as the first line.
* **Standard libraries** — grouped by library: **host** (the event-loop functions — timers,
  microtasks, Base64) and **fetch** (against public APIs: Open-Meteo weather, GitHub).
* **Standard Packages** — the experimental Node-compatibility modules resolved by `nashorn-node`:
  `fs`, `os` and `path`.
* **Nashorn extensions** — `Java.type`, collections, `Java.extend` and `Java.super`,
  `JavaImporter`, Java methods as functions, streams, `JSAdapter`, `JSObject` from Java,
  `Object.bindProperties`, `noSuchProperty`/`noSuchMethod`, `load`/`__FILE__`/`__LINE__`,
  the parser API, the JSR-223 engine driven from script, debugging, script libraries and
  module loaders.

A sample is simply a folder with a `main.js`; a `README.md` beside it is rendered beneath the
editor and console, sibling files appear as read-only editor tabs — and double as **modules**: the
playground registers a module loader over them, so a `main.js` written as a module imports its
siblings with `./name`, and a leading `// @option --annexB=false`
line asks for engine options. The **Scratchpad** at the top of the tree is yours: it is kept in
`~/.nashorn-playground/scratch.js` between sessions.

## Running

**Run** (⌘/Ctrl-Enter) evaluates the buffer; with **Auto-run** on, the sample re-runs half a
second after you stop typing. Output goes to the console — `print` and `console.log` in the text
colour, the error stream in red — with the run's outcome and time on the status line.

**Log expression values** (on by default) runs the program once, untouched, and listens to the
engine instead: a [trace listener](debugging.md#tracing-without-pausing) on the debugger API
announces each statement as it is reached and the completion value of each top-level expression
statement. The console sits to the right of the script and, in this mode, mirrors it line by
line: what a statement evaluates to (shown as `// value`) lands beside it, and
`print`/`console.log` output lands beside the line being executed — inside a loop, per
iteration, not merely at the loop's head — as far as possible, since output that has already run
past a line stays where it is, and a loop that prints nine lines still takes nine rows. Because
nothing is rewritten, the mode changes what a debugger sees not at all: one script, real line
numbers, working breakpoints, values still logged.

**Stop** (Esc) ends a runaway script. Every run is compiled with `--debugger`, so the playground
pauses the script at its next statement and terminates it there — a `while (true) {}` does not
survive it, and neither does a script that catches everything. A script blocked inside a Java
call is interrupted as well.

## Debugging in the playground

**Debug** opens the playground's own debugger — a window laid out like Chrome DevTools' Sources
panel: the script with a breakpoint gutter and an execution pointer, a call stack, watches, scopes
and a breakpoint list down the side, and a console beneath. It is a
[Chrome DevTools Protocol](debugging.md) *client*, but there is no server, no port and no socket
underneath it: the engine and the panel run in the same JVM, so they talk over an **in-process
channel** — the protocol's own JSON messages handed straight from one to the other. Nothing can
fail to bind, and nothing is reachable from outside the process. Pressing it cancels whatever debug
session is already running, starts a fresh one, and runs the sample paused at its first statement,
the moment the panel is actually connected.

Set breakpoints by clicking the gutter, step with the toolbar, hover the scopes tree, evaluate in
the console against the selected frame; right-click a breakpoint to give it a condition, and add
watch expressions that re-evaluate at every pause.

The breakpoints are keyed by the script's url, which the playground keeps stable across runs (it
appends a `//# sourceURL` directive), so a breakpoint set once keeps hitting on every **Debug**.
Every session starts from a **clean debugging context**: the playground clears the debugger's script
registry, so the Sources list shows only this run's files rather than accumulating every sample you
have tried. The clear is a `Runtime.executionContextsCleared` over the protocol, so a connected
debugger resets without the connection dropping, and breakpoints survive (they re-resolve as the run
parses). To make this work even for a re-run of the same sample, the playground's engine keeps **no
class cache** — each run recompiles and re-announces its scripts, where a cache hit would otherwise
leave the freshly cleared list empty.

Exactly **one debug session** is ever live. Closing the debugger window ends it; so does pressing
either debugger button again, or selecting another sample. A script left paused at a breakpoint is
terminated as the session goes away, so nothing is left frozen with no client to resume it.

## Debugging in Chrome

**External Debugger** serves the [Chrome DevTools Protocol](debugging.md) on the playground's engine,
on port 9229 — Node's own `--inspect-brk` default — and runs the sample paused at its first
statement, for an external client to attach to. The status line shows the `ws://…` URL with an
**open chrome://inspect** link that launches Chrome on the inspect page (`chrome://` is no OS scheme,
so the playground starts the browser itself); click **inspect** under *Remote Target* there, and the
script is waiting at its first statement with scopes, call stack and console. A `debugger;` statement
pauses in DevTools the same way.

The session ends by itself when the script finishes, the way a real Node inspector's connection
closes when the debugged process exits: the banner and the link go away, and the server is closed.
Pressing either debugger button, or selecting another sample, ends it early.
