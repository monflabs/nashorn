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
 * A rest-of method that resumes inside an array literal finishes the array the
 * interrupted method was filling. Its element types can differ from that one's,
 * the types of an element narrowing across a deoptimization (here the product,
 * once its operand is no longer a guess), and an element already stored can be
 * undefined, which no primitive array holds: the literal is an Object array in
 * the rest-of method. Reduced from a differential fuzzing finding.
 *
 * @test
 * @run
 */

function __b() { if (0) throw new RangeError(0); return true; }
function __show(v, d) {}
function __err(e) {}
function __p(v) {}
function __t(e) {}
function __c(v) {}
var v0 = true;
let v3 = [];
function f0(a, b) {}
function f2(a, b) {}
try { for (const x0 of [[(0), , Array.from(0).includes(0), `a${(0)[(0)]}b${((s, ...v) => s.raw.join(0) + v.length)`x${[]}y\n`}`].map((a, b) => (Array.from(Object.entries(({constructor: (0), get length() {}}))).with(0) ^ (v3++)))]) {} } catch (e) {}
try { L3: for (let i0 = 0; i0 < 3; i0++) { for (let j0 = 0; j0 < 3 && __b(); j0++) { if (Array.from([f2.apply(null, [...new Map([[(0).name, ((v3--) * [])]]).entries(), ...new Set(0)]), j0, [], (0)()]).map(0)) continue L3; } } } catch (e) {}
print("done");
