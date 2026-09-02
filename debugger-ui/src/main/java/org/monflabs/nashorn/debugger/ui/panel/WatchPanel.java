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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;

/**
 * Watch expressions, re-evaluated at every pause, like Chrome's Watch pane. Add
 * and remove with the toolbar; each row shows the expression and its latest
 * value.
 */
final class WatchPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final transient JList<String> list = new JList<>(model);
    private final transient Map<String, String> values = new LinkedHashMap<>();
    private final transient Consumer<String> onAdd;
    private final transient Consumer<String> onRemove;

    WatchPanel(final Consumer<String> onAdd, final Consumer<String> onRemove) {
        super(new BorderLayout());
        this.onAdd = onAdd;
        this.onRemove = onRemove;
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                maybeRemove(e);
            }

            @Override
            public void mouseReleased(final MouseEvent e) {
                maybeRemove(e);
            }
        });

        final JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        final JButton add = new JButton("+");
        add.setToolTipText("Add a watch expression");
        add.addActionListener(a -> {
            final String expr = JOptionPane.showInputDialog(this, "Expression to watch:");
            if (expr != null && !expr.isEmpty()) {
                addExpression(expr);
                onAdd.accept(expr);
            }
        });
        bar.add(add);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private void maybeRemove(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        final int index = list.locationToIndex(e.getPoint());
        if (index >= 0) {
            final String expr = model.get(index);
            model.remove(index);
            values.remove(expr);
            onRemove.accept(expr);
        }
    }

    private void addExpression(final String expr) {
        if (!contains(expr)) {
            model.addElement(expr);
        }
    }

    private boolean contains(final String expr) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).equals(expr)) {
                return true;
            }
        }
        return false;
    }

    /** Sets a watch's shown value. */
    void setValue(final String expr, final String value) {
        values.put(expr, value);
        list.repaint();
    }

    /** Clears the shown values (resume/detach), keeping the expressions. */
    void clearValues() {
        values.clear();
        list.repaint();
    }

    /** The current expressions, in order. */
    List<String> expressions() {
        return java.util.Collections.list(model.elements());
    }

    private final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            final String expr = String.valueOf(value);
            final String shown = values.get(expr);
            setText(shown == null ? expr : expr + ": " + shown);
            return this;
        }
    }
}
