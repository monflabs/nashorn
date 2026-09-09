# Error.isError

`Error.isError(value)` returns `true` exactly when `value` is an object created as an error (one with
the internal `[[ErrorData]]` slot). It works across realms, ignores the prototype chain, and cannot be
spoofed by an ordinary object that merely looks like an error — nor by a `Proxy` wrapping one, since the
slot lives on the target, not the proxy.
