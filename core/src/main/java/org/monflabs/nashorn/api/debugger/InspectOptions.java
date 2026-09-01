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

/**
 * Where a {@link DebuggerFrontend} listens.
 *
 * @param host the host to bind
 * @param port the port to bind; 0 for one the system picks
 * @param waitForDebugger whether execution waits for a client to attach
 * @since 2017.0.0
 */
public record InspectOptions(String host, int port, boolean waitForDebugger) {

    /** The default host. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** The default port - Node's. */
    public static final int DEFAULT_PORT = 9229;

    /**
     * Parses {@code [host:]port}, {@code host} or the empty string, as the
     * {@code --inspect} option accepts them.
     *
     * @param spec the specification, possibly null or empty
     * @param waitForDebugger whether execution waits for a client
     * @return the options
     * @throws IllegalArgumentException if the port is not a number
     */
    public static InspectOptions parse(final String spec, final boolean waitForDebugger) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (spec != null && !spec.isEmpty() && !"true".equals(spec)) {
            final int colon = spec.lastIndexOf(':');
            final String portText;
            if (colon >= 0) {
                if (colon > 0) {
                    host = spec.substring(0, colon);
                }
                portText = spec.substring(colon + 1);
            } else if (spec.chars().allMatch(Character::isDigit)) {
                portText = spec;
            } else {
                host = spec;
                portText = "";
            }
            if (!portText.isEmpty()) {
                try {
                    port = Integer.parseInt(portText);
                } catch (final NumberFormatException e) {
                    throw new IllegalArgumentException("not a port number: " + portText, e);
                }
            }
        }
        return new InspectOptions(host, port, waitForDebugger);
    }
}
