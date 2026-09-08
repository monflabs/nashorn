# Iterator helpers

ES2025 adds a new `Iterator` global and a family of methods on `%IteratorPrototype%`, which every
built-in iterator inherits. `map`, `filter`, `take`, `drop` and `flatMap` are **lazy** — they return
a new iterator and pull from the source one value at a time, so they compose over infinite sequences.
`reduce`, `toArray`, `forEach`, `some`, `every` and `find` are **eager** terminals. `Iterator.from`
wraps any iterable or iterator so it, too, gains the helpers.