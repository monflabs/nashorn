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

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.monflabs.nashorn.debugger.ui.model.ScriptInfo;

/**
 * The list of scripts, one row per url (the newest script id for that url
 * wins), like Chrome's file navigator. Selecting one opens it in the source
 * panel.
 */
final class ScriptNavigatorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient Map<String, ScriptInfo> byUrl = new LinkedHashMap<>();
    private final DefaultListModel<ScriptInfo> model = new DefaultListModel<>();
    private final transient JList<ScriptInfo> list = new JList<>(model);

    ScriptNavigatorPanel(final Consumer<ScriptInfo> onOpen) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                onOpen.accept(list.getSelectedValue());
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /** Adds or refreshes a script row, keyed by url. */
    void add(final ScriptInfo script) {
        if (script.url() == null) {
            return;
        }
        final boolean known = byUrl.containsKey(script.url());
        byUrl.put(script.url(), script);
        if (!known) {
            model.addElement(script);
        } else {
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).url().equals(script.url())) {
                    model.set(i, script);
                    break;
                }
            }
        }
    }

    /** Clears the list (a fresh attach). */
    void clear() {
        byUrl.clear();
        model.clear();
    }

    private static final class Renderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof ScriptInfo script) {
                setText(script.shortName());
                setToolTipText(script.url());
            }
            return this;
        }
    }
}
