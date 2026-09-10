# Using the engine

The engine implements `javax.script` — `ScriptEngine`, `Compilable` and `Invocable` — plus the
Nashorn-specific types in `org.monflabs.nashorn.api.scripting` where the standard interfaces run out.
This page covers both getting an engine and running scripts against it. **How you create the engine
decides what it can do; how you run scripts against it is the same either way.**

## Getting an engine

There are two entry points:

**The `javax.script` lookup** — the standard JSR-223 way, for **simply evaluating scripts**:

```java
ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn-monflabs");
```

The name must be `nashorn-monflabs` (or the `Nashorn-Monflabs` casing); the engine deliberately does
**not** answer to the generic `js`, `JavaScript` or `ECMAScript`, nor to plain `nashorn` (see
[Creating the engine](engine-setup.md#engine-metadata)). What you get is a *bare* engine: the
defaults, no script libraries, no module loaders. It carries `-doe` (dump the Java stack on a script
error).

**The `NashornScriptEngineBuilder`** — the fork's own builder, and the type-safe way to configure the
engine. Reach for it the moment a script needs an [option](../reference/options.md), a
[script library](../extending/script-libraries.md) or `import`
[module loaders](../extending/module-loaders.md) — none of which the bare engine has:

```java
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;

ScriptEngine engine = new NashornScriptEngineBuilder()
        .strict(true)                                    // an engine option
        .library(new HostLibrary(), new FetchLibrary())  // fetch, timers, atob/btoa
        .moduleLoader(new PathModuleLoader(scriptsDir))  // where import resolves
        .build();
```

`build()` returns an ordinary `javax.script.ScriptEngine`, so everything below this section works the
same whichever way you built it. Prefer the builder for real work; see
[Creating the engine](engine-setup.md) for every option and method.

?> Options and libraries can also be passed as raw `--option` strings and varargs to the deprecated
`NashornScriptEngineFactory.getScriptEngine(...)` overloads. The builder's typed methods are checked
at compile time, and it alone can register a **module loader** — so use it for new code.

## Configuring with the builder

A builder starts with **no options** (what `jjs` runs with), adds what it is told in order — a later
setting of the same option wins, as on a command line — and `build()` validates them, throwing
`IllegalArgumentException` for one it does not know. A builder can be reused, and every `build()` is a
new engine with its own compiled-code cache and globals. The three things only the builder reaches:

**Options.** Each has a typed method; `option(...)` takes any other in its command-line spelling. The
two are interchangeable — `java(false)` and `option("--no-java")` build the same engine.

```java
ScriptEngine engine = new NashornScriptEngineBuilder()
        .annexB(false)                       // --annexB=false — the ECMAScript standard alone
        .java(false)                         // --no-java — the bluntest sandbox
        .locale(Locale.US)
        .optimisticTypes(false)              // run-once script: faster warmup, no deoptimising recompiles
        .option("--class-cache-size=100")    // anything without a named method
        .build();
```

Options are fixed at construction — no per-`eval` switch — so two configurations means two engines,
which coexist cleanly, each its own [Context](../internals/contexts-globals.md). The
[options reference](../reference/options.md) and the tables in
[Creating the engine](engine-setup.md#engine-options) cover every one, including class loading and a
[`ClassFilter`](custom-objects.md#classfilter) (`classLoader(...)`, `classFilter(...)`), which gate
what Java a script can see.

**Script libraries.** A [script library](../extending/script-libraries.md) installs globals into
*every* global the engine creates. Nothing is installed automatically — a bare engine has none, not
even the standard `host` and `fetch`:

```java
engine.eval("setTimeout(() => print('tick'), 10)");   // needs new HostLibrary() above
```

These put an **event loop** behind the engine: `eval` returns when the script is *idle* (its timers
and microtasks have drained), not merely when its synchronous code finishes — see
[Overview and the event loop](../libraries/overview.md). A script that leaves an interval running
keeps `eval` from returning, so clear it in the same evaluation.

**Module loaders.** By default `import` resolves against the filesystem. Register a
[module loader](../extending/module-loaders.md) — files under a root, class-path resources, Java
values, or the [Node modules](../libraries/node.md) — to change that; loaders are consulted in order,
first answer wins, and registering any replaces the default filesystem resolution.

```java
// scriptsDir/greet.mjs:  export function hi(n) { return 'hi ' + n; }
engine.eval("import { hi } from 'greet.mjs'; print(hi('there'))");   // needs the moduleLoader above
```

## Evaluating scripts

From a string, a `Reader`, or a URL:

```java
engine.eval("print('from a string')");
engine.eval(new FileReader("script.js"));
engine.eval(new URLReader(new URL("https://example.com/lib.js")));
```

`org.monflabs.nashorn.api.scripting.URLReader` is a small convenience: the URL becomes the script's
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

The `--global-per-engine` option (a builder option) collapses the model: one global for the whole
engine, whatever bindings are passed. Use it when you want JSR-223's bindings plumbing out of the
picture.

?> A fresh global per bindings is not free — each carries its own set of built-ins. Create bindings
deliberately, reuse them, and prefer `CompiledScript` when running one script against many.

## ScriptObjectMirror

Whenever a script object crosses into Java, it arrives as an
`org.monflabs.nashorn.api.scripting.ScriptObjectMirror` — a `Map<String,Object>` and `Bindings`
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
`org.monflabs.nashorn.api.scripting.NashornException`, which knows the script file, line and column,
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
option (`dumpStackOnError(true)` on a builder; the no-argument factory engine has it on), full
traces are also dumped as they happen.
