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
 * A deoptimising recompilation of a program whose for-of loops bind a
 * block-scoped variable: the rest-of method enters through a jump to its
 * continuation point, so the body before it is dead code to the JVM, and the
 * local variable table entries recorded for that part named slots beyond the
 * max_locals the classfile library computed - "Invalid index N in
 * LocalVariableTable", a ClassFormatError at install. A rest-of method now
 * records no local variable table. Only reproduces with optimistic types on,
 * which is why the option is pinned here rather than left to the suite's
 * two executions.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

var out = [];
var ta = new Uint8Array([5, 6]);
for (const t of ta) out.push(t);
var m = new Map([[1, 'a']]);
for (const [k, v] of m) out.push(k + v);
function* g() { yield 7; yield 8; }
for (const z of g()) out.push(z);
var count = 0;
for (const e of [10, 20, 30]) { count += e; }
out.push(count);
for (const q of "héllo") { try { out.push(q.charCodeAt(0)); } catch (e) { out.push("no"); } }
print(out.join(","));
