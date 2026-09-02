# Node compatibility: the `node` module resolver

Alongside the [standard libraries](overview.md) that install host functions into every global, the
engine ships a **module resolver for Node's built-in modules**. Where the standard libraries answer
`fetch(...)` and `setTimeout(...)` as globals, the node resolver answers `import` — the way Node's
own built-ins are reached:

```js
import fs from 'fs';                 // the whole module
import { readFileSync } from 'fs';  // a named export
import fs from 'node:fs';           // the node: scheme works too
```

It is built into `nashorn-core` and consulted **before** your own [module loaders](../guide/modules.md)
and the filesystem, so a bare `fs` always means the built-in module — exactly as in Node. A specifier
it does not recognise is passed on to the next loader, so it never shadows your own modules.

The one module implemented so far is **`fs`**.

## `fs`

A Java implementation of Node's file-system module over `java.nio.file`. Every operation comes in
the three Node shapes:

- **synchronous** — `fs.readFileSync(path)`, returns or throws;
- **callback** — `fs.readFile(path, (err, data) => …)`, Node's error-first callback;
- **promise** — `fs.promises.readFile(path)`, returns a `Promise`.

The callback and promise forms run the I/O on a background thread and deliver the result on the
realm's [event loop](../guide/concurrency.md), so an `eval` (or the playground) keeps running until
they settle, and `async`/`await` reads naturally.

```js
import fs from 'fs';

// synchronous
fs.writeFileSync('/tmp/note.txt', 'hello');
print(fs.readFileSync('/tmp/note.txt', 'utf8'));      // hello

// callback
fs.readFile('/tmp/note.txt', 'utf8', (err, data) => {
    if (err) throw err;
    print(data);
});

// promise / async-await
const text = await fs.promises.readFile('/tmp/note.txt', 'utf8');
```

### Operations

Each is available as `xSync`, `x` (callback) and `promises.x`, unless noted.

| Group | Operations |
| --- | --- |
| Files | `readFile`, `writeFile`, `appendFile`, `truncate`, `copyFile`, `rename`, `unlink` |
| Directories | `readdir`, `mkdir`, `rmdir`, `rm` (recursive), `mkdtemp` |
| Metadata | `stat`, `lstat`, `realpath`, `access`, `chmod` |
| Links | `symlink`, `readlink` |
| Existence | `existsSync` (sync only), `exists` (callback, `(exists) =>`, no error) |
| Constants | `fs.constants` — `F_OK`/`R_OK`/`W_OK`/`X_OK`, the `O_*` open flags, `COPYFILE_EXCL` |

`readdir(path, { withFileTypes: true })` yields **`Dirent`** objects (`name`, `isFile()`,
`isDirectory()`, `isSymbolicLink()`); otherwise it yields file-name strings. `mkdir` and `rm` take
`{ recursive: true }`; `rm` also takes `{ force: true }`.

`stat`/`lstat` return a **`Stats`** object with `size`, `mtimeMs`/`atimeMs`/`ctimeMs`/`birthtimeMs`,
and the `isFile()`/`isDirectory()`/`isSymbolicLink()` predicates.

### Text, not Buffer

The engine has no Node `Buffer` type, so **`fs` is text-based**: `readFile` returns a **string** —
UTF-8 by default, or the encoding you pass (`'utf8'`, `'latin1'`/`'binary'` for a byte-preserving
round trip, `'ascii'`, `'utf16le'`, or any JDK charset name). `writeFile`/`appendFile` accept a
string, or an array-like of byte values. This covers configuration, JSON, source and log files —
the overwhelming majority of `fs` use — but binary formats that need a real `Buffer` are out of
scope for now.

### Errors

A failure throws (sync) or is delivered as the first callback argument / promise rejection (async)
as an `Error` carrying a Node **`code`**:

```js
try {
    fs.readFileSync('/does/not/exist');
} catch (e) {
    print(e.code);   // ENOENT
}
```

Codes mapped: `ENOENT`, `EEXIST`, `ENOTDIR`, `ENOTEMPTY`, `EACCES`, and `EIO` for anything else.

## Extending it

The resolver is `org.monflabs.nashorn.libs.node.NodeModuleLoader`, and `fs` is `NodeFs`. Another Node
built-in would be a new `case` in the loader returning a `Module.values(...)` whose exports are
[`JSObject`](../extending/apis.md) functions — the same shape `fs` uses. See
[Module loaders](../extending/module-loaders.md) for the module SPI in general.
