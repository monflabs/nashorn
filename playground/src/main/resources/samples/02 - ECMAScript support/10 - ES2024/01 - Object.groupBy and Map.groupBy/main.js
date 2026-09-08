// Object.groupBy / Map.groupBy (ES2024): bucket an iterable by a key function.

const nums = [1, 2, 3, 4, 5, 6];
const parity = Object.groupBy(nums, n => n % 2 === 0 ? 'even' : 'odd');
console.log('odd:', parity.odd.join(','));    // 1,3,5
console.log('even:', parity.even.join(','));  // 2,4,6

// Map.groupBy allows object keys
const small = { label: 'small' }, big = { label: 'big' };
const bySize = Map.groupBy(nums, n => n < 4 ? small : big);
console.log('small bucket:', bySize.get(small).join(','));  // 1,2,3
console.log('big bucket:', bySize.get(big).join(','));      // 4,5,6
