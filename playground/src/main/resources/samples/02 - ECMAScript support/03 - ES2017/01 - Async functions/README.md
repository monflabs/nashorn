# Async functions

`async function` makes promise code read sequentially: `await` unwraps a promise (or a plain
value), `try`/`catch` catches rejections, and the function always returns a promise. The body
runs synchronously up to its first `await` — the last line of output is printed before any timer
fires — and resumes on the microtask queue when the awaited promise settles.

The waiting is real: `later(value, ms)` wraps a `setTimeout` from the host library in a promise,
which is the shape of any asynchronous API. Compare `sequential()` — two awaits, 200 ms — with
`parallel()` — `Promise.all`, 100 ms; and see `failing()` turn a rejection into an ordinary
`catch`. In this engine an async body runs on a virtual thread that parks at each `await`; the
run itself ends when the event loop is idle, i.e. when the last timer has fired.
