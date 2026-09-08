// RegExp v flag (ES2024): set operations inside character classes.

// intersection: a letter that is also a hex digit
const hexLetter = /[\p{L}&&[a-f]]/v;
console.log('b is a hex letter:', hexLetter.test('b'));   // true
console.log('z is a hex letter:', hexLetter.test('z'));   // false

// difference: a lowercase letter that is not a vowel
const consonant = /^[[a-z]--[aeiou]]+$/v;
console.log('"rhythm" all consonants:', consonant.test('rhythm'));  // true
console.log('"area" all consonants:', consonant.test('area'));      // false

// nested union, and the unicodeSets flag reflects on the regex
const digitOrUnderscore = /^[[0-9]_]+$/v;
console.log('"90_21" matches:', digitOrUnderscore.test('90_21'));   // true
console.log('flags:', digitOrUnderscore.flags, '/ unicodeSets:', digitOrUnderscore.unicodeSets);
