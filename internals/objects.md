# Objects and property maps

A JavaScript object in Nashorn is a `ScriptObject`: a Java object holding its property **values**
in fields and arrays, and its property **layout** in an immutable, shared `PropertyMap` — the
classic hidden-class design, tuned so that `invokedynamic` call sites can guard on one reference
comparison.

## PropertyMap: the hidden class

A `PropertyMap` maps keys to `Property` records (kind, attributes, slot) and never changes: adding,
deleting or redefining a property produces a *new* map. The trick that makes this scale is the
**transition history** — each map keeps a weak map from "property added" to "map that resulted", so
two objects that gain the same properties in the same order arrive at the *same* map instance:

```js
function make(x, y) { return { x: x, y: y }; }
make(1, 2); make(3, 4);   // both objects share one PropertyMap
```

Shared map identity is what keeps inline caches monomorphic: a property-access call site links a
method handle guarded by `map == theMapISawLastTime`, one pointer compare. A parallel
proto-history de-duplicates maps after `__proto__`/`setPrototypeOf` changes, and long derivation
chains degrade their history references from soft to weak so they cannot pin memory.

## Where values live: fields and spill

Property values go to one of two places:

- **Fields** of a generated *structure class*. `ObjectClassGenerator`/`StructureLoader` synthesise
  classes named `JO4`, `JO8`, … (single `Object` field per slot) or `JD4`, `JD8`, … (dual
  `long`+`Object` fields, for [optimistic typing](optimistic-typing.md)); an object literal picks
  the smallest padded size that fits its property count. The structure loader is JVM-wide, so every
  Context shares the same `JO`/`JD` zoo.
- **Spill arrays** — a `long[]` and an `Object[]` on the `ScriptObject` — once fields run out or
  for shapes built dynamically. Spill grows in chunks of eight; `SpillProperty` accessors index the
  arrays through cached method handles.

Getter/setter pairs defined by scripts (`Object.defineProperty` accessors) are
`UserAccessorProperty`: the *map* records only a spill slot, and the actual getter/setter functions
sit in that slot — so redefining an accessor's functions does not change the map, and the shape
stays shared.

## Constructors and shared shapes

For `new Point(x, y)`-style allocation, `AllocationStrategy` caches, per constructor and prototype,
the allocator handle and a seed map, so every instance starts from the same shape. The seed is a
`SharedPropertyMap` carrying a switch point; an instance whose prototype assumption stops holding
is demoted to an unshared copy rather than poisoning its siblings.

## Guards, switch points and relinking

`ScriptObject.lookup` answers each unlinked call site with a `GuardedInvocation`:

- **Map identity guard** for the normal case (sometimes elided in favour of letting the
  structure-class cast throw `ClassCastException`, which relinks just as well and costs nothing
  when it never fires).
- **Prototype switch points**: a property found on the prototype chain links with `SwitchPoint`s
  registered on every map along the chain — any shape change up there invalidates all dependent
  sites at once, wholesale rather than guarded per-call.
- **Unstable sites** (too many relinks) drop to a megamorphic handler that looks properties up the
  slow way, ending the relink churn.

The temporal dead zone falls out of the same machinery: a `let` binding before initialisation is a
property flagged `needsDeclaration`, linked to a thrower guarded on the *current* map — the
declaration swaps the map, and the site relinks to a normal read for free.

## Property attributes and the map flags

Maps also carry object-level state — extensibility, "contains array keys" — and the
[builtin switch point](contexts-globals.md) tagging that lets compiled code bind directly to
`Array.prototype.push` and friends until somebody redefines them.

Set `-Dnashorn.debug` and call `Debug.map(obj)` to see any object's map;
`Debug.dumpCounters()` reports process-wide map statistics — both invaluable when checking whether
your object shapes actually converge.
