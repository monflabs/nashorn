# Threads and concurrency

The one-sentence rule: **a Nashorn global, and everything that lives in it, must be used by one
thread at a time**. The engine says so itself — its JSR-223 `THREADING` parameter returns `null`,
which is `javax.script` for "no thread-safety guarantee at all".

## What that means in practice

Script objects are ordinary mutable Java objects with no internal locking; the engine additionally
keeps the *current realm* in a per-thread scoped value, bound around each run of script. So:

- **Do not** run scripts against one engine (or one `Bindings`, or one `ScriptObjectMirror`'s
  realm) from two threads concurrently. Nothing detects it; you get data races.
- **Do** hand work to different threads as different realms, or serialise access yourself.

Three sound patterns, in increasing order of sharing:

**One engine per thread** — the simple recipe. Engines are self-contained; a `ThreadLocal` holding
one per worker gives every thread its own world:

```java
ThreadLocal<ScriptEngine> engines = ThreadLocal.withInitial(
        () -> new NashornScriptEngineBuilder().build());
```

**One engine, one realm per thread** — cheaper when scripts share compiled code. Compile once, then
evaluate the `CompiledScript` against per-thread `Bindings`; each bindings object gets
[its own global](using-the-engine.md#the-scope-model), so the *code* is shared but no mutable state
is:

```java
CompiledScript compiled = ((Compilable) engine).compile(bigScript);
// per thread:
Bindings bindings = engine.createBindings();
compiled.eval(new SimpleScriptContext() {{ setBindings(bindings, ENGINE_SCOPE); }});
```

**One realm, external locking** — when threads genuinely must touch the same script state, make the
lock explicit. `ScriptUtils.makeSynchronizedFunction(fn, monitor)` from Java, or
`Java.synchronized(fn, monitor)` from script, wrap a function so every call synchronizes on the
monitor; anything less targeted should be a lock around the whole engine interaction.

Calling a `ScriptObjectMirror` from a "wrong" thread does work mechanically — mirrors switch the
thread's current realm around each operation — but that only relocates the rule: it makes the call
single-threaded *per call*, not the underlying object safe under concurrency.

## Sharing data on purpose

`SharedArrayBuffer` exists precisely to share memory between threads under defined rules: each
thread (in spec terms, each *agent*) has its own realm, the buffers share storage, and `Atomics`
provides sequentially consistent operations plus `wait`/`notify` for blocking coordination. A Java
host passes the buffer between realms; the [internals page](../internals/atomics.md) shows the
mechanics. This is the *only* shared-mutable-state channel with defined semantics.

For everything else, share **immutable snapshots**: strings, numbers, `Java.asJSONCompatible`
trees, or Java objects with their own synchronization discipline.

## Threads the engine creates

Generators and `async` functions each run their body on a **virtual thread** the engine manages —
that is an implementation technique, not concurrency: exactly one of caller and body ever runs at a
time, and the handoff is synchronous. Two consequences worth knowing:

- Blocking inside a generator body blocks its (virtual) thread, which is cheap — thousands of
  suspended generators cost little.
- Promise reactions and `await` continuations run on the thread that drains the realm's job queue —
  the thread that evaluated the script — **before your `eval` call returns**. There is no hidden
  executor; if a promise chain never settles, the queue simply holds its reactions and `eval`
  returns with them pending.

Details in [Generators and async functions](../internals/generators-async.md).

## `loadWithNewGlobal`

From script, `loadWithNewGlobal(src, args...)` runs code in a fresh realm and returns the result
across the boundary — the script-side isolation primitive, and a natural fit for handing a
self-contained job to another thread's engine.
