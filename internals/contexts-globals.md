# Contexts, globals and realms

Two objects organise everything at run time. A **`Context`** is an engine instance — one per
`NashornScriptEngine`, one per `Shell` run. A **`Global`** is a *realm* — one complete set of
built-ins (`Object`, `Array.prototype`, `print`…) and one global namespace. A Context owns many
Globals; compiled code belongs to the Context and runs against whichever Global is current.

## What lives where

| Per **Context** | Per **Global** (realm) |
| --- | --- |
| `ScriptEnvironment` — the [options](../reference/options.md), fixed at construction | Every built-in constructor and prototype (many created lazily on first touch) |
| Class loaders and the compiled-class cache (per-`Source`, LRU, `--class-cache-size`) | The global lexical scope (`let`/`const` at top level) and its invalidation switch point |
| The persistent code store (`--persistent-code-cache`) | The [job queue](generators-async.md#the-job-queue) — microtasks are per realm |
| The Dynalink [`DynamicLinker`](linking.md) and discovered custom linkers | The [module registry](modules.md) — one record per specifier per realm |
| The `ClassFilter` and application class loader | The live generator set, `$OPTIONS`/`$ENV` when scripting |
| Builtin switch points (invalidated when a builtin is redefined) | |
| `GlobalConstants` (see below) | |

The JVM-wide layer below both: the [structure classes](objects.md) (`JO`/`JD`) come from a single
static loader, so all Contexts in a process share one shape zoo.

## The current realm is a scoped value

`Context.getGlobal()` reads a static `ScopedValue` (JEP 506, final in JDK 25). Everything that runs
script establishes it around the run — `Context.callWithGlobal`/`runWithGlobal` bind the realm for
exactly the duration of an operation: the JSR-223 entry points around each call,
`loadWithNewGlobal` around the loaded script, generator and async bodies on their
[virtual threads](generators-async.md). A scoped value rather than a thread-local because the
binding structurally cannot outlive its scope — nothing to forget to restore, nothing to leak to
the next task on a pooled thread — and because reads are cheaper on the virtual threads generators
run their bodies on. The nested same-realm case (a mirror used inside its own realm) short-circuits
to a comparison. This is still the mechanism behind the
[concurrency rule](../guide/concurrency.md): the engine does not associate state with "the" thread,
it associates a realm with *each* thread, and one realm on two threads at once is a data race. One
consequence worth knowing: scoped values are not inherited by a plainly-started thread, so a thread
you start yourself begins with no realm — exactly why the engine binds the realm explicitly on each
generator's thread.

## Realms are cheap-ish, code is shared

`Context.createGlobal()` builds and initialises a fresh realm. Compiled scripts are explicitly
multi-realm: compiling a `Source` yields the compiled class plus a hook that manufactures a
per-Global program function — so one pile of bytecode serves every realm of the Context, and only
the `ScriptFunction` and its scope are per-realm. That is exactly what the JSR-223 engine does when
[each `Bindings` gets its own global](../guide/using-the-engine.md#the-scope-model), and why
`CompiledScript` against many bindings is cheap.

`loadWithNewGlobal` is the deliberate realm crossing: make a new Global, run the script inside a
scoped binding of it, then wrap the result as a mirror for the caller's realm, which resumes when
the binding ends. Values that cross realms travel as mirrors; that is the boundary's contract.

## GlobalConstants: the one thing that assumes a single realm

Reads of effectively-constant global properties (`Math`, user singletons) can link to a
`MethodHandles.constant` — the fastest possible access — guarded by a switch point on the property's
setter, with a one-reassignment grace so the common `x = function(){…}` redefinition pattern does
not kill it. But a constant folded into a call site couples that site to *one* Global, and the
Context's code is shared. So the machinery arms itself only while the Context has a single Global:
creating the **second** Global invalidates it forever, and entering a different realm
(`callWithGlobal` invalidates on the way in and again for the realm resumed on the way out) also
invalidates outstanding links. Single-realm embedders get the optimisation; multi-realm embedders
silently do not, and nothing is incorrect either way.

## Initialisation order matters

Realm construction runs the [nasgen](nasgen.md)-generated property maps, wires lazily-initialised
builtins behind sentinel getters, applies option-dependent surgery
([`--annexB=false` deletions](annex-b.md), `--no-java`), and only then tags builtin properties with
their Context-level switch points — deletions after tagging would invalidate switch points shared
with every other realm, a mistake the [Annex B implementation](annex-b.md) documents from
experience.
