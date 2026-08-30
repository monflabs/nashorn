# Conformance

This engine implements ECMAScript 2017 — ECMA-262, 8th edition — together with its Annex B, and is
measured against a pinned commit of the official [tc39/test262](https://github.com/tc39/test262)
suite on every conformance run.

**The headline numbers: 50,339 selected executions, 8 expected failures.** Everything else passes,
in both of the engine's typing modes. The eight are one shape — an indirect `eval` whose
block-level function declaration must update a `var` the global already had — rooted in how the
engine merges eval scopes, and each is named in the checked-in expectations file with the reason.
The run fails on an unexpected *pass* as well as an unexpected failure, so conformance can only
move forwards: a fix must remove its expectation line, and a regression cannot hide.

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

Everything else outside the selected slice is simply a later edition — ES2018 and beyond — which
this engine does not claim.

The full report — how the slice is selected, the exact exclusion lists, what Annex B costs, and how
to reproduce every number — is the canonical
[conformance document](../../CONFORMANCE.md ':ignore').

## Running the suite yourself

```bash
mvn -Pfetch-externals -pl core generate-test-resources    # clone test262, once
mvn -Ptest262 -DskipTests verify                          # the full conformance run
```

The run reports `failing: 8   expected to fail: 8` on a healthy tree. Narrow it while working with
`-Dnashorn.test262.include=/built-ins/Math/`.
