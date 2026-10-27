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
 * Regressions found by the robustness harnesses of JavascriptPerformance (JSC
 * stress tests, differential fuzzing against V8, Jazzer): each line prints what
 * V8 prints.
 *
 * @test
 * @run
 */

function show(f) {
    try {
        return String(f());
    } catch (e) {
        return "throw " + (e instanceof Error ? e.constructor.name : typeof e);
    }
}

// a BigInt case of a switch
function sw(x) { switch (x) { case 0n: return "zero"; case 1: return "num1"; default: return "d"; } }
print("switch:", sw(0n), sw(1), sw(0));

// an inherited setter whose property is deleted from the prototype while linked
var calledA = false, counter = 0;
var A = { set y(_) { calledA = true; } };
var B = Object.create(A);
Object.defineProperty(B, "y", { set: function (_) { if (counter++ === 9) delete B.y; }, configurable: true });
var c = Object.create(B);
for (var i = 0; i < 11; i++) { c.y = 42; }
print("deleted setter:", calledA);

// a call with more arguments than one method holds
var args = [];
for (var i = 0; i < 12000; i++) args.push("a" + (i % 3));
var many = new Function("a0", "a1", "a2", "function f() { return arguments.length; } return f(" + args.join(",") + ");");
print("12000 arguments:", many(1, 2, 3));

// a parenthesized optional chain as a callee, and optional calls
var nul = null, o = { b() { return this === o; }, c: { d() { return this === o.c; } } };
print("optional calls:", nul?.b(), o?.b(), (o?.b)(), (o?.c.d)(), show(() => (nul?.b)()), show(() => (nul?.c.d)()));
var log = []; print("arguments evaluated:", show(() => (nul?.c.d)(log.push(1))), log.length);

// a spread array literal with holes
var holes = [...[1], , 2, , ];
print("holes:", holes.length, 1 in holes, 3 in holes);

// a stack overflow and a too long string are RangeErrors
function deep() { return deep() + 1; }
print("stack overflow:", show(deep));
print("string length:", show(() => "x".padStart(2 ** 31 - 1, "ab")), show(() => "ab".repeat(2 ** 30)));

// BigInt shifts and powers past the int range
print("bigint:", 0n << (2n ** 64n), 1n >> (2n ** 64n), -1n >> (2n ** 64n), (-1n) ** (2n ** 64n + 1n), show(() => 1n << (2n ** 64n)));

// a literal with a prototype of its own, after one with Object.prototype was linked
var n = +{};
print("literal proto:", show(() => String({__proto__: null})), Object.getPrototypeOf({__proto__: null}));

// a break out of a switch with a nested switch, in a finally block
try { } finally { switch (0) { case "x": switch (true != []) {} break; } }
print("switch in finally: ok");

// a regexp replace with the d flag
print("replace d flag:", "aXbX".replace(/(X)/dg, "-"));

// a function with more expressions than program points
var body = [];
for (var i = 0; i < 140000; i++) body.push("x = x + " + (i % 7));
print("program points:", new Function("var x = 0; " + body.join(";") + "; return x;")());

// a function with more variables than a class has fields
var names = [];
for (var i = 0; i < 5000; i++) names.push("v" + i);
print("scope fields:", new Function("let " + names.join(",") + "; v0 = 1; v4999 = 2; return v0 + v4999 + (function () { return v4999; })();")());

// a boolean added to a value that deoptimizes the add concatenates "true", not 1
var arr = ["q"];
function str() { return "q"; }
print("boolean add:", true + arr, false + str(), [true + arr][0], (1 == 2) + Reflect.ownKeys({q: 1}), true + 1);

// SameValueZero on a flat string and a concatenated one
var flat = "ab", cons = "a"; cons += "b";
print("same value:", [flat].includes(cons), [flat, 0.5].includes(cons), Object.is(flat, cons));

// a second deoptimization inside an object literal with a computed key
var lv0 = 5; var lv2 = 1.5; var lv3 = {}; function id(x) { return x; }
if (lv3) { lv0 = id(((NaN in ({[("NaN")[lv2]]: lv3})) / undefined)); }
print("literal temp:", lv0);

