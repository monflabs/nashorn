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

package org.monflabs.nashorn.modules.node;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * A Java implementation of Node's {@code os} module, exposed by the
 * {@link NodeModuleLoader} as {@code import os from "os"}. Everything in Node's
 * {@code os} is synchronous, so these are plain functions and values over the
 * JVM's own facilities: {@link System} properties, {@link Runtime},
 * {@link NetworkInterface}, and the operating-system MX bean.
 *
 * <p>A few values are necessarily approximations of what Node reports on a real
 * OS: {@code uptime()} is the JVM's uptime, {@code cpus()} reports the processor
 * count with best-effort model and timing, and {@code loadavg()} carries the
 * one-minute system load in all three slots where the platform exposes only that.
 *
 * @since 2017.0.0
 */
public final class NodeOs {

    private NodeOs() {
    }

    /**
     * The {@code os} module's exports: {@code "default"} is the namespace, and
     * every function and value is also a named export.
     * @return the export map
     */
    public static Map<String, Object> exports() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("EOL", System.lineSeparator());
        m.put("devNull", isWindows() ? "\\\\.\\nul" : "/dev/null");
        put(m, "arch", NodeOs::arch);
        put(m, "platform", NodeOs::platform);
        put(m, "type", NodeOs::type);
        put(m, "release", () -> prop("os.version"));
        put(m, "version", () -> prop("os.name") + " " + prop("os.version"));
        put(m, "machine", () -> prop("os.arch"));
        put(m, "hostname", NodeOs::hostname);
        put(m, "homedir", () -> prop("user.home"));
        put(m, "tmpdir", () -> stripSlash(prop("java.io.tmpdir")));
        put(m, "endianness", () -> ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? "BE" : "LE");
        put(m, "availableParallelism", () -> Runtime.getRuntime().availableProcessors());
        put(m, "uptime", () -> ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        put(m, "totalmem", () -> (double)osBeanLong("getTotalMemorySize", "getTotalPhysicalMemorySize"));
        put(m, "freemem", () -> (double)osBeanLong("getFreeMemorySize", "getFreePhysicalMemorySize"));
        put(m, "loadavg", NodeOs::loadavg);
        put(m, "cpus", NodeOs::cpus);
        put(m, "networkInterfaces", NodeOs::networkInterfaces);
        put(m, "userInfo", NodeOs::userInfo);
        put(m, "getPriority", () -> 0);
        m.put("constants", constants());

        final Map<String, Object> exports = new LinkedHashMap<>(m);
        exports.put("default", namespace(m));
        return exports;
    }

    // ---- values ----

