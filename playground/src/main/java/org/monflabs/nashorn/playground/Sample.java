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

import java.util.List;
import java.util.Map;

/**
 * A sample of the library: a folder with a {@code main.js}, an optional
 * README, optional sibling files, and the engine options its leading
 * {@code // @option} comments ask for.
 *
 * @param id the folder path within the library, the sample's identity
 * @param categories the display names of the enclosing folders, outermost first
 * @param title the display name: the README's first heading, else the folder name without its number
 * @param source the text of main.js
 * @param readme the README text, or null
 * @param files the other files of the folder, by name
 * @param options the engine options the sample asks for
 */
public record Sample(String id, List<String> categories, String title, String source, String readme,
        Map<String, String> files, List<String> options) {

    /** The name the script runs under. */
    public String fileName() {
        return id.substring(id.lastIndexOf('/') + 1) + "/main.js";
    }
}
