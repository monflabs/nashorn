ECMAScript 2017 conformance
===========================

This engine implements [ECMAScript 2017](https://262.ecma-international.org/8.0/)
(ECMA-262, 8th edition) together with its **Annex B**, and is measured against a
pinned commit of [tc39/test262](https://github.com/tc39/test262). The slice is
selected at runtime by `Test262Selector`, and of its 50,339 executions **eight
fail**, all of one shape and named in
`core/src/test/resources/test262-expectations.txt` with the reason. The run fails
on an unexpected pass as well as an unexpected failure, so conformance can only
move forwards.

Annex B is normative-optional and lives behind `--annexB`, which is on by
default. An engine built with `--annexB=false` has none of it.

```
mvn -Pfetch-externals -pl core generate-test-resources    # once
mvn -Ptest262 -DskipTests verify
```

    test262: 50339 executions from src/test/scripts/external/test262-main, in 12 processes
    failing: 8   expected to fail: 8

What is not measured, and why
-----------------------------

The suite holds 53,872 test files and tracks the current draft specification, so
most of it is about editions this engine does not claim. Three things are
excluded by decision, and one proposal filed inside the Annex B directory;
everything else outside the slice is simply later than ECMAScript 2017.

| Excluded | Files | Reason | Revisit? |
| --- | --- | --- | --- |
| `tail-call-optimization` | 35 | Proper tail calls | **Never.** A settled decision, not a task - see below |
| `intl402/` | 3,357 | ECMA-402, a separate standard | Open, but it is a different standard and a different body of work |
| `staging/` | 1,491 | Proposals and unreviewed tests, not part of any edition | **Never.** This engine targets the approved standard - see below |
| `legacy-regexp` tagged | 24 | `RegExp.$1`, `lastMatch` and their kin - a separate Stage 3 proposal, `esid: pending`, filed under `annexB/` by the suite but not part of Annex B | **Never**, on staging's reasoning |

`annexB/` is **no longer excluded**: Annex B is implemented, behind `--annexB`, and its
directory is measured with the rest. See below.

Everything else the selector leaves out is a later edition: the
`async-generator` directories (1,054 files, ECMAScript 2018) and every test whose
`features:` tag names something introduced after ES2017 - object rest and
spread, async iteration, lookbehind and named groups, optional catch binding,
`BigInt`, optional chaining, class fields, and the rest. Those are not failures;
they are outside the target. Most would fail if run, because the features are
not implemented.

ECMA-262 Annex B, behind `--annexB`
-----------------------------------

Annex B is normative-optional: a host may implement it or not, and this one does,
**by default**. `--annexB=false` gives an engine with none of it. The flag is per engine, fixed when
the engine is built, and it covers the whole of Annex B - the built-ins it adds, the syntax it
legalises, and the scoping it changes.

Of the 1,086 files in `annexB/`, **1,078 pass**. What the flag turns on:

| Clause | What it is |
| --- | --- |
| B.1.1 | HTML-like comments: `<!--` anywhere, `-->` at the head of a line. Not in modules |
| B.1.4 | The legacy pattern grammar: octal escapes, a dash beside a class escape, a quantified lookahead |
| B.2.1 | `escape` and `unescape` |
| B.2.2 | `__proto__`, `__defineGetter__`, `__defineSetter__`, `__lookupGetter__`, `__lookupSetter__` |
| B.2.3 | `String.prototype.substr` and the thirteen markup helpers - `anchor`, `big`, `blink`, `bold`, `fixed`, `fontcolor`, `fontsize`, `italics`, `link`, `small`, `strike`, `sub`, `sup` |
| B.2.4 | `Date.prototype.getYear`, `setYear` and `toGMTString`, the last being the same function object as `toUTCString` |
| B.2.5 | `RegExp.prototype.compile` |
| B.3.2, B.3.4 | A function declaration under a label, and as a clause of an `if` |
| B.3.3 | A function declared in a block also binds the name in the variable environment |
| B.3.4 | A call expression as an assignment target: a runtime `ReferenceError` where the specification proper has an early `SyntaxError` |
| B.3.5 | A `var` may take a simple catch parameter's name |
| B.3.6 | `for (var a = 0 in o)` |

Two things the flag does not cover, and one it cannot:

* **B.3.5 is not gated.** A `var` may take a simple catch parameter's name whether the flag is on or
  off. What allows it in this engine is also what makes an ordinary catch parameter visible at all,
  and separating the two is not worth what it would cost.
* **The RegExp legacy statics are not Annex B.** `RegExp.$1`, `input`, `lastMatch`, `leftContext`
  and the rest are a Stage 3 proposal of their own - test262 tags them `legacy-regexp` and gives them
  `esid: pending` - and they are out of scope for the reason `staging` is. They remain present and
  unflagged, as they have always been.
* **`[[IsHTMLDDA]]` (B.3.7) cannot be implemented.** 35 files are tagged `IsHTMLDDA`; the object can
  only come from a web host, and the feature rule drops them.

Eight files still fail, all of one shape: an indirect eval whose block-level function declaration has
to update a `var` of that name the global already had. They pass on their own and under the runner
when a neighbouring file is added or removed; what decides it is whether the outer program's `var`
reached the global object directly or through the merge of its scope, which leaves the eval's binding
aliased to it or orphaned beside it. That is this engine's eval scope merging rather than anything
Annex B asks for.

### The two halves are tested

`core/src/test/scripts/basic/annexB-on.js` and `annexB-off.js` assert every part of the list above,
present and absent; `AnnexBTest` builds two engines that disagree about the flag in one process and
checks that they stay apart, which is the risk in taking built-ins off a shape that nasgen wrote at
build time.

### What it costs

Annex B's built-ins are seventeen more properties on two prototypes, and every global pays for them:
`startup.50globals` measured 6.6% slower with them than without, in nine interleaved pairs. That is
inside the metric's band and the gate passes with it.

Tail calls: never
-----------------

**Proper tail calls are not to be implemented in this engine, now or later.**
This is a settled decision rather than an item of work, and anyone reading the
35 failing tests as a to-do list should stop here.

The 35 tail-call tests are the whole of that exclusion, and nothing else depends
on it. Putting `tail-call-optimization` back in scope and running
`/language/statements/` gives 18 failures out of 8,232 executions, every one of
them a `tco-*` test.

Be clear about the standing of the feature, because the decision is a deliberate
divergence and should not be dressed up as anything else. Proper tail calls have
been normative since ES2015 and remain in the specification: test262 still lists
`tail-call-optimization` among its standard language features, unmarked and
undeprecated. The reasons not to implement them are practical and permanent:

* **The cost lands on every call, not on recursive ones.** A tail position
  cannot be recognised at runtime, so honouring the rule means a trampoline in
  every tail-shaped function - which is most of them. This engine compiles to
  JVM bytecode and links call sites with `invokedynamic`; there is no cheap way
  to discard the caller's frame, and the performance gate exists precisely to
  refuse changes that tax every call for the benefit of a few.
* **The ecosystem did not follow the specification.** JavaScriptCore is the only
  engine that ships proper tail calls. V8 implemented them and withdrew them;
  SpiderMonkey and ChakraCore declined on security and compatibility grounds.
  TC39 spent years on a replacement - explicit syntax, the "syntactic tail
  calls" proposal - which never advanced. Code that depends on the guarantee is
  therefore already unportable, and nothing here would make it portable.
* **Nothing else depends on it.** The exclusion is 35 files that cite no other
  behaviour; leaving it out costs no conformance elsewhere.

If a future maintainer disagrees, the change to make is not a small one: it is a
calling convention, and it should be argued on its merits against the gate, not
adopted because a row in a table looked unfinished.

Staging: never
--------------

**The `staging/` directory is not a conformance target and will not become
one.** This engine implements the latest *approved* edition of ECMA-262 - today
that is the 8th, ECMAScript 2017 - and staging tests things that no edition has
approved.

test262's own contributing guide is explicit about what the directory is for:
getting tests "running across more than one implementation as early as
possible", covering "a Stage 3 TC39 proposal, or a normative pull request".
Tests there are held to lower standards than the main suite - they "are not
required to be split up into one test per file, or to conform to any particular
style as long as they are runnable", and mechanically converted implementation
tests are welcome. They "do not count towards the test262 coverage requirement
for a TC39 proposal to reach Stage 4", and are meant to move out of staging once
the feature settles.

So a staging test measures agreement with a proposal that may still change, be
renamed, or be abandoned. Passing it would say nothing about conformance, and
chasing it would mean implementing semantics that the committee has not
ratified. When a proposal is approved into an edition, its tests leave staging
for the main suite, and that is the point at which they become this engine's
business - by moving the edition target forwards, deliberately, not by widening
the selector.

Reproducing these numbers
-------------------------

The exclusions live in `Test262Selector`: `EXCLUDED_DIRS` for the two directories,
and `FEATURES`, which a test's tags must all appear in - the absence of
`tail-call-optimization` and `legacy-regexp` from it is what leaves those out.
Removing an entry puts that work back in scope, and
`-Dnashorn.test262.include=` narrows the run:

```
mvn -pl core verify -Ptest262 -DskipTests -Dnashorn.test262.include=/annexB/
```

The Annex B slice runs with the default engine, which has the flag on. The off
state is not a second suite run - it is `annexB-off.js` and `AnnexBTest`, which
name every part of it.

Failures are listed in `core/target/test262-failures.txt`. Put the selector back
afterwards - and rebuild the test classes, or the next run will quietly keep the
wider scope.
