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

package org.monflabs.nashorn.playground.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.SampleLibrary;
import org.testng.annotations.Test;

/**
 * The library reads the same tree off a directory and out of a jar.
 */
public class SampleLibraryTest {

    @Test
    public void theBundledLibraryHasTheFourCategoriesInOrder() throws IOException {
        final SampleLibrary library = SampleLibrary.load();
        final List<String> categories = library.root().children().stream().map(SampleLibrary.Node::name).toList();
        assertEquals(categories, List.of("Getting started", "ECMAScript support", "Nashorn extensions", "Standard libraries"));
        assertTrue(library.samples().size() > 40, "samples: " + library.samples().size());
        final Sample first = library.samples().get(0);
        assertEquals(first.title(), "Hello");
        assertEquals(first.categories(), List.of("Getting started"));
        assertEquals(first.fileName(), "01 - Hello/main.js");
    }

    @Test
    public void readmeTitleDirectivesAndSiblingFiles() throws IOException {
        final SampleLibrary library = SampleLibrary.load();
        final Sample scripting = library.byId("03 - Nashorn extensions/14 - Scripting mode");
        assertNotNull(scripting);
        assertEquals(scripting.options(), List.of("-scripting"));
        assertEquals(scripting.title(), "Scripting mode");
        final Sample load = library.byId("03 - Nashorn extensions/12 - load, __FILE__, __LINE__");
        assertNotNull(load);
        assertEquals(load.files().keySet(), java.util.Set.of("helper.js"));
        assertTrue(load.readme().startsWith("# load"));
        assertEquals(load.options(), List.of());
    }

    @Test
    public void optionsStopAtTheFirstCodeLine() {
        assertEquals(SampleLibrary.options("// @option -scripting\n// @option --annexB=false\nprint(1);\n// @option -strict\n"),
                List.of("-scripting", "--annexB=false"));
        assertEquals(SampleLibrary.options("print(1);\n// @option -strict\n"), List.of());
        assertEquals(SampleLibrary.options("\n  // a comment\n//@option   -strict  \nvar x;"), List.of("-strict"));
    }

    @Test
    public void everySampleHasAReadmeWithAParagraph() throws IOException {
        final List<String> missing = new java.util.ArrayList<>();
        for (final Sample sample : SampleLibrary.load().samples()) {
            final String readme = sample.readme();
            if (readme == null) {
                missing.add(sample.id() + ": no README.md");
                continue;
            }
            final StringBuilder prose = new StringBuilder();
            for (final String line : readme.split("\\R")) {
                if (!line.startsWith("#")) {
                    prose.append(line.trim()).append(' ');
                }
            }
            if (prose.toString().trim().length() < 80) {
                missing.add(sample.id() + ": README has a heading but no real paragraph");
            }
        }
        assertTrue(missing.isEmpty(), missing.size() + " sample(s) lack a README paragraph:\n" + String.join("\n", missing));
    }

    @Test
    public void theSameTreeReadsOutOfAJar() throws IOException {
        final Path jar = Files.createTempFile("samples", ".jar");
        try {
            final Path root = Path.of("target", "classes", "samples").toAbsolutePath();
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar)); Stream<Path> walk = Files.walk(root)) {
                for (final Path p : walk.filter(Files::isRegularFile).toList()) {
                    zip.putNextEntry(new ZipEntry("samples/" + root.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, (OutputStream)zip);
                    zip.closeEntry();
                }
            }
            final SampleLibrary fromDir = SampleLibrary.load(root);
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of())) {
                final SampleLibrary fromJar = SampleLibrary.load(fs.getPath("/samples"));
                assertEquals(fromJar.samples().size(), fromDir.samples().size());
                for (int i = 0; i < fromDir.samples().size(); i++) {
                    final Sample a = fromDir.samples().get(i);
                    final Sample b = fromJar.samples().get(i);
                    assertEquals(b.id(), a.id());
                    assertEquals(b.title(), a.title());
                    assertEquals(b.source(), a.source());
                    assertEquals(b.files(), a.files());
                    assertFalse(b.id().contains("\\"), b.id());
                }
            }
        } finally {
            Files.deleteIfExists(jar);
        }
    }
}
