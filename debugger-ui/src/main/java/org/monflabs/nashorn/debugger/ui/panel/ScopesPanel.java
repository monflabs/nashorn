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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import org.monflabs.nashorn.debugger.ui.model.CallFrame;
import org.monflabs.nashorn.debugger.ui.model.PropertyEntry;
import org.monflabs.nashorn.debugger.ui.model.RemoteValue;
import org.monflabs.nashorn.debugger.ui.model.Scope;

/**
 * The selected frame's scopes as an expandable tree, like Chrome's Scope pane.
 * Each scope and each object is a node whose children are fetched lazily when
 * it is first expanded; the whole tree is cleared on resume, when the object
 * ids it holds go stale.
 */
final class ScopesPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Fetches an object's properties, calling back on the ui thread. */
    interface PropertySupplier {
        /** @param objectId the object @param whenReady receives the properties */
        void fetch(String objectId, Consumer<List<PropertyEntry>> whenReady);
    }

    private final transient PropertySupplier properties;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("scopes");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final transient JTree tree = new JTree(model);

    ScopesPanel(final PropertySupplier properties) {
        super(new BorderLayout());
        this.properties = properties;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(final TreeExpansionEvent event) throws ExpandVetoException {
                expand((DefaultMutableTreeNode)event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(final TreeExpansionEvent event) {
                // nothing
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    /** Shows a frame's scope chain. */
    void setFrame(final CallFrame frame) {
        root.removeAllChildren();
        if (frame != null) {
            for (final Scope scope : frame.scopeChain()) {
                final DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Node(scope.label(), scope.object()));
                if (scope.object() != null && scope.object().objectId() != null) {
                    node.add(new DefaultMutableTreeNode(Node.LOADING));
                }
                root.add(node);
            }
        }
        model.reload();
    }

    /** Empties the tree (resume: the ids are stale). */
    void clear() {
        root.removeAllChildren();
        model.reload();
    }

    private void expand(final DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1 || !(node.getFirstChild() instanceof DefaultMutableTreeNode child)
                || child.getUserObject() != Node.LOADING) {
            return;
        }
        final Object userObject = node.getUserObject();
        if (!(userObject instanceof Node info) || info.value == null || info.value.objectId() == null) {
            return;
        }
        properties.fetch(info.value.objectId(), entries -> {
            node.removeAllChildren();
            for (final PropertyEntry entry : entries) {
                final Node childNode = new Node(entry.name() + ": " + display(entry), entry.value());
                final DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(childNode);
                if (entry.value() != null && entry.value().expandable()) {
                    treeNode.add(new DefaultMutableTreeNode(Node.LOADING));
                }
                node.add(treeNode);
            }
            if (node.getChildCount() == 0) {
                node.add(new DefaultMutableTreeNode(new Node("(no properties)", null)));
            }
            model.reload(node);
            tree.expandPath(new TreePath(node.getPath()));
        });
    }

    private static String display(final PropertyEntry entry) {
        if (entry.wasThrown()) {
            return "(throws)";
        }
        return entry.value() == null ? "undefined" : entry.value().display();
    }

    /** A tree node's payload: its label and, when expandable, the value behind it. */
    private static final class Node {
        static final Object LOADING = "Loading…";
        final String label;
        final transient RemoteValue value;

        Node(final String label, final RemoteValue value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
