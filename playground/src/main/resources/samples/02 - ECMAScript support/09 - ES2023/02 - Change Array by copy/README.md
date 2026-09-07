# Change Array by copy

`toReversed`, `toSorted`, `toSpliced` and `with` are non-mutating counterparts of `reverse`, `sort`,
`splice` and index assignment: each returns a **new** array and leaves the original untouched, which
is what immutable-style code and reactive frameworks want. `with(i, v)` replaces one element (a
negative index counts from the end; out of range throws `RangeError`). Typed arrays have `toReversed`,
`toSorted` and `with` (returning a same-type array); `toSpliced` is Array-only.
