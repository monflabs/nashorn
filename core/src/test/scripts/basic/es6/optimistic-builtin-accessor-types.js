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
 * Two things the optimistic type machinery got wrong about built-ins. The
 * type evaluator, asked for the type of ta.buffer while compiling, found the
 * nasgen accessor on %TypedArray%.prototype and called it with the prototype
 * as the receiver - a TypeError out of the compiler; a built-in accessor is
 * now as off-limits to it as a user getter. And a BigInt64Array reported its
 * element type, BigInteger, as its optimistic type, which the code generator
 * has no getter for (an assertion in dynamicGetIndex); an optimistic type is
 * int, double or Object.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

var out = [];
var u8 = new Uint8Array(new ArrayBuffer(8), 0, 1);
out.push(u8.buffer.byteLength, u8.byteLength, u8.byteOffset, u8.length);
var f64 = new Float64Array(new ArrayBuffer(16), 0, 1);
out.push(f64.buffer.byteLength, f64.byteOffset);
function bufOf(t) { return t.buffer; }
out.push(bufOf(u8).byteLength, bufOf(f64).byteLength, bufOf(new Int16Array(2)).byteLength);
var big = new BigInt64Array(2); big[0] = 5n; big[1] = big[0] * 2n; out.push(String(big[1]), typeof big[0]);
var ubig = new BigUint64Array([1n, 2n]); var sum = 0n; for (var i = 0; i < ubig.length; i++) { sum += ubig[i]; } out.push(String(sum));
var rab = new ArrayBuffer(8, { maxByteLength: 16 }); var view = new Uint8Array(rab, 0, 4); view[0] = 7; rab.resize(16); out.push(view[0], view.length);
print(out.join(","));
