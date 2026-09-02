/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Make sure the ECMAScript 2015 builtins are present. This engine has no ES5-only
 * mode, so what this test once asserted was absent must now all be there.
 *
 * @test
 * @run
 */

function checkDefined(name, object) {
    if (typeof object[name] === 'undefined' || !(name in object)) {
        Assert.fail(name + ' is not defined in ' + object);
    }
}

checkDefined('Symbol', this);
checkDefined('Map', this);
checkDefined('Set', this);
checkDefined('WeakMap', this);
checkDefined('WeakSet', this);
checkDefined('getOwnPropertySymbols', Object);
checkDefined('entries', Array.prototype);
checkDefined('values', Array.prototype);
checkDefined('keys', Array.prototype);

function expectParses(src) {
    try {
        Function(src);
    } catch (e) {
        Assert.fail('Should have parsed: ' + src + ' (' + e + ')');
    }
}

expectParses('let i = 0');
expectParses('const i = 0');
expectParses('for (let i = 0; i < 10; i++) print(i)');
expectParses('0b0');
expectParses('0o0');
expectParses('`text`');
expectParses('`${ x }`');
expectParses('`text ${ x } text`');
expectParses('f`text`');
expectParses('for (a of [1, 2, 3]) print(a)');
expectParses('for (var a of [1, 2, 3]) print(a)');
expectParses('for (let a of [1, 2, 3]) print(a)');
expectParses('for (const a of [1, 2, 3]) print(a)');
