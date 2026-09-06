// String.prototype.replaceAll (ES2021): replaces every occurrence, where the
// older replace() with a string search replaced only the first.

console.log('a.b.c'.replaceAll('.', '-'));          // a-b-c
console.log('one two two'.replace('two', 'X'));     // one X two  (replace: first only)
console.log('one two two'.replaceAll('two', 'X'));  // one X X

// the $ substitution patterns work, just as in replace
console.log('a1b2'.replaceAll(/\d/g, '[$&]'));      // a[1]b[2]

// a function replacer is called for each match, with (match, offset, string)
console.log('x-x-x'.replaceAll('x', (m, i) => i));  // 0-2-4

// a RegExp search must be global, or it is a TypeError - the rule that sets
// replaceAll apart from replace
try { 'aa'.replaceAll(/a/, 'b'); } catch (e) { console.log(e.constructor.name); }  // TypeError
