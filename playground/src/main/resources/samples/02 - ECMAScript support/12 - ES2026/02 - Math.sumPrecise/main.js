// Math.sumPrecise (ES2026): the correctly-rounded sum of a sequence of numbers.
// Naive left-to-right addition loses precision when magnitudes differ wildly;
// sumPrecise gives the exact IEEE-754 result as if summed with infinite precision.

const tricky = [1e20, 1, -1e20];
console.log("naive reduce:  ", tricky.reduce((a, b) => a + b, 0)); // 0 - the 1 is lost
console.log("Math.sumPrecise:", Math.sumPrecise(tricky));          // 1 - exact

console.log("0.1 x10:       ", Math.sumPrecise(Array(10).fill(0.1))); // 1 - exact (naive + drifts to 0.9999999999999999)
console.log("empty:         ", Math.sumPrecise([]));                  // 0

// it takes any iterable, and every element must be a Number
function* stream() { yield 0.5; yield 0.25; yield 0.125; }
console.log("from iterator: ", Math.sumPrecise(stream()));            // 0.875
