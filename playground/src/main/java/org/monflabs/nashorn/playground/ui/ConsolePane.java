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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.monflabs.nashorn.playground.ScriptRunner;

/**
 * The console: output in the text colour, errors in red, values in the echo
 * mode in a muted colour next to their line, and a status line beneath. Fed
 * from the worker thread, painted on the event thread, in batches.
 */
public final class ConsolePane extends JPanel implements ScriptRunner.Console {
    private static final long serialVersionUID = 1L;
    private static final int LIMIT = 400_000;

    /** A pending piece of output; a null text only moves to the line. */
    private record Chunk(SimpleAttributeSet style, int line, String text) {}

    private final JTextPane text = new JTextPane() {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return wrap || getParent() == null || getUI().getPreferredSize(this).width <= getParent().getSize().width;
        }
    };
    private final JLabel status = new JLabel(" ");
    private final SimpleAttributeSet plain = new SimpleAttributeSet();
    private final SimpleAttributeSet error = new SimpleAttributeSet();
    private final SimpleAttributeSet value = new SimpleAttributeSet();
    private final transient List<Chunk> pending = new ArrayList<>();
    private boolean flushScheduled;
    private boolean wrap = true;

    /**
     * Creates the pane.
     * @param font the monospaced font
     */
    public ConsolePane(final Font font) {
        super(new BorderLayout());
        text.setEditable(false);
        text.setFont(font);
        StyleConstants.setForeground(error, new Color(0xd0, 0x40, 0x40));
        StyleConstants.setForeground(value, new Color(0x60, 0x90, 0xc0));
        StyleConstants.setItalic(value, true);
        final JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(400, 200));
        add(scroll, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);
    }

    @Override
    public void out(final String s) {
        append(new Chunk(plain, -1, s));
    }

    @Override
    public void err(final String s) {
        append(new Chunk(error, -1, s));
    }

    @Override
    public void valueAtLine(final int line, final String s) {
        append(new Chunk(value, line, s));
    }

    @Override
    public void statementAt(final int line) {
        append(new Chunk(plain, line, null));
    }

    private void append(final Chunk chunk) {
        synchronized (pending) {
            pending.add(chunk);
            if (!flushScheduled) {
                flushScheduled = true;
                SwingUtilities.invokeLater(this::flush);
            }
        }
    }

    private void flush() {
        final List<Chunk> chunks;
        synchronized (pending) {
            chunks = new ArrayList<>(pending);
            pending.clear();
            flushScheduled = false;
        }
        final StyledDocument doc = text.getStyledDocument();
        try {
            for (final Chunk chunk : chunks) {
                if (chunk.text() == null) {
                    padToLine(doc, chunk.line());
                } else if (chunk.line() >= 0) {
                    padToLine(doc, chunk.line());
                    doc.insertString(doc.getLength(), "  // " + chunk.text() + "\n", value);
                } else {
                    doc.insertString(doc.getLength(), chunk.text(), chunk.style());
                }
            }
            if (doc.getLength() > LIMIT) {
                doc.remove(0, doc.getLength() - LIMIT * 3 / 4);
            }
        } catch (final BadLocationException e) {
            // the document is ours; cannot happen
        }
        text.setCaretPosition(doc.getLength());
    }

    /**
     * Pads with blank lines so that what comes next lands on the source line it
     * belongs to - as far as possible: output that already ran past that line
     * stays where it is.
     */
    private void padToLine(final StyledDocument doc, final int line) throws BadLocationException {
        final String all = doc.getText(0, doc.getLength());
        int lines = 0;
        for (int i = 0; i < all.length(); i++) {
            if (all.charAt(i) == '\n') {
                lines++;
            }
        }
        if (!all.isEmpty() && !all.endsWith("\n")) {
            doc.insertString(doc.getLength(), "\n", plain);
            lines++;
        }
        while (lines < line) {
            doc.insertString(doc.getLength(), "\n", plain);
            lines++;
        }
    }

    /** Waits for the pending output to be painted; for tests. */
    void flushNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            flush();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::flush);
            } catch (final InterruptedException | java.lang.reflect.InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Empties the console. */
    public void clear() {
        synchronized (pending) {
            pending.clear();
        }
        text.setText("");
    }

    /** Sets the status line. */
    public void status(final String s) {
        SwingUtilities.invokeLater(() -> status.setText(s));
    }

    /** Whether long lines wrap. */
    public void setWrap(final boolean wrap) {
        this.wrap = wrap;
        text.revalidate();
        text.repaint();
    }

    /** The console's text. */
    public String getText() {
        return text.getText();
    }
}
