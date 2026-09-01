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

package org.monflabs.nashorn.libs;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.EventLoop;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptUtils;

/**
 * The {@code host} library: what a script expects from its host beyond the
 * language and is not in ECMAScript - the WHATWG timers ({@code setTimeout},
 * {@code clearTimeout}, {@code setInterval}, {@code clearInterval}),
 * {@code queueMicrotask}, and the Base64 pair {@code atob}/{@code btoa}.
 *
 * <p>Timers run on the realm's {@link EventLoop}: a callback runs on the
 * script's thread once its delay has passed and the script is between turns,
 * and an {@code eval} that scheduled one returns when the last has fired. Each
 * global has timer ids and pending timers of its own.
 *
 * <p>One class, one function object per entry of {@link Function}, a switch.
 *
 * @since 2017.0.0
 */
public final class HostLibrary implements ScriptLibrary {

    /** The functions, by the names they take in the global. */
    enum Function {
        setTimeout, clearTimeout, setInterval, clearInterval, queueMicrotask, atob, btoa
    }

    @Override
    public String name() {
        return "host";
    }

    @Override
    public Map<String, Object> globals() {
        // called once per global, so every global gets a timer table of its own
        final Timers timers = new Timers();
        final Map<String, Object> globals = new LinkedHashMap<>();
        for (final Function function : Function.values()) {
            globals.put(function.name(), new Method(function, timers));
        }
        return globals;
    }

    /** A global's timers: ids handed out, and the ones still to fire. Loop thread only. */
    private static final class Timers {
        final AtomicInteger nextId = new AtomicInteger(1);
        final Map<Integer, Entry> entries = new HashMap<>();
    }

    /** One timer. */
    private static final class Entry {
        final int id;
        final JSObject callback;
        final Object[] arguments;
        final long intervalMillis;   // 0 for a one-shot
        EventLoop.Timer timer;
        boolean cancelled;

        Entry(final int id, final JSObject callback, final Object[] arguments, final long intervalMillis) {
            this.id = id;
            this.callback = callback;
            this.arguments = arguments;
            this.intervalMillis = intervalMillis;
        }
    }

    /** One of the library's functions. */
    private static final class Method extends AbstractJSObject {
        private final Function function;
        private final Timers timers;

        Method(final Function function, final Timers timers) {
            this.function = function;
            this.timers = timers;
        }

        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object getMember(final String name) {
            return "name".equals(name) ? function.name() : super.getMember(name);
        }

        @Override
        public Object call(final Object thiz, final Object... args) {
            switch (function) {
            case setTimeout:
                return schedule(args, false);
            case setInterval:
                return schedule(args, true);
            case clearTimeout:
            case clearInterval:
                // the two are interchangeable, as the HTML specification says
                cancel(args.length > 0 ? args[0] : ScriptUtils.undefined());
                return ScriptUtils.undefined();
            case queueMicrotask:
                if (args.length == 0 || !ScriptUtils.isCallable(args[0])) {
                    throw ScriptUtils.typeError("queueMicrotask: the argument is not a function");
                }
                final JSObject job = (JSObject)args[0];
                EventLoop.current().queueMicrotask(() -> job.call(ScriptUtils.undefined()));
                return ScriptUtils.undefined();
            case atob:
                if (args.length == 0) {
                    throw ScriptUtils.typeError("atob: 1 argument required");
                }
                return decode(ScriptUtils.toString(args[0]));
            case btoa:
                if (args.length == 0) {
                    throw ScriptUtils.typeError("btoa: 1 argument required");
                }
                return encode(ScriptUtils.toString(args[0]));
            default:
                throw new IllegalStateException(function.name());
            }
        }

        /** setTimeout / setInterval: the id, or 0 for a call with nothing to call, which cancels nothing. */
        private Object schedule(final Object[] args, final boolean repeating) {
            if (args.length == 0 || !ScriptUtils.isCallable(args[0])) {
                return 0;
            }
            final JSObject callback = (JSObject)args[0];
            long delay = args.length > 1 ? ScriptUtils.toLong(args[1]) : 0;
            if (delay < 0) {
                delay = 0;
            }
            final Object[] extra = new Object[Math.max(0, args.length - 2)];
            if (extra.length > 0) {
                System.arraycopy(args, 2, extra, 0, extra.length);
            }
            final Entry entry = new Entry(timers.nextId.getAndIncrement(), callback, extra, repeating ? delay : 0);
            timers.entries.put(entry.id, entry);
            arm(entry, delay, EventLoop.current());
            return entry.id;
        }

        private void arm(final Entry entry, final long delay, final EventLoop loop) {
            entry.timer = loop.schedule(() -> fire(entry, loop), delay);
        }

        private void fire(final Entry entry, final EventLoop loop) {
            if (entry.cancelled) {
                return;
            }
            try {
                entry.callback.call(ScriptUtils.undefined(), entry.arguments);
            } finally {
                if (entry.intervalMillis > 0 && !entry.cancelled) {
                    arm(entry, entry.intervalMillis, loop);
                } else {
                    timers.entries.remove(entry.id);
                }
            }
        }

        private void cancel(final Object id) {
            if (ScriptUtils.isNullOrUndefined(id)) {
                return;
            }
            final Entry entry = timers.entries.remove(ScriptUtils.toInt32(id));
            if (entry != null) {
                entry.cancelled = true;
                if (entry.timer != null) {
                    entry.timer.cancel();
                }
            }
        }

        /** btoa: a binary string - every char code 0 to 255 - as Base64. */
        private static String encode(final String input) {
            final byte[] bytes = new byte[input.length()];
            for (int i = 0; i < input.length(); i++) {
                final char c = input.charAt(i);
                if (c > 0xFF) {
                    throw ScriptUtils.error("btoa: the string to be encoded contains characters outside of the Latin1 range");
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
                throw ScriptUtils.error("atob: the string to be decoded is not correctly encoded");
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
                throw ScriptUtils.error("atob: the string to be decoded is not correctly encoded");
            }
            final char[] chars = new char[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                chars[i] = (char)(bytes[i] & 0xFF);
            }
            return new String(chars);
        }
    }
}
