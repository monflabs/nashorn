# `WeakRef`

`new WeakRef(target)` holds an object weakly — the reference alone does not keep the target from
being garbage-collected. `ref.deref()` returns the target while it is alive, or `undefined` once it
has been reclaimed (timing is up to the collector and not observable synchronously). The target must
be an object. Useful for caches and observers that must not extend an object's lifetime.
