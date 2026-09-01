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

package org.monflabs.nashorn.api.scripting;

import java.lang.invoke.MethodHandle;
import jdk.dynalink.beans.StaticClass;
import jdk.dynalink.linker.LinkerServices;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Utilities that are to be called from script code.
 *
 * @since 1.8u40
 */
public final class ScriptUtils {
    private ScriptUtils() {}

    /**
     * Returns AST as JSON compatible string. This is used to
     * implement "parse" function in resources/parse.js script.
     *
     * @param code code to be parsed
     * @param name name of the code source (used for location)
     * @param includeLoc tells whether to include location information for nodes or not
     * @return JSON string representation of AST of the supplied code
     */
    public static String parse(final String code, final String name, final boolean includeLoc) {
        return ScriptRuntime.parse(code, name, includeLoc);
    }

    /**
     * Method which converts javascript types to java types for the
     * String.format method (jrunscript function sprintf).
     *
     * @param format a format string
     * @param args arguments referenced by the format specifiers in format
     * @return a formatted string
     */
    public static String format(final String format, final Object[] args) {
        return Formatter.format(format, args);
    }

    /**
     * Create a wrapper function that calls {@code func} synchronized on {@code sync} or, if that is undefined,
     * {@code self}. Used to implement "sync" function in resources/mozilla_compat.js.
     *
     * @param func the function to wrap
     * @param sync the object to synchronize on
     * @return a synchronizing wrapper function
     * @throws IllegalArgumentException if func does not represent a script function
     */
    public static Object makeSynchronizedFunction(final Object func, final Object sync) {
        final Object unwrapped = unwrap(func);
        if (unwrapped instanceof ScriptFunction) {
            return ((ScriptFunction)unwrapped).createSynchronized(unwrap(sync));
        }

        throw new IllegalArgumentException();
    }

    /**
     * Make a script object mirror on given object if needed.
     *
     * @param obj object to be wrapped
     * @return wrapped object
     * @throws IllegalArgumentException if obj cannot be wrapped
     */
    public static ScriptObjectMirror wrap(final Object obj) {
        if (obj instanceof ScriptObjectMirror) {
            return (ScriptObjectMirror)obj;
        }

        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            return (ScriptObjectMirror) ScriptObjectMirror.wrap(sobj, Context.getGlobal());
        }

