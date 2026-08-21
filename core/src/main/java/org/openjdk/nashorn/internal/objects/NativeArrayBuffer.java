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

import java.nio.ByteBuffer;

import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Constructor;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Getter;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.ScriptFunction;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * NativeArrayBuffer - ArrayBuffer as described in the JS typed
 * array spec
 */
@ScriptClass("ArrayBuffer")
public final class NativeArrayBuffer extends ScriptObject {
    private final ByteBuffer nb;

    /**
     * Whether the host has detached this buffer.
     *
     * ES2015 24.1.1.3 DetachArrayBuffer is not reachable from a script; it exists
     * for hosts that transfer a buffer elsewhere, and the conformance suite asks
     * for it so that it can check what every operation over a detached buffer
     * does. A detached buffer has a byte length of zero and every view over it
     * becomes empty.
     */
    private volatile boolean detached;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * ES2015 24.1.3.3 get ArrayBuffer [ @@species ].
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

    /**
     * Constructor
     * @param nb native byte buffer to wrap
     * @param global global instance
     */
    protected NativeArrayBuffer(final ByteBuffer nb, final Global global) {
        super(global.getArrayBufferPrototype(), $nasgenmap$);
        this.nb = nb;
    }

    /**
     * Constructor
     * @param nb native byte buffer to wrap
     */
    protected NativeArrayBuffer(final ByteBuffer nb) {
        this(nb, Global.instance());
    }

    /**
     * Constructor
     * @param byteLength byteLength for buffer
     */
    protected NativeArrayBuffer(final int byteLength) {
        this(ByteBuffer.allocateDirect(byteLength));
    }

    /**
     * Clone constructor
     * Used only for slice
     * @param other original buffer
     * @param begin begin byte index
     * @param end   end byte index
     */
    protected NativeArrayBuffer(final NativeArrayBuffer other, final int begin, final int end) {
        this(cloneBuffer(other.getNioBuffer(), begin, end));
    }

    /**
     * Constructor
     * @param newObj is this invoked with new
     * @param self   self reference
     * @param args   arguments to constructor
     * @return new NativeArrayBuffer
     */
    @Constructor(arity = 1)
    public static NativeArrayBuffer constructor(final boolean newObj, final Object self, final Object... args) {
        if (!newObj) {
            throw typeError("constructor.requires.new", "ArrayBuffer");
        }

        if (args.length == 0) {
            return new NativeArrayBuffer(0);
        }

        final Object arg0 = args[0];
        if (arg0 instanceof ByteBuffer) {
            return new NativeArrayBuffer((ByteBuffer)arg0);
        }
        // ES2015 24.1.2.1: ToIndex, so a negative or excessive length is a
        // RangeError rather than something silently truncated to an int
        return new NativeArrayBuffer(ArrayBufferView.toIndex(arg0));
    }

