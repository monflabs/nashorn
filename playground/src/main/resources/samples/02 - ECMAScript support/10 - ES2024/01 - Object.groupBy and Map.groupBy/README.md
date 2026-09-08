# Object.groupBy and Map.groupBy

`Object.groupBy(items, callback)` sorts the items of an iterable into buckets keyed by what the
callback returns for each — an ordinary (null-prototype) object of arrays. `Map.groupBy` does the
same but returns a `Map`, so the keys can be objects and are compared by `SameValueZero`. Neither
mutates the input.
