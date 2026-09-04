# Node path

Node's `path` module, resolved by `import path from 'path'` through the experimental `nashorn-node`
resolver. It is pure path-string manipulation with no filesystem access: `join`, `resolve`,
`normalize` and `relative` to build paths; `dirname`, `basename`, `extname` and `isAbsolute` to
inspect them; `parse`/`format` for the object form. Both flavours are always available whatever the
host — `path.posix` and `path.win32` — and the default is the host's. The module is contributed to
the engine builder explicitly, since the engine discovers nothing on its own.
