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

package org.monflabs.js.debugger.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.monflabs.js.debugger.ui.model.Breakpoint;
import org.monflabs.js.debugger.ui.model.CallFrame;
import org.monflabs.js.debugger.ui.model.ConsoleEntry;
import org.monflabs.js.debugger.ui.model.DebugSession;
import org.monflabs.js.debugger.ui.model.PauseState;
import org.monflabs.js.debugger.ui.model.RemoteValue;
import org.monflabs.js.debugger.ui.model.ScriptInfo;
import org.monflabs.js.debugger.ui.panel.PanelFactory;

/**
 * An embeddable debugger, laid out like Chrome DevTools' Sources panel: a
 * script navigator, a source view with breakpoints and an execution pointer, a
 * sidebar of call stack, watches, scopes and breakpoints, and a console. It
 * speaks the Chrome DevTools Protocol over a WebSocket, so it attaches to any
 * engine running with {@code --inspect} - not only this one.
 *
 * <p>Put it in any container, call {@link #attach} with the {@code ws://} url
 * the server published, and {@link #detach} or {@link #close} when done. It
 * keeps one session for its whole life, so breakpoints set on it survive a
 * detach and reattach. Everything happens on the event dispatch thread.
 */
public final class DebuggerPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;

    /** The connection's state, for a host that wants to reflect it. */
    public enum ConnectionState {
        /** Not attached. */
        DISCONNECTED,
        /** Attaching. */
        CONNECTING,
        /** Attached. */
        CONNECTED,
        /** The last attach failed - the message says why. */
        FAILED
    }

    private final transient DebugSession session;
    private final transient PanelFactory panels;
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton reattach = new JButton("Reattach");
    private transient BiConsumer<ConnectionState, String> onConnection = (state, detail) -> { };
    private String lastUrl;

    /**
     * Creates a panel.
     * @param mono the monospaced font for source and console
     * @param dark whether to use the dark editor theme
     */
    public DebuggerPanel(final Font mono, final boolean dark) {
        super(new BorderLayout());
        session = new DebugSession(SwingUtilities::invokeLater);
        panels = new PanelFactory(session, mono, dark);
        session.addListener(new UiListener());

        add(center(), BorderLayout.CENTER);
        add(statusStrip(), BorderLayout.SOUTH);
    }

    private JComponent center() {
        final JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panels.navigator(),
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panels.source(), panels.sidebar()));
        ((JSplitPane)top.getRightComponent()).setResizeWeight(1.0);
        ((JSplitPane)top.getRightComponent()).setDividerLocation(560);
        top.setResizeWeight(0.0);
        top.setDividerLocation(200);
        panels.navigator().setMinimumSize(new Dimension(120, 0));
        panels.sidebar().setMinimumSize(new Dimension(220, 0));

        final JSplitPane outer = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, panels.console());
        outer.setResizeWeight(0.72);
        outer.setDividerLocation(420);
        outer.setContinuousLayout(true);
        return outer;
    }

    private JComponent statusStrip() {
        final JPanel strip = new JPanel(new BorderLayout());
        strip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        strip.add(statusLabel, BorderLayout.CENTER);
        reattach.setVisible(false);
        reattach.addActionListener(a -> {
            if (lastUrl != null) {
                attach(lastUrl);
            }
        });
        strip.add(reattach, BorderLayout.EAST);
        return strip;
    }

    /**
     * Attaches to a server. Reattaching the same url while connected does
     * nothing; a different url detaches first.
     * @param wsUrl the {@code ws://host:port/...} url
     */
    public void attach(final String wsUrl) {
        lastUrl = wsUrl;
        panels.reset();
        session.attach(wsUrl);
    }

    /** Detaches: resumes a paused script first, then disconnects. */
    public void detach() {
        session.detach();
    }

    /** Detaches and releases the session; the panel is done. */
    @Override
    public void close() {
        session.close();
    }

    /**
     * Registers a listener for connection-state changes, for a host that shows
     * its own status. Called on the event thread.
     * @param listener the listener; the detail is the failure message when failed
     */
    public void onConnectionChange(final BiConsumer<ConnectionState, String> listener) {
        this.onConnection = listener == null ? (state, detail) -> { } : listener;
    }

    /** The session, for tests. */
    DebugSession session() {
        return session;
    }

    private final class UiListener implements DebugSession.SessionListener {
        @Override
        public void stateChanged(final DebugSession.State state) {
            switch (state) {
            case CONNECTING -> {
                setStatus("Connecting…", false);
                onConnection.accept(ConnectionState.CONNECTING, null);
            }
            case RUNNING -> {
                panels.setControlsPaused(false);
                panels.clearExecutionLine();
                setStatus("Connected — running", false);
                onConnection.accept(ConnectionState.CONNECTED, null);
            }
            case PAUSED -> {
                panels.setControlsPaused(true);
                setStatus("Paused", false);
            }
            case DETACHED -> {
                panels.setControlsPaused(false);
                panels.clearExecutionLine();
            }
            default -> { }
            }
        }

        @Override
        public void scriptAdded(final ScriptInfo script) {
            panels.addScript(script);
            // open the first script so there is something to see
            if (session.scripts().size() == 1) {
                panels.openScript(script);
            }
        }

        @Override
        public void scriptsCleared() {
            // the engine's script registry was cleared (e.g. a new run); drop the
            // Sources view, keeping breakpoints, which re-resolve as scripts parse.
            panels.clearScripts();
        }

        @Override
        public void paused(final PauseState pause) {
            final List<CallFrame> frames = pause.frames();
            panels.showFrames(frames, 0);
            if (!frames.isEmpty()) {
                final CallFrame top = frames.get(0);
                panels.openScript(scriptFor(top));
                panels.showExecutionLine(top.url(), top.line());
            }
            if (pause.exception() != null) {
                panels.consoleAppend(new ConsoleEntry(ConsoleEntry.Kind.ERROR, "Paused on exception: " + pause.exception().display()));
            }
            evaluateWatches();
        }

        @Override
        public void resumed() {
            panels.clearExecutionLine();
            panels.clearPaused();
        }

        @Override
        public void frameSelected(final int index) {
            if (session.pauseState() != null) {
                final List<CallFrame> frames = session.pauseState().frames();
                panels.selectFrame(index, frames);
                if (index >= 0 && index < frames.size()) {
                    final CallFrame frame = frames.get(index);
                    panels.showExecutionLine(frame.url(), frame.line());
                }
                evaluateWatches();
            }
        }

        @Override
        public void breakpointsChanged(final List<Breakpoint> breakpoints) {
            panels.setBreakpoints(breakpoints);
        }

        @Override
        public void consoleEntry(final ConsoleEntry entry) {
            panels.consoleAppend(entry);
        }

        @Override
        public void connectionClosed(final String reason) {
            final boolean busy = reason != null && reason.contains("already attached");
            setStatus(busy ? "Another client is already attached" : "Disconnected — " + reason, true);
            onConnection.accept(busy ? ConnectionState.FAILED : ConnectionState.DISCONNECTED, reason);
        }
    }

    private ScriptInfo scriptFor(final CallFrame frame) {
        for (final ScriptInfo script : session.scripts()) {
            if (frame.url() != null && frame.url().equals(script.url())) {
                return script;
            }
        }
        return new ScriptInfo(frame.scriptId(), frame.url(), 0, false);
    }

    private void evaluateWatches() {
        for (final String expr : session.watches()) {
            session.evaluate(expr).whenComplete((value, error) -> SwingUtilities.invokeLater(() ->
                    panels.setWatchValue(expr, error != null ? "(unavailable)"
                            : (value == null ? "undefined" : value.display()))));
        }
    }

    private void setStatus(final String text, final boolean showReattach) {
        statusLabel.setText(text);
        reattach.setVisible(showReattach && lastUrl != null);
    }
}
