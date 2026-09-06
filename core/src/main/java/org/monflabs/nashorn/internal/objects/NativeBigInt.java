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

import static org.monflabs.nashorn.internal.lookup.Lookup.MH;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Undefined;
import org.monflabs.nashorn.internal.runtime.linker.PrimitiveLookup;

/**
 * ES2020 BigInt (20.2). The primitive value is a {@link BigInteger}; this class
 * is its object wrapper and the home of the {@code BigInt} constructor,
 * {@code BigInt.asIntN}/{@code asUintN} and the prototype methods.
 */
@ScriptClass("BigInt")
public final class NativeBigInt extends ScriptObject {

    private final BigInteger value;

    /** Wrap a primitive BigInt for a property access on it. */
    static final MethodHandle WRAPFILTER = findOwnMH("wrapFilter", MH.type(NativeBigInt.class, Object.class));
    private static final MethodHandle PROTOFILTER = findOwnMH("protoFilter", MH.type(Object.class, Object.class));

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    NativeBigInt(final BigInteger value, final Global global) {
        this(value, global.getBigIntPrototype(), $nasgenmap$);
    }

    private NativeBigInt(final BigInteger value, final ScriptObject prototype, final PropertyMap map) {
        super(prototype, map);
        this.value = value;
    }

    @Override
    public String getClassName() {
        return "BigInt";
    }

    private static BigInteger getBigIntValue(final Object self) {
        if (self instanceof BigInteger) {
            return (BigInteger) self;
        } else if (self instanceof NativeBigInt) {
            return ((NativeBigInt) self).value;
        }
        throw typeError("not.a.bigint", ScriptRuntime.safeToString(self));
    }

    /**
     * ES2020 20.2.1.1 BigInt ( value ) - callable, never a constructor.
     *
     * @param newObj whether invoked with new
     * @param self   self reference
     * @param args   the value to convert
     * @return the BigInt primitive
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        if (newObj) {
            throw typeError("bigint.as.constructor");
        }
        final Object value = args.length > 0 ? args[0] : Undefined.getUndefined();
        final Object prim = JSType.toPrimitive(value, Number.class);
        if (prim instanceof Number && !(prim instanceof BigInteger)) {
            return numberToBigInt(((Number) prim).doubleValue());
        }
        return toBigInt(prim);
    }

    /**
     * ES2020 20.2.1.1.1 NumberToBigInt: an integral Number becomes the BigInt of
     * the same mathematical value; a non-integral one is a RangeError.
     */
    private static BigInteger numberToBigInt(final double number) {
        if (Double.isNaN(number) || Double.isInfinite(number) || Math.floor(number) != number) {
            throw rangeError("bigint.from.non.integer", JSType.toString(number));
        }
        return new java.math.BigDecimal(number).toBigIntegerExact();
    }

    /**
     * ES2020 7.1.13 ToBigInt.
     *
     * @param value a value already reduced to a primitive
     * @return the BigInt it converts to
     */
    public static BigInteger toBigInt(final Object value) {
        final Object prim = JSType.toPrimitive(value, Number.class);
        if (prim instanceof BigInteger bi) {
            return bi;
        }
        if (prim instanceof Boolean b) {
            return b ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (JSType.isString(prim)) {
            return stringToBigInt(prim.toString());
        }
        if (prim instanceof Number) {
            throw typeError("cant.convert.to.bigint", "number");
        }
        if (prim instanceof org.monflabs.nashorn.internal.runtime.Symbol) {
            throw typeError("cant.convert.to.bigint", "symbol");
        }
        // undefined and null
        throw typeError("cant.convert.to.bigint", ScriptRuntime.safeToString(prim));
    }

    /** ES2020 StringToBigInt: an empty or whitespace-only string is 0n, a bad one a SyntaxError. */
    private static BigInteger stringToBigInt(final String str) {
        final String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return BigInteger.ZERO;
        }
        try {
            if (trimmed.length() > 2 && trimmed.charAt(0) == '0') {
                final char c = trimmed.charAt(1);
                if (c == 'x' || c == 'X') { return new BigInteger(trimmed.substring(2), 16); }
                if (c == 'o' || c == 'O') { return new BigInteger(trimmed.substring(2), 8); }
                if (c == 'b' || c == 'B') { return new BigInteger(trimmed.substring(2), 2); }
            }
            return new BigInteger(trimmed, 10);
        } catch (final NumberFormatException e) {
            throw syntaxError("cant.convert.to.bigint", "\"" + str + "\"");
        }
    }

