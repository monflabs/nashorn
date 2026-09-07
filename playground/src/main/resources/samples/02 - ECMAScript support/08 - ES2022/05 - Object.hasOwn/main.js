// Object.hasOwn (ES2022): a robust own-property test.

const obj = { a: 1, b: undefined };
console.log('own a:', Object.hasOwn(obj, 'a'));           // true
console.log('own b (undefined value):', Object.hasOwn(obj, 'b'));  // true
console.log('own c:', Object.hasOwn(obj, 'c'));           // false
console.log('inherited toString:', Object.hasOwn(obj, 'toString'));  // false

// works where hasOwnProperty cannot: a null-prototype object
const bare = Object.create(null);
bare.x = 42;
console.log('bare has x:', Object.hasOwn(bare, 'x'));     // true
