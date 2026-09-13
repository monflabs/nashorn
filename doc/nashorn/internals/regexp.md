# Regular expressions

Nashorn does not implement a regexp engine of its own — it rewrites ECMAScript patterns into a form
a Java engine accepts, and runs them on one of two backends.

## The pipeline

```text
pattern source ──► RegExpScanner (rewrite + validate) ──► Joni  (the default)
                                                      └─► java.util.regex  (what Joni cannot express, or opt-in)
```

Which backend a pattern lands on is decided per pattern, not per engine. Joni takes it unless the
pattern needs something Joni's ES5-era JavaScript syntax has no form for, in which case the JDK
engine does:

| Routed to `java.util.regex` | Why |
| --- | --- |
| the `u` and `v` flags | Joni works in UTF-16 code units and has no notion of a code point, which is the whole of what those flags change |
| `(?<name>…)`, `\k<name>`, `(?<=…)`, `(?<!…)` | Joni's JavaScript syntax has neither named groups nor lookbehind |
| `(?ims-ims:…)` (ES2025 pattern modifiers) | Joni has no inline flags |
| everything, with `-Dnashorn.regexp.impl=jdk` | the opt-in |

**`RegExpScanner`** is a recursive-descent scanner over the ECMAScript pattern grammar that emits
an equivalent Java-syntax pattern while enforcing the semantics the backends do not share with
JavaScript: it resolves the octal-escape/backreference ambiguity (a `\7` with no seventh group is
an octal escape; a forward reference to a group that never materialises becomes an unmatchable
assertion), rewrites `\u{...}` code-point escapes, escapes the braces and brackets JavaScript
tolerates bare, and rejects at scan time what the `u` flag makes an error. It is also where
[Annex B's pattern grammar](annex-b.md) lives — legacy octal escapes written out as unicode
escapes (bounded at three digits, because the backends disagree about `\00`), a dash beside a class
escape turned into a literal member, quantified lookaheads — all gated on the flag.

**Joni**, a bundled, package-renamed port of the Oniguruma-derived engine (from JRuby), is the
default backend: a bytecode-compiling matcher working in UTF-16 code units. This fork carries
local modifications aligning it with ECMAScript where Ruby semantics differed — most notably the
empty-repetition rule: ECMAScript fails a repetition's iteration that matched nothing once the
minimum is met (so `(?:(?=(a)))?` leaves its group *undefined* where Ruby keeps the capture), and
the check must not apply to an iteration below a counted quantifier's minimum. The `i`-flag case
folding was likewise taught the pairs Unicode's simple folding has that Java's case mapping lacks.

**`java.util.regex`** takes the patterns in the table above, regardless of configuration. The `u`
and `v` flags are *about* code points — an astral character is one atom, a class range may cross the
surrogate boundary, folding is full Unicode — and Joni's code-unit model cannot express that; the JDK
engine is code-point based, and gets `UNICODE_CASE` when `i` is present. The syntax cases are simpler
still: the scanner emits JDK-compatible syntax for a construct Joni's grammar does not have.
`-Dnashorn.regexp.impl=jdk` opts everything into it.

## ES2018 and later additions

The ES2018 RegExp features are carried by the same rewrite-and-delegate design; both backends accept
the syntax natively, so the scanner mostly passes it through while enforcing the ES rules and
threading a little extra state:

- **`s` (dotAll)** — a new flag; `.` matches line terminators too. Mapped to `Pattern.DOTALL` on the
  JDK backend and Joni's dot-all option, and reported in `flags`/`getFlagString`.
- **Named groups** — `(?<name>…)`, `\k<name>`, the `.groups` object on a match, and `$<name>` in
  `String.prototype.replace`. The scanner passes the group syntax through, collects a
  `name → index` map (rejecting duplicate names), and threads it via `RegExp.getGroupNames()` set by
  each backend; `NativeRegExp` builds the null-prototype `.groups` object from it. A **forward**
  named backreference — to a group that appears later — is emitted as a numbered reference mirroring
  the numeric-escape rule, since a forward reference can never have matched.
- **Lookbehind** — `(?<=…)` and `(?<!…)` pass straight to the backend.
- **Unicode property escapes** — `\p{…}`/`\P{…}` under `/u` (so always the JDK backend). A
  translation layer maps the ES canonical property names and aliases (`General_Category`/`gc`,
  `Script`/`sc`, and the ES binary-property list) to what `java.util.regex` accepts, matching names
  exactly rather than loosely.

Three later editions extend the same design:

- **ES2022's `d` flag** (`hasIndices`) records each group's start and end offsets and exposes them
  as `.indices`, built lazily from the match's own region rather than on every match.
- **ES2024's `v` flag** (`unicodeSets`) brings the class-set grammar — nested classes, `&&` and
  `--`, string literals in `\q{…}` — which the scanner transcribes to the JDK engine's syntax. A
  `\q{…}` holding a multi-character string, and `\p{…}` of *strings* (RGI_Emoji and its kin), are
  engine limits and held out of the conformance slice.
- **ES2025's pattern modifiers** `(?ims-ims:…)` route to the JDK engine (Joni has no inline flags),
  and **duplicate named capture groups** — the same name on groups in disjoint alternatives — map a
  name to the list of indices, with `.groups`, `.indices` and `$<name>` picking whichever group
  actually participated.

Two limits are the substrate's, not the design's, and are documented as such (both in
`doc/CONFORMANCE.md` and the conformance selector, which holds their tests out of the slice rather
than counting them as failures): a set of patterns neither backend can compile with ES semantics —
**unbounded lookbehind** (the JDK's is bounded and left-to-right), open-group backreferences, a
subclassable `exec` — and the `\p{…}` **binary properties** and `Script_Extensions`, which
`java.util.regex` does not expose without a bundled Unicode Character Database.

## Caching

Compiled patterns are cached in a lock-free `ConcurrentHashMap` under a structured key — *pattern,
flags, and the Annex B setting* — with soft values, cleared wholesale past 4096 entries. Annex B
changes what a pattern means, so two engines that disagree about it must not share a compilation.
`RegExp` literals additionally compile once per call site; `new RegExp(...)` goes through the cache
each time.

The cache used to be a synchronized `WeakHashMap` keyed on a freshly concatenated string that
nothing else referenced — so an entry was collectable the moment it was put, and the cache mostly
missed while every lookup still took a JVM-wide monitor. Fixing that is one of the
[measured wins](performance.md) of the 2026 performance work.

## The object model above

`NativeRegExp` holds the compiled `RegExp` plus the per-instance `lastIndex`. `exec`, and the
`String.prototype` methods that delegate through the `@@match`/`@@replace`/`@@search`/`@@split`
symbol protocol, drive the backend's matcher and build result arrays; `RegExp.prototype.compile`
(Annex B) swaps a compiled pattern into an existing instance. The legacy static properties —
`RegExp.$1`, `lastMatch` and their kin — are fed from the last successful match, recorded per
realm.

## Choosing and diagnosing

Stick with Joni unless you have a reason: it is the tuned path for the non-`u` majority. When a
pattern behaves unexpectedly, `-Dnashorn.regexp.impl=jdk` is a quick differential — if behaviour
changes, the pattern is in territory where the backends' semantics (or this fork's Joni
modifications) matter, which narrows the hunt immediately.
