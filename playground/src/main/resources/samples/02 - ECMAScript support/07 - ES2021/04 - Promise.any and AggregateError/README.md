# `Promise.any` and `AggregateError`

`Promise.any(iterable)` fulfils with the first input promise to fulfil, and rejects only if every
one rejects — with an `AggregateError` whose `.errors` array holds each rejection reason in input
order. It is the complement of `Promise.all` (first rejection wins). `AggregateError` is a new error
type, constructible directly as `new AggregateError(errorsIterable, message)`.
