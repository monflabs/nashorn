# Base64

`btoa` and `atob` from the **host** library, as the web platform defines them: `btoa` encodes a
*binary string* - one character per byte, so every char code must be 0-255 - and `atob` decodes
one, forgivingly (whitespace dropped, missing `=` padding tolerated).

A string with characters above 255 is not a binary string; the usual trick, shown here, is to
UTF-8-encode it first with `encodeURIComponent`/`unescape` and reverse that on the way back.
Bad input is reported as an `Error` (a browser's `InvalidCharacterError`).
