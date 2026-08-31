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

import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.rsta.ui.GoToDialog;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.ReplaceDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/**
 * The editor: a tab for the script and one, read-only, for each other file
 * of the sample. RSyntaxTextArea with JavaScript colouring, folding, bracket
 * matching, and Find/Replace/Go-to dialogs.
 */
public final class EditorPane extends JTabbedPane implements SearchListener {
    private static final long serialVersionUID = 1L;

    private final Font font;
    private final boolean dark;
    private final Frame owner;
    private RSyntaxTextArea main;
    private FindDialog findDialog;
    private ReplaceDialog replaceDialog;
    private transient Runnable onEdit = () -> {};

    /**
     * Creates the editor.
     * @param owner the window, for the dialogs
     * @param font the monospaced font
     * @param dark whether the theme is dark
     */
    public EditorPane(final Frame owner, final Font font, final boolean dark) {
        this.owner = owner;
        this.font = font;
        this.dark = dark;
    }

    /** What to run when the script changes. */
    public void onEdit(final Runnable action) {
        this.onEdit = action;
    }

    /**
     * Shows a sample's files.
     * @param source the script
     * @param files the other files, by name
     * @param editable whether the script may be edited
     */
    public void show(final String source, final Map<String, String> files, final boolean editable) {
        removeAll();
        main = area(source, editable);
        main.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent e) { onEdit.run(); }
            @Override public void removeUpdate(final DocumentEvent e) { onEdit.run(); }
            @Override public void changedUpdate(final DocumentEvent e) { onEdit.run(); }
        });
        addTab("main.js", scroll(main));
        for (final Map.Entry<String, String> entry : files.entrySet()) {
            final RSyntaxTextArea other = area(entry.getValue(), false);
            other.setSyntaxEditingStyle(styleFor(entry.getKey()));
            addTab(entry.getKey(), scroll(other));
        }
        setSelectedIndex(0);
    }

    private static String styleFor(final String name) {
        if (name.endsWith(".js")) {
            return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
        }
        if (name.endsWith(".json")) {
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        }
        if (name.endsWith(".java")) {
            return SyntaxConstants.SYNTAX_STYLE_JAVA;
        }
        if (name.endsWith(".xml") || name.endsWith(".html")) {
            return SyntaxConstants.SYNTAX_STYLE_XML;
        }
        if (name.endsWith(".md")) {
            return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    private RSyntaxTextArea area(final String text, final boolean editable) {
        final RSyntaxTextArea area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        area.setCodeFoldingEnabled(true);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setAutoIndentEnabled(true);
        area.setCloseCurlyBraces(true);
        area.setTabSize(4);
        area.setTabsEmulated(true);
        area.setHighlightCurrentLine(true);
        area.setMarkOccurrences(true);
        area.setEditable(editable);
        applyTheme(area);
        area.setFont(font);
        area.setText(text);
        area.setCaretPosition(0);
        area.discardAllEdits();
        final int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(area, KeyEvent.VK_F, menu, "playground.find", this::find);
        bind(area, KeyEvent.VK_R, menu, "playground.replace", this::replace);
        bind(area, KeyEvent.VK_G, menu, "playground.goto", () -> goTo(area));
        return area;
    }

    private static void bind(final RSyntaxTextArea area, final int key, final int modifiers, final String name, final Runnable action) {
        area.getInputMap().put(KeyStroke.getKeyStroke(key, modifiers), name);
        area.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(final ActionEvent e) {
                action.run();
            }
        });
    }

    private void applyTheme(final RSyntaxTextArea area) {
        final String name = dark ? "dark.xml" : "default.xml";
        try (InputStream in = Theme.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + name)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (final IOException ignored) {
            // the default look then
        }
    }

    private static RTextScrollPane scroll(final RSyntaxTextArea area) {
        final RTextScrollPane scroll = new RTextScrollPane(area, true);
        scroll.setFoldIndicatorEnabled(true);
        return scroll;
    }

    /** The script's text. */
    public String getSource() {
        return main == null ? "" : main.getText();
    }

    /** The script's editor, for focus. */
    public RSyntaxTextArea getMainArea() {
        return main;
    }

    private RSyntaxTextArea current() {
        final Component c = getSelectedComponent();
        if (c instanceof RTextScrollPane s && s.getTextArea() instanceof RSyntaxTextArea a) {
            return a;
        }
        return main;
    }

    /** Opens the Find dialog. */
    public void find() {
        if (findDialog == null) {
            findDialog = new FindDialog(owner, this);
        }
        if (replaceDialog != null && replaceDialog.isVisible()) {
            replaceDialog.setVisible(false);
        }
        findDialog.setVisible(true);
    }

    /** Opens the Replace dialog. */
    public void replace() {
        if (replaceDialog == null) {
            replaceDialog = new ReplaceDialog(owner, this);
        }
        if (findDialog != null && findDialog.isVisible()) {
            findDialog.setVisible(false);
        }
        replaceDialog.setVisible(true);
    }

    private void goTo(final RSyntaxTextArea area) {
        final GoToDialog dialog = new GoToDialog(owner);
        dialog.setMaxLineNumberAllowed(area.getLineCount());
        dialog.setVisible(true);
        final int line = dialog.getLineNumber();
        if (line > 0) {
            try {
                area.setCaretPosition(area.getLineStartOffset(line - 1));
            } catch (final BadLocationException ignored) {
                // out of range
            }
        }
    }

    @Override
    public void searchEvent(final SearchEvent e) {
        final SearchContext context = e.getSearchContext();
        final RSyntaxTextArea area = current();
        if (area == null) {
            return;
        }
        final SearchResult result;
        switch (e.getType()) {
        case MARK_ALL -> result = SearchEngine.markAll(area, context);
        case FIND -> result = SearchEngine.find(area, context);
        case REPLACE -> result = SearchEngine.replace(area, context);
        case REPLACE_ALL -> result = SearchEngine.replaceAll(area, context);
        default -> result = null;
        }
        if (result != null && !result.wasFound() && e.getType() != SearchEvent.Type.MARK_ALL) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public String getSelectedText() {
        final RSyntaxTextArea area = current();
        return area == null ? null : area.getSelectedText();
    }
}
