ECMAScript 2021 conformance
===========================

This engine implements [ECMAScript 2021](https://262.ecma-international.org/12.0/)
(ECMA-262, 12th edition) together with its **Annex B**, and is measured against a
pinned commit of [tc39/test262](https://github.com/tc39/test262). The slice is
selected at runtime by `Test262Selector`, and of its 74,069 executions
**92 fail**, named in `core/src/test/resources/test262-expectations.txt`. The run
fails on an unexpected pass as well as an unexpected failure, so conformance can
only move forwards. (All ten ES2022 additions are now in - `.at`, `Object.hasOwn`,
`Error` `cause`, the RegExp `d` flag, class fields, private class members - fields,
methods and accessors, their static forms, and the ergonomic `#x in obj` brand
check - and **top-level await** - so the slice now selects the ES2022 feature tags.
The edition-level documentation catches up to ES2022 separately.)

Of the 92, **8** are the one carried-over Annex B shape: an indirect `eval` whose
block-level function declaration must update a `var` the global already had,
rooted in how the engine merges eval scopes (see below). The other **84** fall in
four ES2022 groups, and every one is a corner rather than a hole:

- **The direct-`eval` interaction with the new lexical features** (the largest
  group). A private-name reference is resolved lexically - `#x` compiles to a read
  of a synthetic `const` the class body binds - so it is correctly visible to a
  direct `eval` nested in a class method; but the engine does not carry the
  enclosing *private environment* or the field-initializer's no-`arguments`
  context into the eval's own parse. So an `eval` naming `arguments` in an
  initializer, or one naming a private member the surrounding class did not
  declare, reads as a runtime error rather than the parse-time `SyntaxError` the
  specification asks for. The same shape covers the empty-`#` eval case.
- **Two `#x in obj` grammar corners**: a private-name operand nested as the *right*
  side of `in`, and one used as a `for`-`in` target, are not rejected at parse.
- **Eighteen exhaustive Unicode identifier tests** that spell *thousands* of
  distinct private names in a single class. Each private name binds a `const` of
  its own, and at that scale the class's generated method passes the JVM's 64 KB
  method limit even after the splitter runs - a size a real program never reaches
  (a class of four thousand *public* fields compiles, having no such bindings).
- **Two top-level-await corners**: `new await` at a module's top level is not
  rejected at parse (it is read as the `await` operator), and one asynchronous
  *cycle* settles a module's fulfilment one microtask later than a spec erratum
  requires, swapping the last two entries of an ordering probe. Everything else
  about top-level await conforms - the `await` operator and `for await` at a
  module's top level, the asynchronous evaluation order across a dependency graph,
  dynamic `import()` of a module that awaits, and rejection propagation.

Everything else about class fields and private members - public, static, and
private fields, private methods and accessors and their static forms, field
initializers (with `this`, `super`, the class name, outer lexicals, and a private
name's own `NamedEvaluation`), computed keys, static initializer blocks, the
brand check, the double-installation `TypeError`, invisibility to every form of
reflection, and the initialization order - conforms.

The corners earlier documented here are **fixed**. The ES2020 ones: `Object(1n) & 1`
and its kin now throw the `TypeError` a BigInt-to-number coercion must (the
call-return Java-argument converter no longer takes a BigInt for a Number);
`(a?.b)()` binds `this` to the chain's base; and `JSON.stringify` calls a BigInt's
`toJSON` with the primitive as its receiver. And the last module-instantiation
corner - **a circular module read before its body runs** (`verify-dfs`): a fixture
imports a hoisted function export back from the entry module that is still
evaluating, and the specification makes that binding available because function
declarations are initialised during module *instantiation*, before any module body
runs. The engine now runs a module body in two passes (`ModuleRecord.instantiate`
then `ModuleRecord.evaluate`): the first makes each scope and hoists its function
declarations across the whole graph, so a cyclic dependent finds the export; the
second runs the bodies for real. The same fix made dynamic `import()` a microtask
(it no longer evaluates inline), so it can no longer preempt the depth-first
evaluation order of the static graph it sits in.

Two ES2018 surfaces are limited by the substrate rather than by choice, and their
tests are held out of the slice — not counted as failures — with the reason
recorded in `Test262Selector`: a set of RegExp patterns neither backend can
compile with ES semantics, and the `\p{…}` Unicode binary properties and
`Script_Extensions`. Both are detailed under [ES2018 RegExp](#es2018-regexp-what-the-backends-cannot-do)
below.

Annex B is normative-optional and lives behind `--annexB`, which is on by
default. An engine built with `--annexB=false` has none of it.

```
mvn -Pfetch-externals -pl core generate-test-resources    # once
mvn -Ptest262 -DskipTests verify
```

    test262: 74069 executions from src/test/scripts/external/test262-main, in 12 processes
    failing: 92   expected to fail: 92

What is not measured, and why
-----------------------------

The suite holds 53,872 test files and tracks the current draft specification, so
most of it is about editions this engine does not claim. Three things are
excluded by decision, and one proposal filed inside the Annex B directory;
everything else outside the slice is simply later than ECMAScript 2021.

| Excluded | Files | Reason | Revisit? |
| --- | --- | --- | --- |
| `tail-call-optimization` | 35 | Proper tail calls | **Never.** A settled decision, not a task - see below |
| `intl402/` | 3,357 | ECMA-402, a separate standard | Open, but it is a different standard and a different body of work |
| `staging/` | 1,483 | Proposals and unreviewed tests, not part of any edition | **Never.** This engine targets the approved standard - see below |
| `legacy-regexp` tagged | 24 | `RegExp.$1`, `lastMatch` and their kin - a separate Stage 3 proposal, `esid: pending`, filed under `annexB/` by the suite but not part of Annex B | **Never**, on staging's reasoning |

`annexB/` is **no longer excluded**: Annex B is implemented, behind `--annexB`, and its
directory is measured with the rest. See below. The `async-generator` directories,
excluded at the ES2017 target, are **now in scope**: async iteration is ES2018 and is
implemented. The ES2019 additions are all in scope and pass: `Array.prototype.flat`
and `flatMap` (and their `@@unscopables` entries), `Object.fromEntries`,
`String.prototype.trimStart`/`trimEnd` (with the Annex B `trimLeft`/`trimRight` as the
same function objects), `Symbol.prototype.description` (nullable), optional catch
binding, the guaranteed-stable `Array.prototype.sort`, the JSON superset (raw
U+2028/U+2029 in string literals) and well-formed `JSON.stringify`. The ES2020
additions are in scope too: nullish coalescing (`??`), optional chaining
(`?.`/`?.[]`/`?.()`), `String.prototype.matchAll` and `Symbol.matchAll`,
`export * as ns from`, dynamic `import()`, `import.meta`, `globalThis`,
`Promise.allSettled`, `for`-`in` order, and the whole of **BigInt** - the
primitive and its operators, `BigInt.asIntN`/`asUintN`, the `BigInt64Array`/
`BigUint64Array` typed arrays, the `DataView` big-64 accessors, and `Atomics` over
them. The ES2021 additions are in scope too: `String.prototype.replaceAll`,
`Promise.any` with `AggregateError`, the logical assignment operators
(`&&=`, `||=`, `??=`), numeric separators (`1_000`), and `WeakRef` /
`FinalizationRegistry`. A handful of BigInt corner cases are settled divergences,
named in the expectations file (see below).

Everything else the selector leaves out is a later edition: every test whose
`features:` tag names something introduced after the target - the RegExp `v` flag,
`Array.prototype.findLast`
and the change-array-by-copy methods, and the rest. Those are not failures; they
are outside the target. Most would fail if run, because the features are not
implemented.

Two ES2018 surfaces are in scope but limited by the substrate, so a bounded set of
their tests is held out of the slice with the reason recorded in the selector -
neither a decision like tail calls nor a later edition, but a data or engine limit
this host cannot cross. See [ES2018 RegExp](#es2018-regexp-what-the-backends-cannot-do).

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

ES2018 async iteration
----------------------

Async iteration — `async function*`, `for await…of`, `yield*` delegation over an async or a
sync-wrapped iterable, the async-generator methods on classes and objects, `Symbol.asyncIterator`,
the `%AsyncGeneratorFunction%` constructor, the `%AsyncFromSyncIterator%` adaptor with its exact
`return`/`throw` and poisoned-wrapper semantics, `for await` iterator-close on an abrupt completion,
and `return()`'s AwaitReturn — **passes the suite in full, including the microtask-tick tests**.

Getting the tick ordering exact rested on one rule about the job queue: **only the event-loop thread
drains it.** A generator/async body runs on its own virtual thread, and that worker must never drain
the shared per-realm queue when its script depth returns to zero (it is marked in `JobQueue`, and
`ScriptRuntime.apply` skips the drain for it) — otherwise, while the caller is parked waiting for the
body's first step, the worker would run the caller's already-queued microtasks off the wrong thread,
out of order. With the worker only handing its result back and the event-loop thread scheduling the
continuation as an ordinary microtask, `await`, resume and completion are plain FIFO microtasks, and
the number and order of turns match the specification.

ES2018 RegExp: what the backends cannot do
------------------------------------------

The ES2018 RegExp features — the `s` (dotAll) flag, named capture groups, lookbehind, and Unicode
property escapes — are implemented over the two backends
([Joni by default, `java.util.regex` for `/u`](nashorn/internals/regexp.md)). Most of the conformance
surface passes; two bounded sets of tests are held out of the slice in `Test262Selector`, because
the substrate cannot meet ES semantics there. These are neither settled exclusions like tail calls
nor later editions — they are the limits of the host's regex engines and Unicode data, recorded so
the boundary is honest.

**Patterns neither backend can compile (`REGEXP_ENGINE_LIMITS`, 15 files).** ECMAScript lookbehind
is variable-width and matches right-to-left; `java.util.regex`'s is bounded and left-to-right, so the
adversarial lookbehind corpus (`RegExp/lookBehind/*` — greedy loops, mutual recursion, captures and
back-references *inside* a lookbehind) and the named-group tests that lean on those same shapes
(`named-groups/lookbehind.js`, `named-groups/*-references.js`) cannot be honoured. Two
`named-groups/groups-object-subclass*` tests require a subclassable `exec`, which this object model
does not expose. Joni, the non-`/u` backend, does not offer ES lookbehind or named groups on its
JavaScript syntax at all, so these are `/u`-forced to the JDK and still hit its limit.

**Unicode property escapes needing data the JDK does not carry (`propertyEscapeInScope`).** The
property-escape *syntax* and mechanism are implemented and verified by the hand-written tests in
`built-ins/RegExp/property-escapes/` (loose matching, the grammar extensions, character classes, the
unsupported-property errors). The exhaustive `generated/` trees are held out for two data reasons:

- **General_Category and Script** are answered by `java.util.regex`, but the suite's `generated/`
  data is keyed to a Unicode version newer than the JDK's (JDK 25 is Unicode 16), so their code-point
  lists disagree over the characters that version added — the same situation as the `LATER_UNICODE`
  identifier tables. The engine is correct for its own Unicode; the exhaustive data is simply newer.
- The **~40 binary properties** the JDK does not carry (`Emoji` and its kin, `Dash`, `Math`,
  `Diacritic`, the `Changes_When_*` set, `ID`/`XID_*`, …) and **`Script_Extensions`** would each need
  the Unicode Character Database bundled to answer a code point at a time — data this engine does not
  ship and the JDK does not expose.

Full property-escape support in the exhaustive sense would mean bundling and version-pinning the UCD;
that is a deliberate non-goal, on a par with the regexp-engine limits above rather than a defect to
fix.

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
that is the 10th, ECMAScript 2019 - and staging tests things that no edition has
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
