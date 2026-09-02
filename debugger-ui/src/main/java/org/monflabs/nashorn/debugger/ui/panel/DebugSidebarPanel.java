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

package org.monflabs.nashorn.debugger.ui.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import org.monflabs.nashorn.debugger.ui.model.CallFrame;
import org.monflabs.nashorn.debugger.ui.model.DebugSession;

/**
 * The right-hand column of the Sources layout: the transport toolbar over the
 * call stack, watches, scopes and breakpoints, each in its own titled section.
 */
final class DebugSidebarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient DebugSession session;
    private final JButton resume = new JButton(DebuggerIcons.resume());
    private final JButton stepOver = new JButton(DebuggerIcons.stepOver());
    private final JButton stepInto = new JButton(DebuggerIcons.stepInto());
    private final JButton stepOut = new JButton(DebuggerIcons.stepOut());
    private final JToggleButton deactivate = new JToggleButton("Deactivate breakpoints");
    private final JComboBox<String> exceptions = new JComboBox<>(new String[] {
        "Don't pause on exceptions", "Pause on uncaught exceptions", "Pause on all exceptions" });

    private final transient CallStackPanel callStack;
    private final transient WatchPanel watches;
    private final transient ScopesPanel scopes;
    private final transient BreakpointListPanel breakpoints;

    DebugSidebarPanel(final DebugSession session, final CallStackPanel callStack, final WatchPanel watches,
                      final ScopesPanel scopes, final BreakpointListPanel breakpoints) {
        super(new BorderLayout());
        this.session = session;
        this.callStack = callStack;
        this.watches = watches;
        this.scopes = scopes;
        this.breakpoints = breakpoints;

        add(toolbar(), BorderLayout.NORTH);
        add(sections(), BorderLayout.CENTER);
        setControlsPaused(false);
    }

    private JToolBar toolbar() {
        final JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        resume.setToolTipText("Resume (F8)");
        resume.addActionListener(a -> session.resume());
        stepOver.setToolTipText("Step over (F10)");
        stepOver.addActionListener(a -> session.stepOver());
        stepInto.setToolTipText("Step into (F11)");
        stepInto.addActionListener(a -> session.stepInto());
        stepOut.setToolTipText("Step out (Shift+F11)");
        stepOut.addActionListener(a -> session.stepOut());
        // the glyphs paint their own disabled state from the button's enabled
        // flag, so pin the disabled icon to the same one - otherwise Swing
        // synthesises a second grey version and an enabled button still looks off
        for (final JButton control : new JButton[] {resume, stepOver, stepInto, stepOut}) {
            control.setDisabledIcon(control.getIcon());
        }
        bar.add(resume);
        bar.add(stepOver);
        bar.add(stepInto);
        bar.add(stepOut);
        bar.addSeparator();
        deactivate.setToolTipText("Deactivate all breakpoints");
        deactivate.addActionListener(a -> session.setBreakpointsActive(!deactivate.isSelected()));
        bar.add(deactivate);
        bar.add(Box.createHorizontalGlue());
        exceptions.addActionListener(a -> session.setPauseOnExceptions(
                switch (exceptions.getSelectedIndex()) {
                    case 1 -> "uncaught";
                    case 2 -> "all";
                    default -> "none";
                }));
        bar.add(exceptions);
        return bar;
    }

    private JComponent sections() {
        final JSplitPane lower = split(titled("Scopes", scopes, null), titled("Breakpoints", breakpoints, null), 0.6);
        final JSplitPane middle = split(titled("Watch", watches, watches.addButton()), lower, 0.3);
        return split(titled("Call Stack", callStack, null), middle, 0.3);
    }

    private static JSplitPane split(final JComponent top, final JComponent bottom, final double weight) {
        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(weight);
        split.setContinuousLayout(true);
        return split;
    }

    /**
     * Wraps a component with a small title, like the playground's single-tab
     * idiom, optionally with a control stuck to the right of the title.
     */
    private static JComponent titled(final String title, final JComponent content, final JComponent right) {
        final JPanel panel = new JPanel(new BorderLayout());
        final JPanel header = new JPanel(new BorderLayout());
        final JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, label.getFont().getSize() - 1f));
        header.add(label, BorderLayout.CENTER);
        if (right != null) {
            final JPanel east = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 1));
            east.add(right);
            header.add(east, BorderLayout.EAST);
        }
        panel.add(header, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /** Enables the stepping controls only when paused; resume too. */
    void setControlsPaused(final boolean paused) {
        resume.setEnabled(paused);
        stepOver.setEnabled(paused);
        stepInto.setEnabled(paused);
        stepOut.setEnabled(paused);
    }

    /** Shows a paused frame's stack and scopes. */
    void showFrames(final List<CallFrame> frames, final int selected) {
        callStack.setFrames(frames);
        if (selected >= 0 && selected < frames.size()) {
            scopes.setFrame(frames.get(selected));
        }
    }

    /** Reflects a frame selection. */
    void selectFrame(final int index, final List<CallFrame> frames) {
        callStack.select(index);
        if (index >= 0 && index < frames.size()) {
            scopes.setFrame(frames.get(index));
        }
    }

    /** Clears the paused state (resume). */
    void clearPaused() {
        callStack.clear();
        scopes.clear();
        watches.clearValues();
    }

    CallStackPanel callStack() {
        return callStack;
    }

    WatchPanel watches() {
        return watches;
    }

    ScopesPanel scopes() {
        return scopes;
    }

    BreakpointListPanel breakpoints() {
        return breakpoints;
    }
}
