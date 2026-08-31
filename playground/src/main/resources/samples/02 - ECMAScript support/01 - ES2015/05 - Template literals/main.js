// Template literals
const firstName = 'John';
console.log(`Hello ${firstName}!
    Good morning!`);

// Multi-line, with expressions
const items = ['apples', 'pears'];
console.log(`${items.length} items:
${items.map((item, i) => `  ${i + 1}. ${item}`).join('\n')}`);

// Tagged template literals: the tag function receives the strings and the values
function highlight(strings, ...values) {
    return strings.reduce((out, s, i) => out + s + (i < values.length ? `[${values[i]}]` : ''), '');
}
const who = 'World', n = 3;
console.log(highlight`Hello ${who}, you have ${n} messages`);

// The raw strings are available to the tag, and through String.raw
console.log(String.raw`no newline here: \n`);
