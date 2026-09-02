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

package org.monflabs.nashorn.playground.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.monflabs.nashorn.debugger.ui.DebuggerPanel;

/**
 * A window around the embeddable {@link DebuggerPanel}, the playground's own
 * debugger: it attaches to the playground's Chrome DevTools Protocol server and
 * shows the sample paused, with breakpoints, stepping, scopes and a console -
 * the same protocol Chrome would use, in-app. Closing it detaches only; the
 * server stays up.
 */
final class DebuggerFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final transient DebuggerPanel panel;
    private final transient Supplier<String> urlSupplier;
    private final transient Preferences prefs = Preferences.userNodeForPackage(DebuggerFrame.class);

    /**
     * Creates the window.
     * @param mono the monospaced font
     * @param dark whether the dark theme is in effect
     * @param urlSupplier yields the current server url, restarting it if need be
     */
    DebuggerFrame(final Font mono, final boolean dark, final Supplier<String> urlSupplier) {
        super("Nashorn Debugger");
        this.urlSupplier = urlSupplier;
        panel = new DebuggerPanel(mono, dark);
        add(panel);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                panel.detach();
                savePrefs();
            }
        });
        setSize(new Dimension(prefs.getInt("dbg.width", 1000), prefs.getInt("dbg.height", 680)));
        setLocationByPlatform(true);
    }

    /**
     * Shows the window and attaches to a url, then runs {@code whenConnected}
     * once the client has finished enabling - the point at which arming a
     * pause-on-start run is safe.
     * @param url the {@code ws://...} url to attach to
     * @param whenConnected what to do once connected (arm and run)
     */
    void show(final String url, final Runnable whenConnected) {
        setVisible(true);
        toFront();
        panel.onConnectionChange((state, detail) -> {
            switch (state) {
            case CONNECTED -> {
                if (whenConnected != null) {
                    whenConnected.run();
                }
            }
            case FAILED -> javax.swing.JOptionPane.showMessageDialog(this,
                    "Chrome DevTools is already attached to the playground.\n"
                    + "Close its window (or its inspect target) and press Debug here again.",
                    "Debugger busy", javax.swing.JOptionPane.WARNING_MESSAGE);
            default -> { }
            }
        });
        panel.attach(url);
    }

    /** Detaches and disposes; the playground is shutting down. */
    void shutdown() {
        panel.close();
        savePrefs();
        dispose();
    }

    private void savePrefs() {
        if (getWidth() > 0 && getHeight() > 0) {
            prefs.putInt("dbg.width", getWidth());
            prefs.putInt("dbg.height", getHeight());
        }
    }
}
