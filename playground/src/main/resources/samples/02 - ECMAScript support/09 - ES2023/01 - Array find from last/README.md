# Array find from last

`findLast` and `findLastIndex` are `find` and `findIndex` scanning from the **end**: they return the
last element (or its index) a predicate accepts, which is what you want when the interesting element
is near the end or there may be several matches and the last is the one that counts. They exist on
`Array.prototype` and on every typed array.
