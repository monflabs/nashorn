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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.support.Guards;
import org.openjdk.nashorn.internal.runtime.ConsString;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyDescriptor;
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

    private ScriptObject target;
    private ScriptObject handler;

    /** Whether this proxy has a [[Call]] and a [[Construct]], decided when it was made. */
    private final boolean callable;
    private final boolean constructor;

    private NativeProxy(final ScriptObject target, final ScriptObject handler, final Global global) {
        super(global.getObjectPrototype(), $nasgenmap$);
        this.target = target;
        this.handler = handler;
        // ES2015 9.5.12 and 9.5.13 decide once, when the proxy is made, whether
        // it has a [[Call]] and a [[Construct]]. Revoking one takes its target
        // away but does not turn a function into an object.
        this.callable = target instanceof ScriptFunction || target.isProxyOverCallable();
        this.constructor = target instanceof ScriptFunction function
                ? function.isConstructor()
                : target.isProxyOverConstructor();
    }

    /**
     * ECMAScript 2015 26.2.2.1 Proxy.revocable(target, handler).
     *
     * @param self    self reference
     * @param target  the object being wrapped
     * @param handler the object holding the traps
     * @return an object holding the proxy and the function that switches it off
     */
    @Function(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object revocable(final Object self, final Object target, final Object handler) {
        final NativeProxy proxy = (NativeProxy)construct(true, self, target, handler);
        final ScriptObject result = Global.newEmptyInstance();
        result.set("proxy", proxy, 0);
        result.set("revoke", ScriptFunction.createBuiltin("", REVOKE.bindTo(proxy)), 0);
        return result;
    }

    @SuppressWarnings("unused")
    private static Object revoke(final NativeProxy proxy, final Object self) {
        // 26.2.2.1.1: revoking twice is not an error, it simply does nothing more
        proxy.target = null;
        proxy.handler = null;
        return ScriptRuntime.UNDEFINED;
    }

    private static final java.lang.invoke.MethodHandle REVOKE =
            find("revoke", Object.class, NativeProxy.class, Object.class);

    /**
     * The handler, or a TypeError if this proxy has been revoked.
     *
     * ES2015 26.2.2.1.1 leaves a revoked proxy with no target and no handler, and
     * every internal method on one throws.
     */
    private ScriptObject handler() {
        if (handler == null) {
            throw typeError("proxy.revoked");
        }
        return handler;
    }

    /** The target, or a TypeError if this proxy has been revoked. */
    private ScriptObject target() {
        if (target == null) {
            throw typeError("proxy.revoked");
        }
        return target;
    }

    /** Whether this proxy has been revoked, which leaves it with no target. */
    public boolean isRevoked() {
        return target == null;
    }

    @Override
    public String getClassName() {
        return target == null ? "Object" : target.getClassName();
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
        final Object value = handler().get(name);
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
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.get(key);
        }
        final Object answered = call(trap, rx, propertyKey(key), this);

        // ES2015 9.5.8 steps 10 and 11: a property the target has fixed - one
        // that can be neither reconfigured nor written - reads as what the
        // target holds, whatever the trap says, and an accessor with no getter
        // reads as undefined.
        if (rx.getOwnPropertyDescriptor(key) instanceof PropertyDescriptor onTarget
                && !onTarget.isConfigurable()) {
            if (onTarget.type() == PropertyDescriptor.DATA && !onTarget.isWritable()
                    && !ScriptRuntime.sameValue(answered, onTarget.getValue())) {
                throw typeError("proxy.get.not.same.value", ScriptRuntime.safeToString(key));
            }
            if (onTarget.type() == PropertyDescriptor.ACCESSOR
                    && onTarget.getGetter() == null
                    && answered != ScriptRuntime.UNDEFINED) {
                throw typeError("proxy.get.not.same.value", ScriptRuntime.safeToString(key));
            }
        }
        return answered;
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
    public boolean has(final double key) {
        return has((Object)JSType.toObject(key));
    }

    @Override
    public boolean has(final int key) {
        return has((Object)Integer.valueOf(key));
    }

    @Override
    public void set(final double key, final Object value, final int flags) {
        set((Object)JSType.toObject(key), value, flags);
    }

    @Override
    public void set(final int key, final Object value, final int flags) {
        set((Object)Integer.valueOf(key), value, flags);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        return delete((Object)Integer.valueOf(key), strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        return delete((Object)JSType.toObject(key), strict);
    }

    @Override
    public void set(final Object key, final Object value, final int flags) {
        final ScriptFunction trap = trap("set");
        if (trap == null) {
            target().set(key, value, flags);
            return;
        }
        final ScriptObject rx = target();
        if (!JSType.toBoolean(call(trap, rx, propertyKey(key), value, this))) {
            if (NashornCallSiteDescriptorStrictness.isStrict(flags)) {
                throw typeError("property.not.writable", ScriptRuntime.safeToString(key),
                        ScriptRuntime.safeToString(this));
            }
            return;
        }

        // ES2015 9.5.9 step 13: a write the target would not have allowed
        // cannot be reported as having happened
        if (rx.getOwnPropertyDescriptor(key) instanceof PropertyDescriptor onTarget
                && !onTarget.isConfigurable()) {
            if (onTarget.type() == PropertyDescriptor.DATA && !onTarget.isWritable()
                    && !ScriptRuntime.sameValue(value, onTarget.getValue())) {
                throw typeError("proxy.set.not.same.value", ScriptRuntime.safeToString(key));
            }
            if (onTarget.type() == PropertyDescriptor.ACCESSOR
                    && onTarget.getSetter() == null) {
                throw typeError("proxy.set.not.same.value", ScriptRuntime.safeToString(key));
            }
        }
    }

    @Override
    public boolean has(final Object key) {
        final ScriptFunction trap = trap("has");
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.has(key);
        }
        final boolean answered = JSType.toBoolean(call(trap, rx, propertyKey(key)));

        // ES2015 9.5.7 step 9: a property the target will not give up cannot be
        // denied, and neither can any of them once the target can take no more
        if (!answered && rx.getOwnPropertyDescriptor(key) instanceof PropertyDescriptor onTarget) {
            if (!onTarget.isConfigurable() || !rx.isExtensible()) {
                throw typeError("proxy.descriptor.hidden", ScriptRuntime.safeToString(key));
            }
        }
        return answered;
    }

    /**
     * A proxy reached as somebody else's prototype is asked the same way it
     * would be asked directly: 9.5.7 is the whole answer, and walks whatever
     * chain its target has itself.
     */
    @Override
    protected boolean hasProperty(final Object key, final boolean deep) {
        return has(key);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        return getOwnPropertyDescriptor(key) != ScriptRuntime.UNDEFINED;
    }

    /**
     * ES2015 9.5.5 [[GetOwnProperty]].
     *
     * The trap may describe the property however it likes, within what the
     * target will vouch for: it may not deny a property the target holds and
     * will not give up, nor call a property its own that a target with no such
     * property cannot be given one, nor say that something is fixed which the
     * target would still let change.
     */
    @Override
    public Object getOwnPropertyDescriptor(final Object key) {
        final ScriptFunction trap = trap("getOwnPropertyDescriptor");
        final ScriptObject target = target();
        if (trap == null) {
            return target.getOwnPropertyDescriptor(key);
        }

        final Object answered = call(trap, target, propertyKey(key));
        if (answered != ScriptRuntime.UNDEFINED && !(answered instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(answered));
        }

        final Object targetDescriptor = target.getOwnPropertyDescriptor(key);
        final PropertyDescriptor onTarget = targetDescriptor instanceof PropertyDescriptor descriptor
                ? descriptor : null;

        if (answered == ScriptRuntime.UNDEFINED) {
            if (onTarget == null) {
                return ScriptRuntime.UNDEFINED;
            }
            if (!onTarget.isConfigurable()) {
                throw typeError("proxy.descriptor.hidden", ScriptRuntime.safeToString(key));
            }
            if (!target.isExtensible()) {
                throw typeError("proxy.descriptor.hidden", ScriptRuntime.safeToString(key));
            }
            return ScriptRuntime.UNDEFINED;
        }

        final PropertyDescriptor result = toPropertyDescriptor(Global.instance(), answered);
        if (!result.has(PropertyDescriptor.CONFIGURABLE) || !result.isConfigurable()) {
            // 9.5.5 step 16: only a property the target itself will not let go
            // of may be reported as one that cannot be reconfigured
            if (onTarget == null || onTarget.isConfigurable()) {
                throw typeError("proxy.descriptor.not.configurable", ScriptRuntime.safeToString(key));
            }
        }
        return result;
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final ScriptFunction trap = trap("deleteProperty");
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.delete(key, strict);
        }
        if (!JSType.toBoolean(call(trap, rx, propertyKey(key)))) {
            return false;
        }
        // 9.5.10 step 11: a property the target will not let go of cannot be
        // reported as deleted
        if (describe(rx, key) instanceof PropertyDescriptor onTarget && !onTarget.isConfigurable()) {
            throw typeError("proxy.delete.not.configurable", ScriptRuntime.safeToString(key));
        }
        return true;
    }

    @Override
    public boolean defineOwnProperty(final Object key, final Object descriptor, final boolean reject) {
        final ScriptFunction trap = trap("defineProperty");
        if (trap == null) {
            return target().defineOwnProperty(key, descriptor, reject);
        }
        final ScriptObject rx = target();
        if (!JSType.toBoolean(call(trap, rx, propertyKey(key), descriptor))) {
            if (reject) {
                throw typeError("cant.redefine.property", ScriptRuntime.safeToString(key),
                        ScriptRuntime.safeToString(this));
            }
            return false;
        }

        // 9.5.6 steps 15 to 19: whatever the handler says it did, the target
        // must be able to agree with
        final PropertyDescriptor asked = toPropertyDescriptor(Global.instance(), descriptor);
        final boolean makingItFinal = asked.has(PropertyDescriptor.CONFIGURABLE) && !asked.isConfigurable();
        final PropertyDescriptor onTarget = describe(rx, key);
        if (onTarget == null) {
            if (!rx.isExtensible() || makingItFinal) {
                throw typeError("proxy.define.not.compatible", ScriptRuntime.safeToString(key));
            }
        } else {
            if (!compatible(rx.isExtensible(), asked, onTarget)
                    || makingItFinal && onTarget.isConfigurable()) {
                throw typeError("proxy.define.not.compatible", ScriptRuntime.safeToString(key));
            }
        }
        return true;
    }

    /**
     * ES2015 9.5.1 [[GetPrototypeOf]] and 9.5.2 [[SetPrototypeOf]].
     *
     * The linker's own {@link #getProto()} is left alone - it is what the
     * property lookup walks - so a proxy's answer is only seen by the operations
     * a script can reach, which is what the specification describes.
     */
    @Override
    public ScriptObject getPrototypeOf() {
        final ScriptFunction trap = trap("getPrototypeOf");
        if (trap == null) {
            return target().getPrototypeOf();
        }
        final ScriptObject rx = target();
        final Object proto = call(trap, rx);
        if (proto != null && !(proto instanceof ScriptObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(proto));
        }
        // 9.5.1 step 9: a target that can no longer be reparented has the
        // prototype it has, and the trap cannot say otherwise
        if (!rx.isExtensible() && proto != rx.getPrototypeOf()) {
            throw typeError("proxy.proto.mismatch", ScriptRuntime.safeToString(this));
        }
        return (ScriptObject)proto;
    }

    @Override
    public void setPrototypeOf(final Object newProto) {
        if (!trySetPrototypeOf(newProto)) {
            throw typeError("cant.set.proto.to.non.object", ScriptRuntime.safeToString(this));
        }
    }

    @Override
    public boolean trySetPrototypeOf(final Object newProto) {
        final ScriptFunction trap = trap("setPrototypeOf");
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.trySetPrototypeOf(newProto);
        }
        if (!JSType.toBoolean(call(trap, rx, newProto))) {
            return false;
        }
        // 9.5.2 step 11, as for the getter: a non-extensible target keeps the
        // prototype it has
        if (!rx.isExtensible() && newProto != rx.getPrototypeOf()) {
            throw typeError("proxy.proto.mismatch", ScriptRuntime.safeToString(this));
        }
        return true;
    }

    /** Whether the object behind however many proxies is an array (ES2015 7.2.2). */
    ScriptObject unwrap() {
        final ScriptObject rx = target();
        return rx instanceof NativeProxy proxy ? proxy.unwrap() : rx;
    }

    @Override
    public boolean isExtensible() {
        final ScriptFunction trap = trap("isExtensible");
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.isExtensible();
        }
        // 9.5.3 step 8: this is the one trap that may not lie at all
        final boolean answered = JSType.toBoolean(call(trap, rx));
        if (answered != rx.isExtensible()) {
            throw typeError("proxy.extensible.mismatch", ScriptRuntime.safeToString(this));
        }
        return answered;
    }

    @Override
    public ScriptObject preventExtensions() {
        if (!tryPreventExtensions()) {
            throw typeError("proxy.not.prevented", ScriptRuntime.safeToString(this));
        }
        return this;
    }

    @Override
    public boolean tryPreventExtensions() {
        final ScriptFunction trap = trap("preventExtensions");
        final ScriptObject rx = target();
        if (trap == null) {
            return rx.tryPreventExtensions();
        }
        if (!JSType.toBoolean(call(trap, rx))) {
            return false;
        }
        // 9.5.4 step 8: saying it happened when the target is still extensible
        if (rx.isExtensible()) {
            throw typeError("proxy.extensible.mismatch", ScriptRuntime.safeToString(this));
        }
        return true;
    }

    /** The target's own descriptor for a key, or null if it has none. */
    private static PropertyDescriptor describe(final ScriptObject rx, final Object key) {
        return rx.getOwnPropertyDescriptor(key) instanceof PropertyDescriptor descriptor ? descriptor : null;
    }

    /**
     * ES2015 9.1.6.2 IsCompatiblePropertyDescriptor, which is
     * ValidateAndApplyPropertyDescriptor asked whether it would succeed rather
     * than told to go ahead.
     */
    private static boolean compatible(final boolean extensible, final PropertyDescriptor asked,
            final PropertyDescriptor current) {
        if (current.isConfigurable()) {
            return true;
        }
        if (asked.has(PropertyDescriptor.CONFIGURABLE) && asked.isConfigurable()) {
            return false;
        }
        if (asked.has(PropertyDescriptor.ENUMERABLE) && asked.isEnumerable() != current.isEnumerable()) {
            return false;
        }
        if (asked.type() == PropertyDescriptor.GENERIC) {
            return true;
        }
        if (asked.type() != current.type()) {
            // a non-configurable property cannot change between data and accessor
            return false;
        }
        if (current.type() == PropertyDescriptor.ACCESSOR) {
            return (!asked.has(PropertyDescriptor.GET) || asked.getGetter() == current.getGetter())
                    && (!asked.has(PropertyDescriptor.SET) || asked.getSetter() == current.getSetter());
        }
        if (current.isWritable()) {
            return true;
        }
        return (!asked.has(PropertyDescriptor.WRITABLE) || !asked.isWritable())
                && (!asked.has(PropertyDescriptor.VALUE)
                        || ScriptRuntime.sameValue(asked.getValue(), current.getValue()));
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
            final Object[] own = type == Symbol.class ? target().getOwnSymbols(all) : target().getOwnKeys(all);
            return java.util.Arrays.copyOf(own, own.length,
                    (Class<? extends T[]>)java.lang.reflect.Array.newInstance(type, 0).getClass());
        }

        final ScriptObject target = target();
        final Object keys = call(trap, target);
        if (!(keys instanceof ScriptObject list)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(keys));
        }

        // ES2015 9.5.11 step 8: what comes back is a list of property keys and
        // nothing else, and no key twice.
        final long length = JSType.toUint32(list.getLength());
        final java.util.List<Object> answered = new java.util.ArrayList<>();
        final java.util.Set<Object> seen = new java.util.HashSet<>();
        for (int i = 0; i < length; i++) {
            final Object key = list.get(i);
            final Object propertyKey;
            if (key instanceof Symbol) {
                propertyKey = key;
            } else if (key instanceof String || key instanceof ConsString) {
                propertyKey = key.toString();
            } else {
                throw typeError("proxy.keys.not.a.property.key", ScriptRuntime.safeToString(key));
            }
            if (!seen.add(propertyKey)) {
                throw typeError("proxy.keys.duplicate", ScriptRuntime.safeToString(key));
            }
            answered.add(propertyKey);
        }

        checkOwnKeysAgainstTarget(target, seen);

        final java.util.List<T> wanted = new java.util.ArrayList<>();
        for (final Object key : answered) {
            if (type.isInstance(key)) {
                wanted.add((T)key);
            }
        }
        return wanted.toArray((T[])java.lang.reflect.Array.newInstance(type, wanted.size()));
    }

    /*
     * ES2015 9.5.12 [[Call]] and 9.5.13 [[Construct]]: a proxy is callable when
     * its target is, and constructible when its target is, so a proxy over a
     * function has to link as one. Nothing else about a proxy is a function -
     * it is not a ScriptFunction and has no function prototype - so the two
     * hooks the linker asks are answered here rather than by inheriting them.
     */

    /**
     * ES2015 7.3.14 SetIntegrityLevel, for {@code Object.seal} and
     * {@code Object.freeze}.
     *
     * A proxy has no property map to seal - the properties it appears to have
     * belong to whatever its traps answer with - so the generic algorithm is
     * the only one available: prevent extensions, then redefine every own key
     * as non-configurable, one call through the traps each. A trap that refuses
     * a redefinition makes the whole operation a TypeError, which is how a
     * handler gets to veto being frozen.
     */
    private ScriptObject setIntegrityLevel(final boolean frozen) {
        preventExtensions();
        if (isExtensible()) {
            throw typeError("proxy.not.prevented", ScriptRuntime.safeToString(this));
        }
        for (final Object key : ownPropertyKeys()) {
            final ScriptObject attributes = Global.newEmptyInstance();
            if (frozen) {
                final Object existing = getOwnPropertyDescriptor(key);
                if (existing == ScriptRuntime.UNDEFINED) {
                    continue;
                }
                if (!((ScriptObject)existing).has("value")) {
                    // an accessor keeps its functions, and only stops being configurable
                    attributes.set("configurable", false, 0);
                    defineOwnProperty(key, attributes, true);
                    continue;
                }
                attributes.set("writable", false, 0);
            }
            attributes.set("configurable", false, 0);
            defineOwnProperty(key, attributes, true);
        }
        return this;
    }

    /**
     * ES2015 7.3.15 TestIntegrityLevel, for {@code Object.isSealed} and
     * {@code Object.isFrozen}. Like the setter, it can only ask the traps.
     */
    private boolean testIntegrityLevel(final boolean frozen) {
        if (isExtensible()) {
            return false;
        }
        for (final Object key : ownPropertyKeys()) {
            final Object described = getOwnPropertyDescriptor(key);
            if (described == ScriptRuntime.UNDEFINED) {
                continue;
            }
            final ScriptObject desc = (ScriptObject)described;
            if (JSType.toBoolean(desc.get("configurable"))) {
                return false;
            }
            if (frozen && desc.has("value") && JSType.toBoolean(desc.get("writable"))) {
                return false;
            }
        }
        return true;
    }

    /** Every own key the ownKeys trap reports, strings and symbols alike. */
    private java.util.List<Object> ownPropertyKeys() {
        final java.util.List<Object> keys = new java.util.ArrayList<>();
        keys.addAll(java.util.Arrays.asList(getOwnKeys(true)));
        keys.addAll(java.util.Arrays.asList(getOwnSymbols(true)));
        return keys;
    }

    @Override
    public ScriptObject seal() {
        return setIntegrityLevel(false);
    }

    @Override
    public ScriptObject freeze() {
        return setIntegrityLevel(true);
    }

    @Override
    public boolean isSealed() {
        return testIntegrityLevel(false);
    }

    @Override
    public boolean isFrozen() {
        return testIntegrityLevel(true);
    }

    @Override
    public boolean isProxyOverCallable() {
        return callable;
    }

    @Override
    public boolean isProxyOverConstructor() {
        return constructor;
    }

    @Override
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        if (!isProxyOverCallable()) {
            return super.findCallMethod(desc, request);
        }
        return invocation(APPLY, desc, 2);
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        if (!isProxyOverConstructor()) {
            return super.findNewMethod(desc, request);
        }
        return invocation(CONSTRUCT, desc, 1);
    }

    /** The call site, with everything past the fixed arguments gathered into an array. */
    private static GuardedInvocation invocation(final MethodHandle handle, final CallSiteDescriptor desc,
            final int fixed) {
        final MethodType type = desc.getMethodType();
        final int count = type.parameterCount();
        // The apply-to-call machinery asks with the arguments already gathered
        // into an array, where an ordinary call site names them one by one.
        final boolean gathered = count == fixed + 1 && type.parameterType(count - 1) == Object[].class;
        final MethodHandle bound = gathered
                ? handle.asType(type)
                : handle.asCollector(Object[].class, Math.max(count - fixed, 0)).asType(type);
        return new GuardedInvocation(bound, Guards.isInstance(NativeProxy.class, type));
    }

    @SuppressWarnings("unused")
    private static Object apply(final Object self, final Object thisArg, final Object[] args) {
        final NativeProxy proxy = (NativeProxy)self;
        final ScriptFunction trap = proxy.trap("apply");
        final ScriptObject target = proxy.target();
        if (trap == null) {
            return target instanceof ScriptFunction
                    ? ScriptRuntime.call(target, thisArg, args)
                    : apply(target, thisArg, args);
        }
        return proxy.call(trap, target, thisArg, new NativeArray(args.clone()));
    }

    @SuppressWarnings("unused")
    private static Object construct(final Object self, final Object[] args) {
        final NativeProxy proxy = (NativeProxy)self;
        final ScriptFunction trap = proxy.trap("construct");
        final ScriptObject target = proxy.target();
        if (trap == null) {
            return target instanceof ScriptFunction function
                    ? ScriptRuntime.construct(function, args)
                    : construct(target, args);
        }
        // 9.5.13 step 9: what a construct trap answers with has to be an object
        final Object created = proxy.call(trap, target, new NativeArray(args.clone()), proxy);
        if (!(created instanceof ScriptObject)) {
            throw typeError("proxy.construct.not.an.object", ScriptRuntime.safeToString(created));
        }
        return created;
    }

    private static final MethodHandle APPLY = find("apply",
            MethodType.methodType(Object.class, Object.class, Object.class, Object[].class));
    private static final MethodHandle CONSTRUCT = find("construct",
            MethodType.methodType(Object.class, Object.class, Object[].class));

    private static MethodHandle find(final String name, final MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(NativeProxy.class, name, type);
        } catch (final ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /**
     * ES2015 9.5.11 steps 9 to 21: what the target will not let the trap hide.
     *
     * A key the target has and will not let go of - one that is not
     * configurable - has to be in the answer. If the target is not extensible
     * then every key it has must be in the answer and nothing else may be,
     * because the set of keys can no longer change.
     */
    private static void checkOwnKeysAgainstTarget(final ScriptObject target, final java.util.Set<Object> answered) {
        final boolean extensible = target.isExtensible();
        final java.util.Set<Object> own = new java.util.HashSet<>();

        for (final Object key : target.getOwnKeys(true)) {
            own.add(key.toString());
        }
        for (final Object key : target.getOwnSymbols(true)) {
            own.add(key);
        }

        for (final Object key : own) {
            if (answered.contains(key)) {
                continue;
            }
            if (!extensible) {
                throw typeError("proxy.keys.missing", ScriptRuntime.safeToString(key));
            }
            final Object descriptor = target.getOwnPropertyDescriptor(key);
            if (descriptor instanceof PropertyDescriptor property && !property.isConfigurable()) {
                throw typeError("proxy.keys.missing", ScriptRuntime.safeToString(key));
            }
        }

        if (!extensible) {
            for (final Object key : answered) {
                if (!own.contains(key)) {
                    throw typeError("proxy.keys.extra", ScriptRuntime.safeToString(key));
                }
            }
        }
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
