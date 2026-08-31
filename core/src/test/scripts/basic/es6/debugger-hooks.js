/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * The debugger's hooks must not change what a script does: every kind of
 * exit from a function, every kind of scope, deoptimisation, splitting and
 * generators, all with --debugger on. Nothing here talks to a debugger; the
 * output is what the same script prints without the option.
 *
 * @test
 * @option --debugger
 * @run
 */

// try/finally and every way out of a function
function exits(kind) {
    try {
        if (kind === "return") return "returned";
        if (kind === "throw") throw new Error("thrown");
        if (kind === "break") { do { break; } while (false); return "broke"; }
        return "fell through";
    } finally {
        print("finally for " + kind);
    }
}
print(exits("return"));
try { exits("throw"); } catch (e) { print("caught " + e.message); }
print(exits("break"));
print(exits("other"));

// closures, blocks, catch parameters and with
function scopes(n) {
    let outer = n;
    {
        let inner = outer + 1;
        var seen = inner;
    }
    try { throw n * 10; } catch (c) { seen += c; }
    with ({ w: 100 }) { seen += w; }
    return function () { return outer + seen; };
}
print(scopes(1)());

// a deoptimisation: int, then double, then object
function grow(x) { var v = x + 1; return v; }
print(grow(1) + " " + grow(1.5) + " " + grow("s"));

// generators run their body on a thread of their own
function* gen(limit) {
    for (let i = 0; i < limit; i++) {
        yield i * i;
    }
    return "done";
}
var it = gen(3), parts = [];
for (var r = it.next(); !r.done; r = it.next()) parts.push(r.value);
print(parts.join(",") + " " + r.value);

// classes: constructors, methods, getters, super
class Base {
    constructor(v) { this.v = v; }
    get twice() { return this.v * 2; }
    describe() { return "base " + this.v; }
}
class Derived extends Base {
    constructor(v) { super(v + 1); }
    describe() { return "derived of " + super.describe(); }
}
var d = new Derived(4);
print(d.describe() + " " + d.twice);

// arrow functions and their this
var obj = { n: 7, arrow: function () { return [1, 2].map(x => x * this.n); } };
print(obj.arrow().join(","));

// a function large enough to be split
var big = "function bigOne(a) { var s = 0;";
for (var i = 0; i < 2500; i++) big += " s += a * " + i + " + (a ? 1 : 0);";
big += " return s; }";
eval(big);
print(bigOne(2));

// a recursive function, so frames nest and unwind through an exception
function depth(n) { if (n === 0) throw new RangeError("bottom"); return depth(n - 1) + 1; }
try { depth(20); } catch (e) { print("unwound " + e.message); }
