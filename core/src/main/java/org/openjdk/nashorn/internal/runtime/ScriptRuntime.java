/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.nashorn.internal.runtime;

import static org.openjdk.nashorn.internal.codegen.CompilerConstants.staticCall;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.syntaxError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;
import static org.openjdk.nashorn.internal.runtime.JSType.isString;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SwitchPoint;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import jdk.dynalink.beans.BeansLinker;
import jdk.dynalink.beans.StaticClass;
import org.openjdk.nashorn.api.scripting.JSObject;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.openjdk.nashorn.internal.codegen.ApplySpecialization;
import org.openjdk.nashorn.internal.codegen.CompilerConstants;
import org.openjdk.nashorn.internal.codegen.CompilerConstants.Call;
import org.openjdk.nashorn.internal.ir.debug.JSONWriter;
import org.openjdk.nashorn.internal.objects.AbstractIterator;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.objects.NativeGenerator;
import org.openjdk.nashorn.internal.objects.NativeSymbol;
import org.openjdk.nashorn.internal.objects.NativeArray;
import org.openjdk.nashorn.internal.objects.NativeObject;
import org.openjdk.nashorn.internal.objects.NativeJava;
import org.openjdk.nashorn.internal.parser.Lexer;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayIndex;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;
import org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import org.openjdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * Utilities to be called by JavaScript runtime API and generated classes.
 */

public final class ScriptRuntime {
    private ScriptRuntime() {
    }

    /** Singleton representing the empty array object '[]' */
    public static final Object[] EMPTY_ARRAY = new Object[0];

    /** Unique instance of undefined. */
    public static final Undefined UNDEFINED = Undefined.getUndefined();

    /**
     * Unique instance of undefined used to mark empty array slots.
     * Can't escape the array.
     */
    public static final Undefined EMPTY = Undefined.getEmpty();

    /** Method handle to generic + operator, operating on objects */
    public static final Call ADD = staticCallNoLookup(ScriptRuntime.class, "ADD", Object.class, Object.class, Object.class);

    /** Method handle to generic === operator, operating on objects */
    public static final Call EQ_STRICT = staticCallNoLookup(ScriptRuntime.class, "EQ_STRICT", boolean.class, Object.class, Object.class);

    /** Method handle used to enter a {@code with} scope at runtime. */
    public static final Call OPEN_WITH = staticCallNoLookup(ScriptRuntime.class, "openWith", ScriptObject.class, ScriptObject.class, Object.class);

    /**
     * Method used to place a scope's variable into the Global scope, which has to be done for the
     * properties declared at outermost script level.
     */
    public static final Call MERGE_SCOPE = staticCallNoLookup(ScriptRuntime.class, "mergeScope", ScriptObject.class, ScriptObject.class);

    /**
     * Return an appropriate iterator for the elements in a for-in construct
     */
    public static final Call TO_PROPERTY_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toPropertyIterator", Iterator.class, Object.class);

    /**
     * Return an appropriate iterator for the elements in a for-each construct
     */
    public static final Call TO_VALUE_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toValueIterator", Iterator.class, Object.class);

    /**
     * Return an appropriate iterator for the elements in a ES6 for-of loop
     */
    public static final Call TO_ES6_ITERATOR = staticCallNoLookup(ScriptRuntime.class, "toES6Iterator", Iterator.class, Object.class);

