# Exponentiation operator

`**` is `Math.pow` as an operator, right-associative like mathematics (`2 ** 3 ** 2` is 512),
with the compound form `**=`. One sharp edge the grammar enforces: a unary minus cannot sit
directly on the base - `(-2) ** 2` needs its parentheses. The second and last feature of ES2016.
