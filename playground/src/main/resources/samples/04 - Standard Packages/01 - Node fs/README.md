# Node fs

Node's `fs` module, resolved by `import fs from 'fs'` through the experimental `nashorn-node`
resolver. Every operation comes in the three Node shapes — synchronous (`readFileSync`), an
error-first callback (`readFile`), and a promise (`fs.promises.readFile`) — and the async forms
run on a background thread and settle on the realm's event loop, so `await` reads naturally. This
sample writes, reads, stats and lists files under a temporary directory, then cleans up. The module
is a separate, unpublished companion to the engine; the playground registers it on the builder
explicitly, the way an embedder would.
