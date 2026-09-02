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

package org.monflabs.nashorn.debugger.ui.panel;

import java.awt.Font;
import java.util.List;
import javax.swing.JComponent;
import org.monflabs.nashorn.debugger.ui.model.Breakpoint;
import org.monflabs.nashorn.debugger.ui.model.CallFrame;
import org.monflabs.nashorn.debugger.ui.model.ConsoleEntry;
import org.monflabs.nashorn.debugger.ui.model.ScriptInfo;
import org.monflabs.nashorn.debugger.ui.model.DebugSession;

/**
 * Builds the debugger's panels and binds each to the session, so the public
 * {@link org.monflabs.nashorn.debugger.ui.DebuggerPanel} in the neighbouring
 * package can assemble the layout without reaching into the package-private
 * panel classes itself. One instance per panel; the session lives as long.
 */
public final class PanelFactory {

    private final transient DebugSession session;
    private final transient ScriptNavigatorPanel navigator;
    private final transient SourcePanel source;
    private final transient CallStackPanel callStack;
    private final transient WatchPanel watches;
    private final transient ScopesPanel scopes;
    private final transient BreakpointListPanel breakpoints;
    private final transient DebugSidebarPanel sidebar;
    private final transient DebugConsolePanel console;

    /**
     * Builds the panels.
     * @param session the session they drive and listen to
     * @param mono the monospaced font
     * @param dark whether to use the dark theme
     */
    public PanelFactory(final DebugSession session, final Font mono, final boolean dark) {
        this.session = session;
        navigator = new ScriptNavigatorPanel(source0 -> this.source0(source0));
        source = new SourcePanel(mono, dark,
                session::toggleBreakpoint,
                (scriptId, whenReady) -> session.scriptSource(scriptId).thenAccept(whenReady));
        callStack = new CallStackPanel(session::selectFrame);
        watches = new WatchPanel(session::addWatch, session::removeWatch);
        scopes = new ScopesPanel((objectId, whenReady) -> session.properties(objectId).thenAccept(whenReady));
        breakpoints = new BreakpointListPanel(new BreakpointActions());
        sidebar = new DebugSidebarPanel(session, callStack, watches, scopes, breakpoints);
        console = new DebugConsolePanel(mono, this::evaluate);
    }

    private void source0(final org.monflabs.nashorn.debugger.ui.model.ScriptInfo script) {
        source.open(script);
    }

    private void evaluate(final String expression) {
        session.evaluate(expression).whenComplete((value, error) -> {
            if (error != null) {
                console.append(new ConsoleEntry(ConsoleEntry.Kind.ERROR, message(error)));
            } else {
                console.append(new ConsoleEntry(ConsoleEntry.Kind.EVAL_RESULT, value == null ? "undefined" : value.display()));
            }
        });
    }

    private static String message(final Throwable error) {
        final Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? String.valueOf(cause) : cause.getMessage();
    }

    // ---- layout accessors (the concrete panels stay package-private) ----

    /** The navigator component. @return the component */
    public JComponent navigator() {
        return navigator;
    }

    /** The source component. @return the component */
    public JComponent source() {
        return source;
    }

    /** The sidebar component. @return the component */
    public JComponent sidebar() {
        return sidebar;
    }

    /** The console component. @return the component */
    public JComponent console() {
        return console;
    }

    // ---- operations the DebuggerPanel drives ----

    /** Enables the stepping controls for a paused engine. @param paused whether paused */
    public void setControlsPaused(final boolean paused) {
        sidebar.setControlsPaused(paused);
    }

    /** Clears the execution pointer from every source view. */
    public void clearExecutionLine() {
        source.clearExecutionLine();
    }

    /** Adds a script to the navigator. @param script the script */
    public void addScript(final ScriptInfo script) {
        navigator.add(script);
    }

    /** Opens a script in the source view. @param script the script */
    public void openScript(final ScriptInfo script) {
        source.open(script);
    }

    /** Shows a paused stack and its scopes. @param frames the frames @param selected the selected index */
    public void showFrames(final List<CallFrame> frames, final int selected) {
        sidebar.showFrames(frames, selected);
    }

    /** Reflects a frame selection. @param index the index @param frames the frames */
    public void selectFrame(final int index, final List<CallFrame> frames) {
        sidebar.selectFrame(index, frames);
    }

    /** Shows the execution pointer. @param url the url @param line the line */
    public void showExecutionLine(final String url, final int line) {
        source.showExecutionLine(url, line);
    }

    /** Clears the paused state. */
    public void clearPaused() {
        sidebar.clearPaused();
    }

    /** Sets the breakpoint list and reconciles the source dots. @param breakpoints the breakpoints */
    public void setBreakpoints(final List<Breakpoint> breakpoints) {
        sidebar.breakpoints().setBreakpoints(breakpoints);
        source.syncBreakpoints(breakpoints);
    }

    /** Shows a watch's value. @param expression the expression @param value the value */
    public void setWatchValue(final String expression, final String value) {
        sidebar.watches().setValue(expression, value);
    }

    /** Appends a console line. @param entry the entry */
    public void consoleAppend(final ConsoleEntry entry) {
        console.append(entry);
    }

    /** Clears every panel to its empty state, for a fresh attach. */
    public void reset() {
        navigator.clear();
        source.clear();
        sidebar.clearPaused();
        console.clear();
    }

    private final class BreakpointActions implements BreakpointListPanel.Actions {
        @Override
        public void reveal(final String url, final int line) {
            for (final org.monflabs.nashorn.debugger.ui.model.ScriptInfo script : session.scripts()) {
                if (url.equals(script.url())) {
                    source.open(script);
                    break;
                }
            }
            source.showExecutionLine(url, line);
            source.clearExecutionLine();
        }

        @Override
        public void remove(final String url, final int line) {
            session.removeBreakpoint(url, line);
        }

        @Override
        public void removeAll() {
            for (final org.monflabs.nashorn.debugger.ui.model.Breakpoint bp : session.breakpoints()) {
                session.removeBreakpoint(bp.url(), bp.line());
            }
        }

        @Override
        public void setEnabled(final String url, final int line, final boolean on) {
            session.setBreakpointEnabled(url, line, on);
        }

        @Override
        public void setCondition(final String url, final int line, final String condition) {
            session.setBreakpointCondition(url, line, condition);
        }
    }
}
