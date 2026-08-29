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

import static org.openjdk.nashorn.internal.lookup.Lookup.MH;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.JSType.isRepresentableAsInt;
import static org.openjdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import org.openjdk.nashorn.internal.lookup.MethodHandleFactory.LookupException;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Getter;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.SpecializedFunction;
import org.openjdk.nashorn.internal.objects.annotations.SpecializedFunction.LinkLogic;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.ConsString;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.OptimisticBuiltins;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.Symbol;
import org.openjdk.nashorn.internal.runtime.WellKnownSymbols;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.arrays.ArrayIndex;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;
import org.openjdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import org.openjdk.nashorn.internal.runtime.linker.NashornGuards;
import org.openjdk.nashorn.internal.runtime.linker.PrimitiveLookup;


/**
 * ECMA 15.5 String Objects.
 */
@ScriptClass("String")
public final class NativeString extends ScriptObject implements OptimisticBuiltins {

    private final CharSequence value;

    /** Method handle to create an object wrapper for a primitive string */
    static final MethodHandle WRAPFILTER = findOwnMH("wrapFilter", MH.type(NativeString.class, Object.class));
    /** Method handle to retrieve the String prototype object */
    private static final MethodHandle PROTOFILTER = findOwnMH("protoFilter", MH.type(Object.class, Object.class));

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeString(final CharSequence value) {
        this(value, Global.instance());
    }

    NativeString(final CharSequence value, final Global global) {
        this(value, global.getStringPrototype(), $nasgenmap$);
    }

    private NativeString(final CharSequence value, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        assert JSType.isString(value);
        this.value = value;
    }

    @Override
    public String safeToString() {
        return "[String " + this + "]";
    }

    @Override
    public String toString() {
        return getStringValue();
    }

    private String getStringValue() {
        return value instanceof String ? (String) value : value.toString();
    }

    private CharSequence getValue() {
        return value;
    }

    @Override
    public String getClassName() {
        return "String";
    }

    @Override
    public Object getLength() {
        return value.length();
    }

    // This is to support length as method call as well.
    @Override
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String name = NashornCallSiteDescriptor.getOperand(desc);

        // if str.length(), then let the bean linker handle it
        if ("length".equals(name) && NashornCallSiteDescriptor.isMethodFirstOperation(desc)) {
            return null;
        }

