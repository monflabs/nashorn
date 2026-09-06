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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * The debugger's glyphs, painted rather than loaded, so they need no resources
 * and look right in any look and feel: the breakpoint dots, the execution
 * arrow, and the toolbar's transport controls.
 */
final class DebuggerIcons {
    private static final Color BREAKPOINT = new Color(0xE5, 0x1C, 0x23);
    private static final Color DISABLED = new Color(0x9E, 0x9E, 0x9E);
    private static final Color ARROW = new Color(0x42, 0x85, 0xF4);
    private static final Color GLYPH = new Color(0x5F, 0x63, 0x68);

    private DebuggerIcons() {
    }

    /** A filled dot for an active breakpoint. */
    static Icon breakpoint() {
        return new DotIcon(BREAKPOINT, true);
    }

    /** A hollow dot for a disabled or unresolved breakpoint. */
    static Icon breakpointHollow() {
        return new DotIcon(DISABLED, false);
    }

    /** A right-pointing arrow marking the paused line. */
    static Icon executionArrow() {
        return new ArrowIcon(false);
    }

    /** The paused-line arrow over a breakpoint dot, for a line that has both. */
    static Icon executionArrowOnBreakpoint() {
        return new ArrowIcon(true);
    }

    /** The resume (play) glyph. */
    static Icon resume() {
        return new GlyphIcon(GlyphIcon.Kind.RESUME);
    }

    /** The pause glyph. */
    static Icon pause() {
        return new GlyphIcon(GlyphIcon.Kind.PAUSE);
    }

    /** Step over. */
    static Icon stepOver() {
        return new GlyphIcon(GlyphIcon.Kind.STEP_OVER);
    }

    /** Step into. */
    static Icon stepInto() {
        return new GlyphIcon(GlyphIcon.Kind.STEP_INTO);
    }

    /** Step out. */
    static Icon stepOut() {
        return new GlyphIcon(GlyphIcon.Kind.STEP_OUT);
    }

    private static Graphics2D prepare(final Graphics g) {
        final Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    /** A breakpoint dot, filled or hollow. */
    private record DotIcon(Color color, boolean filled) implements Icon {
        private static final int SIZE = 12;

        @Override
        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final Graphics2D g2 = prepare(g);
            g2.setColor(color);
            final int d = SIZE - 4;
            if (filled) {
                g2.fillOval(x + 2, y + 2, d, d);
            } else {
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 2, y + 2, d - 1, d - 1);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /** The paused-line arrow, optionally over a breakpoint dot. */
    private static final class ArrowIcon implements Icon {
        private static final int SIZE = 12;
        private final boolean onBreakpoint;

        ArrowIcon(final boolean onBreakpoint) {
            this.onBreakpoint = onBreakpoint;
        }

        @Override
        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final Graphics2D g2 = prepare(g);
            if (onBreakpoint) {
                // draw the dot beneath, so a line with both markers shows both:
                // the red breakpoint and the arrow on top of it
                g2.setColor(BREAKPOINT);
                g2.fillOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
            }
            g2.setColor(ARROW);
            final Path2D path = new Path2D.Float();
            path.moveTo(x + 2, y + 2);
            path.lineTo(x + 9, y + SIZE / 2.0);
            path.lineTo(x + 2, y + SIZE - 2);
            path.closePath();
            g2.fill(path);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /** A transport-control glyph. */
    private static final class GlyphIcon implements Icon {
        enum Kind { RESUME, PAUSE, STEP_OVER, STEP_INTO, STEP_OUT }

        private static final int SIZE = 16;
        private final Kind kind;

        GlyphIcon(final Kind kind) {
            this.kind = kind;
        }

        @Override
        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final Graphics2D g2 = prepare(g);
            // the button's own foreground when active, so an enabled control looks
            // enabled in any look and feel; a muted grey only when truly disabled
            g2.setColor(c != null && !c.isEnabled() ? DISABLED : (c != null ? c.getForeground() : GLYPH));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            final int cx = x + SIZE / 2;
            final int cy = y + SIZE / 2;
            switch (kind) {
            case RESUME -> {
                final Path2D t = new Path2D.Float();
                t.moveTo(x + 4, y + 3);
                t.lineTo(x + 13, cy);
                t.lineTo(x + 4, y + SIZE - 3);
                t.closePath();
                g2.fill(t);
            }
            case PAUSE -> {
                g2.fillRect(x + 4, y + 3, 3, SIZE - 6);
                g2.fillRect(x + 9, y + 3, 3, SIZE - 6);
            }
            case STEP_OVER -> {
                g2.drawArc(x + 3, y + 3, SIZE - 6, SIZE - 6, 20, 140);
                g2.fillOval(cx - 1, y + SIZE - 6, 4, 4);
                arrowHead(g2, x + SIZE - 4, y + 5, 1, 0);
            }
            case STEP_INTO -> {
                g2.drawLine(cx, y + 2, cx, y + SIZE - 6);
                arrowHead(g2, cx, y + SIZE - 6, 0, 1);
                g2.fillOval(cx - 2, y + SIZE - 4, 4, 4);
            }
            case STEP_OUT -> {
                g2.drawLine(cx, y + SIZE - 4, cx, y + 4);
                arrowHead(g2, cx, y + 4, 0, -1);
                g2.fillOval(cx - 2, y + SIZE - 5, 4, 4);
            }
            default -> { }
            }
            g2.dispose();
        }

        private static void arrowHead(final Graphics2D g2, final int tx, final int ty, final int dx, final int dy) {
            if (dy != 0) {
                g2.drawLine(tx, ty, tx - 3, ty - 3 * dy);
                g2.drawLine(tx, ty, tx + 3, ty - 3 * dy);
            } else {
                g2.drawLine(tx, ty, tx - 3 * dx, ty - 3);
                g2.drawLine(tx, ty, tx - 3 * dx, ty + 3);
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
