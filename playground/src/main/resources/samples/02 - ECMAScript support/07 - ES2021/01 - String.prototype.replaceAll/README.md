# `String.prototype.replaceAll`

`str.replaceAll(search, replacement)` replaces **every** non-overlapping occurrence, where a
string-search `replace` replaced only the first. It supports the same `$&`/`` $` ``/`$'`/`$$`
substitution patterns and function replacers, and dispatches through a value's `Symbol.replace`.
A RegExp search must carry the `g` flag or it throws `TypeError`.
