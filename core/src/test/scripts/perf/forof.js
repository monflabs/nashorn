// The array iteration protocol: for-of over dense arrays, spread, and destructuring.
var arr = [];
for (var i = 0; i < 1000; i++) { arr.push(i); }
var total = 0;
for (var round = 0; round < 300; round++) {
    for (const v of arr) { total += v; }
    const copy = [...arr];
    total += copy.length;
    const [a, b, c] = arr;
    total += a + b + c;
}
if (total === 0) { throw new Error("optimised away"); }