// a unary + or - in a test keeps its operand's fraction
var half = 0.5;
print("unary test:", (-half) ? "t" : "f", (+half) ? "t" : "f", (-Infinity) ? "t" : "f", !(-half));

// an object += a number concatenates
var obj = {}; obj += 1.5;
print("object add assign:", obj);

// an accessor and a data property under the same key in a literal: the later one wins
print("literal redefinition:", JSON.stringify(Object.entries({...[5, 6], get 1() { return 1; }})),
    JSON.stringify(Object.entries({0: 5, 1: 6, get 1() { return 1; }})),
    JSON.stringify(Object.entries({get 1() { return 1; }, 1: 6})),
    JSON.stringify(Object.entries({get x() { return 1; }, x: 2})));

// a closure over a loop binding, in a nested loop that binds a name no closure captures
var outer = [];
for (const a of [1, 2]) { for (const b of [3, 4]) { outer.push(() => a); } }
for (const k in {p: 1}) { for (const j in {q: 1}) { outer.push(() => k); } }
print("nested loop binding:", outer.map(f => f()).join(" "));

// -x on a guess that is a BigInt, in a test
print("negate bigint:", (-BigInt.asIntN(69, -(2n ** 63n))) || "x");

// a spread over an accessor of the literal defines the key, where it stands
print("spread over accessor:", JSON.stringify({get 0() { return 1; }, ...["a"]}), JSON.stringify({get y() { return 1; }, ...{y: 2}}),
    JSON.stringify(Object.keys({get y() { return 1; }, [""]: 2, y: 3})));

// ++ and -- on a guess that is a BigInt, in a test; a guess that is a string under a bitwise operator
var big = 5n; if (big--) print("bigint decrement:", big);
var bits = {}; bits ^= (5e-324 + (-1n).toString(10));
print("guess concatenated:", bits);

// a + of undefined and a guess, in an argument: both operands must load as numbers
var u6 = new Map(), u5 = 1, u7 = 0;
function u1(a, b) { u7 = id((`x${((void undeclared) + u5)}y` ?? (u6)?.[a])); }
try { u1(1, 2); } catch (e) { print("undefined plus guess:", e.name); }

// a call's result taken as an int32 by a bitwise operator: ToInt32, not a cast through a saturated long
function huge() { return 1.234567890123e+29; }
print("call to int32:", 0 ^ huge(), 0 | huge(), ~huge(), huge() >> 1);


// an object += undefined concatenates
var withUndefined = {}; withUndefined += void 0;
print("object add undefined:", withUndefined);
function objectResult() { return {valueOf() { return 3; }}; }
function stringResult() { return "5"; }
class ObjectFromConstructor { constructor() { return {valueOf() { return 9; }}; } }
print("call to int32, objects:", 0 ^ objectResult(), 1 - stringResult(), 0 | new ObjectFromConstructor(), ~stringResult());


// BigInt(string) takes the StringIntegerLiteral grammar, and the Number.is* tests are false for a BigInt
print("bigint strings:", ["\u00008", " 8 ", " 8﻿", "0x-1", "٩", "-0x1f", "0b101"].map(function (c) { try { return String(BigInt(c)); } catch (e) { return e.name; } }).join(" "),
    Number.isInteger(-1n), Number.isSafeInteger(1n), Number.isFinite(1n), Number.isNaN(1n));

// && and || under a bitwise operator: the left operand's truth is its own, not that of its int32
var truthy = ["x"], half = 0.5, str = "a";
function arrayResult() { return [5]; }
print("and/or narrowed:", (truthy && 3) | 0, (half && 3) | 0, (str || 7) & 7, (arrayResult() && 3) | 0, (arrayResult() || 3) | 0);

// the iterable of a for-of whose binding is a pattern, or of a for await, sees the bindings uninitialised
try { for (const [dz, dy = dz] of [[dz]]) print(dz); } catch (e) { print("head dead zone:", e.name); }

