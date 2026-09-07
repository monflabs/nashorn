# Top-level `await`

At the **top level of a module**, `await` (and `for await`) may be used directly - no enclosing
`async` function. The module's evaluation becomes asynchronous: importers wait for it to finish, and
the whole graph is ordered so a module runs only after everything it awaited has settled. This file is
a module (it `export`s), so its top-level `await` runs as written.
