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

import java.lang.invoke.MethodHandle;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ES2025 25.1.6 an Iterator Helper object: the lazy result of
 * {@code Iterator.prototype.map}/{@code filter}/{@code take}/{@code drop}/
 * {@code flatMap}. It wraps an underlying iterator (captured with its
 * {@code next} method at creation) and produces its own values on demand,
 * applying the operation one step at a time.
 */
@ScriptClass("IteratorHelper")
public final class IteratorHelper extends AbstractIterator {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /** Which helper operation this object performs. */
    enum Kind { MAP, FILTER, TAKE, DROP, FLATMAP }

    private final Kind kind;
    private final Global global;

    /** The underlying iterator, and its captured next method; iterated is null once done. */
    private Object iterated;
    private final Object nextMethod;

    /** The mapper/predicate, for MAP/FILTER/FLATMAP. */
    private final Object callback;
    /** The 0-based index handed to the callback. */
    private long counter;

    /** For TAKE/DROP: how many are left to yield / still to skip. */
    private double limit;
    private boolean dropDone;

    /** For FLATMAP: the current inner iterator and its next method. */
    private Object innerIterated;
    private Object innerNext;

    IteratorHelper(final Kind kind, final Object iterated, final Object nextMethod,
            final Object callback, final double limit, final Global global) {
        super(global.getIteratorHelperPrototype(), $nasgenmap$);
        this.kind = kind;
        this.iterated = iterated;
        this.nextMethod = nextMethod;
        this.callback = callback;
        this.limit = limit;
        this.global = global;
    }

    @Override
    public String getClassName() {
        return "Iterator Helper";
    }

    /**
     * 25.1.6.1 %IteratorHelperPrototype%.next().
     *
     * @param self the iterator helper
     * @param arg  the (ignored) argument
     * @return the next result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object next(final Object self, final Object arg) {
        if (!(self instanceof IteratorHelper)) {
            throw typeError("not.a.iterator", ScriptRuntime.safeToString(self));
        }
        return ((IteratorHelper) self).next(arg);
    }

    /**
     * 25.1.6.1 %IteratorHelperPrototype%.return(): close the underlying iterator.
     *
     * @param self the iterator helper
     * @param arg  the (ignored) argument
     * @return an already-done result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0, name = "return")
    public static Object ret(final Object self, final Object arg) {
        if (!(self instanceof IteratorHelper helper)) {
            throw typeError("not.a.iterator", ScriptRuntime.safeToString(self));
        }
        // an explicit return() is a normal completion: the underlying's return
        // method's throw (if any) propagates
        final Object inner = helper.innerIterated;
        final Object it = helper.iterated;
        helper.innerIterated = null;
        helper.iterated = null;
        AbstractIterator.closeIterator(inner);
        AbstractIterator.closeIterator(it);
        return helper.makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, helper.global);
    }

    /** Close the underlying iterators for an abrupt completion, dropping their errors. */
    private void close() {
        AbstractIterator.closeIteratorOnError(innerIterated);
        innerIterated = null;
        AbstractIterator.closeIteratorOnError(iterated);
        iterated = null;
    }

    /** Pulls one result object from an (iterator, nextMethod) pair; null when done. */
    private ScriptObject step(final Object it, final Object next) {
        final MethodHandle call = AbstractIterator.getIteratorInvoker(global);
        final Object result;
        try {
            result = call.invokeExact(next, it);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
        if (!(result instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(result));
        }
        final MethodHandle doneInvoker = AbstractIterator.getDoneInvoker(global);
        try {
            if (JSType.toBoolean((Object) doneInvoker.invokeExact((Object) sobj))) {
                return null;
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
        return sobj;
    }

    private Object valueOf(final ScriptObject result) {
        try {
            return (Object) AbstractIterator.getValueInvoker(global).invokeExact((Object) result);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Object callBack(final Object value) {
        try {
            return ScriptRuntime.call(callback, ScriptRuntime.UNDEFINED, new Object[] { value, (double) counter++ });
        } catch (final RuntimeException e) {
            close();
            throw e;
        }
    }

    private IteratorResult done() {
        iterated = null;
        return makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, global);
    }

    @Override
    protected IteratorResult next(final Object arg) {
        if (iterated == null) {
            return makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, global);
        }
        switch (kind) {
        case MAP: {
            final ScriptObject r = step(iterated, nextMethod);
            if (r == null) {
                return done();
            }
            return makeResult(callBack(valueOf(r)), Boolean.FALSE, global);
        }
        case FILTER:
            for (;;) {
                final ScriptObject r = step(iterated, nextMethod);
                if (r == null) {
                    return done();
                }
                final Object value = valueOf(r);
                if (JSType.toBoolean(callBack(value))) {
                    return makeResult(value, Boolean.FALSE, global);
                }
            }
        case TAKE: {
            if (limit <= 0) {
                // take exhausted: close the underlying on a normal completion,
                // letting its return method's throw (if any) propagate
                final Object it = iterated;
                iterated = null;
                AbstractIterator.closeIterator(it);
                return makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, global);
            }
            limit--;
            final ScriptObject r = step(iterated, nextMethod);
            if (r == null) {
                return done();
            }
            return makeResult(valueOf(r), Boolean.FALSE, global);
        }
        case DROP: {
            if (!dropDone) {
                while (limit > 0) {
                    limit--;
                    if (step(iterated, nextMethod) == null) {
                        return done();
                    }
                }
                dropDone = true;
            }
            final ScriptObject r = step(iterated, nextMethod);
            if (r == null) {
                return done();
            }
            return makeResult(valueOf(r), Boolean.FALSE, global);
        }
        case FLATMAP:
            for (;;) {
                if (innerIterated == null) {
                    final ScriptObject r = step(iterated, nextMethod);
                    if (r == null) {
                        return done();
                    }
                    final Object mapped = callBack(valueOf(r));
                    openInner(mapped);
                }
                final ScriptObject ir = step(innerIterated, innerNext);
                if (ir == null) {
                    innerIterated = null;
                    continue;
                }
                return makeResult(valueOf(ir), Boolean.FALSE, global);
            }
        default:
            throw new AssertionError();
        }
    }

    /** ES2025 GetIteratorFlattenable(mapped, reject-primitives) for flatMap. */
    private void openInner(final Object mapped) {
        if (JSType.isPrimitive(mapped)) {
            close();
            throw typeError("not.an.object", ScriptRuntime.safeToString(mapped));
        }
        final ScriptObject obj = (ScriptObject) mapped;
        final Object method = obj.get(NativeSymbol.iterator);
        if (method == ScriptRuntime.UNDEFINED || method == null) {
            innerIterated = obj;
        } else if (Bootstrap.isCallable(method)) {
            final MethodHandle call = AbstractIterator.getIteratorInvoker(global);
            try {
                final Object it = call.invokeExact(method, mapped);
                if (JSType.isPrimitive(it)) {
                    close();
                    throw typeError("not.an.object", ScriptRuntime.safeToString(it));
                }
                innerIterated = it;
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        } else {
            close();
            throw typeError("not.a.function", ScriptRuntime.safeToString(method));
        }
        innerNext = ((ScriptObject) innerIterated).get("next");
    }

    /** ES2025 25.1.6.2 %IteratorHelperPrototype% [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Iterator Helper";
}
