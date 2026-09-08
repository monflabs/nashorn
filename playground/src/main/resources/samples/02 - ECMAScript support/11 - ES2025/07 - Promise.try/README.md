# Promise.try

`Promise.try(fn, ...args)` (ES2025) calls `fn` synchronously and wraps the outcome in a promise: a
normal return resolves it, a thrown error rejects it, and a returned thenable is adopted. It is the
tidy way to start a promise chain from a function that might do any of the three, without wrapping
the first call in `try`/`catch` yourself. Any extra arguments are forwarded to `fn`.