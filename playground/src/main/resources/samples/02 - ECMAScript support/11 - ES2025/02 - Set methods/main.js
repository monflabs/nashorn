// Set methods (ES2025): the standard set-algebra operations, each returning a
// new Set and leaving the operands untouched.

const a = new Set([1, 2, 3, 4]);
const b = new Set([3, 4, 5, 6]);

console.log("union:", [...a.union(b)].join(", "));                 // 1,2,3,4,5,6
console.log("intersection:", [...a.intersection(b)].join(", "));   // 3,4
console.log("difference:", [...a.difference(b)].join(", "));       // 1,2
console.log("symmetricDifference:", [...a.symmetricDifference(b)].join(", ")); // 1,2,5,6

console.log("isSubsetOf:", new Set([1, 2]).isSubsetOf(a));         // true
console.log("isSupersetOf:", a.isSupersetOf(new Set([1, 2])));     // true
console.log("isDisjointFrom:", a.isDisjointFrom(new Set([7, 8]))); // true

// the argument only needs a size, a has() and a keys() - any set-like works
const setLike = { size: 2, has: x => x === 2 || x === 9, keys: () => [2, 9].values() };
console.log("intersection with a set-like:", [...a.intersection(setLike)].join(", ")); // 2