// Number Object in Action

// 1. Properties of Number
console.log("Number.MAX_VALUE:", Number.MAX_VALUE); // Largest possible number
console.log("Number.MIN_VALUE:", Number.MIN_VALUE); // Smallest possible number
console.log("Number.POSITIVE_INFINITY:", Number.POSITIVE_INFINITY); // Positive infinity
console.log("Number.NEGATIVE_INFINITY:", Number.NEGATIVE_INFINITY); // Negative infinity
console.log("Number.NaN:", Number.NaN); // Not-a-Number

// 2. Checking if a value is finite
console.log("Number.isFinite(100):", Number.isFinite(100)); // true
console.log("Number.isFinite(Infinity):", Number.isFinite(Infinity)); // false

// 3. Checking if a value is NaN
console.log("Number.isNaN(NaN):", Number.isNaN(NaN)); // true
console.log("Number.isNaN(123):", Number.isNaN(123)); // false

// 4. Checking if a value is an integer
console.log("Number.isInteger(42):", Number.isInteger(42)); // true
console.log("Number.isInteger(42.5):", Number.isInteger(42.5)); // false

// 5. Parsing numbers from strings
const num1 = Number("42");
const num2 = Number("42.5");
const num3 = Number("not a number");
console.log("Number('42'):", num1); // 42
console.log("Number('42.5'):", num2); // 42.5
console.log("Number('not a number'):", num3); // NaN

// 6. Converting to Fixed Decimal Places
const number = 123.456789;
console.log("number.toFixed(2):", number.toFixed(2)); // "123.46"
console.log("number.toFixed(0):", number.toFixed(0)); // "123"

// 7. Converting to Exponential Notation
console.log("number.toExponential(2):", number.toExponential(2)); // "1.23e+2"

// 8. Converting to a String
console.log("number.toString():", number.toString()); // "123.456789"
console.log("number.toString(16):", number.toString(16)); // "7b.74bc6a7ef9db22d" (Hexadecimal)

// 9. Checking Safe Integers
console.log("Number.isSafeInteger(42):", Number.isSafeInteger(42)); // true
console.log("Number.isSafeInteger(Math.pow(2, 53)):", Number.isSafeInteger(Math.pow(2, 53))); // false

// 10. Working with EPSILON
const a = 0.1 + 0.2;
console.log("0.1 + 0.2 === 0.3:", a === 0.3); // false (due to floating-point precision)
console.log("Math.abs(a - 0.3) < Number.EPSILON:", Math.abs(a - 0.3) < Number.EPSILON); // true

// Combining Methods
const largeNumber = 123456789;
console.log("Large number in exponential:", largeNumber.toExponential());
console.log("Large number in fixed format:", largeNumber.toFixed(2));


// ---- Math ----

// Math Object in Action

// 1. Constants
console.log("Math.PI:", Math.PI); // The value of PI
console.log("Math.E:", Math.E);   // Euler's constant

// 2. Rounding Numbers
console.log("Math.round(4.7):", Math.round(4.7)); // Rounds to the nearest integer
console.log("Math.floor(4.7):", Math.floor(4.7)); // Rounds down
console.log("Math.ceil(4.3):", Math.ceil(4.3));   // Rounds up

// 3. Power and Square Root
console.log("Math.pow(2, 3):", Math.pow(2, 3));   // 2 raised to the power of 3
console.log("Math.sqrt(16):", Math.sqrt(16));     // Square root of 16

// 4. Trigonometric Functions
console.log("Math.sin(Math.PI / 2):", Math.sin(Math.PI / 2)); // Sine of 90 degrees
console.log("Math.cos(0):", Math.cos(0));                    // Cosine of 0 degrees

// 5. Absolute Value
console.log("Math.abs(-42):", Math.abs(-42)); // Absolute value of -42

// 6. Random Numbers
console.log("Math.random():", Math.random()); // Random number between 0 and 1
console.log("Random number between 1 and 100:", Math.floor(Math.random() * 100) + 1);

// 7. Maximum and Minimum
console.log("Math.max(10, 20, 30):", Math.max(10, 20, 30)); // Largest number
console.log("Math.min(10, 20, 30):", Math.min(10, 20, 30)); // Smallest number

// 8. Logarithms
console.log("Math.log(Math.E):", Math.log(Math.E)); // Natural log of E
console.log("Math.log10(1000):", Math.log10(1000)); // Base 10 log of 1000

// 9. Exponential
console.log("Math.exp(1):", Math.exp(1)); // e^1

// 10. Hyperbolic Functions
console.log("Math.sinh(1):", Math.sinh(1)); // Hyperbolic sine of 1
console.log("Math.cosh(1):", Math.cosh(1)); // Hyperbolic cosine of 1

// Combining Methods
const radius = 5;
const area = Math.PI * Math.pow(radius, 2);
console.log(`Area of a circle with radius ${radius}:`, area);
