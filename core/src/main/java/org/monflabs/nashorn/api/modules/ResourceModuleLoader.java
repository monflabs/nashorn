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

package org.monflabs.nashorn.api.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Loads modules from class-path resources under an optional root: with root
 * {@code /resources/module}, the specifier {@code a} names the resource
 * {@code /resources/module/a}, literally; {@code ./x} and {@code ../x}
 * resolve against the importing module's resource path, never above the
 * root. The canonical name is {@code classpath:/<path>}, which no file path
 * collides with. A missing resource, or a referrer of some other loader, is
 * answered with null.
 *
 * @since 2017.0.0
 */
public final class ResourceModuleLoader implements ModuleLoader {
    private final ClassLoader loader;
    private final String root;   // no leading or trailing slash; possibly empty

    /**
     * A loader over a class's class loader.
     *
     * @param anchor the class whose loader serves the resources
     * @param root the resource path the specifiers live under; null or "/" for the top
     */
    public ResourceModuleLoader(final Class<?> anchor, final String root) {
        this(Objects.requireNonNull(anchor, "anchor").getClassLoader(), root);
    }

    /**
     * A loader over a class loader.
     *
     * @param loader the loader whose resources are served
     * @param root the resource path the specifiers live under; null or "/" for the top
     */
    public ResourceModuleLoader(final ClassLoader loader, final String root) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.root = normalize(root == null ? "" : root);
    }

    private static String normalize(final String path) {
        final StringBuilder sb = new StringBuilder(path);
        while (sb.length() > 0 && sb.charAt(0) == '/') {
            sb.deleteCharAt(0);
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    @Override
    public Module load(final String specifier, final Module referrer) {
        final String resolved;
        if (specifier.startsWith("./") || specifier.startsWith("../")) {
            final String base;
            if (referrer == null) {
                base = root;
            } else if (referrer.origin() instanceof ResourcePath importer && importer.loader == loader) {
                final int slash = importer.path.lastIndexOf('/');
                base = slash < 0 ? "" : importer.path.substring(0, slash);
            } else {
                return null;   // another loader's module: not ours to resolve
            }
            resolved = join(base, specifier);
        } else {
            resolved = join(root, normalize(specifier));
        }
        if (resolved == null || !(root.isEmpty() || resolved.equals(root) || resolved.startsWith(root + "/"))) {
            return null;   // escaped above the root
        }
        final String text = read(resolved);
        return text == null ? null : Module.source("classpath:/" + resolved, text, new ResourcePath(loader, resolved));
    }

    /** base + relative, with . and .. folded; null if it climbs out of the tree. */
    private static String join(final String base, final String relative) {
        final Deque<String> parts = new ArrayDeque<>();
        for (final String part : (base.isEmpty() ? relative : base + "/" + relative).split("/")) {
            switch (part) {
            case "":
            case ".":
                break;
            case "..":
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
                break;
            default:
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    private String read(final String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read module resource " + path, e);
        }
    }

    /** The origin of a module this loader made: its loader and resource path. */
    private record ResourcePath(ClassLoader loader, String path) {
    }

    @Override
    public String toString() {
        return "ResourceModuleLoader(/" + root + ")";
    }
}
