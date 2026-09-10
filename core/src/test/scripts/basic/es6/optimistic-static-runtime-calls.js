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
 * Reads and calls the code generator emits as static runtime calls rather
 * than as optimistic operations - a private member read, a super property
 * read, a super call, a call with a spread argument - answer an Object, and
 * the private forms are inside an eval string because the public parser
 * API, which every script here is parsed through as well, does not accept
 * class fields or private members yet
 * the optimistic type calculator must not type them narrower: it did, and the
 * plain conversion that followed turned every string result into 0. Pinned in
 * optimistic mode, where the suite's own execution would otherwise be the only
 * thing exercising it.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

var out = [];
class A { m() { return 'am'; } get g() { return 'ag'; } static sm() { return 'asm'; } }
class B extends A {
    sm() { return super.m(); }
    m() { return super.m() + '!'; } sg() { return super.g; } si() { return super['m'](); }
    static sm() { return super.sm() + '!'; }
}
var b = new B();
out.push(b.sm(), b.m(), b.sg(), b.si(), B.sm());
// the private forms are written inside eval: the public parser API, which
// every script here is also parsed through, does not accept them yet
out.push(eval("class P { #p = 'priv'; static #s = 'spriv'; get #acc() { return 'pget'; } #pm() { return 'pmeth'; }" +
    " static ss() { return P.#s; } priv() { return this.#p; } acc() { return this.#acc; } pm() { return this.#pm(); }" +
    " has(o) { return #p in o; } }" +
    " var p = new P(); [P.ss(), p.priv(), p.acc(), p.pm(), p.has(p), p.has({})].join('/')"));
function s(x) { return 'str' + x; }
function rest(...args) { return s(...args); }
var o = { f: function (...args) { return s(...args); } };
out.push(s(...[1]), rest(2), o.f(3), Reflect.apply(function (...a) { return s(...a); }, null, [4]));
var counted = 0;
class Custom extends Promise {}
var bound = Custom.resolve.bind(Custom);
Custom.resolve = function (...args) { counted++; return bound(...args); };
Promise.all.call(Custom, [1, 2]).then(function (v) { print("all:", v, counted); });
print(out.join(","));
