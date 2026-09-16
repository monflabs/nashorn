# java: Java access

A script's reach into the JVM by name, as a library. Since **2026.0.0** an engine has it only if it
was given it:

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .library(new JavaLibrary())
        .build();
engine.eval("var list = new (Java.type('java.util.ArrayList'))()");
```

## What it installs

| Global | What it is |
| --- | --- |
| `Java` | `Java.type`, `extend`, `super`, `from`, `to`, `isJavaObject`, `isType`, … |
| `JavaImporter` | A scope object of Java packages, for `with` |
| `Packages` | The root of the package namespace |
| `java`, `javax`, `javafx`, `com`, `org`, `edu` | Package roots, so `java.util.ArrayList` resolves as an expression |

The [Connecting with Java](../guide/connecting-with-java.md) guide is the long form: type resolution,
overload selection, `Java.extend`, arrays and collections, functional interfaces.

## This replaced `--no-java`

`--no-java` (and the builder's `java(boolean)`) is **gone**. It took the same properties away again
after nasgen had written them into every global's map, which is the wrong way round: an engine now
has no reach into Java unless an embedder said so, and saying nothing is the safe answer rather than
the dangerous one.

The repair for a `--no-java` engine is to do nothing — that is what an engine is now. The repair for
an engine that relied on the default is one line:

```java
.library(new JavaLibrary())
```

## It is not a security boundary by itself

Leaving the library out removes the ways a script can *name* a Java class. It does not make the
engine a sandbox:

- A Java object the embedder puts into the bindings **is still a Java object**, and its methods are
  reachable from script through the interop linker.
- A `ScriptObjectMirror` handed back to Java is unaffected.

[`ClassFilter`](../guide/connecting-with-java.md) is what decides, class by class, what `Java.type`
may resolve — and it still applies when this library is installed. The two compose: the library
decides *whether* a script can name Java classes at all, the filter decides *which*.

## Who installs it

The [shell](../reference/shell.md) and the [playground](../guide/playground.md) install it, along
with the [nashorn library](nashorn.md) — both are environments where a person is driving and expects
the whole engine. A bare embedded engine has neither.
