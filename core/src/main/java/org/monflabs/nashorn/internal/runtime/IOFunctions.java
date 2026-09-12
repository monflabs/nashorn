/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-09-12 by Philippe Riand:
 * extracted readLine and readFully from the removed ScriptingFunctions, which
 * installed them only under the equally removed scripting mode; they are now
 * unconditional Nashorn extensions.
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

package org.monflabs.nashorn.internal.runtime;

import static org.monflabs.nashorn.internal.lookup.Lookup.MH;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * The two host I/O functions Nashorn adds to the global object: {@code readLine}
 * and {@code readFully}.
 */
public final class IOFunctions {

    /** Handle to implementation of {@link IOFunctions#readLine} - Nashorn extension */
    public static final MethodHandle READLINE = findOwnMH("readLine", Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link IOFunctions#readFully} - Nashorn extension */
    public static final MethodHandle READFULLY = findOwnMH("readFully", Object.class, Object.class, Object.class);

    private IOFunctions() {
    }

    /**
     * Nashorn extension: global.readLine
     * Read one line of input from the standard input.
     *
     * @param self   self reference
     * @param prompt String used as input prompt
     *
     * @return line that was read
     *
     * @throws IOException if an exception occurs
     */
    public static Object readLine(final Object self, final Object prompt) throws IOException {
        if (prompt != UNDEFINED) {
            System.out.print(JSType.toString(prompt));
        }
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    /**
     * Nashorn extension: Read the entire contents of a text file and return as String.
     *
     * @param self self reference
     * @param file The input file whose content is read.
     *
     * @return String content of the input file.
     *
     * @throws IOException if an exception occurs
     */
    public static Object readFully(final Object self, final Object file) throws IOException {
        File f = null;

        if (file instanceof File) {
            f = (File)file;
        } else if (JSType.isString(file)) {
            f = new File(((CharSequence)file).toString());
        }

        if (f == null || !f.isFile()) {
            throw typeError("not.a.file", ScriptRuntime.safeToString(file));
        }

        return new String(Source.readFully(f));
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), IOFunctions.class, name, MH.type(rtype, types));
    }
}
