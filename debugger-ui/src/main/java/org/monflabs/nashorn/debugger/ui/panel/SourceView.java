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

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.util.List;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.IconRowEvent;
import org.fife.ui.rtextarea.IconRowListener;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.monflabs.nashorn.debugger.ui.model.Breakpoint;

/**
 * One script's source, read only, with a breakpoint gutter and an execution
 * pointer - the centre of a Chrome Sources tab. Clicking the gutter toggles a
 * breakpoint through the callback; the model then calls {@link #syncBreakpoints}
 * to reconcile the dots (which is how a server-adjusted line moves one). The
 * execution arrow and the paused-line highlight are set and cleared as the
 * engine pauses and resumes.
 */
final class SourceView extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Told when the user toggles a breakpoint on a line. */
    interface BreakpointToggle {
        /** @param url the script url @param line the line, zero based */
        void toggled(String url, int line);
    }

    private final String url;
    private final transient RSyntaxTextArea area;
    private final transient Gutter gutter;
    private final transient BreakpointToggle toggle;
    private final Color highlightColor;
    private boolean syncing;
    private int executionLine = -1;
    private final transient java.util.Set<Integer> breakpointLines = new java.util.HashSet<>();
    private transient GutterIconInfo executionIcon;
    private transient Object executionHighlight;

    SourceView(final String url, final String source, final Font font, final boolean dark, final BreakpointToggle toggle) {
        super(new BorderLayout());
        this.url = url;
        this.toggle = toggle;
        // a bright, semi-transparent wash - fillRect blends the alpha, so the
        // paused line stands out while its text stays readable
        this.highlightColor = dark ? new Color(0x4A, 0x90, 0xE2, 110) : new Color(0xFF, 0xEB, 0x8C, 160);

        area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        area.setCodeFoldingEnabled(true);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setHighlightCurrentLine(false);
        area.setEditable(false);
        applyTheme(area, dark);
        area.setFont(font);
        area.setText(source);
        area.setCaretPosition(0);
        area.discardAllEdits();

        final RTextScrollPane scroll = new RTextScrollPane(area, true);
        scroll.setIconRowHeaderEnabled(true);
        gutter = scroll.getGutter();
        gutter.setBookmarkingEnabled(true);
        gutter.setBookmarkIcon(DebuggerIcons.breakpoint());
        gutter.addIconRowListener(new IconRowListener() {
            @Override
            public void bookmarkAdded(final IconRowEvent e) {
                if (!syncing) {
                    toggle.toggled(SourceView.this.url, e.getLine());
                }
            }

            @Override
            public void bookmarkRemoved(final IconRowEvent e) {
                if (!syncing) {
                    toggle.toggled(SourceView.this.url, e.getLine());
                }
            }
        });
        add(scroll, BorderLayout.CENTER);
    }

    /** The url this view shows. */
    String url() {
        return url;
    }

    /**
     * Sets the breakpoints for this url; the gutter then shows a dot on each,
     * except the paused line, where a combined arrow-over-dot glyph stands for
     * both. Runs under a guard so its own bookmark changes are not read back as
     * user toggles.
     * @param breakpoints the breakpoints for this url
     */
    void syncBreakpoints(final List<Breakpoint> breakpoints) {
        breakpointLines.clear();
        for (final Breakpoint bp : breakpoints) {
            if (bp.enabled()) {
                breakpointLines.add(bp.resolvedLines().isEmpty() ? bp.line() : bp.resolvedLines().get(0));
            }
        }
        refresh();
    }

    /**
     * Marks a line as the one about to run.
     * @param line the line, zero based
     */
    void setExecutionLine(final int line) {
        executionLine = line;
        refresh();
        try {
            area.setCaretPosition(area.getLineStartOffset(line));
        } catch (final BadLocationException ignored) {
            // the line is out of range
        }
    }

    /** Removes the execution pointer, if any. */
    void clearExecutionLine() {
        executionLine = -1;
        refresh();
    }

    /**
     * Redraws every gutter marker from the model, so that exactly one icon sits
     * on any line: a breakpoint dot, the paused-line arrow, or - where the two
     * coincide - a single glyph showing both. Rebuilding the bookmarks and the
     * tracking icon together is what keeps them from fighting over one line in
     * the gutter's shared icon list.
     */
    private void refresh() {
        syncing = true;
        try {
            for (final GutterIconInfo info : gutter.getBookmarks()) {
                try {
                    gutter.toggleBookmark(area.getLineOfOffset(info.getMarkedOffset()));
                } catch (final BadLocationException ignored) {
                    // the offset is ours; cannot happen
                }
            }
            for (final int line : breakpointLines) {
                if (line == executionLine) {
                    continue;   // shown as the combined glyph below, not a plain dot
                }
                try {
                    gutter.toggleBookmark(line);
                } catch (final BadLocationException ignored) {
                    // a line past the end; skip it
                }
            }
        } finally {
            syncing = false;
        }
        removeExecutionMarker();
        if (executionLine < 0 || executionLine > area.getLineCount() - 1) {
            return;
        }
        try {
            final javax.swing.Icon icon = breakpointLines.contains(executionLine)
                    ? DebuggerIcons.executionArrowOnBreakpoint() : DebuggerIcons.executionArrow();
            executionIcon = gutter.addLineTrackingIcon(executionLine, icon, "Execution position");
            executionHighlight = area.addLineHighlight(executionLine, highlightColor);
        } catch (final BadLocationException ignored) {
            // the line is out of range; leave it unmarked
        }
    }

    private void removeExecutionMarker() {
        if (executionIcon != null) {
            gutter.removeTrackingIcon(executionIcon);
            executionIcon = null;
        }
        if (executionHighlight != null) {
            area.removeLineHighlight(executionHighlight);
            executionHighlight = null;
        }
    }

    /** Whether an execution pointer is currently drawn, for tests. */
    boolean hasExecutionMarker() {
        return executionIcon != null;
    }

    /** The text area, for tests. */
    RSyntaxTextArea textArea() {
        return area;
    }

    /** The gutter, for tests. */
    Gutter gutter() {
        return gutter;
    }

    private static void applyTheme(final RSyntaxTextArea area, final boolean dark) {
        final String name = dark ? "dark.xml" : "default.xml";
        try (InputStream in = Theme.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + name)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (final Exception ignored) {
            // keep the default theme
        }
    }
}
