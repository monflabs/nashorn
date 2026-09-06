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

package org.monflabs.js.debugger.ui.panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import org.monflabs.js.debugger.ui.model.Breakpoint;

/**
 * The breakpoint list: each with its enabled state, double-click to reveal,
 * and a context menu to remove, remove all, or edit a condition.
 */
final class BreakpointListPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** What the panel asks the session to do. */
    interface Actions {
        /** @param url the url @param line the line reveal a breakpoint in the source */
        void reveal(String url, int line);
        /** @param url the url @param line the line remove a breakpoint */
        void remove(String url, int line);
        /** remove every breakpoint */
        void removeAll();
        /** @param url the url @param line the line @param on enable or disable a breakpoint */
        void setEnabled(String url, int line, boolean on);
        /** @param url the url @param line the line @param condition the condition, or null set a condition */
        void setCondition(String url, int line, String condition);
    }

    private final DefaultListModel<Breakpoint> model = new DefaultListModel<>();
    private final transient JList<Breakpoint> list = new JList<>(model);
    private final transient Actions actions;

    BreakpointListPanel(final Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                final int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                final Breakpoint bp = model.get(index);
                if (e.getClickCount() == 2) {
                    actions.reveal(bp.url(), bp.line());
                }
            }

            @Override
            public void mousePressed(final MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                maybePopup(e);
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private void maybePopup(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        final int index = list.locationToIndex(e.getPoint());
        final JPopupMenu menu = new JPopupMenu();
        if (index >= 0) {
            list.setSelectedIndex(index);
            final Breakpoint bp = model.get(index);
            final JMenuItem toggle = new JMenuItem(bp.enabled() ? "Disable" : "Enable");
            toggle.addActionListener(a -> actions.setEnabled(bp.url(), bp.line(), !bp.enabled()));
            menu.add(toggle);
            final JMenuItem condition = new JMenuItem("Edit condition…");
            condition.addActionListener(a -> {
                final String current = bp.condition() == null ? "" : bp.condition();
                final String entered = JOptionPane.showInputDialog(this, "Pause only when this expression is truthy:", current);
                if (entered != null) {
                    actions.setCondition(bp.url(), bp.line(), entered.isEmpty() ? null : entered);
                }
            });
            menu.add(condition);
            final JMenuItem remove = new JMenuItem("Remove");
            remove.addActionListener(a -> actions.remove(bp.url(), bp.line()));
            menu.add(remove);
            menu.addSeparator();
        }
        final JMenuItem removeAll = new JMenuItem("Remove all");
        removeAll.addActionListener(a -> actions.removeAll());
        removeAll.setEnabled(!model.isEmpty());
        menu.add(removeAll);
        menu.show(list, e.getX(), e.getY());
    }

    /** Shows the current breakpoints. */
    void setBreakpoints(final List<Breakpoint> breakpoints) {
        model.clear();
        for (final Breakpoint bp : breakpoints) {
            model.addElement(bp);
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Breakpoint bp) {
                setText((bp.enabled() ? "● " : "○ ") + bp.label());
                setEnabled(bp.enabled());
            }
            return this;
        }
    }
}
