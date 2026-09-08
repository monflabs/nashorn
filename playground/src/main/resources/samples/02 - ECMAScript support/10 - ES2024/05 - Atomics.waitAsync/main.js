// Atomics.waitAsync (ES2024): wait on a shared Int32 without blocking.

const sab = new SharedArrayBuffer(4);
const i32 = new Int32Array(sab);

const result = Atomics.waitAsync(i32, 0, 0);   // value at index 0 is 0, so it waits
console.log('async:', result.async);            // true — a promise we can await

result.value.then(outcome => console.log('woke with:', outcome));  // "ok"

// wake it: change the value and notify the one waiter
Atomics.store(i32, 0, 1);
console.log('notified:', Atomics.notify(i32, 0, 1), 'waiter(s)');   // 1

// a wait whose value already differs resolves synchronously
const already = Atomics.waitAsync(i32, 0, 0);   // value is now 1, not 0
console.log('not-equal is synchronous:', already.async, already.value);  // false not-equal
