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

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.openjdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static org.openjdk.nashorn.internal.runtime.Source.sourceFor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import jdk.dynalink.linker.support.Lookup;
import org.openjdk.nashorn.api.scripting.JSObject;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.parser.Parser;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.ParserException;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptEnvironment;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ECMA 15.3 Function Objects
 *
 * Note: instances of this class are never created. This class is not even a
 * subclass of ScriptObject. But, we use this class to generate prototype and
 * constructor for "Function".
 */
@ScriptClass("Function")
public final class NativeFunction {

    /** apply arg converter handle */
    public static final MethodHandle TO_APPLY_ARGS = Lookup.findOwnStatic(MethodHandles.lookup(), "toApplyArgs", Object[].class, Object.class);

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    // do *not* create me!
    private NativeFunction() {
        throw new UnsupportedOperationException();
    }

    /**
     * ECMA 15.3.4.2 Function.prototype.toString ( )
     *
     * @param self self reference
     * @return string representation of Function
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        return sourceOf(self);
    }

    /**
     * ES2015 19.2.3.6 Function.prototype [ @@hasInstance ] ( V ).
     *
     * The default of what {@code instanceof} does, reachable as a method so that
     * a class can call it after overriding the symbol - and so that overriding
     * it on one function does not lose the ordinary meaning for the rest.
     *
     * @param self the constructor
     * @param v    the value being tested
     * @return whether v was built by self
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE,
            name = "@@hasInstance", arity = 1)
    public static boolean hasInstance(final Object self, final Object v) {
        return self instanceof ScriptFunction function
                && v instanceof ScriptObject instance
                && function.isInstance(instance);
    }

    /**
     * ECMA 15.3.4.3 Function.prototype.apply (thisArg, argArray)
     *
     * @param self   self reference
     * @param thiz   {@code this} arg for apply
     * @param array  array of argument for apply
     * @return result of apply
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object apply(final Object self, final Object thiz, final Object array) {
        checkCallable(self);
        final Object[] args = toApplyArgs(array);

        if (self instanceof ScriptFunction) {
            return ScriptRuntime.apply((ScriptFunction)self, thiz, args);
        } else if (self instanceof ScriptObjectMirror) {
            return ((JSObject)self).call(thiz, args);
        } else if (self instanceof JSObject) {
            final Global global = Global.instance();
            final Object result = ((JSObject) self).call(ScriptObjectMirror.wrap(thiz, global),
                    ScriptObjectMirror.wrapArray(args, global));
            return ScriptObjectMirror.unwrap(result, global);
        }
        // a callable proxy, which is a ScriptObject and not a ScriptFunction
        return ScriptRuntime.call(self, thiz, args);
    }

    /**
     * Given an array-like object, converts it into a Java object array suitable for invocation of ScriptRuntime.apply
     * or for direct invocation of the applied function.
     * @param array the array-like object. Can be null in which case a zero-length array is created.
     * @return the Java array
     */
    public static Object[] toApplyArgs(final Object array) {
        if (array instanceof NativeArguments) {
            return ((NativeArguments)array).getArray().asObjectArray();
        } else if (array instanceof ScriptObject) {
            // look for array-like object
            final ScriptObject sobj = (ScriptObject)array;
            final int n = lengthToInt(sobj.getLength());

            final Object[] args = new Object[n];
            for (int i = 0; i < args.length; i++) {
                args[i] = sobj.get(i);
            }
            return args;
        } else if (array instanceof Object[]) {
            return (Object[])array;
        } else if (array instanceof List) {
            final List<?> list = (List<?>)array;
            return list.toArray(new Object[0]);
        } else if (array == null || array == UNDEFINED) {
            return ScriptRuntime.EMPTY_ARRAY;
        } else if (array instanceof JSObject) {
            // look for array-like JSObject object
            final JSObject jsObj = (JSObject)array;
            final Object   len  = jsObj.hasMember("length")? jsObj.getMember("length") : Integer.valueOf(0);
            final int n = lengthToInt(len);

            final Object[] args = new Object[n];
            for (int i = 0; i < args.length; i++) {
                args[i] = jsObj.hasSlot(i)? jsObj.getSlot(i) : UNDEFINED;
            }
            return args;
        } else {
            throw typeError("function.apply.expects.array");
        }
    }

    private static int lengthToInt(final Object len) {
        final long ln = JSType.toUint32(len);
        // NOTE: ECMASCript 5.1 section 15.3.4.3 says length should be treated as Uint32, but we wouldn't be able to
        // allocate a Java array of more than MAX_VALUE elements anyway, so at this point we have to throw an error.
        // People applying a function to more than 2^31 arguments will unfortunately be out of luck.
        if (ln > Integer.MAX_VALUE) {
            throw rangeError("inappropriate.array.length", JSType.toString(len));
        }
        return (int)ln;
    }

