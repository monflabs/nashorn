# Custom objects and extensions

Passing a Java object to a script gets you its beans-style surface: methods, getters as properties.
Sometimes that is not enough — you want a host object that *feels* native: indexable with `[]`,
callable with `()`, enumerable with `for..in`, answering `typeof` as a function. That is what
`org.monflabs.nashorn.api.scripting.JSObject` is for.

## JSObject and AbstractJSObject

`JSObject` is the interface the engine consults whenever script syntax touches an implementing
object. `AbstractJSObject` implements all of it with inert defaults, so you override only what your
object supports:

| Override | Script syntax it powers |
| --- | --- |
| `getMember` / `setMember` / `hasMember` / `removeMember` | `obj.name`, `obj.name = v`, `"name" in obj`, `delete obj.name` |
| `getSlot` / `setSlot` / `hasSlot` | `obj[i]`, `obj[i] = v` |
| `keySet` / `values` | `for (var k in obj)`, `for each` |
| `call` | `obj(args)` — the object is a function |
| `newObject` | `new obj(args)` — the object is a constructor |
| `isArray` / `isFunction` / `getClassName` | `Array.isArray`, `typeof`, `Object.prototype.toString` |
| `getDefaultValue(hint)` | string/number coercion |

A worked example — a `java.nio.DoubleBuffer` exposed as an indexable, array-like object (full
source: [`samples/BufferArray.java`](../../../samples/BufferArray.java ':ignore')):

```java
import java.nio.DoubleBuffer;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;

public class BufferArray extends AbstractJSObject {
    private final DoubleBuffer buf;

    public BufferArray(final int size) { buf = DoubleBuffer.allocate(size); }

    @Override public boolean hasSlot(final int index) {
        return index >= 0 && index < buf.capacity();
    }
    @Override public Object getSlot(final int index) { return buf.get(index); }
    @Override public void setSlot(final int index, final Object value) {
        buf.put(index, ((Number) value).doubleValue());
    }
    @Override public boolean hasMember(final String name) { return "length".equals(name); }
    @Override public Object getMember(final String name) {
        return "length".equals(name) ? buf.capacity() : null;
    }
}
```

```java
engine.put("bb", new BufferArray(10));
engine.eval("bb[3] = 5.5; print(bb[3]); print(bb.length)");
```

The script indexes and measures it like an array, and every access goes straight to the buffer —
no copying. Mirrors close the loop: `ScriptObjectMirror`, the wrapper Java receives for script
objects, itself extends `AbstractJSObject`, so the same vocabulary describes traffic in both
directions.

For bulk numeric data there is a shortcut that skips even the `JSObject` dispatch:
`ScriptObjectMirror.setIndexedPropertiesToExternalArrayData(ByteBuffer)` backs an ordinary script
object's indexed properties with a direct buffer.

## JSAdapter — the same idea, from the script side

`JSAdapter` is a script-level proxy with Rhino ancestry: an object whose property access is routed
through `__get__`, `__put__`, `__call__`, `__has__`, `__delete__`, `__getKeys__` hooks:

```js
var logged = new JSAdapter({
    __get__:  function(name) { print("get " + name); return name.toUpperCase(); },
    __call__: function(name, arg) { print("call " + name + "(" + arg + ")"); }
});
logged.foo;          // prints "get foo", yields "FOO"
logged.greet("hi");  // prints "call greet(hi)"
```

New code targeting ECMAScript 2018 should usually prefer the standard `Proxy`, which this engine
implements in full; `JSAdapter` remains for the large body of existing code written against it.

## ClassFilter

`ClassFilter` decides which Java classes scripts may see at all — one method, called whenever a
script names a class through `Java.type`, `Packages` or `new`:

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
    .classFilter(className -> className.startsWith("com.mycompany.scriptapi."))
    .build();

engine.eval("Java.type('com.mycompany.scriptapi.Thing')");  // fine
engine.eval("Java.type('java.io.File')");                    // ClassNotFoundException
```

!> A `ClassFilter` is a **class-access gate, not a sandbox**. It does not constrain reflection the
script reaches through objects you hand it, does not limit CPU or memory, and there is no Security
Manager in this fork (the JDK removed it) to back it up. Treat scripts you run as code you trust; if
you cannot, isolate at the process boundary, not inside the JVM. Combine the filter with
[`--no-java`](../reference/options.md) when scripts should be pure computation.

## When this is still not enough

`JSObject` decides how *your* objects behave. To change how the engine links *any* Java type —
adding properties to classes you do not control, say — drop below the API to a
[Dynalink custom linker](dynalink-linkers.md).
