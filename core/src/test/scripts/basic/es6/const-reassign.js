/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8051889: Implement block scoping in symbol assignment and scope computation
 *
 * @test
 * @run
 * @option --language=es6 */

"use strict";

try {
    const x = 2;
    x = 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x++;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x--;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    ++x;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    --x;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x += 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x *= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x /= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x %= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x |= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x &= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x ^= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x <<= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x >>= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    x >>>= 1;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

try {
    const x = 2;
    // 12.5.3.1 makes deleting a binding in strict code an early error, so the
    // delete is written for another eval to refuse
    eval("delete x");
    fail("const assignment didn't throw");
} catch (e) {
    // the parser reports where it read the delete, which is a path this file
    // has no business printing
    print(e.name + ": cannot delete identifier in strict mode");
}

const c = 1;

try {
    c = 2;
    fail("const assignment didn't throw");
} catch (e) {
    print(e);
}

(function() {
    try {
        c = 2;
        fail("const assignment didn't throw");
    } catch (e) {
        print(e);
    }
})();
