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

import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.DebugScope;
import org.monflabs.nashorn.api.debugger.DebugScope.ScopeType;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.FunctionScope;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.WithObject;

/**
 * A call frame as a debugger sees it, over a shadow frame.
 */
final class DebugFrameImpl implements DebugFrame {
    private final PausedEventImpl event;
    private final int index;
    private final Frame frame;

    DebugFrameImpl(final PausedEventImpl event, final int index, final Frame frame) {
        this.event = event;
        this.index = index;
        this.frame = frame;
    }

    @Override
    public String id() {
        return Integer.toString(index);
    }

    @Override
    public String functionName() {
        return frame.functionName;
    }

    @Override
    public Location location() {
        final DebugScriptImpl script = event.debugger.scriptFor(frame.source);
        final ScriptInfo.Entry site = frame.site;
        if (site != null) {
            return new Location(script, site.line, site.column);
        }
        return new Location(script, frame.functionLine, frame.functionColumn);
    }

    @Override
    public Location functionLocation() {
        return new Location(event.debugger.scriptFor(frame.source), frame.functionLine, frame.functionColumn);
    }

    @Override
    public List<DebugScope> scopes() {
        final List<DebugScope> scopes = new ArrayList<>();
        boolean ownFunctionSeen = false;
        ScriptObject scope = frame.scope;
        final Global global = event.contextImpl() == null ? null : event.contextImpl().globalObject();
        while (scope != null) {
            if (scope instanceof Global g) {
                addGlobalScopes(scopes, g);
                return scopes;
            } else if (scope instanceof WithObject with) {
                scopes.add(new DebugScope(ScopeType.WITH, with.getExpression(), null));
            } else if (scope.isBlockScope()) {
                scopes.add(new DebugScope(ownFunctionSeen ? ScopeType.CLOSURE : ScopeType.BLOCK, scope, null));
            } else if (scope instanceof FunctionScope) {
                if (ownFunctionSeen) {
                    scopes.add(new DebugScope(ScopeType.CLOSURE, scope, null));
                } else {
                    scopes.add(new DebugScope(ScopeType.LOCAL, scope, frame.functionName));
                    ownFunctionSeen = true;
                }
            } else if (scope.isScope()) {
                scopes.add(new DebugScope(ownFunctionSeen ? ScopeType.CLOSURE : ScopeType.LOCAL, scope, null));
                ownFunctionSeen = true;
            } else {
                break;
            }
            scope = scope.getProto();
        }
        if (global != null) {
            addGlobalScopes(scopes, global);
        }
        return scopes;
    }

    /**
     * The global object ends every chain twice over: first as the script scope
     * - the properties scripts declared, which is what a person paused at the
     * top level wants to see and what a frontend expands - then as the global
     * itself, built-ins and all, which a frontend keeps folded.
     */
    private void addGlobalScopes(final List<DebugScope> scopes, final Global global) {
        final ExecutionContextImpl ctx = event.debugger.contextFor(global);
        if (ctx != null) {
            scopes.add(new DebugScope(ScopeType.SCRIPT, ctx.scriptScope(), null));
        }
        scopes.add(new DebugScope(ScopeType.GLOBAL, global, null));
    }

    @Override
    public Object thisValue() {
        return frame.self;
    }

    @Override
    public Object evaluate(final String expression) throws DebugException {
        try {
            return event.call(() -> {
                final Global global = Global.instance();
                final ScriptObject scope = frame.scope != null ? frame.scope : global;
                return DebuggerImpl.evalIn(global, scope, expression, frame.self);
            });
        } catch (final DebugException e) {
            throw e;
        } catch (final Exception e) {
            throw new DebugException(e.getMessage(), null, e);
        }
    }

    Frame frame() {
        return frame;
    }
}
