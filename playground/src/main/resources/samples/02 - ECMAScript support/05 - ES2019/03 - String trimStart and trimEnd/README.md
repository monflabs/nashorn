# String.prototype.trimStart / trimEnd

The one-sided companions to `trim`: `trimStart` removes leading whitespace, `trimEnd` removes
trailing whitespace. These are the ES2019 standard names for the long-shipped `trimLeft`/`trimRight`
— which live on as Annex B aliases bound to the **same** function objects, so `trimLeft ===
trimStart`.
