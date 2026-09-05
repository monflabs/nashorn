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
 * ES2018 RegExp Unicode property escapes \p{...}/\P{...} (require the u flag).
 * General_Category and Script are answered exactly by the engine.
 *
 * @test
 * @run
 */

function T(cond) { return cond ? "ok" : "FAIL"; }

print("gc L: " + T(/\p{L}/u.test("a") && !/\p{L}/u.test("1")));
print("gc long name: " + T(/\p{Letter}/u.test("a")));
print("gc=code: " + T(/\p{gc=Lu}/u.test("A") && !/\p{gc=Lu}/u.test("a")));
print("General_Category=long: " + T(/\p{General_Category=Uppercase_Letter}/u.test("A")));
print("script: " + T(/\p{Script=Greek}/u.test("α") && !/\p{Script=Greek}/u.test("a")));
print("script alias: " + T(/\p{sc=Grek}/u.test("α")));
print("negated: " + T(/\P{L}/u.test("1") && !/\P{L}/u.test("a")));
print("in class: " + T(/^[\p{L}\p{Nd}]+$/u.test("ab12")));
print("no u -> literal: " + T(/\p/.test("p")));
print("loose rejected: " + T(rejects("\\p{ Lu }")));
print("scx unsupported: " + T(rejects("\\p{Script_Extensions=Greek}")));

function rejects(src) {
    try { new RegExp(src, "u"); return false; } catch (e) { return e instanceof SyntaxError; }
}
