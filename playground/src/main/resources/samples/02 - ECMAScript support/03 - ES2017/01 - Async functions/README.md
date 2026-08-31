# Async functions

`async function` makes promise code read sequentially: `await` unwraps a promise (or plain
value), `try`/`catch` catches rejections, and the function itself always returns a promise. In
this engine an async body runs the way a generator does - on a virtual thread that parks at each
`await` - and resumes on the microtask queue when the awaited promise settles. The sample's
timestamps show the interleaving: synchronous code first, then the awaited continuations.
