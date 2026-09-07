// Top-level await (ES2022): await at a module's top level, no async wrapper.

console.log('module start');

// a top-level await suspends the module until the promise settles
const value = await Promise.resolve(42);
console.log('awaited value:', value);

// await works in expression position and in a loop header
const doubled = await Promise.resolve(value * 2);
console.log('doubled:', doubled);

for await (const n of [Promise.resolve('a'), Promise.resolve('b')]) {
  console.log('for await:', n);
}

console.log('module end');

// exporting makes this source a module, where top-level await is allowed
export const result = doubled;