        throw new IllegalArgumentException();
    }

    /**
     * Unwrap a script object mirror if needed.
     *
     * @param obj object to be unwrapped
     * @return unwrapped object
     */
    public static Object unwrap(final Object obj) {
        if (obj instanceof ScriptObjectMirror) {
            return ScriptObjectMirror.unwrap(obj, Context.getGlobal());
        }

        return obj;
    }

    /**
     * Wrap an array of object to script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @return wrapped array
     */
    public static Object[] wrapArray(final Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        return ScriptObjectMirror.wrapArray(args, Context.getGlobal());
    }

    /**
     * Unwrap an array of script object mirrors if needed.
     *
     * @param args array to be unwrapped
     * @return unwrapped array
     */
    public static Object[] unwrapArray(final Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        return ScriptObjectMirror.unwrapArray(args, Context.getGlobal());
    }

    /**
     * Convert the given object to the given type, the way the language would:
     * a string, a boolean, or an object with a {@code valueOf} converts to a
     * number as ECMAScript's ToNumber says, {@code undefined} to NaN, and
     * {@code null} - which reaches Java as {@code null} - to 0 for a numeric
     * primitive, {@code false} for {@code boolean}, and {@code null} for any
     * other type.
     *
     * @param obj object to be converted
     * @param type destination type to convert to. type is either a Class
     * or nashorn representation of a Java type returned by Java.type() call in script.
     * @return converted object
     */
    /** What null converts to for each primitive: ToNumber(null) is 0, ToBoolean(null) is false. */
    private static final java.util.Map<Class<?>, Object> NULL_AS_PRIMITIVE = java.util.Map.of(
            double.class, 0.0, float.class, 0.0f, long.class, 0L, int.class, 0,
            short.class, (short)0, byte.class, (byte)0, char.class, (char)0, boolean.class, false);

    public static Object convert(final Object obj, final Object type) {
        final Class<?> clazz;
        if (type instanceof Class) {
            clazz = (Class<?>)type;
        } else if (type instanceof StaticClass) {
            clazz = ((StaticClass)type).getRepresentedClass();
        } else {
            throw new IllegalArgumentException("type expected");
        }

        if (obj == null) {
            return clazz.isPrimitive() ? NULL_AS_PRIMITIVE.get(clazz) : null;
        }

        final LinkerServices linker = Bootstrap.getLinkerServices();
        final Object objToConvert = unwrap(obj);
        final MethodHandle converter = linker.getTypeConverter(objToConvert.getClass(), clazz);
        if (converter == null) {
            // no supported conversion!
            throw new UnsupportedOperationException("conversion not supported");
        }

        try {
            return converter.invoke(objToConvert);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // -- The language's abstract operations, for Java code a script calls --------------------
    //
    // A function or object implemented in Java - a JSObject among a script library's globals,
    // a method put on a prototype from initialize - receives what a script passes: primitives
    // as themselves (a script string possibly as a CharSequence that is not a String), null,
    // undefined, and objects as ScriptObjectMirrors. These are the conversions and tests the
    // language applies to such values, as the engine does them. A mirror converts through
    // its own realm, so a conversion needs no realm bound - except to *make a script error*
    // (a TypeError for a symbol, for null where an object is required) or a wrapper object,
    // which belong to a realm: those say so when there is none, and are meant to be called
    // from where a script called you, where there always is one.

    /**
     * ECMAScript's {@code typeof}: {@code "undefined"}, {@code "object"} (for null
     * too), {@code "boolean"}, {@code "number"}, {@code "string"}, {@code "symbol"}
     * or {@code "function"}.
     *
     * @param value a value
     * @return its type name
     * @since 2017.0.0
     */
    public static String typeOf(final Object value) {
        return JSType.of(value).typeName();
    }

    /**
     * The value {@code undefined}, for a Java function that wants to return it -
     * a Java {@code null} is the script's {@code null}.
     *
     * @return undefined
     * @since 2017.0.0
     */
    public static Object undefined() {
        return ScriptRuntime.UNDEFINED;
    }

    /**
     * Whether a value is {@code undefined}.
     *
     * @param value a value
     * @return true for undefined, false for anything else - null included
     * @since 2017.0.0
     */
    public static boolean isUndefined(final Object value) {
        return value == ScriptRuntime.UNDEFINED;
    }

    /**
     * Whether a value is {@code null} or {@code undefined}.
     *
     * @param value a value
     * @return true for either
     * @since 2017.0.0
     */
    public static boolean isNullOrUndefined(final Object value) {
        return JSType.nullOrUndefined(value);
    }

    /**
     * Whether a value is a script string. A string a script built by
     * concatenation may reach Java as a {@link CharSequence} that is not a
     * {@link String}; this answers for both, and {@link #toString(Object)}
     * makes a String of either.
     *
     * @param value a value
     * @return true for a string
     * @since 2017.0.0
     */
    public static boolean isString(final Object value) {
        return JSType.isString(value);
    }

    /**
     * Whether a value is a script number: an {@link Integer} or a {@link Double},
     * the two the engine represents numbers with. A Java {@code Long} or
     * {@code BigDecimal} is a Java object to a script, not a number - convert it
     * with {@link #toNumber(Object)} if a number is what you mean.
     *
     * @param value a value
     * @return true for a number
     * @since 2017.0.0
     */
    public static boolean isNumber(final Object value) {
        return JSType.isNumber(value);
    }

    /**
     * Whether a value is a primitive: undefined, null, a boolean, a number, a
     * string or a symbol - anything but an object or a function.
     *
     * @param value a value
     * @return true for a primitive
     * @since 2017.0.0
     */
    public static boolean isPrimitive(final Object value) {
        return JSType.isPrimitive(value);
    }

    /**
     * Whether a value can be called: a script function, a {@link JSObject}
     * that says it is a function, a Java method, a class, a functional
     * interface instance.
     *
     * @param value a value
     * @return true if calling it makes sense
     * @since 2017.0.0
     */
    public static boolean isCallable(final Object value) {
        return Bootstrap.isCallable(value);
    }

    /**
     * ECMAScript's ToNumber: a number as itself, a string as a numeric literal
     * ({@code "2"} is 2, {@code " 0x10 "} is 16, {@code "abc"} is NaN),
     * {@code true} and {@code false} as 1 and 0, {@code null} as 0,
     * {@code undefined} as NaN, and an object through its {@code valueOf} or
     * {@code toString}.
     *
     * @param value a value
     * @return the number
     * @throws NashornException with a TypeError for a symbol, or an object that will not convert -
     *         made in the current realm, so from where a script called you
     * @since 2017.0.0
     */
    public static double toNumber(final Object value) {
        return JSType.toNumber(value);
    }

    /**
     * ECMAScript's ToInt32: {@link #toNumber(Object)}, then modulo 2<sup>32</sup>
     * into a signed 32-bit integer - what the bitwise operators see; NaN and the
     * infinities are 0.
     *
     * @param value a value
     * @return the integer
     * @since 2017.0.0
     */
    public static int toInt32(final Object value) {
        return JSType.toInt32(value);
    }

    /**
     * ECMAScript's ToUint32: {@link #toNumber(Object)}, then modulo 2<sup>32</sup>
     * into an unsigned 32-bit integer, as a long - an array index or length.
     *
     * @param value a value
     * @return the integer, 0 to 2<sup>32</sup>-1
     * @since 2017.0.0
     */
    public static long toUint32(final Object value) {
        return JSType.toUint32(value);
    }

    /**
     * ECMAScript's ToUint16: {@link #toNumber(Object)}, then modulo 2<sup>16</sup>
     * - what {@code String.fromCharCode} does to its argument.
     *
     * @param value a value
     * @return the integer, 0 to 65535
     * @since 2017.0.0
     */
    public static int toUint16(final Object value) {
        return JSType.toUint16(value);
    }

    /**
     * {@link #toNumber(Object)} truncated toward zero into a Java long, saturating
     * at the long's range; NaN is 0. The integer part of a number, as
     * {@code Math.trunc} sees it, for Java code that wants a long.
     *
     * @param value a value
     * @return the long
     * @since 2017.0.0
     */
    public static long toLong(final Object value) {
        return JSType.toLong(value);
    }

    /**
     * ECMAScript's ToBoolean, the truthiness test: false for {@code false},
     * 0, NaN, the empty string, {@code null} and {@code undefined}; true for
     * everything else, an empty object or array included.
     *
     * @param value a value
     * @return the boolean
     * @since 2017.0.0
     */
    public static boolean toBoolean(final Object value) {
        return JSType.toBoolean(value);
    }

    /**
     * ECMAScript's ToString, as the language does it: {@code "undefined"},
     * {@code "null"}, {@code "true"}, a number the way a script prints it
     * (1, not 1.0; 1e+21), an object through its {@code toString} or
     * {@code valueOf}. Also the way to turn a script string that reached
     * Java as a {@link CharSequence} into a {@link String}.
     *
     * @param value a value
     * @return the string
     * @throws NashornException with a TypeError for a symbol - made in the current realm, so from
     *         where a script called you
     * @since 2017.0.0
     */
    public static String toString(final Object value) {
        return JSType.toString(value);
    }

    /**
     * ECMAScript's ToPrimitive with no hint: a primitive as itself, an object
     * through {@code valueOf} then {@code toString} - {@code Date} preferring
     * {@code toString}, as the language says.
     *
     * @param value a value
     * @return a primitive
     * @throws NashornException with a TypeError if the object will not convert
     * @since 2017.0.0
     */
    public static Object toPrimitive(final Object value) {
        return JSType.toPrimitive(value);
    }

    /**
     * ECMAScript's ToPrimitive with a hint: {@code Number.class} tries
     * {@code valueOf} first, {@code String.class} tries {@code toString} first.
     *
     * @param value a value
     * @param hint {@code Number.class} or {@code String.class}
     * @return a primitive
     * @throws NashornException with a TypeError if the object will not convert
     * @since 2017.0.0
     */
    public static Object toPrimitive(final Object value, final Class<?> hint) {
        return JSType.toPrimitive(value, hint);
    }

    /**
     * ECMAScript's ToObject: an object as itself, a primitive as its wrapper
     * object - a String, a Number, a Boolean, a Symbol - so that a method
     * written for {@code this} can treat a primitive receiver like an object.
     * The wrapper belongs to the current realm: call this from where a script
     * called you.
     *
     * @param value a value
     * @return the object, as a mirror
     * @throws NashornException with a TypeError for null or undefined
     * @since 2017.0.0
     */
    public static JSObject toObject(final Object value) {
        if (value instanceof JSObject) {
            return (JSObject)value;
        }
        realm();
        final Object object = JSType.toScriptObject(unwrap(value));
        return object instanceof JSObject ? (JSObject)object : wrap(object);
    }

    /**
     * ECMAScript's strict equality, {@code ===}: same type and same value, NaN
     * equal to nothing, +0 equal to -0, objects by identity - two mirrors of
     * one object are equal.
     *
     * @param x a value
     * @param y a value
     * @return whether they are strictly equal
     * @since 2017.0.0
     */
    public static boolean strictEquals(final Object x, final Object y) {
        return x instanceof ScriptObjectMirror && y instanceof ScriptObjectMirror ? x.equals(y) : ScriptRuntime.EQ_STRICT(x, y);
    }

    /**
     * ECMAScript's loose equality, {@code ==}: {@link #strictEquals} after the
     * language's coercions - {@code "2" == 2}, {@code null == undefined}, an
     * object against a primitive through ToPrimitive.
     *
     * @param x a value
     * @param y a value
     * @return whether they are loosely equal
     * @since 2017.0.0
     */
    public static boolean looseEquals(final Object x, final Object y) {
        if (x instanceof ScriptObjectMirror && y instanceof ScriptObjectMirror) {
            return x.equals(y);
        }
        // an object against a number, string or symbol compares through ToPrimitive (7.2.12 steps 10-11);
        // the runtime does that for its own objects, and this does it for a mirror or any other JSObject
        if (x instanceof JSObject && isComparablePrimitive(y)) {
            return ScriptRuntime.EQ(JSType.toPrimitive(x), y);
        }
        if (y instanceof JSObject && isComparablePrimitive(x)) {
            return ScriptRuntime.EQ(x, JSType.toPrimitive(y));
        }
        return ScriptRuntime.EQ(x, y);
    }

    private static boolean isComparablePrimitive(final Object value) {
        return JSType.isPrimitive(value) && !JSType.nullOrUndefined(value);
    }

    /** The current realm, or a plain refusal when there is none - which an NPE deep inside would not say. */
    private static Global realm() {
        final Global global = Context.getGlobal();
        if (global == null) {
            throw new IllegalStateException("no script realm is bound on this thread: call this from where a script called you");
        }
        return global;
    }

    /**
     * ECMAScript's SameValue, what {@code Object.is} answers: strict equality
     * except that NaN is the same as NaN and +0 is not the same as -0.
     *
     * @param x a value
     * @param y a value
     * @return whether they are the same value
     * @since 2017.0.0
     */
    public static boolean sameValue(final Object x, final Object y) {
        return x instanceof ScriptObjectMirror && y instanceof ScriptObjectMirror ? x.equals(y) : ScriptRuntime.sameValue(x, y);
    }

    /**
     * ECMAScript's SameValueZero, what {@code Array.prototype.includes},
     * {@code Map} and {@code Set} use: {@link #sameValue} with +0 and -0 the same.
     *
     * @param x a value
     * @param y a value
     * @return whether they are the same value, zeros aside
     * @since 2017.0.0
     */
    public static boolean sameValueZero(final Object x, final Object y) {
        return x instanceof ScriptObjectMirror && y instanceof ScriptObjectMirror ? x.equals(y) : ScriptRuntime.sameValueZero(x, y);
    }

    /**
     * ECMAScript's RequireObjectCoercible, the check at the head of most
     * built-in methods: a TypeError for {@code null} and {@code undefined},
     * the value itself for anything else.
     *
     * @param value a value, typically {@code this}
     * @return the value
     * @throws NashornException with a TypeError for null or undefined, made in the current realm
     * @throws IllegalStateException for null or undefined with no realm bound on the thread
     * @since 2017.0.0
     */
    public static Object requireObjectCoercible(final Object value) {
        if (JSType.nullOrUndefined(value)) {
            realm();
        }
        return ScriptRuntime.REQUIRE_OBJECT_COERCIBLE(value);
    }

    /**
     * A script {@code TypeError} with this message, for Java code to throw:
     * {@code throw ScriptUtils.typeError("radius must be a number")} reaches
     * the script as an ordinary TypeError it can catch. The error object
     * belongs to the current realm: call this from where a script called you.
     *
     * @param message the message
     * @return the exception to throw
     * @since 2017.0.0
     */
    public static NashornException typeError(final String message) {
        return new ECMAException(realm().newTypeError(message), null);
    }

    /**
     * A plain script {@code Error} with this message, for Java code to throw;
     * see {@link #typeError(String)}.
     *
     * @param message the message
     * @return the exception to throw
     * @since 2017.0.0
     */
    public static NashornException error(final String message) {
        return new ECMAException(realm().newError(message), null);
    }

    /**
     * A script {@code RangeError} with this message, for Java code to throw;
     * see {@link #typeError(String)}.
     *
     * @param message the message
     * @return the exception to throw
     * @since 2017.0.0
     */
    public static NashornException rangeError(final String message) {
        return new ECMAException(realm().newRangeError(message), null);
    }
}
