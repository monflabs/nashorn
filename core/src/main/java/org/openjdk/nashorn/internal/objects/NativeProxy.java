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
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
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
 * ECMAScript 2015 26.2, Proxy.
 *
 * A proxy is a ScriptObject rather than a JSObject on purpose: it has to work as
 * a prototype, as a {@code this}, and be indistinguishable to {@code typeof},
 * none of which a foreign object manages.
 *
 * The traps are consulted from the object's own internal methods. Property
 * lookup through the prototype chain is not intercepted - {@code findProperty}
 * is final and is what the linker and the property-lookup loop use, so leaving
 * it alone is what keeps the monomorphic fast path for ordinary objects
 * untouched. A proxy therefore behaves correctly as the direct target of an
 * operation, which is what almost all use of Proxy is; a proxy sitting in
 * another object's prototype chain is not yet intercepted.
 */
@ScriptClass("Proxy")
public final class NativeProxy extends ScriptObject {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private final ScriptObject target;
    private final ScriptObject handler;

    private NativeProxy(final ScriptObject target, final ScriptObject handler, final Global global) {
        super(global.getObjectPrototype(), $nasgenmap$);
        this.target = target;
        this.handler = handler;
    }

    @Override
    public String getClassName() {
        return target.getClassName();
    }

    /**
     * ECMAScript 2015 26.2.1.1 Proxy(target, handler)
     *
     * @param newObj  is this a new operator invocation
     * @param self    self reference
     * @param target  the object being wrapped
     * @param handler the object holding the traps
     * @return the proxy
     */
    @Constructor(arity = 2)
    public static Object construct(final boolean newObj, final Object self, final Object target,
            final Object handler) {
        if (!newObj) {
            throw typeError("constructor.requires.new", "Proxy");
        }
        if (!(target instanceof ScriptObject targetObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(target));
        }
        if (!(handler instanceof ScriptObject handlerObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(handler));
        }
        return new NativeProxy(targetObject, handlerObject, Global.instance());
    }

    /** The trap of this name, or null when the handler does not define one. */
    private ScriptFunction trap(final String name) {
        final Object value = handler.get(name);
        if (value == null || value == ScriptRuntime.UNDEFINED) {
            return null;
        }
        if (value instanceof ScriptFunction function) {
            return function;
        }
        throw typeError("not.a.function", ScriptRuntime.safeToString(value));
    }

    private Object call(final ScriptFunction trap, final Object... args) {
        return ScriptRuntime.apply(trap, handler, args);
    }

    @Override
    public Object get(final Object key) {
        final ScriptFunction trap = trap("get");
        return trap == null ? target.get(key) : call(trap, target, propertyKey(key), this);
    }

    @Override
    public Object get(final double key) {
        return get((Object)JSType.toObject(key));
    }

    @Override
    public Object get(final int key) {
        return get((Object)Integer.valueOf(key));
    }

    @Override
    public void set(final Object key, final Object value, final int flags) {
        final ScriptFunction trap = trap("set");
        if (trap == null) {
            target.set(key, value, flags);
            return;
        }
        if (!JSType.toBoolean(call(trap, target, propertyKey(key), value, this))
                && NashornCallSiteDescriptorStrictness.isStrict(flags)) {
            throw typeError("cant.set.proto.to.non.object", ScriptRuntime.safeToString(key));
        }
    }

    @Override
    public boolean has(final Object key) {
        final ScriptFunction trap = trap("has");
        return trap == null ? target.has(key) : JSType.toBoolean(call(trap, target, propertyKey(key)));
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final ScriptFunction trap = trap("getOwnPropertyDescriptor");
        if (trap == null) {
            return target.hasOwnProperty(key);
        }
        return call(trap, target, propertyKey(key)) != ScriptRuntime.UNDEFINED;
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final ScriptFunction trap = trap("deleteProperty");
        return trap == null ? target.delete(key, strict)
                : JSType.toBoolean(call(trap, target, propertyKey(key)));
    }

    @Override
    public boolean defineOwnProperty(final Object key, final Object descriptor, final boolean reject) {
        final ScriptFunction trap = trap("defineProperty");
        if (trap == null) {
            return target.defineOwnProperty(key, descriptor, reject);
        }
        final boolean defined = JSType.toBoolean(call(trap, target, propertyKey(key), descriptor));
        if (!defined && reject) {
            throw typeError("cant.redefine.property", ScriptRuntime.safeToString(key),
                    ScriptRuntime.safeToString(this));
        }
        return defined;
    }

    @Override
    public boolean isExtensible() {
        final ScriptFunction trap = trap("isExtensible");
        return trap == null ? target.isExtensible() : JSType.toBoolean(call(trap, target));
    }

