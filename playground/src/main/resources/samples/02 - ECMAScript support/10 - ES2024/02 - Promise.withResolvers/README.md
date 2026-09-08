# Promise.withResolvers

`Promise.withResolvers()` hands back a promise together with its own `resolve` and `reject`
functions — `{ promise, resolve, reject }` — so you can settle it from outside without the
executor-closure dance. Handy when the thing that resolves the promise is an event or a callback
registered elsewhere.
