# `globalThis`

`globalThis` is the standard, environment-agnostic reference to the global object — replacing the
patchwork of `window`, `self`, and `global`. The built-in globals are its properties, a top-level
`var` shows up on it, and a property assigned to it is reachable as a bare identifier.
