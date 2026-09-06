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

import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import org.monflabs.nashorn.internal.runtime.linker.InvokeByName;

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * ECMAScript 2021 20.5.7 AggregateError.
 *
 * The error {@code Promise.any} rejects with when every promise it was given
 * rejected: one error standing for several, which it carries in an "errors"
 * property. It is the only error type that takes something before its message,
 * and the only one whose instances have a property of their own beyond it.
 */
@ScriptClass("Error")
public final class NativeAggregateError extends ScriptObject {
    /** message property in instance */
    @Property(name = NativeError.MESSAGE, attributes = Attribute.NOT_ENUMERABLE)
    public Object instMessage;

    /** error name property */
    @Property(attributes = Attribute.NOT_ENUMERABLE, where = Where.PROTOTYPE)
    public Object name;

    /** ECMA 15.1.1.1 message property */
    @Property(attributes = Attribute.NOT_ENUMERABLE, where = Where.PROTOTYPE)
    public Object message;

    /** Nashorn extension: underlying exception */
    @Property(attributes = Attribute.NOT_ENUMERABLE)
    public Object nashornException;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    @SuppressWarnings("LeakingThisInConstructor")
    private NativeAggregateError(final Object errors, final Object msg, final Object options,
            final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        if (msg != UNDEFINED) {
            this.instMessage = JSType.toString(msg);
        } else {
            this.delete(NativeError.MESSAGE, false);
        }
        // 20.5.7.1.1: the cause (from an options bag) is installed after the
        // message and before the errors, so getOwnPropertyNames sees that order
        NativeError.installCause(this, options);
        // the errors are read into an array of their own, and the property
        // holding it is not enumerable - so it does not turn up in
        // JSON.stringify or a for-in over the error
        addOwnProperty("errors", org.monflabs.nashorn.internal.runtime.Property.NOT_ENUMERABLE,
                Global.allocate(iterableToList(errors)));
        NativeError.initException(this);
    }

    NativeAggregateError(final Object errors, final Object msg, final Object options, final Global global) {
        this(errors, msg, options, global.getAggregateErrorPrototype(), $nasgenmap$);
    }

    /**
     * ES2021 IterableToList of the errors, done through the iterator protocol so a
     * poisoned {@code @@iterator}, a non-object step, or a non-callable {@code next}
     * is the TypeError the specification asks for (rather than being read leniently).
     */
    private static Object[] iterableToList(final Object errors) {
        final Global global = Global.instance();
        final Object iterator = AbstractIterator.getIterator(errors, global);
        final InvokeByName next = AbstractIterator.getNextInvoker(global);
        final MethodHandle done = AbstractIterator.getDoneInvoker(global);
        final MethodHandle value = AbstractIterator.getValueInvoker(global);
        final java.util.List<Object> list = new java.util.ArrayList<>();
        try {
            while (true) {
                final Object step = next.getInvoker().invokeExact(next.getGetter().invokeExact(iterator), iterator, (Object)null);
                if (!(step instanceof ScriptObject)) {
                    throw typeError("not.an.object", ScriptRuntime.safeToString(step));
                }
                if (JSType.toBoolean((Object)done.invokeExact(step))) {
                    return list.toArray();
                }
                list.add((Object)value.invokeExact(step));
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private NativeAggregateError(final Object errors, final Object msg, final Object options) {
        this(errors, msg, options, Global.instance());
    }

    @Override
    public String getClassName() {
        return "Error";
    }

    /**
     * ECMAScript 2021 20.5.7.1 AggregateError(errors, message)
     *
     * @param newObj was this error instantiated with the new operator
     * @param self   self reference
     * @param errors the errors it stands for
     * @param msg    error message
     *
     * @return new AggregateError
     */
    @Constructor(name = "AggregateError", arity = 2)
    public static NativeAggregateError constructor(final boolean newObj, final Object self, final Object errors,
            final Object msg, final Object options) {
        return new NativeAggregateError(errors, msg, options);
    }
}
