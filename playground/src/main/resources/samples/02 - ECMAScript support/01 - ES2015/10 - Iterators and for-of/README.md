# Iterators and for-of

The iteration protocol: an iterable answers `[Symbol.iterator]` with an object whose `next()`
returns `{ value, done }`, and `for...of` drives it - arrays, strings, Maps, Sets, and any
object of your own that implements it, like the sample's range. `for...in` walks property
*names*; `for...of` walks *values*, which is nearly always what a loop means.
