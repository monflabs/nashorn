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

package org.monflabs.nashorn.libs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.runtime.ECMAException;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.JobQueue;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * The {@code host} library: what a script expects from its host beyond the
 * language and is not in ECMAScript - the WHATWG timers ({@code setTimeout},
 * {@code clearTimeout}, {@code setInterval}, {@code clearInterval}),
 * {@code queueMicrotask}, and the Base64 pair {@code atob}/{@code btoa} -
 * as built-in functions of every global, non-enumerable like the language's own.
 *
 * <p>Timers run on the realm's event loop: a callback runs on the script's
 * thread once its delay has passed and the script is between turns, and an
 * {@code eval} that scheduled one returns when the last has fired. Each
 * global has timer ids and pending timers of its own.
 *
 * @since 2017.0.0
 */
public final class HostLibrary implements ScriptLibrary {

    /** The functions, by the names they take in the global. */
    enum Function {
        setTimeout, clearTimeout, setInterval, clearInterval, queueMicrotask, atob, btoa
    }

    private static final MethodHandle CALL;
    static {
        try {
            CALL = MethodHandles.lookup().findStatic(HostLibrary.class, "call",
                    MethodType.methodType(Object.class, Function.class, Timers.class, Object.class, Object[].class));
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public String name() {
        return "host";
    }

    @Override
    public void initialize(final JSObject global) {
        final Global realm = Global.instance();
        final Timers timers = new Timers();   // this global's
        for (final Function function : Function.values()) {
            final MethodHandle bound = MethodHandles.insertArguments(CALL, 0, function, timers);
            realm.installLibraryFunction(function.name(), ScriptFunction.createBuiltin(function.name(), bound));
        }
    }

    /** A global's timers: ids handed out, and the ones still to fire. Loop thread only. */
    private static final class Timers {
        int nextId = 1;
        final Map<Integer, Entry> entries = new HashMap<>();
    }

    /** One timer. */
    private static final class Entry {
        final int id;
        final Object callback;
        final Object[] arguments;
        final long intervalMillis;   // 0 for a one-shot
        Object handle;
        boolean cancelled;

        Entry(final int id, final Object callback, final Object[] arguments, final long intervalMillis) {
            this.id = id;
            this.callback = callback;
            this.arguments = arguments;
            this.intervalMillis = intervalMillis;
        }
    }

    private static ECMAException typeError(final String message) {
        return new ECMAException(Global.instance().newTypeError(message), null);
    }

    private static ECMAException error(final String message) {
        return new ECMAException(Global.instance().newError(message), null);
    }

    /** The one entry point of every function: the switch. */
    @SuppressWarnings("unused")
    private static Object call(final Function function, final Timers timers, final Object self, final Object... args) {
        switch (function) {
        case setTimeout:
            Global.requireEventLoop("setTimeout");
            return schedule(timers, args, false);
        case setInterval:
            Global.requireEventLoop("setInterval");
            return schedule(timers, args, true);
        case clearTimeout:
        case clearInterval:
            // the two are interchangeable, as the HTML specification says
            cancel(timers, args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED);
            return ScriptRuntime.UNDEFINED;
        case queueMicrotask:
            Global.requireEventLoop("queueMicrotask");
            if (args.length == 0 || !Bootstrap.isCallable(args[0])) {
                throw typeError("queueMicrotask: the argument is not a function");
            }
            final Object job = args[0];
            Global.instance().getJobQueue().enqueue(() -> ScriptRuntime.call(job, ScriptRuntime.UNDEFINED, ScriptRuntime.EMPTY_ARRAY));
            return ScriptRuntime.UNDEFINED;
        case atob:
            if (args.length == 0) {
                throw typeError("atob: 1 argument required");
            }
            return decode(JSType.toString(args[0]));
        case btoa:
            if (args.length == 0) {
                throw typeError("btoa: 1 argument required");
            }
            return encode(JSType.toString(args[0]));
        default:
            throw new IllegalStateException(function.name());
        }
    }

    /** setTimeout / setInterval: the id, or 0 for a call with nothing to call, which cancels nothing. */
    private static Object schedule(final Timers timers, final Object[] args, final boolean repeating) {
        if (args.length == 0 || !Bootstrap.isCallable(args[0])) {
            return 0;
        }
        long delay = args.length > 1 ? JSType.toLong(args[1]) : 0;
        if (delay < 0) {
            delay = 0;
        }
        final Object[] extra = new Object[Math.max(0, args.length - 2)];
        if (extra.length > 0) {
            System.arraycopy(args, 2, extra, 0, extra.length);
        }
        final Entry entry = new Entry(timers.nextId++, args[0], extra, repeating ? delay : 0);
        timers.entries.put(entry.id, entry);
        arm(timers, entry, delay, Global.instance().getJobQueue());
        return entry.id;
    }

    private static void arm(final Timers timers, final Entry entry, final long delay, final JobQueue queue) {
        entry.handle = queue.schedule(() -> fire(timers, entry, queue), delay);
    }

    private static void fire(final Timers timers, final Entry entry, final JobQueue queue) {
        if (entry.cancelled) {
            return;
        }
        try {
            ScriptRuntime.call(entry.callback, ScriptRuntime.UNDEFINED, entry.arguments);
        } finally {
            if (entry.intervalMillis > 0 && !entry.cancelled) {
                arm(timers, entry, entry.intervalMillis, queue);
            } else {
                timers.entries.remove(entry.id);
            }
        }
    }

    private static void cancel(final Timers timers, final Object id) {
        if (JSType.nullOrUndefined(id)) {
            return;
        }
        final Entry entry = timers.entries.remove(JSType.toInt32(id));
        if (entry != null) {
            entry.cancelled = true;
            if (entry.handle != null) {
                Global.instance().getJobQueue().cancel(entry.handle);
            }
        }
    }

    /** btoa: a binary string - every char code 0 to 255 - as Base64. */
    private static String encode(final String input) {
        final byte[] bytes = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c > 0xFF) {
                throw error("btoa: the string to be encoded contains characters outside of the Latin1 range");
            }
            bytes[i] = (byte)c;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** atob: forgiving Base64 - whitespace dropped, missing padding tolerated - to a binary string. */
    private static String decode(final String encoded) {
        String stripped = encoded.replaceAll("[\\t\\n\\f\\r ]", "");
        final int rest = stripped.length() % 4;
        if (rest == 1) {
            throw error("atob: the string to be decoded is not correctly encoded");
        }
        if (rest == 2) {
            stripped += "==";
        } else if (rest == 3) {
            stripped += "=";
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stripped);
        } catch (final IllegalArgumentException e) {
            throw error("atob: the string to be decoded is not correctly encoded");
        }
        final char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char)(bytes[i] & 0xFF);
        }
        return new String(chars);
    }
}
