// Map/WeakMap upsert (ES2026): getOrInsert and getOrInsertComputed read the
// value under a key, or - if the key is absent - insert one and return it.
// It replaces the common "has? get : (set, value)" dance.

const counts = new Map();
for (const word of "the cat sat on the mat the".split(" ")) {
  counts.set(word, counts.getOrInsert(word, 0) + 1);
}
console.log("counts:", JSON.stringify([...counts]));  // the:3, cat:1, sat:1, on:1, mat:1

// getOrInsertComputed builds the default lazily, only on a miss, from the key
const cache = new Map();
function slow(n) { console.log("  computing for", n); return n * n; }
console.log("first  9:", cache.getOrInsertComputed(3, slow)); // computes -> 9
console.log("second 9:", cache.getOrInsertComputed(3, slow)); // cached, no compute

// grouping into arrays reads cleanly
const byParity = new Map();
for (const n of [1, 2, 3, 4, 5]) byParity.getOrInsertComputed(n % 2, () => []).push(n);
console.log("by parity:", JSON.stringify([...byParity]));  // 1:[1,3,5], 0:[2,4]
