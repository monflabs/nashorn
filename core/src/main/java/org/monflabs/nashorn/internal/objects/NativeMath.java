/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.SpecializedFunction;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import java.math.BigDecimal;
import org.monflabs.nashorn.internal.runtime.ECMAErrors;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * ECMA 15.8 The Math Object
 *
 */
@ScriptClass("Math")
public final class NativeMath extends ScriptObject {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeMath() {
        // don't create me!
        throw new UnsupportedOperationException();
    }

    /** ECMA 15.8.1.1 - E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double E = Math.E;

    /** ECMA 15.8.1.2 - LN10, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LN10 = 2.302585092994046;

    /** ECMA 15.8.1.3 - LN2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LN2 = 0.6931471805599453;

    /** ECMA 15.8.1.4 - LOG2E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LOG2E = 1.4426950408889634;

    /** ECMA 15.8.1.5 - LOG10E, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double LOG10E = 0.4342944819032518;

    /** ECMA 15.8.1.6 - PI, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double PI = Math.PI;

    /** ECMA 15.8.1.7 - SQRT1_2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double SQRT1_2 = 0.7071067811865476;

    /** ECMA 15.8.1.8 - SQRT2, always a double constant. Not writable or configurable */
    @Property(attributes = Attribute.NON_ENUMERABLE_CONSTANT, where = Where.CONSTRUCTOR)
    public static final double SQRT2 = 1.4142135623730951;

    /**
     * ECMA 15.8.2.1 abs(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double abs(final Object self, final Object x) {
        return Math.abs(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for int values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static double abs(final Object self, final int x) {
        return x == Integer.MIN_VALUE? Math.abs((double)x) : Math.abs(x);
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for long values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static long abs(final Object self, final long x) {
        return Math.abs(x);
    }

    /**
     * ECMA 15.8.2.1 abs(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return abs of argument
     */
    @SpecializedFunction
    public static double abs(final Object self, final double x) {
        return Math.abs(x);
    }

    /**
     * ECMA 15.8.2.2 acos(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return acos of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double acos(final Object self, final Object x) {
        return Math.acos(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.2 acos(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return acos of argument
     */
    @SpecializedFunction
    public static double acos(final Object self, final double x) {
        return Math.acos(x);
    }

    /**
     * ECMA 15.8.2.3 asin(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return asin of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double asin(final Object self, final Object x) {
        return Math.asin(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.3 asin(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return asin of argument
     */
    @SpecializedFunction
    public static double asin(final Object self, final double x) {
        return Math.asin(x);
    }

    /**
     * ECMA 15.8.2.4 atan(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return atan of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double atan(final Object self, final Object x) {
        return Math.atan(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.4 atan(x) - specialization for double values
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return atan of argument
     */
    @SpecializedFunction
    public static double atan(final Object self, final double x) {
        return Math.atan(x);
    }

    /**
     * ECMA 15.8.2.5 atan2(x,y)
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return atan2 of x and y
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double atan2(final Object self, final Object y, final Object x) {
        return Math.atan2(JSType.toNumber(y), JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.5 atan2(x,y) - specialization for double values
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return atan2 of x and y
     */
    @SpecializedFunction
    public static double atan2(final Object self, final double y, final double x) {
        return Math.atan2(y,x);
    }

    /**
     * ECMA 15.8.2.6 ceil(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double ceil(final Object self, final Object x) {
        return Math.ceil(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static int ceil(final Object self, final int x) {
        return x;
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static long ceil(final Object self, final long x) {
        return x;
    }

    /**
     * ECMA 15.8.2.6 ceil(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return ceil of argument
     */
    @SpecializedFunction
    public static double ceil(final Object self, final double x) {
        return Math.ceil(x);
    }

    /**
     * ECMA 15.8.2.7 cos(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return cos of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double cos(final Object self, final Object x) {
        return Math.cos(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.7 cos(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return cos of argument
     */
    @SpecializedFunction
    public static double cos(final Object self, final double x) {
        return Math.cos(x);
    }

    /**
     * ECMA 15.8.2.8 exp(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return exp of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double exp(final Object self, final Object x) {
        return Math.exp(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.9 floor(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double floor(final Object self, final Object x) {
        return Math.floor(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static int floor(final Object self, final int x) {
        return x;
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static long floor(final Object self, final long x) {
        return x;
    }

    /**
     * ECMA 15.8.2.9 floor(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return floor of argument
     */
    @SpecializedFunction
    public static double floor(final Object self, final double x) {
        return Math.floor(x);
    }

