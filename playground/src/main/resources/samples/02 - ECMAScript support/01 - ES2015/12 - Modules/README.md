# Modules

The ES2015 module system — `import`/`export`, default and named exports, namespace imports,
**live bindings**, one evaluation per realm, module scope, cycles — is implemented in full and
held to the module slice of the conformance suite.

What is missing is a **public door**: `eval()` and `jjs` treat their input as a *script*, so a
file containing `export` will not parse there, and nothing on `javax.script` runs a module yet.
The engine's own tests use the internal API — `Context.evaluateModule(Source)`, returning a
`ModuleRecord` with `read(name)`, `exportNames()` and `namespace()` — and that is what this
sample does too. It works here because the playground runs on the class path, where the internal
package is reachable; on a module path it is not exported, and the API carries no compatibility
promise.

The two other tabs are the module files. They are written to a temporary directory before
evaluation, because a specifier like `./counter.js` names a real file relative to its importer —
no search path, no extension guessing.

The whole story, including the Java-side embedding recipe, is in the documentation site's
*ES modules* guide (`doc/nashorn/guide/modules.md`).
