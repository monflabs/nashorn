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

package org.monflabs.nashorn.internal.objects;

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;

/**
 * ES2018 25.1.3 %AsyncIteratorPrototype% - the object every async iterator
 * inherits from, whose only member is {@code [Symbol.asyncIterator]() { return
 * this; }}. It is never instantiated; only its nasgen-built prototype is used,
 * as the parent of %AsyncGeneratorPrototype% and %AsyncFromSyncIteratorPrototype%.
 */
@ScriptClass("AsyncIterator")
public abstract class AbstractAsyncIterator extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    protected AbstractAsyncIterator(final ScriptObject prototype, final PropertyMap map) {
        super(prototype, map);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@asyncIterator")
    public static Object asyncIterator(final Object self) {
        return self;
    }
}
