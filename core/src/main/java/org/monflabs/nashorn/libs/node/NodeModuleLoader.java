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

package org.monflabs.nashorn.libs.node;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.monflabs.nashorn.api.modules.Module;
import org.monflabs.nashorn.api.modules.ModuleLoader;

/**
 * Resolves Node's built-in modules by their bare or {@code node:} specifier -
 * {@code import fs from "fs"} or {@code import fs from "node:fs"}. This is the
 * fork's Node-compatibility resolver, shipped in the engine and consulted before
 * the user's own module loaders and the filesystem, the way the standard
 * libraries ship in the engine. Only the modules it knows are answered; anything
 * else returns null so the next loader gets a turn.
 *
 * <p>The modules implemented so far are {@code fs} (see {@link NodeFs}) and
 * {@code buffer} (the Node {@code Buffer}, a {@code Uint8Array} subclass) and
 * {@code os} (see {@link NodeOs}).
 *
 * @since 2017.0.0
 */
public final class NodeModuleLoader implements ModuleLoader {

    private static final Module FS = Module.values("fs", NodeFs.exports());
    // Node's Buffer, a Uint8Array subclass, defined in buffer.js and compiled lazily on import.
    private static final Module BUFFER = Module.source("buffer", read("buffer.js"));
    private static final Module OS = Module.values("os", NodeOs.exports());

    private static String read(final String resource) {
        try (InputStream in = NodeModuleLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Creates the resolver. */
    public NodeModuleLoader() {
    }

    @Override
    public Module load(final String specifier, final Module referrer) {
        final String name = specifier.startsWith("node:") ? specifier.substring(5) : specifier;
        switch (name) {
        case "fs":
            return FS;
        case "buffer":
            return BUFFER;
        case "os":
            return OS;
        default:
            return null;
        }
    }

    @Override
    public String toString() {
        return "NodeModuleLoader[fs, buffer, os]";
    }
}
