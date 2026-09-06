# Numeric separators (`1_000`)

A single `_` may appear between two digits of any numeric literal — decimal, hex, octal, binary,
the fraction, the exponent, and BigInt literals — as a readability aid with no effect on the value.
A separator that is leading, trailing, doubled, or adjacent to the radix prefix / `.` / `e` / sign
(or inside a legacy-octal literal) is a `SyntaxError`.
