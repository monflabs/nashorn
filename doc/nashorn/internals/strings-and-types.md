# Strings and type coercion

Two closely related value-representation stories: how strings avoid copying, and how the
ECMAScript conversion algorithms map onto Java types.

## ConsString: strings as ropes

`a + b` on strings does not concatenate — it allocates a `ConsString`, a two-field rope node
(`left`, `right`, precomputed length) implementing `CharSequence`. Building a string in a loop is
therefore O(1) per step instead of O(n):

```js
var s = "";
for (var i = 0; i < n; i++) s += piece(i);   // n rope nodes, no copying yet
```

**Flattening is deferred** to the first operation that actually needs the characters — `charAt`,
`toString`, a regexp match. It is synchronised, and iterative with an explicit stack, because a
deeply left-leaning rope built by exactly the loop above would otherwise overflow the Java call
stack. A small state counter adds hysteresis: a rope only ever used as an operand of further
concatenation never flattens at all, while one inspected repeatedly flattens its nested children
too.

The price of the design is a discipline the whole runtime observes: **a JS string is
`String`-or-`ConsString`**, never assumed `java.lang.String`. Every string test is
`JSType.isString(v)` (which accepts both), rope-preserving paths use `toCharSequence`, forcing
paths use `toString`, and the [primitive linker](linking.md) lists `ConsString` beside `String` so
method calls on a rope link identically. This is also visible at the
[Java boundary](../guide/connecting-with-java.md#how-values-convert): a parameter typed
`CharSequence` or `Object` may receive a live rope.

## JSType: the conversion algorithms

`JSType` is the single home of ECMAScript's abstract conversions, as static methods that double as
method-handle targets for [compiled code](architecture.md) and the [linker](linking.md):

| Method | Implements |
| --- | --- |
| `toBoolean` | ToBoolean — with overloads for `int`, `double`, `Object` so compiled code converts without boxing |
| `toNumber` | ToNumber — plus `toNumberOptimistic(v, programPoint)`, which *throws* rather than widens when the value is not already numeric, feeding [deoptimisation](optimistic-typing.md) |
| `toString` / `toCharSequence` | ToString, in forcing and rope-preserving flavours |
| `toInt32` / `toUint32` / `toUint16` | The bit-twiddling integer conversions behind `|0`, `>>>` and friends, again in optimistic variants |
| `toPrimitive(v, hint)` | ToPrimitive — `valueOf`/`toString` ordering by hint, `@@toPrimitive` honoured |
| `toPropertyKey` | ToPropertyKey — strings and symbols survive, everything else stringifies |
| `toScriptObject` / `toObject` | ToObject — primitive wrapping |

The pattern worth noticing is the **optimistic twin**: for each hot conversion there is a
`...Optimistic(value, programPoint)` variant that succeeds only if no conversion is actually
needed, and otherwise throws `UnwarrantedOptimismException` carrying the program point — the
compiler emits the twin at sites it has bet narrow, and the ordinary version everywhere else.

## Operators

The generic (untyped) cases of the language's operators live in `ScriptRuntime` as static methods —
`ADD`, the comparison family (`LT`, `LE`, `GT`, `GE`), `EQUALS`/`STRICT_EQUALS`, `TYPEOF`,
`DELETE`, `IN`, `INSTANCEOF` — again shaped as method-handle targets. `ADD` is the instructive one:
it applies ToPrimitive to both operands and then either builds a `ConsString` (either side a
string) or adds numerically — the rope design and the operator semantics meet in that one method.
Compiled code only calls these generic versions when types are unknown or mixed; when
[optimistic typing](optimistic-typing.md) has settled on ints or doubles, `+` is an `iadd`/`dadd`
with an overflow check, and `ScriptRuntime` never hears about it.

Symbols round out the picture: a `Symbol` is its own runtime type, refuses `toString` coercion (the
one conversion that throws), and participates in property keys via `toPropertyKey` — which is why
the engine's property maps key on `Object`, not `String`.
