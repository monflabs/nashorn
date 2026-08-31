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


package org.monflabs.nashorn.playground.ui;

import java.awt.BorderLayout;
import java.util.Enumeration;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.SampleLibrary;

/**
 * The library as a tree, with a filter box above it.
 */
public final class SampleTree extends JPanel {
    private static final long serialVersionUID = 1L;

    /** The scratchpad's id. */
    public static final String SCRATCH_ID = "_scratch";

    private final transient SampleLibrary library;
    private final transient Sample scratch;
    private final JTree tree = new JTree();
    private final JTextField filter = new JTextField();
    private transient Consumer<Sample> onSelect = s -> {};
    private boolean selecting;

    /** A tree node's payload. */
    private record Item(String name, Sample sample) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Creates the tree.
     * @param library the library
     * @param scratch the scratchpad sample, shown first
     */
    public SampleTree(final SampleLibrary library, final Sample scratch) {
        super(new BorderLayout());
        this.library = library;
        this.scratch = scratch;
        filter.putClientProperty("JTextField.placeholderText", "Filter samples");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(final DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(final DocumentEvent e) { rebuild(); }
        });
        add(filter, BorderLayout.NORTH);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> {
            if (selecting) {
                return;
            }
            final Object node = tree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof Item item && item.sample() != null) {
                onSelect.accept(item.sample());
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);
        rebuild();
    }

    /** What to do when a sample is chosen. */
    public void onSelect(final Consumer<Sample> action) {
        this.onSelect = action;
    }

    private void rebuild() {
        final String needle = filter.getText().trim().toLowerCase(Locale.ROOT);
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Item("Samples", null));
        root.add(new DefaultMutableTreeNode(new Item(scratch.title(), scratch)));
        for (final SampleLibrary.Node child : library.root().children()) {
            final DefaultMutableTreeNode n = build(child, needle);
            if (n != null) {
                root.add(n);
            }
        }
        tree.setModel(new DefaultTreeModel(root));
        if (!needle.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } else {
            for (int i = 0; i < root.getChildCount(); i++) {
                tree.expandPath(new TreePath(((DefaultMutableTreeNode)root.getChildAt(i)).getPath()));
            }
        }
    }

    private static DefaultMutableTreeNode build(final SampleLibrary.Node node, final String needle) {
        if (node.isSample()) {
            return needle.isEmpty() || node.name().toLowerCase(Locale.ROOT).contains(needle)
                    || node.sample().source().toLowerCase(Locale.ROOT).contains(needle)
                    ? new DefaultMutableTreeNode(new Item(node.name(), node.sample())) : null;
        }
        final DefaultMutableTreeNode n = new DefaultMutableTreeNode(new Item(node.name(), null));
        for (final SampleLibrary.Node child : node.children()) {
            final DefaultMutableTreeNode c = build(child, needle);
            if (c != null) {
                n.add(c);
            }
        }
        return n.getChildCount() == 0 ? null : n;
    }

    /**
     * Selects a sample by id, if it is in the tree.
     * @param id the sample id
     * @return true if found
     */
    public boolean select(final String id) {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
        for (final Enumeration<?> e = root.depthFirstEnumeration(); e.hasMoreElements();) {
            final DefaultMutableTreeNode n = (DefaultMutableTreeNode)e.nextElement();
            if (n.getUserObject() instanceof Item item && item.sample() != null && item.sample().id().equals(id)) {
                final TreePath path = new TreePath(n.getPath());
                selecting = true;
                try {
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                } finally {
                    selecting = false;
                }
                onSelect.accept(item.sample());
                return true;
            }
        }
        return false;
    }
}
