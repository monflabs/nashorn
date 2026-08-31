# Promises

The standard container for a value that is not here yet: `then`/`catch` chains, errors flowing
to the nearest rejection handler, and `Promise.all`/`race` combining several. In this engine
promise jobs run on the microtask queue the specification prescribes, drained after the script's
synchronous code - which is why every `then` callback in the sample prints after the last plain
`print`. ES2017's `async`/`await`, one category over, is sugar over exactly this.
