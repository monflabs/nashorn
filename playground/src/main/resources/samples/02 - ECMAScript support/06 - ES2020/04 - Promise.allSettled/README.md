# `Promise.allSettled`

`Promise.allSettled(iterable)` resolves once every input promise has settled, with an array of
result objects — `{status: 'fulfilled', value}` or `{status: 'rejected', reason}` — in input order.
Unlike `Promise.all`, one rejection does not abandon the rest, so it is the tool for "run all of
these and tell me how each one went."
