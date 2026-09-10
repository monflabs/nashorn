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
 * A self-modifying store through an index reference - d[k] *= 5 - keeps the
 * key on the stack across the operand loads, and the stack has to have the
 * same shape at every program point in the original method and in the
 * rest-of continuation that restores it. It depended on whether the index
 * was numeric, an optimistic guess a deoptimisation changes, and the restored
 * stack came out one element off (an ArrayIndexOutOfBoundsException from the
 * continuation). At program scope the key is a scope variable, which is
 * where the guess is made.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

var out = [];
var k = "z"; var d = {z: 2}; d[k] *= 5; out.push(d.z);
var k2 = "y"; var e = {y: 2}; e[k2] += 5; out.push(e.y);
function key(n) { return { toString: function () { return "p" + n; } }; }
var b = {p1: 6, p2: 6}; b[key(1)] ^= 3; b[key(2)] **= 2; out.push(b.p1, b.p2);
var i = 1; var arr = [1, 2, 3]; arr[i] += 10; arr[i + 1] -= 1; out.push(arr.join("/"));
var f = 1.5; var m = {1.5: 4}; m[f] *= 2; out.push(m[1.5]);
var c = {q: 1}; c.q |= 6; out.push(c.q);
print(out.join(","));
