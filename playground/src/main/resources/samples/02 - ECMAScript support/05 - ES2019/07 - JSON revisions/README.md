# JSON revisions

Two ES2019 alignments between JavaScript and JSON. **Well-formed `JSON.stringify`**: a lone surrogate (an unpaired half of a UTF-16 pair) is escaped as `\uXXXX` rather than emitted raw, so the output is always well-formed and re-parseable; valid surrogate pairs still pass through as their character. **The JSON superset**: U+2028 and U+2029, legal in JSON strings but historically illegal in JavaScript string literals, may now appear unescaped in a string literal — so any JSON text is a valid JavaScript string literal.
