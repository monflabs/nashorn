// String Values and String Methods in Action

// 1. String Values
const primitiveString = "Hello, World!";
const emptyString = "";
console.log("Primitive String:", primitiveString);
console.log("Empty String:", emptyString);

// 2. String Object
const stringObject = new String("Hello, String Object!");
console.log("String Object:", stringObject); // [String: 'Hello, String Object!']

// 3. Common String Methods

// 3.1. length
console.log("\nString Length:");
console.log(`Length of "${primitiveString}":`, primitiveString.length);

// 3.2. charAt()
console.log("\ncharAt():");
console.log(`Character at index 0 of "${primitiveString}":`, primitiveString.charAt(0));

// 3.3. indexOf() and lastIndexOf()
console.log("\nindexOf() and lastIndexOf():");
console.log(`Index of "World" in "${primitiveString}":`, primitiveString.indexOf("World"));
console.log(`Last index of "o" in "${primitiveString}":`, primitiveString.lastIndexOf("o"));

// 3.4. slice(), substring(), and substr()
console.log("\nslice(), substring(), and substr():");
console.log(`slice(0, 5):`, primitiveString.slice(0, 5)); // "Hello"
console.log(`substring(7, 12):`, primitiveString.substring(7, 12)); // "World"
console.log(`substr(7, 5):`, primitiveString.substr(7, 5)); // "World"

// 3.5. toUpperCase() and toLowerCase()
console.log("\ntoUpperCase() and toLowerCase():");
console.log(`Uppercase:`, primitiveString.toUpperCase());
console.log(`Lowercase:`, primitiveString.toLowerCase());

// 3.6. includes(), startsWith(), and endsWith()
console.log("\nincludes(), startsWith(), and endsWith():");
console.log(`Includes "World":`, primitiveString.includes("World"));
console.log(`Starts with "Hello":`, primitiveString.startsWith("Hello"));
console.log(`Ends with "!":`, primitiveString.endsWith("!"));

// 3.7. trim()
const paddedString = "   Padded String   ";
console.log("\ntrim():");
console.log(`Original: "${paddedString}"`);
console.log(`Trimmed: "${paddedString.trim()}"`);
// trimStart() and trimEnd() arrived with ES2019; a regular expression does the same
console.log(`Trim Start: "${paddedString.replace(/^\s+/, "")}"`);
console.log(`Trim End: "${paddedString.replace(/\s+$/, "")}"`);

// 3.8. split()
console.log("\nsplit():");
console.log(`Split by ", ":`, primitiveString.split(", "));

// 3.9. replace(), with a global regular expression for every occurrence (replaceAll() is ES2021)
console.log("\nreplace():");
const sentence = "The quick brown fox jumps over the lazy dog. The dog barked.";
console.log(`Replace "dog" with "cat":`, sentence.replace("dog", "cat"));
console.log(`Replace all "dog" with "cat":`, sentence.replace(/dog/g, "cat"));

// 3.10. repeat()
console.log("\nrepeat():");
console.log(`"Hello" repeated 3 times:`, "Hello".repeat(3));

// 4. Template Literals
const name = "Alice";
const age = 25;
console.log("\nTemplate Literals:");
console.log(`My name is ${name} and I am ${age} years old.`);

// 5. Raw Strings using String.raw
console.log("\nRaw Strings:");
console.log(String.raw`This is a raw string with a newline \n that won't be escaped.`);

// 6. Comparing Strings
const string1 = "apple";
const string2 = "banana";
console.log("\nComparing Strings:");
console.log(`"apple" < "banana":`, string1 < string2); // true (alphabetical order)

// 7. Converting to Primitive Value
console.log("\nConverting String Object to Primitive:");
console.log("stringObject.valueOf():", stringObject.valueOf());
