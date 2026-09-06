// String.prototype.matchAll (ES2020): returns an iterator of all match objects
// for a global regexp, each with capture groups, index, and named groups -
// without the manual lastIndex bookkeeping exec() in a loop requires.

const text = 'x=1, y=22, z=333';
const re = /(?<key>\w+)=(?<val>\d+)/g;   // the g flag is required

for (const m of text.matchAll(re)) {
    console.log(m.index, m[0], '->', m.groups.key, '=', m.groups.val);
}
// 0 x=1 -> x = 1
// 5 y=22 -> y = 22
// 11 z=333 -> z = 333

// the result is a fresh iterator each call, so it is easy to collect
const pairs = [...text.matchAll(re)].map(m => [m.groups.key, Number(m.groups.val)]);
console.log(JSON.stringify(Object.fromEntries(pairs)));   // {"x":1,"y":22,"z":333}

// a non-global regexp is a TypeError - matchAll is all-or-nothing on purpose
try { 'abc'.matchAll(/a/); } catch (e) { console.log(e.constructor.name); }  // TypeError
