# Proxies

A `Proxy` wraps a target with a handler whose traps intercept the object's fundamental
operations - `get`, `set`, `has`, `deleteProperty` and the rest - which is the standard,
specified way to do what Nashorn's older `JSAdapter` did before it. The sample shows the two
everyday shapes: a `get` trap supplying defaults, and a `set` trap validating writes and
throwing on bad ones.
