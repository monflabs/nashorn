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

/**
 * The debugger API: a frontend-agnostic view of running scripts - scripts,
 * breakpoints, paused threads, call frames, scopes and values - for an engine
 * created with the {@code --debugger} option. The Chrome DevTools Protocol
 * frontend in the {@code nashorn-debugger} artifact is one client of it; a test
 * or another protocol adapter can be another.
 *
 * <p>Values are handed out as the engine's own objects behind {@link java.lang.Object}
 * and are to be interpreted through {@link org.monflabs.nashorn.api.debugger.DebugValues}
 * only. Anything that reads a script object must run on the thread that owns
 * its realm: while paused, through {@link org.monflabs.nashorn.api.debugger.PausedEvent#call};
 * otherwise through {@link org.monflabs.nashorn.api.debugger.Debugger#evaluate} or
 * {@link org.monflabs.nashorn.api.debugger.DebugValues}, which bind the realm themselves.
 *
 * @since 20
 */
package org.monflabs.nashorn.api.debugger;
