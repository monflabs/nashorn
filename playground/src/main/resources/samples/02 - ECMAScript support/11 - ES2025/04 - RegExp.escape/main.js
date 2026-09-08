// RegExp.escape (ES2025): escape a string so it matches literally inside a
// regular expression, whatever punctuation it contains.

const userInput = "a.b*c+? (d)";
const escaped = RegExp.escape(userInput);
console.log("escaped:", escaped);

// build a pattern that matches the literal text - no accidental metacharacters
const re = new RegExp(escaped);
console.log("matches the literal string:", re.test("a.b*c+? (d)"));  // true
console.log("does not match a wildcard:", re.test("axbxc"));         // false

// the leading character of a run is hex-escaped when it could start an
// identifier or digit, so a spliced-in escape never joins with its neighbours
console.log("escape of 'foo':", RegExp.escape("foo"));
