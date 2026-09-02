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

/**
 * One link of an engine's module-loading chain: asked, in registration order,
 * for every specifier an {@code import} names, until one answers.
 *
 * @since 2017.0.0
 */
@FunctionalInterface
public interface ModuleLoader {

    /**
     * Loads the module a specifier names, or null if this loader does not
     * have it - the chain then moves to the next loader.
     *
     * <p>{@code referrer} is the module the {@code import} was written in,
     * null for an entry module. A loader resolves a relative specifier -
     * {@code ./x}, {@code ../x} - against a referrer it recognises as its own
     * (by {@link Module#origin()}), and returns null for a foreign one.
     *
     * @param specifier the text between the quotes of the import
     * @param referrer the importing module, or null
     * @return the module, or null
     */
    Module load(String specifier, Module referrer);
}