    @Override
    public ScriptObject preventExtensions() {
        final ScriptFunction trap = trap("preventExtensions");
        if (trap == null) {
            target.preventExtensions();
        } else {
            call(trap, target);
        }
        return this;
    }

    /**
     * The proxy's own keys, for Object.keys, for..in and Reflect.ownKeys.
     *
     * The trap returns one list holding both strings and symbols; the caller
     * asks for one kind at a time, so the list is filtered to what was asked
     * for.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T[] getOwnKeys(final Class<T> type, final boolean all, final java.util.Set<T> nonEnumerable) {
        final ScriptFunction trap = trap("ownKeys");
        if (trap == null) {
            // the protected three-argument form is not reachable across packages
            final Object[] own = type == Symbol.class ? target.getOwnSymbols(all) : target.getOwnKeys(all);
            return java.util.Arrays.copyOf(own, own.length,
                    (Class<? extends T[]>)java.lang.reflect.Array.newInstance(type, 0).getClass());
        }

        final Object keys = call(trap, target);
        if (!(keys instanceof ScriptObject list)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(keys));
        }
        final long length = JSType.toUint32(list.getLength());
        final java.util.List<T> wanted = new java.util.ArrayList<>();
        for (int i = 0; i < length; i++) {
            final Object key = list.get(i);
            if (type.isInstance(key)) {
                wanted.add((T)key);
            } else if (type == String.class && !(key instanceof Symbol)) {
                wanted.add((T)JSType.toString(key));
            }
        }
        return wanted.toArray((T[])java.lang.reflect.Array.newInstance(type, wanted.size()));
    }

    /**
     * A named read links straight to the get trap.
     *
     * Without this, {@code proxy.foo} would be resolved by the ordinary property
     * lookup and never reach the handler: the linker does not go through
     * {@link #get(Object)}. The call site is left effectively megamorphic, which
     * is expected of a proxy and costs nothing to anything else.
     */
    @Override
    protected jdk.dynalink.linker.GuardedInvocation findGetMethod(final jdk.dynalink.CallSiteDescriptor desc,
            final jdk.dynalink.linker.LinkRequest request) {
        final String name = org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.getOperand(desc);
        if (name == null) {
            return super.findGetMethod(desc, request);
        }
        return new jdk.dynalink.linker.GuardedInvocation(
                java.lang.invoke.MethodHandles.insertArguments(PROXY_GET, 1, (Object)name),
                jdk.dynalink.linker.support.Guards.isOfClass(NativeProxy.class,
                        java.lang.invoke.MethodType.methodType(boolean.class, Object.class)));
    }

    /** A named write links straight to the set trap, for the same reason. */
    @Override
    protected jdk.dynalink.linker.GuardedInvocation findSetMethod(final jdk.dynalink.CallSiteDescriptor desc,
            final jdk.dynalink.linker.LinkRequest request) {
        final String name = org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.getOperand(desc);
        if (name == null) {
            return super.findSetMethod(desc, request);
        }
        final int flags = org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.getFlags(desc);
        return new jdk.dynalink.linker.GuardedInvocation(
                java.lang.invoke.MethodHandles.insertArguments(PROXY_SET, 1, (Object)name, flags),
                jdk.dynalink.linker.support.Guards.isOfClass(NativeProxy.class,
                        java.lang.invoke.MethodType.methodType(boolean.class, Object.class)));
    }

    @SuppressWarnings("unused")
    private static Object proxyGet(final Object self, final Object key) {
        return ((NativeProxy)self).get(key);
    }

    @SuppressWarnings("unused")
    private static void proxySet(final Object self, final Object key, final int flags, final Object value) {
        ((NativeProxy)self).set(key, value, flags);
    }

    private static final java.lang.invoke.MethodHandle PROXY_GET =
            find("proxyGet", Object.class, Object.class, Object.class);
    private static final java.lang.invoke.MethodHandle PROXY_SET =
            find("proxySet", void.class, Object.class, Object.class, int.class, Object.class);

    private static java.lang.invoke.MethodHandle find(final String name, final Class<?> returnType,
            final Class<?>... parameterTypes) {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(NativeProxy.class, name,
                    java.lang.invoke.MethodType.methodType(returnType, parameterTypes));
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The object this proxy wraps, for the operations that need to reach past the traps. */
    public ScriptObject getTarget() {
        return target;
    }

    private static Object propertyKey(final Object key) {
        return key instanceof Symbol ? key : JSType.toPropertyKey(key);
    }

    /** Whether a set was made from strict code, which turns a refused trap into an error. */
    private static final class NashornCallSiteDescriptorStrictness {
        static boolean isStrict(final int flags) {
            return (flags & org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT) != 0;
        }
    }
}
