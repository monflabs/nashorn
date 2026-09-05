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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * ES2018 25.1.4 %AsyncFromSyncIteratorPrototype% - the adaptor that lets a
 * {@code for await} loop consume an ordinary (synchronous) iterable: each
 * {@code next} calls the sync iterator, awaits the value it produced, and
 * resolves a promise with the wrapped result.
 */
@ScriptClass("AsyncFromSyncIterator")
public final class NativeAsyncFromSyncIterator extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private final ScriptObject syncIterator;
    private final Global global;

    public NativeAsyncFromSyncIterator(final ScriptObject syncIterator, final Global global, final ScriptObject prototype) {
        super(prototype, $nasgenmap$);
        this.syncIterator = syncIterator;
        this.global = global;
    }

    @Override
    public String getClassName() {
        return "AsyncFromSyncIterator";
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object next(final Object self, final Object value) {
        return check(self).step("next", value, false);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "return")
    public static Object _return(final Object self, final Object value) {
        return check(self).step("return", value, true);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "throw")
    public static Object _throw(final Object self, final Object value) {
        return check(self).step("throw", value, true);
    }

    /** Run one step of the sync iterator and adapt it to a promise (25.1.4.4). */
    private Object step(final String method, final Object value, final boolean optional) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        try {
            final Object fn = syncIterator.get(method);
            if (fn == ScriptRuntime.UNDEFINED || fn == null) {
                if (optional) {
                    // no return/throw on the sync iterator: return/throw behave
                    // as if the value were the completion
                    if ("throw".equals(method)) {
                        NativePromise.rejectAsyncPromise(promise, value);
                    } else {
                        resolveResult(promise, value, true);
                    }
                    return promise;
                }
                throw typeError("not.a.function", "next");
            }
            final Object result = ScriptRuntime.apply((ScriptFunction) fn, syncIterator, value);
            if (!(result instanceof ScriptObject resultObject)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(result));
            }
            final boolean done = JSType.toBoolean(resultObject.get("done"));
            final Object itemValue = resultObject.get("value");
            NativePromise.await(global, itemValue,
                    awaited -> resolveResult(promise, awaited, done),
                    error -> NativePromise.rejectAsyncPromise(promise, error));
        } catch (final RuntimeException e) {
            if (e instanceof org.monflabs.nashorn.internal.runtime.ECMAException thrown) {
                NativePromise.rejectAsyncPromise(promise, thrown.getThrown());
            } else {
                throw e;
            }
        }
        return promise;
    }

    private void resolveResult(final NativePromise promise, final Object value, final boolean done) {
        final ScriptObject result = global.newObject();
        result.set("value", value, 0);
        result.set("done", done, 0);
        NativePromise.resolveAsyncPromise(promise, result);
    }

    private static NativeAsyncFromSyncIterator check(final Object self) {
        if (self instanceof NativeAsyncFromSyncIterator iter) {
            return iter;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(self));
    }
}
