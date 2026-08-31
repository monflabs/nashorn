# Nashorn

Nashorn is a JavaScript engine for the JVM: it compiles JavaScript to JVM bytecode and links call
sites with `invokedynamic`. This fork — published as **`org.monflabs.nashorn:nashorn-core`**,
version 20, reporting itself as *OpenJDK-Monflabs* — implements **ECMAScript 2017** (ECMA-262, 8th
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
building custom host objects, ES modules, scripting mode, the parser API. It ends with [debugging scripts](guide/debugging.md) from Chrome DevTools or VS Code.

**[Technical Guide](internals/architecture.md)** — for reading or changing the engine: the compiler
pipeline, optimistic typing, how objects, arrays, strings and call sites really work, generators on
virtual threads, the module system, Annex B's implementation, nasgen.

**[Reference](reference/options.md)** — lookup material: every command-line option, the built-in
globals beyond ECMAScript, `jjs`, logging and debugging switches, and the conformance numbers.

## Viewing these docs

The pages are plain markdown and read fine on GitHub. For the rendered site with search, serve the
repository root and open the docs directory — everything is vendored, so no network is needed:

```bash
python3 -m http.server 8000        # from the repository root
# then open http://localhost:8000/doc/nashorn/
```

## Historical material

The classic Oracle *Java Scripting Programmer's Guide* and the upstream `DEVELOPER_README` are
preserved untouched in [`doc/nashorn-original/`](../nashorn-original/ ':ignore'). They describe the
engine as it was — parts of them (ECMAScript 5.1, `$EXEC`, `--language=es5`) no longer apply to this
fork; the pages here supersede them.
