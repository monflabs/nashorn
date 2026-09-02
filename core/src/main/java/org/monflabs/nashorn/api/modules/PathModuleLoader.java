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

package org.monflabs.nashorn.api.modules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Loads modules from the filesystem, under a root: an entry, bare or rooted
 * specifier resolves against the root, {@code ./x} and {@code ../x} against
 * the directory of the importing module, an absolute path as itself. Names
 * are literal - no extension guessing - and the canonical name is the
 * absolute normalised path. A file that is not there, or a referrer of some
 * other loader, is answered with null.
 *
 * @since 2017.0.0
 */
public final class PathModuleLoader implements ModuleLoader {
    private final Path root;

    /**
     * A loader rooted at a directory.
     *
     * @param root what relative specifiers resolve against
     */
    public PathModuleLoader(final Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Module load(final String specifier, final Module referrer) {
        final Path candidate;
        try {
            final Path path = Path.of(specifier);
            if (path.isAbsolute()) {
                candidate = path.normalize();
            } else if (specifier.startsWith("./") || specifier.startsWith("../")) {
                if (referrer == null) {
                    candidate = root.resolve(specifier).normalize();
                } else if (referrer.origin() instanceof Path importer) {
                    candidate = importer.getParent().resolve(specifier).normalize();
                } else {
                    return null;   // another loader's module: not ours to resolve
                }
            } else {
                candidate = root.resolve(specifier).normalize();
            }
        } catch (final InvalidPathException notAPath) {
            return null;
        }
        if (!Files.isReadable(candidate)) {
            return null;
        }
        try {
            return Module.source(candidate.toString(), Files.readString(candidate), candidate);
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read module " + candidate, e);
        }
    }

    @Override
    public String toString() {
        return "PathModuleLoader(" + root + ")";
    }
}
