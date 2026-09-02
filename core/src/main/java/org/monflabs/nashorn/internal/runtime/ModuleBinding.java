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

package org.monflabs.nashorn.internal.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The getter behind an imported name.
 *
 * ES2015 8.1.1.5 makes an import an indirect binding: it names a binding in
 * another module rather than holding a copy, so a function in the exporting
 * module that reassigns the variable is seen by everyone who imported it. The
 * binding is therefore a property whose getter reads the other module, and it
 * has no setter, because 8.1.1.5.5 makes assigning to an import a TypeError.
 */
final class ModuleBinding {
    private static final MethodHandle READ;

    static {
        try {
            READ = MethodHandles.lookup().findStatic(ModuleBinding.class, "read",
                    MethodType.methodType(Object.class, ModuleRecord.class, String.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    private ModuleBinding() {
    }

    @SuppressWarnings("unused")
    private static Object read(final ModuleRecord from, final String exportName, final Object self) {
        return from.read(exportName);
    }

    /**
     * A getter for one export of one module.
     *
     * @param from       the exporting module
     * @param exportName the name it exports the binding under
     * @return a handle of the shape a built-in accessor needs
     */
    static MethodHandle reader(final ModuleRecord from, final String exportName) {
        return MethodHandles.insertArguments(READ, 0, from, exportName);
    }
}
