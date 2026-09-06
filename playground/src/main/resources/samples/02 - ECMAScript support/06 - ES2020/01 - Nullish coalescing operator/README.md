# Nullish coalescing (`??`)

`a ?? b` yields `b` only when `a` is `null` or `undefined`. Where `||` treats every falsy value
(`0`, `''`, `NaN`, `false`) as absent, `??` treats only the two nullish values that way — the right
default for a numeric or string option whose `0` or `''` is a real value. The right operand is
short-circuited, and mixing `??` with `&&`/`||` without parentheses is an early SyntaxError.
