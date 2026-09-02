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
import org.monflabs.nashorn.api.debugger.InspectOptions;
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
    private final JCheckBox debug = new JCheckBox("Start the debugger server", false);
    private final JButton debugRun = new JButton("Debug");
    private final JLabel debugInfo = new JLabel(" ");
    private final Timer autoRunTimer = new Timer(500, e -> run());
    private final Timer scratchTimer = new Timer(1000, e -> saveScratch());
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
        final Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
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
        debug.addActionListener(e -> toggleDebug());
        debugRun.addActionListener(e -> {
            runner.pauseOnNextRun(true);   // this run only: it pauses at its first statement
            run();
        });
        debugRun.setEnabled(false);
        debugRun.setToolTipText("Run, paused at the first statement, for the attached DevTools");
        debugInfo.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
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
        bar.add(debug);
        bar.add(debugRun);
        final JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.CENTER);
        north.add(debugInfo, BorderLayout.SOUTH);
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

    private void run() {
        if (sample == null) {
            return;
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
    }

    private void toggleDebug() {
        try {
            final String url = runner.debugInChrome(debug.isSelected(), InspectOptions.DEFAULT_PORT);
            debugRun.setEnabled(url != null);
            if (url == null) {
                runner.pauseOnNextRun(false);
                debugInfo.setText(" ");
            } else {
                debugInfo.setText("Debugger listening on " + url + "  \u2014  open chrome://inspect in Chrome and click \"inspect\" under Remote Target, "
                        + "then press Debug to run paused at the first statement.");
            }
        } catch (final IOException e) {
            debug.setSelected(false);
            JOptionPane.showMessageDialog(this, "Cannot start the debugger server: " + e.getMessage(), "Start the debugger server", JOptionPane.ERROR_MESSAGE);
        }
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
