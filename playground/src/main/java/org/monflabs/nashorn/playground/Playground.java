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


package org.monflabs.nashorn.playground;

import java.awt.Image;
import java.awt.Taskbar;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import org.monflabs.nashorn.playground.ui.PlaygroundFrame;

/**
 * The playground: a library of samples, an editor, a console, and the
 * debugger a click away. {@code java -jar nashorn-playground-20-all.jar}.
 */
public final class Playground {
    private Playground() {
    }

    /**
     * Opens the window.
     * @param args {@code --dark} or {@code --light} to force the theme; otherwise the system's
     */
    public static void main(final String[] args) {
        final boolean dark = theme(args);
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Nashorn Playground");
            System.setProperty("apple.awt.application.appearance", "system");
        }
        SwingUtilities.invokeLater(() -> {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            UIManager.put("TextComponent.arc", 6);
            installIcon();
            try {
                final PlaygroundFrame frame = new PlaygroundFrame(SampleLibrary.load(), dark);
                frame.setVisible(true);
            } catch (final IOException e) {
                throw new IllegalStateException("cannot load the samples", e);
            }
        });
    }

    /** Dark or light: the arguments win, then the system's preference. */
    static boolean theme(final String[] args) {
        for (final String arg : args) {
            if (arg.equals("--dark")) {
                return true;
            }
            if (arg.equals("--light")) {
                return false;
            }
        }
        final String forced = System.getProperty("playground.dark");
        if (forced != null) {
            return Boolean.parseBoolean(forced);
        }
        if (SystemInfo.isMacOS) {
            try {
                final Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").redirectErrorStream(true).start();
                final String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                p.waitFor();
                return out.contains("dark");
            } catch (final IOException | InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    private static void installIcon() {
        try (InputStream in = Playground.class.getResourceAsStream("/icons/app.png")) {
            if (in == null) {
                return;
            }
            final Image image = ImageIO.read(in);
            if (image != null && Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(image);
            }
        } catch (final IOException | UnsupportedOperationException | SecurityException ignored) {
            // no icon then
        }
    }
}
