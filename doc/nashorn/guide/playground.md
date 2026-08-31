# The playground

The reactor ships a small Swing application whose whole purpose is to let you try the engine:
a library of samples with an editor, a console, and the debugger a click away. It is an example
application rather than a library — built as an executable jar, not published to Maven Central.

```
mvn -pl playground -am package
java -jar playground/target/nashorn-playground-20-all.jar
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
  scripting mode, the JSR-223 engine driven from script, and a debugging sample.

A sample is simply a folder with a `main.js`; a `README.md` beside it is rendered beneath the
editor, next to the console, sibling files appear as read-only editor tabs, and a leading `// @option -scripting`
line asks for engine options. The **Scratchpad** at the top of the tree is yours: it is kept in
`~/.nashorn-playground/scratch.js` between sessions.

## Running

**Run** (⌘/Ctrl-Enter) evaluates the buffer; with **Auto-run** on, the sample re-runs half a
second after you stop typing. Output goes to the console — `print` and `console.log` in the text
colour, the error stream in red — with the run's outcome and time on the status line.

**Log expression values** evaluates the program one top-level statement at a time and prints
what each statement evaluated to beside its line, in the style of a REPL transcript. Function
declarations are hoisted first, as the engine would.

**Stop** (Esc) ends a runaway script. Every run is compiled with `--debugger`, so the playground
pauses the script at its next statement and terminates it there — a `while (true) {}` does not
survive it, and neither does a script that catches everything. A script blocked inside a Java
call is interrupted as well.

## Debug in Chrome

The **Debug in Chrome** toggle starts the [Chrome DevTools Protocol server](debugging.md) on the
playground's engine — the status line shows the `ws://…` URL. Open `chrome://inspect` in Chrome,
click **inspect** under *Remote Target*, and run a sample: a `debugger;` statement pauses there
with scopes, call stack and console in DevTools, and **Pause on next run** stops at the first
statement of scripts that have none.
