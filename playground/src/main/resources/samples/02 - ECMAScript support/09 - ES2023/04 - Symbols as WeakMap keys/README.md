# Symbols as WeakMap keys

A **non-registered** Symbol (an ordinary `Symbol(...)` or a well-known one) may now be a key in a
`WeakMap` or `WeakSet`, and a target of a `WeakRef` or `FinalizationRegistry` - useful for attaching
weak metadata keyed by a symbol. A **registered** symbol (from `Symbol.for`, which lives forever in
the global registry) still may not, and neither may any other primitive.
