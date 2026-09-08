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

import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ECMAScript 2015 25.4, Promise.
 *
 * Reactions never run inline: they go on the realm's microtask queue and run
 * when the JavaScript stack empties, which is what makes the ordering
 * observable to scripts match the specification.
 */
@ScriptClass("Promise")
public final class NativePromise extends ScriptObject {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * ES2015 25.4.4.6 get Promise [ @@species ].
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

    private enum State { PENDING, FULFILLED, REJECTED }

    /** What to do when a promise settles: one branch of a then(). */
    private record Reaction(Capability capability, Object onFulfilled, Object onRejected) { }

    private State state = State.PENDING;
    private Object value = ScriptRuntime.UNDEFINED;
    private final List<Reaction> reactions = new ArrayList<>();
    private final Global global;

    private NativePromise(final ScriptObject proto, final PropertyMap map, final Global global) {
        super(proto, map);
        this.global = global;
    }

    private static NativePromise allocate(final Global global) {
        return new NativePromise(global.getPromisePrototype(), $nasgenmap$, global);
    }

    /**
     * The promise an async function call hands back, before its body has run.
     *
     * @param global the realm
     * @return a pending promise
     */
    public static NativePromise newAsyncPromise(final Global global) {
        return allocate(global);
    }

    /**
     * Settles the promise an async function returned, with what its body
     * returned - adopting it if that is itself a thenable.
     *
     * @param promise the promise to settle
     * @param value   what the body returned
     */
    public static void resolveAsyncPromise(final NativePromise promise, final Object value) {
        promise.resolveWith(value);
    }

    /**
     * Settles the promise an async function returned, with what its body threw.
     *
     * @param promise the promise to settle
     * @param reason  what the body threw
     */
    public static void rejectAsyncPromise(final NativePromise promise, final Object reason) {
        promise.settle(State.REJECTED, reason);
    }

    /**
     * ES2017 6.2.3.1 Await: reacts to a value once, as a job.
     *
     * A promise of this realm is subscribed to as it stands, which is what makes
     * awaiting one cost a single turn of the queue; anything else is wrapped in
     * a promise resolved with it first, which is a turn either way.
     *
     * @param global      the realm
     * @param value       what is being awaited
     * @param onFulfilled called with the value it fulfils with
     * @param onRejected  called with the reason it rejects with
     */
    public static void await(final Global global, final Object value,
            final java.util.function.Consumer<Object> onFulfilled,
            final java.util.function.Consumer<Object> onRejected) {
        if (value instanceof NativePromise already && already.global == global
                && already.get("constructor") == global.get("Promise")) {
            already.onSettled(onFulfilled, onRejected);
            return;
        }
        final NativePromise wrapper = allocate(global);
        wrapper.resolveWith(value);
        wrapper.onSettled(onFulfilled, onRejected);
    }

    @Override
    public String getClassName() {
        return "Promise";
    }

    /**
     * ECMAScript 2015 25.4.3.1 Promise(executor)
     *
     * @param newObj is this a new operator invocation
     * @param self   self reference
     * @param executor called at once with the resolve and reject functions
     * @return the new promise
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean newObj, final Object self, final Object executor) {
        Global.requireEventLoop("Promise");
        if (!newObj) {
            throw typeError("constructor.requires.new", "Promise");
        }
        // 25.4.3.1 step 2 asks IsCallable, not "is a script function": a JSObject that says
        // it is a function - a promise made by Java code - qualifies too
        if (!Bootstrap.isCallable(executor)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(executor));
        }

        final Global global = Global.instance();
        final NativePromise promise = allocate(global);
        final Settlers settlers = promise.settlers();
        try {
            ScriptRuntime.call(executor, ScriptRuntime.UNDEFINED, new Object[] { settlers.resolve(), settlers.reject() });
        } catch (final ECMAException e) {
            // a throwing executor rejects the promise rather than propagating -
            // unless it had already resolved it, in which case 25.4.3.1 step 9
            // drops what it threw on the floor
            if (!settlers.spent()) {
                promise.settle(State.REJECTED, e.getThrown());
            }
        }
        return promise;
    }

    /**
     * ECMAScript 2015 25.4.5.3 Promise.prototype.then(onFulfilled, onRejected)
     *
     * @param self        the promise
     * @param onFulfilled called when it fulfils
     * @param onRejected  called when it rejects
     * @return a promise for the handler's result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object then(final Object self, final Object onFulfilled, final Object onRejected) {
        final NativePromise promise = check(self);
        // 25.4.5.3 step 3: what the derived promise is made by is the species of
        // the constructor this promise says it has, so a subclass gets a promise
        // of its own kind back and a foreign one gets whatever it makes.
        final Capability capability = newPromiseCapability(
                speciesConstructor(promise, promise.global.get("Promise")));
        promise.performThen(onFulfilled, onRejected, capability);
        return capability.promise();
    }

    /** ES2015 25.4.5.3.1 PerformPromiseThen. */
    private void performThen(final Object onFulfilled, final Object onRejected, final Capability capability) {
        final Reaction reaction = new Reaction(capability, onFulfilled, onRejected);
        if (state == State.PENDING) {
            reactions.add(reaction);
        } else {
            schedule(reaction);
        }
    }

