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
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * ES2018 25.1.4 %AsyncFromSyncIteratorPrototype% - the adaptor that lets a
 * {@code for await} loop, or an async generator's {@code yield*}, consume an
 * ordinary (synchronous) iterable: each {@code next} calls the sync iterator,
 * awaits the value it produced, and resolves a promise with the wrapped result.
 *
 * <p>Per {@code CreateAsyncFromSyncIterator}, the sync iterator's {@code next}
 * method is fetched <em>once</em> when the wrapper is built (it is part of the
 * iterator record) and reused; {@code return} and {@code throw} are looked up on
 * each call.
 */
@ScriptClass("AsyncFromSyncIterator")
public final class NativeAsyncFromSyncIterator extends ScriptObject {
    private static PropertyMap $nasgenmap$;

    private final ScriptObject syncIterator;
    private final Object syncNext;
    private final Global global;

    public NativeAsyncFromSyncIterator(final ScriptObject syncIterator, final Object syncNext,
            final Global global, final ScriptObject prototype) {
        super(prototype, $nasgenmap$);
        this.syncIterator = syncIterator;
        this.syncNext = syncNext;
        this.global = global;
    }

    @Override
    public String getClassName() {
        return "AsyncFromSyncIterator";
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object next(final Object self, final Object... args) {
        return check(self).next(args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED, args.length > 0);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "return", arity = 1)
    public static Object _return(final Object self, final Object... args) {
        return check(self).doReturn(args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED, args.length > 0);
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "throw", arity = 1)
    public static Object _throw(final Object self, final Object... args) {
        return check(self).doThrow(args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED, args.length > 0);
    }

    /** %AsyncFromSyncIteratorPrototype%.next (25.1.4.2.1): call the cached sync next, await, wrap. */
    private Object next(final Object value, final boolean hasValue) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        try {
            if (!(syncNext instanceof ScriptFunction fn)) {
                throw typeError("not.a.function", "next");
            }
            final Object result = hasValue
                    ? ScriptRuntime.apply(fn, syncIterator, value)
                    : ScriptRuntime.apply(fn, syncIterator);
            continuation(promise, result, true);
        } catch (final ECMAException thrown) {
            NativePromise.rejectAsyncPromise(promise, thrown.getThrown());
        }
        return promise;
    }

