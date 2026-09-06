// globalThis (ES2020): a single, host-independent name for the global object.
// Where browsers had window/self and Node had global, every environment now
// exposes globalThis.

console.log(typeof globalThis);                  // object

// the standard globals are properties of it
console.log(globalThis.Math === Math);           // true
console.log(globalThis.JSON === JSON);           // true

// a var at the top level of a script becomes a property of the global object
var planted = 42;
console.log(globalThis.planted);                 // 42

// and a property set on globalThis is visible as a bare global
globalThis.grown = 'here';
console.log(grown);                              // here

// globalThis itself is writable-but-not-enumerable and, like the global, is
// the value of a top-level `this` in sloppy code
console.log(globalThis === this);                // true
