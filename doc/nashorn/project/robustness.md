# Robustness testing

The conformance suites say whether the engine does what the specification asks of programs
written to test it. They say little about what happens to a program nobody wrote on purpose:
an optimistic guess that turns out wrong in the middle of an expression, a deoptimization
inside a `finally`, an array literal resumed in a rest-of method, malformed input to the
parser. Those are what the robustness work looks for, with four layers of tests, two in this
repository and two outside it.

| Layer | Where | What it guards |
| --- | --- | --- |
| The script and Java suites | `mvn -pl core test` | Every fixed bug stays fixed, in both type modes |
| test262 | `mvn -pl core -Ptest262 verify` | Conformance, in both type modes |
| The JSC stress corpus | [JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance) | Crashes and failures in the corners a JIT gets wrong |
| Differential fuzzing | JavascriptPerformance | Wrong results, on programs whose output the specification fully determines |
| Jazzer | JavascriptPerformance | Java exceptions escaping the parser, compiler, regexps and `JSON.parse` |

## In this repository

### The suites, twice

`mvn -pl core test` runs the script tests and the TestNG tests once with optimistic types (the
default) and once with `--optimistic-types=false` (see [Building](building.md)). A change to code
generation can pass one mode and fail the other, and most of what the fuzzer has found lives in
the optimistic pipeline only, so both executions must pass: 1823 tests, no failures.

`core/src/test/scripts/basic/es6/robustness-fixes.js` holds the regressions the harnesses found:
one short case per bug, each printing what V8 prints, diffed against
`robustness-fixes.js.EXPECTED`. A case is written to fail on the build before its fix. A
deoptimization bug often shows only on the second or third run of a function, after a guess
has been made and invalidated, so many cases call a function in a loop or let a global change
type first. The suite runs with `-ea -esa`, so a case also catches compiler assertions.

### test262, twice

`mvn -pl core -Ptest262 verify` runs the ES2026 slice with optimistic types, against
`test262-expectations.txt` (no failures); `-Dnashorn.test262.optimistic=false` runs it
pessimistically, against `test262-expectations-pessimistic.txt` (the 8 Annex B cases). Both
fail on an unexpected pass as well as an unexpected failure. See
[Conformance](../reference/conformance.md). The `S7.8.5` regexp-literal tests can time out on
a loaded machine; rerun them alone before treating that as a regression.

## The harnesses of JavascriptPerformance

[JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance), the project that
benchmarks this fork against the other JVM engines, also runs three robustness harnesses.
Its README is the reference for their options; what follows is what they do and how their
findings are read. They run `NASHORN_MONFLABS` from the `nashorn-core` installed in `~/.m2`,
or from any jar put first on the classpath:

```
cd ../JavascriptPerformance
mvn package -DskipTests
tools/robustness/fetch-corpora.sh            # once: a sparse clone of WebKit's JSTests
R="java -cp ../nashorn/core/target/nashorn-core-2026.0.0.jar:target/javascript-performance-1.0.0-SNAPSHOT-all.jar \
   org.monflabs.nashorn.performance.robustness.Robustness"
$R corpus --engines=NASHORN_MONFLABS
$R fuzz --engines=NASHORN_MONFLABS --seed=40000 --count=500 --max-reduce=40 --out=/tmp/fuzz-40000
tools/robustness/jazzer.sh NashornCompile 600
```

**V8 is the reference** (through Javet): a difference counts only when V8 disagrees with
Nashorn. Each script runs in a fresh realm, in child JVMs that hold 100 files each, so a hang
or a dead JVM costs one file and not the run.

### The JSC stress corpus

JavaScriptCore's `JSTests/stress` is about 6,000 regression tests, many reduced from JIT
crashes: integer overflow, holes, prototype changes in a loop, deep recursion, huge strings.
About 4,300 run here. The others need `jsc` shell functions with real semantics (`$vm`,
`createGlobalObject`, …), modules, or JSC feature flags. A test checks itself, so completing is
passing. A Java exception escaping the engine, a `StackOverflowError` or `OutOfMemoryError`
reached as a Java error, or a throw where V8 completes is a finding. A test V8 fails as well is
JSC-specific and does not count.

