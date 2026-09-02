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

/**
 * Pluggable loading for ES2015 modules. An engine holds a chain of
 * {@link org.monflabs.nashorn.api.modules.ModuleLoader}s, registered with
 * {@link org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder#moduleLoader};
 * every {@code import} asks each loader in order and the first that answers
 * wins. Modules are consumed with the language's own syntax - a source handed
 * to {@code eval} that parses as a module runs as one - and a module can be
 * script ({@link org.monflabs.nashorn.api.modules.Module#source Module.source})
 * or pure Java ({@link org.monflabs.nashorn.api.modules.Module#values Module.values}).
 *
 * @since 2017.0.0
 */
package org.monflabs.nashorn.api.modules;
