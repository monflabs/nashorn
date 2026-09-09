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

import java.lang.invoke.MethodHandle;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ES2025 25.1.3 the {@code Iterator} constructor. It is abstract - constructing
 * it directly is a TypeError, only a subclass may - and its {@code .prototype}
 * is %IteratorPrototype% itself (shared, set up in {@link Global}), so a
 * subclass's instances inherit the iterator helpers. {@code Iterator.from}
 * adapts an arbitrary iterable or iterator into one that does.
 */
@ScriptClass("Iterator")
public final class NativeIterator extends ScriptObject {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeIterator(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ES2025 25.1.3.1 Iterator ( ): abstract, so only a subclass may construct.
     *
     * @param isNew whether {@code new} was used
     * @param self  the object under construction (its prototype is new.target's)
     * @param args  ignored
     * @return the constructed object, for a subclass
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object... args) {
        if (!isNew) {
            throw typeError("constructor.requires.new", "Iterator");
        }
        // ES2025 25.1.3.1: Iterator is abstract - throw when new.target is
        // Iterator itself. The runtime records a pending new.target only when a
        // built-in base constructor runs for another (a subclass's super()); a
        // bare `new Iterator()` runs it for itself and leaves none. So no pending
        // new.target here means new.target is Iterator, which is the error.
        if (!Global.instance().hasPendingNewTarget()) {
            throw typeError("abstract.class.instantiation", "Iterator");
        }
        // The object's prototype is the shared %IteratorPrototype%; the runtime
        // re-parents it to the subclass's prototype on return.
        return new NativeIterator(Global.instance().getIteratorPrototype(), $nasgenmap$);
    }

    /**
     * ES2025 25.1.3.2 Iterator.from ( O ): return {@code O} if it is already an
     * iterator that inherits %IteratorPrototype%, otherwise a wrapper that does.
     *
     * @param self the Iterator constructor
     * @param o    an iterable, an iterator, or a string
     * @return an iterator inheriting the helpers
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static Object from(final Object self, final Object o) {
        final Global global = Global.instance();
        final Object iterator = getIteratorFlattenable(o, global);
        // OrdinaryHasInstance(%Iterator%, iterator): does its chain include
        // %IteratorPrototype%?
        if (inheritsIteratorPrototype(iterator, global)) {
            return iterator;
        }
        final Object nextMethod = ((ScriptObject) iterator).get("next");
        return new WrapForValidIterator(iterator, nextMethod, global);
    }

    /**
     * ES2026 Iterator.concat ( ...items ): a fresh iterator that yields, in
     * order, every value of each argument's iterator. Each argument is validated
     * up front - it must be an object with a callable {@code @@iterator} - but its
     * iterator is opened only when the concatenation reaches it.
     *
     * @param self  the Iterator constructor
     * @param items the iterables to concatenate
     * @return an iterator over the concatenation
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 0)
    public static Object concat(final Object self, final Object... items) {
        final Global global = Global.instance();
        final MethodHandle call = AbstractIterator.getIteratorInvoker(global);
        final Object[] iterables = new Object[items.length];
        final Object[] methods = new Object[items.length];
        for (int i = 0; i < items.length; i++) {
            final Object item = items[i];
            if (JSType.isPrimitive(item)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(item));
            }
            final Object method = getMethod((ScriptObject) Global.toObject(item), item, call);
            if (method == ScriptRuntime.UNDEFINED || method == null) {
                throw typeError("not.a.function", ScriptRuntime.safeToString(method));
            }
            if (!Bootstrap.isCallable(method)) {
                throw typeError("not.a.function", ScriptRuntime.safeToString(method));
            }
            iterables[i] = item;
            methods[i] = method;
        }
        return new IteratorHelper(iterables, methods, global);
    }

    /** ES2025 GetIteratorFlattenable(obj, iterate-string-primitives). */
    private static Object getIteratorFlattenable(final Object obj, final Global global) {
        if (JSType.isPrimitive(obj) && !JSType.isString(obj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(obj));
        }
        final ScriptObject object = (ScriptObject) Global.toObject(obj);
        final MethodHandle call = AbstractIterator.getIteratorInvoker(global);
        // GetMethod(obj, @@iterator) is a GetV: the property is read with obj
        // itself as the receiver, so an @@iterator getter sees obj as its this -
        // the string primitive, not the wrapper made only to reach the property.
        final Object method = getMethod(object, obj, call);
        if (method == ScriptRuntime.UNDEFINED || method == null) {
            return object;
        }
        if (!Bootstrap.isCallable(method)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(method));
        }
        try {
            final Object iterator = call.invokeExact(method, obj);
            if (JSType.isPrimitive(iterator)) {
                throw typeError("not.an.object", ScriptRuntime.safeToString(iterator));
            }
            return iterator;
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * GetMethod(receiver, @@iterator): looks the property up on {@code wrapper}
     * (ToObject of the receiver) but, when it is an accessor, runs the getter
     * with the original {@code receiver} as its this, per GetV. For a string
     * primitive that keeps the getter's this the primitive rather than a wrapper.
     */
    private static Object getMethod(final ScriptObject wrapper, final Object receiver, final MethodHandle call) {
        if (!JSType.isPrimitive(receiver)) {
            // receiver is already an object (its own ToObject): an ordinary
            // [[Get]] reads it with itself as the receiver and runs any proxy
            // trap, which a test observes. Only a primitive needs the wrapper.
            return wrapper.get(NativeSymbol.iterator);
        }
        final org.monflabs.nashorn.internal.runtime.FindProperty found = wrapper.findProperty(NativeSymbol.iterator, true);
        if (found == null) {
            return ScriptRuntime.UNDEFINED;
        }
        if (found.getProperty().isAccessorProperty()) {
            final org.monflabs.nashorn.internal.runtime.ScriptFunction getter = found.getProperty().getGetterFunction(found.getOwner());
            if (getter == null) {
                return ScriptRuntime.UNDEFINED;
            }
            try {
                return call.invokeExact((Object) getter, receiver);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return found.getObjectValue();
    }

    private static boolean inheritsIteratorPrototype(final Object iterator, final Global global) {
        final ScriptObject iteratorPrototype = global.getIteratorPrototype();
        for (ScriptObject proto = ((ScriptObject) iterator).getProto(); proto != null; proto = proto.getProto()) {
            if (proto == iteratorPrototype) {
                return true;
            }
        }
        return false;
    }
}
