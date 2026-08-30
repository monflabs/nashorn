/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.runtime;

import static org.monflabs.nashorn.internal.lookup.Lookup.MH;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.monflabs.nashorn.internal.objects.NativeArray;

/**
 * Global functions supported only in scripting mode.
 */
public final class ScriptingFunctions {

    /** Handle to implementation of {@link ScriptingFunctions#readLine} - Nashorn extension */
    public static final MethodHandle READLINE = findOwnMH("readLine", Object.class, Object.class, Object.class);

    /** Handle to implementation of {@link ScriptingFunctions#readFully} - Nashorn extension */
    public static final MethodHandle READFULLY = findOwnMH("readFully",     Object.class, Object.class, Object.class);

    /** Names of special properties used by $ENV API. */
    public static final String ENV_NAME  = "$ENV";

    /** Name of the environment variable for the current working directory. */
    public static final String PWD_NAME  = "PWD";

    private ScriptingFunctions() {
    }

    /**
     * Nashorn extension: global.readLine (scripting-mode-only)
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
        return readLine(prompt);
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
            f = new java.io.File(((CharSequence)file).toString());
        }

        if (f == null || !f.isFile()) {
            throw typeError("not.a.file", ScriptRuntime.safeToString(file));
        }

        return new String(Source.readFully(f));
    }

    // Implementation for pluggable "readLine" functionality
    // Used by jjs interactive mode

    private static Function<String, String> readLineHelper;

    public static void setReadLineHelper(final Function<String, String> func) {
        readLineHelper = Objects.requireNonNull(func);
    }

    public static Function<String, String> getReadLineHelper() {
        return readLineHelper;
    }

    public static String readLine(final Object prompt) throws IOException {
        final String p = (prompt != UNDEFINED)? JSType.toString(prompt) : "";
        if (readLineHelper != null) {
            return readLineHelper.apply(p);
        } else {
            System.out.print(p);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            return reader.readLine();
        }
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptingFunctions.class, name, MH.type(rtype, types));
    }
}
