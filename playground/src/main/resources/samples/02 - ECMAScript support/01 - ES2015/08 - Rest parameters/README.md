# Rest parameters

`...rest` collects the remaining arguments into a real array - `map`, `reduce` and the rest of
the array toolkit apply directly, which the old `arguments` object never offered. A rest
parameter must be last, and unlike `arguments` it does not include the named parameters before
it.
