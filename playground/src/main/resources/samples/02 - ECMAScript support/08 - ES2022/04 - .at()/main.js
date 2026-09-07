// .at() (ES2022): index access that accepts a negative index (from the end).

const xs = [10, 20, 30, 40];
console.log('at(1):', xs.at(1));     // 20
console.log('at(-1):', xs.at(-1));   // 40  (last)
console.log('at(-2):', xs.at(-2));   // 30
console.log('at(9):', xs.at(9));     // undefined

console.log('string at(-1):', 'nashorn'.at(-1));  // n

const ta = new Uint8Array([1, 2, 3]);
console.log('typed at(-1):', ta.at(-1));          // 3
