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

import java.lang.invoke.MethodHandle;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Undefined;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

import static org.monflabs.nashorn.internal.objects.NativeMap.convertKey;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

/**
 * This implements the ECMA6 Set object.
 */
@ScriptClass("Set")
public class NativeSet extends ScriptObject {

    // our set/map implementation
    private final LinkedMap map = new LinkedMap();

    // Invoker for the forEach callback
    private final static Object FOREACH_INVOKER_KEY = new Object();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * ES2015 23.2.2.2 get Set [ @@species ].
     *
     * The default species is the constructor itself; a subclass overrides it to
     * say what its derived operations should build.
     *
     * @param self self reference
     * @return the constructor it was read from
     */
    @Getter(where = Where.CONSTRUCTOR, name = "@@species", attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object species(final Object self) {
        return self;
    }

    private NativeSet(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ECMA6 23.1 Set constructor
     *
     * @param isNew  whether the new operator used
     * @param self self reference
     * @param arg optional iterable argument
     * @return a new Set object
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object arg){
        if (!isNew) {
            throw typeError("constructor.requires.new", "Set");
        }
        final Global global = Global.instance();
        final NativeSet set = new NativeSet(global.getSetPrototype(), $nasgenmap$);
        // 23.2.1.1 step 8: the values go in through the set's own "add"
        AbstractIterator.fillFrom(set, "add", arg, global, value -> new Object[] { value });
        return set;
    }

    /**
     * ECMA6 23.2.3.1 Set.prototype.add ( value )
     *
     * @param self the self reference
     * @param value the value to add
     * @return this Set object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object add(final Object self, final Object value) {
        getNativeSet(self).map.set(convertKey(value), null);
        return self;
    }

    /**
     * ECMA6 23.2.3.7 Set.prototype.has ( value )
     *
     * @param self the self reference
     * @param value the value
     * @return true if value is contained
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean has(final Object self, final Object value) {
        return getNativeSet(self).map.has(convertKey(value));
    }

    /**
     * ECMA6 23.2.3.2 Set.prototype.clear ( )
     *
     * @param self the self reference
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static void clear(final Object self) {
        getNativeSet(self).map.clear();
    }

    /**
     * ECMA6 23.2.3.4 Set.prototype.delete ( value )
     *
     * @param self the self reference
     * @param value the value
     * @return true if value was deleted
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean delete(final Object self, final Object value) {
        return getNativeSet(self).map.delete(convertKey(value));
    }

    /**
     * ECMA6 23.2.3.9 get Set.prototype.size
     *
     * @param self the self reference
     * @return the number of contained values
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static int size(final Object self) {
        return getNativeSet(self).map.size();
    }

    /**
     * ECMA6 23.2.3.5 Set.prototype.entries ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's entries
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     * ECMA6 23.2.3.10 Set.prototype.values ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.VALUE, Global.instance());
    }


    /**
     * ECMA6 23.2.3.6 Set.prototype.forEach ( callbackfn [ , thisArg ] )
     *
     * @param self the self reference
     * @param callbackFn the callback function
     * @param thisArg optional this object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static void forEach(final Object self, final Object callbackFn, final Object thisArg) {
        final NativeSet set = getNativeSet(self);
        if (!Bootstrap.isCallable(callbackFn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackFn));
        }
        // 23.1.3.5 step 5 calls the callback with what it was given, and a
        // callback that is not strict is entered with the global where that is
        // undefined - the coercion every other iteration helper makes
        final Object callbackThis = thisArg == ScriptRuntime.UNDEFINED && !Bootstrap.isStrictCallable(callbackFn)
                ? Context.getGlobal()
                : thisArg;
        final MethodHandle invoker = Global.instance().getDynamicInvoker(FOREACH_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));

        final LinkedMap.LinkedMapIterator iterator = set.getJavaMap().getIterator();
        for (;;) {
            final LinkedMap.Node node = iterator.next();
            if (node == null) {
                break;
            }

            try {
                final Object result = invoker.invokeExact(callbackFn, callbackThis, node.getKey(), node.getKey(), self);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    // ------------------------------------------------------------------------
    // ES2025 Set methods (24.2.4). Each takes an arbitrary Set-like argument,
    // read through GetSetRecord (its size / has / keys), and returns a new Set
    // or a boolean. The result Set is built with the %Set% intrinsic directly
    // (no @@species), and every value is normalised through convertKey
    // (SameValueZero) before it touches the backing map.
    // ------------------------------------------------------------------------

    private final static Object SET_HAS_INVOKER_KEY = new Object();
    private final static Object SET_KEYS_INVOKER_KEY = new Object();

    /** ES2025 24.2.1.2 Set Record: an argument's {@code size}, {@code has} and {@code keys}. */
    private static final class SetRecord {
        private final Object set;
        private final double size;
        private final Object has;
        private final Object keys;

        SetRecord(final Object set, final double size, final Object has, final Object keys) {
            this.set = set;
            this.size = size;
            this.has = has;
            this.keys = keys;
        }
    }

    /** ES2025 24.2.1.2 GetSetRecord(obj). */
    private static SetRecord getSetRecord(final Object obj) {
        if (JSType.isPrimitive(obj) || !(obj instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }
        final double numSize = JSType.toNumber(sobj.get("size"));
        if (Double.isNaN(numSize)) {
            throw typeError("not.a.number", "size");
        }
        // ES2025 ToIntegerOrInfinity, then the [[Size]] must be non-negative
        if (numSize < 0) {
            throw rangeError("invalid.set.size");
        }
        final double intSize = numSize == Double.POSITIVE_INFINITY ? numSize : Math.floor(numSize);
        final Object has = sobj.get("has");
        if (!Bootstrap.isCallable(has)) {
            throw typeError("not.a.function", "has");
        }
        final Object keys = sobj.get("keys");
        if (!Bootstrap.isCallable(keys)) {
            throw typeError("not.a.function", "keys");
        }
        return new SetRecord(sobj, intSize, has, keys);
    }

    /** ToBoolean(Call(otherRec.[[Has]], otherRec.[[Set]], element)). */
    private static boolean setHas(final SetRecord rec, final Object element, final Global global) {
        final MethodHandle invoker = global.getDynamicInvoker(SET_HAS_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class, Object.class));
        try {
            return JSType.toBoolean((Object) invoker.invokeExact(rec.has, rec.set, element));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Call otherRec.[[Keys]]() and drive the returned iterator, handing each
     * value (as produced) to {@code test}. Its {@code next} method is captured
     * once, as an iterator record does. If {@code test} returns false the
     * iteration stops early and the iterator's {@code return} is called
     * (IteratorClose); it also closes if {@code test} throws.
     */
    private static void setKeys(final SetRecord rec, final Global global, final java.util.function.Predicate<Object> test) {
        final MethodHandle callInvoker = global.getDynamicInvoker(SET_KEYS_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class));
        final MethodHandle doneInvoker = AbstractIterator.getDoneInvoker(global);
        final MethodHandle valueInvoker = AbstractIterator.getValueInvoker(global);
        try {
            final Object iterator = callInvoker.invokeExact(rec.keys, rec.set);
            if (!(iterator instanceof ScriptObject)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(iterator));
            }
            // capture next once (the iterator record's [[NextMethod]])
            final Object nextMethod = ((ScriptObject) iterator).get("next");
            if (!Bootstrap.isCallable(nextMethod)) {
                throw typeError("not.a.function", "next");
            }
            for (;;) {
                final Object result = callInvoker.invokeExact(nextMethod, iterator);
                if (!(result instanceof ScriptObject)) {
                    throw typeError("not.an.object", ScriptRuntime.safeToString(result));
                }
                if (JSType.toBoolean((Object) doneInvoker.invokeExact(result))) {
                    break;
                }
                final Object value = (Object) valueInvoker.invokeExact(result);
                final boolean cont;
                try {
                    cont = test.test(value);
                } catch (final RuntimeException e) {
                    AbstractIterator.closeIterator(iterator);
                    throw e;
                }
                if (!cont) {
                    AbstractIterator.closeIterator(iterator);
                    return;
                }
            }
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** A fresh, empty Set built with the %Set% intrinsic. */
    private static NativeSet newSet() {
        return new NativeSet(Global.instance().getSetPrototype(), $nasgenmap$);
    }

    /** A copy of {@code source} (same elements, same order). */
    private static NativeSet copyOf(final NativeSet source) {
        final NativeSet result = newSet();
        final LinkedMap.LinkedMapIterator it = source.map.getIterator();
        for (LinkedMap.Node node = it.next(); node != null; node = it.next()) {
            result.map.set(node.getKey(), null);
        }
        return result;
    }

    /**
     * ES2025 24.2.4.17 Set.prototype.union ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return a new Set of the elements in either
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object union(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        final NativeSet result = copyOf(o);
        setKeys(rec, global, value -> { result.map.set(convertKey(value), null); return true; });
        return result;
    }

    /**
     * ES2025 24.2.4.9 Set.prototype.intersection ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return a new Set of the elements in both
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object intersection(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        final NativeSet result = newSet();
        if (o.map.size() <= rec.size) {
            final LinkedMap.LinkedMapIterator it = o.map.getIterator();
            for (LinkedMap.Node node = it.next(); node != null; node = it.next()) {
                final Object e = node.getKey();
                if (setHas(rec, e, global) && o.map.has(e)) {
                    result.map.set(e, null);
                }
            }
        } else {
            setKeys(rec, global, value -> {
                final Object k = convertKey(value);
                if (o.map.has(k)) {
                    result.map.set(k, null);
                }
                return true;
            });
        }
        return result;
    }

    /**
     * ES2025 24.2.4.5 Set.prototype.difference ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return a new Set of the elements in this but not other
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object difference(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        final NativeSet result = copyOf(o);
        if (o.map.size() <= rec.size) {
            final LinkedMap.LinkedMapIterator it = o.map.getIterator();
            for (LinkedMap.Node node = it.next(); node != null; node = it.next()) {
                final Object e = node.getKey();
                if (setHas(rec, e, global)) {
                    result.map.delete(e);
                }
            }
        } else {
            setKeys(rec, global, value -> { result.map.delete(convertKey(value)); return true; });
        }
        return result;
    }

    /**
     * ES2025 24.2.4.20 Set.prototype.symmetricDifference ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return a new Set of the elements in exactly one of the two
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object symmetricDifference(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        final NativeSet result = copyOf(o);
        setKeys(rec, global, value -> {
            final Object k = convertKey(value);
            if (o.map.has(k)) {
                result.map.delete(k);
            } else {
                result.map.set(k, null);
            }
            return true;
        });
        return result;
    }

    /**
     * ES2025 24.2.4.10 Set.prototype.isSubsetOf ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return whether every element of this is in other
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean isSubsetOf(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        if (o.map.size() > rec.size) {
            return false;
        }
        final LinkedMap.LinkedMapIterator it = o.map.getIterator();
        for (LinkedMap.Node node = it.next(); node != null; node = it.next()) {
            if (!setHas(rec, node.getKey(), global)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ES2025 24.2.4.11 Set.prototype.isSupersetOf ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return whether every element of other is in this
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean isSupersetOf(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        if (o.map.size() < rec.size) {
            return false;
        }
        final boolean[] superset = { true };
        setKeys(rec, global, value -> {
            if (!o.map.has(convertKey(value))) {
                superset[0] = false;
                return false;
            }
            return true;
        });
        return superset[0];
    }

    /**
     * ES2025 24.2.4.8 Set.prototype.isDisjointFrom ( other ).
     *
     * @param self the self reference
     * @param other a Set-like object
     * @return whether the two share no element
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean isDisjointFrom(final Object self, final Object other) {
        final NativeSet o = getNativeSet(self);
        final Global global = Global.instance();
        final SetRecord rec = getSetRecord(other);
        if (o.map.size() <= rec.size) {
            final LinkedMap.LinkedMapIterator it = o.map.getIterator();
            for (LinkedMap.Node node = it.next(); node != null; node = it.next()) {
                if (setHas(rec, node.getKey(), global)) {
                    return false;
                }
            }
            return true;
        }
        final boolean[] disjoint = { true };
        setKeys(rec, global, value -> {
            if (o.map.has(convertKey(value))) {
                disjoint[0] = false;
                return false;
            }
            return true;
        });
        return disjoint[0];
    }

    @Override
    public String getClassName() {
        return "Set";
    }


    LinkedMap getJavaMap() {
        return map;
    }

    private static NativeSet getNativeSet(final Object self) {
        if (self instanceof NativeSet) {
            return (NativeSet) self;
        } else {
            throw typeError("not.a.set", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ES2015 23.2.3.12 Set.prototype [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Set";

}
