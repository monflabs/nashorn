// Iterator.concat (ES2026): chains several iterables into one iterator that
// yields all of the first, then all of the second, and so on - lazily, opening
// each source only when it is reached.

const chained = Iterator.concat([1, 2], new Set([3, 4]), [5, 6]);
console.log("chained:", chained.toArray().join(", "));  // 1, 2, 3, 4, 5, 6

// each argument must be an iterable object - a bare string is a TypeError,
// so wrap one in its iterator: "ab"[Symbol.iterator]()

// laziness: a later source is not touched until the earlier ones are exhausted
function* loud(name, ...values) {
  console.log("  opening", name);
  yield* values;
}
const it = Iterator.concat(loud("A", 1), loud("B", 2));
console.log("pull 1 ->", it.next().value);  // opening A, then 1
console.log("pull 2 ->", it.next().value);  // opening B, then 2

// because it returns an iterator, the ES2025 helpers chain straight on
console.log("evens:", Iterator.concat([1, 2, 3], [4, 5, 6]).filter(n => n % 2 === 0).toArray().join(", "));
