# `.at()`

`at(index)` reads one element by index, and a **negative** index counts back from the end - `-1` is
the last element - which the `[]` operator does not do. It is on `Array.prototype`, `String.prototype`
and every typed-array prototype. An out-of-range index gives `undefined` (for arrays and typed arrays)
or `undefined` (for strings).
