/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/**
 * Operands whose type is only an optimistic guess must stay guarded wherever
 * the operator could not coerce them silently: a BigInt returned into a
 * bitwise operator on two guesses deoptimizes to the BigInt path instead of
 * failing to convert; the local-variable pass sees the same guesses the code
 * generator does, so a product of a deoptimized wrapper and a guess is typed
 * Object, not double; a ?? whose operand is a guess is an Object (undefined ??
 * undefined was 0) while one over proven ints still types as int and stores
 * into an int local; ** evaluates both operands before converting either.
 * Two on-demand-compilation gaps ride along: a lazily compiled program keeps
 * the eager parse's nested-eval flag, so a class method's direct eval finds
 * the global (it was "eval is not a function"), and a lazily skipped field
 * initializer holding a class expression with private members keeps an empty
 * body (its private-name bindings tripped a symbol-assignment assertion).
 * And five smaller ones: a Proxy's apply result reaches an optimistic call
 * site through the return filter; ~ on a guess stays guarded; an int
 * remainder that would be -0 deoptimizes; (a?.b)() is not typed
 * optimistically; a double-typed read of a missing key deoptimizes instead
 * of answering NaN; and the compile-time type evaluator leaves a with
 * scope alone, since a Proxy's has trap would observe it.
 * The private forms sit in eval strings for the public parser API's sake.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

function id(x) { return x; }
function bits() { return id(5n) & id(3n); }
print("bigint bitwise on two guesses:", bits(), bits());
function mulWrapped() { return Object(5n) * Object(3n); }
print("bigint product of wrappers:", mulWrapped(), mulWrapped());
function mixed() { return id(6n) | id(1n); }
print("bigint or:", mixed());

var scopeUndefined;
var x = scopeUndefined ?? undefined;
print("nullish of guesses:", x, typeof x);
x = undefined ?? 42;
print("nullish with value:", x);
function nullishLocal(a) { var v = 1; v = a ?? 5; return v; }
print("nullish into int local:", nullishLocal(2), nullishLocal(undefined));
function nullishCompound(a) { var v = 1; v += a ?? 5; return v; }
print("nullish compound:", nullishCompound(2), nullishCompound(undefined));

var order = [];
var lhs = { valueOf() { order.push("lhsValue"); return 3; } };
var rhs = { valueOf() { order.push("rhsValue"); return 2; } };
var pow = (order.push("lhs"), lhs) ** (order.push("rhs"), rhs);
print("exponent order:", pow, order.join(","));
function expInts(a, b) { return a ** b; }
print("exponent ints:", expInts(2, 3), expInts(2n, 3n), expInts(1.5, 2));

class Evaluating {
    m() { return eval("1 + 1"); }
    static s() { return eval("typeof Evaluating"); }
}
print("class method eval:", new Evaluating().m(), Evaluating.s());
{
    class Inner { m() { return eval("2 + 2"); } }
    print("block class method eval:", new Inner().m());
}

print("nested private in field initializer:", eval(
    "class Outer { static s = class { #inner = 2; get() { return this.#inner; } }; " +
    "  t = class { #inner = 3; get() { return this.#inner; } }; }\n" +
    "[new Outer.s().get(), new (new Outer().t)().get()].join(',')"));
print("private class in arrow body:", eval(
    "var mk = () => class { #p = 4; get() { return this.#p; } }; new (mk())().get()"));

var proxied = new Proxy(function() { return 1; }, { apply() { return true; } });
function callProxy() { return proxied(); }
print("proxy apply result:", callProxy(), callProxy());
function bitNot(o) { return ~o; }
print("bitwise not:", typeof ~id(1n), ~id(1n), ~id(5));
function rem(a, b) { a %= b; return 1 / a; }
print("remainder sign:", rem(-1, -1), rem(-4, 2), rem(5, 3));
var holder = { b() { return this._b; }, _b: { c: 42 } };
print("optional call this:", (holder?.b)().c, (holder?.b)?.().c, holder?.b?.().c);
var floats = new Float64Array([42, 43]);
var sym = Symbol("k");
function readKey(o, k) { return o[k]; }
print("missing key at a double site:", floats[sym], readKey(floats, sym), readKey({}, "nope"));
var trapLog = [];
var probe = new Proxy({}, { has(t, pk) { trapLog.push(pk); return false; } });
var wanted = { get p() { trapLog.push("get"); return undefined; } };
var fallback = 7, seen;
with (probe) { var { p: seen = fallback } = wanted; }
print("with scope untouched by the compiler:", seen, trapLog.join(","));
