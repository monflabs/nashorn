# Objects from Java: JSObject, and why ScriptObject is internal

Everything a script sees is an object, and two Java classes can be one. `ScriptObject` is what the
engine's own objects are — every literal, array, function and built-in — and it is **internal**.
`JSObject` is the **public** interface a Java object implements to take part in script as if it
were one of them. This page says why the line is where it is, what a `JSObject` can and cannot do,
and how to build extensions with it — the examples below are run by the test suite (`JavaObjectsGuideTest`), so what this page says a script sees is what the engine does.

## Why ScriptObject is internal

`ScriptObject` is not an interface a class *implements*; it is the engine's object model, and a
subclass inherits all of its machinery: a `PropertyMap` (the hidden class that maps property names
to slots), a prototype pointer, the field/spill storage the compiler reads directly, and the
`invokedynamic` linking that caches a property's location per shape and invalidates it through
switch points when a prototype changes ([how it works](../internals/objects.md)). The built-ins are
`@ScriptClass`-annotated `ScriptObject`s that the **nasgen** build tool rewrites into constructor and
prototype objects with fixed maps — classes an IDE compiles alone do not even run.

That is the reason it cannot be a public extension point:

- **It is the engine's contract with itself.** The layout of a `ScriptObject`, the meaning of a map,
  the ordering rules between handlers — all of it changes when the engine changes, with no
  compatibility promise. It is exported only to the shell module.
- **It needs the build.** A `ScriptObject` class is finished by nasgen at `process-classes`, from
  annotations that are themselves internal, and instantiated by `Global` per realm by naming
  convention. None of that exists outside the engine's own build.
- **Every realm holds its prototypes.** A built-in's `prototype` is one object per `Global`, created
  and wired by `Global`; a class from outside has nowhere to hang one.

So the rule is simple: **`ScriptObject` is for extensions that ship inside the engine** — the
[standard libraries](../libraries/overview.md) are the example: `Headers`, `Request` and `Response`
are `@ScriptClass` built-ins precisely because they must be indistinguishable from `Map` or
`Promise`, and they are built, versioned and tested with the engine. Anything else — an application
embedding the engine, a library on the class path, a `ScriptLibrary` of your own — extends the
platform with `JSObject`.

## JSObject and ScriptObject side by side

| | `JSObject` (public) | `ScriptObject` (internal) |
| --- | --- | --- |
| Where | `org.monflabs.nashorn.api.scripting`; implement it, or extend `AbstractJSObject` | `internal.runtime`, exported to the shell only; a `@ScriptClass` finished by nasgen |
| Who | any Java code: embedders, libraries, `ScriptLibrary` providers | the engine and its standard libraries |
| Property access | your `getMember`/`setMember`/`hasMember`/`removeMember`, called on every access | a slot in a hidden-class shape, linked once per call site |
| Prototype chain | **none** — nothing is inherited, `Object.getPrototypeOf` is `null` | a real `[[Prototype]]`; methods on the prototype, `instanceof` through the chain |
| `Object.prototype` / `Function.prototype` methods | not available on the object (`hasOwnProperty`, `call`, `bind`…) | inherited as usual |
| Accessor properties, symbol-keyed members | not expressible (a read-only property is `setMember` ignoring or throwing) | `@Getter`/`@Setter`, `@@iterator`, `@@toStringTag` |
| `typeof`, `instanceof`, calls, `new` | `"function"` if `isFunction()`, `isInstance` decides `instanceof`, `call`, `newObject` | as the language says |
| Conversions | `getDefaultValue(hint)` for `String(o)`, `+o`, `` `${o}` `` | `toString`/`valueOf` on the prototype |
| Enumeration | `keySet()` for `Object.keys` and `for-in` | the map's enumerable keys |
| Speed | a Java call per access, no inline caching of the property itself | inline-cached property access |
| Compatibility | public API, kept stable | none |

The middle rows are the ones that matter in practice: a `JSObject` is an *opaque* object to the
engine — everything it has, it answers for itself, and nothing else is looked up. For a value
object, a function, a service, a proxy over a Java resource, that is exactly right. For something
that has to *be a class* in the ECMAScript sense — prototype methods a script can patch, read-only
accessors, iteration protocol — it is not, and that is the boundary where the standard libraries
went to `ScriptObject`.

## What a script hands you, and what you hand back