        return super.findGetMethod(desc, request);
    }

    // This is to provide array-like access to string characters without creating a NativeString wrapper.
    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final Object self = request.getReceiver();
        final Class<?> returnType = desc.getMethodType().returnType();

        if (returnType == Object.class && JSType.isString(self)) {
            try {
                return new GuardedInvocation(MH.findStatic(MethodHandles.lookup(), NativeString.class, "get", desc.getMethodType()), NashornGuards.getStringGuard());
            } catch (final LookupException e) {
                //empty. Shouldn't happen. Fall back to super
            }
        }
        return super.findGetIndexMethod(desc, request);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final Object key) {
        final CharSequence cs = JSType.toCharSequence(self);
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        if (index >= 0 && index < cs.length()) {
            return String.valueOf(cs.charAt(index));
        }
        return ((ScriptObject) Global.toObject(self)).get(primitiveKey);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final double key) {
        if (isRepresentableAsInt(key)) {
            return get(self, (int)key);
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    @SuppressWarnings("unused")
    private static Object get(final Object self, final long key) {
        final CharSequence cs = JSType.toCharSequence(self);
        if (key >= 0 && key < cs.length()) {
            return String.valueOf(cs.charAt((int)key));
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    private static Object get(final Object self, final int key) {
        final CharSequence cs = JSType.toCharSequence(self);
        if (key >= 0 && key < cs.length()) {
            return String.valueOf(cs.charAt(key));
        }
        return ((ScriptObject) Global.toObject(self)).get(key);
    }

    // String characters can be accessed with array-like indexing..
    @Override
    public Object get(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        if (index >= 0 && index < value.length()) {
            return String.valueOf(value.charAt(index));
        }
        return super.get(primitiveKey);
    }

    @Override
    public Object get(final double key) {
        if (isRepresentableAsInt(key)) {
            return get((int)key);
        }
        return super.get(key);
    }

    @Override
    public Object get(final int key) {
        if (key >= 0 && key < value.length()) {
            return String.valueOf(value.charAt(key));
        }
        return super.get(key);
    }

    @Override
    public int getInt(final Object key, final int programPoint) {
        return JSType.toInt32MaybeOptimistic(get(key), programPoint);
    }

    @Override
    public int getInt(final double key, final int programPoint) {
        return JSType.toInt32MaybeOptimistic(get(key), programPoint);
    }

    @Override
    public int getInt(final int key, final int programPoint) {
        return JSType.toInt32MaybeOptimistic(get(key), programPoint);
    }

    @Override
    public double getDouble(final Object key, final int programPoint) {
        return JSType.toNumberMaybeOptimistic(get(key), programPoint);
    }

    @Override
    public double getDouble(final double key, final int programPoint) {
        return JSType.toNumberMaybeOptimistic(get(key), programPoint);
    }

    @Override
    public double getDouble(final int key, final int programPoint) {
        return JSType.toNumberMaybeOptimistic(get(key), programPoint);
    }

    @Override
    public boolean has(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return isValidStringIndex(index) || super.has(primitiveKey);
    }

    @Override
    public boolean has(final int key) {
        return isValidStringIndex(key) || super.has(key);
    }

    @Override
    public boolean has(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValidStringIndex(index) || super.has(key);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return isValidStringIndex(index) || super.hasOwnProperty(primitiveKey);
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        return isValidStringIndex(key) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isValidStringIndex(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        return !checkDeleteIndex(key, strict) && super.delete(key, strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return !checkDeleteIndex(index, strict) && super.delete(key, strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return !checkDeleteIndex(index, strict) && super.delete(primitiveKey, strict);
    }

    private boolean checkDeleteIndex(final int index, final boolean strict) {
        if (isValidStringIndex(index)) {
            if (strict) {
                throw typeError("cant.delete.property", Integer.toString(index), ScriptRuntime.safeToString(this));
            }
            return true;
        }

        return false;
    }

    @Override
    public Object getOwnPropertyDescriptor(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (index >= 0 && index < value.length()) {
            final Global global = Global.instance();
            return global.newDataDescriptor(String.valueOf(value.charAt(index)), false, true, false);
        }

        return super.getOwnPropertyDescriptor(key);
    }

    /**
     * return a List of own keys associated with the object.
     * @param all True if to include non-enumerable keys.
     * @param nonEnumerable set of non-enumerable properties seen already.Used
     * to filter out shadowed, but enumerable properties from proto children.
     * @return Array of keys.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T[] getOwnKeys(final Class<T> type, final boolean all, final Set<T> nonEnumerable) {
        if (type != String.class) {
            return super.getOwnKeys(type, all, nonEnumerable);
        }

        final List<Object> keys = new ArrayList<>();

        // add string index keys
        for (int i = 0; i < value.length(); i++) {
            keys.add(JSType.toString(i));
        }

        // add super class properties
        keys.addAll(Arrays.asList(super.getOwnKeys(type, all, nonEnumerable)));
        return keys.toArray((T[]) Array.newInstance(type, keys.size()));
    }

    /**
     * ECMA 15.5.3 String.length
     * @param self self reference
     * @return     value of length property for string
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        return getCharSequence(self).length();
    }

    /**
     * ECMA 15.5.3.2 String.fromCharCode ( [ char0 [ , char1 [ , ... ] ] ] )
     * @param self  self reference
     * @param args  array of arguments to be interpreted as char
     * @return string with arguments translated to charcodes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1, where = Where.CONSTRUCTOR)
    public static String fromCharCode(final Object self, final Object... args) {
        final char[] buf = new char[args.length];
        int index = 0;
        for (final Object arg : args) {
            buf[index++] = (char)JSType.toUint16(arg);
        }
        return new String(buf);
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final Object value) {
        if (value instanceof Integer) {
            return fromCharCode(self, (int)value);
        }
        return Character.toString((char)JSType.toUint16(value));
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of int type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static String fromCharCode(final Object self, final int value) {
        return Character.toString((char)(value & 0xffff));
    }

    /**
     * ECMA 15.5.3.2 - specialization for two chars of int type
     * @param self  self reference
     * @param ch1 first char
     * @param ch2 second char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final int ch1, final int ch2) {
        return Character.toString((char)(ch1 & 0xffff)) + Character.toString((char)(ch2 & 0xffff));
    }

    /**
     * ECMA 15.5.3.2 - specialization for three chars of int type
     * @param self  self reference
     * @param ch1 first char
     * @param ch2 second char
     * @param ch3 third char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static Object fromCharCode(final Object self, final int ch1, final int ch2, final int ch3) {
        return Character.toString((char)(ch1 & 0xffff)) + Character.toString((char)(ch2 & 0xffff)) + Character.toString((char)(ch3 & 0xffff));
    }

    /**
     * ECMA 15.5.3.2 - specialization for four chars of int type
     * @param self  self reference
     * @param ch1 first char
     * @param ch2 second char
     * @param ch3 third char
     * @param ch4 fourth char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static String fromCharCode(final Object self, final int ch1, final int ch2, final int ch3, final int ch4) {
        return Character.toString((char)(ch1 & 0xffff)) + Character.toString((char)(ch2 & 0xffff)) + Character.toString((char)(ch3 & 0xffff)) + Character.toString((char)(ch4 & 0xffff));
    }

    /**
     * ECMA 15.5.3.2 - specialization for one char of double type
     * @param self  self reference
     * @param value one argument to be interpreted as char
     * @return string with one charcode
     */
    @SpecializedFunction
    public static String fromCharCode(final Object self, final double value) {
        return Character.toString((char)JSType.toUint16(value));
    }

    /**
     * ECMA 15.5.4.2 String.prototype.toString ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.3 String.prototype.valueOf ( )
     * @param self self reference
     * @return self as string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String valueOf(final Object self) {
        return getString(self);
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String charAt(final Object self, final Object pos) {
        return charAtImpl(checkObjectToString(self), JSType.toInteger(pos));
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos) - specialized version for double position
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @SpecializedFunction
    public static String charAt(final Object self, final double pos) {
        return charAt(self, (int)pos);
    }

    /**
     * ECMA 15.5.4.4 String.prototype.charAt (pos) - specialized version for int position
     * @param self self reference
     * @param pos  position in string
     * @return string representing the char at the given position
     */
    @SpecializedFunction
    public static String charAt(final Object self, final int pos) {
        return charAtImpl(checkObjectToString(self), pos);
    }

    private static String charAtImpl(final String str, final int pos) {
        return pos < 0 || pos >= str.length() ? "" : String.valueOf(str.charAt(pos));
    }

    private static int getValidChar(final Object self, final int pos) {
        try {
            return ((CharSequence)self).charAt(pos);
        } catch (final IndexOutOfBoundsException e) {
            throw new ClassCastException(); //invalid char, out of bounds, force relink
        }
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos)
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static double charCodeAt(final Object self, final Object pos) {
        final String str = checkObjectToString(self);
        final int    idx = JSType.toInteger(pos);
        return idx < 0 || idx >= str.length() ? Double.NaN : str.charAt(idx);
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos) - specialized version for double position
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */
    @SpecializedFunction(linkLogic=CharCodeAtLinkLogic.class)
    public static int charCodeAt(final Object self, final double pos) {
        return charCodeAt(self, (int)pos); //toInt pos is ok
    }

    /**
     * ECMA 15.5.4.5 String.prototype.charCodeAt (pos) - specialized version for int position
     * @param self self reference
     * @param pos  position in string
     * @return number representing charcode at position
     */

    @SpecializedFunction(linkLogic=CharCodeAtLinkLogic.class)
    public static int charCodeAt(final Object self, final int pos) {
        return getValidChar(self, pos);
    }

    /**
     * ECMA 15.5.4.6 String.prototype.concat ( [ string1 [ , string2 [ , ... ] ] ] )
     * @param self self reference
     * @param args list of string to concatenate
     * @return concatenated string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object concat(final Object self, final Object... args) {
        CharSequence cs = checkObjectToString(self);
        if (args != null) {
            for (final Object obj : args) {
                cs = new ConsString(cs, JSType.toCharSequence(obj));
            }
        }
        return cs;
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int indexOf(final Object self, final Object search, final Object pos) {
        final String str = checkObjectToString(self);
        return str.indexOf(JSType.toString(search), JSType.toInteger(pos));
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for no position parameter
     * @param self   self reference
     * @param search string to search for
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search) {
        return indexOf(self, search, 0);
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for double position parameter
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search, final double pos) {
        return indexOf(self, search, (int) pos);
    }

    /**
     * ECMA 15.5.4.7 String.prototype.indexOf (searchString, position) specialized for int position parameter
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return position of first match or -1
     */
    @SpecializedFunction
    public static int indexOf(final Object self, final Object search, final int pos) {
        return checkObjectToString(self).indexOf(JSType.toString(search), pos);
    }

    /**
     * ECMA 15.5.4.8 String.prototype.lastIndexOf (searchString, position)
     * @param self   self reference
     * @param search string to search for
     * @param pos    position to start search
     * @return last position of match or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static int lastIndexOf(final Object self, final Object search, final Object pos) {

        final String str       = checkObjectToString(self);
        final String searchStr = JSType.toString(search);
        final int length       = str.length();

        int end;

        if (pos == UNDEFINED) {
            end = length;
        } else {
            final double numPos = JSType.toNumber(pos);
            end = Double.isNaN(numPos) ? length : (int)numPos;
            if (end < 0) {
                end = 0;
            } else if (end > length) {
                end = length;
            }
        }


        return str.lastIndexOf(searchStr, end);
    }

    /**
     * ECMA 15.5.4.9 String.prototype.localeCompare (that)
     * @param self self reference
     * @param that comparison object
     * @return result of locale sensitive comparison operation between {@code self} and {@code that}
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static double localeCompare(final Object self, final Object that) {

        final String   str      = checkObjectToString(self);
        final Collator collator = Collator.getInstance(Global.getEnv()._locale);

        collator.setStrength(Collator.IDENTICAL);
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        return collator.compare(str, JSType.toString(that));
    }

    /**
     * ECMA 15.5.4.10 String.prototype.match (regexp)
     * @param self   self reference
     * @param regexp regexp expression
     * @return array of regexp matches
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object match(final Object self, final Object regexp) {

        final Object delegated = delegate(self, regexp, NativeSymbol.match);
        if (delegated != NOT_DELEGATED) {
            return delegated;
        }

        final String str = checkObjectToString(self);

        NativeRegExp nativeRegExp;
        if (regexp == UNDEFINED) {
            nativeRegExp = new NativeRegExp("");
        } else {
            nativeRegExp = Global.toRegExp(regexp);
        }

        final Object matched = viaSymbol(nativeRegExp, NativeSymbol.match, str);
        if (matched != NOT_DELEGATED) {
            return matched;
        }

        if (!nativeRegExp.getGlobal()) {
            return nativeRegExp.exec(str);
        }

        nativeRegExp.setLastIndex(0);

        final List<Object> matches = new ArrayList<>();

        ScriptObject result;
        // We follow ECMAScript 6 spec here (checking for empty string instead of previous index)
        // as the ES5 specification is buggy and causes empty strings to be matched twice.
        while ((result = nativeRegExp.exec(str)) != null) {
            final String matchStr = JSType.toString(result.get(0));
            if (matchStr.isEmpty()) {
                nativeRegExp.setLastIndex(nativeRegExp.getLastIndex() + 1);
            }
            matches.add(matchStr);
        }

        if (matches.isEmpty()) {
            return null;
        }

        return new NativeArray(matches.toArray());
    }

    /**
     * ECMA 15.5.4.11 String.prototype.replace (searchValue, replaceValue)
     * @param self        self reference
     * @param string      item to replace
     * @param replacement item to replace it with
     * @return string after replacement
     * @throws Throwable if replacement fails
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object replace(final Object self, final Object string, final Object replacement) throws Throwable {

        final Object delegated = delegate(self, string, NativeSymbol.replace, replacement);
        if (delegated != NOT_DELEGATED) {
            return delegated;
        }

        final String str = checkObjectToString(self);

        final NativeRegExp nativeRegExp;
        if (string instanceof NativeRegExp) {
            nativeRegExp = (NativeRegExp) string;
        } else {
            nativeRegExp = NativeRegExp.flatRegExp(JSType.toString(string));
        }

        final Object replaced = viaSymbol(nativeRegExp, NativeSymbol.replace, str, replacement);
        if (replaced != NOT_DELEGATED) {
            return replaced;
        }

        if (Bootstrap.isCallable(replacement)) {
            return nativeRegExp.replace(str, "", replacement);
        }

        return nativeRegExp.replace(str, JSType.toString(replacement), null);
    }

    /**
     * ECMA 15.5.4.12 String.prototype.search (regexp)
     *
     * @param self    self reference
     * @param string  string to search for
     * @return offset where match occurred
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object search(final Object self, final Object string) {

        final Object delegated = delegate(self, string, NativeSymbol.search);
        if (delegated != NOT_DELEGATED) {
            return delegated;
        }

        final String       str          = checkObjectToString(self);
        final NativeRegExp nativeRegExp = Global.toRegExp(string == UNDEFINED ? "" : string);

        final Object searched = viaSymbol(nativeRegExp, NativeSymbol.search, str);
        if (searched != NOT_DELEGATED) {
            return searched;
        }

        return nativeRegExp.search(str);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end)
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String slice(final Object self, final Object start, final Object end) {

        final String str      = checkObjectToString(self);
        if (end == UNDEFINED) {
            return slice(str, JSType.toInteger(start));
        }
        return slice(str, JSType.toInteger(start), JSType.toInteger(end));
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for single int parameter
     *
     * @param self  self reference
     * @param start start position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final int start) {
        final String str = checkObjectToString(self);
        final int from = start < 0 ? Math.max(str.length() + start, 0) : Math.min(start, str.length());

        return str.substring(from);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for single double parameter
     *
     * @param self  self reference
     * @param start start position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final double start) {
        return slice(self, (int)start);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for two int parameters
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final int start, final int end) {

        final String str = checkObjectToString(self);
        final int len    = str.length();

        final int from = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        final int to   = end < 0   ? Math.max(len + end, 0)   : Math.min(end, len);

        return str.substring(Math.min(from, to), to);
    }

    /**
     * ECMA 15.5.4.13 String.prototype.slice (start, end) specialized for two double parameters
     *
     * @param self  self reference
     * @param start start position for slice
     * @param end   end position for slice
     * @return sliced out substring
     */
    @SpecializedFunction
    public static String slice(final Object self, final double start, final double end) {
        return slice(self, (int)start, (int)end);
    }

    /**
     * ECMA 15.5.4.14 String.prototype.split (separator, limit)
     *
     * @param self      self reference
     * @param separator separator for split
     * @param limit     limit for splits
     * @return array object in which splits have been placed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object split(final Object self, final Object separator, final Object limit) {
        final Object delegated = delegate(self, separator, NativeSymbol.split, limit);
        if (delegated != NOT_DELEGATED) {
            return delegated;
        }

        final String str = checkObjectToString(self);
        final long lim = limit == UNDEFINED ? JSType.MAX_UINT : JSType.toUint32(limit);

        if (separator == UNDEFINED) {
            return lim == 0 ? new NativeArray() : new NativeArray(new Object[]{str});
        }

        if (separator instanceof NativeRegExp regexpSeparator) {
            final Object splitted = viaSymbol(regexpSeparator, NativeSymbol.split, str, limit);
            return splitted != NOT_DELEGATED ? splitted : regexpSeparator.split(str, lim);
        }

        // when separator is a string, it is treated as a literal search string to be used for splitting.
        return splitString(str, JSType.toString(separator), lim);
    }

    /** Distinguishes "the argument had no such method" from a method that returned null. */
    private static final Object NOT_DELEGATED = new Object();

    /**
     * ES2015 21.1.3.11 step 5 and its three siblings: the matching itself is
     * done by the regular expression's own @@match, @@replace, @@search or
     * @@split, so a script that replaces one of those on RegExp.prototype
     * changes what these four do with an argument that is not a regexp.
     *
     * As with the delegation above, the lookup happens only once some script
     * has installed one of the four symbols somewhere: until then the only one
     * to be found is the built-in, and calling it arrives where the direct path
     * starts.
     */
    private static Object viaSymbol(final NativeRegExp regexp, final Symbol symbol, final Object... arguments) {
        if (!WellKnownSymbols.stringMethodsInstalled()
                || !(regexp.get(symbol) instanceof ScriptFunction method)) {
            return NOT_DELEGATED;
        }
        return ScriptRuntime.apply(method, regexp, arguments);
    }

    /**
     * ES2015 21.1.3.11, .14, .15 and .17: match, replace, search and split each
     * hand the work to the argument's own @@match, @@replace, @@search or
     * @@split if it has one, which is how a value other than a regular
     * expression gets to decide what they mean.
     *
     * The lookup is skipped entirely until some script has installed one of the
     * four symbols somewhere. Until then the only objects carrying them are
     * RegExp.prototype's own, and taking the delegated path for those would only
     * arrive back where the direct path starts - on methods hot enough that the
     * detour is worth avoiding.
     */
    private static Object delegate(final Object self, final Object argument, final Symbol symbol,
            final Object... rest) {
        if (!WellKnownSymbols.stringMethodsInstalled()
                || argument == UNDEFINED || argument == null
                || !(argument instanceof ScriptObject sobj)) {
            return NOT_DELEGATED;
        }
        // Only a script function: an embedder's JSObject could in principle carry
        // one of these symbols, but nothing here can call it with a receiver, and
        // the four are the only place it would matter.
        if (!(sobj.get(symbol) instanceof ScriptFunction method)) {
            return NOT_DELEGATED;
        }

        // RequireObjectCoercible on the receiver happens before the symbol is
        // looked for; what the method is handed is the receiver itself, and
        // making a string of it is the method's business rather than this one's
        Global.checkObjectCoercible(self);
        final Object[] arguments = new Object[rest.length + 1];
        arguments[0] = self;
        System.arraycopy(rest, 0, arguments, 1, rest.length);
        return ScriptRuntime.apply(method, argument, arguments);
    }

    private static ScriptObject splitString(final String str, final String separator, final long limit) {
        if (separator.isEmpty()) {
            final int length = (int) Math.min(str.length(), limit);
            final Object[] array = new Object[length];
            for (int i = 0; i < length; i++) {
                array[i] = String.valueOf(str.charAt(i));
            }
            return new NativeArray(array);
        }

        final List<String> elements = new LinkedList<>();
        final int strLength = str.length();
        final int sepLength = separator.length();
        int pos = 0;
        int n = 0;

        while (pos < strLength && n < limit) {
            final int found = str.indexOf(separator, pos);
            if (found == -1) {
                break;
            }
            elements.add(str.substring(pos, found));
            n++;
            pos = found + sepLength;
        }
        if (pos <= strLength && n < limit) {
            elements.add(str.substring(pos));
        }

        return new NativeArray(elements.toArray());
    }

    /**
     * ECMA B.2.3 String.prototype.substr (start, length)
     *
     * @param self   self reference
     * @param start  start position
     * @param length length of section
     * @return substring given start and length of section
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String substr(final Object self, final Object start, final Object length) {
        final String str       = JSType.toString(self);
        final int    strLength = str.length();

        int intStart = JSType.toInteger(start);
        if (intStart < 0) {
            intStart = Math.max(intStart + strLength, 0);
        }

        final int intLen = Math.min(Math.max(length == UNDEFINED ? Integer.MAX_VALUE : JSType.toInteger(length), 0), strLength - intStart);

        return intLen <= 0 ? "" : str.substring(intStart, intStart + intLen);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end)
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String substring(final Object self, final Object start, final Object end) {

        final String str = checkObjectToString(self);
        if (end == UNDEFINED) {
            return substring(str, JSType.toInteger(start));
        }
        return substring(str, JSType.toInteger(start), JSType.toInteger(end));
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for int start parameter
     *
     * @param self  self reference
     * @param start start position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final int start) {
        final String str = checkObjectToString(self);
        if (start < 0) {
            return str;
        } else if (start >= str.length()) {
            return "";
        } else {
            return str.substring(start);
        }
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for double start parameter
     *
     * @param self  self reference
     * @param start start position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final double start) {
        return substring(self, (int)start);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for int start and end parameters
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final int start, final int end) {
        final String str = checkObjectToString(self);
        final int len = str.length();
        final int validStart = start < 0 ? 0 : Math.min(start, len);
        final int validEnd   = end < 0 ? 0 : Math.min(end, len);

        if (validStart < validEnd) {
            return str.substring(validStart, validEnd);
        }
        return str.substring(validEnd, validStart);
    }

    /**
     * ECMA 15.5.4.15 String.prototype.substring (start, end) specialized for double start and end parameters
     *
     * @param self  self reference
     * @param start start position of substring
     * @param end   end position of substring
     * @return substring given start and end indexes
     */
    @SpecializedFunction
    public static String substring(final Object self, final double start, final double end) {
        return substring(self, (int)start, (int)end);
    }

    /**
     * ECMA 15.5.4.16 String.prototype.toLowerCase ( )
     * @param self self reference
     * @return string to lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLowerCase(final Object self) {
        return checkObjectToString(self).toLowerCase(Locale.ROOT);
    }

    /**
     * ECMA 15.5.4.17 String.prototype.toLocaleLowerCase ( )
     * @param self self reference
     * @return string to locale sensitive lower case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleLowerCase(final Object self) {
        return checkObjectToString(self).toLowerCase(Global.getEnv()._locale);
    }

    /**
     * ECMA 15.5.4.18 String.prototype.toUpperCase ( )
     * @param self self reference
     * @return string to upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toUpperCase(final Object self) {
        return checkObjectToString(self).toUpperCase(Locale.ROOT);
    }

    /**
     * ECMA 15.5.4.19 String.prototype.toLocaleUpperCase ( )
     * @param self self reference
     * @return string to locale sensitive upper case
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toLocaleUpperCase(final Object self) {
        return checkObjectToString(self).toUpperCase(Global.getEnv()._locale);
    }

    /**
     * ECMA 15.5.4.20 String.prototype.trim ( )
     * @param self self reference
     * @return string trimmed from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trim(final Object self) {
        final String str = checkObjectToString(self);
        int start = 0;
        int end   = str.length() - 1;

        while (start <= end && ScriptRuntime.isJSWhitespace(str.charAt(start))) {
            start++;
        }
        while (end > start && ScriptRuntime.isJSWhitespace(str.charAt(end))) {
            end--;
        }

        return str.substring(start, end + 1);
    }

    /**
     * Nashorn extension: String.prototype.trimLeft ( )
     * @param self self reference
     * @return string trimmed left from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trimLeft(final Object self) {

        final String str = checkObjectToString(self);
        int start = 0;
        final int end   = str.length() - 1;

        while (start <= end && ScriptRuntime.isJSWhitespace(str.charAt(start))) {
            start++;
        }

        return str.substring(start, end + 1);
    }

    /**
     * Nashorn extension: String.prototype.trimRight ( )
     * @param self self reference
     * @return string trimmed right from whitespace
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String trimRight(final Object self) {

        final String str = checkObjectToString(self);
        final int start = 0;
        int end   = str.length() - 1;

        while (end >= start && ScriptRuntime.isJSWhitespace(str.charAt(end))) {
            end--;
        }

        return str.substring(start, end + 1);
    }

    private static ScriptObject newObj(final CharSequence str) {
        return new NativeString(str);
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] )
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param args   arguments (a value)
     *
     * @return new NativeString, empty string if no args, extraneous args ignored
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        // ES2015 21.1.1.1 step 2: String(symbol) is how a program describes a
        // symbol on purpose, and is the one conversion of one that does not
        // throw. new String(symbol) still does.
        if (!newObj && args.length > 0 && args[0] instanceof Symbol symbol) {
            return symbol.toString();
        }
        final CharSequence str = args.length > 0 ? JSType.toCharSequence(args[0]) : "";
        return newObj ? newObj(str) : str.toString();
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with no args
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     *
     * @return new NativeString ("")
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean newObj, final Object self) {
        return newObj ? newObj("") : "";
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with one arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    argument
     *
     * @return new NativeString (arg)
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean newObj, final Object self, final Object arg) {
        if (!newObj && arg instanceof Symbol symbol) {
            return symbol.toString();
        }
        final CharSequence str = JSType.toCharSequence(arg);
        return newObj ? newObj(str) : str.toString();
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with exactly one {@code int} arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    the arg
     *
     * @return new NativeString containing the string representation of the arg
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean newObj, final Object self, final int arg) {
        final String str = Integer.toString(arg);
        return newObj ? newObj(str) : str;
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with exactly one {@code double} arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    the arg
     *
     * @return new NativeString containing the string representation of the arg
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean newObj, final Object self, final double arg) {
        final String str = JSType.toString(arg);
        return newObj ? newObj(str) : str;
    }

    /**
     * ECMA 15.5.2.1 new String ( [ value ] ) - special version with exactly one {@code boolean} arg
     *
     * Constructor
     *
     * @param newObj is this constructor invoked with the new operator
     * @param self   self reference
     * @param arg    the arg
     *
     * @return new NativeString containing the string representation of the arg
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean newObj, final Object self, final boolean arg) {
        final String str = Boolean.toString(arg);
        return newObj ? newObj(str) : str;
    }

    /**
     * ECMA 6 21.1.3.27 String.prototype [ @@iterator ]( )
     *
     * @param self self reference
     * @return a string iterator
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@iterator")
    public static Object getIterator(final Object self) {
        return new StringIterator(checkObjectToString(self), Global.instance());
    }

    /**
     * Lookup the appropriate method for an invoke dynamic call.
     *
     * @param request  the link request
     * @param receiver receiver of call
     * @return Link to be invoked at call site.
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final Object receiver) {
        return PrimitiveLookup.lookupPrimitive(request, NashornGuards.getStringGuard(),
                new NativeString((CharSequence)receiver), WRAPFILTER, PROTOFILTER);
    }

    @SuppressWarnings("unused")
    private static NativeString wrapFilter(final Object receiver) {
        return new NativeString((CharSequence)receiver);
    }

    @SuppressWarnings("unused")
    private static Object protoFilter(final Object object) {
        return Global.instance().getStringPrototype();
    }

    private static CharSequence getCharSequence(final Object self) {
        if (JSType.isString(self)) {
            return (CharSequence)self;
        } else if (self instanceof NativeString) {
            return ((NativeString)self).getValue();
        } else if (self != null && self == Global.instance().getStringPrototype()) {
            return "";
        } else {
            throw typeError("not.a.string", ScriptRuntime.safeToString(self));
        }
    }

    private static String getString(final Object self) {
        if (self instanceof String) {
            return (String)self;
        } else if (self instanceof ConsString) {
            return self.toString();
        } else if (self instanceof NativeString) {
            return ((NativeString)self).getStringValue();
        } else if (self != null && self == Global.instance().getStringPrototype()) {
            return "";
        } else {
            throw typeError("not.a.string", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * Combines ECMA 9.10 CheckObjectCoercible and ECMA 9.8 ToString with a shortcut for strings.
     *
     * @param self the object
     * @return the object as string
     */
    private static String checkObjectToString(final Object self) {
        if (self instanceof String) {
            return (String)self;
        } else if (self instanceof ConsString) {
            return self.toString();
        } else {
            Global.checkObjectCoercible(self);
            return JSType.toString(self);
        }
    }

    private boolean isValidStringIndex(final int key) {
        return key >= 0 && key < value.length();
    }

    private static MethodHandle findOwnMH(final String name, final MethodType type) {
        return MH.findStatic(MethodHandles.lookup(), NativeString.class, name, type);
    }

    @Override
    public LinkLogic getLinkLogic(final Class<? extends LinkLogic> clazz) {
        if (clazz == CharCodeAtLinkLogic.class) {
            return CharCodeAtLinkLogic.INSTANCE;
        }
        return null;
    }

    @Override
    public boolean hasPerInstanceAssumptions() {
        return false;
    }

    /**
     * This is linker logic charCodeAt - when we specialize further methods in NativeString
     * It may be expanded. It's link check makes sure that we are dealing with a char
     * sequence and that we are in range
     */
    private static final class CharCodeAtLinkLogic extends SpecializedFunction.LinkLogic {
        private static final CharCodeAtLinkLogic INSTANCE = new CharCodeAtLinkLogic();

        @Override
        public boolean canLink(final Object self, final CallSiteDescriptor desc, final LinkRequest request) {
            try {
                //check that it's a char sequence or throw cce
                final CharSequence cs = (CharSequence)self;
                //check that the index, representable as an int, is inside the array
                final int intIndex = JSType.toInteger(request.getArguments()[2]);
                return intIndex >= 0 && intIndex < cs.length(); //can link
            } catch (final ClassCastException | IndexOutOfBoundsException e) {
                //fallthru
            }
            return false;
        }

        /**
         * charCodeAt callsites can throw ClassCastException as a mechanism to have them
         * relinked - this enabled fast checks of the kind of ((IntArrayData)arrayData).push(x)
         * for an IntArrayData only push - if this fails, a CCE will be thrown and we will relink
         */
        @Override
        public Class<? extends Throwable> getRelinkException() {
            return ClassCastException.class;
        }
    }

    /**
     * ECMAScript 2015 21.1.3.3 String.prototype.codePointAt(pos)
     *
     * @param self self reference
     * @param pos  position in the string
     * @return the code point at pos, or undefined if pos is out of range
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object codePointAt(final Object self, final Object pos) {
        final String str = checkObjectToString(self);
        final int index = JSType.toInteger(pos);
        if (index < 0 || index >= str.length()) {
            return ScriptRuntime.UNDEFINED;
        }
        return str.codePointAt(index);
    }

    /**
     * ECMAScript 2015 21.1.3.6 String.prototype.endsWith(searchString, endPosition)
     *
     * @param self   self reference
     * @param search the string to look for
     * @param end    where to treat the string as ending, or undefined for its length
     * @return true if the string ends with the search string
     */
    // 21.1.3.6 declares one parameter fewer than this takes: the rest are optional,
    // and length counts only those a caller has to pass
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean endsWith(final Object self, final Object search, final Object end) {
        final String str = checkObjectToString(self);
        final String searchString = rejectRegExp(search, "endsWith");
        final int endIndex = end == ScriptRuntime.UNDEFINED
                ? str.length()
                : Math.min(Math.max(JSType.toInteger(end), 0), str.length());
        final int start = endIndex - searchString.length();
        return start >= 0 && str.startsWith(searchString, start);
    }

    /**
     * ECMAScript 2015 21.1.3.7 String.prototype.includes(searchString, position)
     *
     * @param self     self reference
     * @param search   the string to look for
     * @param position where to start looking
     * @return true if the string contains the search string
     */
    // 21.1.3.7 declares one parameter fewer than this takes: the rest are optional,
    // and length counts only those a caller has to pass
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean includes(final Object self, final Object search, final Object position) {
        final String str = checkObjectToString(self);
        final String searchString = rejectRegExp(search, "includes");
        final int start = Math.min(Math.max(JSType.toInteger(position), 0), str.length());
        return str.indexOf(searchString, start) != -1;
    }

    /**
     * ECMAScript 2015 21.1.3.18 String.prototype.startsWith(searchString, position)
     *
     * @param self     self reference
     * @param search   the string to look for
     * @param position where to start looking
     * @return true if the string starts with the search string at position
     */
    // 21.1.3.18 declares one parameter fewer than this takes: the rest are optional,
    // and length counts only those a caller has to pass
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static boolean startsWith(final Object self, final Object search, final Object position) {
        final String str = checkObjectToString(self);
        final String searchString = rejectRegExp(search, "startsWith");
        final int start = Math.min(Math.max(JSType.toInteger(position), 0), str.length());
        return str.startsWith(searchString, start);
    }

    /**
     * ECMAScript 2017 21.1.3.14 String.prototype.padStart(maxLength [, fillString])
     *
     * @param self       self reference
     * @param maxLength  the length to reach
     * @param fillString what to pad with, a space by default
     * @return the padded string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static String padStart(final Object self, final Object maxLength, final Object fillString) {
        return pad(self, maxLength, fillString, true);
    }

    /**
     * ECMAScript 2017 21.1.3.13 String.prototype.padEnd(maxLength [, fillString])
     *
     * @param self       self reference
     * @param maxLength  the length to reach
     * @param fillString what to pad with, a space by default
     * @return the padded string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static String padEnd(final Object self, final Object maxLength, final Object fillString) {
        return pad(self, maxLength, fillString, false);
    }

    /**
     * ES2017 21.1.3.15 StringPad, the body both padding methods share.
     *
     * The order matters and the suite checks it: the receiver is coerced first,
     * then the length, then the filler - so a filler whose toString throws does
     * so after the length has already been read.
     */
    private static String pad(final Object self, final Object maxLength, final Object fillString,
            final boolean atStart) {
        final String str = checkObjectToString(self);
        final long max = JSType.toLong(maxLength);
        if (max <= str.length()) {
            return str;
        }
        final String filler = fillString == UNDEFINED ? " " : JSType.toString(fillString);
        if (filler.isEmpty()) {
            return str;
        }

        final int padding = (int)Math.min(max - str.length(), Integer.MAX_VALUE - str.length());
        final StringBuilder sb = new StringBuilder(padding);
        while (sb.length() < padding) {
            sb.append(filler, 0, Math.min(filler.length(), padding - sb.length()));
        }
        return atStart ? sb + str : str + sb;
    }

    /**
     * ECMAScript 2015 21.1.3.13 String.prototype.repeat(count)
     *
     * @param self  self reference
     * @param count how many copies
     * @return the string repeated count times
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String repeat(final Object self, final Object count) {
        final String str = checkObjectToString(self);
        final double n = JSType.toNumber(count);
        if (n < 0 || Double.isInfinite(n)) {
            throw rangeError("invalid.repeat.count", ScriptRuntime.safeToString(count));
        }
        final int times = (int)n;
        if (times == 0 || str.isEmpty()) {
            return "";
        }
        if ((long)times * str.length() > JSType.MAX_UINT) {
            throw rangeError("invalid.repeat.count", ScriptRuntime.safeToString(count));
        }
        return str.repeat(times);
    }

    /**
     * ECMAScript 2015 21.1.3.12 String.prototype.normalize(form)
     *
     * @param self self reference
     * @param form NFC, NFD, NFKC or NFKD; NFC when undefined
     * @return the normalized string
     */
    // 21.1.3.12 declares one parameter fewer than this takes: the rest are optional,
    // and length counts only those a caller has to pass
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static String normalize(final Object self, final Object form) {
        final String str = checkObjectToString(self);
        final String name = form == ScriptRuntime.UNDEFINED ? "NFC" : JSType.toString(form);
        final java.text.Normalizer.Form normalizerForm;
        switch (name) {
            case "NFC"  -> normalizerForm = java.text.Normalizer.Form.NFC;
            case "NFD"  -> normalizerForm = java.text.Normalizer.Form.NFD;
            case "NFKC" -> normalizerForm = java.text.Normalizer.Form.NFKC;
            case "NFKD" -> normalizerForm = java.text.Normalizer.Form.NFKD;
            default -> throw rangeError("invalid.normalize.form", name);
        }
        return java.text.Normalizer.normalize(str, normalizerForm);
    }

    /**
     * ECMAScript 2015 21.1.2.2 String.fromCodePoint(...codePoints)
     *
     * @param self self reference
     * @param args the code points
     * @return a string built from them
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static String fromCodePoint(final Object self, final Object... args) {
        final StringBuilder sb = new StringBuilder(args.length);
        for (final Object arg : args) {
            final double number = JSType.toNumber(arg);
            final int codePoint = (int)number;
            if (codePoint != number || codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
                throw rangeError("invalid.code.point", ScriptRuntime.safeToString(arg));
            }
            sb.appendCodePoint(codePoint);
        }
        return sb.toString();
    }

    /**
     * ECMAScript 2015 21.1.2.4 String.raw(template, ...substitutions)
     *
     * @param self self reference
     * @param args the template object followed by the substitutions
     * @return the template's raw strings interleaved with the substitutions
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static String raw(final Object self, final Object... args) {
        final Object template = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object cooked = JSType.toScriptObject(Global.instance(), template);
        if (!(cooked instanceof ScriptObject templateObject)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(template));
        }
        final Object rawValue = templateObject.get("raw");
        final Object rawObject = JSType.toScriptObject(Global.instance(), rawValue);
        if (!(rawObject instanceof ScriptObject raws)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(rawValue));
        }

        final long length = JSType.toUint32(raws.get("length"));
        final StringBuilder sb = new StringBuilder();
        for (long i = 0; i < length; i++) {
            sb.append(JSType.toString(raws.get(i)));
            // the last raw string has no substitution after it
            if (i + 1 < length && i + 1 < args.length) {
                sb.append(JSType.toString(args[(int)i + 1]));
            }
        }
        return sb.toString();
    }

    /**
     * ES2015 21.1.3.6/7/18: these three reject a regular expression outright
     * rather than coercing it, so that a future change to RegExp.prototype
     * cannot silently change what they search for.
     */
    private static String rejectRegExp(final Object search, final String name) {
        if (search instanceof NativeRegExp) {
            throw typeError("cant.use.regexp", name);
        }
        return JSType.toString(search);
    }
}
