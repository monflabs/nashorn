# Logging and debugging

The engine has a logging system of its own, keyed by subsystem, plus a set of `-Dnashorn.*` system
properties for the switches that predate it. Prefer the loggers when investigating code generation —
they were built for exactly that, and adding print statements to the compiler was never the way.
The upstream [`DEVELOPER_README`](../../nashorn-original/DEVELOPER_README ':ignore') remains the
exhaustive treatment; this page is the distilled, fork-accurate version.

## The loggers

```bash
jjs --log=codegen script.js               # one subsystem at level info
jjs --log=codegen:finest script.js        # explicit level
jjs --log=codegen,fields:finest script.js # several; each takes its own level
```

Levels are the `java.util.logging` names — `severe`, `warning`, `info`, `config`, `fine`, `finer`,
`finest` — and default to `info`. The subsystems:

| Logger | Shows |
| --- | --- |
| `compiler` | Compilation activity at the job level. |
| `recompile` | Optimistic deoptimisation and recompilation — which functions, why, with what types. |
| `codegen` | Every bytecode as it is emitted, with the emission id and stack state (example below). |
| `fold` | Constant folding, before lowering. |
| `lower` | The lowering pass: comparisons to runtime calls, finally inlining, control flow. |
| `symbols` | Assignment of symbols to identifiers. |
| `scopedepths` | Scope-depth calculation for non-local symbols. |
| `fields` | Field representation decisions (`finest` traces every field read/write). |
| `time` | Timers per compilation phase, dumped at exit (example below). |
| `methodhandles` | Method handle operations (very verbose; pairs with `-Dnashorn.methodhandles.debug.stacktrace`). |
| `classcache` | Compiled-class cache hits and evictions. |

### Reading the codegen log

`--log=codegen` prints one line per bytecode. For `return x > 0 ? x : -x`:

```text
[codegen] #44  {2:I O}   dynamic_runtime_call GT:ZOI_I args=2 returnType=boolean
[codegen] #46  {1:Z}     ifeq  ternary_false_5402fe28
[codegen] #47            load symbol x slot=2
[codegen] #48  {1:O}     goto ternary_exit_107c1f2f
[codegen] #49         ternary_false_5402fe28
[codegen] #50            load symbol x slot=2
[codegen] #51  {1:O}     convert object -> double
[codegen] #52  {1:D}     neg
[codegen] #53  {1:D}     convert double -> object
[codegen] #55  {1:O}     return object
```

The leading number is a monotonically increasing emission id; `{n:...}` is the operand stack depth
and types. Set `-Dnashorn.codegen.debug.trace=<id>` to get a Java stack trace at the moment that
particular bytecode is emitted — the quick answer to "who generated that `neg`?".

### Reading the time log

`--log=time` accumulates per-phase timings and dumps them at process exit:

```text
[time] 'JavaScript Parsing'              1076 ms
[time] 'Constant Folding'                 159 ms
[time] 'Control Flow Lowering'            303 ms
[time] 'Bytecode Generation'             5177 ms
[time] 'Class Installation'              1854 ms
[time] Total runtime: 11994 ms (Non-runtime: 11027 ms [91%])
```

The percentage is time spent *not* executing bytecode — compilation and bookkeeping — which is the
number to watch when warmup is the complaint. A level finer than `info` shows individual
compilations as they happen.

## System properties

The ones worth knowing, from the full list in `DEVELOPER_README`:

| Property | Purpose |
| --- | --- |
| `-Dnashorn.args=<string>` | Inject engine options where the launch line is not editable; `-Dnashorn.args.prepend` for defaults that explicit options should override. |
| `-Dnashorn.regexp.impl=[joni\|jdk]` | Regular-expression backend; `joni` is the default. Patterns with the `u` flag always use the JDK engine — see [Regular expressions](../internals/regexp.md). |
| `-Dnashorn.debug` | Debug mode: enables the global `Debug` object (below) and internal counters. |
| `-Dnashorn.codegen.debug.trace=<id>` | Stack trace at a given bytecode emission id. |
| `-Dnashorn.fields.dual` / `-Dnashorn.fields.objects` | Force the dual (primitive+object) or object-only field representation — see [Objects](../internals/objects.md). |
| `-Dnashorn.compiler.splitter.threshold=<n>` | IR weight at which a function is split to stay under the JVM's 64KB method limit (default `0x8000`). |
| `-Dnashorn.unstable.relink.threshold=<n>` | Call-site misses before a site is deemed megamorphic (deprecated in favour of `--unstable-relink-threshold`). |
| `-Dnashorn.spill.threshold=<n>` | Property count above which objects use spill storage (default 256). |
| `-Dnashorn.persistent.code.cache=<dir>` | Directory for `--persistent-code-cache` (default `nashorn_code_cache`). |
| `-Dnashorn.typeInfo.maxFiles`, `.cacheDir`, `.cleanupDelaySeconds` | The optimistic-type information cache: capacity (0 disables, `unlimited` allowed), location, cleanup interval. |
| `-Dnashorn.profilefile=<file>` | Redirect the `--profile-callsites` dump from stderr. |
| `-Dnashorn.lexer.xmlliterals` | Accept XML literals (untested, historical). |

## The `Debug` object

With `-Dnashorn.debug` set, scripts see a global `Debug` with introspection helpers:
`Debug.map(obj)` (the object's [PropertyMap](../internals/objects.md)), `Debug.identical(a, b)`,
`Debug.getClass(obj)`, `Debug.toJavaString(obj)`, `Debug.dumpCounters()` (ScriptObject/scope/
PropertyMap statistics), and an event queue for runtime events (`getRuntimeEvents()` and friends) —
in debug mode the last deoptimisations are recorded there. The full function list is in
`DEVELOPER_README`.

## Seeing the compiler's output

`--print-code` prints the bytecode listing of everything compiled; `--print-code=dir:<dir>` writes
one listing per class. `--print-ast` and `--print-lower-ast` print the IR before and after
lowering; `--print-parse` prints the source as the parser understood it. `--verify-code` runs the
class-file verifier over generated code before installing it — slow, but the first thing to switch
on when the JVM rejects a generated class.