    /**
     * ES2020 20.2.2.1 BigInt.asIntN ( bits, bigint ) - the value modulo 2**bits,
     * interpreted as a signed integer.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object asIntN(final Object self, final Object bits, final Object bigint) {
        final int n = toIndex(bits);
        final BigInteger value = toBigInt(bigint);
        if (n == 0) {
            return BigInteger.ZERO;
        }
        final BigInteger modulus = BigInteger.ONE.shiftLeft(n);
        BigInteger mod = value.mod(modulus);
        if (mod.compareTo(BigInteger.ONE.shiftLeft(n - 1)) >= 0) {
            mod = mod.subtract(modulus);
        }
        return mod;
    }

    /**
     * ES2020 20.2.2.2 BigInt.asUintN ( bits, bigint ) - the value modulo 2**bits,
     * interpreted as an unsigned integer.
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object asUintN(final Object self, final Object bits, final Object bigint) {
        final int n = toIndex(bits);
        final BigInteger value = toBigInt(bigint);
        return value.mod(BigInteger.ONE.shiftLeft(n));
    }

    /** ES2015 7.1.17 ToIndex, enough for the bit counts asIntN/asUintN take. */
    private static int toIndex(final Object value) {
        final double number = JSType.toInteger(value);
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw rangeError("invalid.array.length", JSType.toString(number));
        }
        return (int) number;
    }

    /**
     * ES2020 20.2.3.3 BigInt.prototype.toString ( [ radix ] )
     *
     * @param self  self reference
     * @param radix the radix, 2..36
     * @return the string form
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static String toString(final Object self, final Object radix) {
        final BigInteger value = getBigIntValue(self);
        final int r = radix == Undefined.getUndefined() ? 10 : (int) JSType.toInteger(radix);
        if (r < 2 || r > 36) {
            throw rangeError("invalid.radix");
        }
        return value.toString(r);
    }

    /**
     * ES2020 20.2.3.4 BigInt.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return the string form
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleString(final Object self) {
        return getBigIntValue(self).toString();
    }

    /**
     * ES2020 20.2.3.5 BigInt.prototype.valueOf ( )
     *
     * @param self self reference
     * @return the BigInt primitive
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return getBigIntValue(self);
    }

    /** ES2020 20.2.3.6 BigInt.prototype [ @@toStringTag ]. */
    @org.monflabs.nashorn.internal.objects.annotations.Property(
            where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "BigInt";

    /**
     * Lookup the appropriate method for an invoke dynamic call on a primitive
     * BigInt.
     *
     * @param request  the link request
     * @param receiver the primitive BigInt
     * @return the link
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final Object receiver) {
        return PrimitiveLookup.lookupPrimitive(request, BigInteger.class,
                new NativeBigInt((BigInteger) receiver, Global.instance()), WRAPFILTER, PROTOFILTER);
    }

    @SuppressWarnings("unused")
    private static NativeBigInt wrapFilter(final Object receiver) {
        return new NativeBigInt((BigInteger) receiver, Global.instance());
    }

    @SuppressWarnings("unused")
    private static Object protoFilter(final Object object) {
        return Global.instance().getBigIntPrototype();
    }

    private static MethodHandle findOwnMH(final String name, final java.lang.invoke.MethodType type) {
        return MH.findStatic(java.lang.invoke.MethodHandles.lookup(), NativeBigInt.class, name, type);
    }
}
