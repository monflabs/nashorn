# `BigInt64Array` / `BigUint64Array`

The two BigInt-valued typed arrays hold 64-bit signed and unsigned integers as `BigInt` elements —
the views the Number-based typed arrays could not offer. Assignments wrap to 64 bits, a `Number`
element is a `TypeError`, and the full `%TypedArray%` method set (`map`, `filter`, `slice`, `sort`,
…) works. `DataView.getBigInt64`/`setBigInt64` and their unsigned pair read and write the same values.
