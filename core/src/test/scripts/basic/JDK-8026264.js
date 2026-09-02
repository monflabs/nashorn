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
 * JDK-8026264: Getter, setter function name mangling issues
 *
 * ES2015 14.3.9 names an accessor "get x" or "set x". The prefix is part of the
 * name rather than the internal mangling this test was written to check, so the
 * expectations carry it.
 *
 * @test
 * @run
 */

var obj = {
   get ":"(){},
   set ":"(x){},
   get ""(){},
   set ""(x){}
};

var desc = Object.getOwnPropertyDescriptor(obj, ":");
if (desc.get.name != 'get :') {
    fail("getter name is expected to be 'get :' got " + desc.get.name);
}

if (desc.set.name != 'set :') {
    fail("setter name is expected to be 'set :' got " + desc.set.name);
}

desc = Object.getOwnPropertyDescriptor(obj, "");
if (desc.get.name != 'get ') {
    fail("getter name is expected to be 'get ' got " + desc.get.name);
}

if (desc.set.name != 'set ') {
    fail("setter name is expected to be 'set ' got " + desc.set.name);
}
