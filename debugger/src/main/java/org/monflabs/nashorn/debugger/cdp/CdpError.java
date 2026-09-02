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

package org.monflabs.nashorn.debugger.cdp;

/**
 * A protocol error: the code and message the response carries.
 */
final class CdpError extends Exception {
    private static final long serialVersionUID = 1L;

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int SERVER_ERROR = -32000;

    private final int code;

    CdpError(final int code, final String message) {
        super(message);
        this.code = code;
    }

    int code() {
        return code;
    }

    static CdpError notPaused() {
        return new CdpError(SERVER_ERROR, "Can only perform operation while paused.");
    }

    static CdpError invalidParams(final String what) {
        return new CdpError(INVALID_PARAMS, "Invalid parameters: " + what);
    }
}
