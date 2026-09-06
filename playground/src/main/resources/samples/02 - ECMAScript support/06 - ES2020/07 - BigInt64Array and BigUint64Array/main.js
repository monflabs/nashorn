// BigInt64Array / BigUint64Array (ES2020): typed arrays whose elements are
// 64-bit BigInts - the signed and unsigned integer views that Int32Array and
// friends could not provide, because their elements had to fit in a Number.

const signed = new BigInt64Array(3);
signed[0] = 5n;
signed[1] = -3n;
signed[2] = 2n ** 63n;                 // wraps to the most negative 64-bit value
console.log(signed[0], signed[1], signed[2]);
// 5n -3n -9223372036854775808n

const unsigned = new BigUint64Array([1n, 2n, 3n]);
unsigned[0] = -1n;                     // wraps to the largest unsigned 64-bit value
console.log(unsigned[0]);              // 18446744073709551615n

// they are real typed arrays: the whole %TypedArray% toolkit works
console.log(new BigInt64Array([1n, 2n, 3n]).map(x => x * 10n).join(','));  // 10,20,30
console.log(BigInt64Array.BYTES_PER_ELEMENT);  // 8

// elements are BigInts, so a Number assignment is a TypeError
try { signed[0] = 1; } catch (e) { console.log(e.constructor.name); }      // TypeError

// and a DataView reads/writes the same 64-bit values
const dv = new DataView(new ArrayBuffer(8));
dv.setBigInt64(0, 123456789012345n);
console.log(dv.getBigInt64(0));        // 123456789012345n
