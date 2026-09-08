# Well-formed Unicode strings

A JavaScript string is a sequence of UTF-16 code units, so it can hold a *lone surrogate* that is not
part of a pair. `String.prototype.isWellFormed()` reports whether a string is free of them, and
`toWellFormed()` returns a copy with each lone surrogate replaced by U+FFFD (the replacement
character) — useful before handing text to an API that requires valid Unicode.
