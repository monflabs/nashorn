# Uint8Array base64 and hex

Static `Uint8Array.fromBase64` / `fromHex` build a byte array from text; the `toBase64` / `toHex`
prototype methods go the other way; and `setFromBase64` / `setFromHex` decode directly into an existing
array, returning `{read, written}`. base64 supports the `base64url` alphabet, an `omitPadding` option,
and a `lastChunkHandling` choice of `loose` / `strict` / `stop-before-partial`.
