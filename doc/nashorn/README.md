# Nashorn Monflabs

Nashorn is a JavaScript engine for the JVM: it compiles JavaScript to JVM bytecode and links call
sites with `invokedynamic`. This fork implements **ECMAScript 2026**
([ECMA-262, 17th edition](https://262.ecma-international.org/17.0/)) together with its Annex B,
measured continuously against the official `tc39/test262` conformance suite. It requires **JDK 25 or
later** and has **no dependencies at all**.

```xml
<dependency>
  <groupId>org.monflabs.nashorn</groupId>
  <artifactId>nashorn-core</artifactId>
  <version>2026.0.0</version>
</dependency>
```

Everything about the fork carries its own name, so it never collides with the official Nashorn
library: the Java package and module are `org.monflabs.nashorn` rather than `org.openjdk.nashorn`,
so the two jars can sit side by side anywhere — module path included — the engine reports itself as
*OpenJDK-Monflabs*, and it registers with `javax.script` as `nashorn-monflabs` rather than plain
`nashorn`, so a lookup always finds the engine it named.

```java
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class Hello {
    public static void main(String[] args) throws Exception {
        // print comes from the nashorn library; a bare engine is only ECMAScript
        ScriptEngine engine = new NashornScriptEngineBuilder().library(new NashornLibrary()).build();
        engine.eval("print(`Nashorn speaks ES${2026}`);");
    }
}
```

There is no ES5-only mode and nothing to switch on: classes, generators, `async`/`await`,
destructuring, modules, `Proxy`, `SharedArrayBuffer`, class fields, private members, top-level
`await`, iterator helpers and the rest are simply the language — see
[what language you get](guide/getting-started.md#what-language-you-get) for the edition-by-edition
list. The web-compatibility extensions of Annex B are on by default and removable with
`--annexB=false`.

## What it can do

* **The whole language.** ECMAScript 2026 and its Annex B, measured against `tc39/test262`. Two
  documented exclusions: proper tail calls and ECMA-402. See [Conformance](reference/conformance.md).
* **Java interop.** `Java.type`, `Java.extend`/`Java.super`, `JavaImporter`, bean properties and
  overload resolution, script functions as SAM interfaces, arrays and collections both ways —
  [Connecting with Java](guide/connecting-with-java.md).
* **Embedding.** `javax.script` with `Compilable` and `Invocable`, a typed
  [builder](guide/engine-setup.md) for everything else, `ScriptObjectMirror` and `JSObject` at the
  boundary, and one realm per `Bindings`.
* **Asynchrony.** An [event loop](libraries/overview.md#the-event-loop) per realm, off by default;
  generators, `async` functions and async generators suspend on **virtual threads** rather than a
  compiler-built state machine — [Generators and async](internals/generators-async.md).
* **Extending the engine.** [Script libraries](extending/script-libraries.md) install globals and
  prototype extensions into every realm, from Java or JavaScript;
  [module loaders](extending/module-loaders.md) decide where `import` looks.
* **Host libraries.** [Timers, `queueMicrotask`, `atob`/`btoa`](libraries/host.md) and
  [`fetch`](libraries/fetch.md) with `Headers`/`Request`/`Response`.
* **Node modules.** An experimental [resolver](libraries/node.md) for `fs`, `buffer`, `os`, `path`.
* **Debugging.** A [Chrome DevTools Protocol](guide/debugging.md) server: `--inspect`, then attach
  Chrome or VS Code exactly as with Node.
* **An embeddable debugger UI.** A Swing panel laid out like the DevTools Sources view, speaking the
  same protocol over an **in-process channel** — no port, no socket.
* **Sandboxing.** A [`ClassFilter`](guide/custom-objects.md#classfilter) class by class, leaving the [java library](libraries/java.md) out
  to remove the Java bridge, and an engine-private class path or module layer.
* **Tooling.** A public AST — the [parser API](internals/parser-api.md) — for linters and analysers.
* **Performance.** Bytecode and `invokedynamic`, no interpreter tier;
  [optimistic typing](internals/optimistic-typing.md) on by default and a
  [perf gate](internals/performance.md) on every push.
* **A playground.** A Swing sample browser with an editor, a console and the debugger a click away —
  [build the jar and run it](guide/playground.md).
* **No dependencies at all**, and it **coexists with an upstream `org.openjdk.nashorn` jar** on a
  class path and on a module path alike.

Removed in 2026.1.0: **scripting mode and `jjs`** — the `-scripting` option, heredocs, `#` comments,
`${expr}` in double-quoted strings and the `$EXEC`/`$ENV`/`$OPTIONS`/`$ARG` globals. Shell scripting
is a niche `sh`, Node and Python have taken, and ECMAScript covers what remains. `readLine` and
`readFully` survive as ordinary globals.

## Finding your way

**[User's Guide](guide/getting-started.md)** — for embedding and using the engine: adding the
dependency, [creating](guide/engine-setup.md) and [using](guide/using-the-engine.md) engines, calling
script from Java and Java from script, building custom host objects, ES modules, threads and realms.
It ends with [debugging scripts](guide/debugging.md) from Chrome DevTools or VS Code, and with [the
playground](guide/playground.md) — a runnable sample browser (`java -jar
playground/target/nashorn-playground-2026.0.0-all.jar` after `mvn -pl playground -am package`) whose
library walks the language and the extensions one sample at a time.

**[Extensions and Enhancements](extending/apis.md)** — for giving scripts more than the language: the
public APIs an extension is built from, [objects implemented in Java](extending/java-objects.md) with
`JSObject` (and why the engine's own `ScriptObject` is not for that), and
[script libraries](extending/script-libraries.md), which install globals, scripts and prototype
extensions into every global an engine creates, and [module loaders](extending/module-loaders.md),
which decide where `import` finds its modules.

**[Standard Libraries](libraries/overview.md)** — what a script expects from its host beyond the
language, shipped inside the engine: [timers, `queueMicrotask` and Base64](libraries/host.md),
and [`fetch`](libraries/fetch.md), on an event loop that lets `eval` return when the script is idle.
Alongside them, the separate, experimental [Node module resolver](libraries/node.md) answers
`import fs from 'fs'` and its kin.

**[Technical Guide](internals/architecture.md)** — for reading or changing the engine: the compiler
pipeline, optimistic typing, how objects, arrays, strings and call sites really work, generators on
virtual threads, the module system, Annex B's implementation, nasgen, and the public
[parser API](internals/parser-api.md).

**[Reference](reference/options.md)** — lookup material: every command-line option, the built-in
globals beyond ECMAScript, the shell, logging and debugging switches, the conformance numbers, and
the catalogue of [optimizations over OpenJDK Nashorn 15.7](reference/optimizations.md), each with its
estimated gain and its limits.

**[Contributors](project/building.md)** — building the reactor from source, the
[CI workflows](project/workflows.md), [cutting a release](project/releasing.md), and
[working on these pages](project/documentation.md).

## Historical material

The classic Oracle *Java Scripting Programmer's Guide* and the upstream `DEVELOPER_README` are
preserved untouched in [`doc/nashorn-original/`](../nashorn-original/ ':ignore'). They describe the
engine as it was — parts of them (ECMAScript 5.1, the backquote-exec syntax, the `--language` option)
no longer apply to this fork; the pages here supersede them.
