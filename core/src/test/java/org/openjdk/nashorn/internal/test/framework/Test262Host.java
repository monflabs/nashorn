/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package org.openjdk.nashorn.internal.test.framework;

import org.openjdk.nashorn.internal.objects.NativeArrayBuffer;

/**
 * The part of test262's {@code $262} host object that a script cannot do for
 * itself.
 *
 * The engine's own packages are exported to this one only for the test run, and
 * a script reaching them through {@code Java.type} is subject to the access
 * rules of wherever it happens to be running. This class is on the class path
 * with the rest of the framework, so it is reachable from anywhere, and it is
 * the only thing the host object needs to name.
 */
public final class Test262Host {
    private Test262Host() {
    }

    /**
     * ES2015 24.1.1.3 DetachArrayBuffer, which the suite uses to check what
     * every operation over a detached buffer does.
     *
     * @param buffer the ArrayBuffer to detach
     */
    public static void detachArrayBuffer(final Object buffer) {
        NativeArrayBuffer.detach(buffer);
    }
}
