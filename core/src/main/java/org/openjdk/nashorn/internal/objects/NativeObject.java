/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.dynalink.StandardNamespace.METHOD;
import static jdk.dynalink.StandardNamespace.PROPERTY;
import static jdk.dynalink.StandardOperation.GET;
import static jdk.dynalink.StandardOperation.SET;
import static org.openjdk.nashorn.internal.lookup.Lookup.MH;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.beans.BeansLinker;
import jdk.dynalink.beans.StaticClass;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.support.SimpleLinkRequest;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.openjdk.nashorn.internal.lookup.Lookup;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.AccessorProperty;
import org.openjdk.nashorn.internal.runtime.ECMAException;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.Property;
import org.openjdk.nashorn.internal.runtime.PropertyDescriptor;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.Symbol;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayData;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayIndex;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;
import org.openjdk.nashorn.internal.runtime.linker.InvokeByName;
import org.openjdk.nashorn.internal.runtime.linker.NashornBeansLinker;
import org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * ECMA 15.2 Object objects
 *
 * JavaScript Object constructor/prototype. Note: instances of this class are
 * never created. This class is not even a subclass of ScriptObject. But, we use
 * this class to generate prototype and constructor for "Object".
 *
 */
@ScriptClass("Object")
public final class NativeObject {
    /** Methodhandle to proto getter */
    public static final MethodHandle GET__PROTO__ = findOwnMH("get__proto__", ScriptObject.class, Object.class);

    /** Methodhandle to proto setter */
    public static final MethodHandle SET__PROTO__ = findOwnMH("set__proto__", Object.class, Object.class, Object.class);

    private static final Object TO_STRING = new Object();

    private static InvokeByName getTO_STRING() {
        return Global.instance().getInvokeByName(TO_STRING, () ->
            new InvokeByName("toString", Object.class));
    }

    private static final Operation GET_METHOD   = GET.withNamespace(METHOD);
    private static final Operation GET_PROPERTY = GET.withNamespace(PROPERTY);
    private static final Operation SET_PROPERTY = SET.withNamespace(PROPERTY);

    @SuppressWarnings("unused")
    private static ScriptObject get__proto__(final Object self) {
        // See ES6 draft spec: B.2.2.1.1 get Object.prototype.__proto__
        // Step 1 Let O be the result of calling ToObject passing the this.
        final ScriptObject sobj = Global.checkObject(Global.toObject(self));
        // Step 2 is [[GetPrototypeOf]], which a proxy answers with its own trap
        // rather than with the prototype it was made on - and which may throw
        return sobj.getPrototypeOf();
    }

    @SuppressWarnings("unused")
    private static Object set__proto__(final Object self, final Object proto) {
        // See ES6 draft spec: B.2.2.1.2 set Object.prototype.__proto__
        // Step 1
        Global.checkObjectCoercible(self);
        // Step 4
        if (! (self instanceof ScriptObject)) {
            return UNDEFINED;
        }

        final ScriptObject sobj = (ScriptObject)self;
        // __proto__ assignment ignores non-nulls and non-objects
        // step 3: If Type(proto) is neither Object nor Null, then return undefined.
        if (proto == null || proto instanceof ScriptObject) {
            sobj.setPrototypeOf(proto);
        }
        return UNDEFINED;
    }

    private static final MethodType MIRROR_GETTER_TYPE = MethodType.methodType(Object.class, ScriptObjectMirror.class);
    private static final MethodType MIRROR_SETTER_TYPE = MethodType.methodType(Object.class, ScriptObjectMirror.class, Object.class);

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private NativeObject() {
        // don't create me!
        throw new UnsupportedOperationException();
    }

    private static ECMAException notAnObject(final Object obj) {
        return typeError("not.an.object", ScriptRuntime.safeToString(obj));
    }

