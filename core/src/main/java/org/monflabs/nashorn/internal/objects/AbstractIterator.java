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
import java.util.function.Consumer;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Setter;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.linker.InvokeByName;
import org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

/**
 * ECMA6 25.1.2 The %IteratorPrototype% Object
 */
@ScriptClass("Iterator")
public abstract class AbstractIterator extends ScriptObject {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private final static Object ITERATOR_INVOKER_KEY = new Object();
    private final static Object NEXT_INVOKER_KEY     = new Object();
    private final static Object DONE_INVOKER_KEY     = new Object();

    /**
     * What a built-in iterator's allocation-free step answers when it is
     * exhausted - see {@link ArrayIterator#stepValue()}. Never a JS value.
     */
    public static final Object ITERATION_DONE = new Object();
    private final static Object VALUE_INVOKER_KEY    = new Object();

    /** ECMA6 iteration kinds */
    enum IterationKind {
        /** key iteration */
        KEY,
        /** value iteration */
        VALUE,
        /** key+value iteration */
        KEY_VALUE
    }

    /**
     * Create an abstract iterator object with the given prototype and property map.
     *
     * @param prototype the prototype
     * @param map the property map
     */
    protected AbstractIterator(final ScriptObject prototype, final PropertyMap map) {
        super(prototype, map);
    }

    /**
     * 25.1.2.1 %IteratorPrototype% [ @@iterator ] ( )
     *
     * @param self the self object
     * @return this iterator
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@iterator")
    public static Object getIterator(final Object self) {
        return self;
    }

    @Override
    public String getClassName() {
        return "Iterator";
    }

    /**
     * ES6 25.1.1.2 The Iterator Interface
     *
     * @param arg argument
     * @return next iterator result
     */
    protected abstract IteratorResult next(final Object arg);

    /**
     * ES6 25.1.1.3 The IteratorResult Interface
     *
     * @param value result value
     * @param done result status
     * @param global the global object
     * @return result object
     */
    protected IteratorResult makeResult(final Object value, final Boolean done, final Global global) {
        return new IteratorResult(value, done, global);
    }

