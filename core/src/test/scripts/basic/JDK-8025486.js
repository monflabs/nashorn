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
 * JDK-8025486: RegExp constructor arguments are not evaluated in right order
 *
 * @test
 * @run
 */

new RegExp({
    toString: function() {
        print("source");
        return "a";
    }
}, {
    toString: function() {
        print("flags");
        return "g";
    }
});

// ES2015 21.2.3.1 step 5 made flags legal with a RegExp pattern, where ES5.1
// made it a TypeError. The flags are read, and they replace the pattern's own.
var replaced = new RegExp(/asdf/g, {
    toString: function() {
        print("flags again");
        return "i";
    }
});
if (String(replaced) !== "/asdf/i") {
    fail("expected /asdf/i, got " + replaced);
}
