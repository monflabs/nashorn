# Array.fromAsync

`Array.fromAsync(source, mapfn?, thisArg?)` is the asynchronous form of `Array.from`. It accepts an
async iterable (awaiting each yielded value), a sync iterable, or an array-like, and returns a
**promise** for the resulting array. An optional `mapfn` is applied — and awaited — for each element.
It is the natural way to collect the output of an async generator or a stream into an array.
