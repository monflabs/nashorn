# Optional chaining (`?.`)

`a?.b` returns `undefined` instead of throwing when `a` is `null` or `undefined`. Three forms:
`?.` for a property, `?.[expr]` for a computed property, and `?.()` for a call. Once a link is
nullish the whole chain short-circuits — the remaining accesses, calls and index expressions are
not evaluated. It pairs naturally with `??` to supply a default.
