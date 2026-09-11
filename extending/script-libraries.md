# Script libraries

A *script library* is a bundle of extensions the engine installs into **every global it creates**
before any script runs there: Java values to define as globals, and scripts to evaluate at the top
level. It is how you ship a set of functions and objects — a utility belt, a domain API, a
compatibility layer — as one object you hand the engine, instead of remembering to `eval` a prelude
into each context by hand.

What makes it more than a convenience is that a global in Nashorn is **per `Bindings`**: the default
context has one, every `engine.createBindings()` makes another, and so does `loadWithNewGlobal`
from script. A library is installed into each of them, so its globals are there whichever context a
script runs in. A library reaches the engine **only explicitly** — handed to the builder's
`library(...)` when the engine is built. There is no discovery and no option: a bare engine has none,
and the application decides in code which libraries each engine gets.

## The interface

```java
package org.monflabs.nashorn.api.scripting;

public interface ScriptLibrary {
    String name();                                  // unique; its identity, for override-by-name and errors
    default Map<String, Object> globals() { … }     // Java values to define, first
    default List<Script> scripts()        { … }     // scripts to evaluate, in order, after
    default void initialize(JSObject global) { }    // then: the global itself, to reach into
    record Script(String name, String text) { … }   // of(name, text), ofResource(Class, path), ofUrl(url)
    static ScriptLibrary of(String name, Map<String, Object> globals, Script... scripts);
}
```

In each new global the `globals()` are defined first, then the `scripts()` run in order, so a script
may build on the Java values, and then `initialize(global)` is called with the global object itself
— the same `JSObject` an engine hands out as its engine scope — for whatever is easier done from Java
than declared. Each script runs as a program at the global's top level: its `var` and function
declarations become properties of the global, exactly as if `load`ed. A library that fails — a script
that throws, a resource that is missing, an initializer that throws — fails the creation of the engine
(or of the global) with an `IllegalStateException` naming the library and the stage.

## A worked example

Say you want every script to have `TAU`, an `area(r)` function **implemented in Java**, a
`circumference(r)` function written in script, and a `clock` object backed by Java. The script part
goes in a resource next to a class — and it may use the Java globals freely, since those are defined
before it runs:

```js
// src/main/resources/com/example/geometry/geometry.js
function circumference(r) { return TAU * r; }

var shapes = {
    circle: function (r) { return { radius: r, area: area(r), circumference: circumference(r) }; }
};                                            // area is the Java function below
```

The Java function is a `JSObject` — `AbstractJSObject` with `isFunction()` and `call` — which a
script calls like any other function. Write it the way a script function behaves: a script may
call `area("2")`, `area()` or `area(null)`, and a cast to `Number` would answer with a
`ClassCastException` where the language answers with a conversion. `ScriptUtils.toNumber` *is* the
language's ToNumber, as the engine does it — `"2"` → 2, `true` → 1, an object's `valueOf()`
honoured, `undefined` and `"abc"` → NaN, `null` → 0 — so the function coerces exactly as one written
in JavaScript would; a missing argument is `undefined`, hence NaN, and a bad one can be refused with
a script error the caller can catch:

```java
package com.example.geometry;

import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.ScriptUtils;

public class Area extends AbstractJSObject {
    @Override public boolean isFunction() { return true; }

    @Override public Object call(Object thiz, Object... args) {
        double r = ScriptUtils.toNumber(args.length == 0 ? ScriptUtils.undefined() : args[0]);
        if (r < 0) {
            throw ScriptUtils.rangeError("radius must not be negative: " + ScriptUtils.toString(args[0]));
        }
        return Math.PI * r * r;
    }
}
```

Then describe the library. Either implement the interface:

```java
package com.example.geometry;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;

public class GeometryLibrary implements ScriptLibrary {
    @Override public String name() { return "geometry"; }

    @Override public Map<String, Object> globals() {
        Map<String, Object> globals = new LinkedHashMap<>();    // defined in this order
        globals.put("TAU", 2 * Math.PI);                        // a number
        globals.put("area", new Area());                        // a function implemented in Java
        globals.put("clock", Clock.systemUTC());                // any Java object will do
        return globals;
    }

    @Override public List<Script> scripts() {
        return List.of(Script.ofResource(GeometryLibrary.class, "geometry.js"));
    }
}
```

or let `ScriptLibrary.of` build it from the same parts:

```java
ScriptLibrary geometry = ScriptLibrary.of("geometry",
        Map.of("TAU", 2 * Math.PI, "area", new Area(), "clock", Clock.systemUTC()),
        Script.ofResource(GeometryLibrary.class, "geometry.js"));
```

A global value can be any Java object — scripts use it through the ordinary Java interop
(`clock.instant()`) — or a `JSObject` when it should behave like a native function or object, as
`area` does; the [custom objects](../guide/custom-objects.md) guide covers `JSObject` in depth.

### Script values in Java hands

What a script passes to a Java function - or what `initialize` reads from the global - arrives as
the engine's own values: numbers as Java `Number`s, strings as `String` (or, from inside the engine,
a `CharSequence` that is not one), `null` as `null`, `undefined` as its own singleton, and objects as
`ScriptObjectMirror`s. `ScriptUtils` has the language's operations for them, named after the
specification and delegating to the engine's implementation:

