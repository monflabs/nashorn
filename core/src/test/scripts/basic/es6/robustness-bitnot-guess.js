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
 * A guess typed double keeps its guard under ~: bounded by a number, it would
 * load converted instead, and a BigInt it turns out to be throw a TypeError
 * where ~ makes a BigInt of it. Here the global is first deoptimized to a double
 * by v--, then holds 0n. Reduced from a differential fuzzing finding.
 *
 * @test
 * @run
 */

function always() { return true; }
var count = 10;
var w = 0;
var value = {};
var undefinedBase;
var text = "aAbB";
function decrement(a, b) {
    try {
        for (const [x] of [[1, 2], 'ab', [count]]) {
            switch (((a, b) => (2n ** 64n))(undefined, (0n).toString(5))) { }
            try { let { } = ({...(value--), 1(a) { }, __proto__: null}); } catch (e) { } finally { }
        }
    } finally { }
}
try {
    do {
        try { count = decrement.call((text + "0x10"), (text ?? 1.5)); } catch (e) { } finally { }
    } while (((typeof Number.isSafeInteger((undefinedBase)?.[BigInt.asUintN(38, 7n)]))) && w++ < 2 && always());
} catch (e) { }
if (`x${(7n).toString(28)}y`) { value = 0n - 0n; }
try {
    while ((~value) && w++ < 5 && always()) { }
    print("bit not:", String(~value));
} catch (e) {
    print("bit not:", e.name, e.message);
}
