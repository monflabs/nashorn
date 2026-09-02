/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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
 * JDK-8035312_5 - pushes and pops for frozen array
 *
 * ES2015 makes both a TypeError rather than something quietly dropped, which is
 * what the catch blocks below were written for.
 *
 * @test
 * @run
 */

var b = [1,2,3];
Object.freeze(b);

try {
    b.push(4);
} catch (e) {
    print("length = " + b.length);
    print("i caught an error");
}
print(b);
print(b[3]);
print("length = " + b.length);

var c = [1,2,3];
Object.freeze(c);

for (var i = 0; i < 5; i++) {
    try {
	c.pop();
    } catch (e) {
	print("length = " + c.length);
	print("I caught an error");
	print(c);
    }
}

print(c);
print(c[3]);
print("length = " + b.length);

