# Architecture and the pipeline

Nashorn is an ahead-of-time-shaped, just-in-time-run compiler: every script and function becomes
JVM bytecode — there is no interpreter tier — and every dynamic operation in that bytecode is an
`invokedynamic` instruction that the [linker](linking.md) wires up at first execution. This page
walks the road from source text to installed class; the pages after it each zoom into one
subsystem.

```text
Source ──► Lexer/Parser ──► IR (ir.*) ──► compilation phases ──► CodeGenerator ──► install
              parser/                        codegen/              java.lang.classfile
```

## Parsing

`Source` wraps the characters (file, URL or string, with the digest used for caching). The
hand-written `Lexer` and recursive-descent `Parser` (`internal/parser`) produce the internal IR of
`internal/ir` — immutable AST nodes (`FunctionNode`, `Block`, `VarNode`, expression nodes), where
every transformation is a visitor (`ir/visitor`) that returns new trees, and a `LexicalContext`
tracks the enclosing block/function chain during traversal. Immutability is what makes reparsing
and on-demand recompilation safe: nothing downstream can have scribbled on the tree.

## The compilation phases

`Compiler` drives an ordered list of `CompilationPhase` objects over each function:

| Phase | What it does |
| --- | --- |
| Constant folding | `1 + 2` becomes `3`; trivially dead branches drop. |
| ES6 desugaring (`ES6Desugar`) | Rewrites constructs with no direct bytecode form — destructuring, rest/spread, module prologues, [Annex B's block-function marking](annex-b.md) — into ones that have. Runs before lowering and before symbols, so its synthetic variables get slots like any other. |
| Lowering (`Lower`) | Control-flow surgery: comparisons to runtime calls where needed, `finally` block inlining, completion-value bookkeeping for programs. |
| Splitting (`Splitter`, `SplitIntoFunctions`) | JVM methods top out at 64KB; functions whose IR weight exceeds a threshold are split into artificial nested functions. |
| Program points | Numbers every [optimistic](optimistic-typing.md) operation site. |
| Symbol assignment (`AssignSymbols`) | Declares and resolves every name to a `Symbol`: local slot, scope property, global — including hoisting `var`s to their function body. |
| Scope depths | Computes how many scope links each non-local access must walk, enabling fast-scope loads. |
| Optimistic type assignment, local-variable type calculation | Chooses the narrow types the code will assume, and the slot layout that follows. |
| Bytecode generation | `CodeGenerator` walks the typed IR and emits methods. |
| Install | Defines the classes, creates the `ScriptFunction` machinery. |

## Bytecode without ASM

This fork generates bytecode with the JDK's own `java.lang.classfile` API (JEP 484) — the reason
`nashorn-core` has **zero dependencies**. Two consequences shape the emitter:

- **`CodeBuffer` records, then replays.** The classfile API only hands out a `CodeBuilder` inside
  the callback that builds one method, but the code generator keeps several methods open at once (a
  nested function is emitted while its enclosing function still is). So `MethodEmitter` appends
  `Consumer<CodeBuilder>` operations to a buffer, and `ClassEmitter.toByteArray()` replays them into
  the real builders. Labels force this design: a classfile label belongs to the builder that made
  it, so the emitter's own labels are mapped to real ones only at write time.
- **Stack maps come from a `ClassHierarchyResolver`.** The verifier needs common-supertype answers
  for classes that cannot be loaded yet (the compile unit itself, the runtime-generated
  [structure classes](objects.md)); they are answered from package naming — anything in the
  `scripts`/`objects` packages is a `ScriptObject` subtype, everything else `Object`.

Type descriptors (`ClassDesc`, `MethodTypeDesc`) are parsed once and cached; parsing them per
emission is measurably slower.

## Laziness and recompilation

By default (`--lazy-compilation`), only a program's outermost body is compiled eagerly. Every
nested function is represented by a `RecompilableScriptFunctionData` holding its source range;
first call triggers parse-and-compile of just that function. The same object is the anchor for
[optimistic recompilation](optimistic-typing.md): when a type assumption fails, it reparses the
same source range and compiles a wider variant. This is why IR immutability and a deterministic
pipeline matter — an on-demand compile must reproduce exactly the tree and slot layout the eager
compile would have had.

## Where the compiled code lives

Compiled classes install into a per-[Context](contexts-globals.md) `ScriptLoader`, are cached
per-`Source` in the Context's class cache, and can persist across processes with
`--persistent-code-cache`. One compiled script serves many globals: the bytecode binds to a realm
only through the `ScriptFunction` created against it.

## Seeing it

Every phase can show its work: `--print-parse`, `--print-ast`, `--print-lower-ast`,
`--print-symbols`, `--print-code`, and the [`--log`](../reference/debugging.md) subsystems
(`codegen`, `time`, `recompile`…) trace the pipeline live.
