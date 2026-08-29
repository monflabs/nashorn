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

import static org.openjdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static org.openjdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.openjdk.nashorn.internal.objects.annotations.Attribute;
import org.openjdk.nashorn.internal.objects.annotations.Function;
import org.openjdk.nashorn.internal.objects.annotations.Property;
import org.openjdk.nashorn.internal.objects.annotations.ScriptClass;
import org.openjdk.nashorn.internal.objects.annotations.Where;
import org.openjdk.nashorn.internal.runtime.JSType;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.ScriptRuntime;
import org.openjdk.nashorn.internal.runtime.SharedMemory;

/**
 * The Atomics object (ECMAScript 2017 24.4).
 *
 * Each operation is one access to one element of an integer typed array, and
 * the memory ordering the specification asks for is sequential consistency,
 * which is what a {@link VarHandle} over the viewed bytes gives without any
 * locking of our own. The handles are made per element width rather than per
 * call, because the width is all they depend on.
 *
 * wait and notify are the two that are not a single access: they need a queue
 * per address, which {@link SharedMemory} keeps.
 */
@ScriptClass("Atomics")
public final class NativeAtomics extends ScriptObject {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private static final VarHandle SHORTS =
            MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.nativeOrder());
    private static final VarHandle INTS =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    /** Serialises the element widths that have no atomic update of their own. */
    private static final Object NARROW = new Object();

    private NativeAtomics() {
        super(null, null);
        throw new UnsupportedOperationException();
    }

    /** ES2017 24.4.15 Atomics [ @@toStringTag ]. */
    @Property(where = Where.CONSTRUCTOR, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Atomics";

    /**
     * ES2017 24.4.1 Atomics.add(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to add
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object add(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.ADD);
    }

    /**
     * ES2017 24.4.2 Atomics.and(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to and it with
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object and(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.AND);
    }

    /**
     * ES2017 24.4.3 Atomics.compareExchange(typedArray, index, expected, replacement).
     *
     * @param self        self reference
     * @param array       the array to work on
     * @param index       which element
     * @param expected    the value it must have
     * @param replacement what to put there if it does
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 4)
    public static Object compareExchange(final Object self, final Object array, final Object index,
            final Object expected, final Object replacement) {
        final Access at = access(array, index);
        final int want = at.narrow(JSType.toInt32(expected));
        final int replaceWith = at.narrow(JSType.toInt32(replacement));
        return at.result(at.compareExchange(want, replaceWith));
    }

    /**
     * ES2017 24.4.4 Atomics.exchange(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to put there
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object exchange(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.EXCHANGE);
    }

    /**
     * ES2017 24.4.5 Atomics.isLockFree(size).
     *
     * @param self self reference
     * @param size the element width being asked about
     * @return whether an access of that width needs no lock
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 1)
    public static boolean isLockFree(final Object self, final Object size) {
        final double width = JSType.toNumber(size);
        return width == 1 || width == 2 || width == 4;
    }

    /**
     * ES2017 24.4.6 Atomics.load(typedArray, index).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @return what it holds
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 2)
    public static Object load(final Object self, final Object array, final Object index) {
        final Access at = access(array, index);
        return at.result(at.get());
    }

    /**
     * ES2017 24.4.7 Atomics.or(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to or it with
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object or(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.OR);
    }

    /**
     * ES2017 24.4.9 Atomics.store(typedArray, index, value).
     *
     * Alone among these it answers with what it was given rather than with what
     * was there, and with the number the argument converted to rather than the
     * number the element can hold.
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to put there
     * @return the value, as a number
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object store(final Object self, final Object array, final Object index, final Object value) {
        final Access at = access(array, index);
        // 24.4.9 step 5 converts with ToInteger and answers with that, so a
        // fractional argument is answered as the whole number it was truncated
        // to and a negative zero as a positive one
        final double asNumber = JSType.toNumber(value);
        // truncated here rather than by JSType.toInteger, which clamps to the
        // range of an int - a store of 2^32-1 into a Uint32Array is answered
        // with 2^32-1, and only what is written to the element is narrowed
        final double asInteger = Double.isNaN(asNumber) ? 0.0
                : Math.copySign(Math.floor(Math.abs(asNumber)), asNumber) + 0.0;
        at.set(at.narrow(JSType.toInt32(asInteger)));
        return asInteger;
    }

    /**
     * ES2017 24.4.10 Atomics.sub(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to subtract
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object sub(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.SUB);
    }

    /**
     * ES2017 24.4.13 Atomics.xor(typedArray, index, value).
     *
     * @param self  self reference
     * @param array the array to work on
     * @param index which element
     * @param value what to exclusive-or it with
     * @return the value the element had before
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3)
    public static Object xor(final Object self, final Object array, final Object index, final Object value) {
        return apply(array, index, value, Op.XOR);
    }

    /**
     * ES2017 24.4.11 Atomics.wait(typedArray, index, value, timeout).
     *
     * @param self    self reference
     * @param args    the array, the index, the value to wait on and how long for
     * @return "not-equal" if it already changed, "timed-out", or "ok"
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 4, name = "wait")
    public static Object _wait(final Object self, final Object... args) {
        final Object array = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object index = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object value = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;
        final Object timeout = args.length > 3 ? args[3] : ScriptRuntime.UNDEFINED;

        final Access at = access(array, index, true, true);
        final int want = JSType.toInt32(value);
        final double millis = timeout == ScriptRuntime.UNDEFINED ? Double.POSITIVE_INFINITY
                : Math.max(JSType.toNumber(timeout), 0);
        return SharedMemory.wait(at.storage, at.absoluteOffset, want, millis, at::get);
    }

    /**
     * ES2017 24.4.12 Atomics.notify(typedArray, index, count) - "wake" when it
     * was written.
     *
     * @param self  self reference
     * @param args  the array, the index and how many waiters to wake
     * @return how many were woken
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR, arity = 3, name = "notify")
    public static Object notifyWaiters(final Object self, final Object... args) {
        final Object array = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object index = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object count = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;

        final Access at = access(array, index, false, true, true);
        final double howMany = count == ScriptRuntime.UNDEFINED ? Double.POSITIVE_INFINITY
                : Math.max(JSType.toInteger(count), 0);
        if (!at.shared) {
            // memory nobody else can see has nobody waiting on it
            return 0;
        }
        return SharedMemory.notify(at.storage, at.absoluteOffset, howMany);
    }

    /** The read-modify-write operations, which differ only in what they compute. */
    private enum Op {
        ADD, AND, EXCHANGE, OR, SUB, XOR;

        int combine(final int was, final int operand) {
            return switch (this) {
                case ADD -> was + operand;
                case AND -> was & operand;
                case EXCHANGE -> operand;
                case OR -> was | operand;
                case SUB -> was - operand;
                case XOR -> was ^ operand;
            };
        }
    }

    private static Object apply(final Object array, final Object index, final Object value, final Op op) {
        final Access at = access(array, index);
        final int operand = at.narrow(JSType.toInt32(value));
        int was;
        do {
            was = at.get();
        } while (!at.weakCompareAndSet(was, at.narrow(op.combine(was, operand))));
        return at.result(was);
    }

    /**
     * One element of one integer typed array, resolved once so that the
     * operation itself is a single access.
     */
    private record Access(ByteBuffer bytes, int offset, int width, boolean signed, Object storage,
            int absoluteOffset, boolean shared) {
        /** The element as its own array reads it, which for an unsigned one is not the raw byte. */
        int get() {
            return switch (width) {
                case 1 -> signed ? bytes.get(offset) : bytes.get(offset) & 0xFF;
                case 2 -> signed ? (short)SHORTS.getVolatile(bytes, offset)
                                 : (short)SHORTS.getVolatile(bytes, offset) & 0xFFFF;
                default -> (int)INTS.getVolatile(bytes, offset);
            };
        }

        void set(final int value) {
            switch (width) {
                case 1 -> bytes.put(offset, (byte)value);
                case 2 -> SHORTS.setVolatile(bytes, offset, (short)value);
                default -> INTS.setVolatile(bytes, offset, value);
            }
        }

        /**
         * A byte-buffer view handle offers its atomic update modes for four byte
         * wide types and wider, and nothing narrower: a byte has no view handle
         * at all and a short has one that will only read and write. So the two
         * narrow widths are serialised here instead. That is a lock the
         * specification does not describe - Atomics.isLockFree says as much for
         * them - and it holds only against other narrow accesses made this way.
         */
        boolean weakCompareAndSet(final int was, final int value) {
            if (width == 4) {
                return INTS.compareAndSet(bytes, offset, was, value);
            }
            synchronized (NARROW) {
                if (get() != was) {
                    return false;
                }
                set(value);
                return true;
            }
        }

        int compareExchange(final int want, final int replacement) {
            int was;
            do {
                was = get();
                if (was != want) {
                    return was;
                }
            } while (!weakCompareAndSet(was, replacement));
            return was;
        }

        /** Narrows a converted argument to what the element can actually hold. */
        int narrow(final int value) {
            return switch (width) {
                case 1 -> signed ? (byte)value : value & 0xFF;
                case 2 -> signed ? (short)value : value & 0xFFFF;
                default -> value;
            };
        }

        /** What the operation answers with, which for an unsigned int is not an int at all. */
        Object result(final int value) {
            if (width == 4 && !signed) {
                return (double)(value & 0xFFFFFFFFL);
            }
            return value;
        }
    }

    private static Access access(final Object array, final Object index) {
        return access(array, index, false, false, false);
    }

    private static Access access(final Object array, final Object index, final boolean mustBeShared,
            final boolean mustBeInt32) {
        return access(array, index, mustBeShared, mustBeInt32, false);
    }

    /**
     * ES2017 24.4.1.1 ValidateSharedIntegerTypedArray followed by
     * ValidateAtomicAccess: what the operations may be given, and where in it.
     */
    private static Access access(final Object array, final Object index, final boolean mustBeShared,
            final boolean mustBeInt32, final boolean answersWhenDetached) {
        if (!(array instanceof ArrayBufferView view)) {
            throw typeError("atomics.not.integer.typed.array", ScriptRuntime.safeToString(array));
        }
        final int width;
        final boolean signed;
        switch (view.getClassName()) {
            case "Int8Array" -> { width = 1; signed = true; }
            case "Uint8Array" -> { width = 1; signed = false; }
            case "Int16Array" -> { width = 2; signed = true; }
            case "Uint16Array" -> { width = 2; signed = false; }
            case "Int32Array" -> { width = 4; signed = true; }
            case "Uint32Array" -> { width = 4; signed = false; }
            default -> throw typeError("atomics.not.integer.typed.array", ScriptRuntime.safeToString(array));
        }
        // asked before the index is converted, because 24.4.11 and 24.4.12
        // validate the array first and a converting index can run script
        if (mustBeInt32 && (width != 4 || !signed)) {
            throw typeError("atomics.not.shared.int32", ScriptRuntime.safeToString(array));
        }
        if (mustBeShared && !view.getArrayBuffer().isShared()) {
            throw typeError("atomics.not.shared.int32", ScriptRuntime.safeToString(array));
        }
        if (view.isDetached()) {
            throw typeError("atomics.not.integer.typed.array", ScriptRuntime.safeToString(array));
        }

        // 24.4.1.2 ValidateAtomicAccess reads the length before it converts the
        // index, so an index whose conversion detaches the buffer is measured
        // against the array as it stood
        final long length = view.getElementLength();
        final long asIndex = ArrayBufferView.toIndexLong(index);
        if (asIndex >= length) {
            throw rangeError("inappropriate.array.index", ScriptRuntime.safeToString(index));
        }
        if (view.isDetached()) {
            // the conversion detached it. There is nothing left to read or
            // write; notify answers for memory nobody else can see, and
            // everything else says so
            if (!answersWhenDetached) {
                throw typeError("atomics.not.integer.typed.array", ScriptRuntime.safeToString(array));
            }
            return new Access(null, 0, width, signed, null, 0, false);
        }
        return new Access(view.viewedBytes(), (int)asIndex * width, width, signed,
                // the storage rather than the wrapper: every realm sharing a
                // buffer has a wrapper of its own over the same bytes
                NativeSharedArrayBuffer.storageOf(view.getArrayBuffer()),
                view.getViewByteOffset() + (int)asIndex * width,
                view.getArrayBuffer().isShared());
    }
}
