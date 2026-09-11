# Optimistic typing

JavaScript's numbers are doubles and its variables untyped; the JVM is fastest on `int` operations
in `int` slots. Optimistic typing (`--optimistic-types`, `-ot`) bridges the two by **assuming the
narrow type and being prepared to be wrong**.

## The bet

The compiler assigns each expression the narrowest type it dares — `int` where it can, widening to
`long`, `double`, `Object` only under evidence. `a + b` on two supposed ints compiles to an `iadd`…
but an `iadd` whose result might not fit is emitted as a checked operation that throws
`UnwarrantedOptimismException` (UOE) on overflow, and a property read compiled as returning `int`
gets a return-value filter that throws UOE if the actual value is wider.

## Program points

Every site that can be wrong gets a **program point** — a small integer identifying it uniquely
within the function. The point travels inside the `invokedynamic` call-site flags (packed above bit
15 of the flags word the bootstrap receives — see [linking](linking.md)), so when a UOE surfaces it
names exactly which assumption failed.

## Deoptimising recompilation

A thrown UOE is caught by the function's generated handler and wrapped as a `RewriteException`
carrying the failed program point, the actual value, and the current values of all live locals.
`RecompilableScriptFunctionData` then:

1. records the invalidated point and its now-known wider type;
2. recompiles the function from source with that point pessimised (other points stay optimistic);
3. compiles a one-off **rest-of method** — the same function entered *at* the failure point, whose
   parameters are the captured local values — so the interrupted execution finishes correctly;
4. relinks the call site to the new code for future calls.

An invariant the whole scheme leans on: an optimistic operation must not change the shape of the
live-locals stack between the optimistic method and its rest-of — which is why some expressions
(e.g. the operand of certain compound assignments) are deliberately never optimistic.

Invalidated points are remembered per function (and, with `--persistent-code-cache` plus the
type-info cache, across JVM runs), so the second run of a workload starts with yesterday's lessons.

## Dual fields

The same bet applies to object storage. With optimistic types on, [structure classes](objects.md)
are generated with *dual* fields — a `long` slot and an `Object` slot per property (`JD` classes;
doubles travel as raw bits in the `long`). A property that has only ever held ints is read with a
primitive load, no boxing, no cast; the first wide value demotes that property to its `Object` half.
Without optimistic types (`--optimistic-types=false`), single `Object`-field classes (`JO`) are used.

## The trade

Warmup pays for steady state: every deoptimisation is a full recompile plus a rest-of compile, and
pathological code can deoptimise repeatedly before settling. The mode is the default since
2026.1.0 - it runs Octane's numeric benchmarks three to five times faster - once the conformance
suite passed in it; long-running compute (servers, data crunching) is what it is for, and a
run-once script that never gets hot can turn it off with `--optimistic-types=false`. The
`recompile` [logger](../reference/debugging.md) shows every deoptimisation with its reason —
the first thing to read when warmup looks endless.

The engine's own test suite runs entirely twice — optimistic and pessimistic — precisely because
the two modes execute genuinely different bytecode; a change can be correct in one and wrong in the
other.
