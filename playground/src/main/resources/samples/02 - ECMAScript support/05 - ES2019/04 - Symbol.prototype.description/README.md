# Symbol.prototype.description

A read-only accessor for a symbol's description, unwrapped — `Symbol('x').description` is `'x'`,
where `toString()` would give `'Symbol(x)'`. It is nullable: a symbol made with no argument reports
`undefined`, distinct from `Symbol('')` which reports `''`. Well-known symbols carry their spec name,
so `Symbol.iterator.description === 'Symbol.iterator'`.
