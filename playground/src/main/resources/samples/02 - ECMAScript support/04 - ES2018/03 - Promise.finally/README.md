# Promise.prototype.finally

`.finally(fn)` runs `fn` once the promise settles, resolved **or** rejected. It receives no
argument — it is not told which way things went — and it is transparent: the value or rejection
flows straight through it to the next handler, so `Promise.resolve(42).finally(...)` still resolves
with `42`. (The exception: if the callback itself throws or returns a rejected promise, that
becomes the new outcome.)

It is the natural home for cleanup — stopping a spinner, releasing a lock, closing a handle — the
code you would otherwise duplicate across `then` and `catch`. This was the one ES2018 feature the
fork already carried before the edition bump; the rest arrived with it.
