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

package org.monflabs.nashorn.internal.objects;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.debugger.DebuggerImpl;

/**
 * The {@code console} object, present when the engine runs with a debugger:
 * its functions print like {@code print} does - warnings and errors to the
 * error writer - and report every call to the debugger, which is how a
 * frontend shows them in its console. Not a nasgen class: it is a plain
 * object with builtin functions bound to one instance per global.
 */
public final class NativeConsole {
    private static final MethodType SIGNATURE = MethodType.methodType(Object.class, Object.class, Object[].class);

    private final Global global;
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Long> timers = new HashMap<>();
    private int groupDepth;

    private NativeConsole(final Global global) {
        this.global = global;
    }

    /**
     * Creates the console object of a global.
     * @param global the global
     * @return the console object
     */
    static ScriptObject create(final Global global) {
        final NativeConsole console = new NativeConsole(global);
        final ScriptObject object = global.newObject();
        for (final String name : new String[] {
                "log", "info", "debug", "trace", "warn", "error", "dir", "dirxml", "table", "assert",
                "count", "countReset", "time", "timeEnd", "timeLog", "group", "groupCollapsed", "groupEnd", "clear" }) {
            object.addOwnProperty(name, Attribute.NOT_ENUMERABLE, ScriptFunction.createBuiltin(name, handle(console, name)));
        }
        return object;
    }

    private static MethodHandle handle(final NativeConsole console, final String name) {
        try {
            return MethodHandles.lookup().findVirtual(NativeConsole.class, "assert".equals(name) ? "assert$" : name, SIGNATURE).bindTo(console);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError(name, e);
        }
    }

    // -- the functions; each is (self, args...) as a builtin wants ---------------

    Object log(final Object self, final Object... args) {
        return emit("log", false, format(args), args);
    }

    Object info(final Object self, final Object... args) {
        return emit("info", false, format(args), args);
    }

    Object debug(final Object self, final Object... args) {
        return emit("debug", false, format(args), args);
    }

    Object trace(final Object self, final Object... args) {
        return emit("trace", true, "Trace: " + format(args), args);
    }

    Object warn(final Object self, final Object... args) {
        return emit("warning", true, format(args), args);
    }

    Object error(final Object self, final Object... args) {
        return emit("error", true, format(args), args);
    }

    Object dir(final Object self, final Object... args) {
        return emit("dir", false, format(args), args);
    }

    Object dirxml(final Object self, final Object... args) {
        return emit("dirxml", false, format(args), args);
    }

    Object table(final Object self, final Object... args) {
        return emit("table", false, format(args), args);
    }

    Object assert$(final Object self, final Object... args) {
        return assertion(args);
    }

    // "assert" is a Java keyword; the builtin is named through the table above
    private Object assertion(final Object... args) {
        if (args.length > 0 && JSType.toBoolean(args[0])) {
            return ScriptRuntime.UNDEFINED;
        }
        final Object[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new Object[0];
        final String text = rest.length == 0 ? "Assertion failed" : "Assertion failed: " + format(rest);
        return emit("assert", true, text, rest);
    }

    Object count(final Object self, final Object... args) {
        final String label = label(args);
        final int n = counters.merge(label, 1, Integer::sum);
        return emit("count", false, label + ": " + n, new Object[] { label + ": " + n });
    }

    Object countReset(final Object self, final Object... args) {
        counters.remove(label(args));
        return ScriptRuntime.UNDEFINED;
    }

    Object time(final Object self, final Object... args) {
        timers.put(label(args), System.nanoTime());
        return ScriptRuntime.UNDEFINED;
    }

    Object timeEnd(final Object self, final Object... args) {
        return timeReport("timeEnd", label(args), true);
    }

    Object timeLog(final Object self, final Object... args) {
        return timeReport("timeLog", label(args), false);
    }

    private Object timeReport(final String type, final String label, final boolean end) {
        final Long start = end ? timers.remove(label) : timers.get(label);
        if (start == null) {
            return emit("warning", true, "Timer '" + label + "' does not exist", new Object[0]);
        }
        final String text = label + ": " + ((System.nanoTime() - start) / 1_000_000.0) + " ms";
        return emit(type, false, text, new Object[] { text });
    }

    Object group(final Object self, final Object... args) {
        emit("startGroup", false, format(args), args);
        groupDepth++;
        return ScriptRuntime.UNDEFINED;
    }

    Object groupCollapsed(final Object self, final Object... args) {
        emit("startGroupCollapsed", false, format(args), args);
        groupDepth++;
        return ScriptRuntime.UNDEFINED;
    }

    Object groupEnd(final Object self, final Object... args) {
        if (groupDepth > 0) {
            groupDepth--;
        }
        return emit("endGroup", false, null, new Object[0]);
    }

    Object clear(final Object self, final Object... args) {
        return emit("clear", false, null, new Object[0]);
    }

    // -- plumbing --------------------------------------------------------------

    private static String label(final Object[] args) {
        return args.length == 0 || args[0] == ScriptRuntime.UNDEFINED ? "default" : JSType.toString(args[0]);
    }

    private static String format(final Object[] args) {
        final StringBuilder sb = new StringBuilder();
        for (final Object arg : args) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(JSType.toString(arg));
        }
        return sb.toString();
    }

    private Object emit(final String type, final boolean error, final String text, final Object[] args) {
        if (text != null) {
            global.consolePrint(error, groupDepth == 0 ? text : "  ".repeat(groupDepth) + text);
        }
        final DebuggerImpl debugger = global.debuggerOf();
        if (debugger != null) {
            debugger.consoleCalled(type, args);
        }
        return ScriptRuntime.UNDEFINED;
    }
}
