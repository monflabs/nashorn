// Promise.try (ES2025): run a function and get a promise for its result,
// whether it returns a value, throws, or returns a promise - all folded into
// one promise, with no try/catch at the call site.

function mightThrow(x) {
  if (x < 0) throw new Error("negative!");
  return x * 2;
}

Promise.try(() => mightThrow(21))
  .then(v => console.log("resolved:", v))            // resolved: 42
  .catch(e => console.log("caught:", e.message));

Promise.try(() => mightThrow(-1))
  .then(v => console.log("resolved:", v))
  .catch(e => console.log("caught:", e.message));    // caught: negative!

// extra arguments are passed straight through to the callback
Promise.try((a, b) => a + b, 20, 22).then(v => console.log("with args:", v)); // 42