// parseInt past 2^53 is rounded once
print("parseInt rounding:", parseInt("123456789012345678901234"), parseInt("ffffffffffffffffffff", 16));

// an int product of 0 and a negative number is -0
var zero = 0, negative = -5;
print("negative zero product:", Object.is(zero * negative, -0), Object.is(0 * negative, -0), 1 / (zero * -3));

// join of an array-like whose length is huge: the absent elements are counted, not read one at a time
try { Array.prototype.join.call({length: Infinity}); } catch (e) { print("join huge length:", e.name); }
var lateKey = {length: 4, 0: {toString() { lateKey[3] = "late"; return "x"; }}};
print("join keys:", Array.prototype.join.call({length: 3, 0: "a", 2: "c"}), Array.prototype.join.call(lateKey), Array.prototype.join.call({length: 3, "5000000000": 1}));

// a destructuring for-of in a finally block, deoptimized: its internal binding needs a program point to resume at,
// and the rest-of method the values the enclosing catch blocks read
var fo = {p: 1};
function finallyLoop(a) { try { if (a) throw 1; } catch (e) { for (const k in {a: 1}) { fo.p += 0.5; } } finally { for (const [x] of [[1], "ab"]) { fo.p = fo.p + 0.5; } } return fo.p; }
print("for-of in finally:", finallyLoop(0), finallyLoop(1));

// a throw from the conversion a continuation does on entry, inside a try: only the scopes open at the continuation
// point are popped before its catch block is entered
function catchTarget(e) { return e.name; }
var caught;
try { for (const x0 of [1 / Object.create(null)]) { let i = 0; (() => i)(); } } catch (e) { caught = catchTarget(e); }
print("continuation catch:", caught);

// an array literal element whose type narrowed across a deoptimization: the rest-of method's primitive array is
// converted from the interrupted method's Object array
var big = 4294967295;
try { print("array literal narrowed:", [big["a"] * (big++)][0]); } catch (e) { print("array literal narrowed:", e); }

// a boolean argument picks no long specialization: the result is a number
print("Math boolean argument:", typeof Math.max(81, false), Math.max(81, false) === 81, typeof Math.min(false, 3), typeof Math.abs(true), typeof Math.floor(false));

// an int quotient of 0 by a negative number is -0
function quotient(x, y) { return x / y; }
print("negative zero quotient:", Object.is(quotient(0, -4), -0), quotient(8, -4), Object.is(quotient(0, 4), 0));

// a getter redefining a data property of the same literal keeps its place among the own keys
print("literal getter order:", Object.keys({name: 1, p: 2, get name() { return 3; }}), Object.keys({name: 1, p: 2, get ["name"]() { return 3; }}));

// a symbol dead at the continuation point that an enclosing finally saves when it deoptimizes in turn
var dead = 0, deadOut;
function deadInFinally(a) { if (dead) return 0; try { deadOut = (a++).p; } finally { dead--; } }
print("dead in finally:", deadInFinally(false), deadOut);

// an assignment to a const in its temporal dead zone is a ReferenceError, not the TypeError of an initialized one
var tdzErrors = [];
try { tdzX--; const tdzX = 1; } catch (e) { tdzErrors.push(e.name); }
try { tdzY = 2; const tdzY = 1; } catch (e) { tdzErrors.push(e.name); }
function tdzHead(a) { try { for (const a of [a--]) ; } catch (e) { tdzErrors.push(e.name); } }
tdzHead(1);
const tdzK = 1;
try { tdzK = 2; } catch (e) { tdzErrors.push(e.name); }
print("const assignment in TDZ:", tdzErrors);

// a boolean added to a guess that deoptimizes outside an optimistic +, as a thrown value, stays a boolean
function minusZero() { return "-0"; }
var boolObj = {p: "s"};
try { throw true + minusZero(); } catch (e) { print("thrown boolean sum:", e); }
try { throw false + boolObj.p; } catch (e) { print("thrown boolean sum:", e); }

