// Lookbehind assertions (ES2018): (?<=...) matches only if preceded by the
// pattern, (?<!...) only if not - the mirror of the lookahead JS always had.
// The asserted text is not part of the match.

const text = 'sizes 8, 10 and $12';

// positive lookbehind: the amount after a currency sign, sign not captured
console.log(JSON.stringify(text.match(/(?<=\$)\d+/g)));   // ["12"] - only the priced number

// negative lookbehind: numbers NOT preceded by a dollar sign
console.log(JSON.stringify(text.match(/(?<!\$)\b\d+/g)));  // ["8","10"]

// combine lookbehind with lookahead: insert a thousands separator before every
// run of three digits that is itself preceded by a digit
const grouped = '1234567'.replace(/(?<=\d)(?=(\d{3})+$)/g, ',');
console.log(grouped);   // 1,234,567
