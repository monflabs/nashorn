// Dynamic import() (ES2020): a function-like form that loads a module at
// runtime and resolves to a promise for its namespace object. Unlike a static
// import, the specifier can be computed and the load can be conditional.

// import() returns a promise; the namespace has the named and default exports
import('./math.js').then(m => {
    console.log(m.default);              // the math module
    console.log(m.pi, m.area(2));        // 3.14159 12.56636

    // the same module object comes back on a second import (module cache)
    return import('./math.js');
}).then(again => {
    console.log('cached:', again.pi);    // cached: 3.14159
});

// the specifier is an ordinary expression, so it can be chosen at runtime
const which = './math.js';
import(which).then(m => console.log('computed specifier:', m.pi));  // 3.14159

// this file exports, so it is itself a module - which is the natural home for
// import(), though a plain script can use it too
export {};
