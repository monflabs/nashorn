// BigInt (ES2020): an integer primitive of arbitrary precision. A literal ends
// in n, or BigInt(x) converts. It is its own type - typeof is "bigint" - and
// its arithmetic never loses precision the way Number does past 2**53.

console.log(typeof 10n);                          // bigint

// exact beyond Number's safe integer range
const big = 2n ** 64n;
console.log(big.toString());                      // 18446744073709551616
console.log(9007199254740993n === 9007199254740993n);  // true

// Number loses that precision
console.log(9007199254740993 === 9007199254740992);    // true (!) - Number rounds

// the usual operators, all BigInt-in BigInt-out
console.log(7n / 2n, 7n % 2n, 2n ** 10n);         // 3n 1n 1024n (division truncates)

// BigInt and Number do not mix implicitly - a deliberate TypeError
try { 1n + 1; } catch (e) { console.log(e.constructor.name); }   // TypeError

// but they compare and can be converted explicitly
console.log(2n < 3, 2n == 2);                     // true true
console.log(Number(42n), BigInt(42));             // 42 42n

// asIntN / asUintN wrap to a fixed width
console.log(BigInt.asIntN(8, 256n), BigInt.asUintN(8, -1n));  // 0n 255n