    /**
     * ECMA 15.8.2.10 log(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return log of argument
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double log(final Object self, final Object x) {
        return Math.log(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.10 log(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return log of argument
     */
    @SpecializedFunction
    public static double log(final Object self, final double x) {
        return Math.log(x);
    }

    /**
     * ECMA 15.8.2.11 max(x)
     *
     * @param self  self reference
     * @param args  arguments
     *
     * @return the largest of the arguments, {@link Double#NEGATIVE_INFINITY} if no args given, or identity if one arg is given
     */
    @Function(arity = 2, attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double max(final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return Double.NEGATIVE_INFINITY;
        case 1:
            return JSType.toNumber(args[0]);
        default:
            double res = JSType.toNumber(args[0]);
            for (int i = 1; i < args.length; i++) {
                res = Math.max(res, JSType.toNumber(args[i]));
            }
            return res;
        }
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized no args version
     *
     * @param self  self reference
     *
     * @return {@link Double#NEGATIVE_INFINITY}
     */
    @SpecializedFunction
    public static double max(final Object self) {
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static int max(final Object self, final int x, final int y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static long max(final Object self, final long x, final long y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static double max(final Object self, final double x, final double y) {
        return Math.max(x, y);
    }

    /**
     * ECMA 15.8.2.11 max(x) - specialized version for two Object args
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return largest value of x and y
     */
    @SpecializedFunction
    public static double max(final Object self, final Object x, final Object y) {
        return Math.max(JSType.toNumber(x), JSType.toNumber(y));
    }

    /**
     * ECMA 15.8.2.12 min(x)
     *
     * @param self  self reference
     * @param args  arguments
     *
     * @return the smallest of the arguments, {@link Double#NEGATIVE_INFINITY} if no args given, or identity if one arg is given
     */
    @Function(arity = 2, attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double min(final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return Double.POSITIVE_INFINITY;
        case 1:
            return JSType.toNumber(args[0]);
        default:
            double res = JSType.toNumber(args[0]);
            for (int i = 1; i < args.length; i++) {
                res = Math.min(res, JSType.toNumber(args[i]));
            }
            return res;
        }
    }

    /**
     * ECMA 15.8.2.11 min(x) - specialized no args version
     *
     * @param self  self reference
     *
     * @return {@link Double#POSITIVE_INFINITY}
     */
    @SpecializedFunction
    public static double min(final Object self) {
        return Double.POSITIVE_INFINITY;
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for ints
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static int min(final Object self, final int x, final int y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for longs
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static long min(final Object self, final long x, final long y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static double min(final Object self, final double x, final double y) {
        return Math.min(x, y);
    }

    /**
     * ECMA 15.8.2.12 min(x) - specialized version for two Object args
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return smallest value of x and y
     */
    @SpecializedFunction
    public static double min(final Object self, final Object x, final Object y) {
        return Math.min(JSType.toNumber(x), JSType.toNumber(y));
    }

    /**
     * ECMA 15.8.2.13 pow(x,y)
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return x raised to the power of y
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double pow(final Object self, final Object x, final Object y) {
        return Math.pow(JSType.toNumber(x), JSType.toNumber(y));
    }

    /**
     * ECMA 15.8.2.13 pow(x,y) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     first argument
     * @param y     second argument
     *
     * @return x raised to the power of y
     */
    @SpecializedFunction
    public static double pow(final Object self, final double x, final double y) {
        return Math.pow(x, y);
    }

    /**
     * ECMA 15.8.2.14 random()
     *
     * @param self  self reference
     *
     * @return random number in the range [0..1)
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double random(final Object self) {
        return Math.random();
    }

    /**
     * ECMA 15.8.2.15 round(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return x rounded
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double round(final Object self, final Object x) {
        final double d = JSType.toNumber(x);
        if (Math.getExponent(d) >= 52) {
            return d;
        }
        // 20.2.2.28 is floor(x + 0.5) in exact arithmetic. Adding a half in
        // doubles rounds a value just below one half up to the next integer -
        // 0.5 - ulp/2 answers 1 - so the fraction is what is compared instead
        final double floor = Math.floor(d);
        return Math.copySign(d - floor >= 0.5 ? floor + 1 : floor, d);
    }

    /**
     * ECMA 15.8.2.16 sin(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sin of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double sin(final Object self, final Object x) {
        return Math.sin(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.16 sin(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sin of x
     */
    @SpecializedFunction
    public static double sin(final Object self, final double x) {
        return Math.sin(x);
    }

    /**
     * ECMA 15.8.2.17 sqrt(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sqrt of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double sqrt(final Object self, final Object x) {
        return Math.sqrt(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.17 sqrt(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return sqrt of x
     */
    @SpecializedFunction
    public static double sqrt(final Object self, final double x) {
        return Math.sqrt(x);
    }

    /**
     * ECMA 15.8.2.18 tan(x)
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return tan of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where=Where.CONSTRUCTOR)
    public static double tan(final Object self, final Object x) {
        return Math.tan(JSType.toNumber(x));
    }

    /**
     * ECMA 15.8.2.18 tan(x) - specialized version for doubles
     *
     * @param self  self reference
     * @param x     argument
     *
     * @return tan of x
     */
    @SpecializedFunction
    public static double tan(final Object self, final double x) {
        return Math.tan(x);
    }

    /**
     * ECMAScript 2015 Math.cbrt(x), the cube root of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.cbrt(x), the cube root of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double cbrt(final Object self, final Object x) {
        return cbrt(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.cbrt(x), the cube root of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.cbrt(x), the cube root of x
     */
    @SpecializedFunction
    public static double cbrt(final Object self, final double x) {
        return Math.cbrt(x);
    }

    /**
     * ECMAScript 2015 Math.expm1(x), exp(x) - 1 computed accurately for small x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.expm1(x), exp(x) - 1 computed accurately for small x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double expm1(final Object self, final Object x) {
        return expm1(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.expm1(x), exp(x) - 1 computed accurately for small x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.expm1(x), exp(x) - 1 computed accurately for small x
     */
    @SpecializedFunction
    public static double expm1(final Object self, final double x) {
        return Math.expm1(x);
    }

    /**
     * ECMAScript 2015 Math.log1p(x), log(1 + x) computed accurately for small x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log1p(x), log(1 + x) computed accurately for small x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double log1p(final Object self, final Object x) {
        return log1p(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.log1p(x), log(1 + x) computed accurately for small x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log1p(x), log(1 + x) computed accurately for small x
     */
    @SpecializedFunction
    public static double log1p(final Object self, final double x) {
        return Math.log1p(x);
    }

    /**
     * ECMAScript 2015 Math.log10(x), the base 10 logarithm of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log10(x), the base 10 logarithm of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double log10(final Object self, final Object x) {
        return log10(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.log10(x), the base 10 logarithm of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log10(x), the base 10 logarithm of x
     */
    @SpecializedFunction
    public static double log10(final Object self, final double x) {
        return Math.log10(x);
    }

    /**
     * ECMAScript 2015 Math.sinh(x), the hyperbolic sine of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.sinh(x), the hyperbolic sine of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double sinh(final Object self, final Object x) {
        return sinh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.sinh(x), the hyperbolic sine of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.sinh(x), the hyperbolic sine of x
     */
    @SpecializedFunction
    public static double sinh(final Object self, final double x) {
        return Math.sinh(x);
    }

    /**
     * ECMAScript 2015 Math.cosh(x), the hyperbolic cosine of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.cosh(x), the hyperbolic cosine of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double cosh(final Object self, final Object x) {
        return cosh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.cosh(x), the hyperbolic cosine of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.cosh(x), the hyperbolic cosine of x
     */
    @SpecializedFunction
    public static double cosh(final Object self, final double x) {
        return Math.cosh(x);
    }

    /**
     * ECMAScript 2015 Math.tanh(x), the hyperbolic tangent of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.tanh(x), the hyperbolic tangent of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double tanh(final Object self, final Object x) {
        return tanh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.tanh(x), the hyperbolic tangent of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.tanh(x), the hyperbolic tangent of x
     */
    @SpecializedFunction
    public static double tanh(final Object self, final double x) {
        return Math.tanh(x);
    }

    /**
     * ECMAScript 2015 Math.log2(x), the base 2 logarithm of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log2(x), the base 2 logarithm of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double log2(final Object self, final Object x) {
        return log2(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.log2(x), the base 2 logarithm of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.log2(x), the base 2 logarithm of x
     */
    @SpecializedFunction
    public static double log2(final Object self, final double x) {
        return Math.log(x) / LN2;
    }

    /**
     * ECMAScript 2015 Math.asinh(x), the inverse hyperbolic sine of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.asinh(x), the inverse hyperbolic sine of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double asinh(final Object self, final Object x) {
        return asinh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.asinh(x), the inverse hyperbolic sine of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.asinh(x), the inverse hyperbolic sine of x
     */
    @SpecializedFunction
    public static double asinh(final Object self, final double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
            // preserves -0, and +/-Infinity map to themselves
            return x;
        }
        return x < 0 ? -Math.log(-x + Math.sqrt(x * x + 1.0)) : Math.log(x + Math.sqrt(x * x + 1.0));
    }

    /**
     * ECMAScript 2015 Math.acosh(x), the inverse hyperbolic cosine of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.acosh(x), the inverse hyperbolic cosine of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double acosh(final Object self, final Object x) {
        return acosh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.acosh(x), the inverse hyperbolic cosine of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.acosh(x), the inverse hyperbolic cosine of x
     */
    @SpecializedFunction
    public static double acosh(final Object self, final double x) {
        if (Double.isNaN(x) || x < 1.0) {
            return Double.NaN;
        }
        return Math.log(x + Math.sqrt(x * x - 1.0));
    }

    /**
     * ECMAScript 2015 Math.atanh(x), the inverse hyperbolic tangent of x
     *
     * @param self self reference
     * @param x    argument
     * @return Math.atanh(x), the inverse hyperbolic tangent of x
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double atanh(final Object self, final Object x) {
        return atanh(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.atanh(x), the inverse hyperbolic tangent of x - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.atanh(x), the inverse hyperbolic tangent of x
     */
    @SpecializedFunction
    public static double atanh(final Object self, final double x) {
        if (Double.isNaN(x) || x < -1.0 || x > 1.0) {
            return Double.NaN;
        }
        if (x == 0.0) {
            return x;
        }
        return 0.5 * Math.log((1.0 + x) / (1.0 - x));
    }

    /**
     * ECMAScript 2015 Math.sign(x): -1, -0, +0, +1 or NaN
     *
     * @param self self reference
     * @param x    argument
     * @return Math.sign(x): -1, -0, +0, +1 or NaN
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double sign(final Object self, final Object x) {
        return sign(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.sign(x): -1, -0, +0, +1 or NaN - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.sign(x): -1, -0, +0, +1 or NaN
     */
    @SpecializedFunction
    public static double sign(final Object self, final double x) {
        if (Double.isNaN(x) || x == 0.0) {
            // NaN, and both zeros, are returned unchanged
            return x;
        }
        return Math.signum(x);
    }

    /**
     * ECMAScript 2015 Math.trunc(x), x with any fractional part removed
     *
     * @param self self reference
     * @param x    argument
     * @return Math.trunc(x), x with any fractional part removed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double trunc(final Object self, final Object x) {
        return trunc(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.trunc(x), x with any fractional part removed - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.trunc(x), x with any fractional part removed
     */
    @SpecializedFunction
    public static double trunc(final Object self, final double x) {
        if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
            return x;
        }
        return x < 0 ? Math.ceil(x) : Math.floor(x);
    }

    /**
     * ECMAScript 2015 Math.fround(x), x rounded to the nearest float
     *
     * @param self self reference
     * @param x    argument
     * @return Math.fround(x), x rounded to the nearest float
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double fround(final Object self, final Object x) {
        return fround(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.fround(x), x rounded to the nearest float - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.fround(x), x rounded to the nearest float
     */
    @SpecializedFunction
    public static double fround(final Object self, final double x) {
        return (float)x;
    }

    /**
     * ES2025 Math.f16round(x), x rounded to the nearest half-precision (binary16) value.
     *
     * @param self self reference
     * @param x    argument
     * @return Math.f16round(x)
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double f16round(final Object self, final Object x) {
        return f16round(self, JSType.toNumber(x));
    }

    /**
     * ES2025 Math.f16round(x) - specialized version for doubles.
     *
     * @param self self reference
     * @param x    argument
     * @return Math.f16round(x)
     */
    @SpecializedFunction
    public static double f16round(final Object self, final double x) {
        return Float.float16ToFloat(NativeFloat16Array.doubleToFloat16(x));
    }

    /**
     * ECMAScript 2015 Math.clz32(x), the leading zero count of x as a 32 bit integer
     *
     * @param self self reference
     * @param x    argument
     * @return Math.clz32(x), the leading zero count of x as a 32 bit integer
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static double clz32(final Object self, final Object x) {
        return clz32(self, JSType.toNumber(x));
    }

    /**
     * ECMAScript 2015 Math.clz32(x), the leading zero count of x as a 32 bit integer - specialized version for doubles
     *
     * @param self self reference
     * @param x    argument
     * @return Math.clz32(x), the leading zero count of x as a 32 bit integer
     */
    @SpecializedFunction
    public static double clz32(final Object self, final double x) {
        return Integer.numberOfLeadingZeros(JSType.toUint32(x) == 0 ? 0 : (int)JSType.toUint32(x));
    }

    /**
     * ECMAScript 2015 Math.hypot(x, y, ...), the square root of the sum of squares.
     *
     * @param self self reference
     * @param args the values
     * @return the square root of the sum of the squares of the arguments
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static double hypot(final Object self, final Object... args) {
        // An infinite argument wins over a NaN one, so both are collected before
        // deciding, and every argument is coerced even after the answer is known.
        boolean infinite = false;
        boolean notANumber = false;
        double sum = 0.0;
        double max = 0.0;
        final double[] values = new double[args.length];

        for (int i = 0; i < args.length; i++) {
            final double value = Math.abs(JSType.toNumber(args[i]));
            values[i] = value;
            if (Double.isInfinite(value)) {
                infinite = true;
            } else if (Double.isNaN(value)) {
                notANumber = true;
            } else if (value > max) {
                max = value;
            }
        }

        if (infinite) {
            return Double.POSITIVE_INFINITY;
        }
        if (notANumber) {
            return Double.NaN;
        }
        if (max == 0.0) {
            return 0.0;
        }

        // scale by the largest term so that squaring cannot overflow
        for (final double value : values) {
            final double scaled = value / max;
            sum += scaled * scaled;
        }
        return max * Math.sqrt(sum);
    }

    // Math.sumPrecise running state: only -0 (or nothing) seen so far, a finite
    // running total, a single infinity, or NaN (which any NaN, or both signs of
    // infinity, forces).
    private static final int SUM_MINUS_ZERO = 0, SUM_FINITE = 1, SUM_PLUS_INF = 2, SUM_MINUS_INF = 3, SUM_NAN = 4;

    /**
     * ES2026 21.3.2.20 Math.sumPrecise(items): the correctly-rounded sum of an
     * iterable of Numbers. Each element must be a Number (no coercion) or it is
     * a TypeError. NaN wins; both infinities together are NaN; a lone infinity is
     * that infinity; an empty list or only -0 is -0. The finite sum is
     * accumulated exactly in a {@link BigDecimal} and rounded once to the nearest
     * double, so a magnitude mix like [1e20, 1, -1e20] gives exactly 1.
     *
     * @param self  the Math object
     * @param items an iterable of Numbers
     * @return the precise sum
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static double sumPrecise(final Object self, final Object items) {
        final int[] state = { SUM_MINUS_ZERO };
        final BigDecimal[] sum = { BigDecimal.ZERO };
        AbstractIterator.iterate(items, Global.instance(), value -> {
            if (!JSType.isNumber(value)) {
                throw ECMAErrors.typeError("not.a.number", ScriptRuntime.safeToString(value));
            }
            final double d = ((Number) value).doubleValue();
            switch (state[0]) {
            case SUM_NAN:
                return;
            case SUM_PLUS_INF:
                if (Double.isNaN(d) || d == Double.NEGATIVE_INFINITY) {
                    state[0] = SUM_NAN;
                }
                return;
            case SUM_MINUS_INF:
                if (Double.isNaN(d) || d == Double.POSITIVE_INFINITY) {
                    state[0] = SUM_NAN;
                }
                return;
            default: // SUM_MINUS_ZERO or SUM_FINITE
                if (Double.isNaN(d)) {
                    state[0] = SUM_NAN;
                } else if (d == Double.POSITIVE_INFINITY) {
                    state[0] = SUM_PLUS_INF;
                } else if (d == Double.NEGATIVE_INFINITY) {
                    state[0] = SUM_MINUS_INF;
                } else if (state[0] == SUM_MINUS_ZERO) {
                    // -0 leaves the state as minus-zero; the first other finite
                    // value starts the running total
                    if (!(d == 0.0 && 1.0 / d == Double.NEGATIVE_INFINITY)) {
                        state[0] = SUM_FINITE;
                        sum[0] = new BigDecimal(d);
                    }
                } else {
                    sum[0] = sum[0].add(new BigDecimal(d));
                }
            }
        });
        switch (state[0]) {
        case SUM_NAN:       return Double.NaN;
        case SUM_PLUS_INF:  return Double.POSITIVE_INFINITY;
        case SUM_MINUS_INF: return Double.NEGATIVE_INFINITY;
        case SUM_MINUS_ZERO: return -0.0;
        default:            return sum[0].doubleValue();
        }
    }

    /**
     * ECMAScript 2015 Math.imul(x, y), C-like 32 bit integer multiplication.
     *
     * @param self self reference
     * @param x    first value
     * @param y    second value
     * @return the low 32 bits of the product, as a signed integer
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static int imul(final Object self, final Object x, final Object y) {
        return JSType.toInt32(x) * JSType.toInt32(y);
    }

    /**
     * ES2015 20.2.1.9 Math [ @@toStringTag ].
     */
    @Property(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Math";

}
