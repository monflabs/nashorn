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
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * ES2025 25.1.3.2.1 a Wrap for Valid Iterator: what {@code Iterator.from}
 * returns for an iterator that does not already inherit %IteratorPrototype%. It
 * forwards {@code next} and {@code return} to the wrapped iterator while itself
 * inheriting the iterator helpers.
 */
@ScriptClass("WrapForValidIterator")
public final class WrapForValidIterator extends AbstractIterator {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private Object iterated;
    private final Object nextMethod;
    private final Global global;

    WrapForValidIterator(final Object iterated, final Object nextMethod, final Global global) {
        super(global.getWrapForValidIteratorPrototype(), $nasgenmap$);
        this.iterated = iterated;
        this.nextMethod = nextMethod;
        this.global = global;
    }

    @Override
    public String getClassName() {
        return "Iterator Wrap";
    }

    /**
     * 25.1.3.2.1.1 %WrapForValidIteratorPrototype%.next(): step the wrapped iterator.
     *
     * @param self the wrapper
     * @param arg  ignored
     * @return the next result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object next(final Object self, final Object arg) {
        if (!(self instanceof WrapForValidIterator wrap)) {
            throw typeError("not.a.iterator", ScriptRuntime.safeToString(self));
        }
        return wrap.next(arg);
    }

    /**
     * 25.1.3.2.1.2 %WrapForValidIteratorPrototype%.return(): close the wrapped iterator.
     *
     * @param self the wrapper
     * @param arg  ignored
     * @return an already-done result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0, name = "return")
    public static Object ret(final Object self, final Object arg) {
        if (!(self instanceof WrapForValidIterator wrap)) {
            throw typeError("not.a.iterator", ScriptRuntime.safeToString(self));
        }
        AbstractIterator.closeIterator(wrap.iterated);
        return wrap.makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, wrap.global);
    }

    @Override
    protected IteratorResult next(final Object arg) {
        final ScriptObject result = AbstractIterator.nextResult(iterated, nextMethod, global);
        if (result == null) {
            return makeResult(ScriptRuntime.UNDEFINED, Boolean.TRUE, global);
        }
        return makeResult(AbstractIterator.resultValue(result, global), Boolean.FALSE, global);
    }
}
