// Error.isError (ES2026): a reliable brand check for error objects - true for
// anything with the internal [[ErrorData]] slot, and only that. Unlike
// `instanceof Error` it sees an error from another realm, and unlike a
// duck-typed check it is not fooled by look-alikes.

console.log("TypeError:      ", Error.isError(new TypeError("x")));   // true
console.log("plain Error:    ", Error.isError(new Error()));          // true
console.log("a DOMException? ", Error.isError({ name: "Error" }));    // false - a look-alike
console.log("Error.prototype:", Error.isError(Error.prototype));      // false - not an instance
console.log("a proxy for one:", Error.isError(new Proxy(new Error(), {}))); // false - the slot is on the target
console.log("a string:       ", Error.isError("oops"));               // false
