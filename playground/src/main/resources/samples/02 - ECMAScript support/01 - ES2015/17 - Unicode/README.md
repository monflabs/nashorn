# Unicode

Strings are sequences of UTF-16 code units, and ES2015 finally gave them code-point-aware tools:
`\u{1F600}` escapes, `codePointAt`, `String.fromCodePoint`, and iteration (`for...of`, spread)
that walks characters rather than halves of a surrogate pair - the difference `length` cheerfully
ignores. One deliberate boundary in this engine: the regular-expression `u` flag is accepted with
sticky-and-flags semantics, but full code-point regexp semantics are not implemented.