A `JSObject` method receives what a script passes: numbers as `Integer`/`Double`, strings as
`String`, `null` as `null`, `undefined` as its own value, script objects as `ScriptObjectMirror`s.
`ScriptUtils` has the language's operations on them — `toNumber`, `toString`, `toBoolean`,
`typeOf`, `isUndefined`, the equalities, and `typeError`/`rangeError`/`error` to throw a script
error a caller can catch ([the full table](script-libraries.md#script-values-in-java-hands)). Two
habits keep a `JSObject` honest:

- **Return `ScriptUtils.undefined()` for a property you do not have**, not `null` — `null` is the
  script value `null`.
- **Coerce, do not cast.** `((Number) args[0]).doubleValue()` is a `ClassCastException` for
  `f("2")`; `ScriptUtils.toNumber(args[0])` is what a script function would do.

## Examples

All of these are handed to scripts through a [script library](script-libraries.md) —
`ScriptLibrary.of("examples", Map.of("area", new Area(), …))` — or with `engine.put(name, object)`
for one engine; the mechanism does not change the objects.

### A value object

Data properties, a computed one, a write that validates, and conversions:

```java
final class Point extends AbstractJSObject {
    double x, y;
    Point(double x, double y) { this.x = x; this.y = y; }

    @Override public Object getMember(String name) {
        switch (name) {
            case "x": return x;
            case "y": return y;
            case "length": return Math.hypot(x, y);          // computed on every read
            default: return ScriptUtils.undefined();          // not null: null is a script value
        }
    }
    @Override public void setMember(String name, Object value) {
        switch (name) {
            case "x": x = ScriptUtils.toNumber(value); break;
            case "y": y = ScriptUtils.toNumber(value); break;
            default: throw ScriptUtils.typeError("Point has no property " + name);
        }
    }
    @Override public boolean hasMember(String name) { return Set.of("x", "y", "length").contains(name); }
    @Override public Set<String> keySet() { return new LinkedHashSet<>(List.of("x", "y")); }
    @Override public String getClassName() { return "Point"; }
    @Override public Object getDefaultValue(Class<?> hint) {         // String(p), p + "", +p
        return hint == Number.class ? Math.hypot(x, y) : "Point(" + x + ", " + y + ")";
    }
}
```

```js
p.x + ',' + p.y + ',' + p.length        // "3,4,5"
p.x = 6; p.length                        // 7.21…
'x' in p, 'z' in p, Object.keys(p)       // true, false, ["x", "y"]
String(p), +p                            // "Point(6.0, 4.0)", 7.21…
p.z = 1                                  // TypeError: Point has no property z
```

### A function

`isFunction()` makes it callable; `call` gets the receiver and the arguments:

```java
final class Area extends AbstractJSObject {
    @Override public boolean isFunction() { return true; }

    @Override public Object call(Object thiz, Object... args) {
        double r = ScriptUtils.toNumber(args.length == 0 ? ScriptUtils.undefined() : args[0]);
        if (r < 0) throw ScriptUtils.rangeError("radius must not be negative: " + ScriptUtils.toString(args[0]));
        return Math.PI * r * r;
    }
}
```

```js
area(2), area('1'), isNaN(area())        // 12.56…, 3.14…, true - coerced as the language would
area(-1)                                 // RangeError: radius must not be negative: -1
```

### A class: a constructor with instanceof

A `JSObject` has no prototype, so a "class" is two things: a constructor object that answers `new`
(`newObject`) and `instanceof` (`isInstance`), and instances that hand out **shared** method objects
— one function object per method, created once, checking its receiver:

```java
final class Counter extends AbstractJSObject {
    static final JSObject INCREMENT = new AbstractJSObject() {
        @Override public boolean isFunction() { return true; }
        @Override public Object call(Object thiz, Object... args) {
            if (!(thiz instanceof Counter self)) throw ScriptUtils.typeError("increment called on a non-Counter");
            self.count += args.length == 0 ? 1 : ScriptUtils.toInt32(args[0]);
            return self.count;
        }
    };
    int count;

    @Override public Object getMember(String name) {
        switch (name) {
            case "count": return count;
            case "increment": return INCREMENT;              // the same object for every instance
            default: return ScriptUtils.undefined();
        }
    }
    @Override public String getClassName() { return "Counter"; }

    /** What the script sees as the global Counter. */
    static final JSObject CONSTRUCTOR = new AbstractJSObject() {
        @Override public boolean isFunction() { return true; }
        @Override public Object newObject(Object... args) {
            Counter counter = new Counter();
            counter.count = args.length == 0 ? 0 : ScriptUtils.toInt32(args[0]);
            return counter;
        }
        @Override public Object call(Object thiz, Object... args) { throw ScriptUtils.typeError("Counter requires 'new'"); }
        @Override public boolean isInstance(Object instance) { return instance instanceof Counter; }
        @Override public Object getMember(String name) { return "name".equals(name) ? "Counter" : super.getMember(name); }
    };
}
```

```js
var c = new Counter(40); c.increment(); c.increment(1); c.count    // 42
c instanceof Counter, ({}) instanceof Counter                       // true, false
Counter.name, typeof Counter                                        // "Counter", "function"
Counter()                                                           // TypeError: Counter requires 'new'
```

What this does *not* give is the rest of a class: `Counter.prototype` is `null`, `c.increment.call`
is not a function, `c.hasOwnProperty` does not exist, and `count` cannot be made read-only in the
descriptor sense. If a script needs those, the object has to be a real one — which, for code
outside the engine, means creating it *in script* (a class in a library script) over a Java core.

### A catch-all

Because `getMember` sees every name, an object can answer for names it never declared — a
configuration, a registry, a remote service:

```java
final class Config extends AbstractJSObject {
    final Map<String, String> values;
    Config(Map<String, String> values) { this.values = values; }
    @Override public Object getMember(String name) { return values.getOrDefault(name, "<" + name + " is not set>"); }
    @Override public boolean hasMember(String name) { return values.containsKey(name); }
    @Override public Set<String> keySet() { return values.keySet(); }
}
```

```js
config.host + ':' + config.port          // "example.org:8080"
config.timeout                           // "<timeout is not set>"
Object.keys(config)                      // ["host", "port"]
```

### An array-like

`isArray()`, `hasSlot`/`getSlot`/`setSlot` and a `length` member make indexing work:

```java
final class Bytes extends AbstractJSObject {
    final byte[] bytes;
    Bytes(byte[] bytes) { this.bytes = bytes; }
    @Override public boolean isArray() { return true; }
    @Override public boolean hasSlot(int index) { return index >= 0 && index < bytes.length; }
    @Override public Object getSlot(int index) { return hasSlot(index) ? bytes[index] & 0xFF : ScriptUtils.undefined(); }
    @Override public void setSlot(int index, Object value) { if (hasSlot(index)) bytes[index] = (byte) ScriptUtils.toInt32(value); }
    @Override public Object getMember(String name) { return "length".equals(name) ? bytes.length : ScriptUtils.undefined(); }
}
```

```js
bytes.length, bytes[2], Array.isArray(bytes)     // 3, 255, true
bytes[0] = 9; bytes[0]; typeof bytes[5]          // 9, "undefined"
```

Indexing and `length` are what you get; the `Array.prototype` generics (`join.call(bytes)`,
`map.call(bytes)`) do not see a `JSObject` as array-like, so give a script a real array
(`Java.from`, or a JS array built in a library script) when it needs those.

### Asynchronous results

A `JSObject` that starts work on another thread returns a promise and settles it on the script's
thread through the [event loop](../libraries/overview.md#the-event-loop) — `EventLoop.current().pending()`
from where the script called you, `complete(...)` from anywhere. The promise itself is made with the
realm's own `Promise` constructor, reached from a library's `initialize(global)`; the fetch library's
first, `JSObject`-based version did exactly this and is preserved in the repository's history
(`8368173`) as a worked example.

## Choosing

| You want | Use |
| --- | --- |
| A Java service or bean scripts can call | hand the object over as it is; the interop does the rest |
| An object that *behaves* like a script value — a function, computed or validated properties, a catch-all, a constructor | `JSObject` / `AbstractJSObject` |
| Those in every global, plus prototype extensions from Java | a [`ScriptLibrary`](script-libraries.md) with `globals()` and `initialize(global)` |
| A class with prototype methods, accessors or iteration that scripts can inspect and patch | write the class in a library script over a Java `JSObject` core |
| A built-in indistinguishable from the language's own | `ScriptObject` — only inside the engine, as the standard libraries are |
