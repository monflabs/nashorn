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

The modules implemented so far are **`fs`** (file system), **`buffer`** (Node's `Buffer`) and
**`os`** (system information).

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

### Text and binary

With an **encoding**, `readFile` returns a **string** (`'utf8'` — the default — `'latin1'`/`'binary'`,
`'ascii'`, `'utf16le'`, `'hex'`, `'base64'`, or any JDK charset name). With **no encoding** it returns
a **`Uint8Array`** — the raw bytes, as Node returns a `Buffer` (which *is* a `Uint8Array`). `writeFile`
and `appendFile` accept a string or any array-like of byte values, including a `Uint8Array` or `Buffer`.

## `buffer`

`import { Buffer } from 'buffer'` gives Node's **`Buffer`** — a `Uint8Array` subclass, so it is a real
byte array (indexing, `length`, iteration, `slice`) with Node's extras on top:

```js
import { Buffer } from 'buffer';
import fs from 'fs';

const buf = Buffer.from('café', 'utf8');
print(buf.length, buf.toString('hex'));            // 5  636166c3a9

// upgrade a binary fs read to a Buffer for its accessors
const bytes = fs.readFileSync('/tmp/data.bin');    // a Uint8Array
const n = Buffer.from(bytes).readUInt32BE(0);
```

`Buffer` provides `from` (string/array/ArrayBuffer/Buffer), `alloc`/`allocUnsafe`, `isBuffer`,
`concat`, `byteLength`, `compare`; `toString`/`write` in the encodings above plus `hex`/`base64`/
`base64url`; `slice`, `copy`, `fill`, `equals`, `compare`, `indexOf`/`includes`; and the
`readUInt8`…`readDoubleLE/BE` / `write…` numeric accessors (over a `DataView`). It is defined in
`buffer.js` and compiled the first time it is imported.

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

## `os`

System information, over the JVM's own facilities - everything synchronous, as in Node:

```js
import os from 'os';
print(os.platform(), os.arch(), os.type());     // e.g. darwin arm64 Darwin
print(os.cpus().length, 'cores,', Math.round(os.totalmem() / 1e9) + ' GB');
print(os.homedir(), os.tmpdir());
```

| | |
| --- | --- |
| Identity | `platform()` (`darwin`/`linux`/`win32`/…), `arch()` (`x64`/`arm64`/…), `type()`, `release()`, `version()`, `machine()`, `hostname()` |
| Paths | `homedir()`, `tmpdir()`, `devNull`, `EOL` |
| Hardware | `cpus()`, `availableParallelism()`, `totalmem()`, `freemem()`, `endianness()` |
| Runtime | `uptime()`, `loadavg()`, `getPriority()`, `userInfo()`, `networkInterfaces()` |
| `os.constants` | `signals` (`SIGINT`, `SIGTERM`, …) and `priority` |

A few values approximate what Node reports on a real OS: `uptime()` is the **JVM's** uptime, `cpus()`
reports the processor count with best-effort model and timing, and `loadavg()` carries the one-minute
system load in all three slots on platforms that expose only that.

## Extending it

The resolver is `org.monflabs.nashorn.libs.node.NodeModuleLoader`, and `fs` is `NodeFs`. Another Node
built-in would be a new `case` in the loader returning a `Module.values(...)` whose exports are
[`JSObject`](../extending/apis.md) functions — the same shape `fs` uses. See
[Module loaders](../extending/module-loaders.md) for the module SPI in general.
