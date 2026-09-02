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


package org.monflabs.nashorn.playground;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The sample library, read from the {@code samples} resource tree - out of
 * the jar when packaged, off the directory when run from the build - with no
 * manifest to keep in step: a folder holding a {@code main.js} is a sample,
 * any other folder is a category, and folder names carry a number for the
 * order and lose it for display.
 */
public final class SampleLibrary {
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\s*-\\s*");
    private static final Pattern OPTION = Pattern.compile("^\\s*//\\s*@option\\s+(\\S.*?)\\s*$");
    private static final Pattern HEADING = Pattern.compile("^#+\\s*(.+?)\\s*$");

    /**
     * A node of the library tree: a category with children, or a sample.
     *
     * @param name the display name
     * @param sample the sample, or null for a category
     * @param children the children, in order; empty for a sample
     */
    public record Node(String name, Sample sample, List<Node> children) {
        /** Whether this is a sample rather than a category. */
        public boolean isSample() {
            return sample != null;
        }
    }

    private final Node root;
    private final List<Sample> samples;

    private SampleLibrary(final Node root, final List<Sample> samples) {
        this.root = root;
        this.samples = Collections.unmodifiableList(samples);
    }

    /**
     * Loads the library bundled with the application.
     * @return the library
     * @throws IOException if the resources cannot be read
     */
    public static SampleLibrary load() throws IOException {
        final URL url = SampleLibrary.class.getResource("/samples");
        if (url == null) {
            throw new IOException("no samples resource");
        }
        try {
            return load(resourcePath(url.toURI()));
        } catch (final URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /**
     * Loads a library from a directory tree - the bundled one, or any other.
     * @param root the directory
     * @return the library
     * @throws IOException if it cannot be read
     */
    public static SampleLibrary load(final Path root) throws IOException {
        final List<Sample> samples = new ArrayList<>();
        final Node tree = read(root, root, List.of(), samples);
        return new SampleLibrary(tree, samples);
    }

    /** A path for a resource url, whether it lives in a directory or in a jar. */
    private static Path resourcePath(final URI uri) throws IOException {
        if ("jar".equals(uri.getScheme())) {
            try {
                FileSystems.newFileSystem(uri, Map.of());
            } catch (final FileSystemAlreadyExistsException e) {
                // opened earlier, for us or for another resource
            }
        }
        return Paths.get(uri);
    }

    private static Node read(final Path root, final Path dir, final List<String> categories, final List<Sample> samples) throws IOException {
        final String name = dir.equals(root) ? "Samples" : displayName(dir.getFileName().toString());
        final Path main = dir.resolve("main.js");
        if (Files.isRegularFile(main)) {
            final Sample sample = readSample(root, dir, categories, main);
            samples.add(sample);
            return new Node(sample.title(), sample, List.of());
        }
        final List<Node> children = new ArrayList<>();
        final List<String> nested = new ArrayList<>(categories);
        if (!dir.equals(root)) {
            nested.add(name);
        }
        try (Stream<Path> entries = Files.list(dir)) {
            final List<Path> dirs = entries.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .toList();
            for (final Path child : dirs) {
                children.add(read(root, child, nested, samples));
            }
        }
        return new Node(name, null, Collections.unmodifiableList(children));
    }

    private static Sample readSample(final Path root, final Path dir, final List<String> categories, final Path main) throws IOException {
        final String source = Files.readString(main, StandardCharsets.UTF_8);
        String readme = null;
        final Map<String, String> others = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (final Path p : entries.filter(Files::isRegularFile).sorted().toList()) {
                final String fileName = p.getFileName().toString();
                if (fileName.equals("main.js")) {
                    continue;
                }
                if (fileName.equalsIgnoreCase("README.md")) {
                    readme = Files.readString(p, StandardCharsets.UTF_8);
                } else if (!fileName.startsWith(".")) {
                    others.put(fileName, Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        }
        String id = root.relativize(dir).toString();
        final String separator = dir.getFileSystem().getSeparator();
        if (!separator.equals("/")) {
            id = id.replace(separator, "/");
        }
        final String folderName = displayName(dir.getFileName().toString());
        final String title = readme == null ? folderName : titleOf(readme, folderName);
        return new Sample(id, List.copyOf(categories), title, source, readme, Collections.unmodifiableMap(others), options(source));
    }

    /** The folder name without its ordering number. */
    static String displayName(final String folderName) {
        return NUMBERED.matcher(folderName).replaceFirst("");
    }

    /** The README's first heading, or the fallback. */
    static String titleOf(final String readme, final String fallback) {
        for (final String line : readme.split("\\R")) {
            final Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
            if (!line.isBlank()) {
                break;
            }
        }
        return fallback;
    }

    /**
     * The {@code // @option ...} directives at the head of a source.
     * @param source the script
     * @return the options, in order
     */
    public static List<String> options(final String source) {
        final List<String> options = new ArrayList<>();
        for (final String line : source.split("\\R")) {
            final Matcher m = OPTION.matcher(line);
            if (m.matches()) {
                options.add(m.group(1));
            } else if (!line.isBlank() && !line.trim().startsWith("//")) {
                break;
            }
        }
        return List.copyOf(options);
    }

    /** The tree. */
    public Node root() {
        return root;
    }

    /** Every sample, in tree order. */
    public List<Sample> samples() {
        return samples;
    }

    /** The sample with an id, or null. */
    public Sample byId(final String id) {
        for (final Sample s : samples) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }
}
