# Object.values

The companion `Object.keys` always implied: the own enumerable property *values*, in the same
order. Handy for iterating a plain-object dictionary without touching its keys, and for feeding
an object's contents to array machinery - `Object.values(scores).reduce(...)` is the idiom.
