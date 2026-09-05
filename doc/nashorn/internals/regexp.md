# Regular expressions

Nashorn does not implement a regexp engine of its own — it rewrites ECMAScript patterns into a form
a Java engine accepts, and runs them on one of two backends.

## The pipeline

```text
pattern source ──► RegExpScanner (rewrite + validate) ──► Joni  (default)
                                                      └─► java.util.regex  (u-flagged patterns, or opt-in)
```

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

**`java.util.regex`** handles every pattern with the `u` flag, regardless of configuration: the
unicode flag is *about* code points — an astral character is one atom, a class range may cross the
surrogate boundary, folding is full Unicode — and Joni's code-unit model cannot express that. The
JDK engine is code-point based, so `/u` patterns are compiled there, with `UNICODE_CASE` when `i`
is present. `-Dnashorn.regexp.impl=jdk` opts everything into it.

## ES2018 additions

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

Two limits are the substrate's, not the design's, and are documented as such (both in
`doc/CONFORMANCE.md` and the conformance selector, which holds their tests out of the slice rather
than counting them as failures): a set of patterns neither backend can compile with ES semantics —
**unbounded lookbehind** (the JDK's is bounded and left-to-right), open-group backreferences, a
subclassable `exec` — and the `\p{…}` **binary properties** and `Script_Extensions`, which
`java.util.regex` does not expose without a bundled Unicode Character Database.

## Caching

Compiled patterns are cached in a weak map keyed on *pattern + flags + the Annex B setting* — the
flag changes what a pattern means, so two engines that disagree about it must not share a
compilation. `RegExp` literals additionally compile once per call site; `new RegExp(...)` goes
through the cache each time.

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