// a guess that is a BigInt is not converted before the other operand is evaluated
var orderErrors = [];
try { BigInt.asUintN(43, 5n) | (243 >= undeclaredOperand); } catch (e) { orderErrors.push(e.name); }
try { BigInt.asIntN(13, 0n) / (undeclaredOperand++); } catch (e) { orderErrors.push(e.name); }
var operandSide = 0;
try { BigInt.asUintN(8, 5n) | (operandSide = 1, true); } catch (e) { orderErrors.push(e.name + operandSide); }
print("BigInt operand order:", orderErrors);

// concat spreads a proxy only for an array, and asks it for Symbol.isConcatSpreadable through its get trap
var concatKeys = [];
var concatProxy = new Proxy({}, { get(t, k) { concatKeys.push(String(k)); return typeof k === "string" ? k + "!" : undefined; } });
var concatResult = [].concat(concatProxy, new Proxy([1, , 3], {}));
print("concat proxy:", concatResult.length, concatResult[0] === concatProxy, 2 in concatResult, concatKeys);

// a subclass instance has a map of its own prototype, so a call site linked for a base instance does not take it
class ShapeBase { toString() {} }
class ShapeDerived extends ShapeBase { *[Symbol.iterator]() {} }
class ShapePlain extends ShapeBase {}
String(new ShapeBase());
print("subclass map:", 0 / new ShapeDerived(), 0 / new ShapePlain());
class MethodA { m() { return "A"; } }
class MethodB extends MethodA { m() { return "B"; } }
function callM(o) { return o.m(); }
print("subclass map:", [callM(new MethodA()), callM(new MethodB()), callM(new MethodA()), callM(new MethodB())]);

// Number.prototype, String.prototype and Boolean.prototype are wrapper objects, which JSON unwraps
print("JSON wrapper prototypes:", JSON.stringify([Number.prototype, String.prototype, Boolean.prototype]), JSON.stringify(Object.create(Number.prototype)));

// a comma expression or a conditional whose value is a guess is a guess: a BigInt it turns out to be is not converted
var guessCond = true, guessResults = [];
try { guessResults.push(String(~(0, BigInt.asUintN(6, 7n))), String(~(guessCond ? BigInt.asUintN(6, 7n) : 1))); } catch (e) { guessResults.push(e.name); }
try { guessResults.push(String((0, BigInt.asUintN(6, 7n)) | 1n)); } catch (e) { guessResults.push(e.name); }
print("comma and conditional guesses:", guessResults);

// a local assigned inside an optional chain keeps a value on the path where the chain short-circuits past the
// assignment (a VerifyError: the slot of the type the assignment gave it was read uninitialized)
var chainBase = null, chainSeen = [];
for (let chainKey in "ab") { chainBase?.[chainKey++ ? 1 : 2]; try { undefinedChainFunction(); } catch (e) { chainSeen.push(chainKey); } }
function chainAssign(o) { let k = 1.5; var r = o?.a?.[(k = 2) ? "b" : "c"]; return [k, r]; }
print("assigned in optional chain:", chainSeen, JSON.stringify([chainAssign(null), chainAssign({a: {b: 9}}), chainAssign({})]));

// arithmetic tested for truth is computed in doubles: an int would truncate 1 / 2 and 9 / 0 to 0, and wrap a product
function truthOne() { return 1; }
function truthBig() { return 65536; }
var truthN = 0;
print("arithmetic truth:", (truthOne() / 2 && truthN < 4) ? "t" : "f", (truthOne() / 0 && truthN < 4) ? "t" : "f",
      (truthBig() * truthBig()) ? "t" : "f", (truthBig() * truthBig() && truthN < 4) ? "t" : "f");

// negating a string assigned to a numeric local is typed as a number: the result was cast to a string (a ClassCastException)
function negateString() { var w = 2.5; w = -("a" + 1); var x = 2.5; x = ~("4" + 1); return [w, x]; }
print("negated string:", negateString());

