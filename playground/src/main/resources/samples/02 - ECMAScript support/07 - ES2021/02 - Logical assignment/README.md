# Logical assignment (`&&=`, `||=`, `??=`)

`a ||= b`, `a &&= b` and `a ??= b` assign `b` to `a` only when the logical operator does not
short-circuit — falsy for `||=`, truthy for `&&=`, nullish for `??=`. Because the assignment is
conditional, a member target's setter fires only when the value is actually written, and the base of
`obj[key] ??= v` is evaluated once. `??=` is the right tool for a default that must survive a real
`0` or `''`.
