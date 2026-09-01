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

package org.monflabs.nashorn.api.debugger;

import java.io.IOException;

/**
 * A debugger frontend that the {@code --inspect} option starts: something that
 * exposes a {@link Debugger} to the outside, such as the Chrome DevTools
 * Protocol server of the {@code nashorn-debugger} artifact. Found through
 * {@link java.util.ServiceLoader}.
 *
 * @since 2017.0.0
 */
public interface DebuggerFrontend {

    /**
     * The frontend's name, for messages.
     * @return the name
     */
    String name();

    /**
     * Starts the frontend. With {@link InspectOptions#waitForDebugger()} set,
     * returns only once a client has attached and asked execution to proceed.
     *
     * @param debugger the debugger to expose
     * @param options where to listen
     * @return a handle that stops the frontend
     * @throws IOException if the frontend cannot start
     */
    AutoCloseable start(Debugger debugger, InspectOptions options) throws IOException;
}
