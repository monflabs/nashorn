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

package org.monflabs.nashorn.api.debugger;

import java.util.List;

/**
 * Interprets the engine objects the debugger hands out. The type words follow
 * the Chrome DevTools Protocol, which is what most frontends speak.
 *
 * <p>The reading methods touch script objects and must run on the thread
 * that owns them - through {@link PausedEvent#call} while paused - except
 * that primitives and functions' names may be read anywhere.
 *
 * @since 2017.0.0
 */
public interface DebugValues {

    /** The value of {@code undefined}, which the engine represents by a sentinel. */
    Object UNDEFINED = org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

    /**
     * The type: {@code object}, {@code function}, {@code undefined}, {@code string},
     * {@code number}, {@code boolean}, {@code symbol}.
     * @param value the value
     * @return the type
     */
    String type(Object value);

    /**
     * The subtype of an object: {@code array}, {@code null}, {@code regexp},
     * {@code date}, {@code map}, {@code set}, {@code error}, {@code proxy},
     * {@code promise}, {@code typedarray}, {@code arraybuffer}, {@code dataview},
     * {@code generator}, or null.
     * @param value the value
     * @return the subtype or null
     */
    String subtype(Object value);

    /**
     * The class name of an object: {@code Object}, {@code Array}, {@code Function}, ...
     * @param value the value
     * @return the class name, or null for a primitive
     */
    String className(Object value);

    /**
     * A one line description: a primitive's text, a function's head, an object's class.
     * @param value the value
     * @return the description
     */
    String description(Object value);

    /**
     * Whether the value is a primitive, undefined or null.
     * @param value the value
     * @return true if primitive
     */
    boolean isPrimitive(Object value);

    /**
     * A primitive as a Java value: {@link String}, {@link Double}, {@link Boolean},
     * null for {@code null}, {@link #UNDEFINED} for undefined; an object unchanged.
     * @param value the value
     * @return the Java value
     */
    Object toJava(Object value);

    /**
     * The name a number needs when JSON cannot carry it: {@code NaN},
     * {@code Infinity}, {@code -Infinity}, {@code -0}; otherwise null.
     * @param value the value
     * @return the name or null
     */
    String unserializable(Object value);

    /**
     * An object's own properties.
     * @param object the object
     * @param includeNonEnumerable whether to include non-enumerable properties
     * @param includeIndexed whether to include array elements and other indexed properties
     * @return the properties
     */
    List<DebugProperty> ownProperties(Object object, boolean includeNonEnumerable, boolean includeIndexed);

    /**
     * The internal properties a debugger shows in brackets: {@code [[Prototype]]},
     * a function's {@code [[FunctionLocation]]} as a {@link Location}, a bound function's target.
     * @param object the object
     * @return the properties
     */
    List<DebugProperty> internalProperties(Object object);

    /**
     * An object's prototype.
     * @param object the object
     * @return the prototype, or null
     */
    Object prototype(Object object);

    /**
     * An array's length.
     * @param array the array
     * @return the length, or -1 for a non-array
     */
    long arrayLength(Object array);

    /**
     * Sets a property, as an assignment would.
     * @param object the object
     * @param key the property key: a String or a symbol engine object
     * @param value the value, an engine object or a Java primitive
     */
    void setProperty(Object object, Object key, Object value);

    /**
     * Calls a function.
     * @param function the function
     * @param thisValue the receiver
     * @param arguments the arguments
     * @return the result
     * @throws DebugException if the function throws
     */
    Object callFunction(Object function, Object thisValue, Object... arguments) throws DebugException;

    /**
     * Evaluates an expression in a context with a given receiver, on the calling thread.
     * @param context the context
     * @param expression the expression
     * @param thisValue the receiver, or null for the global object
     * @return the result
     * @throws DebugException if the expression throws or does not parse
     */
    Object evaluateWith(ExecutionContext context, String expression, Object thisValue) throws DebugException;
}
