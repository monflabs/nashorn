# `String.prototype.matchAll`

`str.matchAll(regexp)` returns an iterator over every match, each a full match object with capture
groups, `.index`, and `.groups` for named captures — no `exec`-in-a-loop `lastIndex` bookkeeping.
The regexp must carry the `g` flag (a non-global one throws `TypeError`). It dispatches through the
well-known `Symbol.matchAll`, so a custom matcher can override it.
