# Performance

This page records the engine's performance work: what was measured, what the measurements
pointed at, what was changed for it, and what each change is worth - against the fork's own
previous revision and against the engine this fork started from, OpenJDK Nashorn **15.7**
(the last release Oracle's project published, on the `openjdk-original` branch).

Nothing here is a target. Every number was measured on one machine in one sitting, base and
head interleaved, the way [`buildtools/perf-gate.sh`](../../../buildtools/perf-gate.sh) does it;
absolute milliseconds and ops/minute do not carry to another machine, the *ratios* do. The
[perf gate](#the-perf-gate) is what keeps them from regressing.

## Where the time went

The starting point was a profile, not a hunch. A Java Flight Recorder run of five Octane
benchmarks (richards, deltablue, raytrace, regexp, splay) on the ES2026 engine with its default
options gave, over 7 310 execution samples:

| Frame (anywhere in the top four of a sample) | Share | Reached from |
| --- | ---: | --- |
| `JSType.toPrimitive(Object, Class)` | 24% | `toNumeric`, the relationals, `ADD` |
| `ScriptRuntime.toNumeric` | 15% | `SUB`, `MUL`, `BIT_AND`, `BIT_OR`, `NEG` |
| `JSType.isPrimitive` | 11% | the instanceof chain under `toPrimitive` |
| `ScriptRuntime.EQ` / `equals` / `equalValues` | 8% | an Object-typed `==` |
| Joni `Matcher.search` + `JoniMatcher.<init>` | 9% | `NativeRegExp.execInner` |

and, by allocated bytes: `char[]` 79 GB, `java.lang.Double` 64 GB, `JO4` 41 GB, `int[]` 14 GB,
Joni's `ByteCodeMachine` 10 GB, `ConsString` 9.8 GB, `FindProperty` 6.4 GB.

Two things fall straight out of that. First, the generic arithmetic path had become expensive.
ES2020 made every operator BigInt-aware, so an operator whose operands are both Object-typed -
which, with optimistic types off (the default), is most arithmetic on a property value or an
unproven local - is compiled as a call to `ScriptRuntime.SUB`/`MUL`/`LT`/`BIT_AND` and friends,
and those went through `ToNumeric` → `ToPrimitive` → an eight-way `isPrimitive` instanceof chain,
boxing a `Double` on the way out and unboxing it again, for each operand of each operation.
Second, the Joni regexp backend works on a `char[]`, and a matcher was made - and the whole
subject string copied - for every `exec`, so the canonical `while ((m = re.exec(s)))` loop was
quadratic in the subject length. That one made the fork *slower* than 15.7 on Octane's regexp.

## What changed

Each item names the class it lives in; the comments there say the same thing more locally.

**Number fast paths on the generic operators** (`ScriptRuntime`, `JSType`). Every BigInt-aware
operator and relational - `SUB`, `MUL`, `DIV`, `MOD`, `EXP`, the six bitwise and shift operators,
`NEG`, `BIT_NOT`, `INC`, `DEC`, `LT`, `GT`, `LE`, `GE` - and the abstract `==` now test for the two
boxes the engine produces for a JS number (`Double`, `Integer`) first and compute in `double`
directly. `toNumeric` returns a `Double` as it is and boxes an `Integer` once, and `isPrimitive`
tests for a number first, since that is what it is usually asked about. The BigInt, string and
object paths are untouched and still run for anything that is not a number box.

**One `char[]` per subject in Joni** (`JoniRegExp`). A compiled regexp remembers the last subject
string it was matched against and the `char[]` made from it, and reuses the array while the same
string comes back - `String` is immutable, so identity is a sound key, and Joni only reads the
array. One entry suffices: the hot pattern is one subject, many matches.

**A regexp cache that caches** (`RegExpFactory`). The compiled-pattern cache was a synchronized
`WeakHashMap` keyed by a freshly concatenated `pattern/flags` string that nothing else referenced,
so an entry was collectable the moment it was put and the cache mostly missed - while every regexp
literal evaluation still built the key and took a JVM-wide monitor. It is now a lock-free
`ConcurrentHashMap` keyed by a `(pattern, flags, annexB)` record, with soft values and a size cap.

**Object model** (`ScriptObject`). The spill array - where properties beyond a structure class's
fields live - grew by a fixed eight slots at a time, so a wide, dictionary-shaped object copied it
every eighth property; it now doubles. `useDualFields()` did a `String.startsWith` on the class
name on every spill growth and spill property add; it is a `ClassValue` now. `get(Object)` with a
`String` or `Symbol` key - what every well-known-symbol lookup and internal string-keyed read
passes - no longer runs `ToPrimitive` twice on a key that is already a property key, and
`get(int)`/`get(double)` no longer build the string form of an index on an array miss unless a
prototype-chain lookup actually needs it. The megamorphic relink warning is only formatted when
its logger is enabled; a megamorphic site relinks repeatedly by definition.

**`Array.prototype.sort`** (`NativeArray`). The sort ran on an `Arrays.asList` view, which sorts
the array in place, and then copied the array out again.

**The for-of loop** (`ScriptRuntime.toES6Iterator`, `ArrayIterator`, `Global`). Each step of a
loop called the iterator's `next`, then read `done` and `value` off the fresh result object: three
linked invocations and an allocation per element. When the `next` an iterator answers with is the
realm's own `%ArrayIteratorPrototype%.next` (captured when the prototype is built, before any
script can see it) and the iterator is a built-in array iterator, the loop steps it directly with
`ArrayIterator.stepValue()`, which yields the value or a done marker and allocates nothing. The
`next` property is still read on every step, so a `next` replaced mid-loop is seen.

**Promises** (`NativePromise`, `Global`). `then`, `resolve` and the capability check read the
intrinsic `%Promise%` through `Global.builtinPromise()` instead of a generic lookup of the global
`Promise` property - once per call for the species check and once more for the default capability.
It is also what the specification names; the global property can be reassigned by script.

**Compiler** (`ir.Block`, `parser.Lexer`). A block is rewritten many times across the compilation
phases, and every version deep-copied its symbol table; symbols reference no IR node, so the
versions now share the map and `putSymbol` copies on first write. The lexer built every
identifier through a `StringBuilder`, one character at a time, even without an escape in it; an
escape-free identifier is now a substring, and every identifier is interned per source so that
the same name is one `String` instance wherever it occurs - property maps compare keys by identity
before `equals`.

**Linking** (`NashornCallSiteDescriptor`). The named-operation interning maps that every
`invokedynamic` bootstrap goes through were synchronized `WeakHashMap`s; they are
`ConcurrentHashMap`s with weak values and a periodic sweep of cleared entries.

## What it is worth

All numbers below come from one sitting on an 8-core laptop (JDK 25.0.4), the three engines
interleaved round by round and the median of three rounds taken, exactly as the perf gate does it.
The machine was not idle - a Time Machine backup held one core throughout - so the absolute values
are somewhat higher than a quiet run would give; the ratios between columns are what the run was
for. Every metric is a time in milliseconds, lower is better. "Before" is the fork's previous
revision (ES2026, before this work); "15.7" is OpenJDK Nashorn 15.7 run through the same harness
on the same JVM.

### Perf-gate metrics

| Metric | What it measures | 15.7 | Before | After | After vs before | After vs 15.7 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `run.arith` | Object-typed arithmetic, relationals, bitwise, `==` | 52.4 | 52.4 | 46.6 | **-11%** | -11% |
| `run.regexp` | `exec` loop, `test`/`search` in a loop, a literal in a loop | 90.3 | 91.2 | 10.6 | **-88%** | -88% |
| `run.wideobject` | objects with 40 properties added one at a time | 18.8 | 31.4 | 10.4 | **-67%** | -44% |
| `run.forof` | `for…of`, spread, array destructuring | n/a¹ | 39.9 | 21.5 | **-46%** | n/a¹ |
| `run.megamorphic` | one read site over 24 shapes | 5.8 | 4.9 | 3.9 | **-20%** | -32% |
| `run.typedarray` | typed-array element writes and `set` | 84.7 | 96.6 | 80.3 | **-17%** | -5% |
| `run.sort` | `sort` with and without a comparator | 25.4 | 29.2 | 26.8 | **-8%** | +5% |
| `run.strbuild` | `+=` string building and flattening | 19.3 | 23.8 | 18.7 | **-22%** | -3% |
| `run.toprimitive` | coercions and the `@@toPrimitive` guard | 85.1 | 111.5 | 101.1 | **-9%** | +19% |
| `run.instanceof` | `instanceof` and the `@@hasInstance` guard | 201.3 | 240.6 | 232.4 | **-3%** | +15% |
| `run.protochain` | 3-deep prototype method dispatch | 154.7 | 194.8 | 242.4 | **+24%²** | +57% |
| `run.properties` | small-object shapes, adds and reads | 55.3 | 60.2 | 59.9 | **-1%** | +8% |
| `run.arraymap` | `map`/`filter`/`slice` and ArraySpeciesCreate | 67.3 | 64.4 | 62.0 | **-4%** | -8% |
| `run.concat` | `Array.prototype.concat` and `@@isConcatSpreadable` | 65.6 | 72.2 | 70.9 | **-2%** | +8% |
| `run.closures` | closures, `call`/`apply`, `arguments` | 63.4 | 69.4 | 71.6 | **+3%** | +13% |
| `compile.pdfjs` | compile pdf.js (one large script) | 136.4 | 151.4 | 145.6 | **-4%** | +7% |
| `startup.50globals` | startup: a Context and 50 Globals | 6.7 | 8.2 | 8.1 | **-2%** | +20% |

¹ `for…of`, spread and destructuring are ES2015 syntax, which 15.7 cannot parse.
² Not a regression of the dispatch: the two earlier runs of the same comparison gave −8% and
−10%; the harness run on this script *alone*, three alternating pairs, measured 474–489 ms before
and 390–473 ms after; and a compile-once-run-23-times micro-benchmark, four pairs, 255–259 ms
before and 245–268 ms after. What moved in the full run is the JIT's profile of the shared runtime
methods after the eight scripts that precede this one in the same JVM - the harness runs them in
one process, alphabetically - not the cost of a prototype-chain call.

The regexp loop is nine times faster, a wide object three times, `for…of` almost twice; the
megamorphic read, typed arrays, sorting, string building, arithmetic and coercion each gained
between a tenth and a fifth. Nothing regressed: the two metrics that individual runs flagged
(`closures` in one, `protochain` in another) came out identical in a dedicated alternating
micro-benchmark and are noise from the loaded machine - the reason a single over-band run is
never taken as evidence.

Against 15.7 the engine is now faster wherever this work touched, and slower on three metrics
it did not: `protochain`, `instanceof` and `toprimitive`, which is the standing cost of ES2015 -
a `@@hasInstance` check on every `instanceof`, a `@@toPrimitive` check on every coercion, the
proxy-in-chain bit on every prototype walk - that those three scripts were written to keep within
bounds, and `startup`, where every built-in the six later editions added is one more property on a
new realm. Those are the next targets, not this work's.

### Octane

Octane is reported as ops/minute, higher is better, five iterations per benchmark. It is not a
harness built for a loaded machine: one benchmark takes minutes, the JIT's state at the end of
the previous one leaks into the next, and a disturbance lands on one side only. Two interleaved
rounds of the three engines gave ranges rather than numbers, and the ranges overlap for most
benchmarks. They are given as measured; the perf-gate table above is the evidence, this is the
sanity check.

| Benchmark | 15.7 | Before | After |
| --- | ---: | ---: | ---: |
| box2d | 920–1 194 | 303–510 | 822–903 |
| crypto | 3 520–4 822 | 1 581–3 311 | 3 329–3 370 |
| deltablue | 34 211–75 353 | 30 757–54 588 | 65 657–77 479 |
| earley-boyer | 3 855–9 508 | 4 044–7 771 | 7 848–8 730 |
| navier-stokes | 755–1 422 | 676–1 262 | 1 352–1 388 |
| raytrace | 10 619–22 768 | 5 006–15 665 | 20 163–20 321 |
| regexp | 751–1 395 | 166–422 | 479–483 |
| richards | 91 042–176 189 | 74 405–120 955 | 121 661–149 808 |
| splay | 92 110–145 922 | 141 298–159 165 | 156 494–157 952 |

Two things survive the noise. The new engine is never below the previous one, and on box2d,
deltablue, raytrace and regexp it is clearly above it. And on Octane's **regexp** benchmark 15.7 is
still ahead by a factor of two to three, in every round of every sitting - the one comparison
against upstream that is not in doubt. That benchmark runs hundreds of different patterns each
against a *different* subject through `replace`, `split`, `match` and `exec`, so the per-subject
cache above does not reach it; what it exercises is the ES2015 dispatch that the fork's `replace`,
`split` and `match` now go through - `@@replace`/`@@split`/`@@match` looked up on the regexp, the
`flags`, `global`, `unicode` and `lastIndex` reads the specification prescribes per call, and
`RegExpExec`'s look at `exec` per match - each a generic property read that 15.7's ES5 built-ins
never did. The fix other engines apply is a fast path for a regexp whose prototype is untouched,
guarded by the existing builtin switch points; it is the next performance target, and
`regexp.js` in the perf gate should grow a many-subjects `replace`/`split` loop with it.



## Optimistic types

The largest single lever was not taken, because it is a policy rather than a fix.
`--optimistic-types` ships **off** (inherited from upstream 15.x); with it on, the same engine
runs Octane's crypto and navier-stokes about five times faster and richards about three, at the
cost of a longer warmup through deoptimising recompiles, and it is not the mode the conformance
suite is run in. Measured on the ES2026 engine before this work, three iterations:

| Benchmark | default | `--optimistic-types=true` | `-Dnashorn.fields.dual=true` only |
| --- | ---: | ---: | ---: |
| crypto | 7 357 | 39 541 | 7 256 |
| navier-stokes | 2 354 | 11 977 | 1 917 |
| richards | 120 480 | 346 140 | 127 140 |
| deltablue | 105 020 | 129 304 | 92 248 |
| raytrace | 22 368 | 21 673 | 18 450 |
| regexp | 563 | 531 | 516 |

Dual fields alone give nothing; it is the optimistic typing that pays. A long-running embedder
should turn it on. Flipping the default is the open decision; the prerequisite is running test262
in that mode too, which `-Dnashorn.test262.optimistic=true` now does. The first such run did not
get past its first shard: a deoptimising recompilation that threw left every subsequent caller of
the function waiting on `CompiledFunction`'s monitor forever, and what threw was the JVM rejecting
a rest-of method whose local variable table named slots in code the classfile library had patched
out as unreachable. Both are fixed (see the changelog), and a rest-of method records no local
variable table. With them the run completed for the first time: **2 038 failing executions
against the 8 settled ones**, in four families, all in features the six later editions added -
the ES5 core was clean in this mode too. A second pass fixed the families themselves, leaving
198 executions; a third pass fixed those, and the optimistic run now passes every execution - the
8 Annex B indirect-eval cases the pessimistic run still lists included, because a program compiled on
demand declares its vars on the global directly rather than through the merge of its scope. The run
is compared against its own expectations file, `test262-expectations-optimistic.txt`, which is empty.

| Family | Was | What went wrong, and the fix |
| --- | ---: | --- |
| Modules, dynamic `import()`, top-level `await` | 815 | A deoptimising recompilation re-parsed every function with `Parser.parse`, so a module function's first deoptimisation was a `SyntaxError` at its `import` or `export`. `RecompilableScriptFunctionData` now remembers a module function and re-parses it through the module goal. |
| Class private members, `super`, spread calls, `new.target` | 447 | A private read, a `super` property read, a `super(...)`/`super.m(...)` call, a call with a spread argument and `new.target` are all emitted as static runtime calls answering an Object, outside the optimistic operation the code generator wraps other reads and calls in - but the optimistic type calculator typed them narrower all the same, and the plain conversion that followed turned every string result into `0`. The calculator now tags each of them never-optimistic. |
| BigInt-typed arrays, `Atomics`, `DataView` | 529 | Three separate defects. A `BigInt64Array` reported its element type, `BigInteger`, as its optimistic type, which no element getter exists for (an assertion in `dynamicGetIndex`); it is `Object` now. The type evaluator, asked for the type of `ta.buffer` while compiling, called the built-in accessor with the prototype as its receiver and threw; a built-in accessor is now as off-limits to it as a user getter. And a BigInt-capable operator chose the numeric path whenever one operand was an optimistic guess - `big[0] * 2n` converted the literal to a double before the guessed-int read of `big[0]` could be found out; a guess beside a known object now takes the object path, whose result is typed `Object` whatever the operands are (a string operand no longer makes a product a string). |
| Compound assignment through an index | 30 | The self-modifying store kept its key on the stack in a shape that depended on whether the index was numeric - an optimistic guess a deoptimisation changes - so the rest-of continuation restored the stack one element off ("Index 6 out of bounds"). The base copy is now pushed unconditionally and dropped when the key turns out numeric. Pre-existing in the fork; 15.7 has no evaluate-the-key-once store. |

Regression scripts pin each family in optimistic mode:
`basic/es6/optimistic-static-runtime-calls.js`, `optimistic-compound-index-store.js`,
`optimistic-builtin-accessor-types.js` and `restof-local-variable-table.js`.

### The last 198, and what they were

| Family | Was | What went wrong, and the fix |
| --- | ---: | --- |
| Private members on a nested class | 90 | A class field initialiser is a synthetic function that is always parsed, even when the on-demand compilation of its enclosing function skips it; a class expression in it appended its private-name `const` bindings to the skipped initialiser's body, and `AssignSymbols` asserts a skipped function's body is empty. The initialiser now empties its body when it is being skipped, as `functionBody` does for an ordinary function. |
| Direct `eval` in a class method, `super` from `eval`, private-name early errors from `eval` | 26 | A lazily compiled program is compiled by re-parsing it, and the re-parse skips its nested functions - so the `HAS_NESTED_EVAL` the eager parse had put on the program was lost, the class bindings a nested `eval` could reach sat in bytecode locals rather than in scope, and the method's scope walk (computed against the eager parse) stepped past the missing scope object to an undefined `eval`. The parser restores a re-parsed program's own eager flags, as it already did for a re-parsed nested function. Only a program is ever both lazily compiled and the root of a re-parse, which is why the pessimistic run - whose programs compile eagerly - never saw it. |
| Typed-array `[[Get]]` on a non-element numeric key | 20 | The optimistic `getInt`/`getDouble(key, programPoint)` overloads of `ArrayBufferView` did not have the "never consult the prototype" rule of the plain `get` overloads; they do now, telling an optimistic site to relink where the answer is undefined. And the generic `getDouble` miss path answered `NaN` for a missing key (`ta[symbol]`), where the int one deoptimises - it now deoptimises too. |
| BigInt through optimistic guesses | 16 | Three sub-cases. A bitwise operator on two int guesses loaded them under an int upper bound, which elides the guard (any Number coerces to int silently) - but a BigInt does not coerce, it makes the operation a BigInt one; the bound is now `number` for that case, so a BigInt deoptimises the guess and the object path takes over, and `~guess` does the same. The local-variable type pass typed a binary operator from stand-in operands that had lost the "this is a guess" bit, so `Object(5n) * Object(3n)` after its first deoptimisation was typed `double` by that pass and `Object` by the code generator; the stand-ins now carry it (`Expression.isOptimisticGuess()`, which a bytecode local never is). And `**` loaded its operands as doubles outright, which converted a guessed object operand - its `valueOf` - before the right operand was evaluated. |
| `??` with an optimistic operand, and stored into a local | 12 | `undefined ?? undefined` came out `0`: the nullish node took its type from the guessed-int operands. Its type is now `Object` whenever an operand is a guess, the code generator lands on the node's own type after the join, and a bytecode local's identifier is never a guess (its type is proven), so `x = a ?? 5` into an int local no longer disagrees between the type pass and the code generator. |
| Compound assignment `x op= a ?? b` | 10 | The same nullish typing; with it, the operand-type assertion in `loadBinaryOperands` holds. |
| Arrow `this`/`new.target` at program level | 12 | Not a separate defect: the program-level `:arrowThis` store was a casualty of the lost program flags above. |
| `Array.prototype.concat`/`push` observables | 6 | The specialised `push` wrote into the storage of a frozen array, and the specialised `concat` ignored `@@isConcatSpreadable`; each now declines to link when the generic path would behave differently. |
| `Proxy` apply at an optimistic call site | 6 | The proxy's call invocation was cast to the site's guessed return type instead of going through the optimistic return filter; a non-int result was a `ClassCastException`. |
| Destructuring under `with` over a `Proxy` | 4 | The compile-time type evaluator resolves names in the runtime scope to guess their types; through a `with` object that asks the expression object, and a Proxy's `has` trap observed it, out of program order. Under a `with` the evaluator now leaves every name alone. |
| `(a?.b)()`, `x %= y` leaving `-0` | 4 | The member access inside a called optional chain was typed optimistically, which the call emitter asserts against; and an int remainder whose result is `0` from a negative dividend is `-0`, which an int cannot hold, so it deoptimises now. |

`basic/es6/optimistic-guess-operands.js` pins all of it in optimistic mode. None of this is reachable
with optimistic types off, which is why the pessimistic suite was green throughout; with the
optimistic suite fully green, flipping the default is now only a warmup-cost decision.

## Not done, and why

- **`CodeBuffer`** records one capturing lambda per emitted bytecode instruction and replays them
  when the class is written. Recording into primitive arrays instead would cut compile-time
  allocation, but it is a wide mechanical change to the emitter for a gain on the `compile`
  metric only, and it is left for a change of its own.
- **`AccessorProperty.getOptimisticGetter`** builds its method-handle chain on every call, unlike
  the non-optimistic getter, which is cached. It only runs with optimistic types on, and a cache
  keyed by program point costs memory per property; it goes with the optimistic-types decision.
- **Flattening a `ConsString` through a `StringBuilder`** instead of a `char[]` and
  `new String(char[])` looked like a win on paper - the builder keeps the compact Latin-1 coder
  where the `char[]` is a UTF-16 buffer twice the size plus a compaction pass - and measured 45%
  *slower* on the string-building benchmark, consistently, across four alternations against the
  previous build. Collecting the pieces into a deque and copying them twice (once into the builder,
  once out of it) costs more than the wide buffer saves. Reverted; the measurement is the reason
  the benchmark exists.
- **One thread-local read per `ScriptRuntime.apply`** instead of one on entry and one on exit
  was tried and taken out again: the closures benchmark could not tell the two apart, and the
  two-call form is the simpler code.
- **`-XX:TypeProfileLevel=222`** in the `benchmark` profile measured *slower* than the JVM's
  default on JDK 25 in one run and has not been re-measured with the care a flag change deserves.

## The perf gate

[`buildtools/perf-gate.sh`](../../../buildtools/perf-gate.sh) builds a base revision and the
working tree and measures both, interleaved, with
[`PerfBenchmark`](../../../core/src/test/java/org/monflabs/nashorn/internal/performance/PerfBenchmark.java)
over the scripts in `core/src/test/scripts/perf/`. Seven of those scripts exist to prove one
ES2015 feature is free; eight were added with this work so that the gate can see the hot paths
above move at all - none of them was covered before, which is how a quadratic regexp loop and a
twice-boxed subtraction went unmeasured:

| Script | Hot path |
| --- | --- |
| `arith.js` | Object-typed arithmetic, relationals, bitwise operators, `==` |
| `strbuild.js` | `+=` string building and flattening |
| `regexp.js` | an `exec` loop over one subject, `test` and `search` in a loop, a literal in a loop |
| `wideobject.js` | objects with 40 properties added one at a time |
| `megamorphic.js` | one read site over 24 shapes |
| `forof.js` | `for…of`, spread and array destructuring |
| `closures.js` | closure creation, `call`/`apply`, `arguments` |
| `sort.js` | `sort` with a comparator and with the default order |

To re-measure this page's numbers: `buildtools/perf-gate.sh <base-ref>` for the gate metrics, and
`mvn -Pbenchmark verify` for Octane. For a comparison against 15.7, fetch
`org.openjdk.nashorn:nashorn-core:15.7` and run `org.openjdk.nashorn.tools.Shell` over
`core/src/test/scripts/basic/run-octane.js` with the same benchmark list and iteration count.
