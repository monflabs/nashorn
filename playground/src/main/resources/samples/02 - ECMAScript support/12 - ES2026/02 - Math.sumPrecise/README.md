# Math.sumPrecise

`Math.sumPrecise(iterable)` returns the sum of a sequence of numbers, correctly rounded — the single
`Number` closest to the true mathematical sum, as though every term were added with unlimited
precision before one final rounding. That makes it immune to the ordering and cancellation errors of a
plain `+` loop. Every element must be a `Number` (a non-number throws a `TypeError`); the sum of an
empty sequence is `-0` reported as `0`.
