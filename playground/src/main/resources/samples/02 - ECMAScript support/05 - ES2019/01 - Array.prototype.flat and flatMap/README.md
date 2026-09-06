# Array.prototype.flat / flatMap

`flat(depth)` returns a new array with nested arrays spliced in up to `depth` levels (default `1`,
`Infinity` for fully flat), skipping holes. `flatMap(fn)` is `map` followed by a single level of
`flat` in one pass — handy when each input should produce a variable number of outputs, including
none. Both are ES2019, and both are listed in `Array.prototype[Symbol.unscopables]`.