    private static ByteBuffer cloneBuffer(final ByteBuffer original, final int begin, final int end) {
        final ByteBuffer clone = ByteBuffer.allocateDirect(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        clone.position(begin);
        clone.limit(end);
        return clone.slice();
    }

    ByteBuffer getNioBuffer() {
        return nb;
    }

    /**
     * ES2015 24.1.1.3 DetachArrayBuffer, for a host that hands the storage to
     * someone else. Not reachable from a script: the method is deliberately
     * unannotated, so nasgen puts no property on ArrayBuffer for it.
     *
     * @param buffer the buffer to detach
     */
    public static void detach(final Object buffer) {
        // a host reaches this through Java.type, so the buffer arrives wrapped
        final Object target = buffer instanceof ScriptObjectMirror mirror
                ? ScriptObjectMirror.unwrap(mirror, Context.getGlobal())
                : buffer;
        if (target instanceof NativeArrayBuffer arrayBuffer) {
            arrayBuffer.detached = true;
            return;
        }
        throw typeError("not.an.object", ScriptRuntime.safeToString(buffer));
    }

    /**
     * @return whether this buffer's storage has been taken away
     */
    public boolean isDetached() {
        return detached;
    }

    @Override
    public String getClassName() {
        return "ArrayBuffer";
    }

    /**
     * Byte length for native array buffer
     * @param self native array buffer
     * @return byte length
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteLength(final Object self) {
        if (!(self instanceof NativeArrayBuffer arrayBuffer)) {
            throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
        }
        // ES2015 24.1.4.1 step 4: a detached buffer has no bytes rather than
        // an unknown number of them
        return arrayBuffer.isDetached() ? 0 : arrayBuffer.getByteLength();
    }

    /**
     * ES2015 24.1.4.4 ArrayBuffer.prototype [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "ArrayBuffer";

    /**
     * Returns true if an object is an ArrayBufferView
     *
     * @param self self
     * @param obj  object to check
     *
     * @return true if obj is an ArrayBufferView
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isView(final Object self, final Object obj) {
        // ES2015 24.1.3.1: a DataView is a view as much as a typed array is
        return obj instanceof ArrayBufferView || obj instanceof NativeDataView;
    }

    /**
     * Slice function
     * @param self   native array buffer
     * @param begin0 start byte index
     * @param end0   end byte index
     * @return new array buffer, sliced
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object slice(final Object self, final Object begin0, final Object end0) {
        if (!(self instanceof NativeArrayBuffer source)) {
            throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
        }
        if (source.isDetached()) {
            throw typeError("detached.array.buffer");
        }

        final int byteLength = source.getByteLength();
        final int begin = ArrayBufferView.relativeIndex(begin0, byteLength, 0);
        final int end   = ArrayBufferView.relativeIndex(end0, byteLength, byteLength);
        final int length = Math.max(end - begin, 0);

        // ES2015 24.1.4.3 step 14: the copy is made by the species constructor,
        // which is an ordinary constructor call and can do anything - including
        // detaching the source it is about to be copied from
        final ScriptFunction species = speciesConstructor(source);
        if (species == null) {
            return new NativeArrayBuffer(source, begin, begin + length);
        }

        final Object created = ScriptRuntime.construct(species, (double)length);
        if (!(created instanceof NativeArrayBuffer target)) {
            throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(created));
        }
        if (target.isDetached()) {
            throw typeError("detached.array.buffer");
        }
        if (target == source) {
            throw typeError("arraybuffer.species.same", ScriptRuntime.safeToString(created));
        }
        if (target.getByteLength() < length) {
            throw typeError("arraybuffer.species.too.short", JSType.toString(length));
        }
        if (source.isDetached()) {
            throw typeError("detached.array.buffer");
        }

        final ByteBuffer from = source.getNioBuffer().duplicate();
        final ByteBuffer to   = target.getNioBuffer().duplicate();
        from.position(begin).limit(begin + length);
        to.position(0);
        to.put(from);
        return target;
    }

    /**
     * ES2015 7.3.20 SpeciesConstructor over an ArrayBuffer, or null for the
     * default one.
     */
    private static ScriptFunction speciesConstructor(final NativeArrayBuffer source) {
        final Object constructor = source.get("constructor");
        if (constructor == ScriptRuntime.UNDEFINED) {
            return null;
        }
        if (!(constructor instanceof ScriptObject ctor)) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(constructor));
        }
        final Object species = ctor.get(NativeSymbol.species);
        if (species == ScriptRuntime.UNDEFINED || species == null) {
            return null;
        }
        if (!(species instanceof ScriptFunction function) || !function.isConstructor()) {
            throw typeError("not.a.constructor", ScriptRuntime.safeToString(species));
        }
        return function;
    }

    /**
     * If index is negative, it refers to an index from the end of the array, as
     * opposed to from the beginning. The index is clamped to the valid index
     * range for the array.
     *
     * @param index  The index.
     * @param length The length of the array.
     * @return valid index index in the range [0, length).
     */
    static int adjustIndex(final int index, final int length) {
        return index < 0 ? clamp(index + length, length) : clamp(index, length);
    }

    /**
     * Clamp index into the range [0, length).
     */
    private static int clamp(final int index, final int length) {
        if (index < 0) {
            return 0;
        } else if (index > length) {
            return length;
        }
        return index;
    }

    int getByteLength() {
        return detached ? 0 : nb.limit();
    }

    ByteBuffer getBuffer() {
       return nb;
    }

    ByteBuffer getBuffer(final int offset) {
        return nb.duplicate().position(offset);
    }

    ByteBuffer getBuffer(final int offset, final int length) {
        return getBuffer(offset).limit(length);
    }
}
