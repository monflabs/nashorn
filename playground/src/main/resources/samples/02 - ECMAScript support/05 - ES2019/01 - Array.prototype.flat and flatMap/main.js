// Array.prototype.flat and flatMap (ES2019). flat(depth=1) returns a new array
// with sub-array elements spliced in, up to the given depth; flatMap maps with
// a callback and then flattens the result one level - a map that may emit zero,
// one, or several elements per input.

// flat: one level by default
console.log(JSON.stringify([1, [2, 3], [4, [5]]].flat()));      // [1,2,3,4,[5]]

// a deeper depth, and Infinity for "all the way down"
console.log(JSON.stringify([1, [2, [3, [4]]]].flat(2)));        // [1,2,3,[4]]
console.log(JSON.stringify([1, [2, [3, [4]]]].flat(Infinity))); // [1,2,3,4]

// flat drops holes in a sparse array
console.log(JSON.stringify([1, , 3, [4, , 6]].flat()));         // [1,3,4,6]

// flatMap: map-then-flatten-one-level, in a single pass
console.log(JSON.stringify([1, 2, 3].flatMap(function (x) { return [x, x * 2]; })));
                                                                // [1,2,2,4,3,6]

// emit a variable number of elements per input (here: none for negatives)
const words = ['  hello ', '', ' world  '];
console.log(JSON.stringify(
    words.flatMap(function (w) { return w.trim() ? [w.trim()] : []; })));
                                                                // ["hello","world"]
