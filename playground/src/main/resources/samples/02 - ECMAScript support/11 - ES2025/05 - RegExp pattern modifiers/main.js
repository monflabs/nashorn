// RegExp pattern modifiers (ES2025): turn i, m and s on or off for just part
// of a pattern, with (?flags:...) and (?flags-flags:...).

// case-insensitive only inside the group: "AB" matches, but "c" must be exact
console.log(/(?i:ab)c/.test("ABc"));   // true
console.log(/(?i:ab)c/.test("ABC"));   // false - c is outside the (?i:...)

// remove a flag locally: the whole regex is case-insensitive, except (?-i:b)
console.log(/a(?-i:b)c/i.test("AbC"));  // true
console.log(/a(?-i:b)c/i.test("ABC"));  // false - B is case-sensitive here

// multiline and dotAll, scoped to the group
console.log(/(?m:^item)/.test("first\nitem"));  // true
console.log(/(?s:.)/.test("\n"));               // true - . matches a newline
