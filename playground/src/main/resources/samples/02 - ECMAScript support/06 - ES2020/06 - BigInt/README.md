# BigInt

`BigInt` is an arbitrary-precision integer primitive: a literal such as `10n`, or `BigInt(x)`.
`typeof` is `"bigint"`, and its arithmetic stays exact where `Number` rounds past 2^53. The operators
are BigInt-in/BigInt-out (division truncates); BigInt and Number never mix implicitly — that is a
`TypeError` — though they compare and convert explicitly. `BigInt.asIntN`/`asUintN` wrap to a width.