    private static Object arch() {
        final String a = prop("os.arch").toLowerCase(Locale.ROOT);
        if (a.equals("amd64") || a.equals("x86_64")) { return "x64"; }
        if (a.equals("aarch64")) { return "arm64"; }
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) { return "ia32"; }
        if (a.startsWith("arm")) { return "arm"; }
        if (a.startsWith("ppc64")) { return "ppc64"; }
        if (a.startsWith("s390")) { return "s390x"; }
        return a;
    }

    private static Object platform() {
        final String n = prop("os.name").toLowerCase(Locale.ROOT);
        if (n.contains("win")) { return "win32"; }
        if (n.contains("mac") || n.contains("darwin")) { return "darwin"; }
        if (n.contains("linux")) { return "linux"; }
        if (n.contains("sunos") || n.contains("solaris")) { return "sunos"; }
        if (n.contains("aix")) { return "aix"; }
        if (n.contains("freebsd")) { return "freebsd"; }
        if (n.contains("openbsd")) { return "openbsd"; }
        return n.replace(' ', '_');
    }

    private static Object type() {
        final String n = prop("os.name").toLowerCase(Locale.ROOT);
        if (n.contains("win")) { return "Windows_NT"; }
        if (n.contains("mac") || n.contains("darwin")) { return "Darwin"; }
        if (n.contains("linux")) { return "Linux"; }
        return prop("os.name");
    }

    private static Object hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final Exception e) {
            final String env = System.getenv(isWindows() ? "COMPUTERNAME" : "HOSTNAME");
            return env != null ? env : "localhost";
        }
    }

    private static Object loadavg() {
        final double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        final double v = load < 0 ? 0 : load;
        return Global.instance().wrapAsObject(new Object[] {v, v, v});
    }

    private static Object cpus() {
        final int n = Runtime.getRuntime().availableProcessors();
        final String model = prop("os.name") + " " + prop("os.arch");
        final Object[] out = new Object[n];
        for (int i = 0; i < n; i++) {
            final Map<String, Object> cpu = new LinkedHashMap<>();
            cpu.put("model", model);
            cpu.put("speed", 0);
            final Map<String, Object> times = new LinkedHashMap<>();
            times.put("user", 0.0);
            times.put("nice", 0.0);
            times.put("sys", 0.0);
            times.put("idle", 0.0);
            times.put("irq", 0.0);
            cpu.put("times", namespace(times));
            out[i] = namespace(cpu);
        }
        return Global.instance().wrapAsObject(out);
    }

    private static Object userInfo() {
        final Map<String, Object> u = new LinkedHashMap<>();
        u.put("username", prop("user.name"));
        u.put("homedir", prop("user.home"));
        final String shell = System.getenv("SHELL");
        u.put("shell", isWindows() || shell == null ? null : shell);
        u.put("uid", -1);
        u.put("gid", -1);
        return namespace(u);
    }

    private static Object networkInterfaces() {
        final Map<String, Object> result = new LinkedHashMap<>();
        try {
            for (final NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                final List<Object> addrs = new ArrayList<>();
                for (final InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    final InetAddress a = ia.getAddress();
                    if (a == null) {
                        continue;
                    }
                    final boolean v6 = a.getAddress().length == 16;
                    final Map<String, Object> e = new LinkedHashMap<>();
                    e.put("address", a.getHostAddress().split("%")[0]);
                    e.put("netmask", netmask(ia.getNetworkPrefixLength(), v6));
                    e.put("family", v6 ? "IPv6" : "IPv4");
                    e.put("mac", mac(nif));
                    e.put("internal", a.isLoopbackAddress());
                    e.put("cidr", a.getHostAddress().split("%")[0] + "/" + ia.getNetworkPrefixLength());
                    if (v6) {
                        e.put("scopeid", 0);
                    }
                    addrs.add(namespace(e));
                }
                if (!addrs.isEmpty()) {
                    result.put(nif.getName(), Global.instance().wrapAsObject(addrs.toArray()));
                }
            }
        } catch (final Exception ignored) {
            // no interfaces enumerable
        }
        return namespace(result);
    }

    private static Object constants() {
        final Map<String, Object> signals = new LinkedHashMap<>();
        final String[][] sig = {{"SIGHUP", "1"}, {"SIGINT", "2"}, {"SIGQUIT", "3"}, {"SIGILL", "4"},
            {"SIGABRT", "6"}, {"SIGKILL", "9"}, {"SIGSEGV", "11"}, {"SIGPIPE", "13"}, {"SIGALRM", "14"},
            {"SIGTERM", "15"}, {"SIGCHLD", "17"}, {"SIGCONT", "18"}, {"SIGSTOP", "19"}, {"SIGUSR1", "10"}, {"SIGUSR2", "12"}};
        for (final String[] s : sig) {
            signals.put(s[0], Integer.parseInt(s[1]));
        }
        final Map<String, Object> priority = new LinkedHashMap<>();
        priority.put("PRIORITY_LOW", 19);
        priority.put("PRIORITY_BELOW_NORMAL", 10);
        priority.put("PRIORITY_NORMAL", 0);
        priority.put("PRIORITY_ABOVE_NORMAL", -7);
        priority.put("PRIORITY_HIGH", -14);
        priority.put("PRIORITY_HIGHEST", -20);
        final Map<String, Object> c = new LinkedHashMap<>();
        c.put("signals", namespace(signals));
        c.put("priority", namespace(priority));
        return namespace(c);
    }

    // ---- helpers ----

    private static void put(final Map<String, Object> m, final String name, final Supplier<Object> impl) {
        m.put(name, new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return impl.get();
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:os] }";
            }
        });
    }

    private static JSObject namespace(final Map<String, Object> members) {
        return new AbstractJSObject() {
            @Override
            public Object getMember(final String name) {
                return members.containsKey(name) ? members.get(name) : super.getMember(name);
            }

            @Override
            public boolean hasMember(final String name) {
                return members.containsKey(name);
            }

            @Override
            public java.util.Set<String> keySet() {
                return members.keySet();
            }
        };
    }

    private static String prop(final String key) {
        final String v = System.getProperty(key);
        return v == null ? "" : v;
    }

    private static String stripSlash(final String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static boolean isWindows() {
        return prop("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static long osBeanLong(final String... methods) {
        final Object bean = ManagementFactory.getOperatingSystemMXBean();
        for (final String name : methods) {
            try {
                final Object v = bean.getClass().getMethod(name).invoke(bean);
                if (v instanceof Number n) {
                    return n.longValue();
                }
            } catch (final ReflectiveOperationException | RuntimeException next) {
                // try the next spelling, or give up
            }
        }
        return 0;
    }

    private static String mac(final NetworkInterface nif) {
        try {
            final byte[] hw = nif.getHardwareAddress();
            if (hw == null) {
                return "00:00:00:00:00:00";
            }
            final StringBuilder sb = new StringBuilder();
            for (final byte b : hw) {
                sb.append(sb.length() == 0 ? "" : ":").append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final Exception e) {
            return "00:00:00:00:00:00";
        }
    }

    private static String netmask(final int prefix, final boolean v6) {
        if (v6) {
            final StringBuilder sb = new StringBuilder();
            int bits = prefix;
            for (int g = 0; g < 8; g++) {
                final int take = Math.max(0, Math.min(16, bits));
                sb.append(sb.length() == 0 ? "" : ":").append(Integer.toHexString((0xffff << (16 - take)) & 0xffff));
                bits -= 16;
            }
            return sb.toString();
        }
        final long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return ((mask >> 24) & 0xff) + "." + ((mask >> 16) & 0xff) + "." + ((mask >> 8) & 0xff) + "." + (mask & 0xff);
    }
}
