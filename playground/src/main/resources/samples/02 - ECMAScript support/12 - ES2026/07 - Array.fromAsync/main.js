// Array.fromAsync (ES2026): the async counterpart of Array.from. It drains an
// async iterable - awaiting each value - into an array, and returns a promise.
// It also awaits the values of a plain (sync) iterable or array-like.

async function* countdown(n) {
  while (n > 0) { yield n--; }
}

async function main() {
  const nums = await Array.fromAsync(countdown(3));
  console.log("from async generator:", nums.join(", ")); // 3, 2, 1

  // an optional mapper, awaited per element
  const doubled = await Array.fromAsync(countdown(3), async x => x * 2);
  console.log("mapped:", doubled.join(", "));             // 6, 4, 2

  // a sync iterable of promises: each element is awaited
  const resolved = await Array.fromAsync([Promise.resolve("a"), Promise.resolve("b")]);
  console.log("awaited elements:", resolved.join(", "));  // a, b
}

main();
