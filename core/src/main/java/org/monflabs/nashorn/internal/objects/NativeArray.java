/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.PropertyDescriptor.VALUE;
import static org.monflabs.nashorn.internal.runtime.PropertyDescriptor.WRITABLE;
import static org.monflabs.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;
import static org.monflabs.nashorn.internal.runtime.arrays.ArrayLikeIterator.arrayLikeIterator;
import static org.monflabs.nashorn.internal.runtime.arrays.ArrayLikeIterator.reverseArrayLikeIterator;
import static org.monflabs.nashorn.internal.lookup.Lookup.MH;
import static org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Setter;
import org.monflabs.nashorn.internal.objects.annotations.SpecializedFunction;
import org.monflabs.nashorn.internal.objects.annotations.SpecializedFunction.LinkLogic;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.Debug;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.OptimisticBuiltins;
import org.monflabs.nashorn.internal.runtime.PropertyDescriptor;
import org.monflabs.nashorn.internal.runtime.Property;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.WellKnownSymbols;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayData;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayIndex;
import org.monflabs.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import org.monflabs.nashorn.internal.runtime.arrays.ContinuousArrayData;
import org.monflabs.nashorn.internal.runtime.arrays.IteratorAction;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import org.monflabs.nashorn.internal.runtime.linker.InvokeByName;

/**
 * Runtime representation of a JavaScript array. NativeArray only holds numeric
 * keyed values. All other values are stored in spill.
 */
@ScriptClass("Array")
public final class NativeArray extends ScriptObject implements OptimisticBuiltins {
    private static final Object JOIN                     = new Object();
    private static final Object EVERY_CALLBACK_INVOKER   = new Object();
    private static final Object SOME_CALLBACK_INVOKER    = new Object();
    private static final Object FOREACH_CALLBACK_INVOKER = new Object();
    private static final Object MAP_CALLBACK_INVOKER     = new Object();
    private static final Object FILTER_CALLBACK_INVOKER  = new Object();
    private static final Object REDUCE_CALLBACK_INVOKER  = new Object();
    private static final Object CALL_CMP                 = new Object();
    private static final Object TO_LOCALE_STRING         = new Object();

    /*
     * Constructors.
     */
    NativeArray() {
        this(ArrayData.initialArray());
    }

    NativeArray(final long length) {
        this(ArrayData.allocate(length));
    }

    NativeArray(final int[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final double[] array) {
        this(ArrayData.allocate(array));
    }

    NativeArray(final long[] array) {
        this(ArrayData.allocate(array.length));

        ArrayData arrayData = this.getArray();
        Class<?> widest = int.class;

        for (int index = 0; index < array.length; index++) {
            final long value = array[index];

            if (widest == int.class && JSType.isRepresentableAsInt(value)) {
                arrayData = arrayData.set(index, (int) value, false);
            } else if (widest != Object.class && JSType.isRepresentableAsDouble(value)) {
                arrayData = arrayData.set(index, (double) value, false);
                widest = double.class;
            } else {
                arrayData = arrayData.set(index, (Object) value, false);
                widest = Object.class;
            }
        }

        this.setArray(arrayData);
    }

    NativeArray(final Object[] array) {
        this(ArrayData.allocate(array.length));

        ArrayData arrayData = this.getArray();

        for (int index = 0; index < array.length; index++) {
            final Object value = array[index];

            if (value == ScriptRuntime.EMPTY) {
                arrayData = arrayData.delete(index);
            } else {
                arrayData = arrayData.set(index, value, false);
            }
        }

        this.setArray(arrayData);
    }

    NativeArray(final ArrayData arrayData) {
        this(arrayData, Global.instance());
    }

    NativeArray(final ArrayData arrayData, final Global global) {
        super(global.getArrayPrototype(), $nasgenmap$);
        setArray(arrayData);
        setIsArray();
    }

    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastGetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findGetIndexMethod(desc, request);
    }