    /**
      * Method handle for apply. Used from {@link ScriptFunction} for looking up calls to
      * call sites that are known to be megamorphic. Using an invoke dynamic here would
      * lead to the JVM deoptimizing itself to death
      */
    public static final Call APPLY = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "apply", Object.class, ScriptFunction.class, Object.class, Object[].class);

    /**
     * Throws a reference error for an undefined variable.
     */
    public static final Call THROW_REFERENCE_ERROR = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "throwReferenceError", void.class, String.class);

    /**
     * Throws a reference error for an undefined variable.
     */
    public static final Call THROW_CONST_TYPE_ERROR = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "throwConstTypeError", void.class, String.class);

    /**
     * Used to invalidate builtin names, e.g "Function" mapping to all properties in Function.prototype and Function.prototype itself.
     */
    public static final Call INVALIDATE_RESERVED_BUILTIN_NAME = staticCallNoLookup(ScriptRuntime.class, "invalidateReservedBuiltinName", void.class, String.class);

    /**
     * Used to perform failed delete under strict mode
     */
    public static final Call STRICT_FAIL_DELETE = staticCallNoLookup(ScriptRuntime.class, "strictFailDelete", boolean.class, String.class);

    /**
     * Used to find the scope for slow delete
     */
    public static final Call SLOW_DELETE = staticCallNoLookup(ScriptRuntime.class, "slowDelete", boolean.class, ScriptObject.class, String.class);

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final Object tag, final int deflt) {
        if (tag instanceof Number) {
            final double d = ((Number)tag).doubleValue();
            if (isRepresentableAsInt(d)) {
                return (int)d;
            }
        }
        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final boolean tag, final int deflt) {
        return deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final long tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * Converts a switch tag value to a simple integer. deflt value if it can't.
     *
     * @param tag   Switch statement tag value.
     * @param deflt default to use if not convertible.
     * @return int tag value (or deflt.)
     */
    public static int switchTagAsInt(final double tag, final int deflt) {
        return isRepresentableAsInt(tag) ? (int)tag : deflt;
    }

    /**
     * This is the builtin implementation of {@code Object.prototype.toString}
     * @param self reference
     * @return string representation as object
     */
    public static String builtinObjectToString(final Object self) {
        // ES2015 19.1.3.6: a string-valued Symbol.toStringTag names the object
        // instead of its class.
        if (WellKnownSymbols.toStringTagInstalled() && self instanceof ScriptObject sobj) {
            final Object tag = sobj.get(NativeSymbol.toStringTag);
            if (JSType.isString(tag)) {
                return "[object " + tag + ']';
            }
        }

        String className;
        // Spec tells us to convert primitives by ToObject..
        // But we don't need to -- all we need is the right class name
        // of the corresponding primitive wrapper type.

        final JSType type = JSType.ofNoFunction(self);

        switch (type) {
        case BOOLEAN:
            className = "Boolean";
            break;
        case NUMBER:
            className = "Number";
            break;
        case STRING:
            className = "String";
            break;
        // special case of null and undefined
        case NULL:
            className = "Null";
            break;
        case UNDEFINED:
            className = "Undefined";
            break;
        case OBJECT:
            if (self instanceof ScriptObject) {
                className = ((ScriptObject)self).getClassName();
            } else if (self instanceof JSObject) {
                className = ((JSObject)self).getClassName();
            } else {
                className = self.getClass().getName();
            }
            break;
        default:
            // Nashorn extension: use Java class name
            className = self.getClass().getName();
            break;
        }

        return "[object " + className + ']';
    }

    /**
     * This is called whenever runtime wants to throw an error and wants to provide
     * meaningful information about an object. We don't want to call toString which
     * ends up calling "toString" from script world which may itself throw error.
     * When we want to throw an error, we don't additional error from script land
     * -- which may sometimes lead to infinite recursion.
     *
     * @param obj Object to converted to String safely (without calling user script)
     * @return safe String representation of the given object
     */
    public static String safeToString(final Object obj) {
        return JSType.toStringImpl(obj, true);
    }

    /**
     * Returns an iterator over property identifiers used in the {@code for...in} statement. Note that the ECMAScript
     * 5.1 specification, chapter 12.6.4. uses the terminology "property names", which seems to imply that the property
     * identifiers are expected to be strings, but this is not actually spelled out anywhere, and Nashorn will in some
     * cases deviate from this. Namely, we guarantee to always return an iterator over {@link String} values for any
     * built-in JavaScript object. We will however return an iterator over {@link Integer} objects for native Java
     * arrays and {@link List} objects, as well as arbitrary objects representing keys of a {@link Map}. Therefore, the
     * expression {@code typeof i} within a {@code for(i in obj)} statement can return something other than
     * {@code string} when iterating over native Java arrays, {@code List}, and {@code Map} objects.
     * @param obj object to iterate on.
     * @return iterator over the object's property names.
     */
    public static Iterator<?> toPropertyIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).propertyIterator();
        }

        if (obj != null && obj.getClass().isArray()) {
            return new RangeIterator(Array.getLength(obj));
        }

        if (obj instanceof JSObject) {
            return ((JSObject)obj).keySet().iterator();
        }

        if (obj instanceof List) {
            return new RangeIterator(((List<?>)obj).size());
        }

        if (obj instanceof Map) {
            return ((Map<?,?>)obj).keySet().iterator();
        }

        final Object wrapped = Global.instance().wrapAsObject(obj);
        if (wrapped instanceof ScriptObject) {
            return ((ScriptObject)wrapped).propertyIterator();
        }

        return Collections.emptyIterator();
    }

    private static final class RangeIterator implements Iterator<Integer> {
        private final int length;
        private int index;

        RangeIterator(final int length) {
            this.length = length;
        }

        @Override
        public boolean hasNext() {
            return index < length;
        }

        @Override
        public Integer next() {
            return index++;
        }

    }

    // value Iterator for important Java objects - arrays, maps, iterables.
    private static Iterator<?> iteratorForJavaArrayOrList(final Object obj) {
        if (obj != null && obj.getClass().isArray()) {
            final int    length = Array.getLength(obj);

            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < length;
                }

                @Override
                public Object next() {
                    if (index >= length) {
                        throw new NoSuchElementException();
                    }
                    return Array.get(obj, index++);
                }

            };
        }

        if (obj instanceof Iterable) {
            return ((Iterable<?>)obj).iterator();
        }

        return null;
    }

    /**
     * Returns an iterator over property values used in the {@code for each...in} statement. Aside from built-in JS
     * objects, it also operates on Java arrays, any {@link Iterable}, as well as on {@link Map} objects, iterating over
     * map values.
     * @param obj object to iterate on.
     * @return iterator over the object's property values.
     */
    public static Iterator<?> toValueIterator(final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).valueIterator();
        }

        if (obj instanceof JSObject) {
            return ((JSObject)obj).values().iterator();
        }

        final Iterator<?> itr = iteratorForJavaArrayOrList(obj);
        if (itr != null) {
            return itr;
        }

        if (obj instanceof Map) {
            return ((Map<?,?>)obj).values().iterator();
        }

        final Object wrapped = Global.instance().wrapAsObject(obj);
        if (wrapped instanceof ScriptObject) {
            return ((ScriptObject)wrapped).valueIterator();
        }

        return Collections.emptyIterator();
    }

    /**
     * Returns an iterator over property values used in the {@code for ... of} statement. The iterator uses the
     * Iterator interface defined in version 6 of the ECMAScript specification.
     *
     * @param obj object to iterate on.
     * @return iterator based on the ECMA 6 Iterator interface.
     */
    public static Iterator<?> toES6Iterator(final Object obj) {
        if (obj instanceof CloseableIterator closeable) {
            // a for-of loop whose iterator was obtained ahead of the loop, so that
            // the finally block that closes it can reach it
            return closeable;
        }
        // if not a ScriptObject, try convenience iterator for Java objects!
        if (!(obj instanceof ScriptObject)) {
            final Iterator<?> itr = iteratorForJavaArrayOrList(obj);
            if (itr != null) {
                return itr;
            }

        if (obj instanceof Map) {
            return new Iterator<>() {
                private final Iterator<?> iter = ((Map<?,?>)obj).entrySet().iterator();

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Object next() {
                    Map.Entry<?,?> next = (Map.Entry)iter.next();
                    Object[] keyvalue = new Object[]{next.getKey(), next.getValue()};
                    return NativeJava.from(null, keyvalue);
                }

                @Override
                public void remove() {
                    iter.remove();
                }
            };
        }
        }

        final Global global = Global.instance();
        final Object iterator = AbstractIterator.getIterator(Global.toObject(obj), global);

        final InvokeByName nextInvoker = AbstractIterator.getNextInvoker(global);
        final MethodHandle doneInvoker = AbstractIterator.getDoneInvoker(global);
        final MethodHandle valueInvoker = AbstractIterator.getValueInvoker(global);

        return new CloseableIterator() {

            /**
             * The step this iterator is holding, or null before the first one is
             * asked for.
             *
             * It is fetched when hasNext() asks, and not a moment sooner. The
             * obvious implementation - fetch the following step while returning
             * the current one - reads one element too many, which a script can
             * see: "for (x of it) break" would call next() twice, and Array.from
             * with a mapping function would interleave its calls wrongly.
             */
            private Object nextResult;
            private boolean fetched;
            private boolean exhausted;

            private Object nextResult() {
                try {
                    final Object next = nextInvoker.getGetter().invokeExact(iterator);
                    if (Bootstrap.isCallable(next)) {
                        return nextInvoker.getInvoker().invokeExact(next, iterator, (Object) null);
                    }
                } catch (final RuntimeException|Error r) {
                    throw r;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                if (!fetched) {
                    nextResult = nextResult();
                    fetched = true;
                }
                if (nextResult == null) {
                    exhausted = true;
                    return false;
                }
                try {
                    final Object done = doneInvoker.invokeExact(nextResult);
                    if (JSType.toBoolean(done)) {
                        exhausted = true;
                        return false;
                    }
                    return true;
                } catch (final RuntimeException|Error r) {
                    throw r;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            }

            @Override
            public void close() {
                if (exhausted) {
                    return;
                }
                exhausted = true;
                // ES2015 7.4.6 IteratorClose: tell an unfinished iterator that
                // nobody will ask it for more, so a generator can run its finally
                // blocks. A failure here is not worth reporting over whatever the
                // caller was doing.
                if (iterator instanceof ScriptObject sobj) {
                    try {
                        if (sobj.get("return") instanceof ScriptFunction close) {
                            apply(close, iterator);
                        }
                    } catch (final RuntimeException ignored) {
                        // best effort
                    }
                }
            }

            @Override
            public Object next() {
                if (!fetched) {
                    hasNext();
                }
                fetched = false;
                if (nextResult == null) {
                    return Undefined.getUndefined();
                }
                try {
                    return valueInvoker.invokeExact(nextResult);
                } catch (final RuntimeException|Error r) {
                    throw r;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            }

        };
    }

    /**
     * Merge a scope into its prototype's map.
     * Merge a scope into its prototype.
     *
     * @param scope Scope to merge.
     * @return prototype object after merge
     */
    public static ScriptObject mergeScope(final ScriptObject scope) {
        final ScriptObject parentScope = scope.getProto();
        parentScope.addBoundProperties(scope);
        return parentScope;
    }

    /**
     * Call a function given self and args. If the number of the arguments is known in advance, you can likely achieve
     * better performance by creating a dynamic invoker using {@link Bootstrap#createDynamicCallInvoker(Class, Class...)}
     * then using its {@link MethodHandle#invokeExact(Object...)} method instead.
     *
     * @param target ScriptFunction object.
     * @param self   Receiver in call.
     * @param args   Call arguments.
     * @return Call result.
     */
    public static Object apply(final ScriptFunction target, final Object self, final Object... args) {
        JobQueue.enterScript();
        try {
            return target.invoke(self, args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        } finally {
            if (JobQueue.exitScript()) {
                final Global global = Context.getGlobal();
                if (global != null) {
                    global.getJobQueue().drain();
                }
            }
        }
    }

    /**
     * Throws a reference error for an undefined variable.
     *
     * @param name the variable name
     */
    public static void throwReferenceError(final String name) {
        throw referenceError("not.defined", name);
    }

    /**
     * Throws a type error for an assignment to a const.
     *
     * @param name the const name
     */
    public static void throwConstTypeError(final String name) {
        throw typeError("assign.constant", name);
    }

    /**
     * Call a script function as a constructor with given args.
     *
     * @param target ScriptFunction object.
     * @param args   Call arguments.
     * @return Constructor call result.
     */
    public static Object construct(final ScriptFunction target, final Object... args) {
        try {
            return target.construct(args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * ES2015 7.2.10 SameValueZero, which is SameValue except that it does not
     * distinguish the two zeroes - the comparison Array.prototype.includes and
     * the Map and Set families use, so that a key of -0 is found by 0 while NaN
     * is still found by NaN.
     *
     * @param x one value
     * @param y the other
     * @return whether they are the same for this purpose
     */
    public static boolean sameValueZero(final Object x, final Object y) {
        if (x instanceof Number a && y instanceof Number b) {
            final double dx = a.doubleValue();
            final double dy = b.doubleValue();
            return dx == dy || Double.isNaN(dx) && Double.isNaN(dy);
        }
        return sameValue(x, y);
    }

    /**
     * Generic implementation of ECMA 9.12 - SameValue algorithm
     *
     * @param x first value to compare
     * @param y second value to compare
     *
     * @return true if both objects have the same value
     */
    public static boolean sameValue(final Object x, final Object y) {
        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType != yType) {
            return false;
        }

        if (xType == JSType.UNDEFINED || xType == JSType.NULL) {
            return true;
        }

        if (xType == JSType.NUMBER) {
            final double xVal = ((Number)x).doubleValue();
            final double yVal = ((Number)y).doubleValue();

            if (Double.isNaN(xVal) && Double.isNaN(yVal)) {
                return true;
            }

            // checking for xVal == -0.0 and yVal == +0.0 or vice versa
            if (xVal == 0.0 && Double.doubleToLongBits(xVal) != Double.doubleToLongBits(yVal)) {
                return false;
            }

            return xVal == yVal;
        }

        if (xType == JSType.STRING || yType == JSType.BOOLEAN) {
            return x.equals(y);
        }

        return x == y;
    }

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
        return JSONWriter.parse(Context.getContext(), code, name, includeLoc);
    }

    /**
     * Test whether a char is valid JavaScript whitespace
     * @param ch a char
     * @return true if valid JavaScript whitespace
     */
    public static boolean isJSWhitespace(final char ch) {
        return Lexer.isJSWhitespace(ch);
    }

    /**
     * Entering a {@code with} node requires new scope. This is the implementation. When exiting the with statement,
     * use {@link ScriptObject#getProto()} on the scope.
     *
     * @param scope      existing scope
     * @param expression expression in with
     *
     * @return {@link WithObject} that is the new scope
     */
    public static ScriptObject openWith(final ScriptObject scope, final Object expression) {
        final Global global = Context.getGlobal();
        if (expression == UNDEFINED) {
            throw typeError(global, "cant.apply.with.to.undefined");
        } else if (expression == null) {
            throw typeError(global, "cant.apply.with.to.null");
        }

        if (expression instanceof ScriptObjectMirror) {
            final Object unwrapped = ScriptObjectMirror.unwrap(expression, global);
            if (unwrapped instanceof ScriptObject) {
                return new WithObject(scope, (ScriptObject)unwrapped);
            }
            // foreign ScriptObjectMirror
            final ScriptObject exprObj = global.newObject();
            NativeObject.bindAllProperties(exprObj, (ScriptObjectMirror)expression);
            return new WithObject(scope, exprObj);
        }

        final Object wrappedExpr = JSType.toScriptObject(global, expression);
        if (wrappedExpr instanceof ScriptObject) {
            return new WithObject(scope, (ScriptObject)wrappedExpr);
        }

        throw typeError(global, "cant.apply.with.to.non.scriptobject");
    }

    /**
     * ECMA 11.6.1 - The addition operator (+) - generic implementation
     *
     * @param x  first term
     * @param y  second term
     *
     * @return result of addition
     */
    public static Object ADD(final Object x, final Object y) {
        // This prefix code to handle Number special is for optimization.
        final boolean xIsNumber = x instanceof Number;
        final boolean yIsNumber = y instanceof Number;

        if (xIsNumber && yIsNumber) {
             return ((Number)x).doubleValue() + ((Number)y).doubleValue();
        }

        final boolean xIsUndefined = x == UNDEFINED;
        final boolean yIsUndefined = y == UNDEFINED;

        if (xIsNumber && yIsUndefined || xIsUndefined && yIsNumber || xIsUndefined && yIsUndefined) {
            return Double.NaN;
        }

        // code below is as per the spec.
        final Object xPrim = JSType.toPrimitive(x);
        final Object yPrim = JSType.toPrimitive(y);

        if (isString(xPrim) || isString(yPrim)) {
            try {
                return new ConsString(JSType.toCharSequence(xPrim), JSType.toCharSequence(yPrim));
            } catch (final IllegalArgumentException iae) {
                throw rangeError(iae, "concat.string.too.big");
            }
        }

        return JSType.toNumber(xPrim) + JSType.toNumber(yPrim);
    }

    /**
     * Debugger hook.
     * TODO: currently unimplemented
     *
     * @return undefined
     */
    public static Object DEBUGGER() {
        return UNDEFINED;
    }

    /**
     * New hook
     *
     * @param clazz type for the clss
     * @param args  constructor arguments
     *
     * @return undefined
     */
    public static Object NEW(final Object clazz, final Object... args) {
        return UNDEFINED;
    }

    /**
     * ECMA 11.4.3 The typeof Operator - generic implementation
     *
     * @param object   the object from which to retrieve property to type check
     * @param property property in object to check
     *
     * @return type name
     */
    public static Object TYPEOF(final Object object, final Object property) {
        Object obj = object;

        if (property != null) {
            if (obj instanceof ScriptObject) {
                // this is a scope identifier
                assert property instanceof String;
                final ScriptObject sobj = (ScriptObject) obj;

                final FindProperty find = sobj.findProperty(property, true, true, sobj);
                if (find != null) {
                    obj = find.getObjectValue();
                } else {
                    obj = sobj.invokeNoSuchProperty(property, false, UnwarrantedOptimismException.INVALID_PROGRAM_POINT);
                }

                if(Global.isLocationPropertyPlaceholder(obj)) {
                    if(CompilerConstants.__LINE__.name().equals(property)) {
                        obj = 0;
                    } else {
                        obj = "";
                    }
                }
            } else if (object instanceof Undefined) {
                obj = ((Undefined)obj).get(property);
            } else if (object == null) {
                throw typeError("cant.get.property", safeToString(property), "null");
            } else if (JSType.isPrimitive(obj)) {
                obj = ((ScriptObject)JSType.toScriptObject(obj)).get(property);
            } else if (obj instanceof JSObject) {
                obj = ((JSObject)obj).getMember(property.toString());
            } else {
                obj = UNDEFINED;
            }
        }

        return JSType.of(obj).typeName();
    }

    /**
     * Throw ReferenceError when LHS of assignment or increment/decrement
     * operator is not an assignable node (say a literal)
     *
     * @param lhs Evaluated LHS
     * @param rhs Evaluated RHS
     * @param msg Additional LHS info for error message
     * @return undefined
     */
    public static Object REFERENCE_ERROR(final Object lhs, final Object rhs, final Object msg) {
        throw referenceError("cant.be.used.as.lhs", Objects.toString(msg));
    }

    /**
     * ECMA 11.4.1 - delete operator, implementation for slow scopes
     *
     * This implementation of 'delete' walks the scope chain to find the scope that contains the
     * property to be deleted, then invokes delete on it. Always used on scopes, never strict.
     *
     * @param obj       top scope object
     * @param property  property to delete
     *
     * @return true if property was successfully found and deleted
     */
    public static boolean slowDelete(final ScriptObject obj, final String property) {
        ScriptObject sobj = obj;
        while (sobj != null && sobj.isScope()) {
            final FindProperty find = sobj.findProperty(property, false);
            if (find != null) {
                return sobj.delete(property, false);
            }
            sobj = sobj.getProto();
        }
        return obj.delete(property, false);
    }

    /**
     * ECMA 11.4.1 - delete operator, special case
     *
     * This is 'delete' on a scope; it always fails under strict mode.
     * It always throws an exception, but is declared to return a boolean
     * to be compatible with the delete operator type.
     *
     * @param property  property to delete
     * @return nothing, always throws an exception.
     */
    public static boolean strictFailDelete(final String property) {
        throw syntaxError("strict.cant.delete", property);
    }

    /**
     * ECMA 11.9.1 - The equals operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are equal
     */
    public static boolean EQ(final Object x, final Object y) {
        return equals(x, y);
    }

    /**
     * ECMA 11.9.2 - The does-not-equal operator (==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if type coerced versions of objects are not equal
     */
    public static boolean NE(final Object x, final Object y) {
        return !EQ(x, y);
    }

    /** ECMA 11.9.3 The Abstract Equality Comparison Algorithm */
    private static boolean equals(final Object x, final Object y) {
        // We want to keep this method small so we skip reference equality check for numbers
        // as NaN should return false when compared to itself (JDK-8043608).
        if (x == y && !(x instanceof Number)) {
            return true;
        }
        if (x instanceof ScriptObject && y instanceof ScriptObject) {
            return false; // x != y
        }
        if (x instanceof ScriptObjectMirror || y instanceof ScriptObjectMirror) {
            return ScriptObjectMirror.identical(x, y);
        }
        return equalValues(x, y);
    }

    /**
     * Extracted portion of {@code equals()} that compares objects by value (or by reference, if no known value
     * comparison applies).
     * @param x one value
     * @param y another value
     * @return true if they're equal according to 11.9.3
     */
    private static boolean equalValues(final Object x, final Object y) {
        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType == yType) {
            return equalSameTypeValues(x, y, xType);
        }

        return equalDifferentTypeValues(x, y, xType, yType);
    }

    /**
     * Extracted portion of {@link #equals(Object, Object)} and {@link #strictEquals(Object, Object)} that compares
     * values belonging to the same JSType.
     * @param x one value
     * @param y another value
     * @param type the common type for the values
     * @return true if they're equal
     */
    private static boolean equalSameTypeValues(final Object x, final Object y, final JSType type) {
        if (type == JSType.UNDEFINED || type == JSType.NULL) {
            return true;
        }

        if (type == JSType.NUMBER) {
            return ((Number)x).doubleValue() == ((Number)y).doubleValue();
        }

        if (type == JSType.STRING) {
            // String may be represented by ConsString
            return x.toString().equals(y.toString());
        }

        if (type == JSType.BOOLEAN) {
            return ((Boolean)x).booleanValue() == ((Boolean)y).booleanValue();
        }

        return x == y;
    }

    /**
     * Extracted portion of {@link #equals(Object, Object)} that compares values belonging to different JSTypes.
     * @param x one value
     * @param y another value
     * @param xType the type for the value x
     * @param yType the type for the value y
     * @return true if they're equal
     */
    private static boolean equalDifferentTypeValues(final Object x, final Object y, final JSType xType, final JSType yType) {
        if (isUndefinedAndNull(xType, yType) || isUndefinedAndNull(yType, xType)) {
            return true;
        } else if (isNumberAndString(xType, yType)) {
            return equalNumberToString(x, y);
        } else if (isNumberAndString(yType, xType)) {
            // Can reverse order as both are primitives
            return equalNumberToString(y, x);
        } else if (xType == JSType.BOOLEAN) {
            return equalBooleanToAny(x, y);
        } else if (yType == JSType.BOOLEAN) {
            // Can reverse order as y is primitive
            return equalBooleanToAny(y, x);
        } else if (isPrimitiveAndObject(xType, yType)) {
            return equalWrappedPrimitiveToObject(x, y);
        } else if (isPrimitiveAndObject(yType, xType)) {
            // Can reverse order as y is primitive
            return equalWrappedPrimitiveToObject(y, x);
        }

        return false;
    }

    private static boolean isUndefinedAndNull(final JSType xType, final JSType yType) {
        return xType == JSType.UNDEFINED && yType == JSType.NULL;
    }

    private static boolean isNumberAndString(final JSType xType, final JSType yType) {
        return xType == JSType.NUMBER && yType == JSType.STRING;
    }

    private static boolean isPrimitiveAndObject(final JSType xType, final JSType yType) {
        return (xType == JSType.NUMBER || xType == JSType.STRING || xType == JSType.SYMBOL) && yType == JSType.OBJECT;
    }

    private static boolean equalNumberToString(final Object num, final Object str) {
        // Specification says comparing a number to string should be done as "equals(num, JSType.toNumber(str))". We
        // can short circuit it to this as we know that "num" is a number, so it'll end up being a number-number
        // comparison.
        return ((Number)num).doubleValue() == JSType.toNumber(str.toString());
    }

    private static boolean equalBooleanToAny(final Object bool, final Object any) {
        return equals(JSType.toNumber((Boolean)bool), any);
    }

    private static boolean equalWrappedPrimitiveToObject(final Object numOrStr, final Object any) {
        return equals(numOrStr, JSType.toPrimitive(any));
    }

    /**
     * ECMA 11.9.4 - The strict equal operator (===) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are equal
     */
    public static boolean EQ_STRICT(final Object x, final Object y) {
        return strictEquals(x, y);
    }

    /**
     * ECMA 11.9.5 - The strict non equal operator (!==) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if objects are not equal
     */
    public static boolean NE_STRICT(final Object x, final Object y) {
        return !EQ_STRICT(x, y);
    }

    /** ECMA 11.9.6 The Strict Equality Comparison Algorithm */
    private static boolean strictEquals(final Object x, final Object y) {
        // NOTE: you might be tempted to do a quick x == y comparison. Remember, though, that any Double object having
        // NaN value is not equal to itself by value even though it is referentially.

        final JSType xType = JSType.ofNoFunction(x);
        final JSType yType = JSType.ofNoFunction(y);

        if (xType != yType) {
            return false;
        }

        return equalSameTypeValues(x, y, xType);
    }

    /**
     * ECMA 11.8.6 - The in operator - generic implementation
     *
     * @param property property to check for
     * @param obj object in which to check for property
     *
     * @return true if objects are equal
     */
    public static boolean IN(final Object property, final Object obj) {
        final JSType rvalType = JSType.ofNoFunction(obj);

        if (rvalType == JSType.OBJECT) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)obj).has(property);
            }

            if (obj instanceof JSObject) {
                return ((JSObject)obj).hasMember(Objects.toString(property));
            }

            final Object key = JSType.toPropertyKey(property);

            if (obj instanceof StaticClass) {
                final Class<?> clazz = ((StaticClass) obj).getRepresentedClass();
                return BeansLinker.getReadableStaticPropertyNames(clazz).contains(Objects.toString(key))
                    || BeansLinker.getStaticMethodNames(clazz).contains(Objects.toString(key));
            } else {
                if (obj instanceof Map && ((Map) obj).containsKey(key)) {
                    return true;
                }

                final int index = ArrayIndex.getArrayIndex(key);
                if (index >= 0) {
                    if (obj instanceof List && index < ((List) obj).size()) {
                        return true;
                    }
                    if (obj.getClass().isArray() && index < Array.getLength(obj)) {
                        return true;
                    }
                }

                return BeansLinker.getReadableInstancePropertyNames(obj.getClass()).contains(Objects.toString(key))
                    || BeansLinker.getInstanceMethodNames(obj.getClass()).contains(Objects.toString(key));
            }
        }

        throw typeError("in.with.non.object", rvalType.toString().toLowerCase(Locale.ENGLISH));
    }

    /**
     * ECMA 11.8.6 - The strict instanceof operator - generic implementation
     *
     * @param obj first object to compare
     * @param clazz type to check against
     *
     * @return true if {@code obj} is an instanceof {@code clazz}
     */
    public static boolean INSTANCEOF(final Object obj, final Object clazz) {
        // ES2015 12.9.4: Symbol.hasInstance takes over the operator entirely when
        // present. The flag keeps the ordinary path free of a symbol lookup until
        // some script actually installs one.
        if (WellKnownSymbols.hasInstanceInstalled() && clazz instanceof ScriptObject target) {
            final Object hasInstance = target.get(NativeSymbol.hasInstance);
            if (hasInstance instanceof ScriptFunction handler) {
                return JSType.toBoolean(apply(handler, clazz, obj));
            }
        }

        if (clazz instanceof ScriptFunction) {
            if (obj instanceof ScriptObject) {
                return ((ScriptObject)clazz).isInstance((ScriptObject)obj);
            }
            return false;
        }

        if (clazz instanceof StaticClass) {
            return ((StaticClass)clazz).getRepresentedClass().isInstance(obj);
        }

        if (clazz instanceof JSObject) {
            return ((JSObject)clazz).isInstance(obj);
        }

        // provide for reverse hook
        if (obj instanceof JSObject) {
            return ((JSObject)obj).isInstanceOf(clazz);
        }

        throw typeError("instanceof.on.non.object");
    }

    /**
     * ECMA 11.8.1 - The less than operator ({@literal <}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than y
     */
    public static boolean LT(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) < 0 :
            JSType.toNumber(px) < JSType.toNumber(py);
    }

    private static boolean areBothString(final Object x, final Object y) {
        return isString(x) && isString(y);
    }

    /**
     * ECMA 11.8.2 - The greater than operator ({@literal >}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than y
     */
    public static boolean GT(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) > 0 :
            JSType.toNumber(px) > JSType.toNumber(py);
    }

    /**
     * ECMA 11.8.3 - The less than or equal operator ({@literal <=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is less than or equal to y
     */
    public static boolean LE(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) <= 0 :
            JSType.toNumber(px) <= JSType.toNumber(py);
    }

    /**
     * ECMA 11.8.4 - The greater than or equal operator ({@literal >=}) - generic implementation
     *
     * @param x first object to compare
     * @param y second object to compare
     *
     * @return true if x is greater than or equal to y
     */
    public static boolean GE(final Object x, final Object y) {
        final Object px = JSType.toPrimitive(x, Number.class);
        final Object py = JSType.toPrimitive(y, Number.class);

        return areBothString(px, py) ? px.toString().compareTo(py.toString()) >= 0 :
            JSType.toNumber(px) >= JSType.toNumber(py);
    }

    /**
     * Tag a reserved name as invalidated - used when someone writes
     * to a property with this name - overly conservative, but link time
     * is too late to apply e.g. apply-&gt;call specialization
     * @param name property name
     */
    public static void invalidateReservedBuiltinName(final String name) {
        final Context context = Context.getContext();
        final SwitchPoint sp = context.getBuiltinSwitchPoint(name);
        context.getLogger(ApplySpecialization.class).info("Overwrote special name '" + name +"' - invalidating switchpoint");
        SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
    }

    /**
     * ES6 12.2.9.3 Runtime Semantics: GetTemplateObject(templateLiteral).
     *
     * @param rawStrings array of template raw values
     * @param cookedStrings array of template values
     * @return template object
     */
    public static ScriptObject GET_TEMPLATE_OBJECT(final Object rawStrings, final Object cookedStrings) {
        final ScriptObject template = (ScriptObject)cookedStrings;
        final ScriptObject rawObj = (ScriptObject)rawStrings;
        assert rawObj.getArray().length() == template.getArray().length();
        template.addOwnProperty("raw", Property.NOT_WRITABLE | Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE, rawObj.freeze());
        template.freeze();
        return template;
    }

    /**
     * An iteration that can be told it will not be asked for more.
     *
     * The ES2015 protocol lets an iterator clean up when a consumer stops early -
     * a generator runs its finally blocks - and java.util.Iterator has nowhere to
     * say that, so the adapter carries it here.
     */
    public interface CloseableIterator extends Iterator<Object> {
        /** ES2015 7.4.6 IteratorClose, if the iteration has not already finished. */
        void close();
    }

    /**
     * ES2015 7.4.6 IteratorClose, for a destructuring pattern that stopped before
     * its iterator was done.
     *
     * @param iterator from {@link #GET_ITERATOR}
     * @return undefined
     */
    public static Object ITERATOR_CLOSE(final Object iterator) {
        if (iterator instanceof CloseableIterator closeable) {
            closeable.close();
        }
        return UNDEFINED;
    }

    /**
     * ES6 7.4.1 GetIterator, for array destructuring and spread.
     *
     * The iterator is kept as a {@code java.util.Iterator}, which is what
     * for-of already uses, so both go through the same protocol implementation.
     *
     * @param iterable the value being destructured or spread
     * @return an iterator over it
     */
    public static Object GET_ITERATOR(final Object iterable) {
        final Iterator<?> iterator = toES6Iterator(iterable);
        if (iterator instanceof CloseableIterator) {
            return iterator;
        }
        // A Java array, list or map iterated as an extension: there is no script
        // iterator to close, but the result still has to be recognisable as one
        // already obtained, because a for-of loop hands it back to
        // {@link #toES6Iterator} when it starts.
        return new CloseableIterator() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next();
            }

            @Override
            public void close() {
                // nothing to tell a Java iterator
            }
        };
    }

    /**
     * One step of an array destructuring pattern.
     *
     * An exhausted iterator yields undefined rather than throwing: a pattern
     * may bind more names than the iterable has elements, and the extra ones
     * are simply undefined.
     *
     * @param iterator from {@link #GET_ITERATOR}
     * @return the next value, or undefined
     */
    public static Object ITERATOR_NEXT(final Object iterator) {
        final Iterator<?> iter = (Iterator<?>)iterator;
        return iter.hasNext() ? iter.next() : UNDEFINED;
    }

    /**
     * The tail of an array destructuring pattern - the {@code [a, ...rest]} case.
     *
     * @param iterator from {@link #GET_ITERATOR}
     * @return a new array holding everything the iterator has left
     */
    public static Object ITERATOR_REST(final Object iterator) {
        final Iterator<?> iter = (Iterator<?>)iterator;
        final List<Object> rest = new ArrayList<>();
        while (iter.hasNext()) {
            rest.add(iter.next());
        }
        return Global.allocate(rest.toArray());
    }

    /**
     * ES6 7.2.1 RequireObjectCoercible, the first step of object destructuring.
     *
     * @param value the value being destructured
     * @return the value itself, when it can be coerced to an object
     */
    public static Object REQUIRE_OBJECT_COERCIBLE(final Object value) {
        if (value == null || value == UNDEFINED) {
            throw typeError("cant.get.property", "of", safeToString(value));
        }
        return value;
    }

    /**
     * The array a rest parameter collects: everything passed beyond the declared
     * parameters.
     *
     * @param arguments the callee's argument array
     * @param from      the number of parameters declared before the rest one
     * @return a new array of the remaining arguments, empty if there are none
     */
    public static Object REST_ARGUMENTS(final Object arguments, final Object from) {
        final Object[] args = (Object[])arguments;
        final int start = JSType.toInt32(from);
        if (args == null || start >= args.length) {
            return Global.allocate(ScriptRuntime.EMPTY_ARRAY);
        }
        return Global.allocate(Arrays.copyOfRange(args, start, args.length));
    }

    /**
     * A new, empty array for a literal or argument list that contains a spread
     * element, whose length is not known until it runs.
     *
     * @return the array
     */
    public static Object SPREAD_NEW() {
        return Global.allocate(ScriptRuntime.EMPTY_ARRAY);
    }

    /**
     * Appends one element to an array under construction.
     *
     * @param array the array being built
     * @param value the element
     * @return the array, so that appends chain on the stack
     */
    public static Object SPREAD_APPEND(final Object array, final Object value) {
        final NativeArray target = (NativeArray)array;
        target.set((int)target.getArray().length(), value, 0);
        return target;
    }

    /**
     * Appends everything an iterable yields - the {@code ...xs} itself.
     *
     * @param array    the array being built
     * @param iterable the value being spread
     * @return the array, so that appends chain on the stack
     */
    public static Object SPREAD_APPEND_ALL(final Object array, final Object iterable) {
        final NativeArray target = (NativeArray)array;
        final Iterator<?> iterator = toES6Iterator(iterable);
        while (iterator.hasNext()) {
            target.set((int)target.getArray().length(), iterator.next(), 0);
        }
        return target;
    }

    /**
     * The argument array for a call whose argument list contains a spread.
     *
     * @param array the collected arguments, as a script array
     * @return them as a Java array, ready for a call
     */
    public static Object[] SPREAD_TO_ARGUMENTS(final Object array) {
        final NativeArray collected = (NativeArray)array;
        final int length = (int)collected.getArray().length();
        final Object[] arguments = new Object[length];
        for (int i = 0; i < length; i++) {
            arguments[i] = collected.get(i);
        }
        return arguments;
    }

    /**
     * A call whose argument list contains a spread, so its arity is only known
     * at run time.
     *
     * @param function the callee
     * @param thiz     the this value
     * @param argsArray the collected arguments
     * @return the call's result
     */
    public static Object SPREAD_CALL(final Object function, final Object thiz, final Object argsArray) {
        final Object[] args = SPREAD_TO_ARGUMENTS(argsArray);
        if (function instanceof ScriptFunction scriptFunction) {
            return apply(scriptFunction, thiz, args);
        }
        if (function instanceof ScriptObjectMirror mirror) {
            return mirror.call(thiz, args);
        }
        if (Bootstrap.isCallable(function)) {
            try {
                // the general path: one invoker per arity, built on demand
                final Object[] callArgs = new Object[args.length + 2];
                callArgs[0] = function;
                callArgs[1] = thiz;
                System.arraycopy(args, 0, callArgs, 2, args.length);
                final Class<?>[] types = new Class<?>[callArgs.length];
                java.util.Arrays.fill(types, Object.class);
                return Bootstrap.createDynamicCallInvoker(Object.class, types)
                        .invokeWithArguments(callArgs);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
        throw typeError("not.a.function", safeToString(function));
    }

    /**
     * A method call whose argument list contains a spread.
     *
     * The receiver is evaluated once and used both to find the method and as its
     * this value, which is why this reads the property rather than taking a
     * function already on the stack.
     *
     * @param base      the receiver
     * @param key       the property naming the method
     * @param argsArray the collected arguments
     * @return the call's result
     */
    public static Object SPREAD_CALL_METHOD(final Object base, final Object key, final Object argsArray) {
        if (base == null || base == UNDEFINED) {
            throw typeError("cant.get.property", safeToString(key), safeToString(base));
        }
        final Object holder = base instanceof ScriptObject
                ? base
                : JSType.toScriptObject(Context.getGlobal(), base);
        if (!(holder instanceof ScriptObject scriptObject)) {
            throw typeError("not.a.function", safeToString(key));
        }
        return SPREAD_CALL(scriptObject.get(key), base, argsArray);
    }

    /**
     * A constructor call whose argument list contains a spread.
     *
     * @param function  the constructor
     * @param argsArray the collected arguments
     * @return the new object
     */
    public static Object SPREAD_CONSTRUCT(final Object function, final Object argsArray) {
        if (!(function instanceof ScriptFunction scriptFunction)) {
            throw typeError("not.a.constructor", safeToString(function));
        }
        return construct(scriptFunction, SPREAD_TO_ARGUMENTS(argsArray));
    }

    /** A class element defined on the constructor rather than the prototype. */
    public static final int CLASS_ELEMENT_STATIC = 1;
    /** A class element that is a getter. */
    public static final int CLASS_ELEMENT_GETTER = 2;
    /** A class element that is a setter. */
    public static final int CLASS_ELEMENT_SETTER = 4;

    /**
     * Builds a class.
     *
     * The whole definition arrives as one call rather than as inline property
     * assignments because the ordering rules are not those of an object literal:
     * computed keys have to be evaluated in source order interleaved with the
     * methods, and class methods are non-enumerable, which the object literal
     * path cannot express.
     *
     * @param constructor the constructor function, already created
     * @param heritage    what the class extends, or undefined
     * @param derived     whether an extends clause was written at all, which is
     *                    not the same as heritage being non-null: "extends null"
     *                    is legal and still makes the class derived
     * @param elements    key, flags and value for each element, flattened
     * @return the constructor
     */
    public static Object DEFINE_CLASS(final Object constructor, final Object heritage, final Object derived,
            final Object elements) {
        final ScriptFunction ctor = (ScriptFunction)constructor;
        final ScriptObject prototype = (ScriptObject)ctor.getPrototype();

        if (JSType.toBoolean(derived)) {
            if (heritage == null || heritage == UNDEFINED) {
                // "class C extends null" - the prototype chain simply ends
                prototype.setProto(null);
            } else if (heritage instanceof ScriptFunction parent) {
                // ES2015 14.5.14 step 6.f: the superclass has to be a
                // constructor. An arrow function, a generator, a method and an
                // accessor are all callable and none of them is one.
                if (!parent.isConstructor()) {
                    throw typeError("cant.inherit.from", safeToString(heritage));
                }
                final Object parentPrototype = parent.getPrototype();
                if (parentPrototype != null && parentPrototype != UNDEFINED
                        && !(parentPrototype instanceof ScriptObject)) {
                    throw typeError("cant.inherit.from", safeToString(heritage));
                }
                prototype.setProto(parentPrototype instanceof ScriptObject p ? p : null);
                ctor.setProto(parent);
            } else {
                throw typeError("cant.inherit.from", safeToString(heritage));
            }
        }

        // super in the constructor resolves above the prototype, just as in a method
        ctor.setHomeObject(prototype);

        final NativeArray flattened = (NativeArray)elements;
        final int length = (int)flattened.getArray().length();
        for (int i = 0; i < length; i += 3) {
            final Object key = flattened.get(i);
            final int flags = JSType.toInt32(flattened.get(i + 1));
            final Object value = flattened.get(i + 2);
            final ScriptObject target = (flags & CLASS_ELEMENT_STATIC) != 0 ? ctor : prototype;
            if (value instanceof ScriptFunction method) {
                // super in this method resolves above whichever object it is
                // defined on, so the home object is recorded now
                method.setHomeObject(target);
                // ES2015 14.5.14 step 20: a method with a computed key is named
                // after it, which is only known now that the key is evaluated
                final int accessor = flags & (CLASS_ELEMENT_GETTER | CLASS_ELEMENT_SETTER);
                ScriptFunction.setFunctionName(accessor == 0 ? key
                        : prefixed(accessor == CLASS_ELEMENT_GETTER ? "get " : "set ", key), value);
            }
            defineClassElement(target, key, flags, value);
        }

        // unlike a function's, a class's prototype property is not writable
        final ScriptObject prototypeDescriptor = Global.newEmptyInstance();
        prototypeDescriptor.set("writable", false, 0);
        prototypeDescriptor.set("enumerable", false, 0);
        prototypeDescriptor.set("configurable", false, 0);
        ctor.defineOwnProperty("prototype", prototypeDescriptor, true);

        return ctor;
    }

    /** An accessor's name carries "get " or "set " in front of the key's. */
    private static Object prefixed(final String prefix, final Object key) {
        return key instanceof Symbol symbol ? prefix + "[" + symbol.getName() + "]" : prefix + JSType.toString(key);
    }

    private static void defineClassElement(final ScriptObject target, final Object key, final int flags,
            final Object value) {
        final Object propertyKey = key instanceof Symbol ? key : JSType.toPropertyKey(key);

        // ES2015 14.5.14 step 16: a static element called name or length is the
        // class's own, and the specification skips the step that would have
        // named the constructor. Both are accessors with no setter here, which
        // defineOwnProperty can read but not overwrite, so the built-in one goes
        // first and the element defines a fresh property in its place.
        if (target instanceof ScriptFunction
                && ("name".equals(propertyKey) || "length".equals(propertyKey))) {
            final Property existing = target.getMap().findProperty(propertyKey);
            if (existing != null) {
                target.deleteOwnProperty(existing);
            }
        }

        final ScriptObject descriptor = Global.newEmptyInstance();

        if ((flags & CLASS_ELEMENT_GETTER) != 0) {
            descriptor.set("get", value, 0);
        } else if ((flags & CLASS_ELEMENT_SETTER) != 0) {
            descriptor.set("set", value, 0);
        } else {
            descriptor.set("value", value, 0);
            descriptor.set("writable", true, 0);
        }
        // class elements are non-enumerable; a get/set pair written as two
        // elements merges into one property, which defineOwnProperty does for us
        descriptor.set("enumerable", false, 0);
        descriptor.set("configurable", true, 0);

        target.defineOwnProperty(propertyKey, descriptor, true);
    }

    /**
     * The object {@code super} resolves against: the prototype of the object the
     * running method was defined on.
     *
     * It comes from the method rather than from the receiver, because super is
     * fixed where the method was written - a method borrowed by another object
     * still calls the same super.
     */
    private static ScriptObject superBase(final Object callee) {
        if (!(callee instanceof ScriptFunction function)) {
            throw typeError("no.super");
        }
        final ScriptObject home = function.getHomeObject();
        if (home == null) {
            throw typeError("no.super");
        }
        return home.getProto();
    }

    /**
     * {@code super.x} and {@code super[x]}.
     *
     * @param callee the running method
     * @param key    the property
     * @return its value, looked up above the method's home object
     */
    public static Object SUPER_GET(final Object callee, final Object key) {
        final ScriptObject base = superBase(callee);
        return base == null ? UNDEFINED : base.get(key);
    }

    /**
     * {@code super.x = value} and {@code super[x] = value}, which per ES2015
     * assigns on the receiver rather than on the super object.
     *
     * @param callee the running method
     * @param thiz   the receiver
     * @param key    the property
     * @param value  the value
     * @return the value
     */
    public static Object SUPER_SET(final Object callee, final Object thiz, final Object key, final Object value) {
        superBase(callee);
        if (thiz instanceof ScriptObject receiver) {
            receiver.set(key, value, NashornCallSiteDescriptor.CALLSITE_STRICT);
        }
        return value;
    }

    /**
     * {@code super.m(...)}, which runs the inherited method with the current
     * receiver.
     *
     * @param callee    the running method
     * @param key       the method name
     * @param thiz      the receiver
     * @param argsArray the arguments
     * @return the call's result
     */
    public static Object SUPER_CALL(final Object callee, final Object key, final Object thiz,
            final Object argsArray) {
        final ScriptObject base = superBase(callee);
        final Object method = base == null ? UNDEFINED : base.get(key);
        return SPREAD_CALL(method, thiz, argsArray);
    }

    /**
     * {@code super(...)} in a derived constructor.
     *
     * Nashorn allocates the object before the constructor runs, so rather than
     * constructing a second one this calls the parent constructor on the object
     * that already exists. That is the documented limit of this implementation:
     * a base class that would return an exotic object - Array, Map - does not
     * get to do so for a subclass.
     *
     * @param callee    the running constructor
     * @param thiz      the object being constructed
     * @param argsArray the arguments
     * @return undefined
     */
    public static Object SUPER_CONSTRUCT(final Object callee, final Object thiz, final Object argsArray) {
        if (!(callee instanceof ScriptFunction constructor)) {
            throw typeError("no.super");
        }
        final ScriptObject parent = constructor.getProto();
        if (!(parent instanceof ScriptFunction parentConstructor)) {
            throw typeError("no.super");
        }
        apply(parentConstructor, thiz, SPREAD_TO_ARGUMENTS(argsArray));
        // ES2015 12.3.5.1 step 7: super() evaluates to the this binding it makes,
        // which is the object being built - not whatever the parent returned
        return thiz;
    }

    /**
     * {@code new.target}: the constructor a function is being invoked as, or
     * undefined when it is being called normally.
     *
     * This is inferred from the receiver rather than passed down, because
     * Nashorn's calling convention has nowhere to carry it. That is exact for
     * "new F()" and for a class constructed directly, and it is a documented
     * approximation in two places: inside a base constructor reached through
     * super() it reports that base rather than the most derived constructor,
     * and Reflect.construct's newTarget argument is not honoured.
     *
     * @param callee the running function
     * @param thiz   its receiver
     * @return the constructor, or undefined
     */
    public static Object NEW_TARGET(final Object callee, final Object thiz) {
        if (!(callee instanceof ScriptFunction function) || !(thiz instanceof ScriptObject receiver)) {
            return UNDEFINED;
        }
        final Object prototype = function.getPrototype();
        if (!(prototype instanceof ScriptObject expected)) {
            return UNDEFINED;
        }
        boolean constructing = false;
        for (ScriptObject proto = receiver.getProto(); proto != null; proto = proto.getProto()) {
            if (proto == expected) {
                constructing = true;
                break;
            }
        }
        if (!constructing) {
            return UNDEFINED;
        }
        // The receiver was allocated from new.target's prototype (ES2015 9.2.2
        // step 5), so the object being built names it: a derived constructor
        // running super() sees the derived class, not its own function, and
        // Reflect.construct's third argument is recovered the same way.
        final Object target = receiver.getProto().get("constructor");
        return target instanceof ScriptFunction ? target : function;
    }

    /**
     * The first thing a generator function does.
     *
     * A generator function is compiled as an ordinary function and serves two
     * roles. Called normally it must not run its body at all, but hand back a
     * generator object; the body then runs later, on the generator's own thread,
     * by calling the very same function again. This tells the two apart: the
     * outer call gets a generator back and returns it immediately, and the call
     * made from the generator's thread gets undefined and falls through into the
     * body.
     *
     * @param callee the generator function itself
     * @param self   its this value
     * @param args   its arguments
     * @return a new generator object, or undefined when the body should run
     */
    public static Object GENERATOR_ENTER(final Object callee, final Object self, final Object args) {
        if (GeneratorSupport.entering()) {
            return UNDEFINED;
        }
        final Global global = Context.getGlobal();
        final Object[] arguments = args instanceof Object[] array ? array : ScriptRuntime.EMPTY_ARRAY;
        final GeneratorSupport support = new GeneratorSupport((ScriptFunction)callee, self, arguments, global);
        global.registerGenerator(support);
        return new NativeGenerator(support, global, generatorPrototype((ScriptFunction)callee, global));
    }

    /**
     * {@code yield value} - suspends the generator body until it is resumed.
     *
     * @param value the value to hand to whoever is advancing the generator
     * @return the value the generator is resumed with
     */
    /**
     * ES2015 15.2.1.17: a module's environment, handed over by its own body.
     *
     * It is the one moment at which the imports can be installed - after the
     * scope exists and before any of the body has run.
     *
     * @param scope the module's scope object
     * @return undefined
     */
    public static Object MODULE_SCOPE(final Object scope) {
        if (scope instanceof ScriptObject sobj) {
            ModuleRecord.starting(sobj);
        }
        return UNDEFINED;
    }

    /**
     * ES2015 9.2.2 step 13: what a derived class constructor returns.
     *
     * An object is the result. Undefined means the constructor is handing back
     * the object super() gave it, so the binding has to have been made. Anything
     * else - a number, a string, null - is a TypeError, where a base
     * constructor would simply have ignored it.
     *
     * @param value       the constructor's completion value
     * @param thisBinding whether super() has run
     * @param thiz        the object being built
     * @return what the construction evaluates to
     */
    public static Object DERIVED_RETURN(final Object value, final Object thisBinding, final Object thiz) {
        if (value instanceof ScriptObject) {
            return value;
        }
        if (value == UNDEFINED) {
            return REQUIRE_THIS_INITIALIZED(thisBinding, thiz);
        }
        throw typeError("derived.constructor.return", safeToString(value));
    }

    /**
     * The value a derived class constructor's this binding holds before super()
     * has made it.
     *
     * It is a call rather than the undefined literal so that the variable is
     * typed as an object from the start. The binding is written by the code
     * generator, next to the super() that makes it, and a slot the local
     * variable type calculation has only ever seen hold undefined cannot take
     * an object.
     *
     * @return undefined
     */
    public static Object UNINITIALIZED_THIS() {
        return UNDEFINED;
    }

    /**
     * ES2015 8.1.1.3.4 GetThisBinding: {@code this} inside a derived class
     * constructor, which does not exist until super() has run.
     *
     * The specification models this as a binding in a temporal dead zone rather
     * than as an object that is not ready, and a script can tell the difference:
     * reading it early is a ReferenceError, not a look at a half-built object.
     *
     * @param initialized whether super() has returned
     * @param thiz        the object being built
     * @return the object
     */
    public static Object REQUIRE_THIS_INITIALIZED(final Object binding, final Object thiz) {
        if (binding == UNDEFINED) {
            throw referenceError("this.before.super");
        }
        return thiz;
    }

    /**
     * ES2015 8.1.1.3.1 BindThisValue, run when super() returns.
     *
     * @param initialized whether super() has already run in this constructor
     * @param result      the super call's result, evaluated before this is called
     * @return true, to be stored back into the flag
     */
    public static Object BIND_THIS(final Object binding, final Object result) {
        if (binding != UNDEFINED) {
            throw referenceError("super.called.twice");
        }
        // 12.3.5.1 step 7: super() evaluates to the object it bound
        return result;
    }

    /**
     * ES2015 9.2.2: a class constructor may only be reached with new.
     *
     * The test is the same one new.target answers, so it inherits the same
     * approximation - which is exact for a plain call, the case this guards.
     *
     * @param callee the constructor
     * @param thiz   its receiver
     * @return undefined when the call is legitimate
     */
    public static Object REQUIRE_NEW(final Object callee, final Object thiz) {
        if (NEW_TARGET(callee, thiz) == UNDEFINED) {
            throw typeError("constructor.requires.new",
                    callee instanceof ScriptFunction function ? function.getName() : safeToString(callee));
        }
        return UNDEFINED;
    }

    /**
     * The object a generator gets as its prototype.
     *
     * ES2015 25.2.4: it is the generator function's own "prototype" property, and
     * that object in turn inherits from %GeneratorPrototype%, which is where
     * next, return and throw live. Nashorn builds an ordinary prototype object
     * for every function, so the chain is completed here, the first time one of
     * this function's generators is made.
     */
    private static ScriptObject generatorPrototype(final ScriptFunction generatorFunction, final Global global) {
        final Object own = generatorFunction.getPrototype();
        if (!(own instanceof ScriptObject prototype)) {
            return global.getGeneratorPrototype();
        }
        if (prototype.getProto() != global.getGeneratorPrototype()) {
            prototype.setProto(global.getGeneratorPrototype());
        }
        return prototype;
    }

    /**
     * {@code yield* iterable} - yields everything the iterable produces.
     *
     * On a thread this is just a loop; there is no state machine to thread the
     * delegation through. Values sent in with next() are not forwarded to the
     * inner iterator, and the delegated iterator's own return value is not
     * propagated - both are documented gaps.
     *
     * @param iterable what to delegate to
     * @return undefined
     */
    public static Object YIELD_STAR(final Object iterable) {
        final Iterator<?> iterator = toES6Iterator(iterable);
        while (iterator.hasNext()) {
            YIELD(iterator.next());
        }
        return UNDEFINED;
    }

    public static Object YIELD(final Object value) {
        final GeneratorSupport generator = GeneratorSupport.running();
        if (generator == null) {
            throw typeError("yield.outside.generator");
        }
        return generator.yield(value);
    }
}