    /** %AsyncFromSyncIteratorPrototype%.return (25.1.4.2.2). */
    private Object doReturn(final Object value, final boolean hasValue) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        try {
            final Object returner = getMethod("return");
            if (returner == ScriptRuntime.UNDEFINED) {
                // no return on the sync iterator: resolve with {value, done:true}
                resolveResult(promise, value, true);
                return promise;
            }
            final Object result = hasValue
                    ? ScriptRuntime.apply((ScriptFunction) returner, syncIterator, value)
                    : ScriptRuntime.apply((ScriptFunction) returner, syncIterator);
            if (!(result instanceof ScriptObject)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(result));
            }
            continuation(promise, result, false);
        } catch (final ECMAException thrown) {
            NativePromise.rejectAsyncPromise(promise, thrown.getThrown());
        }
        return promise;
    }

    /** %AsyncFromSyncIteratorPrototype%.throw (25.1.4.2.3). */
    private Object doThrow(final Object value, final boolean hasValue) {
        final NativePromise promise = NativePromise.newAsyncPromise(global);
        try {
            final Object thrower = getMethod("throw");
            if (thrower == ScriptRuntime.UNDEFINED) {
                // The sync iterator has no throw: close it - IteratorClose with a
                // throw completion - then reject with a fresh TypeError. A throwing
                // "return" getter propagates that error; a throwing or non-object
                // return() call is swallowed and the TypeError wins.
                final ECMAException typeErr = typeError("not.a.function", "throw");
                final Object returner = getMethod("return"); // may throw (poisoned getter / not callable)
                if (returner != ScriptRuntime.UNDEFINED) {
                    try {
                        ScriptRuntime.apply((ScriptFunction) returner, syncIterator);
                    } catch (final ECMAException ignoredReturnCall) {
                        // completion is a throw: the return() outcome is discarded
                    }
                }
                NativePromise.rejectAsyncPromise(promise, typeErr.getThrown());
                return promise;
            }
            final Object result = hasValue
                    ? ScriptRuntime.apply((ScriptFunction) thrower, syncIterator, value)
                    : ScriptRuntime.apply((ScriptFunction) thrower, syncIterator);
            if (!(result instanceof ScriptObject)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(result));
            }
            continuation(promise, result, true);
        } catch (final ECMAException thrown) {
            NativePromise.rejectAsyncPromise(promise, thrown.getThrown());
        }
        return promise;
    }

    /**
     * AsyncFromSyncIteratorContinuation (25.1.4.4): read done then value from the
     * sync result, await the value, and resolve with {@code {awaitedValue, done}}.
     * When {@code closeOnRejection} (next/throw, not return), a rejection - of the
     * awaited value, or thrown while wrapping it (a poisoned {@code constructor}) -
     * closes the sync iterator before the promise rejects. A throwing {@code done}
     * or {@code value} getter rejects without closing (it propagates to the caller).
     */
    private void continuation(final NativePromise promise, final Object result, final boolean closeOnRejection) {
        if (!(result instanceof ScriptObject resultObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(result));
        }
        final boolean done = JSType.toBoolean(resultObject.get("done"));
        final Object itemValue = resultObject.get("value");
        try {
            NativePromise.await(global, itemValue,
                    awaited -> resolveResult(promise, awaited, done),
                    error -> rejectClosing(promise, error, closeOnRejection));
        } catch (final ECMAException wrapperError) {
            rejectClosing(promise, wrapperError.getThrown(), closeOnRejection);
        }
    }

    /** Reject the promise, first closing the sync iterator (a throw completion) when asked. */
    private void rejectClosing(final NativePromise promise, final Object reason, final boolean closeOnRejection) {
        Object rejectWith = reason;
        if (closeOnRejection) {
            try {
                closeOnThrow();
            } catch (final ECMAException closeError) {
                // a throwing "return" getter replaces the pending reason
                rejectWith = closeError.getThrown();
            }
        }
        NativePromise.rejectAsyncPromise(promise, rejectWith);
    }

    /**
     * IteratorClose(syncIterator) under a throw completion: call {@code return} if
     * present, discarding its (possibly abrupt) outcome - only a throwing
     * {@code return} <em>getter</em> escapes, to replace the pending reason.
     */
    private void closeOnThrow() {
        final Object returner = getMethod("return");
        if (returner != ScriptRuntime.UNDEFINED) {
            try {
                ScriptRuntime.apply((ScriptFunction) returner, syncIterator);
            } catch (final ECMAException ignoredReturnCall) {
                // the completion is a throw: the return() outcome is discarded
            }
        }
    }

    private void resolveResult(final NativePromise promise, final Object value, final boolean done) {
        final ScriptObject result = global.newObject();
        result.set("value", value, 0);
        result.set("done", done, 0);
        NativePromise.resolveAsyncPromise(promise, result);
    }

    /** GetMethod(syncIterator, name): undefined/null -&gt; undefined; present but not callable -&gt; TypeError. */
    private Object getMethod(final String name) {
        final Object method = syncIterator.get(name);
        if (method == ScriptRuntime.UNDEFINED || method == null) {
            return ScriptRuntime.UNDEFINED;
        }
        if (!(method instanceof ScriptFunction)) {
            throw typeError("not.a.function", name);
        }
        return method;
    }

    private static NativeAsyncFromSyncIterator check(final Object self) {
        if (self instanceof NativeAsyncFromSyncIterator iter) {
            return iter;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(self));
    }
}
