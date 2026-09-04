# noSuchProperty and noSuchMethod

Nashorn's original catch-alls, from the Rhino lineage: `__noSuchProperty__` answers reads of
properties an object does not have, `__noSuchMethod__` answers calls to methods it does not
have. They are how a script object fakes an arbitrary surface - config lookups with defaults, a
map dressed as an object, a `with` scope that resolves anything. ES2015's `Proxy` is the
standard successor with finer-grained traps; these two remain because a decade of embedded
scripts speaks them.
