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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.debugger.ui.model.Breakpoint;
import org.testng.annotations.Test;

/**
 * The source view headless: the gutter toggle reaches the callback once, the
 * model reconciles dots, and the execution pointer leaves nothing behind.
 */
public class SourceViewTest {

    private static SourceView view(final List<String> toggles) {
        return new SourceView("file:///work/x.js", "var a = 1;\nvar b = 2;\nvar c = 3;\n",
                new Font(Font.MONOSPACED, Font.PLAIN, 12), false,
                (url, line) -> toggles.add(url + ":" + line));
    }

    @Test
    public void aGutterToggleReachesTheCallbackOnce() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        // a user click is what RSTA turns into toggleBookmark; drive it directly
        view.gutter().toggleBookmark(1);
        assertEquals(toggles, List.of("file:///work/x.js:1"));
    }

    @Test
    public void syncingBreakpointsDoesNotLoopBackAsToggles() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 2, null, true, "1", List.of())));
        // the dot was placed by the model, not the user: no callback
        assertTrue(toggles.isEmpty(), "unexpected toggles: " + toggles);
        assertEquals(view.gutter().getBookmarks().length, 1);
    }

    @Test
    public void aResolvedLineMovesTheDot() throws Exception {
        final List<String> toggles = new ArrayList<>();
        final SourceView view = view(toggles);
        // requested line 0 but the server resolved it to line 1
        view.syncBreakpoints(List.of(new Breakpoint("file:///work/x.js", 0, null, true, "1", List.of(1))));
        assertEquals(view.gutter().getBookmarks().length, 1);
        assertEquals(view.textArea().getLineOfOffset(view.gutter().getBookmarks()[0].getMarkedOffset()), 1);
    }

    @Test
    public void theExecutionPointerSetsAndClears() {
        final SourceView view = view(new ArrayList<>());
        view.setExecutionLine(1);
        view.clearExecutionLine();
        // the arrow lives as a tracking icon, not a bookmark; a cleared pointer leaves the bookmarks alone
        assertEquals(view.gutter().getBookmarks().length, 0);
        // and setting it again does not accumulate
        view.setExecutionLine(2);
        view.setExecutionLine(0);
        view.clearExecutionLine();
        assertEquals(view.gutter().getBookmarks().length, 0);
    }
}
