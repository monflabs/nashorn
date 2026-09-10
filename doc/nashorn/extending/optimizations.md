# Optimizations over OpenJDK Nashorn 15.7

This page is the catalogue: every optimization this fork carries on top of the engine it started
from, OpenJDK Nashorn **15.7** (the `openjdk-original` branch), what each is estimated to be worth,
and what it does not do or costs. The narrative - the profile that pointed at each item and how
the numbers were taken - is in [Performance](../internals/performance.md); the mechanism behind the largest
item is in [Optimistic typing](../internals/optimistic-typing.md).

A word on the estimates. Every figure comes from the perf gate
([`buildtools/perf-gate.sh`](../../../buildtools/perf-gate.sh), the two engines interleaved on
one machine, minimum within a JVM, median across three JVMs) or from Octane run the same way;
absolute milliseconds do not carry to another machine, ratios do. Two sittings are combined
below: one measured the runtime fast paths against 15.7 with optimistic types *off* (the default
at the time), the other measured the flip of the default against the commit before it. Where a
gain against 15.7 is derived by chaining the two it is marked *est.*; the perf gate against the
`perf-baseline` tag is what checks the combination on every push.

## 1. Optimistic types on by default

Upstream shipped `--optimistic-types` off: narrow (`int`/`double`) types are assumed for every
unproven value and the function is recompiled on demand when a guess fails. The fork turns it on
(2026.1.0), after bringing the ES2026 conformance suite from 2 038 failing executions in that mode
to none - see [Performance](../internals/performance.md#optimistic-types) for the 20-odd defects that were in
the way, each pinned by a regression script.

| What | Gain (the flip, same engine) | Gain vs 15.7 (est.) |
| --- | ---: | ---: |
| Object-typed arithmetic, relationals, bitwise (`arith`) | -64% | -68% |
| `instanceof` and the `@@hasInstance` guard | -72% | -65% |
| Prototype-chain method dispatch (`protochain`) | -79% | -68% |
| Small-object shapes, adds and reads (`properties`) | -60% | -55% |
| Coercions and the `@@toPrimitive` guard (`toprimitive`) | -55% | -45% |
| Closures, `call`/`apply`, `arguments` | -56% | -52% |
| Megamorphic read site | -65% | -75% |
| Typed-array element writes and `set` | -43% | -46% |
| `sort` | -32% | -24% |
| Octane crypto / navier-stokes / richards | 5× / 5× / 3× | - |

Times are milliseconds on the gate's scripts; "gain" is a reduction. The Octane ratios were
measured before the runtime fast paths landed and are a floor.

**Limitations and costs**

- **Warmup.** Every deoptimization is a full recompile of the function plus a "rest-of" compile
  of its continuation, and a run-once script never gets the steady state back. `compile.pdfjs`
  is +17% (optimistic code carries its deoptimization handlers and continuation bookkeeping) and
  `startup.50globals` +5%. A script that runs once and exits may prefer
  `--optimistic-types=false` (`optimisticTypes(false)` on the builder).
- **Top-level loop variables.** The gate's `arraymap` metric (`map`/`filter`/`slice` over a
  64-element array, with the loop's counters as program-level `var`s) reads +23% against the
  commit before the flip and +37% against `perf-baseline`, and a longer warmup (40 runs instead
  of 8) does not close it. Isolated, `map` and `filter` cost the same in both modes; what is
  slower is the loop itself: a program-level `var` is a property of the global, and an `int`
  optimistically stored in its dual-field slot costs about three times what the pessimistic
  boxed store does once HotSpot has compiled the loop (27 ms against 9 ms for 400 000 turns of
  `total += 1`), while the same loop with `-Dnashorn.fields.objects=true`, or inside a function
  where the counters are bytecode locals, is as fast or faster with optimistic types. The
  metric's band is 45% for now; the dual-field scope store is the open item.
- **Eager compilation is incompatible.** A deoptimization recompiles one function from source,
  so `--lazy-compilation=false` alone now turns optimistic types off; naming both explicitly is
  an error, as before.
- **Dual fields.** With optimistic types the generated structure classes carry a `long` and an
  `Object` slot per property (`JD` classes), so plain objects are larger than the `JO` ones the
  pessimistic mode uses. Measured alone (dual fields on, optimism off) that costs 12-19% on
  allocation-heavy Octane benchmarks; with optimism on it is what makes numeric properties
  unboxed, and the net is the table above.
- **Type-information persistence is off** (`nashorn.typeInfo.maxFiles` defaults to 0), so the
  deoptimization history is not carried across JVMs; a process pays its warmup every time.
- **Both modes must keep working.** The core suite runs twice, and test262 runs twice
  (`-Dnashorn.test262.optimistic=false` for the pessimistic slice, which still has the 8 settled
  Annex B failures the optimistic run does not).

## 2. Runtime fast paths

Measured with optimistic types off against 15.7 and the fork's previous revision, one sitting.

| Optimization | Where | Gain | Limitations |
| --- | --- | ---: | --- |
| Number-box fast paths on every BigInt-aware operator and relational (`SUB`, `MUL`, `DIV`, `MOD`, `EXP`, bitwise, shifts, `NEG`, `BIT_NOT`, `INC`, `DEC`, `LT`/`GT`/`LE`/`GE`, `==`) | `ScriptRuntime`, `JSType` | `arith` -11%; a quarter of Octane's samples were here | Only helps the Object-typed path, which optimistic types mostly bypass now; the BigInt, string and object paths are untouched. |
| One `char[]` per subject in the Joni regexp backend (the last subject string and its array are remembered) | `JoniRegExp` | `regexp` loop -88% (nine times faster); the `while ((m = re.exec(s)))` loop was quadratic | One entry: one subject, many matches. Octane's regexp benchmark - hundreds of patterns, each on a *different* subject through `replace`/`split`/`match` - does not benefit and is still 2-3× slower than 15.7, the cost of the ES2015 `@@replace`/`@@split`/`@@match` dispatch and per-call `flags`/`lastIndex` reads; a pristine-prototype fast path is the next target. |
| A regexp cache that caches: lock-free, keyed by a `(pattern, flags, annexB)` record, soft values, size cap (the old weak-keyed one mostly missed and took a JVM-wide lock) | `RegExpFactory` | a literal in a loop no longer recompiles; part of the `regexp` figure | Soft values: under memory pressure entries go and patterns recompile. |
| Geometric spill growth, `useDualFields` as a `ClassValue`, no double `ToPrimitive` for string/symbol keys, no string key built on an array miss, megamorphic relink log guarded | `ScriptObject` | `wideobject` -67% (-44% vs 15.7); `megamorphic` -20% (-32% vs 15.7) | Wide objects still spill: a structure class carries at most the fields it was generated with. |
| `sort` without the extra copy | `NativeArray` | `sort` -8% | The comparator call is still a full script call per comparison. |
| `for…of` over a built-in array iterator stepped directly (`ArrayIterator.stepValue`), no result object per element | `ScriptRuntime`, `ArrayIterator`, `Global` | `forof` -46% (15.7 cannot parse `for…of`) | Only when the iterator's `next` is the realm's own `%ArrayIteratorPrototype%.next`; `next` is still read on every step so a mid-loop replacement is honoured. Other iterables take the generic path. |
| `Promise.prototype.then`/`resolve` read the intrinsic `%Promise%` instead of the global property | `NativePromise`, `Global` | not measured separately | None; it is also what the specification says. |
| Copy-on-write block symbol tables across compilation phases; substring-and-intern identifiers in the lexer | `ir.Block`, `parser.Lexer` | `compile.pdfjs` -4% | Interning is per source, not global. |
| Lock-free named-operation interning in the linker (`ConcurrentHashMap`s with weak values instead of synchronized `WeakHashMap`s) | `NashornCallSiteDescriptor` | contention only; not measured by the gate | - |
| Eight new perf-gate scripts (`arith`, `strbuild`, `regexp`, `wideobject`, `megamorphic`, `forof`, `closures`, `sort`) | `core/src/test/scripts/perf/` | none by themselves - they are what makes the rest measurable | Their bands are the default 20%, not measured per metric like the seven original scripts' are. |

## 3. Reliability under optimistic types

Not optimizations, but what the default flip stood on, and what an embedder turning the mode on
in an older build would hit.

| Fix | Where | What it prevents |
| --- | --- | --- |
| A deoptimizing recompilation that throws installs a fresh switch point, always notifies, and its waiters use a bounded wait that honours interruption | `CompiledFunction` | Every thread that then called the function waited forever on the function's monitor. |
| Rest-of (continuation) methods record no local-variable table | `CodeBuffer`, `ClassEmitter`, `MethodEmitter` | The JVM rejecting the class: `java.lang.classfile` computes `max_locals` from reachable code, and a continuation's prologue is dead. |
| The 2 038 → 0 test262 fixes: module re-parse through the module goal, static runtime calls never optimistically typed, stable stack shape for compound index stores, BigInt guesses on the object path, guarded operands under bitwise operators and `**`, `??` typing, a re-parsed program keeping its eager flags, skipped field initializers kept empty, typed-array element reads, Proxy apply results, const bindings widening in per-iteration scopes, and more | `OptimisticTypesCalculator`, `BinaryNode`, `CodeGenerator`, `Parser`, `ArrayBufferView`, … | Wrong results (`undefined ?? undefined` was `0`, a string result turned into `0`), `TypeError`s on BigInt, "eval is not a function" in class methods, assertion failures in the compiler. |

## 4. Standing costs against 15.7

Against 15.7 with optimistic types off the fork was slower on three gate metrics it had not
touched - `protochain` +57%, `instanceof` +15%, `toprimitive` +19% - the price of ES2015: a
`@@hasInstance` check on every `instanceof`, a `@@toPrimitive` check on every coercion, the
proxy-in-chain bit on every prototype walk. With optimistic types on those three turn into the
largest wins in the first table, so they are no longer a net cost; what remains is:

- **Startup**: +20% against 15.7 (+5% more from the flip). Every built-in the eleven later
  editions added is one more property to install on a new realm; Annex B alone is 6.6%.
- **Octane regexp**: 2-3× slower than 15.7, as above; the one comparison against upstream that
  is not in doubt.
- **Compilation**: +7% against 15.7 before the flip, +17% more with it.

## 5. Tried and reverted

Each was measured and taken out again; the measurement is the reason the benchmark exists.

- Flattening a `ConsString` through a `StringBuilder` rather than a `char[]`: 45% *slower* on
  string building - collecting the pieces into a deque and copying them twice costs more than the
  wide buffer saves.
- One thread-local read per `ScriptRuntime.apply` instead of two: the closures benchmark could
  not tell them apart.
- `-XX:TypeProfileLevel=222` in the benchmark profile: slower than the JVM default on JDK 25 in
  one run; not re-measured with care.

## 6. Not done

- `CodeBuffer` records one capturing lambda per emitted instruction; primitive arrays would cut
  compile-time allocation, for the `compile` metric only.
- `AccessorProperty.getOptimisticGetter` builds its method-handle chain on every link, unlike the
  non-optimistic getter; a cache keyed by program point costs memory per property. It matters
  more now that the mode is the default.
- The pristine-`RegExp.prototype` fast path for `replace`/`split`/`match` (the Octane regexp gap).
- The scalability and reliability items of the September 2026 audit - engine and context
  lifecycle (`close()`), per-Global creation cost, the JVM-wide monitors on `PropertyMap` and
  `Source`, interruptible engine loops - are recorded in the audit plan and not started.

## Reproducing the numbers

`buildtools/perf-gate.sh <base-ref>` for the gate metrics (the working tree against any
revision, `perf-baseline` by default); `mvn -Pbenchmark verify` for Octane; for 15.7, fetch
`org.openjdk.nashorn:nashorn-core:15.7` and run its `Shell` over
`core/src/test/scripts/basic/run-octane.js` with the same benchmark list and iteration count.
Check nothing else is loading the machine first - a Time Machine backup once turned every metric
into a 2× regression.
