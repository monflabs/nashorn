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
 * EXPERIMENTAL, INCOMPLETE. A Node-compatibility module resolver for the
 * Nashorn engine, provided as a convenience and as a worked example. An engine
 * finds it through the {@link org.monflabs.nashorn.api.modules.ModuleLoader}
 * service and consults it before its own module loaders, so a bare
 * {@code import fs from "fs"} (or {@code "node:fs"}) resolves to the built-in.
 *
 * <p>Register it with the engine builder explicitly -
 * {@code new NashornScriptEngineBuilder().moduleLoader(new NodeModuleLoader()).build()};
 * it is not discovered. It implements its modules in pure Java over the engine's
 * internal object model, which {@code org.monflabs.nashorn} exports to this
 * module by name. Because of that coupling it is a companion to one specific
 * {@code nashorn-core} and is not published.
 *
 * @moduleGraph
 * @since 2017.0.0
 */
module org.monflabs.nashorn.modules.node {
    requires transitive org.monflabs.nashorn;
    requires java.management;

    exports org.monflabs.nashorn.modules.node;
}
