# Iterator.concat

`Iterator.concat(...iterables)` returns a fresh iterator that yields every value of each argument in
order. Each argument is checked up front to be an iterable, but its iterator is opened only when the
concatenation reaches it, and the current one is closed before moving on. The result is an iterator, so
the ES2025 helpers (`map`, `filter`, `take`, …) compose directly onto it.