    /**
     * ES2015 7.3.20 SpeciesConstructor: the constructor property, then its
     * @@species; either being absent means the default, and anything present
     * that is not a constructor is a TypeError.
     */
    private static Object speciesConstructor(final ScriptObject object, final Object defaultConstructor) {
        final Object constructor = object.get("constructor");
        if (constructor == ScriptRuntime.UNDEFINED) {
            return defaultConstructor;
        }
        if (!(constructor instanceof ScriptObject ctor)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(constructor));
        }
        final Object species = ctor.get(NativeSymbol.species);
        if (species == ScriptRuntime.UNDEFINED || species == null) {
            return defaultConstructor;
        }
        return species;
    }

    /**
     * ECMAScript 2015 25.4.5.1 Promise.prototype.catch(onRejected)
     *
     * @param self       the promise
     * @param onRejected called when it rejects
     * @return a promise for the handler's result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "catch")
    public static Object _catch(final Object self, final Object onRejected) {
        // ES2015 25.4.5.1 is written as Invoke(promise, "then", ...), which asks
        // nothing about what it was called on: anything with a then answers, and
        // anything without one fails the way calling undefined fails.
        // Invoke starts with ToObject, so a primitive with a then on its
        // prototype answers rather than failing
        if (self == ScriptRuntime.UNDEFINED || self == null) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final Object receiver = self instanceof ScriptObject ? self : Global.toObject(self);
        if (!(receiver instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final Object then = sobj.get("then");
        if (!Bootstrap.isCallable(then)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(then));
        }
        return ScriptRuntime.apply((ScriptFunction)then, self, ScriptRuntime.UNDEFINED, onRejected);
    }

    /**
     * ECMAScript 2015 25.4.4.5 Promise.resolve(x)
     *
     * @param self self reference
     * @param x    the value, which may itself be a promise
     * @return a promise for it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object resolve(final Object self, final Object x) {
        Global.requireEventLoop("Promise.resolve");
        if (!(self instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        // 25.4.4.5 step 3: a promise whose constructor is already the one being
        // asked is handed straight back
        if (x instanceof ScriptObject promise && promise.get("constructor") == self) {
            return x;
        }
        final Capability capability = newPromiseCapability(self);
        capability.resolve(x);
        return capability.promise();
    }

    /**
     * ECMAScript 2015 25.4.4.4 Promise.reject(r)
     *
     * @param self self reference
     * @param r    the reason
     * @return a promise already rejected with it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object reject(final Object self, final Object r) {
        Global.requireEventLoop("Promise.reject");
        final Capability capability = newPromiseCapability(self);
        capability.reject(r);
        return capability.promise();
    }

    /**
     * ECMAScript 2024 27.2.4.8 Promise.withResolvers ( )
     *
     * Makes a new promise (of the constructor {@code this}) together with its
     * resolve and reject functions, so an embedder can settle it from outside
     * without capturing the executor's parameters.
     *
     * @param self the Promise constructor (or a subclass)
     * @return an object {@code { promise, resolve, reject }}
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object withResolvers(final Object self) {
        Global.requireEventLoop("Promise.withResolvers");
        requireConstructor(self);
        final Capability capability = newPromiseCapability(self);
        final ScriptObject result = Global.instance().newObject();
        result.set("promise", capability.promise(), 0);
        result.set("resolve", capability.resolveFunction(), 0);
        result.set("reject", capability.rejectFunction(), 0);
        return result;
    }

    /**
     * ECMAScript 2025 27.2.4.6 Promise.try ( callbackfn, ...args )
     *
     * Runs {@code callbackfn} synchronously and wraps its completion in a
     * promise: a normal return resolves it, a thrown value rejects it. Unlike
     * {@code Promise.resolve().then(cb)} the callback runs now, not on a later
     * microtask, while still funnelling any throw into the promise.
     *
     * @param self the Promise constructor (or a subclass)
     * @param args the callback followed by the arguments to pass it
     * @return a promise for the callback's completion
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1, name = "try")
    public static Object _try(final Object self, final Object... args) {
        Global.requireEventLoop("Promise.try");
        requireConstructor(self);
        final Object callbackfn = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new Object[0];
        try {
            // a normal return goes through PromiseResolve, so a promise of this
            // very constructor is handed straight back rather than wrapped in a
            // fresh one
            return resolve(self, ScriptRuntime.call(callbackfn, ScriptRuntime.UNDEFINED, rest));
        } catch (final ECMAException e) {
            // an abrupt completion rejects a new promise of this constructor
            final Capability capability = newPromiseCapability(self);
            capability.reject(e.getThrown());
            return capability.promise();
        }
    }

    /**
     * ECMAScript 2015 25.4.4.1 Promise.all(iterable)
     *
     * @param self     self reference
     * @param iterable the promises to wait for
     * @return a promise for an array of their values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object all(final Object self, final Object iterable) {
        Global.requireEventLoop("Promise.all");
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        final List<Object> values = new ArrayList<>();
        final int[] remaining = { 1 };

        try {
            final Object onRejected = result.rejectFunction();
            combine(self, iterable, promised -> {
                final int slot = values.size();
                values.add(ScriptRuntime.UNDEFINED);
                remaining[0]++;
                // ES2015 25.4.4.1.2: a resolve element function takes effect once
                final boolean[] alreadyCalled = { false };
                subscribe(promised,
                    callback(v -> {
                        if (alreadyCalled[0]) {
                            return;
                        }
                        alreadyCalled[0] = true;
                        values.set(slot, v);
                        if (--remaining[0] == 0) {
                            result.resolve(new NativeArray(values.toArray()));
                        }
                    }),
                    onRejected);
            });
            // 25.4.4.1 step 8 covers the whole of PerformPromiseAll, and
            // resolving the capability is part of it: a resolve function that
            // throws rejects the promise like anything else here, rather than
            // throwing out of Promise.all
            if (--remaining[0] == 0) {
                result.resolve(new NativeArray(values.toArray()));
            }
        } catch (final ECMAException e) {
            // IfAbruptRejectPromise: the returned promise rejects, nothing escapes
            result.reject(e.getThrown());
        }
        return result.promise();
    }

    /**
     * ECMAScript 2021 25.6.4.3 Promise.any(iterable)
     *
     * The mirror image of all(): the first to be fulfilled settles it, and it
     * gives up only when every one of them has rejected - with one error
     * standing for all of theirs.
     *
     * @param self     self reference
     * @param iterable the promises to take the first of
     * @return a promise for whichever is fulfilled first
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object any(final Object self, final Object iterable) {
        Global.requireEventLoop("Promise.any");
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        final List<Object> errors = new ArrayList<>();
        final int[] remaining = { 1 };

        try {
            final Object onFulfilled = result.resolveFunction();
            combine(self, iterable, promised -> {
                final int slot = errors.size();
                errors.add(ScriptRuntime.UNDEFINED);
                remaining[0]++;
                // 25.6.4.3.2: a reject element function takes effect once
                final boolean[] alreadyCalled = { false };
                subscribe(promised, onFulfilled, callback(r -> {
                    if (alreadyCalled[0]) {
                        return;
                    }
                    alreadyCalled[0] = true;
                    errors.set(slot, r);
                    if (--remaining[0] == 0) {
                        result.reject(aggregate(errors));
                    }
                }));
            });
            if (--remaining[0] == 0) {
                result.reject(aggregate(errors));
            }
        } catch (final ECMAException e) {
            result.reject(e.getThrown());
        }
        return result.promise();
    }

    /** The error every rejection is gathered into, which is what any() gives up with. */
    private static Object aggregate(final List<Object> errors) {
        return Global.instance().newAggregateError(new NativeArray(errors.toArray()),
                "All promises were rejected");
    }

    /**
     * ECMAScript 2020 25.6.4.2 Promise.allSettled(iterable)
     *
     * Waits for every one of them and gives up on none: the promise it returns
     * is fulfilled whatever they do, with a record of what each did.
     *
     * @param self     self reference
     * @param iterable the promises to wait for
     * @return a promise for one record per element
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object allSettled(final Object self, final Object iterable) {
        Global.requireEventLoop("Promise.allSettled");
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        final List<Object> records = new ArrayList<>();
        final int[] remaining = { 1 };

        try {
            combine(self, iterable, promised -> {
                final int slot = records.size();
                records.add(ScriptRuntime.UNDEFINED);
                remaining[0]++;
                // one flag between the pair: an element settles once
                final boolean[] alreadyCalled = { false };
                subscribe(promised,
                    callback(v -> settled(alreadyCalled, records, slot, remaining, result, "fulfilled", "value", v)),
                    callback(r -> settled(alreadyCalled, records, slot, remaining, result, "rejected", "reason", r)));
            });
            if (--remaining[0] == 0) {
                result.resolve(new NativeArray(records.toArray()));
            }
        } catch (final ECMAException e) {
            result.reject(e.getThrown());
        }
        return result.promise();
    }

    private static void settled(final boolean[] alreadyCalled, final List<Object> records, final int slot,
            final int[] remaining, final Capability result, final String status, final String key, final Object value) {
        if (alreadyCalled[0]) {
            return;
        }
        alreadyCalled[0] = true;
        final ScriptObject record = Global.newEmptyInstance();
        record.set("status", status, 0);
        record.set(key, value, 0);
        records.set(slot, record);
        if (--remaining[0] == 0) {
            result.resolve(new NativeArray(records.toArray()));
        }
    }

    /**
     * ECMAScript 2015 25.4.4.3 Promise.race(iterable)
     *
     * @param self     self reference
     * @param iterable the promises to race
     * @return a promise settled like whichever settles first
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object race(final Object self, final Object iterable) {
        Global.requireEventLoop("Promise.race");
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        try {
            final Object onFulfilled = result.resolveFunction();
            final Object onRejected = result.rejectFunction();
            combine(self, iterable,
                    promised -> subscribe(promised, onFulfilled, onRejected));
        } catch (final ECMAException e) {
            result.reject(e.getThrown());
        }
        return result.promise();
    }

    /**
     * ECMAScript 2018 25.6.5.3 Promise.prototype.finally(onFinally)
     *
     * The handler is told nothing and changes nothing: whatever the promise
     * settled with passes through it untouched, which is what separates this
     * from then(f, f). What it hands back is waited for first, though, so a
     * handler that returns a promise delays the settlement - and one that
     * throws replaces it.
     *
     * @param self      the promise
     * @param onFinally called however it settles
     * @return a promise that settles as this one does, after the handler has
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "finally")
    public static Object _finally(final Object self, final Object onFinally) {
        if (!(self instanceof ScriptObject promise)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final Object constructor = speciesConstructor(promise, Global.instance().get("Promise"));

        final Object thenFinally;
        final Object catchFinally;
        if (!Bootstrap.isCallable(onFinally)) {
            // 25.6.5.3 step 6: something uncallable is passed to then as it
            // stands, which ignores it - so the promise passes straight through
            thenFinally = onFinally;
            catchFinally = onFinally;
        } else {
            thenFinally = ScriptFunction.createBuiltin("",
                    java.lang.invoke.MethodHandles.insertArguments(THEN_FINALLY, 0, constructor, onFinally));
            catchFinally = ScriptFunction.createBuiltin("",
                    java.lang.invoke.MethodHandles.insertArguments(CATCH_FINALLY, 0, constructor, onFinally));
        }
        return invokeThen(promise, thenFinally, catchFinally);
    }

    /** Invoke(promise, "then", ...), which is how the specification reaches it. */
    private static Object invokeThen(final Object promise, final Object onFulfilled, final Object onRejected) {
        final Object then = promise instanceof ScriptObject sobj ? sobj.get("then") : ScriptRuntime.UNDEFINED;
        if (!(then instanceof ScriptFunction function)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(then));
        }
        return ScriptRuntime.apply(function, promise, onFulfilled, onRejected);
    }

    @SuppressWarnings("unused")
    private static Object thenFinally(final Object constructor, final Object onFinally, final Object self,
            final Object value) {
        final Object waited = resolve(constructor,
                ScriptRuntime.call(onFinally, ScriptRuntime.UNDEFINED, new Object[0]));
        return invokeThen(waited, ScriptFunction.createBuiltin("",
                java.lang.invoke.MethodHandles.insertArguments(RETURN_VALUE, 0, value)), ScriptRuntime.UNDEFINED);
    }

    @SuppressWarnings("unused")
    private static Object catchFinally(final Object constructor, final Object onFinally, final Object self,
            final Object reason) {
        final Object waited = resolve(constructor,
                ScriptRuntime.call(onFinally, ScriptRuntime.UNDEFINED, new Object[0]));
        return invokeThen(waited, ScriptFunction.createBuiltin("",
                java.lang.invoke.MethodHandles.insertArguments(RETHROW, 0, reason)), ScriptRuntime.UNDEFINED);
    }

    @SuppressWarnings("unused")
    private static Object returnValue(final Object value, final Object self, final Object ignored) {
        return value;
    }

    @SuppressWarnings("unused")
    private static Object rethrow(final Object reason, final Object self, final Object ignored) {
        throw new ECMAException(reason, null);
    }

    private static final java.lang.invoke.MethodHandle THEN_FINALLY = findStatic("thenFinally",
            java.lang.invoke.MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class));
    private static final java.lang.invoke.MethodHandle CATCH_FINALLY = findStatic("catchFinally",
            java.lang.invoke.MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class));
    private static final java.lang.invoke.MethodHandle RETURN_VALUE = findStatic("returnValue",
            java.lang.invoke.MethodType.methodType(Object.class, Object.class, Object.class, Object.class));
    private static final java.lang.invoke.MethodHandle RETHROW = findStatic("rethrow",
            java.lang.invoke.MethodType.methodType(Object.class, Object.class, Object.class, Object.class));

    private static java.lang.invoke.MethodHandle findStatic(final String name,
            final java.lang.invoke.MethodType type) {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(NativePromise.class, name, type);
        } catch (final ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /**
     * ES2015 25.4.1.5 NewPromiseCapability: the promise a combinator returns,
     * and the pair of functions that settle it.
     *
     * The constructor is the one the combinator was called on rather than the
     * built-in Promise, so a subclass - or anything else that takes an executor
     * and hands back a thenable - gets to make the result and to see the
     * executor call. Only when it is this realm's own Promise is the whole
     * dance skipped, which is the common case and observably the same thing.
     */
    private static final class Capability {
        private final Object promise;
        private final Object resolve;
        private final Object reject;
        /** Made only if something asks for the functions themselves. */
        private Settlers settlers;

        Capability(final Object promise, final Object resolve, final Object reject) {
            this.promise = promise;
            this.resolve = resolve;
            this.reject = reject;
        }

        Object promise() {
            return promise;
        }

        void resolve(final Object value) {
            if (promise instanceof NativePromise own && resolve == null) {
                own.resolveWith(value);
            } else {
                ScriptRuntime.call(resolve, ScriptRuntime.UNDEFINED, new Object[] { value });
            }
        }

        void reject(final Object reason) {
            if (promise instanceof NativePromise own && reject == null) {
                own.settle(State.REJECTED, reason);
            } else {
                ScriptRuntime.call(reject, ScriptRuntime.UNDEFINED, new Object[] { reason });
            }
        }

        /**
         * The functions themselves, which a combinator hands to every element's
         * then. It is the same pair every time, and a program can see that it
         * is: the specification passes the capability's own functions through.
         */
        Object resolveFunction() {
            return resolve != null ? resolve : own().resolve();
        }

        Object rejectFunction() {
            return reject != null ? reject : own().reject();
        }

        private Settlers own() {
            if (settlers == null) {
                settlers = ((NativePromise)promise).settlers();
            }
            return settlers;
        }
    }

    private static Capability newPromiseCapability(final Object constructor) {
        if (!(constructor instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        final Global global = Global.instance();
        if (constructor == global.get("Promise") && constructor instanceof ScriptFunction builtin
                && ScriptFunction.getPrototype(builtin) == global.getPromisePrototype()) {
            return new Capability(allocate(global), null, null);
        }

        final Object[] captured = new Object[2];
        final ScriptFunction executor = ScriptFunction.createBuiltin("",
                java.lang.invoke.MethodHandles.insertArguments(CAPTURE, 0, (Object)captured));
        final Object promise = ScriptRuntime.construct(function, executor);

        // 25.4.1.5.1 steps 3 and 4: the executor is called once, with two
        // functions, and a constructor that does otherwise cannot be used
        if (!Bootstrap.isCallable(captured[0]) || !Bootstrap.isCallable(captured[1])) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(captured[0]));
        }
        return new Capability(promise, captured[0], captured[1]);
    }

    @SuppressWarnings("unused")
    private static Object capture(final Object[] captured, final Object self,
            final Object resolve, final Object reject) {
        // 25.4.1.5.1 steps 2 and 3 refuse only what has already been set, and
        // an executor called with nothing sets nothing - so a constructor may
        // call it again afterwards with the two functions for real
        if (captured[0] != ScriptRuntime.UNDEFINED && captured[0] != null
                || captured[1] != ScriptRuntime.UNDEFINED && captured[1] != null) {
            throw typeError("promise.capability.already.settled");
        }
        captured[0] = resolve;
        captured[1] = reject;
        return ScriptRuntime.UNDEFINED;
    }

    private static final java.lang.invoke.MethodHandle CAPTURE = findCapture();

    private static java.lang.invoke.MethodHandle findCapture() {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(NativePromise.class, "capture",
                    java.lang.invoke.MethodType.methodType(Object.class, Object[].class, Object.class,
                            Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /** ES2015 25.4.4.1/25.4.4.3 step 2: the combinators are methods of a constructor. */
    private static void requireConstructor(final Object self) {
        if (!(self instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        // step 6 goes on to NewPromiseCapability(C), which needs C to be one
        if (!(self instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * Walks the iterable that all() and race() are given, handing each element on
     * as a promise.
     *
     * The elements go through {@code this.resolve} rather than the built-in one,
     * because the combinators are specified to read it off the constructor they
     * were called on and a program may well have replaced it. That is not a
     * nicety: a replacement that throws is how the iteration is supposed to stop,
     * and calling the built-in instead walks an infinite iterator forever.
     *
     * Anything thrown closes the iterator and rejects the result, as
     * IteratorClose requires.
     */
    private static void combine(final Object self, final Object iterable,
            final java.util.function.Consumer<Object> onElement) {
        final Global global = Global.instance();
        // 25.4.4.1.1 GetPromiseResolve, before the iterable is touched at all: a
        // constructor whose resolve is not callable fails without asking the
        // iterable for its iterator.
        final Object resolver = self instanceof ScriptObject constructor ? constructor.get("resolve") : ScriptRuntime.UNDEFINED;
        if (!(resolver instanceof ScriptFunction resolveFunction)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(resolver));
        }

        final Object iterator = AbstractIterator.getIterator(iterable, global);
        final org.monflabs.nashorn.internal.runtime.linker.InvokeByName next = AbstractIterator.getNextInvoker(global);
        final java.lang.invoke.MethodHandle done = AbstractIterator.getDoneInvoker(global);
        final java.lang.invoke.MethodHandle value = AbstractIterator.getValueInvoker(global);

        while (true) {
            final Object element;
            // The iterator failing of its own accord - next() throwing, or a
            // poisoned done or value - is not something 7.4.6 closes it for: it
            // is already done. Only what the loop does with what it was handed
            // counts as abandoning the iteration.
            try {
                final Object step = next.getInvoker().invokeExact(next.getGetter().invokeExact(iterator), iterator, (Object)null);
                if (!(step instanceof ScriptObject)) {
                    throw typeError("not.an.object", ScriptRuntime.safeToString(step));
                }
                if (JSType.toBoolean((Object)done.invokeExact(step))) {
                    return;
                }
                element = (Object)value.invokeExact(step);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }

            try {
                onElement.accept(ScriptRuntime.apply(resolveFunction, self, element));
            } catch (final RuntimeException | Error e) {
                closeIterator(iterator, global);
                throw e;
            }
        }
    }

    /**
     * Attaches the combinator's handlers to one element.
     *
     * Through the element's own {@code then}, not through the internal
     * machinery: the specification says so, a program may have replaced it, and
     * a replacement that throws is how the surrounding iteration is meant to
     * stop. Reaching past it walks an infinite iterator forever.
     */
    private static void subscribe(final Object promised, final Object onFulfilled, final Object onRejected) {
        final Object then = promised instanceof ScriptObject sobj ? sobj.get("then") : ScriptRuntime.UNDEFINED;
        if (!(then instanceof ScriptFunction function)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(then));
        }
        ScriptRuntime.apply(function, promised, onFulfilled, onRejected);
    }

    /** Wraps one of the combinator's Java handlers as a function a script can call. */
    private static ScriptFunction callback(final java.util.function.Consumer<Object> action) {
        return ScriptFunction.createBuiltin("",
                java.lang.invoke.MethodHandles.insertArguments(INVOKE_ACTION, 0, action));
    }

    @SuppressWarnings("unused")
    private static Object invokeAction(final java.util.function.Consumer<Object> action, final Object self,
            final Object argument) {
        action.accept(argument);
        return ScriptRuntime.UNDEFINED;
    }

    private static final java.lang.invoke.MethodHandle INVOKE_ACTION = findInvokeAction();

    private static java.lang.invoke.MethodHandle findInvokeAction() {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(NativePromise.class, "invokeAction",
                    java.lang.invoke.MethodType.methodType(Object.class, java.util.function.Consumer.class,
                            Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ES2015 7.4.6 IteratorClose, best effort: a failure here must not mask the original. */
    private static void closeIterator(final Object iterator, final Global global) {
        if (!(iterator instanceof ScriptObject sobj)) {
            return;
        }
        try {
            if (sobj.get("return") instanceof ScriptFunction close) {
                ScriptRuntime.apply(close, iterator);
            }
        } catch (final RuntimeException ignored) {
            // the error that got us here is the one worth reporting
        }
    }

    /** Runs one of two Java actions when this promise settles, as a queued job. */
    private void onSettled(final java.util.function.Consumer<Object> onFulfilled,
            final java.util.function.Consumer<Object> onRejected) {
        final Runnable job = () -> {
            if (state == State.FULFILLED) {
                onFulfilled.accept(value);
            } else {
                onRejected.accept(value);
            }
        };
        if (state == State.PENDING) {
            reactions.add(new Reaction(null, job, job));
        } else {
            global.getJobQueue().enqueue(job);
        }
    }

    /** ES2015 25.4.1.3.2: adopt the state of a thenable, otherwise fulfil. */
    private void resolveWith(final Object x) {
        if (x == this) {
            settle(State.REJECTED, typeError("promise.self.resolution").getThrown());
            return;
        }
        final Object then;
        try {
            // 25.4.1.3.2 step 8: what "then" holds is read here, once, and a
            // promise of this realm is read no differently - one whose then has
            // been replaced is a thenable like any other, and one whose then is
            // still the built-in reaches its own machinery through it
            then = x instanceof ScriptObject sobj ? sobj.get("then") : ScriptRuntime.UNDEFINED;
        } catch (final ECMAException e) {
            // a throwing "then" getter rejects, it does not escape
            settle(State.REJECTED, e.getThrown());
            return;
        }
        if (then instanceof ScriptFunction thenFunction) {
            // a thenable is adopted by calling its then with our own resolve and
            // reject, on the queue rather than inline
            global.getJobQueue().enqueue(() -> {
                final Settlers settlers = settlers();
                try {
                    ScriptRuntime.apply(thenFunction, x, settlers.resolve(), settlers.reject());
                } catch (final ECMAException e) {
                    // 25.4.2.2 step 3: a then that settles and then throws has
                    // already had its say
                    if (!settlers.spent()) {
                        settle(State.REJECTED, e.getThrown());
                    }
                }
            });
            return;
        }
        settle(State.FULFILLED, x);
    }

    private void settle(final State newState, final Object newValue) {
        if (state != State.PENDING) {
            return;
        }
        state = newState;
        value = newValue;
        for (final Reaction reaction : reactions) {
            schedule(reaction);
        }
        reactions.clear();
    }

    /** Queues one reaction; it never runs inline. */
    private void schedule(final Reaction reaction) {
        global.getJobQueue().enqueue(() -> run(reaction));
    }

    private void run(final Reaction reaction) {
        final boolean fulfilled = state == State.FULFILLED;
        final Object handler = fulfilled ? reaction.onFulfilled() : reaction.onRejected();

        if (handler instanceof Runnable action) {
            // an internal reaction, used by all() and race()
            action.run();
            return;
        }

        final Capability capability = reaction.capability();
        if (!(handler instanceof ScriptFunction function)) {
            // no handler for this outcome: pass it straight through
            if (fulfilled) {
                capability.resolve(value);
            } else {
                capability.reject(value);
            }
            return;
        }

        try {
            capability.resolve(ScriptRuntime.apply(function, ScriptRuntime.UNDEFINED, value));
        } catch (final ECMAException e) {
            capability.reject(e.getThrown());
        }
    }

    /**
     * The resolve/reject pair handed to an executor.
     *
     * ES2015 25.4.1.3 gives the two of them one alreadyResolved between them, so
     * whichever is called first is the one that counts and the other does
     * nothing - and neither is named: they are anonymous functions of length 1.
     */
    private record Settlers(ScriptFunction resolve, ScriptFunction reject, boolean[] used) {
        boolean spent() {
            return used[0];
        }
    }

    private Settlers settlers() {
        final boolean[] used = { false };
        return new Settlers(
                ScriptFunction.createBuiltin("",
                        java.lang.invoke.MethodHandles.insertArguments(SETTLE, 0, this, true, used)),
                ScriptFunction.createBuiltin("",
                        java.lang.invoke.MethodHandles.insertArguments(SETTLE, 0, this, false, used)),
                used);
    }

    @SuppressWarnings("unused")
    private static Object settle(final NativePromise promise, final boolean resolving, final boolean[] used,
            final Object self, final Object argument) {
        if (!used[0]) {
            used[0] = true;
            if (resolving) {
                promise.resolveWith(argument);
            } else {
                promise.settle(State.REJECTED, argument);
            }
        }
        return ScriptRuntime.UNDEFINED;
    }

    private static final java.lang.invoke.MethodHandle SETTLE = findSettle();


    private static java.lang.invoke.MethodHandle findSettle() {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(NativePromise.class, "settle",
                    java.lang.invoke.MethodType.methodType(Object.class, NativePromise.class, boolean.class,
                            boolean[].class, Object.class, Object.class));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NativePromise check(final Object self) {
        if (self instanceof NativePromise promise) {
            return promise;
        }
        throw typeError("not.a.promise", ScriptRuntime.safeToString(self));
    }

    /**
     * ES2015 25.4.5.4 Promise.prototype [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Promise";

}
