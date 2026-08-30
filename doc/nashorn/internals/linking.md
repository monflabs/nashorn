# Call sites and linking

Every dynamic operation the compiler emits — `obj.x`, `obj.x = v`, `a[i]`, `f()`, `new C()` — is an
`invokedynamic` instruction. Its bootstrap is `Bootstrap.bootstrap(lookup, opDescriptor, type,
flags)`, and the body of that method is one line: hand the new call site to the per-Context
[Dynalink](https://openjdk.org/jeps/276) `DynamicLinker`. Everything interesting happens in what
that linker consults.

## What a call site says

The `int flags` operand encodes the whole request. The low four bits index the operation —
`GET_PROPERTY`, `GET_ELEMENT`, `GET_METHOD_PROPERTY`, `SET_PROPERTY`, `SET_ELEMENT`,
`REMOVE_PROPERTY`, `CALL`, `NEW`, … — each mapping to a Dynalink operation with an *ordered
namespace list*: a get-for-value tries `PROPERTY, ELEMENT, METHOD`, a get-for-call tries `METHOD,
PROPERTY, ELEMENT`, which is exactly the difference between reading `obj.f` and calling `obj.f()`.
Then come flag bits — `CALLSITE_SCOPE` (this is a variable access, not a member access),
`CALLSITE_STRICT`, `CALLSITE_FAST_SCOPE`, `CALLSITE_OPTIMISTIC`, `CALLSITE_DECLARE`, tracing bits —
and everything from bit 15 up is the [optimistic program point](optimistic-typing.md).

## The linker chain

Linkers are asked in priority order; the first to return a `GuardedInvocation` wins:

| Linker | Links |
| --- | --- |
| `NashornLinker` | `ScriptObject`s and `undefined` — delegates into [`ScriptObject.lookup`](objects.md), and doubles as the type converter for JS→Java conversions (collections, SAM types, arrays). |
| `NashornPrimitiveLinker` | `String`/`ConsString`, `Boolean`, numbers, `Symbol` — wrapper-free primitive semantics: `"abc".length` links against the primitive plus its prototype without ever allocating a `String` object wrapper. |
| `BoundCallableLinker` | Results of `Function.prototype.bind`. |
| `JavaSuperAdapterLinker` | The objects `Java.super()` returns. |
| `JSObjectLinker` | Anything implementing [`JSObject`](../guide/custom-objects.md) — mirrors included. |
| `BrowserJSObjectLinker` | Legacy browser-embedding objects. |
| `ReflectionCheckLinker` | A guard tier that vets reflective access. |
| *(fallback)* `NashornStaticClassLinker` | Type objects from `Java.type` — `new`, statics. |
| *(fallback)* `NashornBeansLinker` | Plain Java objects: beans properties, method overload selection. |
| *(fallback)* `NashornBottomLinker` | The end of the road: TypeErrors for `null` dereference, `undefined` for missing Java members, "not a function". |

[User-supplied linkers](../guide/dynalink-linkers.md), discovered through the Context's class
loader, slot in ahead of the beans linker — which is how scripts can be taught new behaviour for
arbitrary Java types.

## Guarded invocations and relinking

A linker's answer is a method handle plus a **guard** (and optionally `SwitchPoint`s and an
exception trigger). The call site installs the handle; while the guard holds, calls fly at method
handle speed. When it fails, the site relinks — asks the chain again with the new receiver. A site
that relinks too often (`--unstable-relink-threshold`) is deemed megamorphic and links to a
slow-but-stable generic handler. Three guard idioms dominate: [map identity](objects.md) for script
objects, receiver-class checks for Java objects and array storages, and no guard at all where a
`ClassCastException` from the handle's own cast is cheaper and relinks just as well.

The linker factory also wires the optimistic machinery in at this level: a prelink transformer
wraps every invocation with the return-value filter that throws `UnwarrantedOptimismException`
when a value is wider than the call site assumed.

## Java adapters

`Java.extend`, and the automatic conversion of script functions to Java functional interfaces, are
`JavaAdapterFactory`: it generates (and caches, per superclass/interface set) a real subclass whose
overridable methods dispatch to a script object. Two shapes — *class-bound*, where the
implementations are fixed when the adapter class is made, and *instance-bound*, where each
constructor takes a trailing script object (or a single function, for SAM shapes) and every call
looks the member up **live by name**, so reassigning `obj.run` changes behaviour from the next
call. Return values come back through the ordinary JS→Java conversions. `Java.super(adapter)`
links through its own linker straight to `invokespecial`-style super calls.
