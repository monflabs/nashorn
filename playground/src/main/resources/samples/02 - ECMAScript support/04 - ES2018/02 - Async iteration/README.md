# Async iteration

An `async function*` is an **async generator**: a body that may both `yield` and `await`. Its
`next()` returns a promise for the usual `{ value, done }`, so each step can do real asynchronous
work before producing a value. `for await (const x of source)` is the consumer — it calls the
async iterator, awaits each result, and runs the loop body once per settled value.

`for await` also accepts an ordinary synchronous iterable (here, an array of promises): the engine
adapts it through `%AsyncFromSyncIterator%`, awaiting every element. Note the elements come out in
**source order**, not settle order — the loop awaits them one at a time. In this engine the
generator body runs on a virtual thread that parks at each `yield`/`await`; the run ends when the
event loop is idle.
