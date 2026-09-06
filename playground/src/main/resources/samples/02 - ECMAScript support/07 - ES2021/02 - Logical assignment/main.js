// Logical assignment (ES2021): &&=, ||= and ??= combine a logical operator with
// assignment, and - crucially - only assign when the operator does not
// short-circuit, so the right-hand side (and any setter) runs conditionally.

let a = 0;
a ||= 5;                       // assigns because 0 is falsy
console.log(a);                // 5

let b = 3;
b &&= 10;                      // assigns because 3 is truthy
console.log(b);                // 10

let c = null;
c ??= 'default';               // assigns because c is nullish
console.log(c);                // default

let d = 0;
d ??= 99;                      // 0 is NOT nullish, so no assignment
console.log(d);                // 0

// the assignment is skipped entirely when short-circuited - a setter is not
// even called
const obj = { _v: 1, get v() { return this._v; }, set v(x) { console.log('set!'); this._v = x; } };
obj.v ||= 42;                  // v is 1 (truthy), so "set!" never prints
console.log(obj.v);            // 1

// a common use: fill in a default option in place
const opts = { retries: 0 };
opts.retries ??= 3;            // keeps a real 0
opts.timeout ??= 1000;         // supplies a missing one
console.log(opts.retries, opts.timeout);   // 0 1000
