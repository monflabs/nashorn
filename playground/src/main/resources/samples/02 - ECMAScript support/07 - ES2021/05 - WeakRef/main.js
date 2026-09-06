// WeakRef (ES2021): a reference to an object that does not, on its own, keep the
// object from being garbage-collected. deref() returns the object while it is
// still alive, or undefined once it has been reclaimed.

const target = { name: 'cache entry' };
const ref = new WeakRef(target);

// while something else still holds the target strongly, deref() returns it
console.log(ref.deref().name);                 // cache entry
console.log(ref.deref() === target);           // true

// the target must be an object
try { new WeakRef(42); } catch (e) { console.log(e.constructor.name); }   // TypeError

console.log(Object.prototype.toString.call(ref));   // [object WeakRef]

// A WeakRef lets you cache or observe an object without keeping it alive. Once
// nothing else references the target and the collector runs, deref() would begin
// returning undefined - the timing of which is deliberately not observable here.
