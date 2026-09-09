// JSON source access + JSON.rawJSON (ES2026): read and preserve the exact
// numeric text a JSON document carried, so a big or high-precision number
// survives a parse/stringify round-trip without being coerced to a double.

// the reviver now gets a third argument whose .source is the raw token text
const big = '{"id":12345678901234567890}';
JSON.parse(big, (key, value, context) => {
  if (key === "id") console.log("parsed:", value, " source was:", context.source);
  return value;
});

// JSON.rawJSON carries verbatim text that stringify emits untouched
const payload = { id: JSON.rawJSON("12345678901234567890"), pi: JSON.rawJSON("3.141592653589793238") };
console.log("stringified:", JSON.stringify(payload)); // the digits survive exactly
console.log("isRawJSON:  ", JSON.isRawJSON(payload.id), JSON.isRawJSON(42)); // true false
