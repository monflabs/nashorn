# Atomics.waitAsync

`Atomics.waitAsync` is the non-blocking companion to `Atomics.wait`: instead of parking the thread it
returns `{ async, value }` straight away. When the wait would block, `value` is a promise that
settles with `"ok"` when another agent calls `Atomics.notify` on the same shared location, or
`"timed-out"` if a finite timeout runs out first. It needs a `SharedArrayBuffer`.
