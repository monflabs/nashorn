# Module system internals

The [user-facing story](../guide/modules.md) is short: modules work, but only the internal
`Context` API runs them. This page is how they work.

## A module is a function with a prologue

The parser produces a module as a `FunctionNode` whose body is *not* a program: during
[desugaring](architecture.md), `ES6Desugar.moduleEnvironment` marks the function as keeping all its
variables in scope and prepends one statement — a `MODULE_SCOPE` runtime call. That call is the
module's first act at run time: it hands the freshly created scope object to the `ModuleRecord`,
which installs the import bindings into it. This is the only moment imports *can* be installed —
after the scope exists, before any user statement runs — and it is why a module's top-level
declarations live in a **module scope object**, never on the global.

## ModuleRecord and the state machine

Each loaded module is a `ModuleRecord` moving through
`NEW → LINKING → LINKED → EVALUATING → EVALUATED`:

- **`link()`** loads the full graph of requested modules, then resolves every import and every
  indirect export via `resolveExport` — all *before any body runs*. A name nobody exports is a
  `SyntaxError` at instantiation; so is one that two `export *` sources both provide (tracked with
  an explicit `AMBIGUOUS` sentinel, since ambiguity is only an error where the name is actually
  *used*). Cycles terminate because each `(module, name)` resolution in flight is recorded in a set
  — a re-entry returns instead of recursing.
- **`evaluate()`** depth-first evaluates dependencies, then applies the module body. A module
  reached again on the same stack — a cycle — is simply not re-entered; a module already
  `EVALUATED` returns immediately, which is the "runs once" guarantee.

The registry enforcing "same specifier, same module" is a map on the **Global** — one per realm, as
the spec requires. Specifier resolution is the host hook, and this host's answer is the
filesystem: resolve against the importing module's own directory, normalise, require readability.

## Live bindings are getter-only accessors

An imported name is installed into the importing module's scope as a **property with a getter and
no setter**. The getter is a bound method handle — essentially `read(exportingModule,
exportName)` — so:

- every read goes to the exporting module's *current* value: live bindings, nothing copied;
- assignment is impossible by construction — there is no setter to call, which yields exactly the
  TypeError the spec wants;
- the temporal dead zone across cycles needs no extra machinery: reading an export whose module
  body has not yet created the binding (reachable through a cycle) finds either no environment or
  a property still flagged `needsDeclaration`, and throws the same `ReferenceError` ordinary
  [TDZ](objects.md) does.

`import * as ns` is the one exception: the namespace object itself is stored, then the property is
flipped to non-writable. Re-exporting a namespace travels under an internal pseudo-name so that two
modules re-exporting the same namespace agree instead of clashing.

## The namespace object

`ModuleNamespace` implements the spec's exotic namespace: memoised on the record (so identity is
stable), keys sorted, and ambiguous names silently omitted — asking *for* an ambiguous name is the
error, listing the namespace is not.

## Entry points

`Context.evaluateModule(source)` = `loadModule(...).link().evaluate()`. `loadModule` checks the
realm's registry first, so a module graph shared by several entry evaluations loads each file once.
The record's Java surface — `read(exportName)`, `exportNames()`, `namespace()` — is what the
[guide's recipe](../guide/modules.md#running-modules--the-honest-part) uses to pull results out.
