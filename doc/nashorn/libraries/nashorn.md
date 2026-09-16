# nashorn: print, load and the rest

Nashorn's own additions to the language, as a library. None of them are ECMAScript; they are what
Nashorn has always put on the global object beyond the specification, and since **2026.0.0** they are
opt-in the way [`fetch`](fetch.md) and the [timers](host.md) are.

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .library(new NashornLibrary())
        .build();
engine.eval("print('hello')");
```

A bare engine has none of them, and a script that calls `print` on one gets a `ReferenceError`. The
[shell](../reference/shell.md) and the [playground](../guide/playground.md) install the library
themselves — a command line without `print` is not a command line.

## What it installs

| Global | What it is |
| --- | --- |
| `print(arg...)` | Arguments to standard output, space-separated, newline-terminated unless `--print-no-newline` |
| `load(source)` | Evaluate a file, a URL or a `{name, script}` object in this realm |
| `loadWithNewGlobal(source, args...)` | The same, in a fresh realm |
| `readLine([prompt])` | One line from standard input |
| `readFully(file)` | A whole text file as a string |
| `JSAdapter` | The dynamic object with `__get__`/`__put__`/`__call__` traps |
| `__FILE__`, `__DIR__`, `__LINE__` | Where the reading script is |

Each is described on the [built-in globals](../reference/builtins.md) page. They are installed into
**every realm** of that engine, like any other script library, so a `createBindings()` made later has
them too.

## What is deliberately not in it

**Java access.** `Java`, `Packages`, `JavaImporter` and the package roots (`java`, `javax`, `com`, …)
are a library of their own — [`java`](java.md) — for the same reasons. They were separated because
the two are useful apart: an embedder may want `print` and `load` with no reach into the JVM, or the
reverse.

**`exit` and `quit`.** They call `System.exit`. A script embedded in an application has no business
ending the JVM, so the shell installs them instead — on any run, a script file as much as the prompt,
since choosing an exit code is what a command line is for.

**`console`.** It arrives with the [debugger](../guide/debugging.md), not with this.

## Why it moved

An embedder hands a script engine to code it does not control, and every global is surface. Before
2026.0.0 every realm carried `load` — which reads files and URLs — and `readFully`, whether or not the
embedder had any use for them, and removing them meant deleting properties after the fact. The engine
now starts with what ECMAScript defines, and everything else is a decision someone made on purpose.

It is a breaking change, and a deliberate one: a script that calls `print` on an engine built before
this release needs one line added to the builder.
