# Connecting with Java

Scripts have full access to the JVM. The front door is the `Java` global object; the `Packages`
hierarchy is the older, slower door kept for compatibility. Everything on this page works in any
script the engine runs — no options required — and all of it disappears under
[`--no-java`](../reference/options.md).

## Accessing Java classes

`Java.type` takes a fully qualified name and returns a *type object* — the thing you construct with
`new`, read static members from, and use with `instanceof`:

```js
var ArrayList = Java.type("java.util.ArrayList");
var anArrayList = new ArrayList();
var anArrayListWithSize = new ArrayList(16);

var intType         = Java.type("int");
var StringArrayType = Java.type("java.lang.String[]");
var int2DArrayType  = Java.type("int[][]");
```

Inner classes use the binary name (`Java.type("java.awt.geom.Arc2D$Float")`) or a property on the
outer type object (`Java.type("java.awt.geom.Arc2D").Float`). Static fields and methods are
properties of the type object, and JavaBean conventions apply everywhere: a `getName()`/`setName()`
pair reads and writes as a plain `.name` property.

A type object is *not* a `java.lang.Class` — it is closer to what a class name means in Java source.
`type.class` gets the `Class` object; `clazz.static` goes the other way. `anArrayList instanceof
ArrayList` works on the type object, as it should.

The `Packages` root (with shortcuts `java`, `javax`, `com`, `org`, `edu`, `javafx`) resolves names
lazily — `java.util.ArrayList` works anywhere — but each step is a runtime lookup; `Java.type` is
the recommended form. For import-style brevity there is `JavaImporter`:

```js
var imports = new JavaImporter(java.util, java.io);
with (imports) {
    var m = new HashMap();      // java.util.HashMap
    var f = new File("x");      // java.io.File
}
```

?> `JavaImporter` only does anything inside a `with` block — and `with` is illegal in strict mode,
so `JavaImporter` is effectively unavailable under `-strict`. Prefer `Java.type` bindings at the top
of the script.

## Java arrays

Created via array type objects, indexed with normal syntax:

```js
var StringArray = Java.type("java.lang.String[]");
var a = new StringArray(5);
a[0] = "scripting is great!";
print(a.length);                       // 5

var javaIntArray = Java.to([1, "13", false], "int[]");
print(javaIntArray[1]);                // 13 — "13" went through ToNumber
print(javaIntArray[2]);                // 0  — so did false

var jsArray = Java.from(new (Java.type("java.io.File"))(".").listFiles());
```

`Java.to` copies a script array (or any iterable) into a Java array or collection, converting each
element with the ECMAScript conversion rules; the default target is `Object[]`. `Java.from` copies
the other way. These are copies, not views — mutating one side does not touch the other.

## Implementing Java interfaces

Three equivalent forms, terse to explicit:

```js
var Timer = Java.type("java.util.Timer");

// 1. a bare function, wherever a single-method interface is expected
new Timer().schedule(function() print("tick"), 1000);

// 2. an object literal implementing the methods
var task = new (Java.type("java.util.TimerTask"))() {
    run: function() { print("tick"); }
};

// 3. the anonymous-class-like syntax
var r = new java.lang.Runnable() {
    run: function() { print("run!"); }
};
new java.lang.Thread(r).start();
```

Form 1 — passing a function where a *SAM type* (single abstract method) is expected — is the one you
will use constantly: script functions convert automatically to `Runnable`, `Comparator`,
`Function`, listeners, anything with one abstract method. The conversion accepts interfaces and
abstract classes with a no-argument constructor, provided the type and its abstract method are
public.

## Extending Java classes

For everything richer than a SAM there is `Java.extend`:

```js
var ArrayList = Java.type("java.util.ArrayList");
var ArrayListExtender = Java.extend(ArrayList);

var noisy = new ArrayListExtender() {
    size: function() { print("size invoked!"); return 0; },
    add:  function(x, y) {
        print(typeof y === "undefined" ? "add(e) invoked!" : "add(i, e) invoked!");
    }
};
noisy.size();
noisy.add(33, 33);
```

One method implementation answers for every overload of that name — inspect the arguments to tell
them apart, as `add` does above. `Java.extend` accepts at most one class plus any number of
interfaces (`Java.extend(JFrame, Runnable, ActionListener)`), and returns a type object you `new`
like any other.

Why is `Java.extend` needed for concrete classes when interfaces take an object literal directly?
Because `new java.lang.Thread({ run: ... })` is ambiguous — it could mean "extend Thread with this
run" or "call the `Thread(Runnable)` constructor". Nashorn chooses the constructor; `Java.extend`
says the other thing explicitly.

Two flavours of adapter:

- **Instance-bound** (all the examples above): each `new` takes its own implementation object, and
  all instances share one generated class. Method dispatch is live — reassigning `obj.run` changes
  behaviour from the next call.
- **Class-bound**: pass the implementation object to `Java.extend` itself —
  `Java.extend(TimerTask, { run: function() ... })` — and get a class whose behaviour is fixed at
  creation, constructible with the superclass's own constructor signatures.

Inside an override, `Java.super(adapter).method(...)` invokes the superclass implementation.

## Overload resolution

Nashorn picks the right overload from the argument types at call time, the way javac would at
compile time. When you must force a particular one, name its signature as a property:

```js
var out = java.lang.System.out;
out["println(java.lang.Object)"]("hello");   // exactly that overload
```

The unqualified name is right in practice almost always; the explicit form exists for the corner
cases.

## How values convert

- A **script object** passed to Java arrives as a `java.util.Map` view (or converts to
  `List`/`Bindings`/a SAM type where the parameter asks for one).
- **Numbers** are JS doubles; they convert to whatever numeric parameter type the method declares,
  by the ECMAScript rules (hence `"13"` → 13 and `false` → 0 above).
- **Strings** need one caveat going the other way: an object that *behaves* like a JS string may be
  a `CharSequence` without being a `java.lang.String` (the engine uses ropes for concatenation —
  see [Strings internals](../internals/strings-and-types.md)). Java signatures taking
  `CharSequence` or `Object` receive it as-is; `String.valueOf(v)` normalises when in doubt.
- `Java.asJSONCompatible(obj)` wraps a script object tree so Java sees JSON-style `Map`s and
  `List`s all the way down.

## The `Java` object, complete

| Function | What it does |
| --- | --- |
| `Java.type(name)` | Class lookup → type object. |
| `Java.typeName(type)` | The name back from a type object. |
| `Java.extend(types..., [impl])` | Adapter class extending a class and/or interfaces. |
| `Java.super(adapter)` | Super-method access inside adapters. |
| `Java.from(javaArrayOrList)` | Java array/list → script array (copy). |
| `Java.to(scriptObj, type)` | Script array/iterable → Java array/collection (copy). |
| `Java.synchronized(fn, monitor)` | A function that synchronizes on `monitor` around each call. |
| `Java.isJavaObject(v)` / `isScriptObject(v)` | Which world a value belongs to. |
| `Java.isType(v)` / `isJavaMethod(v)` / `isJavaFunction(v)` / `isScriptFunction(v)` | Finer classification. |
| `Java.asJSONCompatible(v)` | JSON-style `Map`/`List` view of a script object tree. |

Under the hood all of this — property access on beans, overload selection, SAM conversion, adapter
generation — is the [Dynalink linker machinery](../internals/linking.md), and it is extensible:
[custom linkers](dynalink-linkers.md) can teach the engine new tricks for your own Java types.