    @Override
    protected GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastSetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }

        return super.findSetIndexMethod(desc, request);
    }

    private static InvokeByName getJOIN() {
        return Global.instance().getInvokeByName(JOIN, () -> new InvokeByName("join", ScriptObject.class));
    }

    private static MethodHandle createIteratorCallbackInvoker(final Object key, final Class<?> rtype) {
        return Global.instance().getDynamicInvoker(key, () -> Bootstrap.createDynamicCallInvoker(rtype, Object.class, Object.class, Object.class,
            double.class, Object.class));
    }

    private static MethodHandle getEVERY_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(EVERY_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getSOME_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(SOME_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getFOREACH_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(FOREACH_CALLBACK_INVOKER, void.class);
    }

    private static MethodHandle getMAP_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(MAP_CALLBACK_INVOKER, Object.class);
    }

    private static MethodHandle getFILTER_CALLBACK_INVOKER() {
        return createIteratorCallbackInvoker(FILTER_CALLBACK_INVOKER, boolean.class);
    }

    private static MethodHandle getREDUCE_CALLBACK_INVOKER() {
        return Global.instance().getDynamicInvoker(REDUCE_CALLBACK_INVOKER, () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class,
             Object.class, Object.class, Object.class, double.class, Object.class));
    }

    private static MethodHandle getCALL_CMP() {
        return Global.instance().getDynamicInvoker(CALL_CMP, () -> Bootstrap.createDynamicCallInvoker(double.class,
            Object.class, Object.class, Object.class, Object.class));
    }

    private static InvokeByName getTO_LOCALE_STRING() {
        return Global.instance().getInvokeByName(TO_LOCALE_STRING, () -> new InvokeByName("toLocaleString", Object.class, String.class));
    }

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * ES2015 22.1.2.5 get Array [ @@species ].
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

    @Override
    public String getClassName() {
        return "Array";
    }

    @Override
    public Object getLength() {
        final long length = getArray().length();
        assert length >= 0L;
        if (length <= Integer.MAX_VALUE) {
            return (int)length;
        }
        return length;
    }

    private boolean defineLength(final long oldLen, final PropertyDescriptor oldLenDesc, final PropertyDescriptor desc, final boolean reject) {
        // Step 3a
        if (!desc.has(VALUE)) {
            return super.defineOwnProperty("length", desc, reject);
        }

        // Step 3b

        // Step 3c and 3d - get new length and convert to long
        final long newLen = NativeArray.validLength(desc.getValue());

        // Step 3e - note that we need to convert to int or double as long is not considered a JS number type anymore
        desc.setValue(JSType.toNarrowestNumber(newLen));

        // Step 3f
        // increasing array length - just need to set new length value (and attributes if any) and return
        if (newLen >= oldLen) {
            return super.defineOwnProperty("length", desc, reject);
        }

        // Step 3g
        if (!oldLenDesc.isWritable()) {
            if (reject) {
                throw typeError("property.not.writable", "length", ScriptRuntime.safeToString(this));
            }
            return false;
        }

        // Step 3h and 3i
        final boolean newWritable = !desc.has(WRITABLE) || desc.isWritable();
        if (!newWritable) {
            desc.setWritable(true);
        }

        // Step 3j and 3k
        final boolean succeeded = super.defineOwnProperty("length", desc, reject);
        if (!succeeded) {
            return false;
        }

        // Step 3l
        // make sure that length is set till the point we can delete the old elements
        long o = oldLen;
        while (newLen < o) {
            o--;
            final boolean deleteSucceeded = delete(o, false);
            if (!deleteSucceeded) {
                desc.setValue(o + 1);
                if (!newWritable) {
                    desc.setWritable(false);
                }
                super.defineOwnProperty("length", desc, false);
                if (reject) {
                    throw typeError("property.not.writable", "length", ScriptRuntime.safeToString(this));
                }
                return false;
            }
        }

        // Step 3m
        if (!newWritable) {
            // make 'length' property not writable
            final ScriptObject newDesc = Global.newEmptyInstance();
            newDesc.set(WRITABLE, false, 0);
            return super.defineOwnProperty("length", newDesc, false);
        }

        return true;
    }

    /**
     * ECMA 15.4.5.1 [[DefineOwnProperty]] ( P, Desc, Throw )
     */
    @Override
    public boolean defineOwnProperty(final Object key, final Object propertyDesc, final boolean reject) {
        final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);

        // never be undefined as "length" is always defined and can't be deleted for arrays
        // Step 1
        final PropertyDescriptor oldLenDesc = (PropertyDescriptor) super.getOwnPropertyDescriptor("length");

        // Step 2
        // get old length and convert to long. Always a Long/Uint32 but we take the safe road.
        final long oldLen = JSType.toUint32(oldLenDesc.getValue());

        // Step 3
        if ("length".equals(key)) {
            // check for length being made non-writable
            final boolean result = defineLength(oldLen, oldLenDesc, desc, reject);
            if (desc.has(WRITABLE) && !desc.isWritable()) {
                setIsLengthNotWritable();
            }
            return result;
        }

        // Step 4a
        final int index = ArrayIndex.getArrayIndex(key);
        if (ArrayIndex.isValidArrayIndex(index)) {
            final long longIndex = ArrayIndex.toLongIndex(index);
            // Step 4b
            // setting an element beyond current length, but 'length' is not writable
            if (longIndex >= oldLen && !oldLenDesc.isWritable()) {
                if (reject) {
                    throw typeError("property.not.writable", Long.toString(longIndex), ScriptRuntime.safeToString(this));
                }
                return false;
            }

            // Step 4c
            // set the new array element
            final boolean succeeded = super.defineOwnProperty(key, desc, false);

            // Step 4d
            if (!succeeded) {
                if (reject) {
                    throw typeError("cant.redefine.property", key.toString(), ScriptRuntime.safeToString(this));
                }
                return false;
            }

            // Step 4e -- adjust new length based on new element index that is set
            if (longIndex >= oldLen) {
                oldLenDesc.setValue(longIndex + 1);
                super.defineOwnProperty("length", oldLenDesc, false);
            }

            // Step 4f
            return true;
        }

        // not an index property
        return super.defineOwnProperty(key, desc, reject);
    }

    /**
     * Spec. mentions use of [[DefineOwnProperty]] for indexed properties in
     * certain places (eg. Array.prototype.map, filter). We can not use ScriptObject.set
     * method in such cases. This is because set method uses inherited setters (if any)
     * from any object in proto chain such as Array.prototype, Object.prototype.
     * This method directly sets a particular element value in the current object.
     *
     * @param index key for property
     * @param value value to define
     */
    @Override
    public final void defineOwnProperty(final int index, final Object value) {
        assert isValidArrayIndex(index) : "invalid array index";
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex >= getArray().length()) {
            // make array big enough to hold..
            setArray(getArray().ensure(longIndex));
        }
        setArray(getArray().set(index, value, false));
    }

    /**
     * Return the array contents upcasted as an ObjectArray, regardless of
     * representation
     *
     * @return an object array
     */
    public Object[] asObjectArray() {
        return getArray().asObjectArray();
    }

    @Override
    public void setIsLengthNotWritable() {
        super.setIsLengthNotWritable();
        setArray(ArrayData.setIsLengthNotWritable(getArray()));
        // the write path asks the property whether it may be written, and the
        // length is an accessor whose setter would otherwise be called and
        // quietly do nothing - where a strict assignment is an error
        final Property length = getMap().findProperty("length");
        if (length != null && length.isWritable()) {
            modifyOwnProperty(length, length.getFlags() | Property.NOT_WRITABLE);
        }
    }

    /**
     * ECMA 15.4.3.2 Array.isArray ( arg )
     *
     * @param self self reference
     * @param arg  argument - object to check
     * @return true if argument is an array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isArray(final Object self, final Object arg) {
        // ES2015 7.2.2 IsArray looks through however many proxies stand in the way
        final Object value = arg instanceof NativeProxy proxy ? proxy.unwrap() : arg;
        return isArray(value) || (value instanceof JSObject && ((JSObject)value).isArray());
    }

    /**
     * Length getter
     * @param self self reference
     * @return the length of the object
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        if (isArray(self)) {
            final long length = ((ScriptObject) self).getArray().length();
            assert length >= 0L;
            // Cast to the narrowest supported numeric type to help optimistic type calculator
            if (length <= Integer.MAX_VALUE) {
                return (int) length;
            }
            return (double) length;
        }

        return 0;
    }

    /**
     * Length setter
     * @param self   self reference
     * @param length new length property
     */
    @Setter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static void length(final Object self, final Object length) {
        if (isArray(self)) {
            ((ScriptObject)self).setLength(validLength(length));
        }
    }

    /**
     * The same, for a write that strict code made.
     *
     * 9.4.2.4 converts the value and only then asks whether the length may be
     * written, in that order and for a reason: converting it runs script, which
     * may be what makes the length non-writable. A setter cannot see the mode
     * it was called in, so a strict write is linked to this one.
     *
     * @param self   the array
     * @param length the new length
     */
    public static void setLengthStrict(final Object self, final Object length) {
        if (!isArray(self)) {
            return;
        }
        final ScriptObject sobj = (ScriptObject)self;
        final long value = validLength(length);
        if (sobj.isLengthNotWritable()) {
            throw typeError("property.not.writable", "length", ScriptRuntime.safeToString(self));
        }
        sobj.setLength(value);
    }

    private static final MethodHandle SET_LENGTH_STRICT = MH.findStatic(MethodHandles.lookup(),
            NativeArray.class, "setLengthStrict", MH.type(void.class, Object.class, Object.class));

    /**
     * Links a strict write of "length" to the setter that can report what the
     * conversion did; everything else is linked as it always was.
     */
    @Override
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = super.findSetMethod(desc, request);
        if (request.isCallSiteUnstable()
                || !NashornCallSiteDescriptor.isStrict(desc)
                || !"length".equals(NashornCallSiteDescriptor.getOperand(desc))) {
            return inv;
        }
        final MethodHandle target = inv.getInvocation();
        if (target == null || target.type().returnType() != void.class || target.type().parameterCount() != 2) {
            return inv;
        }
        return inv.replaceMethods(MH.asType(SET_LENGTH_STRICT, target.type()), inv.getGuard());
    }

    /**
     * Prototype length getter
     * @param self self reference
     * @return the length of the object
     */
    @Getter(name = "length", where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static Object getProtoLength(final Object self) {
        return length(self);  // Same as instance getter but we can't make nasgen use the same method for prototype
    }

    /**
     * Prototype length setter
     * @param self   self reference
     * @param length new length property
     */
    @Setter(name = "length", where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public static void setProtoLength(final Object self, final Object length) {
        length(self, length);  // Same as instance setter but we can't make nasgen use the same method for prototype
    }

    static long validLength(final Object length) {
        // ES5 15.4.5.1, steps 3.c and 3.d require two ToNumber conversions here
        final double doubleLength = JSType.toNumber(length);
        if (doubleLength != JSType.toUint32(length)) {
            throw rangeError("inappropriate.array.length", ScriptRuntime.safeToString(length));
        }
        return (long) doubleLength;
    }

    /**
     * ECMA 15.4.4.2 Array.prototype.toString ( )
     *
     * @param self self reference
     * @return string representation of array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        final Object obj = Global.toObject(self);
        if (obj instanceof ScriptObject) {
            final InvokeByName joinInvoker = getJOIN();
            final ScriptObject sobj = (ScriptObject)obj;
            try {
                final Object join = joinInvoker.getGetter().invokeExact(sobj);
                if (Bootstrap.isCallable(join)) {
                    return joinInvoker.getInvoker().invokeExact(join, sobj);
                }
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        // FIXME: should lookup Object.prototype.toString and call that?
        return ScriptRuntime.builtinObjectToString(self);
    }

    /**
     * Assert that an array is numeric, if not throw type error
     * @param self self array to check
     * @return true if numeric
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object assertNumeric(final Object self) {
        if(!(self instanceof NativeArray && ((NativeArray)self).getArray().getOptimisticType().isNumeric())) {
            throw typeError("not.a.numeric.array", ScriptRuntime.safeToString(self));
        }
        return Boolean.TRUE;
    }

    /**
     * ECMA 15.4.4.3 Array.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return locale specific string representation for array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleString(final Object self) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<Object> iter = arrayLikeIterator(self, true);

        while (iter.hasNext()) {
            final Object obj = iter.next();

            if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                try {
                    if (JSType.toScriptObject(obj) instanceof ScriptObject) {
                        // 22.1.3.26 invokes the element itself: the lookup goes
                        // through a wrapper for a primitive, the call does not,
                        // which is the this a strict callee sees
                        final InvokeByName localeInvoker = getTO_LOCALE_STRING();
                        final Object       toLocaleString = localeInvoker.getGetter().invokeExact(obj);

                        if (Bootstrap.isCallable(toLocaleString)) {
                            sb.append((String)localeInvoker.getInvoker().invokeExact(toLocaleString, obj));
                        } else {
                            throw typeError("not.a.function", "toLocaleString");
                        }
                    }
                } catch (final Error|RuntimeException t) {
                    throw t;
                } catch (final Throwable t) {
                    throw new RuntimeException(t);
                }
            }

            if (iter.hasNext()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param args   arguments (length)
     * @return the new NativeArray
     */
    @Constructor(arity = 1)
    public static NativeArray construct(final boolean newObj, final Object self, final Object... args) {
        switch (args.length) {
        case 0:
            return new NativeArray(0);
        case 1:
            final Object len = args[0];
            if (len instanceof Number) {
                long length;
                if (len instanceof Integer || len instanceof Long) {
                    length = ((Number) len).longValue();
                    if (length >= 0 && length < JSType.MAX_UINT) {
                        return new NativeArray(length);
                    }
                }

                length = JSType.toUint32(len);

                /*
                 * If the argument len is a Number and ToUint32(len) is equal to
                 * len, then the length property of the newly constructed object
                 * is set to ToUint32(len). If the argument len is a Number and
                 * ToUint32(len) is not equal to len, a RangeError exception is
                 * thrown.
                 */
                final double numberLength = ((Number) len).doubleValue();
                if (length != numberLength) {
                    throw rangeError("inappropriate.array.length", JSType.toString(numberLength));
                }

                return new NativeArray(length);
            }
            /*
             * If the argument len is not a Number, then the length property of
             * the newly constructed object is set to 1 and the 0 property of
             * the newly constructed object is set to len
             */
            return new NativeArray(new Object[]{args[0]});
            //fallthru
        default:
            return new NativeArray(args);
        }
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for zero arguments - empty array
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self) {
        return new NativeArray(0);
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for zero arguments - empty array
     *
     * @param newObj  was the new operator used to instantiate this array
     * @param self    self reference
     * @param element first element
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static Object construct(final boolean newObj, final Object self, final boolean element) {
        return new NativeArray(new Object[] { element });
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one integer argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final int length) {
        if (length >= 0) {
            return new NativeArray(length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one long argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final long length) {
        if (length >= 0L && length <= JSType.MAX_UINT) {
            return new NativeArray(length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.2.2 new Array (len)
     *
     * Specialized constructor for one double argument (length)
     *
     * @param newObj was the new operator used to instantiate this array
     * @param self   self reference
     * @param length array length
     * @return the new NativeArray
     */
    @SpecializedFunction(isConstructor=true)
    public static NativeArray construct(final boolean newObj, final Object self, final double length) {
        final long uint32length = JSType.toUint32(length);

        if (uint32length == length) {
            return new NativeArray(uint32length);
        }

        return construct(newObj, self, new Object[]{length});
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class, convertsNumericArgs = false)
    public static Object concat(final Object self, final int arg) {
        if (!hasDefaultSpecies(self, Global.instance())) {
            // a species of its own means the derived array is not this one's to
            // make, and none of what follows applies
            return concat(self, new Object[] { arg });
        }
        final ContinuousArrayData newData = getContinuousArrayDataCCE(self, Integer.class).copy(); //get at least an integer data copy of this data
        newData.fastPush(arg); //add an integer to its end
        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class, convertsNumericArgs = false)
    public static Object concat(final Object self, final double arg) {
        if (!hasDefaultSpecies(self, Global.instance())) {
            // a species of its own means the derived array is not this one's to
            // make, and none of what follows applies
            return concat(self, new Object[] { arg });
        }
        final ContinuousArrayData newData = getContinuousArrayDataCCE(self, Double.class).copy(); //get at least a number array data copy of this data
        newData.fastPush(arg); //add a double at the end
        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param arg argument
     * @return resulting NativeArray
     */
    @SpecializedFunction(linkLogic=ConcatLinkLogic.class)
    public static Object concat(final Object self, final Object arg) {
        if (!hasDefaultSpecies(self, Global.instance())) {
            // a species of its own means the derived array is not this one's to
            // make, and none of what follows applies
            return concat(self, new Object[] { arg });
        }
        //arg is [NativeArray] of same type.
        final ContinuousArrayData selfData = getContinuousArrayDataCCE(self);
        final ContinuousArrayData newData;

        if (arg instanceof NativeArray) {
            final ContinuousArrayData argData = (ContinuousArrayData)((NativeArray)arg).getArray();
            if (argData.isEmpty()) {
                newData = selfData.copy();
            } else if (selfData.isEmpty()) {
                newData = argData.copy();
            } else {
                final Class<?> widestElementType = selfData.widest(argData).getBoxedElementType();
                newData = ((ContinuousArrayData)selfData.convert(widestElementType)).fastConcat((ContinuousArrayData)argData.convert(widestElementType));
            }
        } else {
            newData = getContinuousArrayDataCCE(self, Object.class).copy();
            newData.fastPush(arg);
        }

        return new NativeArray(newData);
    }

    /**
     * ECMA 15.4.4.4 Array.prototype.concat ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param args arguments
     * @return resulting NativeArray
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object concat(final Object self, final Object... args) {
        // ES2015 22.1.3.1 step 2: the result is made before anything is put in
        // it, and before anything is read - the constructor it asks for is read
        // ahead of the first Symbol.isConcatSpreadable - by the species, and an
        // element goes in with CreateDataProperty, which a species that refuses
        // to take one turns into a TypeError
        final Global global = Global.instance();
        final boolean ordinary = !(self instanceof ScriptObject sobj)
                || !isArrayThroughProxies(sobj) || hasDefaultSpecies(sobj, global);
        final ScriptObject result = ordinary ? null : speciesCreate(self, 0);

        final ArrayList<Object> list = new ArrayList<>();

        concatToList(list, Global.toObject(self));

        for (final Object obj : args) {
            concatToList(list, obj);
        }

        if (ordinary) {
            return new NativeArray(list.toArray());
        }

        long index = 0;
        for (final Object value : list) {
            if (value != ScriptRuntime.EMPTY) {
                createDataProperty(result, index, value);
            }
            index++;
        }
        result.set("length", index, CALLSITE_STRICT);
        return result;
    }

    /**
     * CreateDataPropertyOrThrow (ES2015 7.3.6) against an array being built.
     *
     * The operations deriving one array from another put their elements in this
     * way rather than by assignment, so a result that will not take a property -
     * one that is not extensible, or that already has a non-configurable one
     * there - is an error rather than something quietly dropped.
     */
    private static void createDataProperty(final ScriptObject target, final long index, final Object value) {
        final PropertyDescriptor desc = Global.instance().newDataDescriptor(value, true, true, true);
        target.defineOwnProperty(JSType.toString(index), desc, true);
    }

    /** Spreads an object into a concat result by asking it for each element. */
    private static void spreadThroughProperties(final ArrayList<Object> list, final ScriptObject sobj) {
        final long length = toLength(sobj.get("length"));
        // 22.1.3.1 step 5.c.iv: the result cannot hold more than an index can
        // name, and says so before spreading rather than on reaching the end
        if (list.size() + length > MAX_SAFE_INTEGER) {
            throw typeError("array.length.exceeded", JSType.toString((double)(list.size() + length)));
        }
        for (long i = 0; i < length; i++) {
            list.add(sobj.has(i) ? sobj.get(i) : ScriptRuntime.EMPTY);
        }
    }

    private static void concatToList(final ArrayList<Object> list, final Object obj) {
        // IsArray looks through a proxy, and a revoked one has nothing to look
        // through to - which is why this is asked of every argument
        final boolean isScriptArray  = obj instanceof ScriptObject candidate
                ? isArrayThroughProxies(candidate) : isArray(obj);
        final boolean isScriptObject = isScriptArray || obj instanceof ScriptObject;

        // ES2015 22.1.3.1.1: Symbol.isConcatSpreadable overrides the decision
        // either way. The flag keeps the lookup off concat until one is installed.
        if (WellKnownSymbols.isConcatSpreadableInstalled() && obj instanceof ScriptObject sobj) {
            final Object spreadable = sobj.get(NativeSymbol.isConcatSpreadable);
            if (spreadable != ScriptRuntime.UNDEFINED) {
                if (JSType.toBoolean(spreadable)) {
                    spreadThroughProperties(list, sobj);
                } else {
                    list.add(obj);
                }
                return;
            }
        }

        if (obj instanceof NativeProxy proxy) {
            // there is no array data behind a proxy to walk: every element has
            // to be asked for, which is what its handler is there to answer
            spreadThroughProperties(list, proxy);
            return;
        }

        if (isScriptArray || obj instanceof Iterable || obj instanceof JSObject || (obj != null && obj.getClass().isArray())) {
            final Iterator<Object> iter = arrayLikeIterator(obj, true);
            if (iter.hasNext()) {
                for (int i = 0; iter.hasNext(); ++i) {
                    final Object value = iter.next();
                    if (value == ScriptRuntime.UNDEFINED && isScriptObject && !((ScriptObject)obj).has(i)) {
                        // TODO: eventually rewrite arrayLikeIterator to use a three-state enum for handling
                        // UNDEFINED instead of an "includeUndefined" boolean with states SKIP, INCLUDE,
                        // RETURN_EMPTY. Until then, this is how we'll make sure that empty elements don't make it
                        // into the concatenated array.
                        list.add(ScriptRuntime.EMPTY);
                    } else {
                        list.add(value);
                    }
                }
            } else if (!isScriptArray) {
                list.add(obj); // add empty object, but not an empty array
            }
        } else {
            // single element, add it
            list.add(obj);
        }
    }

    /**
     * ECMA 15.4.4.5 Array.prototype.join (separator)
     *
     * @param self      self reference
     * @param separator element separator
     * @return string representation after join
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String join(final Object self, final Object separator) {
        final StringBuilder    sb   = new StringBuilder();
        final Iterator<Object> iter = arrayLikeIterator(self, true);
        final String           sep  = separator == ScriptRuntime.UNDEFINED ? "," : JSType.toString(separator);

        while (iter.hasNext()) {
            final Object obj = iter.next();

            if (obj != null && obj != ScriptRuntime.UNDEFINED) {
                sb.append(JSType.toString(obj));
            }

            if (iter.hasNext()) {
                sb.append(sep);
            }
        }

        return sb.toString();
    }

    /**
     * Specialization of pop for ContinuousArrayData
     *   The link guard checks that the array is continuous AND not empty.
     *   The runtime guard checks that the guard is continuous (CCE otherwise)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static int popInt(final Object self) {
        //must be non empty IntArrayData
        return getContinuousNonEmptyArrayDataCCE(self).fastPopInt();
    }

    /**
     * Specialization of pop for ContinuousArrayData
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static double popDouble(final Object self) {
        //must be non empty int long or double array data
        return getContinuousNonEmptyArrayDataCCE(self).fastPopDouble();
    }

    /**
     * Specialization of pop for ContinuousArrayData
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @return element popped
     * @throws ClassCastException if array is empty, facilitating Undefined return value
     */
    @SpecializedFunction(name="pop", linkLogic=PopLinkLogic.class)
    public static Object popObject(final Object self) {
        //can be any data, because the numeric ones will throw cce and force relink
        return getContinuousArrayDataCCE(self, null).fastPopObject();
    }

    /**
     * ECMA 15.4.4.6 Array.prototype.pop ()
     *
     * @param self self reference
     * @return array after pop
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object pop(final Object self) {
        final Object obj = Global.toObject(self);
        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }
        final ScriptObject sobj = (ScriptObject)obj;

        if (bulkable(sobj)) {
            return sobj.getArray().pop();
        }

        final long len = toLength(sobj.getLength());

        if (len == 0) {
            sobj.set("length", 0, CALLSITE_STRICT);
            return ScriptRuntime.UNDEFINED;
        }

        final long   index   = len - 1;
        final Object element = sobj.get(index);

        sobj.delete(index, true);
        sobj.set("length", index, CALLSITE_STRICT);

        return element;
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(linkLogic=PushLinkLogic.class, convertsNumericArgs = false)
    public static double push(final Object self, final int arg) {
        return getContinuousArrayDataCCE(self, Integer.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(linkLogic=PushLinkLogic.class, convertsNumericArgs = false)
    public static double push(final Object self, final double arg) {
        return getContinuousArrayDataCCE(self, Double.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * Primitive specialization, {@link LinkLogic}
     *
     * @param self self reference
     * @param arg a primitive to push
     * @return array length after push
     */
    @SpecializedFunction(name="push", linkLogic=PushLinkLogic.class)
    public static double pushObject(final Object self, final Object arg) {
        return getContinuousArrayDataCCE(self, Object.class).fastPush(arg);
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...)
     *
     * @param self self reference
     * @param args arguments to push
     * @return array length after pushes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object push(final Object self, final Object... args) {
        try {
            final Object obj = Global.toObject(self);
            if (!(obj instanceof ScriptObject)) {
                return args.length;
            }
            final ScriptObject sobj = (ScriptObject)obj;

            if (bulkable(sobj) && sobj.getArray().length() + args.length <= JSType.MAX_UINT) {
                final ArrayData newData = sobj.getArray().push(true, args);
                sobj.setArray(newData);
                return JSType.toNarrowestNumber(newData.length());
            }

            long len = toLength(sobj.getLength());
            // ES2015 22.1.3.17 step 5: there is no index past 2^53-1 to put
            // anything at, and the length is not raised to somewhere nothing
            // can be read from
            if (len + args.length > MAX_SAFE_INTEGER) {
                throw typeError("array.length.exceeded", JSType.toString((double)len));
            }
            for (final Object element : args) {
                sobj.set(len++, element, CALLSITE_STRICT);
            }
            sobj.set("length", len, CALLSITE_STRICT);

            return JSType.toNarrowestNumber(len);
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError(Context.getGlobal(), e, "not.an.object", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.4.4.7 Array.prototype.push (args...) specialized for single object argument
     *
     * @param self self reference
     * @param arg argument to push
     * @return array length after pushes
     */
    @SpecializedFunction
    public static double push(final Object self, final Object arg) {
        final Object obj = Global.toObject(self);
        if (!(obj instanceof ScriptObject)) {
            return 1d;
        }
        final ScriptObject sobj = (ScriptObject)obj;
        final ArrayData arrayData = sobj.getArray();
        final long length = arrayData.length();
        if (bulkable(sobj) && length < JSType.MAX_UINT) {
            sobj.setArray(arrayData.push(true, arg));
            return length + 1;
        }

        long len = toLength(sobj.getLength());
        if (len >= MAX_SAFE_INTEGER) {
            // 22.1.3.17 step 5: no index past 2^53-1 to put it at
            throw typeError("array.length.exceeded", JSType.toString((double)len));
        }
        sobj.set(len++, arg, CALLSITE_STRICT);
        sobj.set("length", len, CALLSITE_STRICT);
        return len;
    }

    /**
     * ECMA 15.4.4.8 Array.prototype.reverse ()
     *
     * @param self self reference
     * @return reversed array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object reverse(final Object self) {
        final Object obj = Global.toObject(self);
        if (!(obj instanceof ScriptObject)) {
            return obj;
        }

        final ScriptObject sobj   = (ScriptObject)obj;
        final long         len    = toLength(sobj.getLength());
        final long         middle = len / 2;

        for (long lower = 0; lower != middle; lower++) {
            final long    upper       = len - lower - 1;
            // 22.1.3.21 asks whether each element is there and reads it before
            // going on to the other, so a getter that removes the other one is
            // seen to have done so
            final boolean lowerExists = sobj.has(lower);
            final Object  lowerValue  = lowerExists ? sobj.get(lower) : ScriptRuntime.UNDEFINED;
            final boolean upperExists = sobj.has(upper);
            final Object  upperValue  = upperExists ? sobj.get(upper) : ScriptRuntime.UNDEFINED;

            if (lowerExists && upperExists) {
                sobj.set(lower, upperValue, CALLSITE_STRICT);
                sobj.set(upper, lowerValue, CALLSITE_STRICT);
            } else if (!lowerExists && upperExists) {
                sobj.set(lower, upperValue, CALLSITE_STRICT);
                sobj.delete(upper, true);
            } else if (lowerExists) {
                sobj.delete(lower, true);
                sobj.set(upper, lowerValue, CALLSITE_STRICT);
            }
        }
        return sobj;
    }

    /**
     * ECMA 15.4.4.9 Array.prototype.shift ()
     *
     * @param self self reference
     * @return shifted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object shift(final Object self) {
        final Object obj = Global.toObject(self);

        Object first = ScriptRuntime.UNDEFINED;

        if (!(obj instanceof ScriptObject)) {
            return first;
        }

        final ScriptObject sobj   = (ScriptObject) obj;

        long len = toLength(sobj.getLength());

        if (len > 0) {
            first = sobj.get(0);

            if (bulkable(sobj)) {
                sobj.getArray().shiftLeft(1);
            } else {
                boolean hasPrevious = true;
                for (long k = 1; k < len; k++) {
                    final boolean hasCurrent = sobj.has(k);
                    if (hasCurrent) {
                        sobj.set(k - 1, sobj.get(k), CALLSITE_STRICT);
                    } else if (hasPrevious) {
                        sobj.delete(k - 1, true);
                    }
                    hasPrevious = hasCurrent;
                }
            }
            sobj.delete(--len, true);
        } else {
            len = 0;
        }

        sobj.set("length", len, CALLSITE_STRICT);

        return first;
    }

    /**
     * ECMA 15.4.4.10 Array.prototype.slice ( start [ , end ] )
     *
     * @param self  self reference
     * @param start start of slice (inclusive)
     * @param end   end of slice (optional, exclusive)
     * @return sliced array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object slice(final Object self, final Object start, final Object end) {
        final Object       obj                 = Global.toObject(self);
        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final ScriptObject sobj                = (ScriptObject)obj;
        final long         len                 = toLength(sobj.getLength());
        final long         relativeStart       = JSType.toLong(start);
        final long         relativeEnd         = end == ScriptRuntime.UNDEFINED ? len : JSType.toLong(end);

        long k = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len);
        final long finale = relativeEnd < 0 ? Math.max(len + relativeEnd, 0) : Math.min(relativeEnd, len);

        final long count = Math.max(finale - k, 0);
        final boolean ordinary = hasDefaultSpecies(sobj, Global.instance());

        if (ordinary) {
            if (count == 0) {
                return new NativeArray(0);
            }
            if (bulkable(sobj)) {
                return new NativeArray(sobj.getArray().slice(k, finale));
            }
            // Construct array with proper length to have a deleted filter on undefined elements
            final NativeArray copy = new NativeArray(count);
            for (long n = 0; k < finale; n++, k++) {
                if (sobj.has(k)) {
                    copy.defineOwnProperty(ArrayIndex.getArrayIndex(n), sobj.get(k));
                }
            }
            return copy;
        }

        // ES2015 22.1.3.22 step 9: a species is given the count up front
        final ScriptObject result = speciesCreate(self, count);
        for (long n = 0; k < finale; n++, k++) {
            if (sobj.has(k)) {
                createDataProperty(result, n, sobj.get(k));
            }
        }
        result.set("length", count, CALLSITE_STRICT);
        return result;
    }

    private static Object compareFunction(final Object comparefn) {
        if (comparefn == ScriptRuntime.UNDEFINED) {
            return null;
        }

        if (!Bootstrap.isCallable(comparefn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(comparefn));
        }

        return comparefn;
    }

    /**
     * How far sort is willing to count.
     *
     * ES2015 22.1.3.24 reads every index from zero to the length, which is how
     * it comes to see an inherited element or one behind an accessor. A length
     * is a claim rather than a count, though, and one array-like in the wild
     * claims 4294967295 while holding a single element.
     */
    private static final long SORT_SCAN_LIMIT = 1L << 20;

    /** The largest integer a double holds exactly, which bounds any length. */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    /** The longest an array can be: a length is an unsigned 32 bit number. */
    private static final long MAX_ARRAY_LENGTH = 4294967295L;

    /**
     * ES2015 7.1.15 ToLength, which an array-like's length goes through: up to
     * 2^53-1, where ToUint32 would wrap at 2^32.
     */
    private static long toLength(final Object value) {
        final double number = JSType.toNumber(value);
        if (Double.isNaN(number) || number <= 0) {
            return 0;
        }
        return (long)Math.min(Math.floor(number), 9007199254740991d);
    }

    private static Object[] sort(final Object[] array, final Object comparefn) {
        final Object cmp = compareFunction(comparefn);

        final List<Object> list = Arrays.asList(array);
        final Object cmpThis = cmp == null || Bootstrap.isStrictCallable(cmp) ? ScriptRuntime.UNDEFINED : Global.instance();

        try {
            list.sort(new Comparator<>() {
                private final MethodHandle call_cmp = getCALL_CMP();
                @Override
                public int compare(final Object x, final Object y) {
                    if (x == ScriptRuntime.UNDEFINED && y == ScriptRuntime.UNDEFINED) {
                        return 0;
                    } else if (x == ScriptRuntime.UNDEFINED) {
                        return 1;
                    } else if (y == ScriptRuntime.UNDEFINED) {
                        return -1;
                    }

                    if (cmp != null) {
                        try {
                            return (int)Math.signum((double)call_cmp.invokeExact(cmp, cmpThis, x, y));
                        } catch (final RuntimeException | Error e) {
                            throw e;
                        } catch (final Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }

                    return JSType.toString(x).compareTo(JSType.toString(y));
                }
            });
        } catch (final IllegalArgumentException iae) {
            // Collections.sort throws IllegalArgumentException when
            // Comparison method violates its general contract

            // See ECMA spec 15.4.4.11 Array.prototype.sort (comparefn).
            // If "comparefn" is not undefined and is not a consistent
            // comparison function for the elements of this array, the
            // behaviour of sort is implementation-defined.
        }

        return list.toArray(new Object[0]);
    }

    /**
     * ECMA 15.4.4.11 Array.prototype.sort ( comparefn )
     *
     * @param self       self reference
     * @param comparefn  element comparison function
     * @return sorted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static ScriptObject sort(final Object self, final Object comparefn) {
        // ES2015 22.1.3.24 step 1: the comparison function is checked before
        // anything else, including reading the length
        if (comparefn != ScriptRuntime.UNDEFINED && !Bootstrap.isCallable(comparefn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(comparefn));
        }

        if (!(Global.toObject(self) instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final long len = toLength(sobj.getLength());
        if (len < 2) {
            return sobj;
        }
        // SortIndexedProperties: a hole is left out and reappears at the end as
        // a hole, where an element whose value is undefined is sorted - to the
        // end, but it is still there afterwards. Reading is by Get, so an
        // inherited element or an accessor is seen, which is the whole
        // difference between this and walking the array's storage.
        final List<Object> items = new ArrayList<>();
        final List<Long> occupied = len > SORT_SCAN_LIMIT ? new ArrayList<>() : null;

        if (occupied == null) {
            for (long i = 0; i < len; i++) {
                if (sobj.has(i)) {
                    items.add(sobj.get(i));
                }
            }
        } else {
            // A length nothing could fill is not one to count through: the
            // elements the object actually holds are the ones that can sort,
            // and asking about the 4294967294 absent ones would take longer
            // than any program has. What is given up is an inherited element
            // among them, which is the only thing the count would find.
            for (final Iterator<Long> iter = sobj.getArray().indexIterator(); iter.hasNext(); ) {
                final long index = iter.next();
                if (index >= len) {
                    break;
                }
                occupied.add(index);
                items.add(sobj.get(index));
            }
        }

        final Object[] sorted = sort(items.toArray(), comparefn);

        for (int i = 0; i < sorted.length; i++) {
            sobj.set(i, sorted[i], NashornCallSiteDescriptor.CALLSITE_STRICT);
        }
        if (occupied == null) {
            for (long i = sorted.length; i < len; i++) {
                sobj.delete(i, true);
            }
        } else {
            for (final long index : occupied) {
                if (index >= sorted.length) {
                    sobj.delete(index, true);
                }
            }
        }
        return sobj;
    }

    /**
     * ECMA 15.4.4.12 Array.prototype.splice ( start, deleteCount [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self self reference
     * @param args arguments
     * @return result of splice
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object splice(final Object self, final Object... args) {
        final Object obj = Global.toObject(self);

        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final ScriptObject sobj          = (ScriptObject)obj;
        final long         len           = toLength(sobj.getLength());
        final long         relativeStart = JSType.toLong(args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED);

        final long actualStart = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len);
        final long actualDeleteCount;
        Object[] items = ScriptRuntime.EMPTY_ARRAY;

        if (args.length == 0) {
            actualDeleteCount = 0;
        } else if (args.length == 1) {
            actualDeleteCount = len - actualStart;
        } else {
            actualDeleteCount = Math.min(Math.max(JSType.toLong(args[1]), 0), len - actualStart);
            if (args.length > 2) {
                items = new Object[args.length - 2];
                System.arraycopy(args, 2, items, 0, items.length);
            }
        }

        // ES2015 22.1.3.25 step 11 asks before it starts moving anything, as
        // unshift does: what is put back must still fit below 2^53-1
        if (len + items.length - actualDeleteCount > MAX_SAFE_INTEGER) {
            throw typeError("array.length.exceeded", JSType.toString((double)len));
        }

        if (!hasDefaultSpecies(sobj, Global.instance())) {
            // ES2015 22.1.3.25 step 12: what is removed goes into an array the
            // species makes, and only the removed part does - the splicing
            // itself still happens to this array
            final ScriptObject removed = speciesCreate(self, actualDeleteCount);
            for (long k = 0; k < actualDeleteCount; k++) {
                if (sobj.has(actualStart + k)) {
                    createDataProperty(removed, k, sobj.get(actualStart + k));
                }
            }
            removed.set("length", actualDeleteCount, CALLSITE_STRICT);
            slowSplice(sobj, actualStart, actualDeleteCount, items, len);
            return removed;
        }

        NativeArray returnValue;

        if (actualStart <= Integer.MAX_VALUE && actualDeleteCount <= Integer.MAX_VALUE && bulkable(sobj)) {
            try {
                returnValue = new NativeArray(sobj.getArray().fastSplice((int)actualStart, (int)actualDeleteCount, items.length));

                // Since this is a dense bulkable array we can use faster defineOwnProperty to copy new elements
                int k = (int) actualStart;
                for (int i = 0; i < items.length; i++, k++) {
                    sobj.defineOwnProperty(k, items[i]);
                }
            } catch (final UnsupportedOperationException uoe) {
                returnValue = slowSplice(sobj, actualStart, actualDeleteCount, items, len);
            }
        } else {
            returnValue = slowSplice(sobj, actualStart, actualDeleteCount, items, len);
        }

        return returnValue;
    }

    private static NativeArray slowSplice(final ScriptObject sobj, final long start, final long deleteCount, final Object[] items, final long len) {

        final NativeArray array = new NativeArray(deleteCount);

        for (long k = 0; k < deleteCount; k++) {
            final long from = start + k;

            if (sobj.has(from)) {
                array.defineOwnProperty(ArrayIndex.getArrayIndex(k), sobj.get(from));
            }
        }

        if (items.length < deleteCount) {
            for (long k = start; k < len - deleteCount; k++) {
                final long from = k + deleteCount;
                final long to   = k + items.length;

                if (sobj.has(from)) {
                    sobj.set(to, sobj.get(from), CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (long k = len; k > len - deleteCount + items.length; k--) {
                sobj.delete(k - 1, true);
            }
        } else if (items.length > deleteCount) {
            for (long k = len - deleteCount; k > start; k--) {
                final long from = k + deleteCount - 1;
                final long to   = k + items.length - 1;

                if (sobj.has(from)) {
                    final Object fromValue = sobj.get(from);
                    sobj.set(to, fromValue, CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }
        }

        long k = start;
        for (int i = 0; i < items.length; i++, k++) {
            sobj.set(k, items[i], CALLSITE_STRICT);
        }

        final long newLength = len - deleteCount + items.length;
        sobj.set("length", newLength, CALLSITE_STRICT);

        return array;
    }

    /**
     * ECMA 15.4.4.13 Array.prototype.unshift ( [ item1 [ , item2 [ , ... ] ] ] )
     *
     * @param self  self reference
     * @param items items for unshift
     * @return unshifted array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object unshift(final Object self, final Object... items) {
        final Object obj = Global.toObject(self);

        if (!(obj instanceof ScriptObject)) {
            return ScriptRuntime.UNDEFINED;
        }

        final ScriptObject sobj   = (ScriptObject)obj;
        final long         len    = toLength(sobj.getLength());

        if (items == null) {
            return ScriptRuntime.UNDEFINED;
        }

        // ES2015 22.1.3.28 step 4.a asks before it starts moving anything: there
        // is nowhere past 2^53-1 for the last element to go
        if (items.length > 0 && len + items.length > MAX_SAFE_INTEGER) {
            throw typeError("array.length.exceeded", JSType.toString((double)len));
        }

        if (bulkable(sobj)) {
            sobj.getArray().shiftRight(items.length);

            for (int j = 0; j < items.length; j++) {
                sobj.setArray(sobj.getArray().set(j, items[j], true));
            }
        } else if (items.length > 0) {
            // nothing is inserted and so nothing moves when there are no items
            // - 22.1.3.28 step 4 asks first, and a length near 2^53 makes the
            // difference between returning and walking every index
            for (long k = len; k > 0; k--) {
                final long from = k - 1;
                final long to = k + items.length - 1;

                if (sobj.has(from)) {
                    final Object fromValue = sobj.get(from);
                    sobj.set(to, fromValue, CALLSITE_STRICT);
                } else {
                    sobj.delete(to, true);
                }
            }

            for (int j = 0; j < items.length; j++) {
                sobj.set(j, items[j], CALLSITE_STRICT);
            }
        }

        final long newLength = len + items.length;
        sobj.set("length", newLength, CALLSITE_STRICT);

        return JSType.toNarrowestNumber(newLength);
    }

    /**
     * ECMA 15.4.4.14 Array.prototype.indexOf ( searchElement [ , fromIndex ] )
     *
     * @param self           self reference
     * @param searchElement  element to search for
     * @param fromIndex      start index of search
     * @return index of element, or -1 if not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double indexOf(final Object self, final Object searchElement, final Object fromIndex) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = toLength(sobj.getLength());
            if (len == 0) {
                return -1;
            }

            final long         n = JSType.toLong(fromIndex);
            if (n >= len) {
                return -1;
            }


            for (long k = Math.max(0, n < 0 ? len - Math.abs(n) : n); k < len; k++) {
                if (sobj.has(k)) {
                    if (ScriptRuntime.EQ_STRICT(sobj.get(k), searchElement)) {
                        return k;
                    }
                }
            }
        } catch (final ClassCastException | NullPointerException e) {
            //fallthru
        }

        return -1;
    }

    /**
     * ECMAScript 2016 22.1.3.11 Array.prototype.includes ( searchElement [ , fromIndex ] )
     *
     * Unlike indexOf it compares with SameValueZero, so it finds a NaN that
     * indexOf cannot, and it does not skip holes: a missing element reads as
     * undefined and matches one.
     *
     * @param self          the array
     * @param searchElement what to look for
     * @param fromIndex     where to start, negative counting from the end
     * @return whether it is there
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean includes(final Object self, final Object searchElement, final Object fromIndex) {
        final ScriptObject sobj = Global.toObject(self) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            return false;
        }
        final long length = toLength(sobj.getLength());
        if (length == 0) {
            return false;
        }
        final long relative = JSType.toLong(fromIndex);
        long k = relative < 0 ? Math.max(length + relative, 0) : Math.min(relative, length);
        for (; k < length; k++) {
            if (ScriptRuntime.sameValueZero(searchElement, sobj.get(k))) {
                return true;
            }
        }
        return false;
    }

    /**
     * ECMA 15.4.4.15 Array.prototype.lastIndexOf ( searchElement [ , fromIndex ] )
     *
     * @param self self reference
     * @param args arguments: element to search for and optional from index
     * @return index of element, or -1 if not found
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static double lastIndexOf(final Object self, final Object... args) {
        try {
            final ScriptObject sobj = (ScriptObject)Global.toObject(self);
            final long         len  = toLength(sobj.getLength());

            if (len == 0) {
                return -1;
            }

            final Object searchElement = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
            final long   n             = args.length > 1 ? JSType.toLong(args[1]) : len - 1;

            for (long k = n < 0 ? len - Math.abs(n) : Math.min(n, len - 1); k >= 0; k--) {
                if (sobj.has(k)) {
                    if (ScriptRuntime.EQ_STRICT(sobj.get(k), searchElement)) {
                        return k;
                    }
                }
            }
        } catch (final ClassCastException | NullPointerException e) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }

        return -1;
    }

    /**
     * ECMA 15.4.4.16 Array.prototype.every ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return true if callback function return true for every element in the array, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean every(final Object self, final Object callbackfn, final Object thisArg) {
        return applyEvery(Global.toObject(self), callbackfn, thisArg);
    }

    private static boolean applyEvery(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<>(Global.toObject(self), callbackfn, thisArg, true) {
            private final MethodHandle everyInvoker = getEVERY_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                return result = (boolean)everyInvoker.invokeExact(callbackfn, thisArg, val, i, self);
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.17 Array.prototype.some ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return true if callback function returned true for any element in the array, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean some(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<>(Global.toObject(self), callbackfn, thisArg, false) {
            private final MethodHandle someInvoker = getSOME_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                return !(result = (boolean)someInvoker.invokeExact(callbackfn, thisArg, val, i, self));
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.18 Array.prototype.forEach ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object forEach(final Object self, final Object callbackfn, final Object thisArg) {
        return new IteratorAction<Object>(Global.toObject(self), callbackfn, thisArg, ScriptRuntime.UNDEFINED) {
            private final MethodHandle forEachInvoker = getFOREACH_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                forEachInvoker.invokeExact(callbackfn, thisArg, val, i, self);
                return true;
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.19 Array.prototype.map ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return array with elements transformed by map function
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static ScriptObject map(final Object self, final Object callbackfn, final Object thisArg) {
        final boolean ordinary = hasDefaultSpecies(self, Global.instance());
        return new IteratorAction<ScriptObject>(Global.toObject(self), callbackfn, thisArg, null) {
            private final MethodHandle mapInvoker = getMAP_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                final Object r = mapInvoker.invokeExact(callbackfn, thisArg, val, i, self);
                if (ordinary) {
                    result.defineOwnProperty(ArrayIndex.getArrayIndex(index), r);
                } else {
                    // CreateDataPropertyOrThrow, which replaces what a
                    // species-provided array already has at that index,
                    // attributes and all - and costs a descriptor to say so,
                    // which is why an ordinary array does not go through it
                    createDataProperty(result, index, r);
                }
                return true;
            }

            @Override
            public void applyLoopBegin(final ArrayLikeIterator<Object> iter0) {
                // map return array should be of same length as source array
                // even if callback reduces source array length
                result = speciesCreate(self, iter0.getLength());
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.20 Array.prototype.filter ( callbackfn [ , thisArg ] )
     *
     * @param self        self reference
     * @param callbackfn  callback function per element
     * @param thisArg     this argument
     * @return filtered array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static ScriptObject filter(final Object self, final Object callbackfn, final Object thisArg) {
        final boolean ordinary = hasDefaultSpecies(self, Global.instance());
        return new IteratorAction<ScriptObject>(Global.toObject(self), callbackfn, thisArg, speciesCreate(self, 0)) {
            private long to = 0;
            private final MethodHandle filterInvoker = getFILTER_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                if ((boolean)filterInvoker.invokeExact(callbackfn, thisArg, val, i, self)) {
                    if (ordinary) {
                        result.defineOwnProperty(ArrayIndex.getArrayIndex(to++), val);
                    } else {
                        createDataProperty(result, to++, val);
                    }
                }
                return true;
            }
        }.apply();
    }

    /**
     * ES2019 22.1.3.10 Array.prototype.flat ( [ depth ] )
     *
     * @param self  self reference
     * @param depth how many levels deep to flatten (default 1)
     * @return a new array with sub-array elements flattened into it
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object flat(final Object self, final Object depth) {
        final ScriptObject source = (ScriptObject) Global.toObject(self);
        final long sourceLen = toLength(source.get("length"));
        double depthNum = 1;
        if (depth != ScriptRuntime.UNDEFINED) {
            depthNum = JSType.toInteger(depth);
        }
        final ScriptObject result = speciesCreate(self, 0);
        flattenIntoArray(result, source, sourceLen, 0, depthNum, null, null);
        return result;
    }

    /**
     * ES2019 22.1.3.11 Array.prototype.flatMap ( callbackfn [ , thisArg ] )
     *
     * @param self       self reference
     * @param callbackfn maps each element before it is flattened one level
     * @param thisArg    this value for the callback
     * @return a new array of the mapped elements, flattened one level
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object flatMap(final Object self, final Object callbackfn, final Object thisArg) {
        final ScriptObject source = (ScriptObject) Global.toObject(self);
        final long sourceLen = toLength(source.get("length"));
        if (!Bootstrap.isCallable(callbackfn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackfn));
        }
        final ScriptObject result = speciesCreate(self, 0);
        flattenIntoArray(result, source, sourceLen, 0, 1, callbackfn, thisArg);
        return result;
    }

    /**
     * ES2019 22.1.3.10.1 FlattenIntoArray: copy {@code source}'s own indexed
     * elements into {@code target} starting at {@code start}, descending
     * {@code depth} levels into any element that is itself an array (holes are
     * skipped). With a mapper set (flatMap), each element is mapped first and
     * only one level is flattened.
     *
     * @return the next free index in {@code target}
     */
    private static long flattenIntoArray(final ScriptObject target, final ScriptObject source, final long sourceLen,
            final long start, final double depth, final Object mapper, final Object thisArg) {
        final MethodHandle mapInvoker = mapper == null ? null : getMAP_CALLBACK_INVOKER();
        long targetIndex = start;
        for (long k = 0; k < sourceLen; k++) {
            if (source.has((double) k)) {
                Object element = source.get((double) k);
                if (mapInvoker != null) {
                    try {
                        element = mapInvoker.invokeExact(mapper, thisArg, element, (double) k, (Object) source);
                    } catch (final RuntimeException | Error e) {
                        throw e;
                    } catch (final Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
                if (depth > 0 && isArray(null, element)) {
                    final ScriptObject inner = (ScriptObject) Global.toObject(element);
                    targetIndex = flattenIntoArray(target, inner, toLength(inner.get("length")),
                            targetIndex, depth - 1, null, null);
                } else {
                    if (targetIndex >= 9007199254740991L) {
                        // 2^53 - 1: more indices than an array-like can name
                        throw typeError("array.length.exceeded", Long.toString(targetIndex));
                    }
                    createDataProperty(target, targetIndex, element);
                    targetIndex++;
                }
            }
        }
        return targetIndex;
    }

    /**
     * ES2015 9.4.2.3 ArraySpeciesCreate: what the operations deriving one array
     * from another build.
     *
     * The answer is an ordinary array unless a subclass has said otherwise, and
     * the check for that is one reference comparison: an array whose prototype
     * is the realm's own Array.prototype has the realm's own constructor and so
     * the default species. Walking constructor and @@species on every map or
     * filter of a plain array would put two property reads on a hot path to
     * reach a conclusion already known.
     *
     * @param original the array being derived from
     * @param length   the length to create with
     * @return the object to fill in
     */
    /**
     * Whether an array is one whose derived arrays are ordinary arrays.
     *
     * An array whose prototype is the realm's own Array.prototype, and which
     * does not shadow constructor with one of its own, has the realm's
     * constructor and so the default species. That is two reference
     * comparisons where the full answer is two property reads and a call, on a
     * path that map, filter, slice and concat all sit on.
     */
    private static boolean hasDefaultSpecies(final Object array, final Global global) {
        // A proxy answers for its target and what it answers with is its
        // handler's business, so there is no shape here to read without asking.
        if (!(array instanceof NativeArray sobj) || sobj.getProto() != global.getArrayPrototype()) {
            return false;
        }
        // An array that has been given no property of its own beyond the length
        // it is born with cannot be shadowing constructor, and asking the map
        // how many it has is a field read where asking it for one by name is a
        // hash lookup - on a path map and concat take once per call.
        final PropertyMap map = sobj.getMap();
        return map.size() <= 1 || map.findProperty("constructor") == null;
    }

    /**
     * ES2015 7.2.2 IsArray, which looks through however many proxies stand in
     * the way - and throws for a revoked one, which has nothing to look through
     * to.
     */
    private static boolean isArrayThroughProxies(final ScriptObject sobj) {
        return isArray(sobj instanceof NativeProxy proxy ? proxy.unwrap() : sobj);
    }

    private static ScriptObject speciesCreate(final Object original, final long length) {
        final Global global = Global.instance();
        if (!(original instanceof ScriptObject sobj) || !isArrayThroughProxies(sobj)) {
            return arrayCreate(length);
        }
        if (hasDefaultSpecies(sobj, global)) {
            return arrayCreate(length);
        }

        final Object constructor = sobj.get("constructor");
        if (constructor == ScriptRuntime.UNDEFINED) {
            return arrayCreate(length);
        }
        if (!(constructor instanceof ScriptObject ctor)) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        final Object species = ctor.get(NativeSymbol.species);
        if (species == ScriptRuntime.UNDEFINED || species == null
                || species == global.get("Array")) {
            return arrayCreate(length);
        }
        if (!(species instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(species));
        }
        // 9.4.2.3 step 11 hands the length to the species as a number and lets
        // it make what it likes of it: only the array this would otherwise have
        // made has a length it cannot hold
        final Object created = ScriptRuntime.construct(function, (double)length);
        if (created instanceof ScriptObject target) {
            return target;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(created));
    }

    /** ES2015 9.4.2.2 ArrayCreate: an array cannot be 2^32 long or longer. */
    private static NativeArray arrayCreate(final long length) {
        if (length > MAX_ARRAY_LENGTH) {
            throw rangeError("inappropriate.array.length", JSType.toString((double)length));
        }
        return new NativeArray(length);
    }

    private static Object reduceInner(final ArrayLikeIterator<Object> iter, final Object self, final Object... args) {
        final Object  callbackfn          = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final boolean initialValuePresent = args.length > 1;

        Object initialValue = initialValuePresent ? args[1] : ScriptRuntime.UNDEFINED;

        if (callbackfn == ScriptRuntime.UNDEFINED) {
            throw typeError("not.a.function", "undefined");
        }

        if (!initialValuePresent) {
            if (iter.hasNext()) {
                initialValue = iter.next();
            } else {
                throw typeError("array.reduce.invalid.init");
            }
        }

        //if initial value is ScriptRuntime.UNDEFINED - step forward once.
        return new IteratorAction<>(Global.toObject(self), callbackfn, ScriptRuntime.UNDEFINED, initialValue, iter) {
            private final MethodHandle reduceInvoker = getREDUCE_CALLBACK_INVOKER();

            @Override
            protected boolean forEach(final Object val, final double i) throws Throwable {
                // the this a callback with none of its own is called with,
                // which a non-strict one reads as the global object
                result = reduceInvoker.invokeExact(callbackfn, thisArg, result, val, i, self);
                return true;
            }
        }.apply();
    }

    /**
     * ECMA 15.4.4.21 Array.prototype.reduce ( callbackfn [ , initialValue ] )
     *
     * @param self self reference
     * @param args arguments to reduce
     * @return accumulated result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduce(final Object self, final Object... args) {
        return reduceInner(arrayLikeIterator(self), self, args);
    }

    /**
     * ECMA 15.4.4.22 Array.prototype.reduceRight ( callbackfn [ , initialValue ] )
     *
     * @param self        self reference
     * @param args arguments to reduce
     * @return accumulated result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object reduceRight(final Object self, final Object... args) {
        return reduceInner(reverseArrayLikeIterator(self), self, args);
    }

    /**
     * ECMA6 22.1.3.4 Array.prototype.entries ( )
     *
     * @param self the self reference
     * @return an iterator over the array's entries
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return ArrayIterator.newArrayKeyValueIterator(self);
    }

    /**
     * ECMA6 22.1.3.13 Array.prototype.keys ( )
     *
     * @param self the self reference
     * @return an iterator over the array's keys
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object keys(final Object self) {
        return ArrayIterator.newArrayKeyIterator(self);
    }

    /**
     * ECMA6 22.1.3.29 Array.prototype.values ( )
     *
     * @param self the self reference
     * @return an iterator over the array's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return ArrayIterator.newArrayValueIterator(self);
    }

    /**
     * Determine if Java bulk array operations may be used on the underlying
     * storage. This is possible only if the object's prototype chain is empty
     * or each of the prototypes in the chain is empty.
     *
     * @param self the object to examine
     * @return true if optimizable
     */
    private static boolean bulkable(final ScriptObject self) {
        return self.isArray() && !hasInheritedArrayEntries(self) && !self.isLengthNotWritable()
                && !self.getArray().isSealed();
    }

    private static boolean hasInheritedArrayEntries(final ScriptObject self) {
        ScriptObject proto = self.getProto();
        while (proto != null) {
            if (proto.hasArrayEntries()) {
                return true;
            }
            proto = proto.getProto();
        }

        return false;
    }

    @Override
    public String toString() {
        return "NativeArray@" + Debug.id(this) + " [" + getArray().getClass().getSimpleName() + ']';
    }

    @Override
    public SpecializedFunction.LinkLogic getLinkLogic(final Class<? extends LinkLogic> clazz) {
        if (clazz == PushLinkLogic.class) {
            return PushLinkLogic.INSTANCE;
        } else if (clazz == PopLinkLogic.class) {
            return PopLinkLogic.INSTANCE;
        } else if (clazz == ConcatLinkLogic.class) {
            return ConcatLinkLogic.INSTANCE;
        }
        return null;
    }

    @Override
    public boolean hasPerInstanceAssumptions() {
        return true; //length writable switchpoint
    }

    /**
     * This is an abstract super class that contains common functionality for all
     * specialized optimistic builtins in NativeArray. For example, it handles the
     * modification switchpoint which is touched when length is written.
     */
    private static abstract class ArrayLinkLogic extends SpecializedFunction.LinkLogic {
        protected ArrayLinkLogic() {
        }

        protected static ContinuousArrayData getContinuousArrayData(final Object self) {
            try {
                //cast to NativeArray, to avoid cases like x = {0:0, 1:1}, x.length = 2, where we can't use the array push/pop
                return (ContinuousArrayData)((NativeArray)self).getArray();
            } catch (final Exception e) {
                return null;
            }
        }

        /**
         * Push and pop callsites can throw ClassCastException as a mechanism to have them
         * relinked - this enabled fast checks of the kind of ((IntArrayData)arrayData).push(x)
         * for an IntArrayData only push - if this fails, a CCE will be thrown and we will relink
         */
        @Override
        public Class<? extends Throwable> getRelinkException() {
            return ClassCastException.class;
        }
    }

    /**
     * This is linker logic for optimistic concatenations
     */
    private static final class ConcatLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new ConcatLinkLogic();

        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            final Object[] args = request.getArguments();

            if (args.length != 3) { //single argument check
                return false;
            }

            final ContinuousArrayData selfData = getContinuousArrayData(self);
            if (selfData == null) {
                return false;
            }

            final Object arg = args[2];
            // The generic version uses its own logic and ArrayLikeIterator to decide if an object should
            // be iterated over or added as single element. To avoid duplication of code and err on the safe side
            // we only use the specialized version if arg is either a continuous array or a JS primitive.
            if (arg instanceof NativeArray) {
                return (getContinuousArrayData(arg) != null);
            }

            return JSType.isPrimitive(arg);
        }
    }

    /**
     * This is linker logic for optimistic pushes
     */
    private static final class PushLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new PushLinkLogic();

        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            return getContinuousArrayData(self) != null;
        }
    }

    /**
     * This is linker logic for optimistic pops
     */
    private static final class PopLinkLogic extends ArrayLinkLogic {
        private static final LinkLogic INSTANCE = new PopLinkLogic();

        /**
         * We need to check if we are dealing with a continuous non empty array data here,
         * as pop with a primitive return value returns undefined for arrays with length 0
         */
        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            final ContinuousArrayData data = getContinuousNonEmptyArrayData(self);
            if (data != null) {
                final Class<?> elementType = data.getElementType();
                final Class<?> returnType  = desc.getMethodType().returnType();
                return JSType.getAccessorTypeIndex(returnType) >= JSType.getAccessorTypeIndex(elementType); // type fits
            }
            return false;
        }

        private static ContinuousArrayData getContinuousNonEmptyArrayData(final Object self) {
            final ContinuousArrayData data = getContinuousArrayData(self);
            if (data != null) {
                return data.isEmpty() ? null : data;
            }
            return null;
        }
    }

    //runtime calls for push and pops. they could be used as guards, but they also perform the runtime logic,
    //so rather than synthesizing them into a guard method handle that would also perform the push on the
    //retrieved receiver, we use this as runtime logic

    //TODO - fold these into the Link logics, but I'll do that as a later step, as I want to do a checkin
    //where everything works first

    private static ContinuousArrayData getContinuousNonEmptyArrayDataCCE(final Object self) {
        try {
            final ContinuousArrayData data = (ContinuousArrayData) ((NativeArray)self).getArray();
            if (!data.isEmpty()) {
                return data; //if length is 0 we cannot pop and have to relink, because then we'd have to return an undefined, which is a wider type than e.g. int
           }
        } catch (final NullPointerException e) {
            //fallthru
        }
        throw new ClassCastException();
    }

    private static ContinuousArrayData getContinuousArrayDataCCE(final Object self) {
        try {
            return (ContinuousArrayData)((NativeArray)self).getArray();
         } catch (final NullPointerException e) {
             throw new ClassCastException();
         }
    }

    private static ContinuousArrayData getContinuousArrayDataCCE(final Object self, final Class<?> elementType) {
        try {
           return (ContinuousArrayData)((NativeArray)self).getArray(elementType); //ensure element type can fit "elementType"
        } catch (final NullPointerException e) {
            throw new ClassCastException();
        }
    }

    /**
     * ECMAScript 2015 22.1.3.8 Array.prototype.find(predicate, thisArg)
     *
     * @param self      self reference
     * @param predicate called for each element until it returns a truthy value
     * @param thisArg   the this value for the predicate
     * @return the first element the predicate accepts, or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object find(final Object self, final Object predicate, final Object thisArg) {
        return findInternal(self, predicate, thisArg, true);
    }

    /**
     * ECMAScript 2015 22.1.3.9 Array.prototype.findIndex(predicate, thisArg)
     *
     * @param self      self reference
     * @param predicate called for each element until it returns a truthy value
     * @param thisArg   the this value for the predicate
     * @return the index of the first element the predicate accepts, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object findIndex(final Object self, final Object predicate, final Object thisArg) {
        return findInternal(self, predicate, thisArg, false);
    }

    /**
     * Shared by find and findIndex, which differ only in what they return.
     *
     * Unlike the other iteration methods these visit holes too, so an absent
     * element is offered to the predicate as undefined.
     */
    private static Object findInternal(final Object self, final Object predicate, final Object thisArg,
            final boolean wantValue) {
        final ScriptObject sobj = Global.toObject(self) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        // 22.1.3.8 reads the length before it looks at the predicate, so an
        // abrupt one is what the caller sees rather than the type error
        final long length = toLength(sobj.getLength());
        final ScriptFunction callback = asFunction(predicate);

        for (long i = 0; i < length; i++) {
            final Object value = sobj.get(i);
            if (JSType.toBoolean(ScriptRuntime.apply(callback, thisArg, value, (double)i, sobj))) {
                return wantValue ? value : (double)i;
            }
        }
        return wantValue ? ScriptRuntime.UNDEFINED : Double.valueOf(-1);
    }

    /**
     * ECMAScript 2015 22.1.3.6 Array.prototype.fill(value, start, end)
     *
     * @param self  self reference
     * @param value what to write
     * @param start first index, negative counting from the end
     * @param end   one past the last index, negative counting from the end
     * @return the array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object fill(final Object self, final Object value, final Object start, final Object end) {
        final ScriptObject sobj = Global.toObject(self) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final long length = toLength(sobj.getLength());
        final long from = relativeIndex(start, length, 0);
        final long to = relativeIndex(end, length, length);

        for (long i = from; i < to; i++) {
            sobj.set(i, value, CALLSITE_STRICT);
        }
        return sobj;
    }

    /**
     * ECMAScript 2015 22.1.3.3 Array.prototype.copyWithin(target, start, end)
     *
     * @param self   self reference
     * @param target where to copy to
     * @param start  where to copy from
     * @param end    one past the last index to copy
     * @return the array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object copyWithin(final Object self, final Object target, final Object start, final Object end) {
        final ScriptObject sobj = Global.toObject(self) instanceof ScriptObject o ? o : null;
        if (sobj == null) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        final long length = toLength(sobj.getLength());
        final long to = relativeIndex(target, length, 0);
        final long from = relativeIndex(start, length, 0);
        final long last = relativeIndex(end, length, length);
        long count = Math.min(last - from, length - to);

        // copy backwards when the ranges overlap the wrong way, as memmove would
        long readAt = from;
        long writeAt = to;
        int step = 1;
        if (from < to && to < from + count) {
            step = -1;
            readAt += count - 1;
            writeAt += count - 1;
        }

        while (count > 0) {
            if (sobj.has(readAt)) {
                sobj.set(writeAt, sobj.get(readAt), CALLSITE_STRICT);
            } else {
                sobj.delete(writeAt, true);
            }
            readAt += step;
            writeAt += step;
            count--;
        }
        return sobj;
    }

    /**
     * ECMAScript 2015 22.1.2.3 Array.of(...items)
     *
     * @param self self reference
     * @param args the elements
     * @return a new array of exactly those elements
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 0)
    public static Object of(final Object self, final Object... args) {
        // unlike the Array constructor, a single numeric argument is an element
        // rather than a length
        final ScriptObject target = create(self, (double)args.length);
        for (int i = 0; i < args.length; i++) {
            define(target, i, args[i]);
        }
        target.set("length", (double)args.length, CALLSITE_STRICT);
        return target;
    }

    /**
     * ECMAScript 2015 22.1.2.1 Array.from(items, mapFn, thisArg)
     *
     * @param self self reference
     * @param args the source, an optional mapping function, and its this value
     * @return a new array built from the source
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static Object from(final Object self, final Object... args) {
        final Object items = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object mapFn = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object thisArg = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;

        if (items == null || items == ScriptRuntime.UNDEFINED) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(items));
        }
        final ScriptFunction mapper = mapFn == ScriptRuntime.UNDEFINED ? null : asFunction(mapFn);

        // ES2015 22.1.2.1 steps 5.a and 7.b: the target is built before anything
        // is read, so a constructor that throws does so before the iterator is
        // even asked for. It also maps each element as it arrives rather than
        // afterwards, so the mapper's side effects interleave with the
        // iterator's and an error from it stops the walk where it happened.
        if (isIterable(items)) {
            final ScriptObject target = create(self, null);
            final Iterator<?> iterator = ScriptRuntime.toES6Iterator(items);
            long index = 0;
            try {
                while (iterator.hasNext()) {
                    final Object element = iterator.next();
                    define(target, index, mapper == null ? element
                            : ScriptRuntime.apply(mapper, thisArg, element, (double)index));
                    index++;
                }
            } catch (final RuntimeException e) {
                // steps 7.e and 7.g: what the mapper throws, or what defining
                // the property throws, ends the walk - and 7.4.6 tells the
                // iterator so, without reporting what it makes of that
                ScriptRuntime.ITERATOR_CLOSE_QUIET(iterator);
                throw e;
            }
            target.set("length", (double)index, CALLSITE_STRICT);
            return target;
        }

        // array-like: read by index up to length
        final Object source = JSType.toScriptObject(Global.instance(), items);
        final long length = source instanceof ScriptObject sobj ? toLength(sobj.getLength()) : 0;
        final ScriptObject target = create(self, (double)length);
        if (source instanceof ScriptObject sobj) {
            for (long i = 0; i < length; i++) {
                final Object element = sobj.get(i);
                define(target, i, mapper == null ? element
                        : ScriptRuntime.apply(mapper, thisArg, element, (double)i));
            }
        }
        target.set("length", (double)length, CALLSITE_STRICT);
        return target;
    }

    /**
     * Builds the result of Array.from and Array.of.
     *
     * Both are generic: called on a constructor other than Array - which is how
     * a subclass inherits them - they construct that instead, and the elements
     * are defined on it one by one, so that a constructor returning a hostile
     * object reports its own errors.
     */
    /**
     * The object {@code Array.from} and {@code Array.of} fill in: whatever the
     * this they were called on constructs, or a plain array when that is Array
     * itself or not a constructor at all (ES2015 22.1.2.1 step 5.a, 22.1.2.3
     * step 3).
     *
     * @param self   the constructor they were called on
     * @param length the argument to give it, or null to construct with none
     */
    private static ScriptObject create(final Object self, final Double length) {
        if (!(self instanceof ScriptFunction constructor) || !constructor.isConstructor()
                || self == Global.instance().get("Array")) {
            return length == null ? new NativeArray() : new NativeArray(length.longValue());
        }
        final Object created = length == null
                ? ScriptRuntime.construct(constructor)
                : ScriptRuntime.construct(constructor, length);
        if (created instanceof ScriptObject target) {
            return target;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(created));
    }

    /**
     * CreateDataPropertyOrThrow, not Set: an existing non-configurable element
     * has to make this fail even when it is writable.
     */
    private static void define(final ScriptObject target, final long index, final Object value) {
        final ScriptObject descriptor = Global.newEmptyInstance();
        descriptor.set("value", value, 0);
        descriptor.set("writable", true, 0);
        descriptor.set("enumerable", true, 0);
        descriptor.set("configurable", true, 0);
        target.defineOwnProperty(JSType.toString(index), descriptor, true);
    }

    /**
     * Whether Array.from should walk this value with the iterator protocol
     * rather than by index. A string is iterable, and so is anything carrying
     * Symbol.iterator.
     */
    private static boolean isIterable(final Object items) {
        if (JSType.isString(items) || items instanceof NativeString) {
            return true;
        }
        final Object object = JSType.toScriptObject(Global.instance(), items);
        return object instanceof ScriptObject sobj
                && sobj.get(NativeSymbol.iterator) instanceof ScriptFunction;
    }

    /** Resolves a possibly negative or absent index against a length, as the spec's RelativeIndex does. */
    private static long relativeIndex(final Object index, final long length, final long whenUndefined) {
        if (index == ScriptRuntime.UNDEFINED) {
            return whenUndefined;
        }
        // ToInteger, done in doubles: an index near 2^53 does not fit an int,
        // and the one it would be clamped to is a different element
        final double number = JSType.toNumber(index);
        final double relative = Double.isNaN(number) ? 0
                : number < 0 ? Math.ceil(number) : Math.floor(number);
        return relative < 0 ? (long)Math.max(length + relative, 0) : (long)Math.min(relative, length);
    }

    /** Coerces a callback argument, rejecting anything that cannot be called. */
    private static ScriptFunction asFunction(final Object callback) {
        if (callback instanceof ScriptFunction function) {
            return function;
        }
        throw typeError("not.a.function", ScriptRuntime.safeToString(callback));
    }
}
