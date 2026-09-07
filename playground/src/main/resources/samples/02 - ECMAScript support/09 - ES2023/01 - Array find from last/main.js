// Array.prototype.findLast / findLastIndex (ES2023): search from the end.

const nums = [1, 5, 2, 5, 3];
console.log('findLast < 4:', nums.findLast(x => x < 4));        // 3
console.log('findLastIndex === 5:', nums.findLastIndex(x => x === 5));  // 3
console.log('findLast > 9:', nums.findLast(x => x > 9));        // undefined
console.log('findLastIndex > 9:', nums.findLastIndex(x => x > 9));  // -1

// also on typed arrays
const ta = new Int16Array([10, 20, 30]);
console.log('typed findLast even:', ta.findLast(x => x % 2 === 0));  // 30
