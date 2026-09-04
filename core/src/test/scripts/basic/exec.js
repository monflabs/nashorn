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
 * The $EXEC scripting function runs a command in a separate process and
 * exposes its stdout (both as the return value and as $OUT) and its exit
 * code as $EXIT. It is a scripting-mode-only Nashorn extension; the removed
 * backquote syntax is NOT reinstated with it.
 *
 * @test
 * @option -scripting
 * @run
 * @runif os.not.windows
 */

print("typeof $EXEC = " + typeof $EXEC);

var out = $EXEC("echo hello");
print("return  = [" + out.trim() + "]");
print("$OUT    = [" + $OUT.trim() + "]");
print("$EXIT   = " + $EXIT);
