# Script libraries

A *script library* is a bundle of extensions the engine installs into **every global it creates**
before any script runs there: Java values to define as globals, and scripts to evaluate at the top
level. It is how you ship a set of functions and objects — a utility belt, a domain API, a
compatibility layer — as one artifact that every engine picks up, instead of remembering to `eval`
a prelude into each context by hand.

Two things make it more than a convenience. A global in Nashorn is **per `Bindings`**: the default
context has one, every `engine.createBindings()` makes another, and so does `loadWithNewGlobal`
from script. A library is installed into each of them, so its globals are there whichever context a
script runs in. And it reaches the engine **by discovery** — a service provider on the class path or
module path — so dropping the jar in is enough; or **explicitly**, handed to the factory when the
engine is built.

## The interface

```java
package org.monflabs.nashorn.api.scripting;

public interface ScriptLibrary {
    String name();                                  // unique; what --libraries selects by
    default Map<String, Object> globals() { … }     // Java values to define, first
    default List<Script> scripts()        { … }     // scripts to evaluate, in order, after
    record Script(String name, String text) { … }   // of(name, text), ofResource(Class, path), ofUrl(url)
    static ScriptLibrary of(String name, Map<String, Object> globals, Script... scripts);
}
```

In each new global the `globals()` are defined first, then the `scripts()` run in order, so a script
may build on the Java values. Each script runs as a program at the global's top level: its `var` and
function declarations become properties of the global, exactly as if `load`ed. A library that fails —
a script that throws, a resource that is missing — fails the creation of the engine (or of the
global) with an `IllegalStateException` naming the library and the script.

## A worked example

Say you want every script to have `TAU`, a `circumference(r)` function, and a `clock` object backed
by Java. Put the script part in a resource next to a class:

```js
// src/main/resources/com/example/geometry/geometry.js
function circumference(r) { return TAU * r; }
function area(r)          { return TAU / 2 * r * r; }

var shapes = {
    circle: function (r) { return { radius: r, area: area(r), circumference: circumference(r) }; }
};
```

Then describe the library. Either implement the interface:

```java
package com.example.geometry;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;

public class GeometryLibrary implements ScriptLibrary {
    @Override public String name() { return "geometry"; }

    @Override public Map<String, Object> globals() {
        return Map.of("TAU", 2 * Math.PI, "clock", Clock.systemUTC());   // any Java object will do
    }

    @Override public List<Script> scripts() {
        return List.of(Script.ofResource(GeometryLibrary.class, "geometry.js"));
    }
}
```

or let `ScriptLibrary.of` build it from the same parts:

```java
ScriptLibrary geometry = ScriptLibrary.of("geometry",
        Map.of("TAU", 2 * Math.PI, "clock", Clock.systemUTC()),
        Script.ofResource(GeometryLibrary.class, "geometry.js"));
```

A global value can be any Java object — scripts use it through the ordinary Java interop
(`clock.instant()`) — or a `JSObject` when it should behave like a native function or object; the
[custom objects](custom-objects.md) guide covers that.

### Handing it to the engine explicitly

```java
ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(geometry);
engine.eval("print(circumference(1), shapes.circle(2).area, clock.instant())");

Bindings other = engine.createBindings();
engine.eval("print(typeof circumference)", other);       // function - every global has it
```

The factory has three overloads for this: `getScriptEngine(ScriptLibrary...)`,
`getScriptEngine(String[] args, ScriptLibrary...)`, and the full
`getScriptEngine(String[] args, ClassLoader, ClassFilter, List<ScriptLibrary>)`. An explicit library
always applies, whatever `--libraries` says, and replaces a discovered library of the same name —
which is also how an application overrides a library its class path happens to carry.

### Registering it for discovery

For the jar to extend every engine that can see it, register the implementation as a service. On
the module path, in the library's `module-info.java`:

```java
module com.example.geometry {
    requires org.monflabs.nashorn;
    provides org.monflabs.nashorn.api.scripting.ScriptLibrary with com.example.geometry.GeometryLibrary;
}
```

On the class path, a file `META-INF/services/org.monflabs.nashorn.api.scripting.ScriptLibrary`
containing the line `com.example.geometry.GeometryLibrary`. Doing both keeps the jar working either
way, which is what `nashorn-core` itself does for its own services.

Discovery goes through the engine's application class loader — the one `NashornScriptEngineFactory`
was given, or the thread's context class loader — so a library is found wherever the application's
own classes are. A provider needs a public no-argument constructor.

### Choosing which discovered libraries apply

The `--libraries` option, on the `jjs` command line or among the factory arguments, selects among
the *discovered* libraries: `--libraries=all` (the default), `--libraries=none`, or a list of names,
`--libraries=geometry,logging`. Libraries passed explicitly are not subject to it.

```java
new NashornScriptEngineFactory().getScriptEngine("--libraries=none");   // a bare engine
```

## What to keep in mind

- **Every global gets its own evaluation.** A library's script runs once per global, so state it
  keeps in a `var` is per global, not shared across contexts. Shared state belongs in a Java object
  handed out through `globals()`.
- **Order.** Discovered libraries run in discovery order, then the explicit ones in the order given;
  a library may rely on one that runs before it, and on its own globals from its own scripts.
- **Cost.** Installation is part of creating a global, which is on the path of every
  `createBindings()`. The scripts compile once per engine (the code cache keys on the source) and
  run once per global; keep a library's top level to declarations and light setup.
- **Naming.** Names are the library's identity for `--libraries` and for override-by-name, so pick
  something as specific as a package name would be.
- **The shell too.** `jjs` and `org.monflabs.nashorn.tools.Shell` build a `Context` the same way, so a
  discovered library is present there as well — handy for a house REPL, and worth `--libraries=none`
  when you want a clean engine.

The playground's *Nashorn extensions → Script libraries* sample builds a library from a script tab
and a Java value, hands it to a second engine, and runs code against it.
