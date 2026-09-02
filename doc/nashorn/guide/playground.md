# The playground

The reactor ships a small Swing application whose whole purpose is to let you try the engine:
a library of samples with an editor, a console, and the debugger a click away. It is an example
application rather than a library — built as an executable jar, not published to Maven Central.

```
mvn -pl playground -am package
java -jar playground/target/nashorn-playground-2017.0.0-all.jar
```

(`--dark` or `--light` forces the theme; by default it follows the system.)

## The samples

The tree on the left holds three categories, read straight out of the jar's resources:

* **Getting started** — `print` and `console`, values and types, functions and closures, the
  built-in objects, errors.
* **ECMAScript support** — the ES2015, ES2016 and ES2017 feature sets, one sample per feature,
  plus an Annex B pair that is worth running twice: once as is, once with
  `// @option --annexB=false` as the first line.
* **Nashorn extensions** — `Java.type`, collections, `Java.extend` and `Java.super`,
  `JavaImporter`, streams, `JSAdapter`, `Object.bindProperties`, `load`, the parser API,
  scripting mode, the JSR-223 engine driven from script, script libraries, module loaders, and a
  debugging sample.
* **Standard libraries** — the host functions on the event loop: timers,
  microtasks, Base64, and `fetch` against public APIs (Open-Meteo weather, GitHub).

A sample is simply a folder with a `main.js`; a `README.md` beside it is rendered beneath the
editor and console, sibling files appear as read-only editor tabs — and double as **modules**: the
playground registers a module loader over them, so a `main.js` written as a module imports its
siblings with `./name`, and a leading `// @option -scripting`
line asks for engine options. The **Scratchpad** at the top of the tree is yours: it is kept in
`~/.nashorn-playground/scratch.js` between sessions.

## Running

**Run** (⌘/Ctrl-Enter) evaluates the buffer; with **Auto-run** on, the sample re-runs half a
second after you stop typing. Output goes to the console — `print` and `console.log` in the text
colour, the error stream in red — with the run's outcome and time on the status line.

**Log expression values** (on by default) evaluates the program one top-level statement at a
time. The console sits to the right of the script and, in this mode, mirrors it line by line: what
a statement evaluates to (shown as `// value`) lands beside it, and `print`/`console.log` output
lands beside the line of the *call* — inside a loop, a block or a catch, not merely at the
statement's head — as far as possible, since output that has already run past a line stays where
it is, and a loop that prints nine lines still takes nine rows. Function declarations are hoisted first, as the engine
would.

**Stop** (Esc) ends a runaway script. Every run is compiled with `--debugger`, so the playground
pauses the script at its next statement and terminates it there — a `while (true) {}` does not
survive it, and neither does a script that catches everything. A script blocked inside a Java
call is interrupted as well.

## Debugging in Chrome

**Start the debugger server** serves the [Chrome DevTools Protocol](debugging.md) on the
playground's engine — the status line shows the `ws://…` URL. Open `chrome://inspect` in Chrome
and click **inspect** under *Remote Target*. From there, **Run** behaves as always — a
`debugger;` statement pauses in DevTools with scopes, call stack and console — and the **Debug**
button (enabled while the server runs) runs the sample paused at its first statement, so a script
with no `debugger;` in it can get breakpoints before anything happens. The pause applies to that
run only.
