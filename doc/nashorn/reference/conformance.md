# Conformance

This engine implements ECMAScript 2026 — ECMA-262, 17th edition — together with its Annex B, and is
measured against a pinned commit of the official [tc39/test262](https://github.com/tc39/test262)
suite on every conformance run.

**~78,600 selected executions.** With optimistic types — this engine's default, where upstream had them off —
every one passes. With `--optimistic-types=false`, eight fail. The run is diffed against a
checked-in expectations file and fails on an unexpected *pass* as well as an unexpected failure, so
conformance can only move forwards.

This page lists the divergences: what fails, what is held out of the measured slice, and what is
excluded from it. Everything not named here conforms.

## What fails

Eight executions, in the pessimistic mode only. All eight are one shape — an **indirect `eval` whose
block-level function declaration must update a `var` the global already had** (Annex B B.3.3). A
program compiled eagerly reaches the global through the merge of its scope and misses the update;
compiled on demand, as every program is under optimistic types, its `var` reaches the global
directly and the cases pass. The feature works in ordinary use — the failures need the runner's
pre-populated global. They are listed in
`core/src/test/resources/test262-expectations-pessimistic.txt`.

## What is held out of the slice

These are engine or substrate limits rather than missing features. Their tests are **not counted as
failures**; the selector holds them out with the reason recorded beside each.

| Held out | The limit |
| --- | --- |
| A set of RegExp patterns (ES2018) | Neither backend can compile them with ES semantics: unbounded lookbehind (the JDK's is bounded and left-to-right), open-group backreferences, a subclassable `exec`. |
| `\p{…}` binary properties and `Script_Extensions` (ES2018) | `java.util.regex` does not expose them without a bundled Unicode Character Database. General-category and `Script` escapes, named groups, the `s` flag and bounded lookbehind all work on both backends. |
| `\q{…}` of a multi-character string, and `\p{…}` of *strings* (ES2024 `v` flag) | A `java.util.regex` character class has no member that is a string. Every other part of the class-set grammar — nested classes, union, `&&`, `--`, ranges, single-code-point `\q{…}`, property escapes — is implemented and passes. |
| Twelve dotAll / multiline / ignoreCase files under RegExp pattern modifiers (ES2025) | A modifier-bearing pattern must compile with the JDK engine, since Joni has no inline flags, and that engine's flavour diverges: a non-unicode `.` under `(?s:…)` matches a whole code point, and `$` under multiline and the folding of `\b`/`\w`/`\P{…}` under `(?i:…)` follow `java.util.regex` rather than the ES `Canonicalize`. The modifier grammar itself parses and the ordinary cases pass. |
| Five duplicate-named-group files (ES2025) | A `\k<name>` backreference to a duplicated name would need a single numbered backreference that selects whichever group matched *and* still fails on a text mismatch. Everything else about duplicate names — `.groups`, `.indices`, enumeration order, the `String.prototype` methods, the same-alternative rejection — passes. |
| Eight Unicode 17.0.0 identifier tests | JDK 25 carries Unicode 16, so a Unicode 17 identifier is not one to it. |

## Annex B

Annex B — the normative-optional annex of features the web depends on — is implemented **behind
`--annexB`, which is on by default**. It covers the legacy built-ins (`escape`, `substr`, the
`String` markup helpers, `__proto__` and the `__defineGetter__` family, `getYear`,
`RegExp.prototype.compile`), the legacy syntax (HTML-like comments, the old pattern grammar,
`for (var i = 0 in o)`), and the web-compatibility scoping rules, most notably B.3.3: a function
declared in a block is also visible, `var`-like, in the enclosing function. `--annexB=false` gives
an engine with none of it. 1,078 of the annex's 1,086 test files pass; the eight that do not are
the indirect-eval shape described under [What fails](#what-fails).

## What is excluded, and why

| Excluded | Why |
| --- | --- |
| Proper tail calls | Normative since ES2015, but a permanent, documented divergence: honouring it costs a trampoline in every tail-shaped function, and no engine but JavaScriptCore ships it. |
| ECMA-402 (`intl402/`) | The Internationalization API is a separate standard, explicitly optional; the `Intl` object is absent and `toLocaleString` and friends use the spec's default behaviour. |
| `staging/` | test262's incubator for proposal tests — not part of any edition. This engine targets approved editions. |
| `legacy-regexp` | `RegExp.$1` and its kin are a Stage 3 proposal the suite files under Annex B; the properties themselves have always been present, but the proposal's tests are out of scope. |
| `[[IsHTMLDDA]]` | `document.all` emulation can only be produced by a web host. |

Everything else outside the selected slice is simply a later edition — ES2027 and beyond (Temporal,
explicit resource management, `Atomics.pause`, import defer) — which this engine does not claim.

The full report — how the slice is selected, the exact exclusion lists, what Annex B costs, and how
to reproduce every number — is the canonical
[conformance document](../../CONFORMANCE.md ':ignore').

## Running the suite yourself

```bash
mvn -Pfetch-externals -pl core generate-test-resources    # clone test262, once
mvn -Ptest262 -DskipTests verify                          # the full conformance run
```

On a healthy tree the run ends with `failing: 0   expected to fail: 0`; add
`-Dnashorn.test262.optimistic=false` for the pessimistic slice, which ends with
`failing: 8   expected to fail: 8`. Narrow either while working with
`-Dnashorn.test262.include=/built-ins/Math/`, and regenerate the expectations with
`-Dnashorn.test262.write.expectations=true`.
