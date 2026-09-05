// Unicode property escapes (ES2018): \p{...} matches code points by Unicode
// property, and \P{...} its complement. They require the `u` flag, so matching
// is over code points, astral characters included.

// a general category: letters, across scripts
console.log('abc123 déjà λ'.match(/\p{Letter}+/gu));   // ["abc","déjà","λ"]

// a named script
console.log('mix αβγ of Greek'.match(/\p{Script=Greek}+/u)[0]);   // αβγ

// \P is the negation: everything that is not a letter or digit
console.log('a-b_c!d'.replace(/\P{Letter}+/gu, ' '));   // a b c d

// property escapes see astral code points as single atoms
const withEmoji = 'hi 😀 there';
console.log(withEmoji.match(/\p{Letter}+/gu));   // ["hi","there"]  - the emoji is not a letter

// aliases work too: \p{gc=Nd} is the decimal-number general category
console.log('a1b2c3'.match(/\p{gc=Nd}/gu));   // ["1","2","3"]
