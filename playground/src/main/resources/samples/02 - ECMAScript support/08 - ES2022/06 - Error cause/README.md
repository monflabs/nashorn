# Error `cause`

Every `Error` constructor takes an options object with a `cause`: `new Error(msg, { cause })`. It
records the underlying error a higher-level one is wrapping, without losing it, so a caught error can
be re-thrown with context while the original stays reachable through `err.cause`.
