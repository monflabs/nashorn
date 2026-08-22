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

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;

import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Symbol;

/**
 * ECMAScript 2015 26.1, the Reflect namespace.
 *
 * These are the object internal methods, exposed as ordinary functions and
 * reporting failure by returning false where the Object equivalents throw. They
 * are also the shape a Proxy's traps have to take, which is why they come first.
 */
@ScriptClass("Reflect")
public final class NativeReflect extends ScriptObject {
    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeReflect() {
        // not to be instantiated
        throw new UnsupportedOperationException();
    }

    /**
     * ECMAScript 2015 26.1.1 Reflect.apply(target, thisArgument, argumentsList)
     *
     * @param self self reference
     * @param target the function to call
     * @param thisArg its this value
     * @param args an array-like of arguments
     * @return the call's result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object apply(final Object self, final Object target, final Object thisArg, final Object args) {
        if (target instanceof ScriptFunction function) {
            return ScriptRuntime.apply(function, thisArg, toArguments(args));
        }
        // a callable proxy is callable without being a ScriptFunction
        if (target instanceof ScriptObject sobj && sobj.isProxyOverCallable()) {
            return ScriptRuntime.call(target, thisArg, toArguments(args));
        }
        throw typeError("not.a.function", ScriptRuntime.safeToString(target));
    }

    /**
     * ECMAScript 2015 26.1.2 Reflect.construct(target, argumentsList[, newTarget])
     *
     * The newTarget argument is accepted but not honoured; Nashorn allocates the
     * object from the constructor being called, so a different prototype cannot
     * be substituted.
     *
     * @param self self reference
     * @param args the target, the argument list, and optionally newTarget
     * @return the newly constructed object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object construct(final Object self, final Object... args) {
        final Object target = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        if (!isConstructor(target)) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(target));
        }
        if (args.length > 2 && !isConstructor(args[2])) {
            // newTarget must itself be a constructor. test262's isConstructor
            // harness is built entirely on this check, so getting it wrong marks
            // every builtin as constructible.
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(args[2]));
        }
        final Object list = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        if (!(target instanceof ScriptFunction constructor)) {
            // a proxy, whose construct trap is reached by calling it with new
            return ScriptRuntime.newInstance(target, toArguments(list));
        }
        final Object given = args.length > 2 ? args[2] : constructor;
        final ScriptFunction newTarget = given instanceof ScriptFunction function ? function : constructor;

        // ES2015 26.1.2 builds the object for newTarget, which is what decides
        // its prototype and what new.target reads as inside it
        try {
            return constructor.construct(newTarget, toArguments(list));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * ECMAScript 2015 26.1.3 Reflect.defineProperty(target, propertyKey, attributes)
     *
     * @param self self reference
     * @param target the object to define on
     * @param key the property key
     * @param attributes the descriptor
     * @return true if the property was defined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static boolean defineProperty(final Object self, final Object target, final Object key,
            final Object attributes) {
        return object(target, "defineProperty").defineOwnProperty(propertyKey(key), attributes, false);
    }

    /**
     * ECMAScript 2015 26.1.4 Reflect.deleteProperty(target, propertyKey)
     *
     * @param self self reference
     * @param target the object to delete from
     * @param key the property key
     * @return true if the property is gone
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static boolean deleteProperty(final Object self, final Object target, final Object key) {
        return object(target, "deleteProperty").delete(propertyKey(key), false);
    }

    /**
     * ECMAScript 2015 26.1.5 Reflect.get(target, propertyKey[, receiver])
     *
     * @param self self reference
     * @param target the object to read from
     * @param key the property key
     * @param receiver ignored; Nashorn's property reads have no separate receiver
     * @return the value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object get(final Object self, final Object target, final Object key, final Object receiver) {
        return object(target, "get").get(propertyKey(key));
    }

    /**
     * ECMAScript 2015 26.1.6 Reflect.getOwnPropertyDescriptor(target, propertyKey)
     *
     * @param self self reference
     * @param target the object to query
     * @param key the property key
     * @return the descriptor, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object getOwnPropertyDescriptor(final Object self, final Object target, final Object key) {
        return NativeObject.getOwnPropertyDescriptor(self, object(target, "getOwnPropertyDescriptor"), key);
    }

    /**
     * ECMAScript 2015 26.1.7 Reflect.getPrototypeOf(target)
     *
     * @param self self reference
     * @param target the object to query
     * @return its prototype, or null
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getPrototypeOf(final Object self, final Object target) {
        final ScriptObject proto = object(target, "getPrototypeOf").getPrototypeOf();
        return proto == null ? null : proto;
    }

    /**
     * ECMAScript 2015 26.1.8 Reflect.has(target, propertyKey)
     *
     * @param self self reference
     * @param target the object to query
     * @param key the property key
     * @return true if the property is there, own or inherited
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static boolean has(final Object self, final Object target, final Object key) {
        return object(target, "has").has(propertyKey(key));
    }

    /**
     * ECMAScript 2015 26.1.9 Reflect.isExtensible(target)
     *
     * @param self self reference
     * @param target the object to query
     * @return true if properties may still be added
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isExtensible(final Object self, final Object target) {
        return object(target, "isExtensible").isExtensible();
    }

    /**
     * ECMAScript 2015 26.1.10 Reflect.ownKeys(target)
     *
     * @param self self reference
     * @param target the object to query
     * @return an array of its own keys, strings first and then symbols
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object ownKeys(final Object self, final Object target) {
        final ScriptObject sobj = object(target, "ownKeys");
        final Object[] strings = sobj.getOwnKeys(true);
        final Symbol[] symbols = sobj.getOwnSymbols(true);
        final Object[] keys = new Object[strings.length + symbols.length];
        System.arraycopy(strings, 0, keys, 0, strings.length);
        System.arraycopy(symbols, 0, keys, strings.length, symbols.length);
        return new NativeArray(keys);
    }

    /**
     * ECMAScript 2015 26.1.11 Reflect.preventExtensions(target)
     *
     * @param self self reference
     * @param target the object to seal off
     * @return true
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean preventExtensions(final Object self, final Object target) {
        object(target, "preventExtensions").preventExtensions();
        return true;
    }

    /**
     * ECMAScript 2015 26.1.12 Reflect.set(target, propertyKey, V[, receiver])
     *
     * @param self self reference
     * @param args the target, the key, the value, and optionally a receiver
     * @return true if the assignment went through
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static boolean set(final Object self, final Object... args) {
        final Object target = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object key = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object value = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;
        final ScriptObject sobj = object(target, "set");
        try {
            sobj.set(propertyKey(key), value, org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT);
            return true;
        } catch (final RuntimeException e) {
            // Reflect reports failure by returning false where a strict
            // assignment would throw
            return false;
        }
    }

    /**
     * ECMAScript 2015 26.1.14 Reflect.setPrototypeOf(target, proto)
     *
     * @param self self reference
     * @param target the object to reparent
     * @param proto the new prototype, an object or null
     * @return true if it was set
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static boolean setPrototypeOf(final Object self, final Object target, final Object proto) {
        final ScriptObject sobj = object(target, "setPrototypeOf");
        if (proto != null && proto != ScriptRuntime.UNDEFINED && !(proto instanceof ScriptObject)) {
            throw typeError("proto.not.an.object", ScriptRuntime.safeToString(proto));
        }
        try {
            sobj.setPrototypeOf(proto == ScriptRuntime.UNDEFINED ? null : proto);
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Whether a value can be used with new, which a builtin function cannot. */
    private static boolean isConstructor(final Object value) {
        if (value instanceof ScriptFunction function) {
            return function.isConstructor();
        }
        // a proxy over a constructor is one, and is not a ScriptFunction
        return value instanceof ScriptObject sobj && sobj.isProxyOverCallable()
                && sobj.isProxyOverConstructor();
    }

    /** Every Reflect function rejects a non-object target outright. */
    private static ScriptObject object(final Object target, final String method) {
        if (target instanceof ScriptObject sobj) {
            return sobj;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(target));
    }

    /** A key is a symbol as-is, and a string otherwise. */
    private static Object propertyKey(final Object key) {
        return key instanceof Symbol ? key : JSType.toPropertyKey(key);
    }

    /** Coerces Reflect.apply's and Reflect.construct's array-like argument list. */
    private static Object[] toArguments(final Object list) {
        if (list == null || list == ScriptRuntime.UNDEFINED) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(list));
        }
        return ScriptRuntime.SPREAD_TO_ARGUMENTS(NativeArray.from(null, list));
    }
}
