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
 * Check that we can assign to all properties from builtin functions and their
 * prototypes. This is to make sure all such properties are writable.
 *
 * @test
 * @run
 */

(function() {
    var PropNamesGetter = Object.getOwnPropertyNames;
    var ObjectType = Object;

    function assignAll(obj) {
        if (! (obj instanceof ObjectType)) {
            return;
        }
        var props = PropNamesGetter(obj);
        for (var p in props) {
            // Some ES2015 builtins expose accessors that reject the prototype as
            // receiver - Map.prototype.size throws "not a Map object". Reading
            // them is supposed to fail; the point of the test is that assigning
            // to builtin function properties does not break the engine.
            try {
                var value = obj[props[p]];
                obj[props[p]] = value;
            } catch (e) {
                if (!(e instanceof TypeError)) {
                    throw e;
                }
            }
        }
    }

    var globalProps = PropNamesGetter(this);
    for (var i in globalProps) {
        var prop = globalProps[i];
        if (typeof this[prop] == 'function') {
            assignAll(this[prop].prototype);
            assignAll(this[prop]);
        }
    }
})();
