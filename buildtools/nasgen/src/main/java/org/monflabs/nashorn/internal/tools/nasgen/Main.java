/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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

package org.monflabs.nashorn.internal.tools.nasgen;

import java.io.IOException;
import java.lang.classfile.ClassModel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class for the "nasgen" tool.
 *
 */
public class Main {
    private static final boolean DEBUG = Boolean.getBoolean("nasgen.debug");

    private interface ErrorReporter {
        public void error(String msg);
    }

    /**
     * Public entry point for Nasgen if invoked from command line. Nasgen takes three arguments
     * in order: input directory, package list, output directory
     *
     * @param args argument vector
     */
    public static void main(final String[] args) {
        final ErrorReporter reporter = msg -> Main.error(msg, 1);
        if (args.length == 3) {
            processAll(args[0], args[1], args[2], reporter);
        } else {
            error("Usage: nasgen <input-dir> <package-list> <output-dir>", 1);
        }
    }

    private static void processAll(final String in, final String pkgList, final String out, final ErrorReporter reporter) {
        final Path inDir = Path.of(in);
        if (!Files.isDirectory(inDir)) {
            reporter.error(in + " does not exist or not a directory");
            return;
        }

        final Path outDir = Path.of(out);
        if (!Files.isDirectory(outDir)) {
            reporter.error(out + " does not exist or not a directory");
            return;
        }

        for (final String pkg : pkgList.split(":")) {
            final String pkgPath = pkg.replace('.', '/');
            for (final Path clazz : classFilesIn(inDir.resolve(pkgPath), reporter)) {
                if (! process(clazz, outDir.resolve(pkgPath), reporter)) {
                    return;
                }
            }
        }
    }

    private static List<Path> classFilesIn(final Path dir, final ErrorReporter reporter) {
        final List<Path> classes = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "*.class")) {
            for (final Path entry : entries) {
                if (Files.isRegularFile(entry)) {
                    classes.add(entry);
                }
            }
        } catch (final IOException e) {
            reporter.error(e.getMessage());
        }
        return classes;
    }

    private static boolean process(final Path inFile, final Path outDir, final ErrorReporter reporter) {
        try {
            final ClassModel cm = ClassGenerator.CLASS_FILE.parse(Files.readAllBytes(inFile));
            final ScriptClassInfo sci = ScriptClassInfoCollector.collect(cm);

            if (sci != null) {
                try {
                    sci.verify();
                } catch (final Exception e) {
                    reporter.error(e.getMessage());
                    return false;
                }

                // create necessary output package dir
                Files.createDirectories(outDir);

                // instrument @ScriptClass
                final String fileName = inFile.getFileName().toString();
                write(outDir.resolve(fileName), ScriptClassInstrumentor.instrument(cm, sci));

                // simple class name without package prefix
                final String simpleName = fileName.substring(0, fileName.indexOf(".class"));

                if (sci.isPrototypeNeeded()) {
                    write(outDir.resolve(simpleName + StringConstants.PROTOTYPE_SUFFIX + ".class"),
                          new PrototypeGenerator(sci).getClassBytes());
                }

                if (sci.isConstructorNeeded()) {
                    write(outDir.resolve(simpleName + StringConstants.CONSTRUCTOR_SUFFIX + ".class"),
                          new ConstructorGenerator(sci).getClassBytes());
                }
            }
            return true;
        } catch (final IOException | RuntimeException e) {
            if (DEBUG) {
                e.printStackTrace(System.err);
            }
            reporter.error(inFile + ": " + e);

            return false;
        }
    }

    private static void write(final Path file, final byte[] classBytes) throws IOException {
        if (DEBUG) {
            verify(classBytes);
        }
        Files.write(file, classBytes);
    }

    private static void verify(final byte[] classBytes) {
        for (final VerifyError error : ClassGenerator.CLASS_FILE.verify(classBytes)) {
            System.err.println(error.getMessage());
        }
    }

    private static void error(final String msg, final int exitCode) {
        System.err.println(msg);
        System.exit(exitCode);
    }
}
