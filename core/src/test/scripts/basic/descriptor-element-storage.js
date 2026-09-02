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
 * An element defined through a property descriptor lands in element storage.
 *
 * CreateDataPropertyOrThrow - which slice, map and filter use for a result
 * that is not an ordinary array - makes writable, enumerable, configurable
 * data properties, which is exactly what element storage represents. Held in
 * the property map instead, the element is invisible to the bulk reads that
 * consult the array data alone: Java.to saw undefined where the script saw
 * the value.
 *
 * @test
 * @run
 */

function assertEq(actual, expected) {
    if (actual !== expected) {
        throw new Error("expected " + expected + ", found " + actual);
    }
}

var sliced = Array.prototype.slice.call({ length: 2, 0: "echo", 1: "x" });
assertEq(sliced[0], "echo");

var javaArray = Java.to(sliced, "java.lang.String[]");
assertEq(javaArray[0], "echo");
assertEq(javaArray[1], "x");

function viaArguments() {
    return Java.to(Array.prototype.slice.call(arguments), "java.lang.String[]");
}
var fromArgs = viaArguments("a", "b");
assertEq(fromArgs[0], "a");
assertEq(fromArgs[1], "b");

// defineProperty with default-true attributes reaches element storage too
var arr = [];
Object.defineProperty(arr, 0, { value: 7, writable: true, enumerable: true, configurable: true });
assertEq(Java.to(arr, "int[]")[0], 7);

// while one with restricted attributes keeps descriptor semantics
var restricted = [];
Object.defineProperty(restricted, 0, { value: 9, writable: false, enumerable: false, configurable: false });
var desc = Object.getOwnPropertyDescriptor(restricted, 0);
assertEq(desc.writable, false);
assertEq(desc.enumerable, false);
assertEq(restricted[0], 9);

print("ok");
