ECMAScript 2017 conformance
===========================

This engine implements [ECMAScript 2017](https://262.ecma-international.org/8.0/)
(ECMA-262, 8th edition) and is measured against a pinned commit of
[tc39/test262](https://github.com/tc39/test262). The ES2017 slice of that suite
is selected at runtime by `Test262Selector`, and **every selected execution
passes**: `core/src/test/resources/test262-expectations.txt` is empty, and the
run fails on an unexpected pass as well as an unexpected failure, so conformance
can only move forwards.

```
mvn -Pfetch-externals -pl core generate-test-resources    # once
mvn -Ptest262 -DskipTests verify
```

    test262: 48970 executions from src/test/scripts/external/test262-main, in 12 processes
    failing: 0   expected to fail: 0

What is not measured, and why
-----------------------------

The suite holds 53,872 test files and tracks the current draft specification, so
most of it is about editions this engine does not claim. Four things are
excluded by decision; everything else outside the slice is simply later than
ECMAScript 2017.

| Excluded | Files | Reason |
| --- | --- | --- |
| `annexB/` | 1,086 | Normative-optional, and written for browser hosts |
| `tail-call-optimization` | 35 | Proper tail calls, excluded by decision |
| `intl402/` | 3,357 | ECMA-402, a separate standard |
| `staging/` | 1,491 | Not normative |

Everything else the selector leaves out is a later edition: the
`async-generator` directories (1,054 files, ECMAScript 2018) and every test whose
`features:` tag names something introduced after ES2017 - object rest and
spread, async iteration, lookbehind and named groups, optional catch binding,
`BigInt`, optional chaining, class fields, and the rest. Those are not failures;
they are outside the target. Most would fail if run, because the features are
not implemented.

Annex B, measured
-----------------

Annex B is excluded by decision rather than by capability, and the decision is
not all-or-nothing: **336 of the 1,086 files already pass**, because the parts of
Annex B that are not about browser semantics were implemented long ago -
`escape`/`unescape` (35 files), `String.prototype.substr` (14) and
`trimLeft`/`trimRight` (8), `Date.prototype.getYear`/`setYear` (20),
`RegExp.prototype.compile` (19) and the RegExp legacy statics `input`,
`lastMatch`, `leftContext`, `rightContext` and `lastParen` (20).

The 750 files that fail are one decision and a small tail. Lifting the directory
exclusion and running the slice gives:

| Cluster | Files | What it is |
| --- | --- | --- |
| B.3.3 function-in-block hoisting | 635 | direct eval 253, indirect eval 133, function code 126, global code 123 |
| `String.prototype` HTML methods | 82 | `anchor`, `big`, `blink`, `bold`, `fixed`, `fontcolor`, `fontsize`, `italics`, `link`, `small`, `strike`, `sub`, `sup` (B.2.3) - not implemented |
| HTML-like comments | 13 | `<!--` and `-->` as comment syntax (B.1.1), including through `new Function` |
| `CallExpression` as an assignment target | 7 | `f() = 1`, `f()++`, `for (f() in o)` |
| Legacy regular expression syntax | 5 | Octal escapes, `[a-]`-style class ranges, quantified assertions (B.1.4) |
| `RegExp.prototype.compile` | 4 | The `compile(regexp)` overload only |
| `Date` | 2 | `toGMTString` must be the same function object as `toUTCString`; one `setYear` coercion order |
| Labelled function declaration | 1 | `l: function f(){}` |

### Why B.3.3 is a decision and not a gap

B.3.3 says that in sloppy code a host also creates a *var*-scoped binding for a
block-level function's name in the enclosing function or script, initialised to
`undefined` on entry and assigned the function object when the block's
declaration is evaluated. The name leaks out of the block, carrying a value only
if the block ran.

```js
function f() {
    { function g() { return "inner"; } }
    return g();          // Annex B: "inner".  Here: ReferenceError
}
```

This fork made the opposite choice while implementing ES2015, and the change log
records it: a function declaration inside a block is scoped to that block and is
not visible after it ends, which is why `--function-statement-error` and
`--function-statement-warning` were deleted. Implementing B.3.3 would take source
that throws today and make it work.

The extension is also conditional - the extra binding is suppressed where it
would collide with a lexical declaration, with certain parameter or catch
parameter names, or where an early error would result, and it never applies in
strict mode. The 635 tests enumerate that matrix across the four places bindings
are instantiated, and 146 of the B.3.3 files already pass here precisely because
they assert the extension is *not* honoured.

Implementing it would mean a second hoisting pass in symbol assignment and
lowering, on top of the block scoping the ES2015 work rebuilt, observable only
for code the modern specification says is block-local.

### Tail calls

The 35 tail-call tests are the whole of that exclusion, and nothing else depends
on it. Putting `tail-call-optimization` back in scope and running
`/language/statements/` gives 18 failures out of 8,232 executions, every one of
them a `tco-*` test. Proper tail calls are normative from ES2015 on, but a
trampoline in tail position costs every call in a tail-shaped function; no engine
but JavaScriptCore ships them.

Reproducing these numbers
-------------------------

The exclusions live in `Test262Selector`: `EXCLUDED_DIRS` for the three
directories, and the absence of `tail-call-optimization` from `FEATURES` for the
fourth. Removing an entry puts that work back in scope, and
`-Dnashorn.test262.include=` narrows the run:

```
mvn -pl core verify -Ptest262 -DskipTests -Dnashorn.test262.include=/annexB/
```

Failures are listed in `core/target/test262-failures.txt`. Put the selector back
afterwards - and rebuild the test classes, or the next run will quietly keep the
wider scope.
