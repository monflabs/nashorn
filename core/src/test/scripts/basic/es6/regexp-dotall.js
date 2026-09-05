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
 * ES2018 RegExp s (dotAll) flag: '.' matches line terminators too.
 *
 * @test
 * @run
 */

var dot = new RegExp("a.b", "s");
print("dotAll=" + dot.dotAll);
print("flags=" + dot.flags);
print("match=" + dot.test("a\nb"));

print("noFlag=" + new RegExp("a.b").dotAll);
print("noMatch=" + new RegExp("a.b").test("a\nb"));

var order = new RegExp("a.b", "gimsy");
print("order=" + order.flags);

var uni = new RegExp("a.b", "su");
print("unicode=" + uni.flags + " " + uni.test("a\nb"));

print("proto=" + RegExp.prototype.dotAll);

try {
    new RegExp("x", "ss");
    print("FAIL: duplicate s accepted");
} catch (e) {
    print("dupThrows=" + (e instanceof SyntaxError));
}
