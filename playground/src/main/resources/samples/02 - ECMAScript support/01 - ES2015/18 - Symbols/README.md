# Symbols

A symbol is a unique, unforgeable property key: `Symbol('desc')` never equals another, symbol
properties stay out of `for...in`, `Object.keys` and `JSON.stringify`, and the global registry
(`Symbol.for`) shares one by name when you want that. They exist so libraries can add properties
without colliding - and the *well-known* symbols (`Symbol.iterator` and kin) are how ES2015 lets
your objects plug into the language's own protocols.
