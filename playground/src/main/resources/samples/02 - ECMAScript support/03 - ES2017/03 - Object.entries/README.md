# Object.entries

`[key, value]` pairs for the own enumerable properties, completing the keys/values/entries trio
objects now share with `Map`. It makes an object destructurable in a `for...of`
(`for (const [k, v] of Object.entries(o))`) and convertible to a real `Map` in one call - both
shown in the sample.
