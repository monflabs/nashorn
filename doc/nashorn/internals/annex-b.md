# Annex B

ECMA-262's Annex B — the normative-optional web-compatibility extensions — is implemented behind
one per-engine flag, `--annexB`, **on by default**. What the annex contains and what passing
`--annexB=false` removes is covered in [Conformance](../reference/conformance.md); this page is how
the flag threads through the engine, and how its hardest clause is implemented.

## Five touch points, and one deliberate exception

The flag is a `ScriptEnvironment` boolean, fixed at engine construction, read in five places:

1. **`Global` initialisation** — when off, the Annex B built-ins are *deleted* from the freshly
   built realm. [nasgen](nasgen.md) bakes each class's member set into a static property map at
   build time, so the shape every realm starts from is the superset; the off-configuration takes
   the difference away. [Property maps](objects.md) being immutable with memoised derivations, the
   Annex-B-less shape is computed once per process, not once per realm. **Ordering is
   load-bearing**: deletions run *before* builtin properties are tagged with their Context-level
   switch points — a deletion after tagging would invalidate a switch point shared with every other
   realm in the Context, a silent process-wide deoptimisation. Lazily built prototypes (`Date`,
   `RegExp`) are pruned inside their lazy initialisers, where they are built.
2. **The `Lexer`** takes the flag as a constructor boolean (it has no environment reference) and
   uses it to recognise B.1.1's HTML-like comments — `<!--` anywhere, `-->` only at the head of a
   line, and never in modules, whose parse passes `false` regardless.
3. **The `Parser`** reads it for the syntax clauses: function declarations under labels and as
   `if` clauses (B.3.2/B.3.4, plain functions only — generators and async functions stay errors),
   call expressions as assignment targets (a *runtime* `ReferenceError` replacing the early
   `SyntaxError`, with the right-hand side unevaluated), `for (var i = 0 in o)` (B.3.6), and the
   B.3.3 marking described below.
4. **`RegExpScanner`** gets it through a factory query that asks the *current Context* — pointedly
   not a JVM-global, so two engines disagreeing about the flag never share a compiled pattern; the
   [regexp cache](regexp.md) keys on it too. It gates B.1.4: legacy octal escapes, a dash beside a
   class escape as a literal, quantified lookaheads.
5. **The persistent code cache** appends a marker to its directory name when the flag is off —
   Annex B decides what source *means*, so a class compiled one way must not be served to an engine
   configured the other.

The exception: **B.3.5** — a `var` taking a simple catch parameter's name — is *not* gated. The
internal flag-clearing that permits it is also what makes an ordinary catch parameter visible at
all, and untangling the two costs more than the purity is worth. Stated in the conformance
document rather than left to be discovered.

## B.3.3: block-level function declarations, hoisted

The annex's hardest clause: in sloppy code, `{ function f() {} }` must *also* bind `f` in the
enclosing function's variable environment — initialised to `undefined` at entry, assigned the
function object **when the declaration's position is reached** — unless that binding would have
been an early error. Three properties make it awkward: the two bindings share one name, eligibility
depends on declarations not yet parsed when the block is, and the assignment's position (not the
block's entry) is observable.

The implementation splits the work across the pipeline — *the parser marks, the desugarer decides,
symbol assignment sees an ordinary tree*:

1. **The parser** keeps the block-scoped declaration exactly as ES2015 has it, and *appends* a
   marker statement at the declaration's source position: an assignment `f = f` whose left-hand
   `IdentNode` carries a dedicated flag.
2. **`ES6Desugar`**, leaving each block, decides whether the extension applies: not if the name is
   a parameter or `arguments`, not if any block between here and the function body binds it
   lexically (`let`, `const`, `class`, another block's function — but *not* a simple catch
   parameter, which B.3.5 lets a var shadow). Surviving names get a plain, initialiser-less `var`
   declared at the top of the function body; failing markers are dropped.
3. **The aliasing problem** — a name resolves to the innermost binding, so the marker's `f = f`
   would naively assign the block binding to itself — is solved by the ident flag: symbol
   resolution for a flagged target starts its outward search **at the function body**, skipping
   every intervening block. From there the compiler works with symbol identity and nothing
   downstream knows Annex B exists.

Two rules the tests forced into shape: two *plain* function declarations of one name in one block
are legal (B.3.3.4) and the **later** wins, which meant hoisting block declarations in source order
rather than prepending each (which reversed them); and a block-level declaration named `arguments`
no longer suppresses the arguments object — that suppression belongs to a function-body
declaration only, a bug that predated Annex B and surfaced under its tests.

For global and `eval` code the same synthetic `var` flows through the existing
declaration-instantiation paths, whose collision rules (a global lexical binding wins; an eval must
not shadow a lexical binding between it and its variable environment) are exactly the annex's
suppression conditions, landing on machinery that already existed.
