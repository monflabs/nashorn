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
 * Backquote string should result in error with -nse even with -scripting
 *
 * @option -nse
 * @option -scripting
 * @test
 * @run
 */

// Backquote used to introduce a shell command under -scripting, and -nse turned
// that extension off. It is a template literal now - standard syntax, so -nse
// does not disable it.
if (`ls -l` !== "ls -l") {
    throw new Error("backquote should produce a template literal");
}
var x = 2;
if (`a ${x} b` !== "a 2 b") {
    throw new Error("template substitution failed");
}
