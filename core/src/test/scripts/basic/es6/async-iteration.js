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
 * ES2018 async iteration: async generators (async function*), await and yield
 * in the same body, yield* delegation, for-await-of over async and sync
 * iterables, async generator methods, and Symbol.asyncIterator.
 *
 * @test
 * @run
 */

async function* ag() { yield 1; yield await Promise.resolve(2); yield 3; }
async function* deleg() { yield 0; yield* ag(); yield* [4, 5]; }

class C { async *m() { yield "a"; yield "b"; } }
var obj = { async *g() { yield "x"; } };

async function main() {
    var a = [];
    for await (const x of ag()) a.push(x);
    print("async gen: " + a.join(","));

    var d = [];
    for await (const x of deleg()) d.push(x);
    print("yield*: " + d.join(","));

    var s = [];
    for await (const x of [10, Promise.resolve(20), 30]) s.push(x);
    print("sync iterable awaited: " + s.join(","));

    var cm = [];
    for await (const x of new C().m()) cm.push(x);
    print("class method: " + cm.join(","));

    var om = [];
    for await (const x of obj.g()) om.push(x);
    print("object method: " + om.join(","));

    print("asyncIterator self: " + (ag()[Symbol.asyncIterator]() !== undefined));
    print("tag: " + Object.prototype.toString.call(ag()));
    return "done";
}
main().then(r => print(r), e => print("ERR: " + e));
