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

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.nio.ByteBuffer;

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Getter;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.internal.runtime.Context;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * NativeArrayBuffer - ArrayBuffer as described in the JS typed
 * array spec
 */
@ScriptClass("ArrayBuffer")
public class NativeArrayBuffer extends ScriptObject {
    /**
     * The backing store. For a fixed-length buffer its capacity is the byte
     * length. For a resizable one (ES2024) it is allocated to {@link
     * #maxByteLength} up front and never reallocated, so a view's cached
     * duplicate stays valid across a resize; {@link #byteLength} tracks the
     * currently-exposed prefix.
     */
    private final ByteBuffer nb;

    /** The currently-exposed byte length (≤ the backing capacity). */
    private int byteLength;

    /** The resizable/growable maximum, or -1 for a fixed-length buffer. */
    private final int maxByteLength;

    /**
     * The views (typed arrays and DataViews) over a resizable buffer, so a
     * resize can rebuild their bounds. Null for a fixed buffer, which never
     * needs it; entries are weak so a view that is gone is not retained.
     */
    private java.util.List<java.lang.ref.WeakReference<ResizeListener>> views;

    /** A view whose bounds follow a resizable buffer's current byte length. */
    interface ResizeListener {
        /** The buffer's byte length changed; recompute this view's bounds. */
        void bufferResized();
    }

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
        this(nb, nb.limit(), -1, global);
    }

    /**
     * Constructor for a buffer with an explicit (possibly resizable) length.
     *
     * @param nb            backing store (capacity is the max for a resizable one)
     * @param byteLength     the currently-exposed byte length
     * @param maxByteLength  the resizable maximum, or -1 for fixed length
     * @param global         global instance
     */
    protected NativeArrayBuffer(final ByteBuffer nb, final int byteLength, final int maxByteLength, final Global global) {
        super(global.getArrayBufferPrototype(), $nasgenmap$);
        this.nb = nb;
        this.byteLength = byteLength;
        this.maxByteLength = maxByteLength;
    }

    /**
     * Constructor for a subclass with a prototype and a map of its own.
     *
     * @param nb        native byte buffer to wrap
     * @param prototype what the buffer inherits from
     * @param map       its property map
     */
    protected NativeArrayBuffer(final ByteBuffer nb, final ScriptObject prototype, final PropertyMap map) {
        this(nb, nb.limit(), -1, prototype, map);
    }

    /**
     * Constructor for a subclass with an explicit (possibly resizable/growable)
     * length, prototype and map of its own.
     *
     * @param nb            backing store (capacity is the max for a resizable one)
     * @param byteLength     the currently-exposed byte length
     * @param maxByteLength  the resizable/growable maximum, or -1 for fixed length
     * @param prototype     what the buffer inherits from
     * @param map           its property map
     */
    protected NativeArrayBuffer(final ByteBuffer nb, final int byteLength, final int maxByteLength, final ScriptObject prototype, final PropertyMap map) {
        super(prototype, map);
        this.nb = nb;
        this.byteLength = byteLength;
        this.maxByteLength = maxByteLength;
    }

    /**
     * Whether this buffer's storage may be reached from more than one agent.
     *
     * @return true for a SharedArrayBuffer
     */
    public boolean isShared() {
        return false;
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
            return withNewTargetPrototype(new NativeArrayBuffer(0));
        }

        final Object arg0 = args[0];
        if (arg0 instanceof ByteBuffer) {
            return withNewTargetPrototype(new NativeArrayBuffer((ByteBuffer)arg0));
        }
        // ES2015 24.1.2.1: ToIndex, so a negative or excessive length is a
        // RangeError rather than something silently truncated to an int
        final long byteLength = ArrayBufferView.toIndexLong(arg0);
        // ES2024 25.1.3.1: the second argument is an options bag; a
        // maxByteLength there makes the buffer resizable, its store allocated up
        // front to the maximum.
        final long maxByteLength = maxByteLengthOption(args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED);
        // ES2024 AllocateArrayBuffer step 1 compares the length against
        // maxByteLength before OrdinaryCreateFromConstructor reads new.target's
        // prototype, so this RangeError precedes that get...
        if (maxByteLength >= 0 && byteLength > maxByteLength) {
            throw rangeError("arraybuffer.length.exceeds.max");
        }
        final ScriptObject prototype = Global.instance().takeNewTargetPrototype();
        // ...whereas allocating the data block (where a length there is no room
        // for fails) happens after, so its RangeError follows the prototype get.
        if (byteLength > Integer.MAX_VALUE || maxByteLength > Integer.MAX_VALUE) {
            throw rangeError("not.an.index", JSType.toString(arg0));
        }
        final int max = (int) maxByteLength;
        final ByteBuffer store = ByteBuffer.allocateDirect(maxByteLength >= 0 ? max : (int) byteLength);
        final NativeArrayBuffer buffer = new NativeArrayBuffer(store, (int) byteLength, max, Global.instance());
        if (prototype != null) {
            buffer.setInitialProto(prototype);
        }
        return buffer;
    }

    /**
     * Reads the {@code maxByteLength} option (ES2024 25.1.3.1 /
     * GetArrayBufferMaxByteLengthOption): -1 when the argument is not an object
     * or has no such property, otherwise the ToIndex of the property.
     */
    static long maxByteLengthOption(final Object options) {
        if (!(options instanceof ScriptObject bag)) {
            // a non-object (undefined included) means "not resizable"; a
            // primitive that is not undefined is simply not an options object
            return -1;
        }
        final Object max = bag.get("maxByteLength");
        if (max == ScriptRuntime.UNDEFINED) {
            return -1;
        }
        return ArrayBufferView.toIndexLong(max);
    }

    /**
     * Gives a buffer the prototype of the new.target it is being built for,
     * where the length left nothing to fail on.
     */
    private static NativeArrayBuffer withNewTargetPrototype(final NativeArrayBuffer buffer) {
        final ScriptObject prototype = Global.instance().takeNewTargetPrototype();
        if (prototype != null) {
            buffer.setInitialProto(prototype);
        }
        return buffer;
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

    protected ByteBuffer getNioBuffer() {
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

    private static NativeArrayBuffer asArrayBuffer(final Object self) {
        // the ArrayBuffer.prototype resizable/detached accessors and resize /
        // transfer are not shared: a SharedArrayBuffer receiver is a TypeError.
        if (self instanceof NativeArrayBuffer arrayBuffer && !arrayBuffer.isShared()) {
            return arrayBuffer;
        }
        throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
    }

    /**
     * ES2024 25.1.6.4 get ArrayBuffer.prototype.maxByteLength - the resizable
     * maximum, or the current byte length for a fixed-length buffer.
     *
     * @param self self reference
     * @return the maximum byte length
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static int maxByteLength(final Object self) {
        final NativeArrayBuffer arrayBuffer = asArrayBuffer(self);
        if (arrayBuffer.isDetached()) {
            return 0;
        }
        return arrayBuffer.isResizable() ? arrayBuffer.getMaxByteLength() : arrayBuffer.getByteLength();
    }

    /**
     * ES2024 25.1.6.5 get ArrayBuffer.prototype.resizable.
     *
     * @param self self reference
     * @return whether the buffer can be resized
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object resizable(final Object self) {
        return asArrayBuffer(self).isResizable();
    }

    /**
     * ES2024 25.1.6.3 get ArrayBuffer.prototype.detached.
     *
     * @param self self reference
     * @return whether the buffer has been detached (e.g. transferred away)
     */
    @Getter(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR)
    public static Object detached(final Object self) {
        return asArrayBuffer(self).isDetached();
    }

    /**
     * ES2024 25.1.6.14 ArrayBuffer.prototype.resize ( newLength )
     *
     * Changes the exposed byte length of a resizable buffer, zeroing the bytes
     * that move across the boundary so a later grow never reveals stale data,
     * and rebuilds the bounds of every view over it.
     *
     * @param self       self reference
     * @param newLength  the requested new byte length
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object resize(final Object self, final Object newLength) {
        final NativeArrayBuffer arrayBuffer = asArrayBuffer(self);
        if (!arrayBuffer.isResizable()) {
            throw typeError("arraybuffer.not.resizable");
        }
        // ToIndex runs user code (a valueOf), which can detach the buffer, so
        // the detached check follows the coercion rather than preceding it.
        final long requested = ArrayBufferView.toIndexLong(newLength);
        if (arrayBuffer.isDetached()) {
            throw typeError("arraybuffer.is.detached");
        }
        if (requested > arrayBuffer.getMaxByteLength()) {
            throw rangeError("arraybuffer.length.exceeds.max");
        }
        final int newLen = (int) requested;
        final int oldLen = arrayBuffer.byteLength;
        // zero the bytes on the moved side of the boundary: on a shrink so a
        // later grow re-reveals zeros, on a grow so the newly-exposed bytes are
        // zero as CreateByteDataBlock would leave them.
        final int from = Math.min(oldLen, newLen);
        final int to   = Math.max(oldLen, newLen);
        final ByteBuffer store = arrayBuffer.nb;
        for (int i = from; i < to; i++) {
            store.put(i, (byte) 0);
        }
        arrayBuffer.setByteLengthAndRebuild(newLen);
        return ScriptRuntime.UNDEFINED;
    }

    /**
     * ES2024 25.1.6.15 ArrayBuffer.prototype.transfer ( [ newLength ] ) and
     * 25.1.6.16 transferToFixedLength: hand this buffer's bytes to a new buffer
     * and detach this one. {@code transfer} keeps resizability (the new buffer's
     * maxByteLength is this one's); {@code transferToFixedLength} makes a
     * fixed-length buffer.
     *
     * @param self        self reference
     * @param newLength   the new byte length, or undefined to keep the current
     * @param fixedLength true for transferToFixedLength
     * @return the new ArrayBuffer
     */
    private static NativeArrayBuffer transferImpl(final Object self, final Object newLength, final boolean fixedLength) {
        final NativeArrayBuffer source = asArrayBuffer(self);
        if (source.isShared()) {
            throw typeError("not.an.arraybuffer.in.dataview", ScriptRuntime.safeToString(self));
        }
        if (source.isDetached()) {
            throw typeError("arraybuffer.is.detached");
        }
        final long requested = newLength == ScriptRuntime.UNDEFINED
                ? source.getByteLength() : ArrayBufferView.toIndexLong(newLength);
        if (requested > Integer.MAX_VALUE) {
            throw rangeError("not.an.index", JSType.toString(newLength));
        }
        final int newLen = (int) requested;
        final int newMax = fixedLength ? -1 : source.getMaxByteLength();
        if (newMax >= 0 && newLen > newMax) {
            throw rangeError("arraybuffer.length.exceeds.max");
        }
        final ByteBuffer store = ByteBuffer.allocateDirect(newMax >= 0 ? newMax : newLen);
        final int copied = Math.min(newLen, source.getByteLength());
        for (int i = 0; i < copied; i++) {
            store.put(i, source.nb.get(i));
        }
        source.detached = true;
        source.setByteLengthAndRebuild(0);
        return new NativeArrayBuffer(store, newLen, newMax, Global.instance());
    }

    /**
     * ES2024 25.1.6.15 ArrayBuffer.prototype.transfer ( [ newLength ] ).
     *
     * @param self      self reference
     * @param newLength the new byte length, or undefined to keep the current
     * @return the new ArrayBuffer, this one detached
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object transfer(final Object self, final Object newLength) {
        return transferImpl(self, newLength, false);
    }

    /**
     * ES2024 25.1.6.16 ArrayBuffer.prototype.transferToFixedLength ( [ newLength ] ).
     *
     * @param self      self reference
     * @param newLength the new byte length, or undefined to keep the current
     * @return the new fixed-length ArrayBuffer, this one detached
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object transferToFixedLength(final Object self, final Object newLength) {
        return transferImpl(self, newLength, true);
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
        if (!(self instanceof NativeArrayBuffer source) || source.isShared()) {
            // 24.1.4.3 step 3: a shared buffer has its own slice, and this one
            // is not it
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

    protected int getByteLength() {
        return detached ? 0 : byteLength;
    }

    /**
     * @return whether this buffer can be resized (ES2024) - constructed with a
     *         {@code maxByteLength} option
     */
    public boolean isResizable() {
        return maxByteLength >= 0;
    }

    /** The resizable/growable maximum, or -1 for a fixed-length buffer. */
    int getMaxByteLength() {
        return maxByteLength;
    }

    /**
     * Registers a view so a later resize can rebuild its bounds. A no-op for a
     * fixed buffer, whose views never move.
     *
     * @param view the typed array or DataView to keep in step
     */
    void registerView(final ResizeListener view) {
        if (!isResizable()) {
            return;
        }
        if (views == null) {
            views = new java.util.ArrayList<>();
        }
        views.add(new java.lang.ref.WeakReference<>(view));
    }

    /**
     * Grows a growable (shared) buffer's exposed length. No zeroing: the store
     * was allocated to the maximum and starts zeroed, and a shared buffer only
     * ever grows, so the newly-exposed bytes are already zero and zeroing them
     * would race the other agents.
     *
     * @param newByteLength the larger byte length
     */
    void growTo(final int newByteLength) {
        setByteLengthAndRebuild(newByteLength);
    }

    /**
     * Sets a new byte length after a resize/grow and rebuilds every live view's
     * bounds. Bytes are shared with the backing store, which never moves, so a
     * view's cached duplicate stays valid; only its start/end change.
     */
    private void setByteLengthAndRebuild(final int newByteLength) {
        this.byteLength = newByteLength;
        if (views == null) {
            return;
        }
        final java.util.Iterator<java.lang.ref.WeakReference<ResizeListener>> it = views.iterator();
        while (it.hasNext()) {
            final ResizeListener view = it.next().get();
            if (view == null) {
                it.remove();
            } else {
                view.bufferResized();
            }
        }
    }

    ByteBuffer getBuffer() {
       return nb;
    }

    ByteBuffer getBuffer(final int offset, final int length) {
        // a slice rather than a duplicate that has been positioned: what reads
        // it reads by absolute index, which counts from the start of the buffer
        // and takes no notice of where a position was left
        return nb.duplicate().position(offset).limit(offset + length).slice();
    }
}