| Group | Methods | Notes |
| --- | --- | --- |
| Type tests | `typeOf(v)`, `isUndefined(v)`, `isNullOrUndefined(v)`, `isString(v)`, `isNumber(v)`, `isPrimitive(v)`, `isCallable(v)`, `undefined()` | `typeOf` answers as the operator does; `undefined()` is the value to *return* undefined |
| Conversions | `toNumber(v)`, `toInt32(v)`, `toUint32(v)`, `toUint16(v)`, `toLong(v)`, `toBoolean(v)`, `toString(v)`, `toPrimitive(v[, hint])`, `toObject(v)` | ToNumber, ToInt32, ToUint32, ToUint16, ToBoolean, ToString, ToPrimitive, ToObject - the tables of the specification, `toLong` a saturating truncation for Java's sake |
| Equality | `strictEquals(x, y)`, `looseEquals(x, y)`, `sameValue(x, y)`, `sameValueZero(x, y)` | `===`, `==`, `Object.is`, and what `includes`/`Map`/`Set` use; two mirrors of one object are one object |
| Errors | `requireObjectCoercible(v)`, `typeError(msg)`, `rangeError(msg)` | the check at the head of most built-ins, and script errors for Java to `throw` - they reach the script as ordinary `TypeError`/`RangeError` it can catch |

A mirror converts through its own realm, so a conversion needs nothing bound on the thread. What
does need the caller's realm is *making* something that belongs to one - a script error (a
`TypeError` for a symbol, for `null` where an object is required, or `typeError(...)` itself) or a
wrapper object from `toObject` - and those are meant to be called from where a script called you,
where there always is one; called with no realm bound they say so.

### Extending what is already there

`globals()` defines *new* names. To change objects the global already has — add a method to a
built-in prototype, wrap an existing function — there are two routes, shown on the same
`capitalize` method for strings.

**From a script**, because a library script runs at the global's top level with the whole realm in
reach, it is one line of JavaScript:

```js
// strings.js, listed in scripts()
String.prototype.capitalize = function () {
    return this.length === 0 ? this : this.charAt(0).toUpperCase() + this.slice(1);
};
```

**From Java**, when the method's body is Java code, `initialize` receives the global and the library
navigates to the prototype and installs a `JSObject` function there:

```java
public class StringsLibrary implements ScriptLibrary {
    @Override public String name() { return "strings"; }

    @Override public void initialize(JSObject global) {
        JSObject string    = (JSObject) global.getMember("String");
        JSObject prototype = (JSObject) string.getMember("prototype");
        prototype.setMember("capitalize", new AbstractJSObject() {
            @Override public boolean isFunction() { return true; }

            @Override public Object call(Object thiz, Object... args) {
                String s = String.valueOf(thiz);              // the receiver: the string itself
                return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        });
    }
}
```

Either way, `'nashorn'.capitalize()` is `"Nashorn"` in every global the engine creates — each global
has its own `String.prototype`, and the library is installed into each. The `JSObject` the initializer
gets is a mirror bound to that global's realm: `getMember`/`setMember`/`callMember` and `eval(String)`
all act there, and a Java function installed through it is called with the script-side receiver as
`thiz`. Because libraries go one after the other, an initializer sees the libraries before it
complete, and its own scripts already run — so it can also wrap or decorate functions those scripts
declared.

### Handing it to the engine explicitly

```java
ScriptEngine engine = new NashornScriptEngineBuilder().library(geometry).build();
engine.eval("print(circumference(1), area(2), shapes.circle(2).area, clock.instant())");

Bindings other = engine.createBindings();
engine.eval("print(typeof circumference)", other);       // function - every global has it
```

`library(...)` takes any number, in the order they apply. A library reaches the engine only by being
handed in like this — there is no discovery and no option (the deprecated factory overloads aside), so
a bare engine has none. A later library replaces an earlier one of the same name.

### No discovery — a library is just a class you pass

There is no service registration, no `META-INF/services`, and no option. A library is an ordinary
object you hand to the builder's `library(...)`; the application decides, in code, which libraries
each engine gets. Nothing is installed automatically — not even the engine's own `host` and `fetch`
(see [the standard libraries](../libraries/overview.md)), which you add the same way:

```java
new NashornScriptEngineBuilder().build();                                          // a bare engine
new NashornScriptEngineBuilder().library(new HostLibrary(), geometry).build();     // just these two
```

## What to keep in mind

- **Every global gets its own evaluation.** A library's script runs once per global, so state it
  keeps in a `var` is per global, not shared across contexts. Shared state belongs in a Java object
  handed out through `globals()`.
- **Order.** Libraries run in the order given to `library(...)`,
  each fully — globals, scripts, `initialize` — before the next; a library may rely on one that runs
  before it, and on its own globals from its own scripts. A library that must see all the others
  goes last.
- **Cost.** Installation is part of creating a global, which is on the path of every
  `createBindings()`. The scripts compile once per engine (the code cache keys on the source) and
  run once per global; keep a library's top level to declarations and light setup.
- **Naming.** A name is the library's identity for override-by-name, so pick something as specific
  as a package name would be.
- **The shell.** The embeddable engine takes only what you hand it, but `jjs` is a tool: it installs
  the standard `host` and `fetch` libraries into its own engine by default (`--std-libraries=false`
  for a bare shell). Your own libraries are not among them - `jjs` has no way to load an arbitrary
  library from the command line.

The [playground](../guide/playground.md)'s *Nashorn extensions → Script libraries* sample builds a library from a script tab
and a Java value, hands it to a second engine, and runs code against it.
