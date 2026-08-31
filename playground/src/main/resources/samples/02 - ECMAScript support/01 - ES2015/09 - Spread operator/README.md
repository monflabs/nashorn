# Spread operator

The other direction from rest: `...` in a call spreads an iterable into arguments, and in an
array literal splices one into place - concatenation, cloning and inserting without `apply` or
`concat`. Anything iterable spreads, a string included; spreading into object literals is
ES2018 and deliberately outside this engine's ES2017 target.
