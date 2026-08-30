# Using the engine

Everything on this page is plain `javax.script` — the engine implements `ScriptEngine`,
`Compilable` and `Invocable` — plus the Nashorn-specific types in
`org.openjdk.nashorn.api.scripting` where the standard interfaces run out.

## Evaluating scripts

From a string, a `Reader`, or a URL:

```java
ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

engine.eval("print('from a string')");
engine.eval(new FileReader("script.js"));
engine.eval(new URLReader(new URL("https://example.com/lib.js")));
```

`org.openjdk.nashorn.api.scripting.URLReader` is a small convenience: the URL becomes the script's
name, so `__FILE__` and stack traces point at the right place. When evaluating a plain `Reader`, set
the name yourself via `engine.put(ScriptEngine.FILENAME, "myscript.js")` first — anonymous sources
make for unhelpful stack traces.

## Passing variables

`engine.put` makes any Java object visible as a global variable; scripts call its methods directly:

```java
File f = new File("test.txt");
engine.put("file", f);
engine.eval("print(file.getAbsolutePath())");
```

The reverse direction is `engine.get("name")`, which returns script objects wrapped as
[`ScriptObjectMirror`](#scriptobjectmirror) — more on that below.

## Compiling once, running many times

The engine implements `Compilable`. When a script runs repeatedly, compile it once:

```java
Compilable compilable = (Compilable) engine;
CompiledScript compiled = compilable.compile("f(x) * 2");
for (int i = 0; i < 1000; i++) {
    engine.put("x", i);
    compiled.eval();
}
```

Compiled scripts are also the cheap way to run one script against
[several bindings](#the-scope-model) — the bytecode is shared, only the globals differ.

## Invoking functions and methods

The engine implements `Invocable`:

```java
engine.eval("function hello(name) { print('Hello, ' + name); }");
Invocable inv = (Invocable) engine;
inv.invokeFunction("hello", "Scripting!!");           // a global function

engine.eval("var obj = { run: function() { print('run called'); } }");
Object obj = engine.get("obj");
inv.invokeMethod(obj, "run");                          // a method on a script object
```

## Implementing Java interfaces with script

`getInterface` turns script functions into an implementation of any interface — the JSR-223 way to
hand script logic to Java code that expects a type:

```java
// global functions implement the interface...
engine.eval("function run() { print('run() called'); }");
Runnable r = ((Invocable) engine).getInterface(Runnable.class);
new Thread(r).start();

// ...or the methods of one script object do
engine.eval("var obj = { run: function() { print('obj.run called'); } }");
Runnable r2 = ((Invocable) engine).getInterface(engine.get("obj"), Runnable.class);
```

(Scripts can do the same in the other direction without any Java-side help — see
[Connecting with Java](connecting-with-java.md#implementing-java-interfaces).)

## The scope model

This is the part of `javax.script` where Nashorn has real semantics of its own, and it is worth
getting straight.

Every evaluation happens against a **global object** — the thing that owns `Object`, `Array`,
`print` and your top-level `var`s. The engine decides which global to use by looking at the
`ENGINE_SCOPE` bindings of the `ScriptContext`:

1. **The default context**: its `ENGINE_SCOPE` bindings *are* the engine's built-in global, wrapped
   as a mirror. `engine.put("x", …)` therefore writes a real global property.
2. **Your own bindings** (`engine.createBindings()`, or any `SimpleBindings`): the engine associates
   a **fresh global** with that bindings object, storing it under the reserved key
   `"nashorn.global"`. Different bindings, different globals — separate `Object`, separate
   prototypes, no shared state.
3. **`GLOBAL_SCOPE`** bindings are visible read-mostly through a fallback (the engine consults them
   when a name is not found on the global), not copied.

That is what makes the classic multi-scope example work:

```java
engine.put("x", "hello");
engine.eval("print(x)");                     // hello — the default global

ScriptContext newContext = new SimpleScriptContext();
newContext.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
newContext.getBindings(ScriptContext.ENGINE_SCOPE).put("x", "world");
engine.eval("print(x)", newContext);         // world — a different global
```

The `--global-per-engine` option collapses the model: one global for the whole engine, whatever
bindings are passed. Use it when you want JSR-223's bindings plumbing out of the picture.

?> A fresh global per bindings is not free — each carries its own set of built-ins. Create bindings
deliberately, reuse them, and prefer `CompiledScript` when running one script against many.

## ScriptObjectMirror

Whenever a script object crosses into Java, it arrives as an
`org.openjdk.nashorn.api.scripting.ScriptObjectMirror` — a `Map<String,Object>` and `Bindings`
view of the live object, plus everything JavaScript can do with it:

```java
ScriptObjectMirror obj = (ScriptObjectMirror) engine.eval(
        "({ name: 'x', greet: function(who) { return this.name + ' greets ' + who; } })");

obj.callMember("greet", "you");        // invoke a method
obj.get("name");                       // read a property (Map view)
obj.setMember("name", "y");            // write one
obj.keySet();                          // enumerate

ScriptObjectMirror arr = (ScriptObjectMirror) engine.eval("[1, 2, 3]");
arr.isArray();                         // true
arr.getSlot(1);                        // 2
arr.to(List.class);                    // convert to a java.util.List view
```

Useful odds and ends: `isFunction()` / `call(thiz, args...)` for function mirrors, `freeze()` /
`seal()` / `preventExtensions()` (chainable), `getOwnPropertyDescriptor`, `eval(String)` to evaluate
in the object's realm, and the static `ScriptObjectMirror.wrap`/`unwrap` for moving values between
realms explicitly. `setIndexedPropertiesToExternalArrayData(ByteBuffer)` backs an object's indexed
properties with a direct buffer — the fast lane for bulk numeric data.

`ScriptUtils` is the script-side utility bag: `parse` (source to a JSON AST string), `format`
(printf with JS conversions), `makeSynchronizedFunction`, and `convert` (explicit JS-to-Java-type
conversion using the engine's own rules).

## Errors

Script failures surface as `javax.script.ScriptException`, usually wrapping a
`org.openjdk.nashorn.api.scripting.NashornException`, which knows the script file, line and column,
and can render the *script* stack:

```java
try {
    engine.eval("function f() { throw new Error('boom') } f()");
} catch (ScriptException e) {
    Throwable cause = e.getCause();
    if (cause instanceof NashornException ne) {
        System.err.println(NashornException.getScriptStackString(ne));
        // at f (<eval>:1) ...
    }
}
```

A JS `Error` object itself is accessible via `NashornException.getEcmaError()`. With the `-doe`
option (the factory default), full traces are also dumped as they happen.
