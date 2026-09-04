# Nashorn

Nashorn is a JavaScript engine for the JVM: it compiles JavaScript to JVM bytecode and links call
sites with `invokedynamic`. This fork — published as **`org.monflabs.nashorn:nashorn-core`**,
version 2017.0.0, reporting itself as *OpenJDK-Monflabs* — implements **ECMAScript 2017** (ECMA-262, 8th
edition) together with its Annex B, measured continuously against the official `tc39/test262`
conformance suite. It requires **JDK 25 or later** and has **no dependencies at all**. To avoid colliding with the
official Nashorn library, everything about it carries the fork's own name: the Java package and
module are `org.monflabs.nashorn` rather than `org.openjdk.nashorn`, so the two jars can sit side
by side anywhere - module path included - and the script engine registers as `nashorn-monflabs`
rather than `nashorn`, so a `javax.script` lookup always finds the engine it named.

```java
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class Hello {
    public static void main(String[] args) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn-monflabs");
        engine.eval("print(`Nashorn speaks ${'ES' + 2017}`);");
    }
}
```

There is no ES5-only mode and nothing to switch on: classes, generators, `async`/`await`,
destructuring, modules, `Proxy`, `SharedArrayBuffer` and the rest are simply the language. The
web-compatibility extensions of Annex B are on by default and removable with `--annexB=false`.

## Finding your way

**[User's Guide](guide/getting-started.md)** — for embedding and using the engine: adding the
dependency, creating and configuring engines, calling script from Java and Java from script,
building custom host objects, ES modules, scripting mode, the parser API. It ends with [debugging
scripts](guide/debugging.md) from Chrome DevTools or VS Code, and with [the
playground](guide/playground.md) — a runnable sample browser (`java -jar
playground/target/nashorn-playground-2017.0.0-all.jar` after `mvn -pl playground -am package`) whose
library walks the language and the extensions one sample at a time.

**[Extending the engine](extending/apis.md)** — for giving scripts more than the language: the
public APIs an extension is built from, [objects implemented in Java](extending/java-objects.md) with
`JSObject` (and why the engine's own `ScriptObject` is not for that), and
[script libraries](extending/script-libraries.md), which install globals, scripts and prototype
extensions into every global an engine creates, and [module loaders](extending/module-loaders.md),
which decide where `import` finds its modules.

**[Standard Libraries](libraries/overview.md)** — what a script expects from its host beyond the
language, shipped inside the engine: [timers, `queueMicrotask` and Base64](libraries/host.md),
and [`fetch`](libraries/fetch.md), on an event loop that lets `eval` return when the script is idle.

**[Technical Guide](internals/architecture.md)** — for reading or changing the engine: the compiler
pipeline, optimistic typing, how objects, arrays, strings and call sites really work, generators on
virtual threads, the module system, Annex B's implementation, nasgen.

**[Reference](reference/options.md)** — lookup material: every command-line option, the built-in
globals beyond ECMAScript, `jjs`, logging and debugging switches, and the conformance numbers.

## Viewing these docs

The pages are plain markdown and read fine on GitHub. For the rendered site with search — everything
is vendored, so no network is needed — run the helper from the repository root:

```bash
./serve-docs.sh            # serves this site at http://localhost:8000/  (pass a port to change it)
```

Then open <http://localhost:8000/>. It is just a static file server: docsify fetches the markdown at
runtime, so opening `index.html` as a `file://` URL will not work. The equivalent by hand is:

```bash
python3 -m http.server 8000 --directory doc/nashorn
```

If you have Node, the docsify CLI is nicer for editing — it serves on
<http://localhost:3000/> **with livereload**, so the browser refreshes as you save (no more stale
sidebar or hard reloads). From the repository root:

```bash
docsify serve doc/nashorn          # or, with nothing installed: npx docsify-cli serve doc/nashorn
```

## Historical material

The classic Oracle *Java Scripting Programmer's Guide* and the upstream `DEVELOPER_README` are
preserved untouched in [`doc/nashorn-original/`](../nashorn-original/ ':ignore'). They describe the
engine as it was — parts of them (ECMAScript 5.1, the backquote-exec syntax, the `--language` option)
no longer apply to this fork; the pages here supersede them.
