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

package org.monflabs.js.debugger.ui.cdp;

/**
 * A protocol failure: either a JSON-RPC {@code error} the server returned for
 * a request, or a transport-level condition mapped to a code of its own. The
 * {@link #code()} is the server's for a real protocol error, and one of the
 * negative {@code TRANSPORT_*} constants otherwise, so a client can tell "the
 * server refused this call" from "there is no connection".
 */
public final class CdpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The connection was refused because another client is already attached (HTTP 403). */
    public static final int TRANSPORT_BUSY = -403;
    /** The connection closed, or never opened, for any other reason. */
    public static final int TRANSPORT_CLOSED = -1;

    private final int code;

    /**
     * Creates a failure.
     * @param code the server's error code, or a {@code TRANSPORT_*} constant
     * @param message the message
     */
    public CdpException(final int code, final String message) {
        super(message);
        this.code = code;
    }

    /**
     * The error code.
     * @return the code
     */
    public int code() {
        return code;
    }

    /** Whether this is a transport failure rather than a protocol error. */
    public boolean isTransport() {
        return code == TRANSPORT_BUSY || code == TRANSPORT_CLOSED;
    }
}
