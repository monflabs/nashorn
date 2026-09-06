// Optional chaining (ES2020): a?.b short-circuits to undefined when a is null
// or undefined, instead of throwing. It comes in three forms: ?. for property
// access, ?.[] for computed access, and ?.() for a call.

const user = {
    name: 'Ada',
    address: { city: 'London' },
    greet: function () { return 'hi'; }
};
const empty = {};

// property access that would otherwise throw on a missing intermediate
console.log(user.address?.city);       // London
console.log(empty.address?.city);      // undefined  (no TypeError)

// computed access
const key = 'address';
console.log(empty?.[key]?.city);       // undefined

// optional call: invoke only if the function is there
console.log(user.greet?.());           // hi
console.log(empty.greet?.());          // undefined

// short-circuit: once the chain gives up, the rest is not evaluated
let called = false;
const r = empty.address?.[(called = true, 'city')];
console.log(r, 'index evaluated:', called);   // undefined index evaluated: false

// combines naturally with ?? for a default
console.log(empty.address?.city ?? 'unknown');  // unknown
