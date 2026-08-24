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

package org.openjdk.nashorn.internal.runtime;

import java.util.List;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.objects.NativeSymbol;

/**
 * A module namespace object (ECMAScript 2015 9.4.6), which {@code import * as ns}
 * binds.
 *
 * It is not an ordinary object. Its exports read the module's bindings as they
 * stand, and they describe themselves as values rather than as the accessors
 * that implement them - which is observable, because describing one reads it,
 * and reading an export whose declaration has not run yet is a ReferenceError.
 * Nothing about it can be changed: no property may be written, deleted or
 * redefined, it has no prototype, and it can never be extended.
 */
public final class ModuleNamespace extends ScriptObject {
    private final ModuleRecord module;
    private final List<String> exports;

    ModuleNamespace(final ModuleRecord module, final List<String> exports) {
        super(null, PropertyMap.newMap());
        this.module = module;
        this.exports = exports;

        for (final String exportName : exports) {
            addOwnProperty(exportName, Property.NOT_WRITABLE | Property.NOT_CONFIGURABLE,
                    ScriptFunction.createBuiltin(exportName, ModuleBinding.reader(module, exportName)), null);
        }
        // 9.4.6.5: the only other property it has, and the only one not enumerable
        addOwnProperty(NativeSymbol.toStringTag,
                Property.NOT_WRITABLE | Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE, "Module");
        preventExtensions();
    }

    /**
     * ES2015 9.4.6.4 [[GetOwnProperty]]: an export is a value, not an accessor,
     * and asking about it reads it.
     */
    @Override
    public Object getOwnPropertyDescriptor(final Object key) {
        if (key instanceof String name && exports.contains(name)) {
            return Global.instance().newDataDescriptor(module.read(name), false, true, true);
        }
        return super.getOwnPropertyDescriptor(key);
    }

    /** ES2015 9.4.6.7 [[Delete]]: an export cannot be removed. */
    @Override
    public boolean delete(final Object key, final boolean strict) {
        if (key instanceof String name && exports.contains(name)
                || NativeSymbol.toStringTag.equals(key)) {
            if (strict) {
                throw ECMAErrors.typeError("cant.delete.property", ScriptRuntime.safeToString(key),
                        ScriptRuntime.safeToString(this));
            }
            return false;
        }
        return super.delete(key, strict);
    }

    /**
     * ES2015 9.4.6.6 [[DefineOwnProperty]]: an export may be redefined only as
     * exactly what it already is, which is a way of asking rather than changing.
     */
    @Override
    public boolean defineOwnProperty(final Object key, final Object descriptor, final boolean reject) {
        if (!(key instanceof String name) || !exports.contains(name)) {
            return super.defineOwnProperty(key, descriptor, reject);
        }

        final PropertyDescriptor asked = toPropertyDescriptor(Global.instance(), descriptor);
        final boolean same =
                !(asked.has(PropertyDescriptor.CONFIGURABLE) && asked.isConfigurable())
                && !(asked.has(PropertyDescriptor.ENUMERABLE) && !asked.isEnumerable())
                && asked.type() != PropertyDescriptor.ACCESSOR
                && !(asked.has(PropertyDescriptor.WRITABLE) && !asked.isWritable())
                && (!asked.has(PropertyDescriptor.VALUE)
                        || ScriptRuntime.sameValue(asked.getValue(), module.read(name)));
        if (!same && reject) {
            throw ECMAErrors.typeError("cant.redefine.property", ScriptRuntime.safeToString(key),
                    ScriptRuntime.safeToString(this));
        }
        return same;
    }
}
