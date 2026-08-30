/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.objects;

import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Undefined;

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

@ScriptClass("ArrayIterator")
public class ArrayIterator extends AbstractIterator {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private ScriptObject iteratedObject;
    private long nextIndex = 0L;
    private final IterationKind iterationKind;
    private final Global global;


    private ArrayIterator(final Object iteratedObject, final IterationKind iterationKind, final Global global) {
        super(global.getArrayIteratorPrototype(), $nasgenmap$);
        this.iteratedObject = iteratedObject instanceof ScriptObject ? (ScriptObject) iteratedObject : null;
        this.iterationKind = iterationKind;
        this.global = global;
    }

    static ArrayIterator newArrayValueIterator(final Object iteratedObject) {
        return new ArrayIterator(Global.toObject(iteratedObject), IterationKind.VALUE, Global.instance());
    }

    static ArrayIterator newArrayKeyIterator(final Object iteratedObject) {
        return new ArrayIterator(Global.toObject(iteratedObject), IterationKind.KEY, Global.instance());
    }

    static ArrayIterator newArrayKeyValueIterator(final Object iteratedObject) {
        return new ArrayIterator(Global.toObject(iteratedObject), IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     * 22.1.5.2.1 %ArrayIteratorPrototype%.next()
     *
     * @param self the self reference
     * @param arg the argument
     * @return the next result
     */
    // 22.1.5.2.1 and its siblings declare no parameter: what the value would
    // be is not read, and length says so
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object next(final Object self, final Object arg) {
        if (!(self instanceof ArrayIterator)) {
            throw typeError("not.a.array.iterator", ScriptRuntime.safeToString(self));
        }
        return ((ArrayIterator)self).next(arg);
    }

    @Override
    public String getClassName() {
        return "Array Iterator";
    }

    @Override
    protected IteratorResult next(final Object arg) {
        final long index = nextIndex;

        // 22.1.5.2.1 step 8.a: a typed array is validated on every step, so a
        // buffer detached in the middle of a loop stops it with an error rather
        // than with an end
        if (iteratedObject instanceof ArrayBufferView view && view.isDetached()) {
            throw typeError("detached.array.buffer");
        }

        if (iteratedObject == null || index >= JSType.toUint32(iteratedObject.getLength())) {
            // ES6 22.1.5.2.1 step 10
            iteratedObject = null;
            return makeResult(Undefined.getUndefined(), Boolean.TRUE, global);
        }

        nextIndex++;

        if (iterationKind == IterationKind.KEY_VALUE) {
            final NativeArray value = new NativeArray(
                    new Object[] {JSType.toNarrowestNumber(index), iteratedObject.get((double) index)});
            return makeResult(value, Boolean.FALSE, global);
        }

        final Object value = iterationKind == IterationKind.KEY ?
                JSType.toNarrowestNumber(index) : iteratedObject.get((double) index);
        return makeResult(value, Boolean.FALSE, global);
    }


    /**
     * ES2015 22.1.5.2.2 %ArrayIteratorPrototype% [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Array Iterator";

}
