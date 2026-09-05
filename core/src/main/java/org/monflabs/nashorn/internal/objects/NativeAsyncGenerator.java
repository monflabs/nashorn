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
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.AsyncGeneratorSupport;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * An ECMAScript 2018 async generator object - the value {@code async function*}
 * returns. Its {@code next}, {@code return} and {@code throw} each answer with a
 * promise for a {@code {value, done}} result, driven by {@link AsyncGeneratorSupport}.
 */
@ScriptClass("AsyncGenerator")
public final class NativeAsyncGenerator extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private final AsyncGeneratorSupport support;

    public NativeAsyncGenerator(final AsyncGeneratorSupport support, final ScriptObject prototype) {
        super(prototype, $nasgenmap$);
        this.support = support;
    }

    public AsyncGeneratorSupport getSupport() {
        return support;
    }

    @Override
    public String getClassName() {
        return "AsyncGenerator";
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object next(final Object self, final Object value) {
        if (self instanceof NativeAsyncGenerator generator) {
            return generator.support.next(value);
        }
        return rejectBadThis(self);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "return")
    public static Object _return(final Object self, final Object value) {
        if (self instanceof NativeAsyncGenerator generator) {
            return generator.support.doReturn(value);
        }
        return rejectBadThis(self);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "throw")
    public static Object _throw(final Object self, final Object exception) {
        if (self instanceof NativeAsyncGenerator generator) {
            return generator.support.doThrow(exception);
        }
        return rejectBadThis(self);
    }

    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "AsyncGenerator";

    /**
     * An AsyncGeneratorValidate failure is IfAbruptRejectPromise (25.5.1.2/.3/.4):
     * next/return/throw with a bad {@code this} must return a <em>rejected
     * promise</em>, never throw synchronously.
     */
    private static NativePromise rejectBadThis(final Object self) {
        final Global global = Global.instance();
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        NativePromise.rejectAsyncPromise(promise,
                ECMAErrors.typeError("not.a.generator", ScriptRuntime.safeToString(self)).getThrown());
        return promise;
    }
}
