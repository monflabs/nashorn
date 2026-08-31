/*
 * Copyright (c) 2026, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * A regular expression literal inside a template substitution. The lexer
 * scans a substitution to its closing brace in one go, and used to leave a
 * slash there for the parser to settle, which the parser never got to see.
 *
 * @test
 * @run
 */

print(`${/a/.test('a')}`);
print(`${'xyx'.replace(/x/g, 'y')}`);
print(`${[1, 2, 3].filter(x => /2/.test(x))}`);
print(`${ /^\s+/.source }`);

// a slash after an operand is still a division
var six = 6, two = 2;
print(`${six / two}`);
print(`${(six) / two}`);
print(`${[six][0] / two}`);
print(`${six /= two}`);

// the tag sees the same
function tag(strings, ...values) {
    return values.join(',');
}
print(tag`${/a/.test('a')} ${six / two}`);

// nested templates, and a regex that contains braces and a slash
print(`${`${/\{2\}/.test('{2}')}`}`);
print(`${'a/b'.split(/\//).length}`);