    private static void checkCallable(final Object self) {
        if (!(self instanceof ScriptFunction || (self instanceof JSObject && ((JSObject)self).isFunction()))) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * ECMA 15.3.4.4 Function.prototype.call (thisArg [ , arg1 [ , arg2, ... ] ] )
     *
     * @param self self reference
     * @param args arguments for call
     * @return result of call
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object call(final Object self, final Object... args) {
        checkCallable(self);

        final Object thiz = (args.length == 0) ? UNDEFINED : args[0];
        Object[] arguments;

        if (args.length > 1) {
            arguments = new Object[args.length - 1];
            System.arraycopy(args, 1, arguments, 0, arguments.length);
        } else {
            arguments = ScriptRuntime.EMPTY_ARRAY;
        }

        if (self instanceof ScriptFunction) {
            return ScriptRuntime.apply((ScriptFunction)self, thiz, arguments);
        } else if (self instanceof JSObject) {
            return ((JSObject)self).call(thiz, arguments);
        }

        // a callable proxy, which is a ScriptObject and not a ScriptFunction
        return ScriptRuntime.call(self, thiz, arguments);
    }

    /**
     * ECMA 15.3.4.5 Function.prototype.bind (thisArg [, arg1 [, arg2, ...]])
     *
     * @param self self reference
     * @param args arguments for bind
     * @return function with bound arguments
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object bind(final Object self, final Object... args) {
        final Object thiz = (args.length == 0) ? UNDEFINED : args[0];

        Object[] arguments;
        if (args.length > 1) {
            arguments = new Object[args.length - 1];
            System.arraycopy(args, 1, arguments, 0, arguments.length);
        } else {
            arguments = ScriptRuntime.EMPTY_ARRAY;
        }

        return Bootstrap.bindCallable(self, thiz, arguments);
    }

    /**
     * Nashorn extension: Function.prototype.toSource
     *
     * @param self self reference
     * @return source for function
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toSource(final Object self) {
        return sourceOf(self);
    }

    private static String sourceOf(final Object self) {
        if (self instanceof ScriptFunction function) {
            return function.toSource();
        }
        // Anything else callable - a proxy over a function, most of all - has no
        // source of its own to give back, and 19.2.3.5 asks only for something
        // that reads as a function: the native form is what is left to say.
        if (Bootstrap.isCallable(self)) {
            return "function () { [native code] }";
        }
        throw typeError("not.a.function", ScriptRuntime.safeToString(self));
    }

    /**
     * ECMA 15.3.2.1 new Function (p1, p2, ... , pn, body)
     *
     * Constructor
     *
     * @param newObj is the new operator used for constructing this function
     * @param self   self reference
     * @param args   arguments
     * @return new NativeFunction
     */
    @Constructor(arity = 1)
    public static ScriptFunction function(final boolean newObj, final Object self, final Object... args) {
        // ES2017 19.2.1.1.1 CreateDynamicFunction builds the source in one exact
        // shape, and Function.prototype.toString hands that shape back: the
        // parameters as written, then a newline before the closing parenthesis,
        // and the body on lines of its own. The newlines are not decoration - a
        // parameter list or a body ending in a // comment would otherwise
        // swallow what follows it.
        final String parameters;
        final String body;
        if (args.length > 0) {
            final StringBuilder list = new StringBuilder();
            for (int i = 0; i < args.length - 1; i++) {
                if (i > 0) {
                    list.append(',');
                }
                list.append(JSType.toString(args[i]));
            }
            parameters = list.toString();
            body = JSType.toString(args[args.length - 1]);
            if (!parameters.isEmpty()) {
                checkFunctionParameters(parameters);
            }
            checkFunctionBody(body);
        } else {
            parameters = "";
            body = "";
        }

        // wrapped in parentheses so that it is evaluated as an expression; the
        // function's own source is what is inside them
        final String source = "(function anonymous(" + parameters + "\n) {\n" + body + "\n})";
        final Global global = Global.instance();
        final Context context = global.getContext();
        return (ScriptFunction)context.eval(global, source, global, "<function>");
    }

    private static void checkFunctionParameters(final String params) {
        final Parser parser = getParser(params);
        try {
            parser.parseFormalParameterList();
        } catch (final ParserException pe) {
            pe.throwAsEcmaException();
        }
    }

    private static void checkFunctionBody(final String funcBody) {
        final Parser parser = getParser(funcBody);
        try {
            parser.parseFunctionBody();
        } catch (final ParserException pe) {
            pe.throwAsEcmaException();
        }
    }

    private static Parser getParser(final String sourceText) {
        final ScriptEnvironment env = Global.getEnv();
        return new Parser(env, sourceFor("<function>", sourceText), new Context.ThrowErrorManager(), env._strict, null);
    }
}
