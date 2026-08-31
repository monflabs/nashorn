// RegExp Object in Action

// 1. Creating Regular Expressions
const regex1 = /hello/; // Using literal syntax
const regex2 = new RegExp("world", "i"); // Using RegExp constructor with case-insensitive flag

// 2. Testing for Matches
const text = "Hello, world!";
console.log("regex1.test(text):", regex1.test(text)); // false
console.log("regex2.test(text):", regex2.test(text)); // true (case-insensitive)

// 3. Executing a Regular Expression
const execResult = regex1.exec(text);
console.log("regex1.exec(text):", execResult); // Returns an array with match details

// 4. Matching Patterns
const matchResult = text.match(/world/i);
console.log("text.match(/world/i):", matchResult); // Returns an array with match details

// 5. Replacing Text
const replacedText = text.replace(/hello/i, "Hi");
console.log("text.replace(/hello/i, 'Hi'):", replacedText); // Replaces "Hello" with "Hi"

// 6. Splitting Strings
const splitResult = text.split(/,\s*/);
console.log("text.split(/,\\s*/):", splitResult); // Splits the text into ["Hello", "world!"]

// 7. Flags in Action
const regexGlobal = /o/g; // Global flag
const globalMatch = text.match(regexGlobal);
console.log("text.match(/o/g):", globalMatch); // Matches all "o" characters: ["o", "o"]

const regexCaseInsensitive = /hello/i; // Case-insensitive flag
console.log("regexCaseInsensitive.test(text):", regexCaseInsensitive.test(text)); // true

// 8. Using Special Characters
const specialText = "a1 b2 c3";
const digitRegex = /\d/g; // Matches digits
const digitMatch = specialText.match(digitRegex);
console.log("specialText.match(/\\d/g):", digitMatch); // ["1", "2", "3"]

const wordRegex = /\w+/g; // Matches words
const wordMatch = specialText.match(wordRegex);
console.log("specialText.match(/\\w+/g):", wordMatch); // ["a1", "b2", "c3"]

// 9. Lookahead and Lookbehind
const lookaheadText = "apple 123, orange 456";
const lookaheadRegex = /\w+(?=\s\d+)/g; // Matches words followed by a space and digits
console.log("lookaheadText.match(/\\w+(?=\\s\\d+)/g):", lookaheadText.match(lookaheadRegex)); // ["apple", "orange"]

// 10. Combining Patterns
const combinedText = "cat bat rat";
const combinedRegex = /(cat|bat)/g; // Matches "cat" or "bat"
console.log("combinedText.match(/(cat|bat)/g):", combinedText.match(combinedRegex)); // ["cat", "bat"]
