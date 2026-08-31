# let and const

Block scoping arrived in ES2015: `let` and `const` live in the nearest block, are not hoisted
past their declaration (reading one earlier is the temporal dead zone, a `ReferenceError`), and
give each loop iteration its own binding - the fix for the classic closure-in-a-loop surprise.
`const` freezes the binding, not the value: a `const` object can still be mutated.
