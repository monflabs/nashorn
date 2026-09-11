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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.monflabs.js.debugger.ui.DebuggerPanel;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.debugger.inprocess.InProcessCdpServer;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.SampleLibrary;
import org.monflabs.nashorn.playground.ScriptRunner;

/**
 * The window: the samples on the left; the editor over the console and the
 * README on the right; the actions across the top.
 */
public final class PlaygroundFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Path SCRATCH_FILE = Path.of(System.getProperty("user.home"), ".nashorn-playground", "scratch.js");
    private static final String SCRATCH_DEFAULT = "// Scratchpad - your own code goes here, and is kept between sessions.\n"
            + "var greeting = 'Hello';\nprint(greeting + ', Nashorn!');\n";

    private final transient Preferences prefs = Preferences.userNodeForPackage(PlaygroundFrame.class);
    private final transient ScriptRunner runner = new ScriptRunner();
    private final SampleTree tree;
    private final EditorPane editor;
    private final ConsolePane console;
    private final ReadmePane readme;
    private final JSplitPane consoleSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JSplitPane readmeSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JCheckBox autoRun = new JCheckBox("Auto-run", true);
    private final JCheckBox echo = new JCheckBox("Log expression values", true);
    private final JCheckBox wordWrap = new JCheckBox("Word wrap", true);
    private final JCheckBox preserve = new JCheckBox("Preserve console", false);
    private final JButton debugButton = new JButton("Debug");
    private final JButton externalDebugButton = new JButton("External Debugger");
    private final JLabel debugInfo = new JLabel(" ");
    private final JButton inspectLink = new JButton("<html><u>open chrome://inspect</u></html>");
    private final Timer autoRunTimer = new Timer(500, e -> run());
    private final Timer scratchTimer = new Timer(1000, e -> saveScratch());
    private final transient Font mono;
    private final boolean dark;
    // The one debugging session currently running, of either kind - pressing
    // either button, closing the debugger window, or selecting another sample
    // all funnel through cancelDebugSession(), so exactly one session is ever
    // live. The token identifies it, so a teardown that has since been
    // superseded by a newer session does nothing.
    private transient Object debugSession;
    private transient AutoCloseable debugServer;
    private transient JFrame debugFrame;
    private transient DebuggerPanel debugPanel;
    private transient Sample sample;
    private transient Sample scratch;
    private transient Future<?> queued;

    /**
     * Creates the window.
     * @param library the samples
     * @param dark whether the theme is dark
     */
    public PlaygroundFrame(final SampleLibrary library, final boolean dark) {
        super("Nashorn Playground");
        this.mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        this.dark = dark;
        scratch = loadScratch();
        tree = new SampleTree(library, scratch);
        editor = new EditorPane(this, mono, dark);
        console = new ConsolePane(mono);
        readme = new ReadmePane(dark);
        autoRunTimer.setRepeats(false);
        scratchTimer.setRepeats(false);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                saveScratch();
                savePrefs();
                cancelDebugSession();
                runner.close();
                dispose();
                System.exit(0);
            }
        });

        add(toolbar(), BorderLayout.NORTH);
        // the console beside the script, so that in the echo mode its lines can mirror the script's
        consoleSplit.setLeftComponent(editor);
        consoleSplit.setRightComponent(titled("Console", console));
        consoleSplit.setResizeWeight(0.55);
        readmeSplit.setTopComponent(consoleSplit);
        readmeSplit.setBottomComponent(titled("README", readme));
        readmeSplit.setResizeWeight(0.75);
        final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, readmeSplit);
        split.setResizeWeight(0.0);
        tree.setMinimumSize(new Dimension(200, 100));
        tree.setPreferredSize(new Dimension(280, 600));
        add(split, BorderLayout.CENTER);

        tree.onSelect(this::show);
        editor.onEdit(this::edited);
        keys();
        loadPrefs();
        split.setDividerLocation(prefs.getInt("divider", 280));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(final WindowEvent e) {
                // the nested splits have their real size only now; a remembered position, else a proportion
                final int consoleDivider = prefs.getInt("consoleDivider", -1);
                if (consoleDivider > 0) {
                    consoleSplit.setDividerLocation(consoleDivider);
                } else {
                    consoleSplit.setDividerLocation(0.55);
                }
                final int readmeDivider = prefs.getInt("readmeDivider", -1);
                if (readmeDivider > 0) {
                    readmeSplit.setDividerLocation(readmeDivider);
                } else {
                    readmeSplit.setDividerLocation(0.72);
                }
            }

            @Override
            public void windowClosing(final WindowEvent e) {
                prefs.putInt("divider", split.getDividerLocation());
                prefs.putInt("consoleDivider", consoleSplit.getDividerLocation());
                prefs.putInt("readmeDivider", readmeSplit.getDividerLocation());
            }
        });
        if (!tree.select(prefs.get("sample", ""))) {
            final List<Sample> samples = library.samples();
            if (!samples.isEmpty()) {
                tree.select(samples.get(0).id());
            }
        }
    }

    private JComponent toolbar() {
        final JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        runButton.addActionListener(e -> run());
        stopButton.addActionListener(e -> runner.stop());
        stopButton.setEnabled(false);
        final JButton clear = new JButton("Clear");
        clear.addActionListener(e -> console.clear());
        final JButton saveAs = new JButton("Save as\u2026");
        saveAs.addActionListener(e -> saveAs());
        echo.addActionListener(e -> run());
        wordWrap.addActionListener(e -> console.setWrap(wordWrap.isSelected()));
        debugButton.addActionListener(e -> startDebugSession(true));
        debugButton.setToolTipText("Debug the current sample in the built-in debugger panel, paused at its first "
                + "statement. Cancels any running debug session and starts a fresh one, closing any open "
                + "debugger window first.");
        externalDebugButton.addActionListener(e -> startDebugSession(false));
        externalDebugButton.setToolTipText("Start a Chrome DevTools Protocol server on port " + InspectOptions.DEFAULT_PORT
                + " - Node's own --inspect-brk default - running the current sample paused at its first "
                + "statement, for an external debugger (Chrome DevTools, VS Code, ...) to attach to. Cancels "
                + "any running debug session and starts a fresh one.");
        debugInfo.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        inspectLink.setBorderPainted(false);
        inspectLink.setContentAreaFilled(false);
        inspectLink.setFocusPainted(false);
        inspectLink.setForeground(new java.awt.Color(0x2a, 0x6f, 0xc4));
        inspectLink.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        inspectLink.setToolTipText("Launch Chrome on its inspect page; the playground appears under Remote Target");
        inspectLink.setVisible(false);
        inspectLink.addActionListener(e -> openChromeInspect());
        bar.add(runButton);
        bar.add(stopButton);
        bar.addSeparator();
        bar.add(autoRun);
        bar.add(echo);
        bar.addSeparator();
        bar.add(clear);
        bar.add(wordWrap);
        bar.add(preserve);
        bar.addSeparator();
        bar.add(saveAs);
        bar.add(Box.createHorizontalGlue());
        bar.add(debugButton);
        bar.add(externalDebugButton);
        final JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.CENTER);
        final JPanel info = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        info.add(debugInfo);
        info.add(inspectLink);
        north.add(info, BorderLayout.SOUTH);
        return north;
    }

    /** One pane wrapped in a single-tab container, so it carries a title. */
    private static JTabbedPane titled(final String title, final JComponent pane) {
        final JTabbedPane tab = new JTabbedPane();
        tab.addTab(title, pane);
        return tab;
    }

    private void keys() {
        final int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        final JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menu), "run");
        root.getActionMap().put("run", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(final ActionEvent e) {
                run();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "stop");
        root.getActionMap().put("stop", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(final ActionEvent e) {
                runner.stop();
            }
        });
    }

    // -- samples ----------------------------------------------------------------

    private void show(final Sample s) {
        if (sample != null && sample == scratch) {
            saveScratch();
        }
        // selecting another sample cancels any debug session on the old one
        cancelDebugSession();
        sample = s;
        editor.show(s.source(), s.files(), true);
        readme.show(s.readme() != null ? s.readme() : "## " + s.title());
        setTitle("Nashorn Playground - " + s.title());
        prefs.put("sample", s.id());
        if (autoRun.isSelected()) {
            run();
        }
    }

    private void edited() {
        if (sample == scratch) {
            scratchTimer.restart();
        }
        if (autoRun.isSelected()) {
            autoRunTimer.restart();
        }
    }

    private Sample loadScratch() {
        String text = SCRATCH_DEFAULT;
        try {
            if (Files.isRegularFile(SCRATCH_FILE)) {
                text = Files.readString(SCRATCH_FILE, StandardCharsets.UTF_8);
            }
        } catch (final IOException ignored) {
            // the default then
        }
        return new Sample(SampleTree.SCRATCH_ID, List.of(), "Scratchpad", text,
                "# Scratchpad\n\nYour own code. It is saved in `" + SCRATCH_FILE + "` and kept between sessions.\n\n"
                + "Run with **Run** or \u2318/Ctrl-Enter; **Stop** or Esc ends a script that runs away; "
                + "**Log expression values** shows what each top-level statement evaluates to.",
                Map.of(), List.of());
    }

    private void saveScratch() {
        if (sample != scratch) {
            return;
        }
        final String text = editor.getSource();
        scratch = withSource(scratch, text);
        sample = scratch;
        try {
            Files.createDirectories(SCRATCH_FILE.getParent());
            Files.writeString(SCRATCH_FILE, text, StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // nowhere to keep it
        }
    }

    private static Sample withSource(final Sample s, final String text) {
        return new Sample(s.id(), s.categories(), s.title(), text, s.readme(), s.files(), SampleLibrary.options(text));
    }

    private void saveAs() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(sample == null ? "script.js" : sample.title().replaceAll("[^A-Za-z0-9._-]", "_") + ".js"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(chooser.getSelectedFile().toPath(), editor.getSource(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot save", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // -- running ------------------------------------------------------------------

    private Future<?> run() {
        if (sample == null) {
            return null;
        }
        autoRunTimer.stop();
        if (queued != null) {
            queued.cancel(false);
        }
        final String source = editor.getSource();
        final Sample current = withSource(sample, source);
        if (!preserve.isSelected()) {
            console.clear();
        }
        queued = runner.run(current, source, echo.isSelected(), console, new ScriptRunner.Listener() {
            @Override
            public void started() {
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    console.status("Running\u2026");
                });
            }

            @Override
            public void finished(final ScriptRunner.Result result) {
                if (result.failure() != null) {
                    console.err(ScriptRunner.describe(result.failure()) + "\n");
                }
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    console.status(result.terminated() ? "Stopped after " + result.millis() + " ms"
                            : result.failure() != null ? "Failed after " + result.millis() + " ms" : "Done in " + result.millis() + " ms");
                });
            }
        });
        return queued;
    }

    /**
     * Starts a fresh debug session for the current sample, paused at its first
     * statement - like a real {@code node --inspect-brk} launch. Cancels
     * whatever session (of either kind) is already running first, so this is
     * the only entry point either debugger button needs.
     *
     * @param internal true for the built-in Swing panel over the in-process,
     *        socket-free CDP transport ("Debug"); false for a real Chrome
     *        DevTools Protocol server on Nashorn's default debugger port, for
     *        an external client (Chrome DevTools, VS Code, ...) to attach to
     *        ("External Debugger")
     */
    private void startDebugSession(final boolean internal) {
        cancelDebugSession();
        if (sample == null) {
            return;
        }
        final Debugger debugger;
        try {
            debugger = runner.debuggerFor(withSource(sample, editor.getSource()));
        } catch (final RuntimeException e) {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()), "Start the debugger", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // a brand new, cleared registry: the client attaches to an empty
        // Sources list, and the run below repopulates it
        runner.clearDebugScripts();
        final Object token = new Object();
        if (internal) {
            // no socket, no port: the engine and this panel run in the very
            // same JVM, so there is nothing to dial and nothing that can fail
            // binding
            final InProcessCdpServer.Handle server = InProcessCdpServer.open(debugger, InspectOptions.parse("", false));
            final DebuggerPanel panel = new DebuggerPanel(mono, dark);
            final JFrame frame = new JFrame("Nashorn Debugger");
            frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            frame.setSize(1200, 850);
            frame.getContentPane().add(panel, BorderLayout.CENTER);

            debugSession = token;
            debugServer = server;
            debugFrame = frame;
            debugPanel = panel;

            final AtomicBoolean started = new AtomicBoolean();
            // the script only starts once the panel is actually connected and
            // has enabled the Debugger domain - Debugger.enable does not replay
            // an already-in-progress pause, so starting any earlier could pause
            // the script before a client exists to be told about it
            panel.onConnectionChange((state, detail) -> {
                if (state == DebuggerPanel.ConnectionState.CONNECTED && started.compareAndSet(false, true)) {
                    SwingUtilities.invokeLater(() -> {
                        runner.pauseOnNextRun(true);
                        run();
                    });
                }
            });
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(final WindowEvent e) {
                    // only if this window's own session is still the active one
                    // - avoids re-entering cancelDebugSession() when it is what
                    // disposed this window in the first place
                    if (debugSession == token) {
                        cancelDebugSession();
                    }
                }
            });

            frame.setVisible(true);
            panel.attach(server.clientChannel());
        } else {
            final String url;
            try {
                url = runner.debugInChrome(true, InspectOptions.DEFAULT_PORT);
            } catch (final IOException e) {
                JOptionPane.showMessageDialog(this, "Cannot start the debugger server: " + e.getMessage(),
                        "Start the debugger", JOptionPane.ERROR_MESSAGE);
                return;
            }
            debugSession = token;
            debugServer = () -> runner.debugInChrome(false, 0);

            runner.pauseOnNextRun(true);
            final Future<?> running = run();
            debugInfo.setText("Debugger listening on " + url + ", paused at the first statement \u2014 ");
            inspectLink.setVisible(true);

            // A real Node/V8 inspector closes the connection when the debugged
            // process exits, which is how DevTools knows the run is over.
            // Without an equivalent here, a script resumed to completion just
            // leaves the server open with no signal at all - the banner never
            // goes away even though the run has genuinely finished. Watching
            // the run and cancelling the session on completion (only if this
            // has not since been superseded by a newer session) fixes that.
            final Thread watcher = new Thread(() -> {
                try {
                    if (running != null) {
                        running.get();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException ended) {
                    // over either way
                }
                SwingUtilities.invokeLater(() -> {
                    if (debugSession == token) {
                        cancelDebugSession();
                    }
                });
            }, "playground-debug-session-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    /**
     * Tears down whichever debug session (built-in panel or external CDP
     * server) is currently running, if any - closing the debugger window first
     * if one is open, and ending a script left paused in it. Called before
     * starting a fresh session, when the debugger window is closed, when
     * another sample is selected, and when the playground shuts down.
     */
    private void cancelDebugSession() {
        final Object session = debugSession;
        debugSession = null;
        final AutoCloseable server = debugServer;
        debugServer = null;
        final JFrame frame = debugFrame;
        debugFrame = null;
        final DebuggerPanel panel = debugPanel;
        debugPanel = null;

        if (session != null) {
            // a script frozen at a breakpoint would otherwise stay frozen with
            // nothing left to resume it
            runner.pauseOnNextRun(false);
            runner.stop();
        }
        if (frame != null) {
            frame.dispose();
        }
        if (panel != null) {
            panel.close();
        }
        if (server != null) {
            try {
                server.close();
            } catch (final Exception ignored) {
                // closing anyway
            }
        }
        debugInfo.setText(" ");
        inspectLink.setVisible(false);
    }

    /**
     * Launches Chrome (or another Chromium) on its inspect page. chrome:// is
     * not an OS-registered scheme, so the browser is started with the URL as
     * an argument, per platform, first candidate that starts winning.
     */
    private void openChromeInspect() {
        final String url = "chrome://inspect/#devices";
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        final java.util.List<java.util.List<String>> candidates = new java.util.ArrayList<>();
        if (os.contains("mac")) {
            candidates.add(java.util.List.of("open", "-a", "Google Chrome", url));
            candidates.add(java.util.List.of("open", "-a", "Chromium", url));
        } else if (os.contains("win")) {
            candidates.add(java.util.List.of("cmd", "/c", "start", "chrome", url));
            candidates.add(java.util.List.of("cmd", "/c", "start", "msedge", "edge://inspect/#devices"));
        } else {
            candidates.add(java.util.List.of("google-chrome", url));
            candidates.add(java.util.List.of("chromium", url));
            candidates.add(java.util.List.of("chromium-browser", url));
        }
        for (final java.util.List<String> command : candidates) {
            try {
                final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() == 0) {
                    return;   // still running, or done and content: the browser is on its way
                }
            } catch (final IOException notThere) {
                // try the next candidate
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        JOptionPane.showMessageDialog(this,
                "Could not launch Chrome. Open chrome://inspect in a Chromium browser yourself;\nthe playground appears under Remote Target.",
                "Start the debugger server", JOptionPane.INFORMATION_MESSAGE);
    }

    // -- preferences ----------------------------------------------------------------

    private void loadPrefs() {
        setSize(prefs.getInt("width", 1200), prefs.getInt("height", 800));
        final int x = prefs.getInt("x", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE) {
            setLocationRelativeTo(null);
        } else {
            setLocation(x, prefs.getInt("y", 0));
        }
        autoRun.setSelected(prefs.getBoolean("autoRun", true));
        echo.setSelected(prefs.getBoolean("echo", true));
        wordWrap.setSelected(prefs.getBoolean("wordWrap", true));
        console.setWrap(wordWrap.isSelected());
        preserve.setSelected(prefs.getBoolean("preserve", false));
    }

    private void savePrefs() {
        prefs.putInt("width", getWidth());
        prefs.putInt("height", getHeight());
        prefs.putInt("x", getX());
        prefs.putInt("y", getY());
        prefs.putBoolean("autoRun", autoRun.isSelected());
        prefs.putBoolean("echo", echo.isSelected());
        prefs.putBoolean("wordWrap", wordWrap.isSelected());
        prefs.putBoolean("preserve", preserve.isSelected());
    }
}
