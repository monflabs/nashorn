# Modules

The ES2015 module system - `import`/`export`, default and named exports, namespace imports,
**live bindings**, one evaluation per realm, module scope, cycles - is implemented in full and
held to the module slice of the conformance suite.

Modules are consumed with the language's own syntax: a source handed to `eval` that parses as a
module runs as one, and its result is the module's **namespace object**. Where an `import` finds
its modules is the engine's **module-loading chain** (`org.monflabs.nashorn.api.modules`),
registered on the builder - every loader is asked in order, the first that answers wins, null
means "not mine". This sample registers two: a loader written as a *script function* (the
interface has one method, so a function converts) serving the sample's other tabs, and a
`JavaModuleLoader` serving a module whose exports are pure Java values - no script behind
`import { TAU } from "constants"` at all.

`PathModuleLoader` (files under a root) and `ResourceModuleLoader` (class-path resources) ship
too; with no loader registered, a specifier is a filesystem path relative to its importer. The
*ES modules* guide (`doc/nashorn/guide/modules.md`) has the whole story.
