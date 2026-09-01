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

package org.monflabs.nashorn.internal.objects;

import java.util.List;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.Undefined;

/**
 * The iterator Headers.entries(), keys() and values() hand out, over a
 * snapshot of the headers in name order.
 */
@ScriptClass("HeadersIterator")
public final class HeadersIterator extends AbstractIterator {
    private static PropertyMap $nasgenmap$;

    private final List<String[]> rows;
    private final IterationKind kind;
    private final Global global;
    private int index;

    HeadersIterator(final List<String[]> rows, final IterationKind kind, final Global global) {
        super(global.getHeadersIteratorPrototype(), $nasgenmap$);
        this.rows = rows;
        this.kind = kind;
        this.global = global;
    }

    /**
     * The next result.
     * @param self self
     * @param arg ignored
     * @return the result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object next(final Object self, final Object arg) {
        if (!(self instanceof HeadersIterator iterator)) {
            throw new ECMAException(Global.instance().newTypeError("not a Headers iterator"), null);
        }
        return iterator.next(arg);
    }

    @Override
    protected IteratorResult next(final Object arg) {
        if (index >= rows.size()) {
            return makeResult(Undefined.getUndefined(), Boolean.TRUE, global);
        }
        final String[] row = rows.get(index++);
        switch (kind) {
        case KEY:
            return makeResult(row[0], Boolean.FALSE, global);
        case VALUE:
            return makeResult(row[1], Boolean.FALSE, global);
        default:
            return makeResult(global.wrapAsObject(new Object[] { row[0], row[1] }), Boolean.FALSE, global);
        }
    }

    /** %HeadersIteratorPrototype% [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Headers Iterator";

    @Override
    public String getClassName() {
        return "Headers Iterator";
    }
}
