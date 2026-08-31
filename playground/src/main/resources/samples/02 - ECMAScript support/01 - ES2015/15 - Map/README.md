# Map

Keyed storage where the key can be *anything* - objects, `NaN`, functions - unlike an object,
whose keys are strings and symbols. Entries iterate in insertion order, `size` is free,
and nothing collides with prototype properties, which is why "a map from X to Y" should be a
`Map` and not an object used as one. (The engine's Java interop also lets a script index a
`java.util.Map` directly - see the *Java collections* sample.)
