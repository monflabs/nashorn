/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.nashorn.internal.runtime.arrays;

import java.lang.invoke.SwitchPoint;

/**
 * Whether any object anywhere holds a property at an index that a write below
 * it could not simply shadow.
 *
 * Writing to an index an array does not have yet is, by 9.1.9.1, a walk of its
 * prototype chain, and what the walk finds decides the write: an accessor up
 * there is entitled to it, and a property that is not writable refuses it. An
 * ordinary writable one does not, because the write makes an own property of
 * the array either way - which is what the fast path does.
 *
 * Walking on every append would cost more than the walk ever finds, because
 * almost no program puts such a property at an index of an object that is a
 * prototype: it takes a defineProperty, or a freeze, to do it at all, since an
 * ordinary write goes to the array data rather than to the map.
 *
 * So the append is linked directly to the element while this holds, and the
 * link is dropped when it stops holding. Like the well-known symbol flags, this
 * is one-way and global rather than per-realm: what it guards is a shape shared
 * by every array in the process, and a property once defined may have been
 * copied anywhere.
 */
public final class IndexedPrototypes {
    private static volatile SwitchPoint pristine = new SwitchPoint();

    private IndexedPrototypes() {
    }

    /**
     * The switch point an append may be linked against, or null once one has
     * been defined and there is nothing left to guard.
     *
     * @return the switch point while no object holds a property at an index
     */
    public static SwitchPoint pristine() {
        final SwitchPoint current = pristine;
        return current.hasBeenInvalidated() ? null : current;
    }

    /** Records that some object now holds a property at an index. */
    public static void note() {
        final SwitchPoint current = pristine;
        if (!current.hasBeenInvalidated()) {
            SwitchPoint.invalidateAll(new SwitchPoint[] { current });
        }
    }
}
