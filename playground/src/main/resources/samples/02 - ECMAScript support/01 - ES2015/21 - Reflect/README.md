# Reflect

The `Reflect` object collects the language's internal operations as ordinary functions -
`construct`, `apply`, `defineProperty`, `deleteProperty`, `get` - with saner return values than
their statement counterparts (booleans instead of throws, for `defineProperty`). Its methods
pair one-to-one with `Proxy` traps, so a trap that wants the default behaviour just forwards to
`Reflect`.
