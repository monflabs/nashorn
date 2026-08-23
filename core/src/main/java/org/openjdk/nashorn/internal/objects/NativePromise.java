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

import java.util.ArrayList;
import java.util.List;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Getter;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.runtime.ECMAException;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;

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
    private record Reaction(NativePromise derived, Object onFulfilled, Object onRejected) { }

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
        if (!newObj) {
            throw typeError("constructor.requires.new", "Promise");
        }
        if (!(executor instanceof ScriptFunction function)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(executor));
        }

        final Global global = Global.instance();
        final NativePromise promise = allocate(global);
        try {
            ScriptRuntime.apply(function, ScriptRuntime.UNDEFINED,
                    promise.resolveFunction(), promise.rejectFunction());
        } catch (final ECMAException e) {
            // a throwing executor rejects the promise rather than propagating
            promise.settle(State.REJECTED, e.getThrown());
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
        final NativePromise derived = allocate(promise.global);
        final Reaction reaction = new Reaction(derived, onFulfilled, onRejected);

        if (promise.state == State.PENDING) {
            promise.reactions.add(reaction);
        } else {
            promise.schedule(reaction);
        }
        return derived;
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
        if (!(self instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final Object then = sobj.get("then");
        if (!Bootstrap.isCallable(then)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(then));
        }
        return ScriptRuntime.apply((ScriptFunction)then, sobj, ScriptRuntime.UNDEFINED, onRejected);
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
        final Capability capability = newPromiseCapability(self);
        capability.reject(r);
        return capability.promise();
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
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        final List<Object> values = new ArrayList<>();
        final int[] remaining = { 1 };

        try {
            combine(self, iterable, promised -> {
                final int slot = values.size();
                values.add(ScriptRuntime.UNDEFINED);
                remaining[0]++;
                // ES2015 25.4.4.1.2: a resolve element function takes effect once
                final boolean[] alreadyCalled = { false };
                subscribe(promised,
                    v -> {
                        if (alreadyCalled[0]) {
                            return;
                        }
                        alreadyCalled[0] = true;
                        values.set(slot, v);
                        if (--remaining[0] == 0) {
                            result.resolve(new NativeArray(values.toArray()));
                        }
                    },
                    r -> result.reject(r));
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
     * ECMAScript 2015 25.4.4.3 Promise.race(iterable)
     *
     * @param self     self reference
     * @param iterable the promises to race
     * @return a promise settled like whichever settles first
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object race(final Object self, final Object iterable) {
        requireConstructor(self);
        final Capability result = newPromiseCapability(self);
        try {
            combine(self, iterable,
                    promised -> subscribe(promised, result::resolve, result::reject));
        } catch (final ECMAException e) {
            result.reject(e.getThrown());
        }
        return result.promise();
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
    private record Capability(Object promise, Object resolve, Object reject) {
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
        if (captured[0] != null || captured[1] != null) {
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
        final Object resolver = self instanceof ScriptObject constructor ? constructor.get("resolve") : ScriptRuntime.UNDEFINED;

        Object iterator = null;
        try {
            iterator = AbstractIterator.getIterator(iterable, global);
            final org.openjdk.nashorn.internal.runtime.linker.InvokeByName next = AbstractIterator.getNextInvoker(global);
            final java.lang.invoke.MethodHandle done = AbstractIterator.getDoneInvoker(global);
            final java.lang.invoke.MethodHandle value = AbstractIterator.getValueInvoker(global);

            while (true) {
                final Object step = next.getInvoker().invokeExact(next.getGetter().invokeExact(iterator), iterator, (Object)null);
                if (!(step instanceof ScriptObject)) {
                    throw typeError("not.an.object", ScriptRuntime.safeToString(step));
                }
                if (JSType.toBoolean((Object)done.invokeExact(step))) {
                    return;
                }
                final Object element = (Object)value.invokeExact(step);
                final Object promised = resolver instanceof ScriptFunction resolveFunction
                        ? ScriptRuntime.apply(resolveFunction, self, element)
                        : resolve(self, element);
                onElement.accept(promised);
            }
        } catch (final RuntimeException | Error e) {
            closeIterator(iterator, global);
            throw e;
        } catch (final Throwable t) {
            closeIterator(iterator, global);
            throw new RuntimeException(t);
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
    private static void subscribe(final Object promised, final java.util.function.Consumer<Object> onFulfilled,
            final java.util.function.Consumer<Object> onRejected) {
        if (promised instanceof ScriptObject sobj && sobj.get("then") instanceof ScriptFunction then) {
            ScriptRuntime.apply(then, promised, callback(onFulfilled), callback(onRejected));
            return;
        }
        // not a thenable at all: it counts as already fulfilled with itself
        onFulfilled.accept(promised);
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
        if (x instanceof NativePromise thenable) {
            thenable.onSettled(v -> settle(State.FULFILLED, v), r -> settle(State.REJECTED, r));
            return;
        }
        final Object then;
        try {
            then = x instanceof ScriptObject sobj ? sobj.get("then") : ScriptRuntime.UNDEFINED;
        } catch (final ECMAException e) {
            // a throwing "then" getter rejects, it does not escape
            settle(State.REJECTED, e.getThrown());
            return;
        }
        if (then instanceof ScriptFunction thenFunction) {
            // a foreign thenable is adopted by calling its then with our own
            // resolve and reject, on the queue rather than inline
            global.getJobQueue().enqueue(() -> {
                try {
                    ScriptRuntime.apply(thenFunction, x, resolveFunction(), rejectFunction());
                } catch (final ECMAException e) {
                    settle(State.REJECTED, e.getThrown());
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

        final NativePromise derived = reaction.derived();
        if (!(handler instanceof ScriptFunction function)) {
            // no handler for this outcome: pass it straight through
            if (fulfilled) {
                derived.resolveWith(value);
            } else {
                derived.settle(State.REJECTED, value);
            }
            return;
        }

        try {
            derived.resolveWith(ScriptRuntime.apply(function, ScriptRuntime.UNDEFINED, value));
        } catch (final ECMAException e) {
            derived.settle(State.REJECTED, e.getThrown());
        }
    }

    private ScriptFunction resolveFunction() {
        return settler(true);
    }

    private ScriptFunction rejectFunction() {
        return settler(false);
    }

    /** The resolve/reject pair handed to an executor; each may only take effect once. */
    private ScriptFunction settler(final boolean resolving) {
        final boolean[] used = { false };
        return ScriptFunction.createBuiltin(resolving ? "resolve" : "reject",
                java.lang.invoke.MethodHandles.insertArguments(SETTLE, 0, this, resolving, used));
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
