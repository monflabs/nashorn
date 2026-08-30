# Getting started

Nashorn is published to Maven Central as a single, dependency-free artifact:

```xml
<dependency>
    <groupId>org.monflabs.nashorn</groupId>
    <artifactId>nashorn-core</artifactId>
    <version>20</version>
</dependency>
```

```groovy
implementation 'org.monflabs.nashorn:nashorn-core:20'
```

It requires **JDK 25 or later**, at build and at run time. Releases up to 15.7 were published by the
upstream project as `org.openjdk.nashorn:nashorn-core` and target Java 11 — this fork continues that
line under new coordinates, with the Java packages renamed to match
(`org.openjdk.nashorn.*` → `org.monflabs.nashorn.*`).

## Class path or module path

The engine is a JPMS module named `org.monflabs.nashorn` with no dependencies of its own. Both
placements work:

- **Module path** (preferred): the module exports only its two API packages
  (`org.monflabs.nashorn.api.scripting`, `org.monflabs.nashorn.api.tree`), so internals stay sealed
  and the service registration flows through `provides javax.script.ScriptEngineFactory`.
- **Class path**: the same registration is duplicated in `META-INF/services`, so
  `ScriptEngineManager` discovery works there too.

?> Because the module and packages carry this fork's own name, this artifact **can share a class
path or a module path** with an upstream `org.openjdk.nashorn:nashorn-core` jar — different modules,
different packages. Both register with `javax.script`, so when both are present pick by engine name
rather than relying on discovery order.

## Hello, world

The engine registers with `javax.script` under the names `nashorn-monflabs`, `js`, `JavaScript`
and `ECMAScript` (each in both cases). It deliberately does **not** register as plain `nashorn` —
just as the packages are renamed so the jars can coexist, the engine name is the fork's own so a
lookup never resolves to the wrong engine when the official Nashorn library is also present:

```java
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class EvalScript {
    public static void main(final String[] args) throws Exception {
        // create a script engine manager
        final ScriptEngineManager factory = new ScriptEngineManager();
        // create a JavaScript engine
        final ScriptEngine engine = factory.getEngineByName("nashorn-monflabs");
        // evaluate JavaScript code from String
        engine.eval("print('Hello, World')");
    }
}
```

Compile, run, and `Hello, World` appears — the exception handling is elided here; `eval` throws
`javax.script.ScriptException` on script errors.

## What language you get

The engine speaks **ECMAScript 2017**, whole: `let`/`const`, classes, arrow functions, template
literals, destructuring, generators, `async`/`await`, `Proxy`, `Reflect`, `Promise`, typed arrays,
`SharedArrayBuffer` and `Atomics`. There is no ES5 mode and no version switch — `--language` is
accepted for old command lines but `es6` is its only value. Annex B, the web-compatibility annex
(`escape`, `__proto__`, HTML-like comments, block-function hoisting…), is on by default and removed
entirely by the `--annexB=false` [option](../reference/options.md).

Two things older Nashorn documentation promises are gone from this fork: the `$EXEC`/backquote
process extension (the backquote now belongs to template literals — see
[Scripting mode](scripting-mode.md)), and everything to do with the Security Manager, which the JDK
itself removed.

## Where next

- [Creating the engine](engine-setup.md) — factory overloads, options, class filtering.
- [Using the engine](using-the-engine.md) — evaluating, invoking, bindings and scopes.
- [Connecting with Java](connecting-with-java.md) — the `Java` object and everything interop.
- Running scripts from the command line — [jjs and the shell](../reference/jjs.md).
