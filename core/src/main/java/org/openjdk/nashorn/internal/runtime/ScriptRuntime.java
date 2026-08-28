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
import java.lang.invoke.MethodType;
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
import org.openjdk.nashorn.internal.objects.ArrayBufferView;
import org.openjdk.nashorn.internal.objects.Global;
import org.openjdk.nashorn.internal.objects.NativeGenerator;
import org.openjdk.nashorn.internal.objects.NativeProxy;
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

    /** Merge a scope into its prototype, keeping what the scope binds lexically */
    public static final Call MERGE_EVAL_SCOPE = staticCallNoLookup(ScriptRuntime.class, "mergeEvalScope", ScriptObject.class, ScriptObject.class);

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

    /** Reads one declared parameter out of a variable-arity function's arguments. */
    public static final Call GET_VARARG = staticCall(MethodHandles.lookup(), ScriptRuntime.class, "getVararg", Object.class, Object[].class, int.class);

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
     *
     * ES2015 19.1.3.6 replaced the ES5.1 [[Class]] lookup with a two-part rule:
     * a short list of built-in kinds still names itself, everything else is
     * "Object", and a string-valued {@code Symbol.toStringTag} on the object
     * overrides whichever of the two applies. The list is closed - a Map or a
     * Promise is an "Object" as far as this operation can tell, and reads as
     * "[object Map]" only because its prototype carries the symbol.
     *
     * <p>Which kind it is has to be decided before the symbol is read, because
     * reading it can run script: a proxy for an array that its own
     * {@code @@toStringTag} getter revokes is still an array here.
     *
     * @param self reference
     * @return string representation as object
     */
    public static String builtinObjectToString(final Object self) {
        if (self == UNDEFINED) {
            return "[object Undefined]";
        }
        if (self == null) {
            return "[object Null]";
        }

        // ToObject, so that a primitive is named by its wrapper's prototype and
        // can be given a tag through it
        final Object obj = Global.toObject(self);
        if (!(obj instanceof ScriptObject sobj)) {
            return "[object " + foreignTag(obj) + ']';
        }

        final String builtinTag = builtinTag(sobj);
        final Object tag = sobj.get(NativeSymbol.toStringTag);
        return "[object " + (isString(tag) ? tag : builtinTag) + ']';
    }

    /**
     * The built-in kind {@code Object.prototype.toString} reports for an object
     * that has no {@code Symbol.toStringTag}, per the list in ES2015 19.1.3.6.
     *
     * <p>The kinds named there are exactly the ones ES5.1 already had, less the
     * ones ES2015 took the name away from: a Map or a Promise reads as
     * "[object Map]" only because its prototype carries the symbol, and reads as
     * "[object Object]" once that is deleted. Everything Nashorn adds of its own
     * - the global, a Java package, a JSAdapter - keeps naming itself, since no
     * specification has an opinion about those.
     */
    private static String builtinTag(final ScriptObject sobj) {
        if (sobj instanceof NativeProxy proxy && proxy.isRevoked()) {
            // IsArray, which 19.1.3.6 performs first, throws for one of these
            throw typeError("proxy.revoked");
        }
        if (sobj instanceof ArrayBufferView) {
            return "Object";
        }
        final String className = sobj.getClassName();
        return switch (className) {
            case "Math", "JSON", "Symbol", "Map", "Set", "WeakMap", "WeakSet", "Promise",
                 "ArrayBuffer", "DataView", "Generator", "Iterator",
                 "ArrayIterator", "StringIterator", "MapIterator", "SetIterator" -> "Object";
            default -> className;
        };
    }

    /** The name for something that is not a script object at all. */
    private static String foreignTag(final Object obj) {
        if (obj instanceof JSObject jsObj) {
            return jsObj.isFunction() ? "Function" : jsObj.isArray() ? "Array" : jsObj.getClassName();
        }
        // Nashorn extension: a Java object is named by its class
        return obj.getClass().getName();
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
                        final Object result = nextInvoker.getInvoker().invokeExact(next, iterator, (Object) null);
                        // ES2015 7.4.2 step 3: what next answers with has to be
                        // an object. Reading "done" off a primitive answers
                        // undefined, which is a loop that never ends.
                        if (JSType.isPrimitive(result)) {
                            exhausted = true;
                            throw typeError("not.an.object", safeToString(result));
                        }
                        return result;
                    }
                } catch (final RuntimeException|Error r) {
                    // ES2015 7.4.6 is only reached when the iteration is being
                    // abandoned by whoever was driving it. An iterator that
                    // threw of its own accord is done - 13.7.5.13 sets its
                    // [[done]] before letting the throw out - and is not asked
                    // to close on top of that.
                    exhausted = true;
                    throw r;
                } catch (final Throwable t) {
                    exhausted = true;
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
                    exhausted = true;
                    throw r;
                } catch (final Throwable t) {
                    exhausted = true;
                    throw new RuntimeException(t);
                }
            }

            @Override
            public void close(final boolean report) {
                if (exhausted) {
                    return;
                }
                exhausted = true;
                // ES2015 7.4.6 IteratorClose: tell an unfinished iterator that
                // nobody will ask it for more, so a generator can run its finally
                // blocks.
                if (!(iterator instanceof ScriptObject sobj)) {
                    return;
                }
                final Object close;
                try {
                    // step 4 GetMethod, which can throw all by itself when
                    // "return" is an accessor
                    close = sobj.get("return");
                } catch (final RuntimeException e) {
                    if (report) {
                        throw e;
                    }
                    return;
                }
                if (close == null || close == UNDEFINED) {
                    // step 5.b: an iterator that does not say goodbye
                    return;
                }
                if (!Bootstrap.isCallable(close)) {
                    if (report) {
                        throw typeError("not.a.function", safeToString(close));
                    }
                    return;
                }
                final Object result;
                try {
                    result = call(close, iterator, EMPTY_ARRAY);
                } catch (final RuntimeException e) {
                    if (report) {
                        throw e;
                    }
                    return;
                }
                // step 8: return() answers with a result object, like next()
                if (report && JSType.isPrimitive(result)) {
                    throw typeError("not.an.object", safeToString(result));
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
                    // IteratorValue, which 13.7.5.13 treats the same way
                    exhausted = true;
                    throw r;
                } catch (final Throwable t) {
                    exhausted = true;
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
     * Merge what an eval declared with var into the scope that called it, and
     * keep the rest.
     *
     * ES2015 18.2.1.3 gives eval code two environments: its var declarations
     * belong to the caller's variable environment, which is what the merge
     * makes them, while its let, const and class declarations belong to an
     * environment of the eval's own, which goes away when it returns. So the
     * scope that is returned - the one the eval code goes on to run against -
     * is therefore a fresh one holding the lexical bindings, standing between
     * the eval code and the scope that called it.
     *
     * @param scope the eval program's scope
     * @return the scope the eval code runs against
     */
    public static ScriptObject mergeEvalScope(final ScriptObject scope) {
        final ScriptObject parentScope = scope.getProto();
        final Property[] all = scope.getMap().getProperties();
        int lexicalCount = 0;
        for (final Property property : all) {
            if (property.isLexicalBinding()) {
                lexicalCount++;
            }
        }
        if (lexicalCount == 0) {
            // an eval that binds nothing lexically, which is most of them, is
            // the ordinary merge and pays nothing for this one being different
            parentScope.addBoundProperties(scope, all);
            return parentScope;
        }

        final List<Property> vars = new ArrayList<>(all.length - lexicalCount);
        final List<Property> lexical = new ArrayList<>(lexicalCount);
        for (final Property property : all) {
            (property.isLexicalBinding() ? lexical : vars).add(property);
        }
        parentScope.addBoundProperties(scope, vars.toArray(new Property[0]));
        // the environment of the eval's own, holding what it bound lexically
        // and nothing else, so that what it bound with var is still read and
        // written where it was merged to
        final ScriptObject lexicalScope = new Scope(parentScope, PropertyMap.newMap(Scope.class));
        lexicalScope.addBoundProperties(scope, lexical.toArray(new Property[0]));
        return lexicalScope;
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
    /**
     * Constructs with anything that can be used with new, whatever it is.
     *
     * @param constructor the constructor
     * @param args        its arguments
     * @return the object it made
     */
    public static Object newInstance(final Object constructor, final Object[] args) {
        if (constructor instanceof ScriptFunction function) {
            return construct(function, args);
        }
        try {
            final Object[] callArgs = new Object[args.length + 1];
            callArgs[0] = constructor;
            System.arraycopy(args, 0, callArgs, 1, args.length);
            final Class<?>[] types = new Class<?>[callArgs.length];
            java.util.Arrays.fill(types, Object.class);
            return Bootstrap.createDynamicInvoker("", NashornCallSiteDescriptor.NEW, Object.class, types)
                    .invokeWithArguments(callArgs);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

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
     * One declared parameter of a variable-arity function.
     *
     * A function is compiled variable arity when it has a rest parameter, or an
     * arguments object, or more parameters than a call site can carry, and its
     * declared parameters are then read out of the array it was called with.
     * There may be fewer arguments than parameters - which is a missing
     * parameter, not an error.
     *
     * @param arguments what the function was called with
     * @param index     which parameter to read
     * @return the argument, or undefined if it was not passed
     */
    public static Object getVararg(final Object[] arguments, final int index) {
        return arguments != null && index < arguments.length ? arguments[index] : UNDEFINED;
    }

    /**
     * A read of a binding that has not been initialised yet.
     *
     * ES2015 9.2.12: in a function whose parameter list has expressions in it,
     * every parameter binding is created before any initialiser runs and is
     * initialised in order, so "function (a = a)" and "function (a = b, b)"
     * both read a binding that is not there yet.
     *
     * @param name the binding being read
     * @return never returns
     */
    public static Object UNINITIALIZED_BINDING(final Object name) {
        throw referenceError("not.defined", JSType.toString(name));
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
        /**
         * ES2015 7.4.6 IteratorClose, if the iteration has not already finished.
         *
         * @param report whether what return() does is the caller's business.
         *               It is not when the iteration is being abandoned because
         *               something threw: 7.4.6 step 6 hands the original throw
         *               back rather than whatever the close made of it.
         */
        void close(boolean report);
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
            closeable.close(true);
        }
        return UNDEFINED;
    }

    /**
     * The same, where the iteration is being abandoned rather than finished.
     *
     * A for-of loop closes its iterator from a finally block, which runs while
     * an exception is on its way out as readily as it does on the way to the
     * next statement. ES2015 7.4.6 step 6 keeps the original throw in that case,
     * so nothing the close does is reported.
     *
     * @param iterator from {@link #GET_ITERATOR}
     * @return undefined
     */
    /**
     * Closes an iterator, reporting what the close does wrong unless something
     * is already being thrown.
     *
     * ES2015 13.7.5.13 closes with whatever completion left the loop: a normal
     * one, a break, a continue or a return all report, and a throw does not,
     * because the throw on its way out is the one worth reporting.
     *
     * @param iterator from {@link #GET_ITERATOR}
     * @param threw    whether the loop is being left by a throw
     * @return undefined
     */
    public static Object ITERATOR_CLOSE_MAYBE(final Object iterator, final Object threw) {
        if (iterator instanceof CloseableIterator closeable) {
            closeable.close(!JSType.toBoolean(threw));
        }
        return UNDEFINED;
    }

    public static Object ITERATOR_CLOSE_QUIET(final Object iterator) {
        if (iterator instanceof CloseableIterator closeable) {
            closeable.close(false);
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
            public void close(final boolean report) {
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
        return call(function, thiz, SPREAD_TO_ARGUMENTS(argsArray));
    }

    /**
     * Calls anything callable, whatever kind of callable it is.
     *
     * @param function the callable
     * @param thiz     its this value
     * @param args     its arguments
     * @return what it returned
     */
    public static Object call(final Object function, final Object thiz, final Object[] args) {
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
    /**
     * ES2015 14.5.14 step 6.f: what a class may extend is a constructor and
     * nothing else. An arrow function, a generator, a method and an accessor are
     * all callable and none of them is one; a proxy is one exactly when what it
     * proxies is.
     */
    private static boolean isClassHeritage(final Object heritage) {
        if (heritage instanceof ScriptFunction function) {
            return function.isConstructor();
        }
        return heritage instanceof ScriptObject sobj && sobj.isProxyOverConstructor();
    }

    public static Object DEFINE_CLASS(final Object constructor, final Object heritage, final Object derived,
            final Object elements, final Object position, final Object sourceLength) {
        final ScriptFunction ctor = (ScriptFunction)constructor;
        final ScriptObject prototype = (ScriptObject)ctor.getPrototype();

        // 19.2.3.5: what a class answers toString with is the class as written
        ctor.setSourceRange(JSType.toInt32(position), JSType.toInt32(sourceLength));

        if (JSType.toBoolean(derived)) {
            if (heritage == null) {
                // "class C extends null" - the prototype chain simply ends
                prototype.setProto(null);
            } else if (isClassHeritage(heritage)) {
                final ScriptObject parent = (ScriptObject)heritage;
                // ES2015 14.5.14 step 6.e reads "prototype" as an ordinary
                // property, so an accessor there runs and a function that has
                // none at all - a bound one - answers with undefined, which is
                // neither an object nor null and so is an error
                final Object parentPrototype = parent.get("prototype");
                if (parentPrototype != null && !(parentPrototype instanceof ScriptObject)) {
                    throw typeError("cant.inherit.from", safeToString(heritage));
                }
                prototype.setProto((ScriptObject)parentPrototype);
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
        return key instanceof Symbol symbol ? prefix + symbol.asFunctionName() : prefix + JSType.toString(key);
    }

    private static void defineClassElement(final ScriptObject target, final Object key, final int flags,
            final Object value) {
        final Object propertyKey = key instanceof Symbol ? key : JSType.toPropertyKey(key);

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

    /** Gives every method of a just-built object literal the literal as its home object. */
    public static final Call SET_METHOD_HOMES = staticCallNoLookup(ScriptRuntime.class, "setMethodHomes",
            ScriptObject.class, ScriptObject.class);

    /**
     * ES2015 9.1.15 MakeMethod, for an object literal: each method it defines
     * remembers the literal, so that super resolves above it.
     *
     * A function that is not a method cannot mention super - the parser refuses
     * it - so handing one a home object it will never read costs nothing, and
     * saves knowing at this point which of the values were written as methods.
     *
     * @param literal the object just built
     * @return the same object
     */
    public static ScriptObject setMethodHomes(final ScriptObject literal) {
        for (final Property property : literal.getMap().getProperties()) {
            if (property instanceof UserAccessorProperty accessor) {
                makeMethod(accessor.getGetterFunction(literal), literal);
                makeMethod(accessor.getSetterFunction(literal), literal);
            } else {
                makeMethod(literal.get(property.getKey()), literal);
            }
        }
        return literal;
    }

    private static void makeMethod(final Object value, final ScriptObject literal) {
        if (value instanceof ScriptFunction function && function.getHomeObject() == null) {
            function.setHomeObject(literal);
        }
    }

    /**
     * {@code super.x = v} and {@code super[x] = v} (ES2015 12.3.5.3).
     *
     * A super assignment is looked up above the method's home object but written
     * to the receiver: a setter found up there runs on {@code this}, and
     * anything else becomes an ordinary property of {@code this}. Which is the
     * same thing an ordinary assignment does, in every case where the receiver's
     * own prototype chain is the one the home object sits in - the two only part
     * company when the receiver has an own property of that name shadowing a
     * setter above, and this follows the specification there.
     *
     * @param callee the running method
     * @param key    the property
     * @param thiz   the receiver
     * @param value  what to store
     * @param strict whether the assignment was written in strict code
     * @return the value, which is what an assignment evaluates to
     */
    public static Object SUPER_SET(final Object callee, final Object key, final Object thiz, final Object value,
            final boolean strict) {
        final ScriptObject base = superBase(callee);
        final Object name = JSType.toPropertyKey(key);

        final FindProperty found = base.findProperty(name, true);
        if (found != null && found.getProperty() instanceof UserAccessorProperty accessor) {
            final ScriptFunction setter = accessor.getSetterFunction(found.getOwner());
            if (setter != null) {
                apply(setter, thiz, value);
            } else if (strict) {
                throw typeError("property.has.no.setter", safeToString(name), safeToString(thiz));
            }
            return value;
        }

        if (thiz instanceof ScriptObject receiver) {
            // 9.1.9.2 step 3 asks the receiver what it already has before
            // writing, and on an exotic object that is observable - a module
            // namespace answers by reading the export, which is a ReferenceError
            // while the binding is still in its dead zone
            receiver.getOwnPropertyDescriptor(name);
            receiver.set(name, value, strict ? NashornCallSiteDescriptor.CALLSITE_STRICT : 0);
        } else if (strict) {
            throw typeError("cant.set.property", safeToString(name), safeToString(thiz));
        }
        return value;
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
        final ScriptObject base = home.getProto();
        if (base == null) {
            // 8.1.1.3.5 hands back null, and reading a property of it is the
            // ordinary TypeError that reading one of null always is
            throw typeError("cant.get.property", "super", "null");
        }
        return base;
    }

    /**
     * The object a name resolves to, for an assignment that reads it first.
     *
     * ES2015 12.15.4 evaluates the left-hand side once, before the read and
     * before the right-hand side, and writes back through that same reference.
     * Where the scope is dynamic the two would not otherwise agree: a with block
     * whose getter deletes the property, or a direct eval that declares one,
     * changes what the name resolves to in between. The object holding it is
     * found here, before any of that has run, and the write goes there.
     *
     * @param scope the running scope
     * @param name  the name being assigned to
     * @return the object the reference is based on
     */
    public static Object SCOPE_BASE(final Object scope, final Object name) {
        for (ScriptObject current = (ScriptObject)scope; current != null; current = current.getProto()) {
            if (current instanceof WithObject) {
                // the with block answers for the object it was given, and for
                // nothing of its own, so a hit here is a hit on that object
                if (current.findProperty(name, false) != null) {
                    return current;
                }
                continue;
            }
            if (current instanceof Global global) {
                // a program's let and const bindings sit beside the global
                // rather than on it, and shadow what is on it
                final ScriptObject lexical = global.getLexicalScope();
                if (lexical != null && lexical.hasOwnProperty(name)) {
                    return lexical;
                }
                // the global ends the scope chain and has a prototype chain of
                // its own; every link before it is asked about itself alone
                return current.findProperty(name, true) != null ? current : Context.getGlobal();
            }
            if (current.findProperty(name, false) != null) {
                return current;
            }
        }
        // an unresolvable reference: the read that follows is a ReferenceError,
        // and where it is not, 8.1.1.4.9 writes the name on the global
        return Context.getGlobal();
    }

    /**
     * Writes through the reference {@link #SCOPE_BASE} made.
     *
     * What the write means depends on what the reference named: a scope object
     * holds bindings, where assigning to a const is an error whether the code is
     * strict or not, while a with block's binding object is an ordinary object
     * and an ordinary property write is what 8.1.1.2.5 asks for.
     *
     * @param base   the object the reference named
     * @param name   the name
     * @param value  what to write
     * @param strict whether the assignment is in strict code
     */
    public static void SCOPE_PUT(final Object base, final Object name, final Object value, final boolean strict) {
        final int strictFlag = strict ? NashornCallSiteDescriptor.CALLSITE_STRICT : 0;
        if (base instanceof WithObject with) {
            final ScriptObject bindings = with.getExpression();
            if (strict && !bindings.has(name)) {
                // 8.1.1.2.5 step 3: strict code is told when the binding it
                // resolved to has gone in the meantime, which a getter deleting
                // the property it was read through is how it happens
                throw referenceError("not.defined", JSType.toString(name));
            }
            // otherwise an ordinary property write on the object the with block
            // was given, whether or not it still has one under that name
            bindings.set(name, value, strictFlag);
            return;
        }
        ((ScriptObject)base).set(name, value, NashornCallSiteDescriptor.CALLSITE_SCOPE | strictFlag);
    }

    /** {@link #DEFINE_LITERAL_PROPERTY} as a call. */
    public static final Call DEFINE_LITERAL_PROPERTY = staticCallNoLookup(ScriptRuntime.class,
            "DEFINE_LITERAL_PROPERTY", void.class, Object.class, Object.class, Object.class);

    /**
     * ES2015 9.1.6.3 CreateDataPropertyOrThrow, which is how an object literal
     * writes a property it could not bake into its map.
     *
     * The property is defined rather than set, which matters for a name the
     * prototype chain answers for: writing __proto__ would run the accessor
     * Object.prototype has and reparent the object, where the specification
     * makes an ordinary own property of it.
     *
     * @param object the literal being built
     * @param key    the property key
     * @param value  its value
     */
    public static void DEFINE_LITERAL_PROPERTY(final Object object, final Object key, final Object value) {
        final ScriptObject sobj = (ScriptObject)object;
        final Object name = JSType.toPropertyKey(key);
        if (!ArrayIndex.isValidArrayIndex(ArrayIndex.getArrayIndex(name)) && sobj.getMap().findProperty(name) == null) {
            sobj.addOwnProperty(name, 0, value);
            return;
        }
        // an index, or a key the literal already has, where an ordinary write
        // reaches the same property and does the same thing
        sobj.set(name, value, 0);
    }

    /**
     * ES2015 9.2.6: a named function expression's own name is an immutable
     * binding of a scope holding nothing else, so an assignment to it does not
     * take - and in strict code, where 6.2.3.2 PutValue is told to throw, it is
     * a TypeError. The value is evaluated first, which is why it is passed and
     * not just the name.
     *
     * @param value what was assigned, already evaluated
     * @param name  the function's name
     * @return never
     */
    public static Object ASSIGN_TO_FUNCTION_NAME(final Object value, final Object name) {
        throw typeError("assign.constant", JSType.toString(name));
    }

    /** {@link #TO_PROPERTY_KEY} as a call. */
    public static final Call TO_PROPERTY_KEY = staticCallNoLookup(ScriptRuntime.class,
            "TO_PROPERTY_KEY", Object.class, Object.class);

    /**
     * ES2015 7.1.14 ToPropertyKey, where there is no base to check first.
     *
     * An object literal converts a computed key as soon as it has evaluated it,
     * before the value beside it is evaluated at all, which is observable when
     * the key is an object whose toString does something. Anything that is not
     * an object converts to the same thing whenever it is asked, and is left
     * alone so the array and string fast paths still see it.
     *
     * @param key the evaluated key expression
     * @return the property key
     */
    public static Object TO_PROPERTY_KEY(final Object key) {
        return key instanceof ScriptObject || key instanceof JSObject ? JSType.toPropertyKey(key) : key;
    }

    /**
     * A computed property key, made where the reference is.
     *
     * ES2015 12.3.2.1 checks the base and converts the key as part of evaluating
     * {@code base[expr]}, so an object whose toString is observable is asked
     * once however many times the reference is then used - and a compound
     * assignment uses it twice, to read and to write. The base is checked first,
     * so {@code null[prop()]} runs prop() and then fails without ever asking
     * what it answered with for its string. Anything that is not an object
     * converts to the same thing every time and is left alone, so the array and
     * string fast paths still see the value they expect.
     *
     * @param base the evaluated base expression
     * @param key  the evaluated key expression
     * @return the property key
     */
    public static Object TO_PROPERTY_KEY(final Object base, final Object key) {
        if (base == null || base == UNDEFINED) {
            throw typeError("cant.get.property", safeToString(key), safeToString(base));
        }
        return key instanceof ScriptObject || key instanceof JSObject ? JSType.toPropertyKey(key) : key;
    }

    /**
     * {@code super.x} and {@code super[x]}.
     *
     * ES2015 8.1.1.3.5 looks the property up above the method's home object but
     * reads it with the running method's receiver, so a getter inherited from
     * two levels up still sees the object the method was called on.
     *
     * @param callee the running method
     * @param key    the property
     * @param thiz   the receiver
     * @return its value, looked up above the method's home object
     */
    public static Object SUPER_GET(final Object callee, final Object key, final Object thiz) {
        final ScriptObject base = superBase(callee);
        final Object name = JSType.toPropertyKey(key);

        final FindProperty found = base.findProperty(name, true);
        if (found != null && found.getProperty() instanceof UserAccessorProperty accessor) {
            final ScriptFunction getter = accessor.getGetterFunction(found.getOwner());
            return getter == null ? UNDEFINED : apply(getter, thiz);
        }
        return base.get(name);
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
        final Object method = base.get(key);
        return SPREAD_CALL(method, thiz, argsArray);
    }

    /**
     * {@code super(...)} in a derived constructor.
     *
     * @param callee    the running constructor
     * @param thiz      its receiver, which is new.target
     * @param argsArray the arguments
     * @return undefined
     */
    public static Object SUPER_CONSTRUCT(final Object callee, final Object thiz, final Object argsArray) {
        return SUPER_CONSTRUCT_ARGS(callee, thiz, SPREAD_TO_ARGUMENTS(argsArray));
    }

    /**
     * ES2015 12.3.5.1 step 5: the parent is constructed, not called, and it is
     * told what new.target is - which for a derived constructor is what its own
     * this slot holds, since it allocated nothing.
     *
     * @param callee    the derived constructor
     * @param thiz      its receiver, which is new.target
     * @param args      the arguments, already spread
     * @return the object the parent made
     */
    private static Object SUPER_CONSTRUCT_ARGS(final Object callee, final Object thiz, final Object[] args) {
        if (!(callee instanceof ScriptFunction constructor)) {
            throw typeError("no.super");
        }
        final ScriptObject parent = constructor.getProto();
        if (!(parent instanceof ScriptFunction parentConstructor) || !parentConstructor.isConstructor()) {
            // "class C extends null" leaves the constructor inheriting from
            // Function.prototype, which is callable and is not a constructor
            throw typeError("not.a.constructor", safeToString(parent));
        }
        if (!(thiz instanceof ScriptFunction newTarget)) {
            throw typeError("no.super");
        }
        try {
            return parentConstructor.construct(newTarget, args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * The key a direct eval's program function records the caller's
     * {@code new.target} under. It is not a name script code can write.
     */
    public static final String EVAL_NEW_TARGET_KEY = ":evalNewTarget";

    /**
     * {@code new.target} in the top level of eval code, which is the one of the
     * function the eval was called from rather than of the eval itself.
     *
     * @param callee the running eval program
     * @return the caller's new.target, or undefined
     */
    public static Object EVAL_NEW_TARGET(final Object callee) {
        return callee instanceof ScriptObject program ? program.get(EVAL_NEW_TARGET_KEY) : UNDEFINED;
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
        if (callee instanceof ScriptFunction running && running.isSubclassConstructor()) {
            // a derived constructor allocated nothing, so its this slot is
            // new.target itself
            return thiz instanceof ScriptFunction ? thiz : UNDEFINED;
        }
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
     * The same, for a generator whose parameter list has to be bound at the call.
     *
     * @param callee the generator function
     * @param self   its this value
     * @param args   its arguments
     * @return the generator object, or undefined on the generator's own thread
     */
    public static Object GENERATOR_ENTER_PARAMETERS(final Object callee, final Object self, final Object args) {
        if (GeneratorSupport.entering()) {
            return UNDEFINED;
        }
        final Global global = Context.getGlobal();
        final Object[] arguments = args instanceof Object[] array ? array : ScriptRuntime.EMPTY_ARRAY;
        final GeneratorSupport support = new GeneratorSupport((ScriptFunction)callee, self, arguments, global);
        global.registerGenerator(support);
        // ES2015 25.2.1.1 steps 2 and 3: the parameters are bound and only then
        // is the generator object made, so everything the parameter list does -
        // a default that throws, an iterator it steps, a getter it reads, an
        // assignment to the function's own prototype - has happened by the time
        // the object takes what it inherits from.
        support.bindParameters();
        return new NativeGenerator(support, global, generatorPrototype((ScriptFunction)callee, global));
    }

    /**
     * Where such a generator's body waits, its parameters bound.
     *
     * @return undefined
     */
    public static Object GENERATOR_PARAMETERS_BOUND() {
        final GeneratorSupport support = GeneratorSupport.running();
        if (support != null) {
            support.parametersBound();
        }
        return UNDEFINED;
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
    public static Object DERIVED_RETURN(final Object value, final Object thisBinding) {
        if (value == UNDEFINED) {
            return REQUIRE_THIS_INITIALIZED(thisBinding);
        }
        // Anything else is handed on as it stands, including something that is
        // not an object at all. 9.2.2 step 13 refuses that, but it does so in
        // [[Construct]] rather than here: the finally blocks the constructor
        // leaves through run in between, and one of those throwing is the error
        // worth reporting. ScriptFunction.construct makes the check.
        return value;
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
        // Not plain undefined: an arrow written in a derived constructor reads
        // the binding without anything telling it that is where it is - the
        // arrow is compiled on its own, and nothing of the constructor is in
        // range then - so what says the binding has not been made has to be
        // something no ordinary this could be. It reads as undefined wherever it
        // is seen at all, which is nowhere the specification allows.
        return Undefined.getEmpty();
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
    public static Object REQUIRE_THIS_INITIALIZED(final Object binding) {
        if (binding == Undefined.getEmpty()) {
            throw referenceError("this.before.super");
        }
        return binding;
    }

    /**
     * ES2015 8.1.1.3.1 BindThisValue, run when super() returns.
     *
     * @param initialized whether super() has already run in this constructor
     * @param result      the super call's result, evaluated before this is called
     * @return true, to be stored back into the flag
     */
    public static Object BIND_THIS(final Object binding, final Object result) {
        if (binding != Undefined.getEmpty()) {
            throw referenceError("super.called.twice");
        }
        // 12.3.5.1 step 7: super() evaluates to the object it bound
        return result;
    }

    private static final MethodHandle SUPER_INIT;

    static {
        try {
            SUPER_INIT = MethodHandles.lookup().findStatic(ScriptRuntime.class, "superInit",
                    MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object[].class));
        } catch (final ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /**
     * The function an arrow calls super() through.
     *
     * An arrow has neither a callee of the constructor's nor its new.target, and
     * cannot be given them: it may be compiled on its own, long after the
     * constructor was, with nothing of the constructor in sight but what its
     * scope holds. So the constructor puts a function there that already knows
     * both, and the arrow's super() is a call to it.
     *
     * @param callee    the derived constructor super() belongs to
     * @param newTarget what it is being constructed as
     * @return a function that constructs the parent
     */
    public static Object SUPER_INITIALIZER(final Object callee, final Object newTarget) {
        return ScriptFunction.createBuiltin("super",
                MethodHandles.insertArguments(SUPER_INIT, 0, callee, newTarget));
    }

    @SuppressWarnings("unused")
    private static Object superInit(final Object callee, final Object newTarget, final Object self, final Object... args) {
        return SUPER_CONSTRUCT_ARGS(callee, newTarget, args);
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
     * {@code yield* iterable} - hands the generator over to another iterator
     * until it is finished (ES2015 14.4.14).
     *
     * Delegation is not a loop of yields. Everything the caller does to the
     * outer generator has to reach the inner iterator: a value sent with next()
     * is passed on, a throw() is offered to the inner iterator's own throw, and
     * a return() to its return - and any of the three may answer with a value
     * and carry on. What the outer generator yields is the result object the
     * inner one produced, not a fresh one, so a "value" the inner iterator
     * computes lazily is not read until somebody asks for it.
     *
     * @param iterable what to delegate to
     * @return the value the inner iterator finished with
     */
    public static Object YIELD_STAR(final Object iterable) {
        final GeneratorSupport generator = GeneratorSupport.running();
        if (generator == null) {
            throw typeError("yield.outside.generator");
        }

        final Object delegate = iteratorOf(iterable);
        if (!(delegate instanceof ScriptObject iterator)) {
            // a Java array, list or map, which has no protocol to delegate to
            final Iterator<?> plain = toES6Iterator(iterable);
            while (plain.hasNext()) {
                YIELD(plain.next());
            }
            return UNDEFINED;
        }

        // 7.4.1 reads next once, at the start, so replacing it later is not seen
        final Object next = iterator.get("next");
        Object received = UNDEFINED;
        String how = "next";

        while (true) {
            final ScriptObject result;
            switch (how) {
            case "next" -> result = iterationResult(call(next, iterator, new Object[] { received }));
            case "throw" -> {
                final Object thrower = iterator.get("throw");
                if (thrower == UNDEFINED || thrower == null) {
                    // 14.4.14 step 6.b.iii: nothing to hand the throw to, and the
                    // iterator is told the delegation is over before it is reported
                    closeDelegate(iterator);
                    throw typeError("not.a.function", "throw");
                }
                result = iterationResult(call(thrower, iterator, new Object[] { received }));
            }
            default -> {
                final Object returner = iterator.get("return");
                if (returner == UNDEFINED || returner == null) {
                    // nothing to tell: the outer generator just returns
                    throw GeneratorSupport.returning(received);
                }
                result = iterationResult(call(returner, iterator, new Object[] { received }));
                if (JSType.toBoolean(result.get("done"))) {
                    throw GeneratorSupport.returning(result.get("value"));
                }
            }
            }

            if (!"return".equals(how) && JSType.toBoolean(result.get("done"))) {
                return result.get("value");
            }

            final Object[] resumed = generator.yieldDelegating(result);
            how = (String)resumed[0];
            received = resumed[1];
        }
    }

    /** The iterator to delegate to, or null for something with no iterator protocol. */
    private static Object iteratorOf(final Object iterable) {
        final Object coerced = Global.toObject(iterable);
        if (!(coerced instanceof ScriptObject sobj)) {
            return null;
        }
        final Object method = sobj.get(NativeSymbol.iterator);
        if (!Bootstrap.isCallable(method)) {
            return null;
        }
        final Object iterator = call(method, sobj, new Object[0]);
        if (!(iterator instanceof ScriptObject)) {
            throw typeError("not.an.object", safeToString(iterator));
        }
        return iterator;
    }

    /** Tells an iterator the delegation is over, without letting that be reported. */
    private static void closeDelegate(final ScriptObject iterator) {
        try {
            final Object close = iterator.get("return");
            if (Bootstrap.isCallable(close)) {
                call(close, iterator, new Object[0]);
            }
        } catch (final RuntimeException ignored) {
            // the error on its way out is the one worth reporting
        }
    }

    /** What next, throw and return each have to answer with (7.4.1 and its neighbours). */
    private static ScriptObject iterationResult(final Object result) {
        if (result instanceof ScriptObject sobj) {
            return sobj;
        }
        throw typeError("not.an.object", safeToString(result));
    }

    /**
     * The first thing an async function does.
     *
     * Like a generator, an async function is compiled as an ordinary function
     * and serves two roles: called normally it runs its body as far as the first
     * await and hands back a promise, and the call made from the body's own
     * thread falls through into the body itself.
     *
     * @param callee the async function
     * @param self   its this value
     * @param args   its arguments
     * @return the promise the call evaluates to, or undefined on the body's thread
     */
    public static Object ASYNC_ENTER(final Object callee, final Object self, final Object args) {
        if (AsyncSupport.entering()) {
            return UNDEFINED;
        }
        final Object[] arguments = args instanceof Object[] array ? array : ScriptRuntime.EMPTY_ARRAY;
        return AsyncSupport.start((ScriptFunction)callee, self, arguments, Context.getGlobal());
    }

    /**
     * {@code await x} - waits for a value to settle.
     *
     * @param value what to wait for
     * @return what it fulfilled with
     */
    public static Object AWAIT(final Object value) {
        final AsyncSupport async = AsyncSupport.running();
        if (async == null) {
            throw typeError("await.outside.async");
        }
        return async.await(value);
    }

    public static Object YIELD(final Object value) {
        final GeneratorSupport generator = GeneratorSupport.running();
        if (generator == null) {
            throw typeError("yield.outside.generator");
        }
        return generator.yield(value);
    }
}