// a slice past a leading hole does not keep the hole: [, 'a', 'b'].slice(1) has 'a' at index 0
function sliceHoles(a) { var r = []; for (var i = 0; i < a.length; i++) r.push(i in a ? a[i] : "<hole>"); return r.join(); }
var sliceDeleted = ["x", "y", "z", "w"]; delete sliceDeleted[1]; delete sliceDeleted[2];
print("slice past holes:", sliceHoles([, "a", "b"].slice(1)), sliceHoles([, ..."ab"].slice(1)), sliceHoles(sliceDeleted.slice(2)), sliceHoles(sliceDeleted.slice(0, 2)));

// a function resumed after a deoptimization does not convert an operand already evaluated to a narrower type than
// the deoptimized code gave it: undefined became 0, and undefined <= 10000 was true
var resumedOperand = "10";
print("resumed operand:", undefined <= ((resumedOperand * 1000) >>> []));

function guessOverObject() {
    var r = [];
    for (let i = 0; i < 2; i++) { r.push(i / new Uint8Array([1])); }
    for (let i = 0; i < 2; i++) { r.push(String([i - "aAbB"])); }
    for (const x of "x") { for (let i = 0; i < 2; i++) { try { r.push(x / (2n ** 64n)); } catch (e) { r.push(e.name); } } }
    return r.join();
}
print("guess over object:", guessOverObject());

// an array that holds itself joins to the empty string where it recurs, as in every other engine; the join
// recursed until the stack overflowed into a RangeError
function cyclicJoin() {
    var a = [-0, "t", -1];
    a[1] = a;
    var b = [1, [2]];
    b[1].push(b);
    return [String(a), a.join(), a - 1, b.toLocaleString()].join(" ");
}
print("cyclic join:", cyclicJoin());

// pendingK() is pending when pendingArr() deoptimizes; widened for that rest-of method only, it was an int guess again in the
// next one, so t0 was typed a number there and its string arrived as 0
var pendingV3 = 10;
let pendingFlag = true;
function pendingK() { return pendingV3; }
function pendingArr() { return [1]; }
function pendingThenLive() {
    const t0 = pendingK() + Array.from(pendingArr()).flatMap((a) => [a, 2]);
    if (pendingFlag--) {}
    return t0;
}
print("pending then live:", pendingThenLive());

// defining an element past the end leaves the indices it skips holes; ensure filled them, so index 0 of a
// spread-made empty array held a null that "in" and Object.keys saw
function defineSkips() {
    var a = [...[]];
    Object.defineProperty(a, '1', { value: 5, writable: true, enumerable: true, configurable: true });
    return [a.length, 0 in a, Object.keys(a)].join(" ");
}
print("define skips:", defineSkips());

// a string compared with a BigInt is read by the StringIntegerLiteral grammar, as BigInt() reads it: a NUL is not
// white space (it was trimmed, so "\u00003" >= 0n held) and a no-break space is (it was not trimmed)
print("bigint string compare:", "\u00003" >= 0n, "\u00003" == 3n, "\u00a03" == 3n, " 0x10 " == 16n, "3x" < 4n);

// v0-- is pending beside the call when replaceAll deoptimizes; widened, it lost its scope-base temporary in the
// rest-of method, every later local moved a slot, and the callee was restored from its this
var pendingDecrement = 0;
class PendingDecrement { constructor(a) { this.x = ((pendingDecrement--) % [Object.getOwnPropertyNames("ab".replaceAll(0))]); } }
print("pending decrement:", new PendingDecrement(0).x, pendingDecrement);

// a division compared strictly with an object is done in doubles, not optimistically, and loaded its guesses
// unguarded: the null-prototype operand was converted (a TypeError) before the undeclared one was read
let strictDivisor = Object.create(null);
var strictDivision = () => (strictDivisor / undeclaredDivisor) === strictDivisor;
try { strictDivision(); } catch (e) { print("strict division:", e.name); }

// deoptimized at BigInt(3), the program was recompiled while bitwiseDouble still held 1.5, so its reads were
// guessed doubles; a double guess under a bitwise operator went unguarded and the BigInt was converted (a TypeError)
var bitwiseDouble = 1.5;
bitwiseDouble = BigInt(3);
print("bitwise double guess:", String(bitwiseDouble ^ bitwiseDouble), String(bitwiseDouble | 1n), String(~bitwiseDouble));
