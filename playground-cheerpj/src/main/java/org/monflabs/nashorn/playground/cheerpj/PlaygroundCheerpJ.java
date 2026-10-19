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

package org.monflabs.nashorn.playground.cheerpj;

import javax.swing.JFrame;
import org.monflabs.nashorn.playground.Playground;

/**
 * The playground as CheerpJ runs it in a web page: the desktop application,
 * undecorated and maximized so that it fills the page. The page passes
 * {@code --dark} or {@code --light}, the mode the documentation site shows.
 */
public final class PlaygroundCheerpJ {
    private PlaygroundCheerpJ() {
    }

    /**
     * Opens the playground over the whole page.
     * @param args {@code --dark} or {@code --light} to force the theme; otherwise the system's
     */
    public static void main(final String[] args) {
        Playground.open(args, frame -> {
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        });
    }
}
