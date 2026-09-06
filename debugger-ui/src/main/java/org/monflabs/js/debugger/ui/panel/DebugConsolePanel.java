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
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.monflabs.js.debugger.ui.model.ConsoleEntry;

/**
 * The debugger console: script output and evaluation results above, a prompt
 * below. What the user types is evaluated on the selected frame when paused, or
 * in the global context when running, through the supplied evaluator. Output is
 * batched onto the event thread, like the playground's console.
 */
final class DebugConsolePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int LIMIT = 400_000;

    private final JTextPane text = new JTextPane();
    private final JTextField prompt = new JTextField();
    private final transient SimpleAttributeSet plain = new SimpleAttributeSet();
    private final transient SimpleAttributeSet error = new SimpleAttributeSet();
    private final transient SimpleAttributeSet input = new SimpleAttributeSet();
    private final transient SimpleAttributeSet result = new SimpleAttributeSet();
    private final transient List<ConsoleEntry> pending = new ArrayList<>();
    private final transient Consumer<String> evaluator;
    private boolean flushScheduled;

    DebugConsolePanel(final Font font, final Consumer<String> evaluator) {
        super(new BorderLayout());
        this.evaluator = evaluator;
        text.setEditable(false);
        text.setFont(font);
        prompt.setFont(font);
        StyleConstants.setForeground(error, new Color(0xD0, 0x40, 0x40));
        StyleConstants.setForeground(input, new Color(0x60, 0x90, 0xC0));
        StyleConstants.setForeground(result, new Color(0x50, 0x80, 0x50));
        prompt.addActionListener(this::onEnter);
        add(new JScrollPane(text), BorderLayout.CENTER);
        add(prompt, BorderLayout.SOUTH);
    }

    private void onEnter(final ActionEvent e) {
        final String expr = prompt.getText().trim();
        if (expr.isEmpty()) {
            return;
        }
        prompt.setText("");
        append(new ConsoleEntry(ConsoleEntry.Kind.EVAL_INPUT, "› " + expr));
        evaluator.accept(expr);
    }

    /** Adds a console line; safe from any thread. */
    void append(final ConsoleEntry entry) {
        synchronized (pending) {
            pending.add(entry);
            if (!flushScheduled) {
                flushScheduled = true;
                SwingUtilities.invokeLater(this::flush);
            }
        }
    }

    private void flush() {
        final List<ConsoleEntry> batch;
        synchronized (pending) {
            batch = new ArrayList<>(pending);
            pending.clear();
            flushScheduled = false;
        }
        final StyledDocument doc = text.getStyledDocument();
        try {
            for (final ConsoleEntry entry : batch) {
                doc.insertString(doc.getLength(), entry.text() + "\n", styleFor(entry.kind()));
            }
            if (doc.getLength() > LIMIT) {
                doc.remove(0, doc.getLength() - LIMIT * 3 / 4);
            }
        } catch (final BadLocationException ignored) {
            // the document is ours
        }
        text.setCaretPosition(doc.getLength());
    }

    private SimpleAttributeSet styleFor(final ConsoleEntry.Kind kind) {
        return switch (kind) {
            case ERROR -> error;
            case EVAL_INPUT -> input;
            case EVAL_RESULT -> result;
            default -> plain;
        };
    }

    /** Empties the console. */
    void clear() {
        synchronized (pending) {
            pending.clear();
        }
        text.setText("");
    }

    /** Flushes pending output now; for tests. */
    void flushNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            flush();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::flush);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final java.lang.reflect.InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The console text, for tests. */
    String getConsoleText() {
        return text.getText();
    }
}
