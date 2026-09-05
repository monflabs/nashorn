/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.SpecializedFunction;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.BitVector;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ParserException;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.regexp.RegExp;
import org.monflabs.nashorn.internal.runtime.regexp.RegExpFactory;
import org.monflabs.nashorn.internal.runtime.regexp.RegExpMatcher;
import org.monflabs.nashorn.internal.runtime.regexp.RegExpResult;

/**
 * ECMA 15.10 RegExp Objects.
 */
@ScriptClass("RegExp")
public final class NativeRegExp extends ScriptObject {
    /** ECMA 15.10.7.5 lastIndex property */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_CONFIGURABLE)
    public Object lastIndex;

    /** Compiled regexp */
    private RegExp regexp;

    // Reference to global object needed to support static RegExp properties
    private final Global globalObject;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * ES2015 21.2.4.2 get RegExp [ @@species ].
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

    private NativeRegExp(final Global global) {
        super(global.getRegExpPrototype(), $nasgenmap$);
        this.globalObject = global;
    }

    NativeRegExp(final String input, final String flagString, final Global global, final ScriptObject proto) {
        super(proto, $nasgenmap$);
        try {
            this.regexp = RegExpFactory.create(input, flagString);
        } catch (final ParserException e) {
            // translate it as SyntaxError object and throw it
            e.throwAsEcmaException();
            throw new AssertionError(); //guard against null warnings below
        }
        this.globalObject = global;
        this.setLastIndex(0);
    }

    NativeRegExp(final String input, final String flagString, final Global global) {
        this(input, flagString, global, global.getRegExpPrototype());
    }

    NativeRegExp(final String input, final String flagString) {
        this(input, flagString, Global.instance());
    }

    NativeRegExp(final String string, final Global global) {
        this(string, "", global);
    }

    NativeRegExp(final String string) {
        this(string, Global.instance());
    }

    NativeRegExp(final NativeRegExp regExp) {
        this(Global.instance());
        this.lastIndex  = regExp.getLastIndexObject();
        this.regexp      = regExp.getRegExp();
    }

    @Override
    public String getClassName() {
        return "RegExp";
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param args  arguments (optional: pattern and flags)
     * @return new NativeRegExp
     */
    @Constructor(arity = 2)
    public static Object constructor(final boolean isNew, final Object self, final Object... args) {
        return construct(isNew,
                args.length > 0 ? args[0] : UNDEFINED,
                args.length > 1 ? args[1] : UNDEFINED);
    }

    /**
     * ES2015 7.2.8 IsRegExp: an object counts as a regular expression if it says
     * so with {@code Symbol.match}, whatever it actually is, and only falls back
     * to being one when it stays silent.
     */
    private static boolean isRegExp(final Object value) {
        if (!(value instanceof ScriptObject sobj)) {
            return false;
        }
        final Object matcher = sobj.get(NativeSymbol.match);
        return matcher == UNDEFINED ? value instanceof NativeRegExp : JSType.toBoolean(matcher);
    }

    /**
     * ES2015 21.2.3.1 RegExp(pattern, flags).
     *
     * Three things changed here from ES5.1. Called as a function on a regular
     * expression whose constructor is this one, with no flags, it hands the same
     * object back rather than copying - but called with new it always copies.
     * A pattern that is not a regular expression but claims to be one, through
     * a truthy {@code Symbol.match}, is taken apart with its own "source" and
     * "flags". And a regular expression pattern may be given flags, which
     * replaces the ones it had; ES5.1 made that a TypeError.
     */
    private static Object construct(final boolean isNew, final Object pattern, final Object flags) {
        final boolean patternIsRegExp = isRegExp(pattern);

        if (!isNew && patternIsRegExp && flags == UNDEFINED
                && ((ScriptObject)pattern).get("constructor") == Global.instance().get("RegExp")) {
            return pattern;
        }

        final String source;
        final String flagString;
        if (pattern instanceof NativeRegExp re) {
            source = re.getRegExp().getSource();
            flagString = flags == UNDEFINED ? JSType.toString(flags(re)) : JSType.toString(flags);
        } else if (patternIsRegExp) {
            final ScriptObject sobj = (ScriptObject)pattern;
            source = JSType.toString(sobj.get("source"));
            flagString = flags == UNDEFINED ? JSType.toString(sobj.get("flags")) : JSType.toString(flags);
        } else {
            source = pattern == UNDEFINED ? "" : JSType.toString(pattern);
            flagString = flags == UNDEFINED ? "" : JSType.toString(flags);
        }
        return new NativeRegExp(source, flagString);
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, no args, empty regexp
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @return new NativeRegExp
     */
    @SpecializedFunction(isConstructor=true)
    public static NativeRegExp constructor(final boolean isNew, final Object self) {
        return new NativeRegExp("", "");
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, pattern, no flags
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param pattern pattern
     * @return new NativeRegExp
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean isNew, final Object self, final Object pattern) {
        return construct(isNew, pattern, UNDEFINED);
    }

    /**
     * ECMA 15.10.4
     *
     * Constructor - specialized version, pattern and flags
     *
     * @param isNew is the new operator used for instantiating this regexp
     * @param self  self reference
     * @param pattern pattern
     * @param flags  flags
     * @return new NativeRegExp
     */
    @SpecializedFunction(isConstructor=true)
    public static Object constructor(final boolean isNew, final Object self, final Object pattern, final Object flags) {
        return construct(isNew, pattern, flags);
    }

    /**
     * External constructor used in generated code, which explains the public access
     *
     * @param regexp regexp
     * @param flags  flags
     * @return new NativeRegExp
     */
    public static NativeRegExp newRegExp(final Object regexp, final Object flags) {
        String  patternString = "";
        String  flagString    = "";

        if (regexp != UNDEFINED) {
            if (regexp instanceof NativeRegExp source) {
                // ES2015 21.2.3.1 step 5: a RegExp pattern with flags is now
                // legal and means "the same source, these flags instead", where
                // ES5.1 made it a TypeError. Without flags it is still a copy.
                return flags == UNDEFINED
                        ? source
                        : new NativeRegExp(source.getRegExp().getSource(), JSType.toString(flags));
            }
            patternString = JSType.toString(regexp);
        }

        if (flags != UNDEFINED) {
            flagString = JSType.toString(flags);
        }

        return new NativeRegExp(patternString, flagString);
    }

    /**
     * Build a regexp that matches {@code string} as-is. All meta-characters will be escaped.
     *
     * @param string pattern string
     * @return flat regexp
     */
    static NativeRegExp flatRegExp(final String string) {
        // escape special characters
        StringBuilder sb = null;
        final int length = string.length();

        for (int i = 0; i < length; i++) {
            final char c = string.charAt(i);
            switch (c) {
                case '^':
                case '$':
                case '\\':
                case '.':
                case '*':
                case '+':
                case '?':
                case '(':
                case ')':
                case '[':
                case '{':
                case '|':
                    if (sb == null) {
                        sb = new StringBuilder(length * 2);
                        sb.append(string, 0, i);
                    }
                    sb.append('\\');
                    sb.append(c);
                    break;
                default:
                    if (sb != null) {
                        sb.append(c);
                    }
                    break;
            }
        }
        return new NativeRegExp(sb == null ? string : sb.toString(), "");
    }

    private String getFlagString() {
        final StringBuilder sb = new StringBuilder(3);

        if (regexp.isGlobal()) {
            sb.append('g');
        }
        if (regexp.isIgnoreCase()) {
            sb.append('i');
        }
        if (regexp.isMultiline()) {
            sb.append('m');
        }
        if (regexp.isDotAll()) {
            sb.append('s');
        }

        return sb.toString();
    }

    @Override
    public String safeToString() {
        return "[RegExp " + this + "]";
    }

    @Override
    public String toString() {
        return "/" + regexp.getSource() + "/" + getFlagString();
    }

    /**
     * Nashorn extension: RegExp.prototype.compile - everybody implements this!
     *
     * @param self    self reference
     * @param pattern pattern
     * @param flags   flags
     * @return new NativeRegExp
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static ScriptObject compile(final Object self, final Object pattern, final Object flags) {
        final NativeRegExp regExp = checkRegExp(self);
        final NativeRegExp compiled;

        if (pattern instanceof NativeRegExp source) {
            // B.2.5.1 step 1: this is not the constructor, and a pattern that is
            // already a regexp brings its own flags - being given a second set
            // is an error rather than an override
            if (flags != UNDEFINED) {
                throw typeError("regex.cant.supply.flags");
            }
            compiled = new NativeRegExp(source.getRegExp().getSource(), flagsOf(source.getRegExp()));
        } else {
            compiled = newRegExp(pattern, flags);
        }

        // the pattern is installed before lastIndex is reset, which is
        // observable: a lastIndex that was made unwritable throws, and the
        // pattern it throws over is the new one
        regExp.setRegExp(compiled.getRegExp());
        regExp.set("lastIndex", 0, CALLSITE_STRICT);

        // Some implementations return undefined. Some return 'self'. Since return
        // value is most likely be ignored, we can play safe and return 'self'.
        return regExp;
    }

    /**
     * The flags a compiled regexp was made with, read from it rather than from
     * the properties that describe it: B.2.5.1 takes [[OriginalFlags]], and a
     * subclass that overrides {@code global} does not get a say.
     */
    private static String flagsOf(final RegExp regexp) {
        final StringBuilder sb = new StringBuilder(5);
        if (regexp.isGlobal()) {
            sb.append('g');
        }
        if (regexp.isIgnoreCase()) {
            sb.append('i');
        }
        if (regexp.isMultiline()) {
            sb.append('m');
        }
        if (regexp.isUnicode()) {
            sb.append('u');
        }
        if (regexp.isSticky()) {
            sb.append('y');
        }
        return sb.toString();
    }

    /**
     * ECMA 15.10.6.2 RegExp.prototype.exec(string)
     *
     * @param self   self reference
     * @param string string to match against regexp
     * @return array containing the matches or {@code null} if no match
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static ScriptObject exec(final Object self, final Object string) {
        return checkRegExp(self).exec(JSType.toString(string));
    }

    /**
     * ECMA 15.10.6.3 RegExp.prototype.test(string)
     *
     * @param self   self reference
     * @param string string to test for matches against regexp
     * @return true if matches found, false otherwise
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean test(final Object self, final Object string) {
        return checkRegExp(self).test(JSType.toString(string));
    }

    /**
     * ES2015 21.2.5.6 RegExp.prototype [ @@match ] ( string ).
     *
     * String.prototype.match delegates here, which is what makes the behaviour
     * replaceable. Like its three siblings it is generic: it reads flags and
     * lastIndex as properties and goes through the object's own exec, so it
     * works on a subclass that overrides either.
     *
     * @param self   the regular expression, or anything shaped like one
     * @param string what to match against
     * @return the matches, or null if there are none
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@match", arity = 1)
    public static Object match(final Object self, final Object string) {
        final ScriptObject rx = matcherObject(self);
        final String str = JSType.toString(string);

        // the flags are read once, as a string, rather than as the individual
        // accessors: a subclass that overrides "flags" decides all of them
        final String flags = JSType.toString(rx.get("flags"));
        if (flags.indexOf('g') < 0) {
            return regExpExec(rx, str);
        }

        final boolean unicode = flags.indexOf('u') >= 0;
        rx.set("lastIndex", 0, CALLSITE_STRICT);

        final List<Object> matches = new ArrayList<>();
        while (true) {
            final ScriptObject result = regExpExec(rx, str);
            if (result == null) {
                return matches.isEmpty() ? null : new NativeArray(matches.toArray());
            }
            final String matched = JSType.toString(result.get(0));
            matches.add(matched);
            if (matched.isEmpty()) {
                rx.set("lastIndex", (double)advanceStringIndex(str, lastIndex(rx), unicode), CALLSITE_STRICT);
            }
        }
    }

    /**
     * ES2015 21.2.5.9 RegExp.prototype [ @@search ] ( string ).
     *
     * @param self   the regular expression
     * @param string what to search
     * @return the offset of the first match, or -1
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@search", arity = 1)
    public static Object search(final Object self, final Object string) {
        final ScriptObject rx = matcherObject(self);
        final String str = JSType.toString(string);

        // searching must not be observable through lastIndex, so it is put back
        final Object previous = rx.get("lastIndex");
        if (!ScriptRuntime.sameValue(previous, 0)) {
            rx.set("lastIndex", 0, CALLSITE_STRICT);
        }
        final ScriptObject result = regExpExec(rx, str);
        if (!ScriptRuntime.sameValue(rx.get("lastIndex"), previous)) {
            rx.set("lastIndex", previous, CALLSITE_STRICT);
        }
        return result == null ? -1 : result.get("index");
    }

    /**
     * ES2015 21.2.5.8 RegExp.prototype [ @@replace ] ( string, replaceValue ).
     *
     * @param self        the regular expression
     * @param string      what to search
     * @param replacement the replacement text, or a function producing it
     * @return the resulting string
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@replace", arity = 2)
    public static Object replace(final Object self, final Object string, final Object replacement) {
        final ScriptObject rx = matcherObject(self);
        final String str = JSType.toString(string);
        final boolean callable = Bootstrap.isCallable(replacement);
        final String replaceText = callable ? null : JSType.toString(replacement);

        final String flags = JSType.toString(rx.get("flags"));
        final boolean global = flags.indexOf('g') >= 0;
        final boolean unicode = global && flags.indexOf('u') >= 0;
        if (global) {
            rx.set("lastIndex", 0, CALLSITE_STRICT);
        }

        // every match is collected before any replacement is built, so that a
        // replacement function cannot disturb the walk
        final List<ScriptObject> results = new ArrayList<>();
        while (true) {
            final ScriptObject result = regExpExec(rx, str);
            if (result == null) {
                break;
            }
            results.add(result);
            if (!global) {
                break;
            }
            if (JSType.toString(result.get(0)).isEmpty()) {
                rx.set("lastIndex", (double)advanceStringIndex(str, lastIndex(rx), unicode), CALLSITE_STRICT);
            }
        }

        final StringBuilder accumulated = new StringBuilder();
        int nextSourcePosition = 0;
        for (final ScriptObject result : results) {
            final String matched = JSType.toString(result.get(0));
            final int captureCount = (int)Math.max(JSType.toUint32(result.getLength()) - 1, 0);
            final int position = Math.min(Math.max(JSType.toInteger(result.get("index")), 0), str.length());

            final Object[] captures = new Object[captureCount];
            for (int i = 0; i < captureCount; i++) {
                final Object capture = result.get(i + 1);
                captures[i] = capture == UNDEFINED ? UNDEFINED : JSType.toString(capture);
            }

            final String replaced;
            if (callable) {
                final Object[] arguments = new Object[captureCount + 3];
                arguments[0] = matched;
                System.arraycopy(captures, 0, arguments, 1, captureCount);
                arguments[captureCount + 1] = (double)position;
                arguments[captureCount + 2] = str;
                replaced = JSType.toString(ScriptRuntime.apply((ScriptFunction)replacement, UNDEFINED, arguments));
            } else {
                replaced = getSubstitution(matched, str, position, captures, replaceText);
            }

            if (position >= nextSourcePosition) {
                accumulated.append(str, nextSourcePosition, position).append(replaced);
                nextSourcePosition = position + matched.length();
            }
        }
        if (nextSourcePosition < str.length()) {
            accumulated.append(str, nextSourcePosition, str.length());
        }
        return accumulated.toString();
    }

    /**
     * ES2015 21.2.5.11 RegExp.prototype [ @@split ] ( string, limit ).
     *
     * The splitting is done by a second regular expression built from this one
     * with the sticky flag added, so that each attempt is anchored where the last
     * match ended - and it is built through the species constructor, so a
     * subclass splits with its own kind.
     *
     * @param self   the regular expression
     * @param string what to split
     * @param limit  how many pieces at most
     * @return the pieces
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@split", arity = 2)
    public static Object split(final Object self, final Object string, final Object limit) {
        final ScriptObject rx = matcherObject(self);
        final String str = JSType.toString(string);

        final String flags = JSType.toString(rx.get("flags"));
        final boolean unicode = flags.indexOf('u') >= 0;
        final String stickyFlags = flags.indexOf('y') >= 0 ? flags : flags + "y";
        final ScriptObject splitter = construct(speciesConstructor(rx), rx, stickyFlags);

        final List<Object> pieces = new ArrayList<>();
        final long lim = limit == UNDEFINED ? JSType.MAX_UINT : JSType.toUint32(limit);
        if (lim == 0) {
            return new NativeArray();
        }

        final int size = str.length();
        if (size == 0) {
            return regExpExec(splitter, str) != null ? new NativeArray() : new NativeArray(new Object[] { str });
        }

        int p = 0;
        long q = 0;
        while (q < size) {
            splitter.set("lastIndex", (double)q, CALLSITE_STRICT);
            final ScriptObject result = regExpExec(splitter, str);
            if (result == null) {
                q = advanceStringIndex(str, q, unicode);
                continue;
            }
            final int e = (int)Math.min(toLength(splitter.get("lastIndex")), size);
            if (e == p) {
                q = advanceStringIndex(str, q, unicode);
                continue;
            }
            pieces.add(str.substring(p, (int)q));
            if (pieces.size() == lim) {
                return new NativeArray(pieces.toArray());
            }
            p = e;
            final int captureCount = (int)Math.max(JSType.toUint32(result.getLength()) - 1, 0);
            for (int i = 1; i <= captureCount; i++) {
                pieces.add(result.get(i));
                if (pieces.size() == lim) {
                    return new NativeArray(pieces.toArray());
                }
            }
            q = p;
        }
        pieces.add(str.substring(p, size));
        return new NativeArray(pieces.toArray());
    }

    /**
     * The receiver of one of the four symbol methods, which ES2015 21.2.5.6 and
     * its siblings require to be an object and nothing more - not a regular
     * expression, which is what lets a plain object with an exec method stand in
     * for one.
     */
    private static ScriptObject matcherObject(final Object self) {
        if (self instanceof ScriptObject sobj) {
            return sobj;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(self));
    }

    /**
     * ES2015 21.2.5.2.1 RegExpExec: the object's own exec if it has a callable
     * one, and the built-in otherwise.
     */
    private static ScriptObject regExpExec(final ScriptObject rx, final String str) {
        final Object exec = rx.get("exec");
        if (Bootstrap.isCallable(exec) && exec instanceof ScriptFunction function) {
            final Object result = ScriptRuntime.apply(function, rx, str);
            // step 5: what exec answered is an object or it is null, and
            // nothing else - undefined is not a way of saying it matched
            // nothing, and neither is a number
            if (result == null) {
                return null;
            }
            if (result instanceof ScriptObject sobj) {
                return sobj;
            }
            throw typeError("not.an.object", ScriptRuntime.safeToString(result));
        }
        return checkRegExp(rx).exec(str);
    }

    /** ES2015 21.2.5.2.3 AdvanceStringIndex, which steps over a whole code point in unicode mode. */
    private static long advanceStringIndex(final String str, final long index, final boolean unicode) {
        if (!unicode || index + 1 >= str.length()) {
            return index + 1;
        }
        final char first = str.charAt((int)index);
        if (first < 0xD800 || first > 0xDBFF) {
            return index + 1;
        }
        final char second = str.charAt((int)index + 1);
        return second < 0xDC00 || second > 0xDFFF ? index + 1 : index + 2;
    }

    private static long lastIndex(final ScriptObject rx) {
        // 21.2.5.8 reads it with ToLength, which clamps at 2^53-1 where an
        // unsigned wrap would answer with something else entirely
        return toLength(rx.get("lastIndex"));
    }

    /** ES2015 7.1.15 ToLength. */
    private static long toLength(final Object value) {
        final double number = JSType.toNumber(value);
        return Double.isNaN(number) || number < 0 ? 0
                : (long)Math.min(number, 9007199254740991d);
    }

    /** The constructor a derived operation should build with (ES2015 7.3.20). */
    private static Object speciesConstructor(final ScriptObject rx) {
        final Object constructor = rx.get("constructor");
        if (constructor == UNDEFINED) {
            return Global.instance().get("RegExp");
        }
        if (!(constructor instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(constructor));
        }
        final Object species = sobj.get(NativeSymbol.species);
        return species == UNDEFINED || species == null ? Global.instance().get("RegExp") : species;
    }

    private static ScriptObject construct(final Object constructor, final Object pattern, final String flags) {
        if (!(constructor instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        final Object splitter = ScriptRuntime.construct(function, pattern, flags);
        if (splitter instanceof ScriptObject sobj) {
            return sobj;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(splitter));
    }

    /**
     * ES2015 21.1.3.14.1 GetSubstitution - what the dollar sequences in a
     * replacement string stand for.
     */
    private static String getSubstitution(final String matched, final String str, final int position,
            final Object[] captures, final String replacement) {
        final StringBuilder sb = new StringBuilder();
        final int tail = position + matched.length();

        for (int i = 0; i < replacement.length(); i++) {
            final char c = replacement.charAt(i);
            if (c != '$' || i + 1 == replacement.length()) {
                sb.append(c);
                continue;
            }
            final char next = replacement.charAt(i + 1);
            switch (next) {
            case '$' -> {
                sb.append('$');
                i++;
            }
            case '&' -> {
                sb.append(matched);
                i++;
            }
            case '`' -> {
                sb.append(str, 0, position);
                i++;
            }
            case '\'' -> {
                sb.append(str, Math.min(tail, str.length()), str.length());
                i++;
            }
            default -> {
                // $n and $nn, taking two digits when they name a group that exists
                final int one = Character.digit(next, 10);
                if (one < 0) {
                    sb.append(c);
                    break;
                }
                int group = one;
                int consumed = 1;
                if (i + 2 < replacement.length()) {
                    final int two = Character.digit(replacement.charAt(i + 2), 10);
                    if (two >= 0 && one * 10 + two <= captures.length && one * 10 + two > 0) {
                        group = one * 10 + two;
                        consumed = 2;
                    }
                }
                if (group == 0 || group > captures.length) {
                    sb.append(c);
                    break;
                }
                final Object capture = captures[group - 1];
                if (capture != UNDEFINED) {
                    sb.append(JSType.toString(capture));
                }
                i += consumed;
            }
            }
        }
        return sb.toString();
    }

    /**
     * ECMAScript 2015 21.2.5.14 RegExp.prototype.toString()
     *
     * It reads source and flags from whatever it was called on rather than
     * from a pattern of its own, so a subclass that answers either of them
     * differently is honoured - and the prototype, which answers "(?:)" and
     * "", reads as an expression that matches nothing.
     *
     * @param self self reference
     * @return string version of regexp
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        if (!(self instanceof ScriptObject sobj)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(self));
        }
        return "/" + JSType.toString(sobj.get("source")) + "/" + JSType.toString(sobj.get("flags"));
    }

    /**
     * ECMA 15.10.7.1 source
     *
     * @param self self reference
     * @return the input string for the regexp
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object source(final Object self) {
        // ES2015 21.2.5.10: the prototype is an ordinary object now, and describes
        // the pattern that matches nothing rather than pretending to be one
        return isRegExpPrototype(self) ? "(?:)" : escapePattern(checkRegExp(self).getRegExp().getSource());
    }

    /**
     * EscapeRegExpPattern, ES2015 21.2.5.10.
     *
     * The source is what stands between the slashes of a literal, so a slash
     * of its own would end it and a line terminator could not appear in it at
     * all. Both are written as escapes, which leaves a string that can be put
     * back between slashes and read as the same pattern.
     */
    private static String escapePattern(final String source) {
        StringBuilder sb = null;
        for (int i = 0; i < source.length(); i++) {
            final char ch = source.charAt(i);
            final String escape;
            switch (ch) {
                case '/':  escape = "\\/";      break;
                case '\n': escape = "\\n";      break;
                case '\r': escape = "\\r";      break;
                case '\u2028': escape = "\\u2028"; break;
                case '\u2029': escape = "\\u2029"; break;
                case '\\':
                    // an escape stands for whatever follows it, including a
                    // slash that has already been written as one
                    if (sb != null && i + 1 < source.length()) {
                        sb.append(ch).append(source.charAt(i + 1));
                    }
                    i++;
                    continue;
                default:   escape = null;      break;
            }
            if (escape == null) {
                if (sb != null) {
                    sb.append(ch);
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder(source.length() + 8).append(source, 0, i);
                }
                sb.append(escape);
            }
        }
        return sb == null ? source : sb.toString();
    }

    /**
     * Whether this is %RegExpPrototype%, which ES2015 21.2.5 stopped making a
     * regular expression: every flag accessor answers undefined for it.
     */
    private static boolean isRegExpPrototype(final Object self) {
        return self == Global.instance().getRegExpPrototype();
    }

    /**
     * ECMAScript 2015 21.2.5.3 flags, the regexp's flags as a string.
     *
     * The order is fixed by the spec - g, i, m, u, y - not the order they were
     * written in.
     *
     * @param self self reference
     * @return the flags
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object flags(final Object self) {
        // ES2015 21.2.5.3 reads the five flags as properties of the receiver, in
        // that order, so a subclass that overrides one of them is honoured and an
        // object that is not a regular expression at all still gets an answer
        final ScriptObject rx = matcherObject(self);
        final StringBuilder sb = new StringBuilder(5);
        if (JSType.toBoolean(rx.get("global"))) {
            sb.append('g');
        }
        if (JSType.toBoolean(rx.get("ignoreCase"))) {
            sb.append('i');
        }
        if (JSType.toBoolean(rx.get("multiline"))) {
            sb.append('m');
        }
        if (JSType.toBoolean(rx.get("dotAll"))) {
            sb.append('s');
        }
        if (JSType.toBoolean(rx.get("unicode"))) {
            sb.append('u');
        }
        if (JSType.toBoolean(rx.get("sticky"))) {
            sb.append('y');
        }
        return sb.toString();
    }

    /**
     * ECMAScript 2015 21.2.5.12 sticky
     *
     * @param self self reference
     * @return true if this regexp only matches at lastIndex
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object sticky(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isSticky();
    }

    /**
     * ECMAScript 2015 21.2.5.15 unicode
     *
     * @param self self reference
     * @return true if this regexp is in unicode mode
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object unicode(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isUnicode();
    }

    /**
     * ECMA 15.10.7.2 global
     *
     * @param self self reference
     * @return true if this regexp is flagged global, false otherwise
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object global(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isGlobal();
    }

    /**
     * ECMA 15.10.7.3 ignoreCase
     *
     * @param self self reference
     * @return true if this regexp if flagged to ignore case, false otherwise
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object ignoreCase(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isIgnoreCase();
    }

    /**
     * ECMA 15.10.7.4 multiline
     *
     * @param self self reference
     * @return true if this regexp is flagged to be multiline, false otherwise
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object multiline(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isMultiline();
    }

    /**
     * ECMAScript 2018 21.2.5.3 dotAll
     *
     * @param self self reference
     * @return true if this regexp has the s flag, so that {@code .} matches line terminators
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object dotAll(final Object self) {
        return isRegExpPrototype(self) ? UNDEFINED : checkRegExp(self).getRegExp().isDotAll();
    }

    /**
     * Getter for non-standard RegExp.input property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "input")
    public static Object getLastInput(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput();
    }

    /**
     * Getter for non-standard RegExp.multiline property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "multiline")
    public static Object getLastMultiline(final Object self) {
        return false; // doesn't ever seem to become true and isn't documented anyhwere
    }

    /**
     * Getter for non-standard RegExp.lastMatch property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "lastMatch")
    public static Object getLastMatch(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(0);
    }

    /**
     * Getter for non-standard RegExp.lastParen property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "lastParen")
    public static Object getLastParen(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getLastParen();
    }

    /**
     * Getter for non-standard RegExp.leftContext property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "leftContext")
    public static Object getLeftContext(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput().substring(0, match.getIndex());
    }

    /**
     * Getter for non-standard RegExp.rightContext property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "rightContext")
    public static Object getRightContext(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getInput().substring(match.getIndex() + match.length());
    }

    /**
     * Getter for non-standard RegExp.$1 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$1")
    public static Object getGroup1(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(1);
    }

    /**
     * Getter for non-standard RegExp.$2 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$2")
    public static Object getGroup2(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(2);
    }

    /**
     * Getter for non-standard RegExp.$3 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$3")
    public static Object getGroup3(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(3);
    }

    /**
     * Getter for non-standard RegExp.$4 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$4")
    public static Object getGroup4(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(4);
    }

    /**
     * Getter for non-standard RegExp.$5 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$5")
    public static Object getGroup5(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(5);
    }

    /**
     * Getter for non-standard RegExp.$6 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$6")
    public static Object getGroup6(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(6);
    }

    /**
     * Getter for non-standard RegExp.$7 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$7")
    public static Object getGroup7(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(7);
    }

    /**
     * Getter for non-standard RegExp.$8 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$8")
    public static Object getGroup8(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(8);
    }

    /**
     * Getter for non-standard RegExp.$9 property.
     * @param self self object
     * @return last regexp input
     */
    @Getter(where = Where.CONSTRUCTOR, attributes = Attribute.CONSTANT | Attribute.IS_ACCESSOR, name = "$9")
    public static Object getGroup9(final Object self) {
        final RegExpResult match = Global.instance().getLastRegExpResult();
        return match == null ? "" : match.getGroup(9);
    }

    /**
     * ES2015 21.2.5.2.2 writes lastIndex with Set(R, "lastIndex", n, true), so a
     * lastIndex that has been redefined non-writable makes matching a TypeError.
     * The ordinary case still writes the field directly - going through the
     * property would put a lookup and a handle invocation on every match.
     */
    private void writeLastIndex(final int value) {
        final org.monflabs.nashorn.internal.runtime.Property property = getMap().findProperty("lastIndex");
        if (property != null && property.isWritable()) {
            setLastIndex(value);
        } else {
            set("lastIndex", value, CALLSITE_STRICT);
        }
    }

    private RegExpResult execInner(final String string) {
        // ES2015 21.2.5.2.2: a sticky regexp tracks lastIndex the way a global
        // one does, and both reset it on failure.
        final boolean isSticky = regexp.isSticky();
        final boolean tracksLastIndex = regexp.isGlobal() || isSticky;
        // ES2015 21.2.5.2.2 step 4 reads lastIndex whatever the flags are, and
        // only then decides to ignore it - so a lastIndex with a valueOf sees it
        // called even for a plain regexp.
        final int lastIndex = getLastIndex();
        final int start = tracksLastIndex ? lastIndex : 0;

        if (start < 0 || start > string.length()) {
            if (tracksLastIndex) {
                writeLastIndex(0);
            }
            return null;
        }

        final RegExpMatcher matcher = regexp.match(string);
        if (matcher == null || !matcher.search(start)) {
            if (tracksLastIndex) {
                writeLastIndex(0);
            }
            return null;
        }

        if (isSticky && matcher.start() != start) {
            // sticky means anchored at lastIndex, not "found somewhere after it"
            writeLastIndex(0);
            return null;
        }

        if (tracksLastIndex) {
            writeLastIndex(matcher.end());
        }

        final RegExpResult match = new RegExpResult(string, matcher.start(), groups(matcher));
        globalObject.setLastRegExpResult(match);
        return match;
    }

    /**
     * Convert java.util.regex.Matcher groups to JavaScript groups.
     * That is, replace null and groups that didn't match with undefined.
     */
    private Object[] groups(final RegExpMatcher matcher) {
        final int groupCount = matcher.groupCount();
        final Object[] groups = new Object[groupCount + 1];
        final BitVector groupsInNegativeLookahead  = regexp.getGroupsInNegativeLookahead();

        for (int i = 0, lastGroupStart = matcher.start(); i <= groupCount; i++) {
            final int groupStart = matcher.start(i);
            if (lastGroupStart > groupStart
                    || groupsInNegativeLookahead != null && groupsInNegativeLookahead.isSet(i)) {
                // (1) ECMA 15.10.2.5 NOTE 3: need to clear Atom's captures each time Atom is repeated.
                // (2) ECMA 15.10.2.8 NOTE 3: Backreferences to captures in (?!Disjunction) from elsewhere
                // in the pattern always return undefined because the negative lookahead must fail.
                groups[i] = UNDEFINED;
                continue;
            }
            final String group = matcher.group(i);
            groups[i] = group == null ? UNDEFINED : group;
            lastGroupStart = groupStart;
        }
        return groups;
    }

    /**
     * Executes a search for a match within a string based on a regular
     * expression. It returns an array of information or null if no match is
     * found.
     *
     * @param string String to match.
     * @return NativeArray of matches, string or null.
     */
    public NativeRegExpExecResult exec(final String string) {
        final RegExpResult match = execInner(string);

        if (match == null) {
            return null;
        }

        return new NativeRegExpExecResult(match, globalObject);
    }

    /**
     * Executes a search for a match within a string based on a regular
     * expression.
     *
     * @param string String to match.
     * @return True if a match is found.
     */
    public boolean test(final String string) {
        return execInner(string) != null;
    }

    /**
     * Searches and replaces the regular expression portion (match) with the
     * replaced text instead. For the "replacement text" parameter, you can use
     * the keywords $1 to $2 to replace the original text with values from
     * sub-patterns defined within the main pattern.
     *
     * @param string String to match.
     * @param replacement Replacement string.
     * @return String with substitutions.
     */
    String replace(final String string, final String replacement, final Object function) throws Throwable {
        final RegExpMatcher matcher = regexp.match(string);

        if (matcher == null) {
            return string;
        }

        if (!regexp.isGlobal()) {
            if (!matcher.search(0)) {
                return string;
            }

            final StringBuilder sb = new StringBuilder();
            sb.append(string, 0, matcher.start());

            if (function != null) {
                final Object self = Bootstrap.isStrictCallable(function) ? UNDEFINED : Global.instance();
                sb.append(callReplaceValue(getReplaceValueInvoker(), function, self, matcher, string));
            } else {
                appendReplacement(matcher, string, replacement, sb);
            }
            sb.append(string, matcher.end(), string.length());
            return sb.toString();
        }

        setLastIndex(0);

        if (!matcher.search(0)) {
            return string;
        }

        int thisIndex = 0;
        int previousLastIndex;
        final StringBuilder sb = new StringBuilder();

        final MethodHandle invoker = function == null ? null : getReplaceValueInvoker();
        final Object self = function == null || Bootstrap.isStrictCallable(function) ? UNDEFINED : Global.instance();

        do {
            sb.append(string, thisIndex, matcher.start());
            if (function != null) {
                sb.append(callReplaceValue(invoker, function, self, matcher, string));
            } else {
                appendReplacement(matcher, string, replacement, sb);
            }

            thisIndex = matcher.end();

            // ECMA6 21.2.5.6 step 8.g.iv.5: If matchStr is empty advance index by one
            if (matcher.start() == matcher.end()) {
                setLastIndex(thisIndex + 1);
                previousLastIndex = thisIndex + 1;
            } else {
                previousLastIndex = thisIndex;
            }
        } while (previousLastIndex <= string.length() && matcher.search(previousLastIndex));

        sb.append(string, thisIndex, string.length());

        return sb.toString();
    }

    private void appendReplacement(final RegExpMatcher matcher, final String text, final String replacement, final StringBuilder sb) {
        /*
         * Process substitution patterns:
         *
         * $$ -> $
         * $& -> the matched substring
         * $` -> the portion of string that precedes matched substring
         * $' -> the portion of string that follows the matched substring
         * $n -> the nth capture, where n is [1-9] and $n is NOT followed by a decimal digit
         * $nn -> the nnth capture, where nn is a two digit decimal number [01-99].
         */

        int cursor = 0;
        Object[] groups = null;

        while (cursor < replacement.length()) {
            char nextChar = replacement.charAt(cursor);
            if (nextChar == '$') {
                // Skip past $
                cursor++;
                if (cursor == replacement.length()) {
                    // nothing after "$"
                    sb.append('$');
                    break;
                }

                nextChar = replacement.charAt(cursor);
                final int firstDigit = nextChar - '0';

                if (firstDigit >= 0 && firstDigit <= 9 && firstDigit <= matcher.groupCount()) {
                    // $0 is not supported, but $01 is. implementation-defined: if n>m, ignore second digit.
                    int refNum = firstDigit;
                    cursor++;
                    if (cursor < replacement.length() && firstDigit < matcher.groupCount()) {
                        final int secondDigit = replacement.charAt(cursor) - '0';
                        if (secondDigit >= 0 && secondDigit <= 9) {
                            final int newRefNum = firstDigit * 10 + secondDigit;
                            if (newRefNum <= matcher.groupCount() && newRefNum > 0) {
                                // $nn ($01-$99)
                                refNum = newRefNum;
                                cursor++;
                            }
                        }
                    }
                    if (refNum > 0) {
                        if (groups == null) {
                            groups = groups(matcher);
                        }
                        // Append group if matched.
                        if (groups[refNum] != UNDEFINED) {
                            sb.append((String) groups[refNum]);
                        }
                    } else { // $0. ignore.
                        assert refNum == 0;
                        sb.append("$0");
                    }
                } else if (nextChar == '$') {
                    sb.append('$');
                    cursor++;
                } else if (nextChar == '&') {
                    sb.append(matcher.group());
                    cursor++;
                } else if (nextChar == '`') {
                    sb.append(text, 0, matcher.start());
                    cursor++;
                } else if (nextChar == '\'') {
                    sb.append(text, matcher.end(), text.length());
                    cursor++;
                } else {
                    // unknown substitution or $n with n>m. skip.
                    sb.append('$');
                }
            } else {
                sb.append(nextChar);
                cursor++;
            }
        }
    }

    private static final Object REPLACE_VALUE = new Object();

    private static MethodHandle getReplaceValueInvoker() {
        return Global.instance().getDynamicInvoker(REPLACE_VALUE,
            () -> Bootstrap.createDynamicCallInvoker(String.class, Object.class, Object.class, Object[].class)
        );
    }

    private String callReplaceValue(final MethodHandle invoker, final Object function, final Object self, final RegExpMatcher matcher, final String string) throws Throwable {
        final Object[] groups = groups(matcher);
        final Object[] args   = Arrays.copyOf(groups, groups.length + 2);

        args[groups.length]     = matcher.start();
        args[groups.length + 1] = string;

        return (String)invoker.invokeExact(function, self, args);
    }


    /**
     * Tests for a match in a string. It returns the index of the match, or -1
     * if not found.
     *
     * @param string String to match.
     * @return Index of match.
     */
    int search(final String string) {
        final RegExpResult match = execInner(string);

        if (match == null) {
            return -1;
        }

        return match.getIndex();
    }

    /**
     * Fast lastIndex getter
     * @return last index property as int
     */
    public int getLastIndex() {
        // ES2015 21.2.5.2.2 step 4 is a ToLength: an index before the start of
        // the string is the start of it, not a failure to match, and one past
        // what an int can hold is past the end of any string
        final double index = JSType.toNumber(lastIndex);
        return Double.isNaN(index) || index < 0 ? 0 : (int)Math.min(index, Integer.MAX_VALUE);
    }

    /**
     * Fast lastIndex getter
     * @return last index property as boxed integer
     */
    public Object getLastIndexObject() {
        return lastIndex;
    }

    /**
     * Fast lastIndex setter
     * @param lastIndex lastIndex
     */
    public void setLastIndex(final int lastIndex) {
        this.lastIndex = JSType.toObject(lastIndex);
    }

    private static NativeRegExp checkRegExp(final Object self) {
        if (self instanceof NativeRegExp) {
            return (NativeRegExp)self;
        }
        // ES2015 21.2.5: RegExp.prototype is an ordinary object and matches
        // nothing, where ES5.1 made it a RegExp of its own - so a method
        // called on it has no pattern to work from and says so
        throw typeError("not.a.regexp", ScriptRuntime.safeToString(self));
    }

    boolean getGlobal() {
        return regexp.isGlobal();
    }

    private RegExp getRegExp() {
        return regexp;
    }

    private void setRegExp(final RegExp regexp) {
        this.regexp = regexp;
    }

}
