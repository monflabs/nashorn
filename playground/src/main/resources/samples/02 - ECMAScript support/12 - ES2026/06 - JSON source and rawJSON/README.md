# JSON source access and JSON.rawJSON

`JSON.parse`'s reviver receives a third `context` argument; for a primitive value it carries
`context.source`, the exact source text the value was parsed from — so you can see that
`12345678901234567890` was in the document even though the parsed `Number` has rounded. Going the other
way, `JSON.rawJSON(text)` wraps a pre-formatted JSON primitive that `JSON.stringify` emits verbatim, and
`JSON.isRawJSON` tests for it. Together they let a large or high-precision number round-trip losslessly.
