/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Philippe Riand designates this
 * particular file as subject to the "Classpath" exception as provided
 * in the LICENSE file that accompanied this code.
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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.util.Set;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * A global object as a debugger sees it.
 */
final class ExecutionContextImpl implements ExecutionContext {
    private final int id;
    private final Global global;
    private final ScriptScopeView scriptScope;

    ExecutionContextImpl(final int id, final Global global) {
        this.id = id;
        this.global = global;
        // what the global has before any script ran is what a script scope leaves out
        this.scriptScope = new ScriptScopeView(global, Set.of(global.getOwnKeys(true)));
    }

    /** The script scope: the global's own, script-made properties. */
    ScriptScopeView scriptScope() {
        return scriptScope;
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
