# Arrays

A JavaScript array's elements do not live in its [property map](objects.md) — they live in an
`ArrayData`, a pluggable storage object on the `ScriptObject`, chosen and re-chosen to fit what the
array actually holds. `NativeArray` is a thin shell over it; so is every object that happens to have
indexed properties.

## The storage lattice

Concrete storages, in widening order:

```text
IntArrayData (int[]) ──► NumberArrayData (double[]) ──► ObjectArrayData (Object[])
                                                              │
                                       SparseArrayData (dense head + TreeMap<Long,Object>)
```

A new `[]` starts as `IntArrayData`. Storing a double into int storage converts the whole array up
to `NumberArrayData`; storing anything else converts to `ObjectArrayData`. The lattice is
**one-way** — nothing narrows back, because the cost of re-scanning to prove narrowability outweighs
what it would buy. Writing far beyond the current length (past 128K elements dense) wraps the data
in `SparseArrayData`, which keeps a dense head plus a tree map for the outliers — `a[1e9] = 1` does
not allocate a gigabyte.

Around any storage, **filters** layer behaviour without changing it: `DeletedArrayFilter` and
`DeletedRangeArrayFilter` track holes (so `new Array(n)` is a deleted range, not n stored
undefineds), `UndefinedArrayFilter` tracks stored undefineds, and `FrozenArrayFilter`,
`SealedArrayFilter`, `NonExtensibleArrayFilter`, `LengthNotWritableFilter` implement
`Object.freeze`/`seal`/`preventExtensions` and a non-writable `length` by refusing the relevant
mutations.

## The fast paths

Compiled code does not call `get`/`set` methods on this abstraction — it links **directly to the
storage**. For `a[i]` on, say, int data, `ContinuousArrayData` builds an element-getter handle over
the `int[]`, guarded by "this object's array data is exactly `IntArrayData`" — one class check.
Conversion of the storage naturally fails the guard and relinks. The getter also carries the
call site's [optimistic program point](optimistic-typing.md), so an element wider than the site's
assumed type deoptimises exactly like any other failed assumption.

`push` and `pop` have their own specialised links (`fastPush`, `fastPopObject`…), which skip even
the index arithmetic and rely on a `ClassCastException` to relink when the storage changes.

### Writing past the end, and the pristine switch point

Appending — `a[a.length] = v` — is by ECMAScript a walk of the prototype chain first: an accessor
or a non-writable property at that index *up the chain* is entitled to intercept the write. Walking
on every append would cost more than it ever finds, because putting such a property at an index of
a prototype takes a deliberate `defineProperty` or `freeze`. So the engine keeps one process-wide
`SwitchPoint` — "no object anywhere holds an indexed property a write could not simply shadow" —
and links appends straight to the element store under it. The first definition of an indexed
accessor or read-only indexed property anywhere invalidates it, once and for all, and appends fall
back to the honest walk. Process-global and one-way on purpose: the shape it guards is shared by
every array in the process.

## Length and truncation

`length` is a field of the `ArrayData`, changed only through `setLength` (which the
length-not-writable filter turns into a no-op). Shrinking `length` truncates the storage; deleting
ranges installs the deleted-range filter rather than compacting.

## Typed arrays

`Int8Array` and family share the same design one level down: their `ArrayData` is a
`TypedArrayData` view over a `ByteBuffer` — for a [`SharedArrayBuffer`](atomics.md), a direct
buffer whose storage other threads see. Element access links to the buffer exactly like the
`int[]` case above; there is no boxing between a typed array and its bytes.
