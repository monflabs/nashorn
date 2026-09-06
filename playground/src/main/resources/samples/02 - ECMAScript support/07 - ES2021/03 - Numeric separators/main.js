// Numeric separators (ES2021): a single _ may sit between digits of any numeric
// literal, purely as a readability aid - it has no effect on the value.

console.log(1_000_000);              // 1000000
console.log(1_000_000 === 1000000); // true

// in every base, and in the fraction and exponent
console.log(0xFF_FF_FF);            // 16777215  (hex)
console.log(0b1010_0001);           // 161       (binary)
console.log(0o7_5_5);               // 493       (octal)
console.log(3.141_592_653);         // 3.141592653
console.log(1_000e1_0);             // 10000000000000

// and in BigInt literals
console.log((9_007_199_254_740_993n).toString());   // 9007199254740993

// a separator must sit between two digits: leading, trailing, doubled, or next
// to the '.', 'x' or 'e' is a SyntaxError
try { eval('1__0'); } catch (e) { console.log(e.constructor.name); }   // SyntaxError
