/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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

import java.util.Map;
import java.lang.invoke.MethodHandle;
import java.util.WeakHashMap;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.Undefined;

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.JSType.isPrimitive;
import org.monflabs.nashorn.internal.runtime.Symbol;

/**
 * This implements the ECMA6 WeakMap object.
 */
@ScriptClass("WeakMap")
public class NativeWeakMap extends ScriptObject {

    private final Map<Object, Object> jmap = new WeakHashMap<>();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeWeakMap(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ECMA6 23.3.1 The WeakMap Constructor
     *
     * @param isNew  whether the new operator used
     * @param self self reference
     * @param arg optional iterable argument
     * @return a new WeakMap object
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object arg) {
        if (!isNew) {
            throw typeError("constructor.requires.new", "WeakMap");
        }
        final Global global = Global.instance();
        final NativeWeakMap weakMap = new NativeWeakMap(global.getWeakMapPrototype(), $nasgenmap$);
        // 23.3.1.1 step 7: the entries go in through the map's own "set"
        AbstractIterator.fillFrom(weakMap, "set", arg, global, NativeMap::entryOf);
        return weakMap;
    }

    /**
     * ECMA6 23.3.3.5 WeakMap.prototype.set ( key , value )
     *
     * @param self the self reference
     * @param key the key
     * @param value the value
     * @return this WeakMap object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object set(final Object self, final Object key, final Object value) {
        final NativeWeakMap map = getMap(self);
        map.jmap.put(checkKey(key), value);
        return self;
    }

    /**
     * ECMA6 23.3.3.3 WeakMap.prototype.get ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return the associated value or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object get(final Object self, final Object key) {
        final NativeWeakMap map = getMap(self);
        if (!canBeHeldWeakly(key)) {
            return Undefined.getUndefined();
        }
        // 23.3.3.3 step 4: a key the map does not hold reads as undefined, which
        // the Java map answers for with a null it also uses for a stored one
        return map.jmap.getOrDefault(key, Undefined.getUndefined());
    }

    private static final Object GETORINSERT_INVOKER_KEY = new Object();

    /**
     * ES2026 24.3.3.5 WeakMap.prototype.getOrInsert(key, value): the value
     * already stored under {@code key}, or - if none - {@code value}, inserted.
     *
     * @param self  the self reference
     * @param key   the key (must be able to be held weakly)
     * @param value the value to insert if the key is absent
     * @return the existing or newly inserted value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object getOrInsert(final Object self, final Object key, final Object value) {
        final NativeWeakMap map = getMap(self);
        checkKey(key);
        if (map.jmap.containsKey(key)) {
            return map.jmap.get(key);
        }
        map.jmap.put(key, value);
        return value;
    }

    /**
     * ES2026 24.3.3.6 WeakMap.prototype.getOrInsertComputed(key, callbackfn):
     * the value already stored under {@code key}, or - if none - the result of
     * calling {@code callbackfn} with the key, which is then stored.
     *
     * @param self       the self reference
     * @param key        the key (must be able to be held weakly)
     * @param callbackfn computes the value to insert when the key is absent
     * @return the existing or computed value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object getOrInsertComputed(final Object self, final Object key, final Object callbackfn) {
        final NativeWeakMap map = getMap(self);
        if (!Bootstrap.isCallable(callbackfn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackfn));
        }
        checkKey(key);
        if (map.jmap.containsKey(key)) {
            return map.jmap.get(key);
        }
        final MethodHandle invoker = Global.instance().getDynamicInvoker(GETORINSERT_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class, Object.class));
        final Object value;
        try {
            value = invoker.invokeExact(callbackfn, (Object) ScriptRuntime.UNDEFINED, key);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
        map.jmap.put(key, value);
        return value;
    }

    /**
     * ECMA6 23.3.3.2 WeakMap.prototype.delete ( key )
     *
     * @param self the self reference
     * @param key the key to delete
     * @return true if the key was deleted
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean delete(final Object self, final Object key) {
        final Map<Object, Object> map = getMap(self).jmap;
        if (!canBeHeldWeakly(key)) {
            return false;
        }
        final boolean returnValue = map.containsKey(key);
        map.remove(key);
        return returnValue;
    }

    /**
     * ECMA6 23.3.3.4 WeakMap.prototype.has ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return true if key is contained
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean has(final Object self, final Object key) {
        final NativeWeakMap map = getMap(self);
        return canBeHeldWeakly(key) && map.jmap.containsKey(key);
    }

    @Override
    public String getClassName() {
        return "WeakMap";
    }

    /**
     * Make sure {@code key} is not a JavaScript primitive value.
     *
     * @param key a key object
     * @return the valid key
     */
    static Object checkKey(final Object key) {
        if (!canBeHeldWeakly(key)) {
            throw typeError("invalid.weak.key", ScriptRuntime.safeToString(key));
        }
        return key;
    }

    /**
     * ES2023 CanBeHeldWeakly: a value a weak collection may hold as a key or
     * target - any object, or a Symbol that is not registered (not made by
     * {@code Symbol.for}). A registered symbol, and every other primitive, may not.
     *
     * @param key the candidate key or target
     * @return true if it can be held weakly
     */
    static boolean canBeHeldWeakly(final Object key) {
        if (!isPrimitive(key)) {
            return true;
        }
        return key instanceof Symbol symbol && !NativeSymbol.isRegistered(symbol);
    }



    private static NativeWeakMap getMap(final Object self) {
        if (self instanceof NativeWeakMap) {
            return (NativeWeakMap)self;
        } else {
            throw typeError("not.a.weak.map", ScriptRuntime.safeToString(self));
        }
    }


    /**
     * ES2015 23.3.3.6 WeakMap.prototype [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "WeakMap";

}
