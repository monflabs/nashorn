# Object.fromEntries

The inverse of `Object.entries`: given any iterable of `[key, value]` pairs — an array of pairs, or a
`Map` — it builds a plain object, defining each as an own enumerable data property. Paired with
`Object.entries`, it turns "transform an object" into a clean round trip: `entries` → `map`/`filter`
→ `fromEntries`.
