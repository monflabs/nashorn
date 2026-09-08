// Float16Array (ES2025): a typed array of IEEE half-precision (binary16) floats.

const f16 = new Float16Array([1.5, 3.14159, -0.1]);
console.log("BYTES_PER_ELEMENT:", Float16Array.BYTES_PER_ELEMENT);  // 2
console.log("stored values:", Array.from(f16, x => x.toFixed(4)).join(", "));

// half precision has ~3 decimal digits: 3.14159 is rounded to the nearest binary16
console.log("pi in half precision:", f16[1]);

// Math.f16round rounds a number to what a Float16Array would store
console.log("Math.f16round(1.337):", Math.f16round(1.337));

// DataView reads and writes binary16 too
const dv = new DataView(new ArrayBuffer(2));
dv.setFloat16(0, 0.5);
console.log("DataView getFloat16:", dv.getFloat16(0));  // 0.5