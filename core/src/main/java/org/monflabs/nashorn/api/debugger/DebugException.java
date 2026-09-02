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

package org.monflabs.nashorn.api.debugger;

/**
 * An evaluation failed: the expression did not parse, or threw.
 *
 * @since 2017.0.0
 */
public final class DebugException extends Exception {
    private static final long serialVersionUID = 1L;

    private final transient Object thrown;

    /**
     * Creates the exception.
     * @param message the message
     * @param thrown the value the script threw, an engine object, or null for a parse error
     * @param cause the underlying exception
     */
    public DebugException(final String message, final Object thrown, final Throwable cause) {
        super(message, cause);
        this.thrown = thrown;
    }

    /**
     * The value the script threw, if the expression threw.
     * @return an engine object, or null
     */
    public Object thrown() {
        return thrown;
    }
}
