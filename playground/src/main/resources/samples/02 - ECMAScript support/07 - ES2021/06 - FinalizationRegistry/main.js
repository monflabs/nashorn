// FinalizationRegistry (ES2021): request a cleanup callback to run - on the
// event loop - after a registered object has been garbage-collected. The
// callback receives the "held value" you registered alongside the target.

const registry = new FinalizationRegistry(heldValue => {
    // runs at some point after the matching target is reclaimed
    console.log('cleaned up:', heldValue);
});

// register(target, heldValue [, unregisterToken])
let resource = { handle: 1 };
const token = {};
registry.register(resource, 'resource #1', token);

// a registration can be cancelled before collection with its token
console.log(registry.unregister(token));       // true

// the callback must be callable; target and heldValue must differ
try { new FinalizationRegistry(123); } catch (e) { console.log(e.constructor.name); }   // TypeError
try { const o = {}; new FinalizationRegistry(()=>{}).register(o, o); }
catch (e) { console.log(e.constructor.name); }                                          // TypeError

console.log(Object.prototype.toString.call(registry));   // [object FinalizationRegistry]

// Cleanup fires only after garbage collection reclaims the target, whose timing
// is not deterministic - so this sample shows the API rather than waiting for it.
