// Change Array by copy (ES2023): reverse/sort/splice/index-set without mutating.

const original = [3, 1, 2];

console.log('toSorted:', JSON.stringify(original.toSorted()));       // [1,2,3]
console.log('toReversed:', JSON.stringify(original.toReversed()));   // [2,1,3]
console.log('with(0, 9):', JSON.stringify(original.with(0, 9)));     // [9,1,2]
console.log('with(-1, 9):', JSON.stringify(original.with(-1, 9)));   // [3,1,9]
console.log('toSpliced(1,1,"x"):', JSON.stringify(original.toSpliced(1, 1, 'x')));  // [3,"x",2]

// the original is never touched
console.log('original still:', JSON.stringify(original));            // [3,1,2]

// out-of-range index throws, rather than growing the array
try { original.with(5, 0); } catch (e) { console.log('with out of range:', e.constructor.name); }  // RangeError

// typed arrays copy to the same type (no toSpliced)
const bytes = new Uint8Array([2, 0, 1]);
console.log('typed toSorted:', bytes.toSorted().join(','), '- still Uint8Array:', bytes.toSorted() instanceof Uint8Array);
