# Objects and JSON

Objects as the language's universal record: literals, property access, and the round trip through
`JSON.stringify` and `JSON.parse` - with a replacer, a reviver, and pretty-printing. The engine's
JSON parser is its own fast path, not `eval` in disguise, so parsing untrusted data is exactly as
safe as the specification says it is.
