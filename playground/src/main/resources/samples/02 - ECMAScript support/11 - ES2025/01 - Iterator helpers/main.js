// Iterator helpers (ES2025): lazy, composable operations on any iterator,
// reached through the new Iterator global that every iterator inherits from.

function* naturals() { let n = 1; while (true) yield n++; }

// map/filter/take/drop are lazy - naturals() is infinite, take() stops it
const firstThreeEvenSquares = naturals()
  .map(n => n * n)
  .filter(n => n % 2 === 0)
  .take(3)
  .toArray();
console.log("first three even squares:", firstThreeEvenSquares.join(", "));  // 4, 16, 36

// eager terminals: reduce, forEach, some, every, find
const sum = [1, 2, 3, 4].values().drop(1).reduce((a, b) => a + b, 0);
console.log("sum of all but the first:", sum);  // 9

// flatMap flattens one level
const words = ["hello world", "of iterators"].values().flatMap(s => s.split(" ").values());
console.log("words:", words.toArray().join(", "));

// Iterator.from adapts any iterable or iterator so it gets the helpers too
console.log("from a Set:", Iterator.from(new Set([3, 1, 2])).map(x => x * 10).toArray().join(", "));