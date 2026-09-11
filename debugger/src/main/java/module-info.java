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
 * The Chrome DevTools Protocol frontend for the Nashorn debugger. An engine
 * created with {@code --inspect} finds it through the
 * {@link org.monflabs.nashorn.api.debugger.DebuggerFrontend} service; an
 * embedder can also start {@link org.monflabs.nashorn.debugger.CdpServer}
 * itself.
 *
 * @moduleGraph
 * @since 2017.0.0
 */
module org.monflabs.nashorn.debugger {
    requires transitive org.monflabs.nashorn;

    exports org.monflabs.nashorn.debugger;
    exports org.monflabs.nashorn.debugger.inprocess;

    provides org.monflabs.nashorn.api.debugger.DebuggerFrontend with
        org.monflabs.nashorn.debugger.CdpServer;
}
