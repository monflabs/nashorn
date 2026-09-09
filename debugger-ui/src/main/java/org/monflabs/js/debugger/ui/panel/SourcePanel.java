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
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.monflabs.js.debugger.ui.model.Breakpoint;
import org.monflabs.js.debugger.ui.model.ScriptInfo;

/**
 * The open source tabs, one {@link SourceView} per url, created on demand -
 * the centre of the Sources layout. It shows the execution line during a pause
 * and keeps each view's breakpoint dots in sync with the model.
 */
final class SourcePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Fetches a script's source text, calling the callback on the ui thread. */
    interface SourceSupplier {
        /** @param scriptId the script @param whenReady receives the source */
        void fetch(String scriptId, java.util.function.Consumer<String> whenReady);
    }

    private final Font font;
    private final boolean dark;
    private final transient SourceView.BreakpointToggle toggle;
    private final transient SourceSupplier sources;
    private final transient Map<String, SourceView> views = new HashMap<>();
    private final transient Map<String, List<java.util.function.Consumer<SourceView>>> pending = new HashMap<>();
    private final transient Map<String, ScriptInfo> scriptsByUrl = new HashMap<>();
    private final JLabel placeholder = new JLabel("No script open", SwingConstants.CENTER);
    private String currentUrl;

    SourcePanel(final Font font, final boolean dark, final SourceView.BreakpointToggle toggle, final SourceSupplier sources) {
        super(new BorderLayout());
        this.font = font;
        this.dark = dark;
        this.toggle = toggle;
        this.sources = sources;
        add(placeholder, BorderLayout.CENTER);
    }

    /** Opens (or reveals) a script. */
    void open(final ScriptInfo script) {
        scriptsByUrl.put(script.url(), script);
        show(script.url());
    }

    private void show(final String url) {
        if (url == null) {
            return;
        }
        if (url.equals(currentUrl) && views.containsKey(url)) {
            return;
        }
        withView(url, view -> swapTo(url, view));
    }

    /**
     * Runs {@code action} on the {@link SourceView} for {@code url} - now if the
     * view already exists, otherwise once its source fetch resolves and the view
     * is created. An action requested while a fetch is in flight rides that same
     * fetch. This is what stops a highlight (or a reveal) that arrives before the
     * asynchronous getScriptSource has returned from being silently dropped: the
     * first pause on a script genuinely races its scriptParsed-triggered fetch.
     */
    private void withView(final String url, final java.util.function.Consumer<SourceView> action) {
        if (url == null) {
            return;
        }
        final SourceView existing = views.get(url);
        if (existing != null) {
            action.accept(existing);
            return;
        }
        final ScriptInfo script = scriptsByUrl.get(url);
        if (script == null) {
            return;
        }
        final List<java.util.function.Consumer<SourceView>> inFlight = pending.get(url);
        if (inFlight != null) {
            // a fetch for this url is already running - queue onto it
            inFlight.add(action);
            return;
        }
        final List<java.util.function.Consumer<SourceView>> queued = new ArrayList<>();
        queued.add(action);
        pending.put(url, queued);
        sources.fetch(script.scriptId(), source -> {
            SourceView view = views.get(url);
            if (view == null) {
                view = new SourceView(url, source, font, dark, toggle);
                views.put(url, view);
            }
            final SourceView created = view;
            final List<java.util.function.Consumer<SourceView>> actions = pending.remove(url);
            if (actions != null) {
                for (final java.util.function.Consumer<SourceView> a : actions) {
                    a.accept(created);
                }
            }
        });
    }

    /** The view for a url, or null if none is loaded yet - for tests. */
    SourceView viewForTest(final String url) {
        return views.get(url);
    }

    private void swapTo(final String url, final SourceView view) {
        removeAll();
        add(view, BorderLayout.CENTER);
        currentUrl = url;
        revalidate();
        repaint();
    }

    /** Shows the execution arrow on a url's line, opening the script if needed. */
    void showExecutionLine(final String url, final int line) {
        withView(url, view -> {
            swapTo(url, view);
            view.setExecutionLine(line);
        });
    }

    /** Clears the execution arrow from every open view. */
    void clearExecutionLine() {
        for (final SourceView view : views.values()) {
            view.clearExecutionLine();
        }
    }

    /** Reconciles every open view's breakpoint dots to the model. */
    void syncBreakpoints(final List<Breakpoint> breakpoints) {
        for (final SourceView view : views.values()) {
            final List<Breakpoint> forUrl = breakpoints.stream().filter(b -> b.url().equals(view.url())).toList();
            view.syncBreakpoints(forUrl);
        }
    }

    /** Forgets all open views (a fresh attach). */
    void clear() {
        views.clear();
        pending.clear();
        scriptsByUrl.clear();
        currentUrl = null;
        removeAll();
        add(placeholder, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
