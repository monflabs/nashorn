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

import java.util.List;

/**
 * A compiled script: a source the engine has turned into code.
 *
 * @since 20
 */
public interface DebugScript {

    /**
     * The id, unique within the debugger.
     * @return the id
     */
    String id();

    /**
     * The url. A source loaded from a URL keeps it; any other gets a unique
     * {@code nashorn:} url, so that every script can be named in a breakpoint.
     * @return the url
     */
    String url();

    /**
     * The name the source was given - a file name, {@code <eval>}, and so on.
     * @return the name
     */
    String name();

    /**
     * The source text.
     * @return the text
     */
    String source();

    /**
     * A digest of the source, stable across engines.
     * @return the digest
     */
    String hash();

    /**
     * The last line, zero based.
     * @return the last line
     */
    int endLine();

    /**
     * The column after the last character of the last line, zero based.
     * @return the end column
     */
    int endColumn();

    /**
     * The length of the source in characters.
     * @return the length
     */
    int length();

    /**
     * Whether this is code handed to {@code eval}.
     * @return true for eval code
     */
    boolean isEval();

    /**
     * Whether this is a module rather than a script.
     * @return true for a module
     */
    boolean isModule();

    /**
     * The context the script was compiled for.
     * @return the context
     */
    ExecutionContext context();

    /**
     * The locations a breakpoint can be set at within a range, in order.
     *
     * @param startLine first line, zero based
     * @param startColumn first column on that line, zero based
     * @param endLine last line, inclusive; negative for the end of the script
     * @param endColumn last column on that line, exclusive
     * @return the locations
     */
    List<Location> possibleBreakpoints(int startLine, int startColumn, int endLine, int endColumn);
}
