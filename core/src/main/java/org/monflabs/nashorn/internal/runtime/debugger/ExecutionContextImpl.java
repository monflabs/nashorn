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

package org.monflabs.nashorn.internal.runtime.debugger;

import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * A global object as a debugger sees it.
 */
final class ExecutionContextImpl implements ExecutionContext {
    private final int id;
    private final Global global;

    ExecutionContextImpl(final int id, final Global global) {
        this.id = id;
        this.global = global;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String name() {
        return "nashorn";
    }

    @Override
    public Object global() {
        return global;
    }

    Global globalObject() {
        return global;
    }
}