    static MethodHandle getIteratorInvoker(final Global global) {
        return global.getDynamicInvoker(ITERATOR_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class));
    }

    /**
     * Get the invoker for the ES6 iterator {@code next} method.
     * @param global the global object
     * @return the next invoker
     */
    public static InvokeByName getNextInvoker(final Global global) {
        return global.getInvokeByName(AbstractIterator.NEXT_INVOKER_KEY,
                () -> new InvokeByName("next", Object.class, Object.class, Object.class));
    }

    /**
     * Get the invoker for the ES6 iterator result {@code done} property.
     * @param global the global object
     * @return the done invoker
     */
    public static MethodHandle getDoneInvoker(final Global global) {
        return global.getDynamicInvoker(AbstractIterator.DONE_INVOKER_KEY,
                () -> Bootstrap.createDynamicInvoker("done", NashornCallSiteDescriptor.GET_PROPERTY, Object.class, Object.class));
    }

    /**
     * Get the invoker for the ES6 iterator result {@code value} property.
     * @param global the global object
     * @return the value invoker
     */
    public static MethodHandle getValueInvoker(final Global global) {
        return global.getDynamicInvoker(AbstractIterator.VALUE_INVOKER_KEY,
                () -> Bootstrap.createDynamicInvoker("value", NashornCallSiteDescriptor.GET_PROPERTY, Object.class, Object.class));
    }

    /**
     * ES6 7.4.1 GetIterator abstract operation
     *
     * @param iterable an object
     * @param global the global object
     * @return the iterator
     */
    public static Object getIterator(final Object iterable, final Global global) {
        final Object object = Global.toObject(iterable);

        if (object instanceof ScriptObject) {
            // TODO we need to implement fast property access for Symbol keys in order to use InvokeByName here.
            final Object getter = ((ScriptObject) object).get(NativeSymbol.iterator);

            if (Bootstrap.isCallable(getter)) {
                try {
                    final MethodHandle invoker = getIteratorInvoker(global);

                    final Object value = invoker.invokeExact(getter, iterable);
                    if (JSType.isPrimitive(value)) {
                        throw typeError("not.an.object", ScriptRuntime.safeToString(value));
                    }
                    return value;

                } catch (final RuntimeException | Error e) {
                    // An error thrown by the script's own @@iterator is the
                    // program's, not a failure of the call: wrapping it made it a
                    // Java object with no constructor, which a catch block cannot
                    // tell apart from anything else.
                    throw e;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            throw typeError("not.a.function", ScriptRuntime.safeToString(getter));
        }

        throw typeError("cannot.get.iterator", ScriptRuntime.safeToString(iterable));
    }

    /**
     * Iterate over an iterable object, passing every value to {@code consumer}.
     *
     * @param iterable an iterable object
     * @param global the current global
     * @param consumer the value consumer
     */
    /**
     * ES2015 23.1.1.1 and its three siblings: a collection built from an iterable
     * is filled through the method it publishes rather than by reaching inside.
     *
     * The method is read once, before anything is iterated, so a getter on it
     * runs once and what it throws comes out before the iterable is touched; it
     * has to be callable; and it is called for each entry, so a replacement is
     * used and counted. What it throws closes the iterator.
     *
     * @param collection the collection being built
     * @param name       what it calls the method that adds to it
     * @param iterable   what to fill it from, which may be nothing
     * @param global     the current global
     * @param entry      how to make the arguments of one call out of one item
     */
    public static void fillFrom(final ScriptObject collection, final String name, final Object iterable,
            final Global global, final java.util.function.Function<Object, Object[]> entry) {
        if (iterable == null || iterable == ScriptRuntime.UNDEFINED) {
            return;
        }
        final Object adder = collection.get(name);
        if (!Bootstrap.isCallable(adder)) {
            throw typeError(global, "not.a.function", ScriptRuntime.safeToString(adder));
        }
        iterate(iterable, global, value -> ScriptRuntime.apply((ScriptFunction)adder, collection, entry.apply(value)));
    }

    /**
     * ES2015 7.4.6 IteratorClose, for an iteration being abandoned because
     * something threw.
     *
     * The iterator here is the object the iterable answered with rather than one
     * of Nashorn's own wrappers, so its return method is asked for and called
     * outright. Step 6 hands the original throw back, so whatever the close
     * makes of it is dropped.
     */
    private static void closeQuietly(final Object iterator) {
        try {
            final Object returnMethod = iterator instanceof ScriptObject sobj
                    ? sobj.get("return")
                    : ScriptRuntime.UNDEFINED;
            if (Bootstrap.isCallable(returnMethod)) {
                ScriptRuntime.apply((ScriptFunction)returnMethod, iterator);
            }
        } catch (final RuntimeException ignored) {
            // the throw on its way out is the one worth reporting
        }
    }

    public static void iterate(final Object iterable, final Global global, final Consumer<Object> consumer) {

        final Object iterator = AbstractIterator.getIterator(Global.toObject(iterable), global);

        final InvokeByName nextInvoker = getNextInvoker(global);
        final MethodHandle doneInvoker = getDoneInvoker(global);
        final MethodHandle valueInvoker = getValueInvoker(global);

        try {
            do {
                final Object next = nextInvoker.getGetter().invokeExact(iterator);
                if (!Bootstrap.isCallable(next)) {
                    // a non-callable "next" is a TypeError, and the iterator is
                    // not closed - it was never successfully stepped
                    throw typeError(global, "not.a.function", "next");
                }

                final Object result = nextInvoker.getInvoker().invokeExact(next, iterator, (Object) null);
                if (!(result instanceof ScriptObject)) {
                    // IteratorNext must return an object; again the iterator is
                    // not closed (7.4.2)
                    throw typeError(global, "not.an.object", ScriptRuntime.safeToString(result));
                }

                final Object done = doneInvoker.invokeExact(result);
                if (JSType.toBoolean(done)) {
                    break;
                }

                final Object value = valueInvoker.invokeExact(result);
                try {
                    consumer.accept(value);
                } catch (final RuntimeException r) {
                    closeQuietly(iterator);
                    throw r;
                }

            } while (true);

        } catch (final RuntimeException r) {
            throw r;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }

    }

    // ------------------------------------------------------------------------
    // ES2025 Iterator Helpers (25.1.4), which live on %Iterator.prototype% -
    // this very object - so every iterator inherits them. The lazy ones
    // (map/filter/take/drop/flatMap) return an IteratorHelper; the eager ones
    // (reduce/toArray/forEach/some/every/find) drive the iterator to the end.
    // Each captures GetIteratorDirect(this) = (this, this.next) up front.
    // ------------------------------------------------------------------------

    private static ScriptObject requireIteratorSelf(final Object self) {
        if (self instanceof ScriptObject sobj) {
            return sobj;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(self));
    }

    private static void requireCallback(final Object fn) {
        if (!Bootstrap.isCallable(fn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(fn));
        }
    }

    /** As {@link #requireCallback} but, on failure, first closes {@code iterated}. */
    private static void requireCallbackClosing(final Object fn, final ScriptObject iterated) {
        if (!Bootstrap.isCallable(fn)) {
            closeIteratorOnError(iterated);
            throw typeError("not.a.function", ScriptRuntime.safeToString(fn));
        }
    }

    /** ToIntegerOrInfinity of the take/drop limit, rejecting NaN and a negative result. */
    private static double integerLimit(final Object arg) {
        final double num = JSType.toNumber(arg);
        if (Double.isNaN(num)) {
            throw rangeError("invalid.iterator.limit", ScriptRuntime.safeToString(arg));
        }
        if (Double.isInfinite(num)) {
            return num;
        }
        // ToIntegerOrInfinity truncates toward zero, so -0.5 becomes 0, not negative
        final double integer = (double) (long) num;
        // a finite limit must be a non-negative safe integer
        if (integer < 0 || integer > 9007199254740991.0) {
            throw rangeError("invalid.iterator.limit", ScriptRuntime.safeToString(arg));
        }
        return integer;
    }

    /** One IteratorNext on a captured (iterator, next); the result object, or null if done. */
    static ScriptObject nextResult(final Object iterated, final Object nextMethod, final Global global) {
        final MethodHandle call = getIteratorInvoker(global);
        final Object result;
        try {
            result = call.invokeExact(nextMethod, iterated);
            if (!(result instanceof ScriptObject sobj)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(result));
            }
            return JSType.toBoolean((Object) getDoneInvoker(global).invokeExact((Object) sobj)) ? null : sobj;
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static Object resultValue(final ScriptObject result, final Global global) {
        try {
            return (Object) getValueInvoker(global).invokeExact((Object) result);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * ES2025 7.4.11 IteratorClose for a normal completion: call {@code return}
     * and let whatever it throws propagate.
     */
    static void closeIterator(final Object iterated) {
        if (iterated instanceof ScriptObject sobj) {
            final Object ret = sobj.get("return");
            if (Bootstrap.isCallable(ret)) {
                ScriptRuntime.apply((ScriptFunction) ret, iterated);
            }
        }
    }

    /**
     * ES2025 7.4.11 IteratorClose for an abrupt completion: call {@code return}
     * but drop whatever it throws, so the error already under way is the one
     * that gets out.
     */
    static void closeIteratorOnError(final Object iterated) {
        if (iterated instanceof ScriptObject sobj) {
            final Object ret = sobj.get("return");
            if (Bootstrap.isCallable(ret)) {
                try {
                    ScriptRuntime.apply((ScriptFunction) ret, iterated);
                } catch (final RuntimeException ignored) {
                    // the throw already under way is the one worth reporting
                }
            }
        }
    }

    /**
     * ES2025 25.1.4.6 Iterator.prototype.map ( mapper ).
     *
     * @param self the iterator
     * @param mapper the mapping function
     * @return a lazy iterator of the mapped values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object map(final Object self, final Object mapper) {
        final ScriptObject iterated = requireIteratorSelf(self);
        requireCallbackClosing(mapper, iterated);
        return new IteratorHelper(IteratorHelper.Kind.MAP, iterated, iterated.get("next"), mapper, 0, Global.instance());
    }

    /**
     * ES2025 25.1.4.3 Iterator.prototype.filter ( predicate ).
     *
     * @param self the iterator
     * @param predicate the predicate
     * @return a lazy iterator of the kept values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object filter(final Object self, final Object predicate) {
        final ScriptObject iterated = requireIteratorSelf(self);
        requireCallbackClosing(predicate, iterated);
        return new IteratorHelper(IteratorHelper.Kind.FILTER, iterated, iterated.get("next"), predicate, 0, Global.instance());
    }

    /**
     * ES2025 25.1.4.9 Iterator.prototype.take ( limit ).
     *
     * @param self the iterator
     * @param limit how many values to yield
     * @return a lazy iterator of at most {@code limit} values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object take(final Object self, final Object limit) {
        final ScriptObject iterated = requireIteratorSelf(self);
        final double lim;
        try {
            lim = integerLimit(limit);
        } catch (final RuntimeException e) {
            closeIteratorOnError(iterated);
            throw e;
        }
        return new IteratorHelper(IteratorHelper.Kind.TAKE, iterated, iterated.get("next"), null, lim, Global.instance());
    }

    /**
     * ES2025 25.1.4.2 Iterator.prototype.drop ( limit ).
     *
     * @param self the iterator
     * @param limit how many values to skip
     * @return a lazy iterator past the first {@code limit} values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object drop(final Object self, final Object limit) {
        final ScriptObject iterated = requireIteratorSelf(self);
        final double lim;
        try {
            lim = integerLimit(limit);
        } catch (final RuntimeException e) {
            closeIteratorOnError(iterated);
            throw e;
        }
        return new IteratorHelper(IteratorHelper.Kind.DROP, iterated, iterated.get("next"), null, lim, Global.instance());
    }

    /**
     * ES2025 25.1.4.4 Iterator.prototype.flatMap ( mapper ).
     *
     * @param self the iterator
     * @param mapper the mapping function, whose results are flattened
     * @return a lazy iterator of the flattened values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object flatMap(final Object self, final Object mapper) {
        final ScriptObject iterated = requireIteratorSelf(self);
        requireCallbackClosing(mapper, iterated);
        return new IteratorHelper(IteratorHelper.Kind.FLATMAP, iterated, iterated.get("next"), mapper, 0, Global.instance());
    }

    /**
     * ES2025 25.1.4.8 Iterator.prototype.reduce ( reducer [ , initialValue ] ).
     *
     * @param self the iterator
     * @param args the reducer and an optional initial value
     * @return the accumulated result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduce(final Object self, final Object... args) {
        final Global global = Global.instance();
        final ScriptObject iterated = requireIteratorSelf(self);
        final Object reducer = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        requireCallbackClosing(reducer, iterated);
        final Object nextMethod = iterated.get("next");
        Object accumulator;
        long counter;
        if (args.length > 1) {
            accumulator = args[1];
            counter = 0;
        } else {
            final ScriptObject r = nextResult(iterated, nextMethod, global);
            if (r == null) {
                throw typeError("array.reduce.invalid.init");
            }
            accumulator = resultValue(r, global);
            counter = 1;
        }
        for (;;) {
            final ScriptObject r = nextResult(iterated, nextMethod, global);
            if (r == null) {
                return accumulator;
            }
            final Object value = resultValue(r, global);
            try {
                accumulator = ScriptRuntime.call(reducer, ScriptRuntime.UNDEFINED, new Object[] { accumulator, value, (double) counter++ });
            } catch (final RuntimeException e) {
                closeIteratorOnError(iterated);
                throw e;
            }
        }
    }

    /**
     * ES2025 25.1.4.10 Iterator.prototype.toArray ( ).
     *
     * @param self the iterator
     * @return an Array of the remaining values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object toArray(final Object self) {
        final Global global = Global.instance();
        final ScriptObject iterated = requireIteratorSelf(self);
        final Object nextMethod = iterated.get("next");
        final java.util.List<Object> values = new java.util.ArrayList<>();
        for (;;) {
            final ScriptObject r = nextResult(iterated, nextMethod, global);
            if (r == null) {
                break;
            }
            values.add(resultValue(r, global));
        }
        return new NativeArray(values.toArray());
    }

    /**
     * ES2025 25.1.4.5 Iterator.prototype.forEach ( fn ).
     *
     * @param self the iterator
     * @param fn the callback
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object forEach(final Object self, final Object fn) {
        final Global global = Global.instance();
        final ScriptObject iterated = requireIteratorSelf(self);
        requireCallbackClosing(fn, iterated);
        final Object nextMethod = iterated.get("next");
        long counter = 0;
        for (;;) {
            final ScriptObject r = nextResult(iterated, nextMethod, global);
            if (r == null) {
                return ScriptRuntime.UNDEFINED;
            }
            final Object value = resultValue(r, global);
            try {
                ScriptRuntime.call(fn, ScriptRuntime.UNDEFINED, new Object[] { value, (double) counter++ });
            } catch (final RuntimeException e) {
                closeIteratorOnError(iterated);
                throw e;
            }
        }
    }

    /**
     * ES2025 25.1.4.9 Iterator.prototype.some ( predicate ).
     *
     * @param self the iterator
     * @param predicate the predicate
     * @return whether any value satisfies it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean some(final Object self, final Object predicate) {
        return findOrTest(self, predicate, Mode.SOME) == Boolean.TRUE;
    }

    /**
     * ES2025 25.1.4.1 Iterator.prototype.every ( predicate ).
     *
     * @param self the iterator
     * @param predicate the predicate
     * @return whether every value satisfies it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean every(final Object self, final Object predicate) {
        return findOrTest(self, predicate, Mode.EVERY) == Boolean.TRUE;
    }

    /**
     * ES2025 25.1.4.5 Iterator.prototype.find ( predicate ).
     *
     * @param self the iterator
     * @param predicate the predicate
     * @return the first value satisfying it, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object find(final Object self, final Object predicate) {
        return findOrTest(self, predicate, Mode.FIND);
    }

    private enum Mode { SOME, EVERY, FIND }

    private static Object findOrTest(final Object self, final Object predicate, final Mode mode) {
        final Global global = Global.instance();
        final ScriptObject iterated = requireIteratorSelf(self);
        requireCallbackClosing(predicate, iterated);
        final Object nextMethod = iterated.get("next");
        long counter = 0;
        for (;;) {
            final ScriptObject r = nextResult(iterated, nextMethod, global);
            if (r == null) {
                return mode == Mode.EVERY ? Boolean.TRUE : mode == Mode.SOME ? Boolean.FALSE : ScriptRuntime.UNDEFINED;
            }
            final Object value = resultValue(r, global);
            final boolean matched;
            try {
                matched = JSType.toBoolean(ScriptRuntime.call(predicate, ScriptRuntime.UNDEFINED, new Object[] { value, (double) counter++ }));
            } catch (final RuntimeException e) {
                closeIteratorOnError(iterated);
                throw e;
            }
            if (mode == Mode.EVERY ? !matched : matched) {
                closeIterator(iterated);
                return mode == Mode.EVERY ? Boolean.FALSE : mode == Mode.SOME ? Boolean.TRUE : value;
            }
        }
    }

    // ES2025 25.1.4.11 %Iterator.prototype% [ @@toStringTag ] is an accessor
    // whose get returns "Iterator" and whose set is a
    // SetterThatIgnoresPrototypeProperties. Like the constructor accessor below
    // it is installed by hand in Global from these handles, not via @Getter /
    // @Setter: a nasgen-generated accessor setter is invoked with `this` bound
    // to the home prototype rather than to the assignment's receiver, which
    // breaks the ignore-prototype rule (a write through a child would look like
    // a write on the home). A hand-built accessor gets the true receiver.

    /** Handle for the get %Iterator.prototype% [ @@toStringTag ] accessor. */
    public static final MethodHandle TOSTRINGTAG_GET;
    /** Handle for the set %Iterator.prototype% [ @@toStringTag ] accessor. */
    public static final MethodHandle TOSTRINGTAG_SET;
    static {
        try {
            final java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
            TOSTRINGTAG_GET = lookup.findStatic(AbstractIterator.class, "toStringTagGet",
                    java.lang.invoke.MethodType.methodType(Object.class, Object.class));
            TOSTRINGTAG_SET = lookup.findStatic(AbstractIterator.class, "toStringTagSet",
                    java.lang.invoke.MethodType.methodType(void.class, Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * ES2025 25.1.4.11 get %Iterator.prototype% [ @@toStringTag ]: returns "Iterator".
     * @param self the receiver (ignored)
     * @return "Iterator"
     */
    public static Object toStringTagGet(final Object self) {
        return "Iterator";
    }

    /**
     * ES2025 25.1.4.11 set %Iterator.prototype% [ @@toStringTag ]: a
     * SetterThatIgnoresPrototypeProperties.
     * @param self the receiver
     * @param value the value to set
     */
    public static void toStringTagSet(final Object self, final Object value) {
        setIgnoringPrototype(self, NativeSymbol.toStringTag, value);
    }

    // ES2025 25.1.4.12 %Iterator.prototype%.constructor is an accessor whose get
    // returns %Iterator% and whose set is a SetterThatIgnoresPrototypeProperties.
    // A @Getter/@Setter named "constructor" collides with the constructor slot
    // PrototypeObject puts on every prototype and degrades to a data property,
    // so Global installs this pair on %IteratorPrototype% by hand, from these
    // handles, replacing PrototypeObject's slot.

    /** Handle for the get %Iterator.prototype%.constructor accessor. */
    public static final MethodHandle CONSTRUCTOR_GET;
    /** Handle for the set %Iterator.prototype%.constructor accessor. */
    public static final MethodHandle CONSTRUCTOR_SET;
    static {
        try {
            final java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
            CONSTRUCTOR_GET = lookup.findStatic(AbstractIterator.class, "constructorGet",
                    java.lang.invoke.MethodType.methodType(Object.class, Object.class));
            CONSTRUCTOR_SET = lookup.findStatic(AbstractIterator.class, "constructorSet",
                    java.lang.invoke.MethodType.methodType(void.class, Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * ES2025 25.1.4.12 get %Iterator.prototype%.constructor: returns %Iterator%.
     * @param self the receiver (ignored)
     * @return the %Iterator% constructor
     */
    public static Object constructorGet(final Object self) {
        return Global.instance().getIteratorConstructor();
    }

    /**
     * ES2025 25.1.4.12 set %Iterator.prototype%.constructor: a
     * SetterThatIgnoresPrototypeProperties.
     * @param self  the receiver
     * @param value the value to set
     */
    public static void constructorSet(final Object self, final Object value) {
        setIgnoringPrototype(self, "constructor", value);
    }

    /**
     * ES2025 10.4.7 SetterThatIgnoresPrototypeProperties(this, home, key, value):
     * throw if {@code this} is not an object or is the home prototype itself
     * (emulating a write to a non-writable own data property in strict mode);
     * otherwise set an existing own property or create a new own data one. The
     * home is always {@code %Iterator.prototype%} here.
     */
    private static void setIgnoringPrototype(final Object self, final Object key, final Object value) {
        final Global global = Global.instance();
        if (!(self instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        if (sobj == global.getIteratorPrototype()) {
            throw typeError("cant.set.prototype.property", ScriptRuntime.safeToString(key));
        }
        if (sobj.hasOwnProperty(key)) {
            sobj.set(key, value, NashornCallSiteDescriptor.CALLSITE_STRICT);
        } else {
            sobj.defineOwnProperty(key, global.newDataDescriptor(value, true, true, true), true);
        }
    }
}
