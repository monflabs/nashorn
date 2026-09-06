// Two JSON-related ES2019 changes. Well-formed JSON.stringify: a lone
// surrogate - half of a UTF-16 pair, which is not a valid character - is now
// escaped as \uXXXX, so stringify always returns well-formed UTF-16 that can
// be parsed back. And the JSON superset: U+2028 (line separator) and U+2029
// (paragraph separator) may now appear raw inside a string literal, so any
// valid JSON text is now valid inside a script.

// a lone high surrogate: escaped, not emitted raw and broken
console.log(JSON.stringify('\uD834'));            // "\ud834"

// a valid pair (the musical G-clef) still passes through as the character
console.log(JSON.stringify('\uD834\uDD1E').length);   // 4  (a quote, two surrogate code units, a quote)

// so stringify output is always safe to parse again, even for a lone surrogate
const round = JSON.parse(JSON.stringify('\uD834'));
console.log(round.charCodeAt(0).toString(16));     // d834

// JSON superset: this string literal contains a raw U+2028 between the words
const withLS = 'line separator';
console.log(withLS.length);                        // 14
console.log(withLS.charCodeAt(4).toString(16));    // 2028
