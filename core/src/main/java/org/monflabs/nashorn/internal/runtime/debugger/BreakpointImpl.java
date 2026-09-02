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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.monflabs.nashorn.api.debugger.Breakpoint;
import org.monflabs.nashorn.api.debugger.BreakpointRequest;
import org.monflabs.nashorn.api.debugger.DebugException;
import org.monflabs.nashorn.api.debugger.Location;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ScriptObject;

/**
 * A breakpoint: a request, and the entries it resolved to so far.
 */
final class BreakpointImpl implements Breakpoint {
    final DebuggerImpl debugger;
    private final String id;
    private final BreakpointRequest request;
    private final Pattern urlPattern;
    private final List<Location> locations = Collections.synchronizedList(new ArrayList<>());
    private final List<ScriptInfo.Entry> entries = new ArrayList<>();
    private boolean conditionFailed;

    BreakpointImpl(final DebuggerImpl debugger, final String id, final BreakpointRequest request) {
        this.debugger = debugger;
        this.id = id;
        this.request = request;
        this.urlPattern = request.urlRegex() == null ? null : Pattern.compile(request.urlRegex());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public BreakpointRequest request() {
        return request;
    }

    @Override
    public List<Location> locations() {
        return List.copyOf(locations);
    }

    boolean matches(final DebugScriptImpl script) {
        if (request.scriptId() != null) {
            return request.scriptId().equals(script.id());
        }
        if (request.url() != null) {
            return request.url().equals(script.url());
        }
        if (urlPattern != null) {
            return urlPattern.matcher(script.url()).matches();
        }
        return false;
    }

    /** Resolves into a script; called under the debugger's lock. Returns the location, or null. */
    Location resolve(final DebugScriptImpl script) {
        final ScriptInfo.Entry entry = script.info().ceiling(request.line(), request.column());
        if (entry == null) {
            return null;
        }
        for (final ScriptInfo.Entry existing : entries) {
            if (existing == entry) {
                return null;
            }
        }
        entries.add(entry);
        attach(entry);
        final Location location = new Location(script, entry.line, entry.column);
        locations.add(location);
        return location;
    }

    private void attach(final ScriptInfo.Entry entry) {
        final BreakpointImpl[] current = entry.breakpoints;
        if (current == null) {
            entry.breakpoints = new BreakpointImpl[] { this };
        } else {
            final BreakpointImpl[] grown = Arrays.copyOf(current, current.length + 1);
            grown[current.length] = this;
            entry.breakpoints = grown;
        }
    }

    /** Detaches from every entry; called under the debugger's lock. */
    void detach() {
        for (final ScriptInfo.Entry entry : entries) {
            final BreakpointImpl[] current = entry.breakpoints;
            if (current == null) {
                continue;
            }
            final List<BreakpointImpl> kept = new ArrayList<>(current.length);
            for (final BreakpointImpl b : current) {
                if (b != this) {
                    kept.add(b);
                }
            }
            entry.breakpoints = kept.isEmpty() ? null : kept.toArray(new BreakpointImpl[0]);
        }
        entries.clear();
        locations.clear();
    }

    /** Whether the breakpoint pauses here: always, unless it has a condition that is not truthy. */
    boolean holds(final ShadowStack stack, final Frame frame) {
        final String condition = request.condition();
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        stack.inCommand = true;
        try {
            final Global global = Global.instance();
            final ScriptObject scope = frame.scope != null ? frame.scope : global;
            return JSType.toBoolean(DebuggerImpl.evalIn(global, scope, condition, frame.self));
        } catch (final DebugException e) {
            if (!conditionFailed) {
                conditionFailed = true;
                debugger.fireConsoleError("Breakpoint " + id + " condition failed: " + e.getMessage(), stack);
            }
            return false;
        } finally {
            stack.inCommand = false;
        }
    }
}
