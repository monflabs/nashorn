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
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.monflabs.js.debugger.ui.model.CallFrame;

/** The paused call stack, innermost first; selecting a frame drives the rest. */
final class CallStackPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<CallFrame> model = new DefaultListModel<>();
    private final transient JList<CallFrame> list = new JList<>(model);
    private boolean updating;

    CallStackPanel(final IntConsumer onSelect) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating && list.getSelectedIndex() >= 0) {
                onSelect.accept(list.getSelectedIndex());
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Shows a new stack, selecting the top frame. */
    void setFrames(final List<CallFrame> frames) {
        updating = true;
        model.clear();
        for (final CallFrame frame : frames) {
            model.addElement(frame);
        }
        if (!frames.isEmpty()) {
            list.setSelectedIndex(0);
        }
        updating = false;
    }

    /** Reflects the selected frame without firing the callback. */
    void select(final int index) {
        updating = true;
        list.setSelectedIndex(index);
        updating = false;
    }

    /** Empties the stack (resume). */
    void clear() {
        updating = true;
        model.clear();
        updating = false;
    }

    private static final class Renderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof CallFrame frame) {
                setText(frame.label());
            }
            return this;
        }
    }
}
