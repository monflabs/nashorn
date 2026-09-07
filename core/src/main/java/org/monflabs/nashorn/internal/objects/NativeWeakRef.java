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

import java.lang.ref.WeakReference;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Undefined;

/**
 * ECMAScript 2021 26.1 WeakRef: a weak reference to an object whose referent may
 * be reclaimed by the garbage collector once nothing else holds it strongly.
 */
@ScriptClass("WeakRef")
public final class NativeWeakRef extends ScriptObject {

    /** The weakly-held referent. */
    private final WeakReference<Object> ref;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeWeakRef(final ScriptObject proto, final PropertyMap map, final Object target) {
        super(proto, map);
        this.ref = new WeakReference<>(target);
    }

    /** ES2021 26.1.3.3 WeakRef.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "WeakRef";

    /**
     * ES2021 26.1.1.1 WeakRef(target).
     *
     * @param isNew  whether the new operator was used
     * @param self   self reference
     * @param target the object to hold weakly
     * @return a new WeakRef
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean isNew, final Object self, final Object target) {
        if (!isNew) {
            throw typeError("constructor.requires.new", "WeakRef");
        }
        // 26.1.1.1 step 3: the target must be an Object
        // ES2023: a WeakRef may hold any object, or a non-registered Symbol
        if (!NativeWeakMap.canBeHeldWeakly(target)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(target));
        }
        final Global global = Global.instance();
        return new NativeWeakRef(global.getWeakRefPrototype(), $nasgenmap$, target);
    }

    /**
     * ES2021 26.1.3.2 WeakRef.prototype.deref().
     *
     * @param self self reference
     * @return the referent, or undefined once it has been reclaimed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object deref(final Object self) {
        if (!(self instanceof NativeWeakRef weakRef)) {
            throw typeError("not.a.weakref", ScriptRuntime.safeToString(self));
        }
        final Object target = weakRef.ref.get();
        return target == null ? Undefined.getUndefined() : target;
    }
}
