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

import static org.testng.Assert.assertEquals;

import java.awt.Font;
import java.util.function.Consumer;
import org.monflabs.js.debugger.ui.model.ScriptInfo;
import org.testng.annotations.Test;

/**
 * The Sources panel must apply an execution-line highlight even when it arrives
 * before the asynchronous source fetch has resolved - the real event-vs-RPC
 * race a script's very first pause hits.
 */
public class SourcePanelTest {

    /** A source supplier that captures its callback instead of resolving it now. */
    private static final class DeferredSupplier implements SourcePanel.SourceSupplier {
        private Consumer<String> pending;
        private final String source;

        DeferredSupplier(final String source) {
            this.source = source;
        }

        @Override
        public void fetch(final String scriptId, final Consumer<String> whenReady) {
            this.pending = whenReady;
        }

        /** Resolves the one captured fetch, the way the CDP reply eventually would. */
        void resolve() {
            final Consumer<String> c = pending;
            pending = null;
            c.accept(source);
        }
    }

    @Test
    public void highlightArrivingBeforeTheFetchResolvesIsNotDropped() {
        final DeferredSupplier sources = new DeferredSupplier("var a = 1;\nvar b = 2;\nvar c = 3;\n");
        final SourcePanel panel = new SourcePanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false,
                (url, line) -> { }, sources);
        final String url = "file:///work/race.js";
        panel.open(new ScriptInfo("1", url, 2, false));

        // the pause arrives while the source fetch is still in flight - the view
        // does not exist yet, so the pre-fix code silently dropped this
        panel.showExecutionLine(url, 1);

        // now the fetch resolves, creating the view; the highlight must be applied
        sources.resolve();

        final SourceView view = panel.viewForTest(url);
        assertEquals(view.executionLine(), 1, "the execution highlight must survive the fetch race");
    }

    @Test
    public void aHighlightOnAnAlreadyLoadedViewStillWorks() {
        final DeferredSupplier sources = new DeferredSupplier("var a = 1;\nvar b = 2;\n");
        final SourcePanel panel = new SourcePanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false,
                (url, line) -> { }, sources);
        final String url = "file:///work/loaded.js";
        panel.open(new ScriptInfo("1", url, 1, false));
        sources.resolve();   // the view exists before the highlight (a later pause)

        panel.showExecutionLine(url, 0);
        assertEquals(panel.viewForTest(url).executionLine(), 0);
    }
}
