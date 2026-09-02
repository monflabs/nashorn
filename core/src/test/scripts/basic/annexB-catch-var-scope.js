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
 * B.3.5's var-scoped binding of a catch parameter's name is a binding no
 * name in the catch block resolves to, so nothing marks it as a scope symbol
 * by use. In a program with an eval - or under the debugger - every var
 * lives in scope, and once the body has a scope of its own (the block-level
 * function's binding gives it one) code generation asserted on it.
 *
 * @test
 * @run
 */

eval("1");

try {
    throw 'thrown';
} catch (e) {
    var e = 'redeclared';
    print(e);
}

{
    function inBlock() { return 'hoisted'; }
}

print('e' in this, this.e, typeof e);
print(inBlock());

function f() {
    eval("1");
    try {
        throw 'thrown';
    } catch (e) {
        var e = 'redeclared in f';
        print(e);
    }
    {
        function g() { return 'hoisted in f'; }
    }
    return g();
}
print(f());
