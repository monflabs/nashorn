# Conformance

This engine implements ECMAScript 2023 — ECMA-262, 14th edition — together with its Annex B, and is
measured against a pinned commit of the official [tc39/test262](https://github.com/tc39/test262)
suite on every conformance run.

**The headline numbers: 74,600 selected executions, 8 expected failures.** Everything else passes,
in both of the engine's typing modes. All eight are one shape — an indirect `eval` whose block-level
function declaration must update a `var` the global already had, rooted in how the engine merges eval
scopes (the feature works in ordinary use; the failures need the runner's pre-populated global). No
ES2022 or ES2023 corner remains: the exhaustive Unicode identifier tests (thousands of private names
in a single class) now compile, since the splitter divides a block of lexical declarations across
sub-methods rather than overflowing the JVM's 64 KB method limit. The
run fails on an unexpected *pass* as well as an unexpected failure, so conformance can only move
forwards: a fix must remove its expectation line, and a regression cannot hide.

Two ES2018 surfaces are limited by the substrate rather than by choice, and their tests are held out
of the slice (not counted as failures) with the reason recorded in the selector: a set of RegExp
patterns that neither backend can compile with ES semantics — unbounded lookbehind, open-group
backreferences, a subclassable `exec` — and the `\p{…}` Unicode **binary properties** and
`Script_Extensions`, which the JDK's regex engine does not expose without a bundled Unicode Character
Database. General-category and `Script` property escapes, named groups, the `s` flag, and bounded
lookbehind all work on both backends.

## Annex B

Annex B — the normative-optional annex of features the web depends on — is implemented **behind
`--annexB`, which is on by default**. It covers the legacy built-ins (`escape`, `substr`, the
`String` markup helpers, `__proto__` and the `__defineGetter__` family, `getYear`,
`RegExp.prototype.compile`), the legacy syntax (HTML-like comments, the old pattern grammar,
`for (var i = 0 in o)`), and the web-compatibility scoping rules, most notably B.3.3: a function
declared in a block is also visible, `var`-like, in the enclosing function. `--annexB=false` gives
an engine with none of it. 1,078 of the annex's 1,086 test files pass.

## What is excluded, and why

| Excluded | Why |
| --- | --- |
| Proper tail calls | Normative since ES2015, but a permanent, documented divergence: honouring it costs a trampoline in every tail-shaped function, and no engine but JavaScriptCore ships it. |
| ECMA-402 (`intl402/`) | The Internationalization API is a separate standard, explicitly optional; the `Intl` object is absent and `toLocaleString` and friends use the spec's default behaviour. |
| `staging/` | test262's incubator for proposal tests — not part of any edition. This engine targets approved editions. |
| `legacy-regexp` | `RegExp.$1` and its kin are a Stage 3 proposal the suite files under Annex B; the properties themselves have always been present, but the proposal's tests are out of scope. |
| `[[IsHTMLDDA]]` | `document.all` emulation can only be produced by a web host. |

Everything else outside the selected slice is simply a later edition — ES2023 and beyond — which
this engine does not claim.

The full report — how the slice is selected, the exact exclusion lists, what Annex B costs, and how
to reproduce every number — is the canonical
[conformance document](../../CONFORMANCE.md ':ignore').

## Running the suite yourself

```bash
mvn -Pfetch-externals -pl core generate-test-resources    # clone test262, once
mvn -Ptest262 -DskipTests verify                          # the full conformance run
```

The run reports `failing: 20   expected to fail: 20` on a healthy tree. Narrow it while working
with `-Dnashorn.test262.include=/built-ins/Math/`.
