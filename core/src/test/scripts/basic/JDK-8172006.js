/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8172006: Nashorn JavaScript engine fails to call @FunctionalInterface with a java.util.List argument
 *
 * @test
 * @run
 */

var f = Java.type("org.monflabs.nashorn.test.models.FunctionalInterfaceProvider").getImpl();

var s = "a";
s += "b";
var o = { p: 10 };
var a = [1, 2, 3];

f([ f([]), f([]), f([]), f([]), f([]), f([]), f([]), f([]) ]);
f([ f([]), f([ f([]), f([f([]), f([]), f([]) ]), f([])]), f([]) ]);
f([ f([s]), f([o]), f([a]), f([s + "c"]), f([o]), f([a]) ]);