### Differential fuzzing

The generator writes programs of 10 to 30 top-level statements, each in a `try` of its own:
classes with fields, private members, accessors and inheritance, generators, closures,
destructuring, spread, labeled loops, `switch`, getters, proxies, BigInt, regexps, iterator
helpers, promises and about fifty built-in methods. Everything they print goes through a
`__p` that depends on nothing an engine may choose, and implementation-defined corners
(`Date`, `Math.random`, locales, transcendental `Math`, error messages, inconsistent
comparators) are never generated. A step budget makes every program end the same way on
every engine. So any difference from V8 is a bug of one of the two engines, and the harness
reduces it, statement by statement, to a program in `<out>/reduced/` that runs as it is in
the Nashorn shell or in Node. Its header records what each engine printed.

The same seed always generates the same program. The campaign that produced the
"Robustness fixes" entry of the changelog ran fifteen batches of 500 programs per round,
seeds 40000, 50000, … 180000, against a copy of the jar frozen at the start of the round so
a rebuild could not change the engine under it. A round was repeated after every batch of
fixes, until one found nothing new: seventeen rounds, nine batches of fixes.

### Jazzer

`tools/robustness/jazzer.sh <Target> [seconds]` runs coverage-guided fuzzing over the engine's
own classes, seeded with the JSC tests and a dictionary of JavaScript tokens:

| Target | What it does with the fuzzer's bytes |
| --- | --- |
| `NashornCompile` | Parses and compiles them as a script |
| `NashornRegExp` | Compiles a pattern with flags and runs it on a short subject |
| `NashornJson` | Parses them as JSON and checks that the value survives a round trip |

A JavaScript error is an expected outcome; a Java exception that escapes is a finding. The
corpus Jazzer grows is kept under `target/robustness/jazzer/<Target>/corpus`, so the next run
continues from it.

## Reading a finding

Each reduced program is checked before it becomes a fix:

1. **Optimistic or not.** Run it with and without `--optimistic-types=false`. A difference
   that disappears pessimistically is in the optimistic pipeline (a guess without its guard, a
   continuation that restores the wrong value, a type persisted by one compilation and not
   the next); `--log=recompile` shows the deoptimizations that lead to it.
2. **Against the other engines.** Run it on the Monflabs Rhino fork, compiled and interpreted,
   and on Node. When V8 is the odd one out, the specification decides, and V8 has been wrong.
3. **Inherited or not.** Compare with OpenJDK Nashorn 15.7, or read the code on the
   `openjdk-original` branch. A bug inherited from upstream is fixed all the same and is marked
   so in the changelog.

A fixed bug gets a case in `robustness-fixes.js` that fails before the fix, a sentence in the
`CHANGELOG.md` entry, and, when Rhino has the same bug, a fix there too (its counterpart is
`RobustnessFixesTest`). Then the whole chain runs again: both executions of the suite, test262
in both modes, and the fuzz batches.

### Differences that are not bugs

A few kinds of differences come back in every round and are settled:

| Kind | Example | Why it is not a bug |
| --- | --- | --- |
| Property order after a spread | `Object.keys({...1, get y() {}, length() {}})` is `length,y` in V8 | V8 reorders the keys of a literal that starts with a spread; the specification, Nashorn and Rhino give `y,length` |
| The last digit of a large double | `2.7356347530552557e+28` in V8, `…553e+28` here | The shortest-digits printing of a value computed through a different but equally exact sequence of operations |
| `RangeError` for `TypeError` at an engine limit | A string or array past the engine's maximum length | Which limit is reached first is the engine's own; both are errors |
| A throw V8 shares | The program throws in both engines | Not a difference |

What a finding was, and the fix, are recorded in the "Robustness fixes" entry of the
[changelog](https://github.com/monflabs/nashorn/blob/main/CHANGELOG.md), batch by batch.
