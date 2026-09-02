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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.nio.ByteBuffer;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * A SharedArrayBuffer (ECMAScript 2017 24.2), which is an ArrayBuffer whose
 * storage may be reached from more than one agent at once.
 *
 * It is the same storage class as an ordinary buffer - a direct
 * {@link ByteBuffer}, which is what lets a view be built over either without
 * knowing which it has - and differs in what may be done to it. It cannot be
 * detached, so a view over one never becomes empty; its slice makes another
 * shared buffer rather than an ordinary one; and it is what the Atomics
 * operations insist on being given.
 */
@ScriptClass("SharedArrayBuffer")
public final class NativeSharedArrayBuffer extends NativeArrayBuffer {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeSharedArrayBuffer(final ByteBuffer nb, final Global global) {
        super(nb, global.getSharedArrayBufferPrototype(), $nasgenmap$);
    }

    /**
     * Wraps storage that already exists in the realm asking for it.
     *
     * Sharing a buffer between agents means sharing the storage, not the object:
     * each agent sees a SharedArrayBuffer of its own realm over the same bytes,
     * which is what makes the atomic operations meet.
     *
     * @param bytes  the storage to wrap
     * @param global the realm the wrapper belongs to
     * @return the wrapper
     */
    public static NativeSharedArrayBuffer wrap(final ByteBuffer bytes, final Global global) {
        return new NativeSharedArrayBuffer(bytes, global);
    }

    /**
     * The storage behind a shared buffer, for a host handing it to another agent.
     *
     * @param buffer the buffer
     * @return its bytes, or null if it was not a shared buffer
     */
    public static ByteBuffer storageOf(final Object buffer) {
        // a host reaches this through Java.type, so the buffer arrives wrapped
        final Object target = buffer instanceof org.monflabs.nashorn.api.scripting.ScriptObjectMirror mirror
                ? org.monflabs.nashorn.api.scripting.ScriptObjectMirror.unwrap(mirror,
                        org.monflabs.nashorn.internal.runtime.Context.getGlobal())
                : buffer;
        // a view over one will do as well as the buffer itself
        final Object owner = target instanceof ArrayBufferView view ? view.getArrayBuffer() : target;
        return owner instanceof NativeSharedArrayBuffer shared ? shared.getNioBuffer() : null;
    }

    /**
     * ES2017 24.2.2.1 SharedArrayBuffer(length).
     *
     * @param newObj is this invoked with new
     * @param self   self reference
     * @param args   the byte length
     * @return the buffer
     */
    @Constructor(arity = 1)
    public static NativeSharedArrayBuffer constructor(final boolean newObj, final Object self, final Object... args) {
        if (!newObj) {
            throw typeError("constructor.requires.new", "SharedArrayBuffer");
        }
        final long byteLength = args.length == 0 ? 0 : ArrayBufferView.toIndexLong(args[0]);
        // 24.2.1.1 reads new.target's prototype before it allocates the data,
        // which is what a length there is no room for fails at
        final ScriptObject prototype = Global.instance().takeNewTargetPrototype();
        if (byteLength > Integer.MAX_VALUE) {
            throw rangeError("not.an.index", JSType.toString(args[0]));
        }
        final NativeSharedArrayBuffer buffer =
                new NativeSharedArrayBuffer(ByteBuffer.allocateDirect((int)byteLength), Global.instance());
        if (prototype != null) {
            buffer.setInitialProto(prototype);
        }
        return buffer;
    }

    /**
     * ES2017 24.2.3.2 get SharedArrayBuffer [ @@species ].
     *
     * @param self self reference
     * @return the constructor it was read from
     */
    @Getter(where = Where.CONSTRUCTOR, name = "@@species", attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object species(final Object self) {
        return self;
    }

    /**
     * ES2017 24.2.4.1 get SharedArrayBuffer.prototype.byteLength.
     *
     * @param self the buffer
     * @return how many bytes it holds
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int byteLength(final Object self) {
        return check(self).getByteLength();
    }

    /**
     * ES2017 24.2.4.3 SharedArrayBuffer.prototype.slice.
     *
     * @param self  the buffer
     * @param begin where to start
     * @param end   where to stop
     * @return another shared buffer holding the copy
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object slice(final Object self, final Object begin, final Object end) {
        final NativeSharedArrayBuffer buffer = check(self);
        final int byteLength = buffer.getByteLength();
        final int from = relative(JSType.toInteger(begin), byteLength);
        final int to = end == ScriptRuntime.UNDEFINED ? byteLength : relative(JSType.toInteger(end), byteLength);

        final int length = Math.max(to - from, 0);

        // 24.2.4.3 step 12: the copy is made by the species constructor, which
        // is an ordinary constructor call and can hand back anything at all -
        // so what it hands back is checked before a byte is written into it.
        final ScriptFunction species = speciesConstructor(buffer);
        final NativeSharedArrayBuffer target;
        if (species == null) {
            target = new NativeSharedArrayBuffer(ByteBuffer.allocateDirect(length), Global.instance());
        } else {
            final Object created = ScriptRuntime.construct(species, (double)length);
            if (!(created instanceof NativeSharedArrayBuffer made)) {
                throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(created));
            }
            if (made == buffer) {
                throw typeError("arraybuffer.species.same", ScriptRuntime.safeToString(created));
            }
            if (made.getByteLength() < length) {
                throw typeError("arraybuffer.species.too.short", JSType.toString(length));
            }
            target = made;
        }

        final ByteBuffer source = buffer.getNioBuffer().duplicate();
        source.position(from).limit(from + length);
        final ByteBuffer copy = target.getNioBuffer().duplicate();
        copy.position(0);
        copy.put(source);
        return target;
    }

    /**
     * ES2015 7.3.20 SpeciesConstructor over a shared buffer, or null for the
     * default one.
     */
    private static ScriptFunction speciesConstructor(final NativeSharedArrayBuffer source) {
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

    /** ES2017 24.2.4.4 SharedArrayBuffer.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "SharedArrayBuffer";

    @Override
    public String getClassName() {
        return "SharedArrayBuffer";
    }

    @Override
    public boolean isShared() {
        return true;
    }

    private static int relative(final int index, final int length) {
        return index < 0 ? Math.max(length + index, 0) : Math.min(index, length);
    }

    /** The receiver, which these operations are as particular about as their ordinary siblings. */
    private static NativeSharedArrayBuffer check(final Object self) {
        if (self instanceof NativeSharedArrayBuffer buffer) {
            return buffer;
        }
        throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
    }
}
