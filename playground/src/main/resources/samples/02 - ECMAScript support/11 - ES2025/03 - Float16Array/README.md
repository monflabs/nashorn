# Float16Array

ES2025 adds `Float16Array`, a typed array whose elements are IEEE 754 half-precision (binary16, two
bytes) floating-point numbers — useful for GPU and ML data. `Math.f16round(x)` rounds a number to the
nearest value representable as a binary16, and `DataView` gains `getFloat16`/`setFloat16`. Because a
binary16 keeps only about three decimal digits, values like π are visibly rounded when stored.