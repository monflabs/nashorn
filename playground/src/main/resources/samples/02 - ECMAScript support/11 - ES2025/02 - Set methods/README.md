# Set methods

ES2025 gives `Set.prototype` the classic set operations: `union`, `intersection`, `difference`,
`symmetricDifference`, `isSubsetOf`, `isSupersetOf` and `isDisjointFrom`. Each reads the other operand
through a *Set Record* — its `size`, `has` and `keys` — so any set-like object works, not only a real
`Set`, and each returns a fresh `Set` without mutating either operand.