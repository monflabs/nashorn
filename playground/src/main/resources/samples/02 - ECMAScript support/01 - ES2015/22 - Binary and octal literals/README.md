# Binary and octal literals

`0b101` and `0o17` join hexadecimal as first-class numeric literals, replacing the sloppy-mode
legacy octal (`017`) that Annex B keeps alive for old code. `Number('0b101')` and
`parseInt(s, 2)` cover the string side; the sample shows both directions and the `toString(radix)`
round trip.
