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
 * JDK-8041995: optimistic object property maps were only updated if the outermost program
 * point in a property setter failed, not an inner one, which is wrong.
 *
 * @test
 * @run
 */

function xyzzy() {
    return 17.4711;
}
var obj = {
    z: -xyzzy()
};
print(obj.z);

function phlug() {
    var obj = {
    4: -Infinity,
     5: Infinity,
    length: 5 - Math.pow(2, 32)
    };

    // ES2015 7.1.15 ToLength reads a negative length as zero, where ToUint32
    // wrapped 5 - 2^32 round to 5 and searched five indices. Either way the
    // point below stands: the length is a double, and reading it as an int
    // would set this looping four billion times.
    return Array.prototype.lastIndexOf.call(obj, -Infinity) === -1;
}

var d = new Date;
print(phlug());
var d2 = new Date - d;
print(d2 < 5000); // if this takes more than five seconds we have read the double length as an int

function wrong() {
    var obj = {
    length1: 5 - Math.pow(2, 32),
    length2: 4 - Math.pow(2, 32),
    length3: 3 - Math.pow(2, 32),
    length4: 2 - Math.pow(2, 32),
    length5: 1 - Math.pow(2, 32),
    length6: Math.pow(2, 32)
    };
    for (var i in obj) {
       print(obj[i]);
    }
}

wrong();
