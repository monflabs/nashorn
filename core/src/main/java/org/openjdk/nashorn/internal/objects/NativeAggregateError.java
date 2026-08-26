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

package org.openjdk.nashorn.internal.objects;

import static org.openjdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;

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
    private NativeAggregateError(final Object errors, final Object msg, final ScriptObject proto,
            final PropertyMap map) {
        super(proto, map);
        if (msg != UNDEFINED) {
            this.instMessage = JSType.toString(msg);
        } else {
            this.delete(NativeError.MESSAGE, false);
        }
        // 20.5.7.1.1 step 8: the errors are read into an array of their own, and
        // the property holding it is not enumerable - so it does not turn up in
        // JSON.stringify or a for-in over the error
        addOwnProperty("errors", org.openjdk.nashorn.internal.runtime.Property.NOT_ENUMERABLE,
                ScriptRuntime.ITERATOR_REST(ScriptRuntime.GET_ITERATOR(errors)));
        NativeError.initException(this);
    }

    NativeAggregateError(final Object errors, final Object msg, final Global global) {
        this(errors, msg, global.getAggregateErrorPrototype(), $nasgenmap$);
    }

    private NativeAggregateError(final Object errors, final Object msg) {
        this(errors, msg, Global.instance());
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
            final Object msg) {
        return new NativeAggregateError(errors, msg);
    }
}
