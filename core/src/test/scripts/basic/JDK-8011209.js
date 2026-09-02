/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8011209: Object.getOwnPropertyDescriptor(function(){"use strict"},"caller").get.length is not 0
 *
 * @test
 * @run
 */

// ES2015 16.2 forbids the own "caller" and "arguments" a strict function used
// to carry; the one pair the [[ThrowTypeError]] function backs lives on
// Function.prototype, and that is where its shape is checked from now.
var callerPropDesc = Object.getOwnPropertyDescriptor(Function.prototype, "caller");

var getterLen = callerPropDesc.get.length;
if (getterLen != 0) {
    fail("caller's get.length != 0");
}

var setterLen = callerPropDesc.set.length;
if (setterLen != 0) {
    fail("caller's set.length != 1");
}

var argumentsPropDesc = Object.getOwnPropertyDescriptor(Function.prototype, "arguments");

getterLen = argumentsPropDesc.get.length;
if (getterLen != 0) {
    fail("arguments's get.length != 0");
}

setterLen = argumentsPropDesc.set.length;
if (setterLen != 0) {
    fail("arguments's set.length != 1");
}

var strictArgs = (function() { 'use strict'; return arguments; })();
// ES2017 dropped the caller poison pill: an unmapped arguments object has no
// such property at all, and only callee is still one
if (Object.getOwnPropertyDescriptor(strictArgs, "caller") !== undefined) {
    fail("arguments has a caller property");
}

calleePropDesc = Object.getOwnPropertyDescriptor(strictArgs,"callee");
getterLen = calleePropDesc.get.length;
if (getterLen != 0) {
    fail("argument.callee's get.length != 0");
}

setterLen = calleePropDesc.set.length;
if (setterLen != 0) {
    fail("argument.callee's set.length != 1");
}