    /**
     * Nashorn extension: setIndexedPropertiesToExternalArrayData
     *
     * @param self self reference
     * @param obj object whose index properties are backed by buffer
     * @param buf external buffer - should be a nio ByteBuffer
     * @return the 'obj' object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject setIndexedPropertiesToExternalArrayData(final Object self, final Object obj, final Object buf) {
        Global.checkObject(obj);
        final ScriptObject sobj = (ScriptObject)obj;
        if (buf instanceof ByteBuffer) {
            sobj.setArray(ArrayData.allocate((ByteBuffer)buf));
        } else {
            throw typeError("not.a.bytebuffer", "setIndexedPropertiesToExternalArrayData's buf argument");
        }
        return sobj;
    }


    /**
     * ECMA 15.2.3.2 Object.getPrototypeOf ( O )
     *
     * @param  self self reference
     * @param  obj object to get prototype from
     * @return the prototype of an object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getPrototypeOf(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).getPrototypeOf();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).getProto();
        }

        // ES2015 19.1.2.9 coerces rather than rejecting, so a primitive is
        // answered with its wrapper's prototype
        final Object coerced = JSType.toScriptObject(Global.instance(), obj);
        if (coerced instanceof ScriptObject) {
            return ((ScriptObject)coerced).getPrototypeOf();
        }
        // host (Java) objects have null __proto__
        return null;
    }

    /**
     * Nashorn extension: Object.setPrototypeOf ( O, proto )
     * Also found in ES6 draft specification.
     *
     * @param  self self reference
     * @param  obj object to set prototype for
     * @param  proto prototype object to be used
     * @return object whose prototype is set
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object setPrototypeOf(final Object self, final Object obj, final Object proto) {
        Global.checkObjectCoercible(obj);

        if (obj instanceof ScriptObject) {
            ((ScriptObject)obj).setPrototypeOf(proto);
        } else if (obj instanceof ScriptObjectMirror) {
            ((ScriptObjectMirror)obj).setProto(proto);
        }

        return obj;
    }

    /**
     * ECMA 15.2.3.3 Object.getOwnPropertyDescriptor ( O, P )
     *
     * @param self  self reference
     * @param obj   object from which to get property descriptor for {@code ToString(prop)}
     * @param prop  property descriptor
     * @return property descriptor
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object getOwnPropertyDescriptor(final Object self, final Object obj, final Object prop) {
        if (obj instanceof ScriptObject) {
            // ES2015 19.1.2.6 takes a property key, which is a string or a
            // symbol. Coercing to a string threw for every symbol there is, so
            // no symbol-keyed property could be described at all.
            final Object       key  = JSType.toPropertyKey(prop);
            final ScriptObject sobj = (ScriptObject)obj;

            return sobj.getOwnPropertyDescriptor(key);
        } else if (obj instanceof ScriptObjectMirror) {
            final String       key  = JSType.toString(prop);
            final ScriptObjectMirror sobjMirror = (ScriptObjectMirror)obj;

            return sobjMirror.getOwnPropertyDescriptor(key);
        }

        // ES2015 19.1.2.6 coerces rather than rejecting
        final Object coerced = JSType.toScriptObject(Global.instance(), obj);
        if (coerced instanceof ScriptObject) {
            return ((ScriptObject)coerced).getOwnPropertyDescriptor(JSType.toPropertyKey(prop));
        }
        return UNDEFINED;
    }

    /**
     * ECMA 15.2.3.4 Object.getOwnPropertyNames ( O )
     *
     * @param self self reference
     * @param obj  object to query for property names
     * @return array of property names
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject getOwnPropertyNames(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return new NativeArray(((ScriptObject)obj).getOwnKeys(true));
        } else if (obj instanceof ScriptObjectMirror) {
            return new NativeArray(((ScriptObjectMirror)obj).getOwnKeys(true));
        }
        // ES2015 coerces a primitive rather than rejecting it
        final var global = Global.instance();
        final var coerced = JSType.toScriptObject(global, obj);
        if (coerced instanceof ScriptObject) {
            return new NativeArray(((ScriptObject)coerced).getOwnKeys(true));
        }
        return new NativeArray();
    }

    /**
     * ECMA 2 19.1.2.8 Object.getOwnPropertySymbols ( O )
     *
     * @param self self reference
     * @param obj  object to query for property names
     * @return array of property names
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject getOwnPropertySymbols(final Object self, final Object obj) {
        final var obj2 = JSType.toScriptObject(obj);
        if (obj2 instanceof ScriptObject) {
            return new NativeArray(((ScriptObject)obj2).getOwnSymbols(true));
        }
        // TODO: we don't support this on ScriptObjectMirror objects yet
        return new NativeArray();
    }

    /**
     * ECMA 15.2.3.5 Object.create ( O [, Properties] )
     *
     * @param self  self reference
     * @param proto prototype object
     * @param props properties to define
     * @return object created
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject create(final Object self, final Object proto, final Object props) {
        if (proto != null) {
            Global.checkObject(proto);
        }

        // FIXME: should we create a proper object with correct number of
        // properties?
        final ScriptObject newObj = Global.newEmptyInstance();
        newObj.setProto((ScriptObject)proto);
        if (props != UNDEFINED) {
            NativeObject.defineProperties(self, newObj, props);
        }

        return newObj;
    }

    /**
     * ECMA 15.2.3.6 Object.defineProperty ( O, P, Attributes )
     *
     * @param self self reference
     * @param obj  object in which to define a property
     * @param prop property to define
     * @param attr attributes for property descriptor
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject defineProperty(final Object self, final Object obj, final Object prop, final Object attr) {
        final ScriptObject sobj = Global.checkObject(obj);
        sobj.defineOwnProperty(JSType.toPropertyKey(prop), attr, true);
        return sobj;
    }

    /**
     * ECMA 5.2.3.7 Object.defineProperties ( O, Properties )
     *
     * @param self  self reference
     * @param obj   object in which to define properties
     * @param props properties
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject defineProperties(final Object self, final Object obj, final Object props) {
        final ScriptObject sobj     = Global.checkObject(obj);
        final Object       propsObj = Global.toObject(props);

        if (propsObj instanceof ScriptObject properties) {
            // ES2015 19.1.2.3 walks OwnPropertyKeys, which is the string keys
            // and then the symbol ones - a descriptor can be filed under either
            for (final Object key : properties.getOwnKeys(false)) {
                sobj.defineOwnProperty(JSType.toPropertyKey(key), properties.get(key), true);
            }
            for (final Object key : properties.getOwnSymbols(false)) {
                sobj.defineOwnProperty(key, properties.get(key), true);
            }
        }
        return sobj;
    }

    /**
     * ECMA 15.2.3.8 Object.seal ( O )
     *
     * @param self self reference
     * @param obj  object to seal
     * @return sealed object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object seal(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).seal();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).seal();
        }
        // ES2015: a no-op on a non-object rather than a TypeError
        return obj;
    }


    /**
     * ECMA 15.2.3.9 Object.freeze ( O )
     *
     * @param self self reference
     * @param obj object to freeze
     * @return frozen object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object freeze(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).freeze();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).freeze();
        }
        // ES2015: a no-op on a non-object rather than a TypeError
        return obj;
    }

    /**
     * ECMA 15.2.3.10 Object.preventExtensions ( O )
     *
     * @param self self reference
     * @param obj  object, for which to set the internal extensible property to false
     * @return object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object preventExtensions(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).preventExtensions();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).preventExtensions();
        }
        // ES2015: a no-op on a non-object rather than a TypeError
        return obj;
    }

    /**
     * ECMA 15.2.3.11 Object.isSealed ( O )
     *
     * @param self self reference
     * @param obj check whether an object is sealed
     * @return true if sealed, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isSealed(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isSealed();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isSealed();
        }
        // ES2015: a non-object has no properties to add or change
        return true;
    }

    /**
     * ECMA 15.2.3.12 Object.isFrozen ( O )
     *
     * @param self self reference
     * @param obj check whether an object
     * @return true if object is frozen, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isFrozen(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isFrozen();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isFrozen();
        }
        // ES2015: a non-object has no properties to add or change
        return true;
    }

    /**
     * ECMA 15.2.3.13 Object.isExtensible ( O )
     *
     * @param self self reference
     * @param obj check whether an object is extensible
     * @return true if object is extensible, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isExtensible(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).isExtensible();
        } else if (obj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)obj).isExtensible();
        }
        // ES2015: a non-object is never extensible
        return false;
    }

    /**
     * ECMAScript 2017 19.1.2.21 Object.values ( O )
     *
     * @param self self reference
     * @param obj  the object
     * @return its own enumerable string-keyed property values, in key order
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject values(final Object self, final Object obj) {
        return new NativeArray(entries(obj, false));
    }

    /**
     * ECMAScript 2017 19.1.2.5 Object.entries ( O )
     *
     * @param self self reference
     * @param obj  the object
     * @return one two-element array per own enumerable string-keyed property
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject entries(final Object self, final Object obj) {
        return new NativeArray(entries(obj, true));
    }

    /**
     * The shared body of Object.values and Object.entries (ES2017 7.3.21
     * EnumerableOwnProperties).
     *
     * A property is read only after it has been found enumerable, and a property
     * a getter deletes while the walk is under way is simply not there any more.
     */
    private static Object[] entries(final Object obj, final boolean withKeys) {
        final ScriptObject sobj = Global.toObject(obj) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            throw notAnObject(obj);
        }
        final List<Object> collected = new ArrayList<>();
        // the descriptor below is what says whether a property is enumerable,
        // so an object that has to be asked for one is asked once rather than
        // twice: what it reports here is every key it has
        for (final String key : sobj.getOwnKeys(sobj.answersForEveryKey())) {
            final Object descriptor = sobj.getOwnPropertyDescriptor(key);
            if (!(descriptor instanceof ScriptObject own) || !JSType.toBoolean(own.get("enumerable"))) {
                continue;
            }
            final Object value = sobj.get(key);
            collected.add(withKeys ? new NativeArray(new Object[] { key, value }) : value);
        }
        return collected.toArray();
    }

    /**
     * ECMAScript 2017 19.1.2.9 Object.getOwnPropertyDescriptors ( O )
     *
     * @param self self reference
     * @param obj  the object
     * @return an object holding one descriptor per own property, symbols included
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject getOwnPropertyDescriptors(final Object self, final Object obj) {
        final ScriptObject sobj = Global.toObject(obj) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            throw notAnObject(obj);
        }
        final ScriptObject descriptors = Global.newEmptyInstance();
        for (final Object key : sobj.getOwnKeysAndSymbols(true)) {
            addDescriptor(descriptors, sobj, key);
        }
        return descriptors;
    }

    private static void addDescriptor(final ScriptObject descriptors, final ScriptObject sobj, final Object key) {
        final Object descriptor = sobj.getOwnPropertyDescriptor(key);
        if (descriptor != ScriptRuntime.UNDEFINED) {
            descriptors.set(key, descriptor, 0);
        }
    }

    /**
     * ECMA 15.2.3.14 Object.keys ( O )
     *
     * @param self self reference
     * @param obj  object from which to extract keys
     * @return array of keys in object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static ScriptObject keys(final Object self, final Object obj) {
        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            return new NativeArray(sobj.getOwnKeys(false));
        } else if (obj instanceof ScriptObjectMirror) {
            final ScriptObjectMirror sobjMirror = (ScriptObjectMirror)obj;
            return new NativeArray(sobjMirror.getOwnKeys(false));
        }

        // ES2015 19.1.2.16 coerces rather than rejecting
        final Object coerced = JSType.toScriptObject(Global.instance(), obj);
        if (coerced instanceof ScriptObject) {
            return new NativeArray(((ScriptObject)coerced).getOwnKeys(false));
        }
        return new NativeArray();
    }

    /**
     * ECMA 15.2.2.1 , 15.2.1.1 new Object([value]) and Object([value])
     *
     * Constructor
     *
     * @param newObj is the new object instantiated with the new operator
     * @param self   self reference
     * @param value  value of object to be instantiated
     * @return the new NativeObject
     */
    @Constructor
    public static Object construct(final boolean newObj, final Object self, final Object value) {
        final JSType type = JSType.ofNoFunction(value);

        // Object(null), Object(undefined), Object() are same as "new Object()"

        if (newObj || type == JSType.NULL || type == JSType.UNDEFINED) {
            switch (type) {
            case BOOLEAN:
            case NUMBER:
            case STRING:
            case SYMBOL:
                return Global.toObject(value);
            case OBJECT:
                return value;
            case NULL:
            case UNDEFINED:
                // fall through..
            default:
                break;
            }

            return Global.newEmptyInstance();
        }

        return Global.toObject(value);
    }

    /**
     * ECMA 15.2.4.2 Object.prototype.toString ( )
     *
     * @param self self reference
     * @return ToString of object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * ECMA 15.2.4.3 Object.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return localized ToString
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleString(final Object self) {
        if (JSType.toScriptObject(self) instanceof ScriptObject) {
            // 19.1.3.5 invokes toString on the this value itself: the lookup
            // goes through a wrapper for a primitive, the call does not, which
            // is the this a strict callee sees
            final InvokeByName toStringInvoker = getTO_STRING();
            try {
                final Object toString = toStringInvoker.getGetter().invokeExact(self);

                if (Bootstrap.isCallable(toString)) {
                    return toStringInvoker.getInvoker().invokeExact(toString, self);
                }
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }

            throw typeError("not.a.function", "toString");
        }

        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * ECMA 15.2.4.4 Object.prototype.valueOf ( )
     *
     * @param self self reference
     * @return value of object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return Global.toObject(self);
    }

    /**
     * ECMA B.2.2.2 Object.prototype.__defineGetter__ ( P, getter )
     *
     * @param self   self reference
     * @param prop   the property key
     * @param getter the function to read it with
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object __defineGetter__(final Object self, final Object prop, final Object getter) {
        return defineAccessor(self, prop, getter, true);
    }

    /**
     * ECMA B.2.2.3 Object.prototype.__defineSetter__ ( P, setter )
     *
     * @param self   self reference
     * @param prop   the property key
     * @param setter the function to write it with
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object __defineSetter__(final Object self, final Object prop, final Object setter) {
        return defineAccessor(self, prop, setter, false);
    }

    /**
     * B.2.2.2 and B.2.2.3, which differ only in which half of the accessor they
     * are given.
     *
     * The function is checked before the key is converted: 22.2.2.2 step 2
     * comes before step 3, and a key whose toString has a side effect makes the
     * order observable.
     */
    private static Object defineAccessor(final Object self, final Object prop, final Object accessor,
            final boolean isGetter) {
        final ScriptObject sobj = Global.checkObject(Global.toObject(self));

        if (!Bootstrap.isCallable(accessor)) {
            throw typeError(isGetter ? "not.a.function" : "not.a.function", ScriptRuntime.safeToString(accessor));
        }

        final Object key = JSType.toPropertyKey(prop);
        final PropertyDescriptor desc = Global.instance().newAccessorDescriptor(
                isGetter ? accessor : null, isGetter ? null : accessor, true, true);

        sobj.defineOwnProperty(key, desc, true);
        return UNDEFINED;
    }

    /**
     * ECMA B.2.2.4 Object.prototype.__lookupGetter__ ( P )
     *
     * @param self self reference
     * @param prop the property key
     * @return the getter of the first own property of that name on the
     *         prototype chain, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object __lookupGetter__(final Object self, final Object prop) {
        return lookupAccessor(self, prop, true);
    }

    /**
     * ECMA B.2.2.5 Object.prototype.__lookupSetter__ ( P )
     *
     * @param self self reference
     * @param prop the property key
     * @return the setter of the first own property of that name on the
     *         prototype chain, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object __lookupSetter__(final Object self, final Object prop) {
        return lookupAccessor(self, prop, false);
    }

    /**
     * B.2.2.4 and B.2.2.5. The walk stops at the first object that owns the
     * name, whether or not it is an accessor: a data property shadows an
     * accessor above it, and answers undefined for both halves.
     */
    private static Object lookupAccessor(final Object self, final Object prop, final boolean isGetter) {
        // the receiver is coerced before the key is, which a key whose toString
        // has a side effect can tell apart
        ScriptObject sobj = Global.checkObject(Global.toObject(self));
        final Object key = JSType.toPropertyKey(prop);

        while (sobj != null) {
            final Object desc = sobj.getOwnPropertyDescriptor(key);
            if (desc instanceof AccessorPropertyDescriptor accessor) {
                final Object half = isGetter ? accessor.get : accessor.set;
                return half == null ? UNDEFINED : half;
            }
            if (desc != UNDEFINED) {
                return UNDEFINED;
            }
            sobj = sobj.getPrototypeOf();
        }

        return UNDEFINED;
    }

    /**
     * ECMA 15.2.4.5 Object.prototype.hasOwnProperty (V)
     *
     * @param self self reference
     * @param v property to check for
     * @return true if property exists in object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean hasOwnProperty(final Object self, final Object v) {
        // Convert ScriptObjects to primitive with String.class hint
        // but no need to convert other primitives to string.
        final Object key = JSType.toPrimitive(v, String.class);
        final Object obj = Global.toObject(self);

        return obj instanceof ScriptObject && ((ScriptObject)obj).hasOwnProperty(key);
    }

    /**
     * ECMA 15.2.4.6 Object.prototype.isPrototypeOf (V)
     *
     * @param self self reference
     * @param v v prototype object to check against
     * @return true if object is prototype of v
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean isPrototypeOf(final Object self, final Object v) {
        if (!(v instanceof ScriptObject)) {
            return false;
        }

        final Object obj   = Global.toObject(self);
        ScriptObject proto = (ScriptObject)v;

        do {
            // 19.1.3.3 walks with [[GetPrototypeOf]], which a proxy answers
            // with its own trap rather than with the prototype it was made on
            proto = proto.getPrototypeOf();
            if (proto == obj) {
                return true;
            }
        } while (proto != null);

        return false;
    }

    /**
     * ECMA 15.2.4.7 Object.prototype.propertyIsEnumerable (V)
     *
     * @param self self reference
     * @param v property to check if enumerable
     * @return true if property is enumerable
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean propertyIsEnumerable(final Object self, final Object v) {
        // ES2015 19.1.3.4 takes a property key, and a symbol is one: converting
        // it to a string is the one thing a symbol refuses to do
        final Object key = JSType.toPropertyKey(v);
        final Object obj = Global.toObject(self);

        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject) obj;
            // 19.1.3.4 step 3 describes the property rather than reading its
            // flags out of the map, which is observable on an object that
            // answers for itself - a module namespace reads the export
            final Object descriptor = sobj.getOwnPropertyDescriptor(key);
            if (descriptor instanceof ScriptObject described) {
                return JSType.toBoolean(described.get("enumerable"));
            }
            return sobj.getArray().has(ArrayIndex.getArrayIndex(v));
        }

        return false;
    }

    /**
     * Nashorn extension: Object.bindProperties
     *
     * Binds the source object's properties to the target object. Binding
     * properties allows two-way read/write for the properties of the source object.
     *
     * Example:
     * <pre>
     * var obj = { x: 34, y: 100 };
     * var foo = {}
     *
     * // bind properties of "obj" to "foo" object
     * Object.bindProperties(foo, obj);
     *
     * // now, we can access/write on 'foo' properties
     * print(foo.x); // prints obj.x which is 34
     *
     * // update obj.x via foo.x
     * foo.x = "hello";
     * print(obj.x); // prints "hello" now
     *
     * obj.x = 42;   // foo.x also becomes 42
     * print(foo.x); // prints 42
     * </pre>
     * <p>
     * The source object bound can be a ScriptObject or a ScriptOjectMirror.
     * null or undefined source object results in TypeError being thrown.
     * </p>
     * Example:
     * <pre>
     * var obj = loadWithNewGlobal({
     *    name: "test",
     *    script: "obj = { x: 33, y: 'hello' }"
     * });
     *
     * // bind 'obj's properties to global scope 'this'
     * Object.bindProperties(this, obj);
     * print(x);         // prints 33
     * print(y);         // prints "hello"
     * x = Math.PI;      // changes obj.x to Math.PI
     * print(obj.x);     // prints Math.PI
     * </pre>
     *
     * Limitations of property binding:
     * <ul>
     * <li> Only enumerable, immediate (not proto inherited) properties of the source object are bound.
     * <li> If the target object already contains a property called "foo", the source's "foo" is skipped (not bound).
     * <li> Properties added to the source object after binding to the target are not bound.
     * <li> Property configuration changes on the source object (or on the target) is not propagated.
     * <li> Delete of property on the target (or the source) is not propagated -
     * only the property value is set to 'undefined' if the property happens to be a data property.
     * </ul>
     * <p>
     * It is recommended that the bound properties be treated as non-configurable
     * properties to avoid surprises.
     * </p>
     *
     * @param self self reference
     * @param target the target object to which the source object's properties are bound
     * @param source the source object whose properties are bound to the target
     * @return the target object after property binding
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object bindProperties(final Object self, final Object target, final Object source) {
        // target object has to be a ScriptObject
        final ScriptObject targetObj = Global.checkObject(target);
        // check null or undefined source object
        Global.checkObjectCoercible(source);

        if (source instanceof ScriptObject) {
            final ScriptObject sourceObj  = (ScriptObject)source;

            final PropertyMap  sourceMap  = sourceObj.getMap();
            final Property[]   properties = sourceMap.getProperties();
            //replace the map and blow up everything to objects to work with dual fields :-(

            // filter non-enumerable properties
            final ArrayList<Property> propList = new ArrayList<>();
            for (final Property prop : properties) {
                if (prop.isEnumerable()) {
                    final Object value = sourceObj.get(prop.getKey());
                    prop.setType(Object.class);
                    prop.setValue(sourceObj, sourceObj, value, false);
                    propList.add(prop);
                }
            }

            if (!propList.isEmpty()) {
                targetObj.addBoundProperties(sourceObj, propList.toArray(new Property[0]));
            }
        } else if (source instanceof ScriptObjectMirror) {
            // get enumerable, immediate properties of mirror
            final ScriptObjectMirror mirror = (ScriptObjectMirror)source;
            final String[] keys = mirror.getOwnKeys(false);
            if (keys.length == 0) {
                // nothing to bind
                return target;
            }

            // make accessor properties using dynamic invoker getters and setters
            final AccessorProperty[] props = new AccessorProperty[keys.length];
            for (int idx = 0; idx < keys.length; idx++) {
                props[idx] = createAccessorProperty(keys[idx]);
            }

            targetObj.addBoundProperties(source, props);
        } else if (source instanceof StaticClass) {
            final Class<?> clazz = ((StaticClass)source).getRepresentedClass();
            Bootstrap.checkReflectionAccess(clazz, true);
            bindBeanProperties(targetObj, source, BeansLinker.getReadableStaticPropertyNames(clazz),
                    BeansLinker.getWritableStaticPropertyNames(clazz), BeansLinker.getStaticMethodNames(clazz));
        } else {
            final Class<?> clazz = source.getClass();
            Bootstrap.checkReflectionAccess(clazz, false);
            bindBeanProperties(targetObj, source, BeansLinker.getReadableInstancePropertyNames(clazz),
                    BeansLinker.getWritableInstancePropertyNames(clazz), BeansLinker.getInstanceMethodNames(clazz));
        }

        return target;
    }

    private static AccessorProperty createAccessorProperty(final String name) {
        final MethodHandle getter = Bootstrap.createDynamicInvoker(name, NashornCallSiteDescriptor.GET_METHOD_PROPERTY, MIRROR_GETTER_TYPE);
        final MethodHandle setter = Bootstrap.createDynamicInvoker(name, NashornCallSiteDescriptor.SET_PROPERTY, MIRROR_SETTER_TYPE);
        return AccessorProperty.create(name, 0, getter, setter);
    }

    /**
     * Binds the source mirror object's properties to the target object. Binding
     * properties allows two-way read/write for the properties of the source object.
     * All inherited, enumerable properties are also bound. This method is used to
     * to make 'with' statement work with ScriptObjectMirror as scope object.
     *
     * @param target the target object to which the source object's properties are bound
     * @param source the source object whose properties are bound to the target
     * @return the target object after property binding
     */
    public static Object bindAllProperties(final ScriptObject target, final ScriptObjectMirror source) {
        final Set<String> keys = source.keySet();
        // make accessor properties using dynamic invoker getters and setters
        final AccessorProperty[] props = new AccessorProperty[keys.size()];
        int idx = 0;
        for (final String name : keys) {
            props[idx] = createAccessorProperty(name);
            idx++;
        }

        target.addBoundProperties(source, props);
        return target;
    }

    private static void bindBeanProperties(final ScriptObject targetObj, final Object source,
            final Collection<String> readablePropertyNames, final Collection<String> writablePropertyNames,
            final Collection<String> methodNames) {
        final Set<String> propertyNames = new HashSet<>(readablePropertyNames);
        propertyNames.addAll(writablePropertyNames);

        final Class<?> clazz = source.getClass();

        final MethodType getterType = MethodType.methodType(Object.class, clazz);
        final MethodType setterType = MethodType.methodType(Object.class, clazz, Object.class);

        final GuardingDynamicLinker linker = Bootstrap.getBeanLinkerForClass(clazz);

        final List<AccessorProperty> properties = new ArrayList<>(propertyNames.size() + methodNames.size());
        for(final String methodName: methodNames) {
            final MethodHandle method;
            try {
                method = getBeanOperation(linker, GET_METHOD, methodName, getterType, source);
            } catch(final IllegalAccessError e) {
                // Presumably, this was a caller sensitive method. Ignore it and carry on.
                continue;
            }
            properties.add(AccessorProperty.create(methodName, Property.NOT_WRITABLE, getBoundBeanMethodGetter(source,
                    method), Lookup.EMPTY_SETTER));
        }
        for(final String propertyName: propertyNames) {
            MethodHandle getter;
            if(readablePropertyNames.contains(propertyName)) {
                try {
                    getter = getBeanOperation(linker, GET_PROPERTY, propertyName, getterType, source);
                } catch(final IllegalAccessError e) {
                    // Presumably, this was a caller sensitive method. Ignore it and carry on.
                    getter = Lookup.EMPTY_GETTER;
                }
            } else {
                getter = Lookup.EMPTY_GETTER;
            }
            final boolean isWritable = writablePropertyNames.contains(propertyName);
            MethodHandle setter;
            if(isWritable) {
                try {
                    setter = getBeanOperation(linker, SET_PROPERTY, propertyName, setterType, source);
                } catch(final IllegalAccessError e) {
                    // Presumably, this was a caller sensitive method. Ignore it and carry on.
                    setter = Lookup.EMPTY_SETTER;
                }
            } else {
                setter = Lookup.EMPTY_SETTER;
            }
            if(getter != Lookup.EMPTY_GETTER || setter != Lookup.EMPTY_SETTER) {
                properties.add(AccessorProperty.create(propertyName, isWritable ? 0 : Property.NOT_WRITABLE, getter, setter));
            }
        }

        targetObj.addBoundProperties(source, properties.toArray(new AccessorProperty[0]));
    }

    private static MethodHandle getBoundBeanMethodGetter(final Object source, final MethodHandle methodGetter) {
        try {
            // NOTE: we're relying on the fact that StandardOperation.GET_METHOD return value is constant for any given method
            // name and object linked with BeansLinker. (Actually, an even stronger assumption is true: return value is
            // constant for any given method name and object's class.)
            return MethodHandles.dropArguments(MethodHandles.constant(Object.class,
                    Bootstrap.bindCallable(methodGetter.invoke(source), source, null)), 0, Object.class);
        } catch(RuntimeException|Error e) {
            throw e;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle getBeanOperation(final GuardingDynamicLinker linker, final Operation operation,
            final String name, final MethodType methodType, final Object source) {
        final GuardedInvocation inv;
        try {
            inv = NashornBeansLinker.getGuardedInvocation(linker, createLinkRequest(operation.named(name), methodType, source), Bootstrap.getLinkerServices());
            assert passesGuard(source, inv.getGuard());
        } catch(RuntimeException|Error e) {
            throw e;
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
        assert inv.getSwitchPoints() == null; // Linkers in Dynalink's beans package don't use switchpoints.
        // We discard the guard, as all method handles will be bound to a specific object.
        return inv.getInvocation();
    }

    private static boolean passesGuard(final Object obj, final MethodHandle guard) throws Throwable {
        return guard == null || (boolean)guard.invoke(obj);
    }

    private static LinkRequest createLinkRequest(final Operation operation, final MethodType methodType, final Object source) {
        return new SimpleLinkRequest(new CallSiteDescriptor(MethodHandles.publicLookup(), operation,
                methodType), false, source);
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NativeObject.class, name, MH.type(rtype, types));
    }

    /**
     * ECMAScript 2015 19.1.2.1 Object.assign(target, ...sources)
     *
     * @param self self reference
     * @param args the target followed by the sources
     * @return the target
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object assign(final Object self, final Object... args) {
        // nasgen requires a varargs builtin to be exactly (self, Object...), so
        // the target is the first of args rather than a parameter of its own.
        final Object target = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        if (target == null || target == ScriptRuntime.UNDEFINED) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(target));
        }
        final Object to = JSType.toScriptObject(Global.instance(), target);
        if (!(to instanceof ScriptObject targetObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(target));
        }

        for (int i = 1; i < args.length; i++) {
            final Object source = args[i];
            if (source == null || source == ScriptRuntime.UNDEFINED) {
                // null and undefined sources are skipped rather than rejected
                continue;
            }
            final Object from = JSType.toScriptObject(Global.instance(), source);
            if (!(from instanceof ScriptObject sourceObject)) {
                continue;
            }
            // Own enumerable keys, strings and symbols alike - getOwnKeys covers
            // only the strings, and skipping the symbols means a frozen target
            // with a symbol-keyed property is written to without complaint.
            if (sourceObject.answersForEveryKey()) {
                // 19.1.2.1 asks the source for its keys once and then asks it
                // about each of them in that order, which is what a proxy's
                // traps are entitled to see. An ordinary object's map answers
                // the same thing without the descriptor for every property.
                for (final Object key : sourceObject.getOwnKeysAndSymbols(true)) {
                    if (sourceObject.getOwnPropertyDescriptor(key) instanceof PropertyDescriptor described
                            && described.isEnumerable()) {
                        targetObject.set(key, sourceObject.get(key), NashornCallSiteDescriptor.CALLSITE_STRICT);
                    }
                }
                continue;
            }
            for (final Object key : sourceObject.getOwnKeys(false)) {
                targetObject.set(key, sourceObject.get(key), NashornCallSiteDescriptor.CALLSITE_STRICT);
            }
            for (final Symbol key : sourceObject.getOwnSymbols(false)) {
                targetObject.set(key, sourceObject.get(key), NashornCallSiteDescriptor.CALLSITE_STRICT);
            }
        }
        return targetObject;
    }

    /**
     * ECMAScript 2015 19.1.2.10 Object.is(x, y), SameValue.
     *
     * Differs from === in exactly two places: NaN is the same as itself, and +0
     * is not the same as -0.
     *
     * @param self self reference
     * @param x    first value
     * @param y    second value
     * @return true if the two are the same value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static boolean is(final Object self, final Object x, final Object y) {
        if (x instanceof Number a && y instanceof Number b) {
            final double dx = a.doubleValue();
            final double dy = b.doubleValue();
            if (Double.isNaN(dx) && Double.isNaN(dy)) {
                return true;
            }
            // distinguishes the two zeros, which == and === do not
            return Double.compare(dx, dy) == 0;
        }
        return ScriptRuntime.EQ_STRICT(x, y);
    }
}
