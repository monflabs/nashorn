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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.Undefined;

/**
 * A Java implementation of Node's {@code path} module, exposed by the
 * {@link NodeModuleLoader} as {@code import path from "path"}. Everything in
 * Node's {@code path} is pure string manipulation, so this is a direct port of
 * Node's algorithms with no filesystem access.
 *
 * <p>Both platform flavours are provided: {@code path.posix} (forward-slash,
 * {@code :} delimiter) and {@code path.win32} (back-slash, drive letters and UNC
 * paths, {@code ;} delimiter). The default export - and the bare named exports
 * such as {@code join} - are the flavour matching the host operating system, as
 * in Node. Only {@code resolve} consults the process working directory
 * ({@code user.dir}); everything else depends solely on its arguments.
 *
 * @since 2017.0.0
 */
public final class NodePath {

    private static final int SLASH = '/';
    private static final int BSLASH = '\\';
    private static final int DOT = '.';
    private static final int COLON = ':';
    private static final int QUESTION = '?';

    private static final Impl POSIX = new Impl(false, "/", ":");
    private static final Impl WIN32 = new Impl(true, "\\", ";");

    private NodePath() {
    }

    /**
     * The {@code path} module's exports: {@code "default"} is the namespace for
     * the host platform, every function and value on it is also a named export,
     * and {@code posix} / {@code win32} expose the two flavours explicitly.
     * @return the export map
     */
    public static Map<String, Object> exports() {
        final Map<String, Object> posixM = POSIX.members();
        final Map<String, Object> win32M = WIN32.members();
        final JSObject posixNs = namespace(posixM);
        final JSObject win32Ns = namespace(win32M);
        // Each flavour reaches both, and itself, the way Node cross-links them.
        posixM.put("posix", posixNs);
        posixM.put("win32", win32Ns);
        win32M.put("posix", posixNs);
        win32M.put("win32", win32Ns);

        final boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        final Map<String, Object> def = isWindows ? win32M : posixM;
        final JSObject defNs = isWindows ? win32Ns : posixNs;

        final Map<String, Object> exports = new LinkedHashMap<>(def);
        exports.put("default", defNs);
        return exports;
    }

    // ---- one platform flavour ----

    private static final class Impl {
        private final boolean win32;
        private final String sep;
        private final String delimiter;

        Impl(final boolean win32, final String sep, final String delimiter) {
            this.win32 = win32;
            this.sep = sep;
            this.delimiter = delimiter;
        }

        Map<String, Object> members() {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("resolve", fn("resolve", this::resolve));
            m.put("normalize", fn("normalize", a -> normalize(str(arg(a, 0)))));
            m.put("isAbsolute", fn("isAbsolute", a -> isAbsolute(str(arg(a, 0)))));
            m.put("join", fn("join", this::join));
            m.put("relative", fn("relative", a -> relative(str(arg(a, 0)), str(arg(a, 1)))));
            m.put("toNamespacedPath", fn("toNamespacedPath", a -> toNamespacedPath(arg(a, 0))));
            m.put("dirname", fn("dirname", a -> dirname(str(arg(a, 0)))));
            m.put("basename", fn("basename", this::basename));
            m.put("extname", fn("extname", a -> extname(str(arg(a, 0)))));
            m.put("format", fn("format", a -> format(arg(a, 0))));
            m.put("parse", fn("parse", a -> parse(str(arg(a, 0)))));
            m.put("sep", sep);
            m.put("delimiter", delimiter);
            return m;
        }

        // ---- separators ----

        private boolean isSep(final int c) {
            return c == SLASH || (win32 && c == BSLASH);
        }

        // ---- the shared core: collapse '.' and '..' segments ----

        private String normalizeString(final String path, final boolean allowAboveRoot, final String separator) {
            final StringBuilder res = new StringBuilder();
            int lastSegmentLength = 0;
            int lastSlash = -1;
            int dots = 0;
            int code = 0;
            for (int i = 0; i <= path.length(); ++i) {
                if (i < path.length()) {
                    code = path.charAt(i);
                } else if (isSep(code)) {
                    break;
                } else {
                    code = SLASH;
                }
                if (isSep(code)) {
                    if (lastSlash == i - 1 || dots == 1) {
                        // NOOP: an empty segment or a single '.'.
                        lastSlash = i;
                        dots = 0;
                        continue;
                    } else if (dots == 2) {
                        if (res.length() < 2 || lastSegmentLength != 2
                                || res.charAt(res.length() - 1) != DOT
                                || res.charAt(res.length() - 2) != DOT) {
                            if (res.length() > 2) {
                                final int lastSlashIndex = res.lastIndexOf(separator);
                                if (lastSlashIndex == -1) {
                                    res.setLength(0);
                                    lastSegmentLength = 0;
                                } else {
                                    res.setLength(lastSlashIndex);
                                    lastSegmentLength = res.length() - 1 - res.lastIndexOf(separator);
                                }
                                lastSlash = i;
                                dots = 0;
                                continue;
                            } else if (res.length() != 0) {
                                res.setLength(0);
                                lastSegmentLength = 0;
                                lastSlash = i;
                                dots = 0;
                                continue;
                            }
                        }
                        if (allowAboveRoot) {
                            if (res.length() > 0) {
                                res.append(separator).append("..");
                            } else {
                                res.append("..");
                            }
                            lastSegmentLength = 2;
                        }
                    } else {
                        if (res.length() > 0) {
                            res.append(separator).append(path, lastSlash + 1, i);
                        } else {
                            res.append(path, lastSlash + 1, i);
                        }
                        lastSegmentLength = i - lastSlash - 1;
                    }
                    lastSlash = i;
                    dots = 0;
                } else if (code == DOT && dots != -1) {
                    ++dots;
                } else {
                    dots = -1;
                }
            }
            return res.toString();
        }

        // ---- resolve ----

        private Object resolve(final Object[] args) {
            if (win32) {
                return win32Resolve(args);
            }
            return posixResolve(args);
        }

        private String posixResolve(final Object[] args) {
            String resolvedPath = "";
            boolean resolvedAbsolute = false;
            for (int i = args.length - 1; i >= -1 && !resolvedAbsolute; i--) {
                final String path = i >= 0 ? str(args[i]) : cwd();
                if (path.isEmpty()) {
                    continue;
                }
                resolvedPath = path + "/" + resolvedPath;
                resolvedAbsolute = path.charAt(0) == SLASH;
            }
            resolvedPath = normalizeString(resolvedPath, !resolvedAbsolute, "/");
            if (resolvedAbsolute) {
                return "/" + resolvedPath;
            }
            return resolvedPath.length() > 0 ? resolvedPath : ".";
        }

        private String win32Resolve(final Object[] args) {
            String resolvedDevice = "";
            String resolvedTail = "";
            boolean resolvedAbsolute = false;
            for (int i = args.length - 1; i >= -1 && !resolvedAbsolute; i--) {
                String path;
                if (i >= 0) {
                    path = str(args[i]);
                    if (path.isEmpty()) {
                        continue;
                    }
                } else if (resolvedDevice.isEmpty()) {
                    path = cwd();
                } else {
                    // A real Windows keeps a per-drive working directory in the
                    // environment; that is not portable, so fall back to the root.
                    path = resolvedDevice + "\\";
                }
                final int len = path.length();
                int rootEnd = 0;
                String device = "";
                boolean isAbsolute = false;
                final int code = path.charAt(0);
                if (len == 1) {
                    if (isSep(code)) {
                        rootEnd = 1;
                        isAbsolute = true;
                    }
                } else if (isSep(code)) {
                    isAbsolute = true;
                    if (isSep(path.charAt(1))) {
                        int j = 2;
                        int last = j;
                        while (j < len && !isSep(path.charAt(j))) {
                            j++;
                        }
                        if (j < len && j != last) {
                            final String firstPart = path.substring(last, j);
                            last = j;
                            while (j < len && isSep(path.charAt(j))) {
                                j++;
                            }
                            if (j < len && j != last) {
                                last = j;
                                while (j < len && !isSep(path.charAt(j))) {
                                    j++;
                                }
                                if (j == len || j != last) {
                                    device = "\\\\" + firstPart + "\\" + path.substring(last, j);
                                    rootEnd = j;
                                }
                            }
                        }
                    } else {
                        rootEnd = 1;
                    }
                } else if (isWinDriveRoot(code) && path.charAt(1) == COLON) {
                    device = path.substring(0, 2);
                    rootEnd = 2;
                    if (len > 2 && isSep(path.charAt(2))) {
                        isAbsolute = true;
                        rootEnd = 3;
                    }
                }
                if (!device.isEmpty()) {
                    if (!resolvedDevice.isEmpty()) {
                        if (!device.equalsIgnoreCase(resolvedDevice)) {
                            continue;
                        }
                    } else {
                        resolvedDevice = device;
                    }
                }
                if (resolvedAbsolute) {
                    if (!resolvedDevice.isEmpty()) {
                        break;
                    }
                } else {
                    resolvedTail = path.substring(rootEnd) + "\\" + resolvedTail;
                    resolvedAbsolute = isAbsolute;
                    if (isAbsolute && !resolvedDevice.isEmpty()) {
                        break;
                    }
                }
            }
            resolvedTail = normalizeString(resolvedTail, !resolvedAbsolute, "\\");
            if (resolvedAbsolute) {
                return resolvedDevice + "\\" + resolvedTail;
            }
            final String out = resolvedDevice + resolvedTail;
            return out.isEmpty() ? "." : out;
        }

        // ---- normalize ----

        private String normalize(final String path) {
            return win32 ? win32Normalize(path) : posixNormalize(path);
        }

        private String posixNormalize(final String path) {
            if (path.isEmpty()) {
                return ".";
            }
            final boolean isAbsolute = path.charAt(0) == SLASH;
            final boolean trailingSeparator = path.charAt(path.length() - 1) == SLASH;
            String p = normalizeString(path, !isAbsolute, "/");
            if (p.isEmpty()) {
                if (isAbsolute) {
                    return "/";
                }
                return trailingSeparator ? "./" : ".";
            }
            if (trailingSeparator) {
                p += "/";
            }
            return isAbsolute ? "/" + p : p;
        }

        private String win32Normalize(final String path) {
            final int len = path.length();
            if (len == 0) {
                return ".";
            }
            int rootEnd = 0;
            String device = null;
            boolean isAbsolute = false;
            final int code = path.charAt(0);
            if (len == 1) {
                return code == SLASH ? "\\" : path;
            }
            if (isSep(code)) {
                isAbsolute = true;
                if (isSep(path.charAt(1))) {
                    int j = 2;
                    int last = j;
                    while (j < len && !isSep(path.charAt(j))) {
                        j++;
                    }
                    if (j < len && j != last) {
                        final String firstPart = path.substring(last, j);
                        last = j;
                        while (j < len && isSep(path.charAt(j))) {
                            j++;
                        }
                        if (j < len && j != last) {
                            last = j;
                            while (j < len && !isSep(path.charAt(j))) {
                                j++;
                            }
                            if (j == len) {
                                return "\\\\" + firstPart + "\\" + path.substring(last) + "\\";
                            }
                            if (j != last) {
                                device = "\\\\" + firstPart + "\\" + path.substring(last, j);
                                rootEnd = j;
                            }
                        }
                    }
                } else {
                    rootEnd = 1;
                }
            } else if (isWinDriveRoot(code) && path.charAt(1) == COLON) {
                device = path.substring(0, 2);
                rootEnd = 2;
                if (len > 2 && isSep(path.charAt(2))) {
                    isAbsolute = true;
                    rootEnd = 3;
                }
            }
            String tail = rootEnd < len ? normalizeString(path.substring(rootEnd), !isAbsolute, "\\") : "";
            if (tail.isEmpty() && !isAbsolute) {
                tail = ".";
            }
            if (!tail.isEmpty() && isSep(path.charAt(len - 1))) {
                tail += "\\";
            }
            if (device == null) {
                return isAbsolute ? "\\" + tail : tail;
            }
            return isAbsolute ? device + "\\" + tail : device + tail;
        }

        // ---- isAbsolute ----

        private Object isAbsolute(final String path) {
            final int len = path.length();
            if (len == 0) {
                return false;
            }
            final int code = path.charAt(0);
            if (win32) {
                return isSep(code)
                        || (len > 2 && isWinDriveRoot(code) && path.charAt(1) == COLON && isSep(path.charAt(2)));
            }
            return code == SLASH;
        }

        // ---- join ----

        private Object join(final Object[] args) {
            return win32 ? win32Join(args) : posixJoin(args);
        }

        private String posixJoin(final Object[] args) {
            if (args.length == 0) {
                return ".";
            }
            String joined = null;
            for (final Object o : args) {
                final String arg = str(o);
                if (!arg.isEmpty()) {
                    joined = joined == null ? arg : joined + "/" + arg;
                }
            }
            return joined == null ? "." : posixNormalize(joined);
        }

        private String win32Join(final Object[] args) {
            if (args.length == 0) {
                return ".";
            }
            String joined = null;
            String firstPart = null;
            for (final Object o : args) {
                final String arg = str(o);
                if (!arg.isEmpty()) {
                    if (joined == null) {
                        joined = arg;
                        firstPart = arg;
                    } else {
                        joined += "\\" + arg;
                    }
                }
            }
            if (joined == null) {
                return ".";
            }
            // Preserve exactly one leading UNC slash pair, as Node does.
            boolean needsReplace = true;
            int slashCount = 0;
            if (isSep(firstPart.charAt(0))) {
                ++slashCount;
                final int firstLen = firstPart.length();
                if (firstLen > 1 && isSep(firstPart.charAt(1))) {
                    ++slashCount;
                    if (firstLen > 2) {
                        if (isSep(firstPart.charAt(2))) {
                            ++slashCount;
                        } else {
                            needsReplace = false;
                        }
                    }
                }
            }
            if (needsReplace) {
                while (slashCount < joined.length() && isSep(joined.charAt(slashCount))) {
                    slashCount++;
                }
                if (slashCount >= 2) {
                    joined = "\\" + joined.substring(slashCount);
                }
            }
            return win32Normalize(joined);
        }

        // ---- relative ----

        private String relative(final String fromArg, final String toArg) {
            return win32 ? win32Relative(fromArg, toArg) : posixRelative(fromArg, toArg);
        }

        private String posixRelative(final String fromArg, final String toArg) {
            if (fromArg.equals(toArg)) {
                return "";
            }
            final String from = posixResolve(new Object[] {fromArg});
            final String to = posixResolve(new Object[] {toArg});
            if (from.equals(to)) {
                return "";
            }
            final int fromStart = 1;
            final int fromEnd = from.length();
            final int fromLen = fromEnd - fromStart;
            final int toStart = 1;
            final int toLen = to.length() - toStart;
            final int length = Math.min(fromLen, toLen);
            int lastCommonSep = -1;
            int i = 0;
            for (; i < length; i++) {
                final int fromCode = from.charAt(fromStart + i);
                if (fromCode != to.charAt(toStart + i)) {
                    break;
                } else if (fromCode == SLASH) {
                    lastCommonSep = i;
                }
            }
            if (i == length) {
                if (toLen > length) {
                    if (to.charAt(toStart + i) == SLASH) {
                        return to.substring(toStart + i + 1);
                    }
                    if (i == 0) {
                        return to.substring(toStart + i);
                    }
                } else if (fromLen > length) {
                    if (from.charAt(fromStart + i) == SLASH) {
                        lastCommonSep = i;
                    } else if (i == 0) {
                        lastCommonSep = 0;
                    }
                }
            }
            final StringBuilder out = new StringBuilder();
            for (i = fromStart + lastCommonSep + 1; i <= fromEnd; ++i) {
                if (i == fromEnd || from.charAt(i) == SLASH) {
                    out.append(out.length() == 0 ? ".." : "/..");
                }
            }
            return out + to.substring(toStart + lastCommonSep);
        }

        private String win32Relative(final String fromArg, final String toArg) {
            if (fromArg.equals(toArg)) {
                return "";
            }
            final String fromOrig = win32Resolve(new Object[] {fromArg});
            final String toOrig = win32Resolve(new Object[] {toArg});
            if (fromOrig.equalsIgnoreCase(toOrig)) {
                return "";
            }
            final String from = fromOrig.toLowerCase(Locale.ROOT);
            final String to = toOrig.toLowerCase(Locale.ROOT);
            if (from.equals(to)) {
                return "";
            }
            int fromStart = 0;
            while (fromStart < from.length() && from.charAt(fromStart) == BSLASH) {
                fromStart++;
            }
            int fromEnd = from.length();
            while (fromEnd - 1 > fromStart && from.charAt(fromEnd - 1) == BSLASH) {
                fromEnd--;
            }
            final int fromLen = fromEnd - fromStart;
            int toStart = 0;
            while (toStart < to.length() && to.charAt(toStart) == BSLASH) {
                toStart++;
            }
            int toEnd = to.length();
            while (toEnd - 1 > toStart && to.charAt(toEnd - 1) == BSLASH) {
                toEnd--;
            }
            final int toLen = toEnd - toStart;
            final int length = Math.min(fromLen, toLen);
            int lastCommonSep = -1;
            int i = 0;
            for (; i < length; i++) {
                final int fromCode = from.charAt(fromStart + i);
                if (fromCode != to.charAt(toStart + i)) {
                    break;
                } else if (fromCode == BSLASH) {
                    lastCommonSep = i;
                }
            }
            if (i != length) {
                if (lastCommonSep == -1) {
                    return toOrig;
                }
            } else {
                if (toLen > length) {
                    if (to.charAt(toStart + i) == BSLASH) {
                        return toOrig.substring(toStart + i + 1);
                    }
                    if (i == 2) {
                        return toOrig.substring(toStart + i);
                    }
                }
                if (fromLen > length) {
                    if (from.charAt(fromStart + i) == BSLASH) {
                        lastCommonSep = i;
                    } else if (i == 2) {
                        lastCommonSep = 3;
                    }
                }
                if (lastCommonSep == -1) {
                    lastCommonSep = 0;
                }
            }
            final StringBuilder out = new StringBuilder();
            for (i = fromStart + lastCommonSep + 1; i <= fromEnd; ++i) {
                if (i == fromEnd || from.charAt(i) == BSLASH) {
                    out.append(out.length() == 0 ? ".." : "\\..");
                }
            }
            toStart += lastCommonSep;
            if (out.length() > 0) {
                return out + toOrig.substring(toStart, toEnd);
            }
            if (toOrig.charAt(toStart) == BSLASH) {
                ++toStart;
            }
            return toOrig.substring(toStart, toEnd);
        }

        // ---- toNamespacedPath ----

        private Object toNamespacedPath(final Object pathArg) {
            if (!win32) {
                return pathArg;
            }
            if (!(pathArg instanceof CharSequence)) {
                return pathArg;
            }
            final String path = pathArg.toString();
            if (path.isEmpty()) {
                return path;
            }
            final String resolvedPath = win32Resolve(new Object[] {path});
            if (resolvedPath.length() <= 2) {
                return path;
            }
            if (resolvedPath.charAt(0) == BSLASH) {
                if (resolvedPath.charAt(1) == BSLASH) {
                    final int code = resolvedPath.charAt(2);
                    if (code != QUESTION && code != DOT) {
                        return "\\\\?\\UNC\\" + resolvedPath.substring(2);
                    }
                }
            } else if (isWinDriveRoot(resolvedPath.charAt(0)) && resolvedPath.charAt(1) == COLON
                    && resolvedPath.charAt(2) == BSLASH) {
                return "\\\\?\\" + resolvedPath;
            }
            return path;
        }

        // ---- dirname ----

        private String dirname(final String path) {
            return win32 ? win32Dirname(path) : posixDirname(path);
        }

        private String posixDirname(final String path) {
            if (path.isEmpty()) {
                return ".";
            }
            final boolean hasRoot = path.charAt(0) == SLASH;
            int end = -1;
            boolean matchedSlash = true;
            for (int i = path.length() - 1; i >= 1; --i) {
                if (path.charAt(i) == SLASH) {
                    if (!matchedSlash) {
                        end = i;
                        break;
                    }
                } else {
                    matchedSlash = false;
                }
            }
            if (end == -1) {
                return hasRoot ? "/" : ".";
            }
            if (hasRoot && end == 1) {
                return "//";
            }
            return path.substring(0, end);
        }

        private String win32Dirname(final String path) {
            final int len = path.length();
            if (len == 0) {
                return ".";
            }
            int rootEnd = -1;
            int offset = 0;
            final int code = path.charAt(0);
            if (len == 1) {
                return isSep(code) ? path : ".";
            }
            if (isSep(code)) {
                rootEnd = 1;
                offset = 1;
                if (isSep(path.charAt(1))) {
                    int j = 2;
                    int last = j;
                    while (j < len && !isSep(path.charAt(j))) {
                        j++;
                    }
                    if (j < len && j != last) {
                        last = j;
                        while (j < len && isSep(path.charAt(j))) {
                            j++;
                        }
                        if (j < len && j != last) {
                            last = j;
                            while (j < len && !isSep(path.charAt(j))) {
                                j++;
                            }
                            if (j == len) {
                                return path;
                            }
                            if (j != last) {
                                rootEnd = j + 1;
                                offset = j + 1;
                            }
                        }
                    }
                }
            } else if (isWinDriveRoot(code) && path.charAt(1) == COLON) {
                rootEnd = len > 2 && isSep(path.charAt(2)) ? 3 : 2;
                offset = rootEnd;
            }
            int end = -1;
            boolean matchedSlash = true;
            for (int i = len - 1; i >= offset; --i) {
                if (isSep(path.charAt(i))) {
                    if (!matchedSlash) {
                        end = i;
                        break;
                    }
                } else {
                    matchedSlash = false;
                }
            }
            if (end == -1) {
                if (rootEnd == -1) {
                    return ".";
                }
                end = rootEnd;
            }
            return path.substring(0, end);
        }

        // ---- basename ----

        private Object basename(final Object[] args) {
            final String path = str(arg(args, 0));
            final Object suffixArg = args.length > 1 ? args[1] : null;
            final String suffix = suffixArg == null || isUndefined(suffixArg) ? null : str(suffixArg);
            int start = 0;
            int end = -1;
            boolean matchedSlash = true;
            if (win32 && path.length() >= 2 && isWinDriveRoot(path.charAt(0)) && path.charAt(1) == COLON) {
                start = 2;
            }
            if (suffix != null && suffix.length() > 0 && suffix.length() <= path.length()) {
                if (suffix.equals(path)) {
                    return "";
                }
                int extIdx = suffix.length() - 1;
                int firstNonSlashEnd = -1;
                for (int i = path.length() - 1; i >= start; --i) {
                    final int code = path.charAt(i);
                    if (isSep(code)) {
                        if (!matchedSlash) {
                            start = i + 1;
                            break;
                        }
                    } else {
                        if (firstNonSlashEnd == -1) {
                            matchedSlash = false;
                            firstNonSlashEnd = i + 1;
                        }
                        if (extIdx >= 0) {
                            if (code == suffix.charAt(extIdx)) {
                                if (--extIdx == -1) {
                                    end = i;
                                }
                            } else {
                                extIdx = -1;
                                end = firstNonSlashEnd;
                            }
                        }
                    }
                }
                if (start == end) {
                    end = firstNonSlashEnd;
                } else if (end == -1) {
                    end = path.length();
                }
                return path.substring(start, end);
            }
            for (int i = path.length() - 1; i >= start; --i) {
                if (isSep(path.charAt(i))) {
                    if (!matchedSlash) {
                        start = i + 1;
                        break;
                    }
                } else if (end == -1) {
                    matchedSlash = false;
                    end = i + 1;
                }
            }
            if (end == -1) {
                return "";
            }
            return path.substring(start, end);
        }

        // ---- extname ----

        private String extname(final String path) {
            int start = 0;
            int startDot = -1;
            int startPart = 0;
            int end = -1;
            boolean matchedSlash = true;
            int preDotState = 0;
            if (win32 && path.length() >= 2 && path.charAt(1) == COLON && isWinDriveRoot(path.charAt(0))) {
                start = 2;
                startPart = 2;
            }
            for (int i = path.length() - 1; i >= start; --i) {
                final int code = path.charAt(i);
                if (isSep(code)) {
                    if (!matchedSlash) {
                        startPart = i + 1;
                        break;
                    }
                    continue;
                }
                if (end == -1) {
                    matchedSlash = false;
                    end = i + 1;
                }
                if (code == DOT) {
                    if (startDot == -1) {
                        startDot = i;
                    } else if (preDotState != 1) {
                        preDotState = 1;
                    }
                } else if (startDot != -1) {
                    preDotState = -1;
                }
            }
            if (startDot == -1 || end == -1 || preDotState == 0
                    || (preDotState == 1 && startDot == end - 1 && startDot == startPart + 1)) {
                return "";
            }
            return path.substring(startDot, end);
        }

        // ---- format ----

        private String format(final Object pathObject) {
            final String dir = firstNonEmpty(member(pathObject, "dir"), member(pathObject, "root"));
            final String base = coalesce(member(pathObject, "base"),
                    optStr(member(pathObject, "name")) + optStr(member(pathObject, "ext")));
            if (dir.isEmpty()) {
                return base;
            }
            final String root = optStr(member(pathObject, "root"));
            if (dir.equals(root)) {
                return dir + base;
            }
            return dir + sep + base;
        }

        // ---- parse ----

        private Object parse(final String path) {
            final Map<String, Object> ret = new LinkedHashMap<>();
            ret.put("root", "");
            ret.put("dir", "");
            ret.put("base", "");
            ret.put("ext", "");
            ret.put("name", "");
            if (path.isEmpty()) {
                return namespace(ret);
            }
            if (win32) {
                win32Parse(path, ret);
            } else {
                posixParse(path, ret);
            }
            return namespace(ret);
        }

        private void posixParse(final String path, final Map<String, Object> ret) {
            final boolean isAbsolute = path.charAt(0) == SLASH;
            final int start;
            if (isAbsolute) {
                ret.put("root", "/");
                start = 1;
            } else {
                start = 0;
            }
            int startDot = -1;
            int startPart = 0;
            int end = -1;
            boolean matchedSlash = true;
            int i = path.length() - 1;
            int preDotState = 0;
            for (; i >= start; --i) {
                final int code = path.charAt(i);
                if (code == SLASH) {
                    if (!matchedSlash) {
                        startPart = i + 1;
                        break;
                    }
                    continue;
                }
                if (end == -1) {
                    matchedSlash = false;
                    end = i + 1;
                }
                if (code == DOT) {
                    if (startDot == -1) {
                        startDot = i;
                    } else if (preDotState != 1) {
                        preDotState = 1;
                    }
                } else if (startDot != -1) {
                    preDotState = -1;
                }
            }
            if (end != -1) {
                final int st = startPart == 0 && isAbsolute ? 1 : startPart;
                if (startDot == -1 || preDotState == 0
                        || (preDotState == 1 && startDot == end - 1 && startDot == startPart + 1)) {
                    ret.put("base", path.substring(st, end));
                    ret.put("name", path.substring(st, end));
                } else {
                    ret.put("name", path.substring(st, startDot));
                    ret.put("base", path.substring(st, end));
                    ret.put("ext", path.substring(startDot, end));
                }
            }
            if (startPart > 0) {
                ret.put("dir", path.substring(0, startPart - 1));
            } else if (isAbsolute) {
                ret.put("dir", "/");
            }
        }

        private void win32Parse(final String path, final Map<String, Object> ret) {
            final int len = path.length();
            int rootEnd = 0;
            int code = path.charAt(0);
            if (len == 1) {
                if (isSep(code)) {
                    ret.put("root", path);
                    ret.put("dir", path);
                } else {
                    ret.put("base", path);
                    ret.put("name", path);
                }
                return;
            }
            if (isSep(code)) {
                rootEnd = 1;
                if (isSep(path.charAt(1))) {
                    int j = 2;
                    int last = j;
                    while (j < len && !isSep(path.charAt(j))) {
                        j++;
                    }
                    if (j < len && j != last) {
                        last = j;
                        while (j < len && isSep(path.charAt(j))) {
                            j++;
                        }
                        if (j < len && j != last) {
                            last = j;
                            while (j < len && !isSep(path.charAt(j))) {
                                j++;
                            }
                            if (j == len) {
                                rootEnd = j;
                            } else if (j != last) {
                                rootEnd = j + 1;
                            }
                        }
                    }
                }
            } else if (isWinDriveRoot(code) && path.charAt(1) == COLON) {
                if (len <= 2) {
                    ret.put("root", path);
                    ret.put("dir", path);
                    return;
                }
                rootEnd = 2;
                if (isSep(path.charAt(2))) {
                    if (len == 3) {
                        ret.put("root", path);
                        ret.put("dir", path);
                        return;
                    }
                    rootEnd = 3;
                }
            }
            if (rootEnd > 0) {
                ret.put("root", path.substring(0, rootEnd));
            }
            int startDot = -1;
            int startPart = rootEnd;
            int end = -1;
            boolean matchedSlash = true;
            int i = len - 1;
            int preDotState = 0;
            for (; i >= rootEnd; --i) {
                code = path.charAt(i);
                if (isSep(code)) {
                    if (!matchedSlash) {
                        startPart = i + 1;
                        break;
                    }
                    continue;
                }
                if (end == -1) {
                    matchedSlash = false;
                    end = i + 1;
                }
                if (code == DOT) {
                    if (startDot == -1) {
                        startDot = i;
                    } else if (preDotState != 1) {
                        preDotState = 1;
                    }
                } else if (startDot != -1) {
                    preDotState = -1;
                }
            }
            if (end != -1) {
                if (startDot == -1 || preDotState == 0
                        || (preDotState == 1 && startDot == end - 1 && startDot == startPart + 1)) {
                    ret.put("base", path.substring(startPart, end));
                    ret.put("name", path.substring(startPart, end));
                } else {
                    ret.put("name", path.substring(startPart, startDot));
                    ret.put("base", path.substring(startPart, end));
                    ret.put("ext", path.substring(startDot, end));
                }
            }
            if (startPart > 0 && startPart != rootEnd) {
                ret.put("dir", path.substring(0, startPart - 1));
            } else {
                ret.put("dir", ret.get("root"));
            }
        }
    }

    // ---- shared helpers ----

    private static boolean isWinDriveRoot(final int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static String cwd() {
        final String d = System.getProperty("user.dir");
        return d == null ? "/" : d;
    }

    private static Object arg(final Object[] args, final int i) {
        return i < args.length ? args[i] : Undefined.getUndefined();
    }

    private static boolean isUndefined(final Object o) {
        return o == null || o instanceof Undefined;
    }

    /** Node's {@code validateString}: a non-string argument is a TypeError. */
    private static String str(final Object o) {
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof CharSequence c) {
            return c.toString();
        }
        throw ScriptUtils.typeError("The \"path\" argument must be of type string.");
    }

    private static Object member(final Object obj, final String key) {
        final Object unwrapped = ScriptUtils.unwrap(obj);
        Object v = null;
        if (unwrapped instanceof ScriptObject so) {
            v = so.get(key);
        } else if (unwrapped instanceof JSObject j) {
            v = j.getMember(key);
        } else if (unwrapped instanceof Map<?, ?> m) {
            v = m.get(key);
        }
        return isUndefined(v) ? null : v;
    }

    private static String optStr(final Object o) {
        return o == null ? "" : o.toString();
    }

    private static String firstNonEmpty(final Object a, final Object b) {
        final String s = optStr(a);
        return s.isEmpty() ? optStr(b) : s;
    }

    private static String coalesce(final Object a, final String fallback) {
        final String s = optStr(a);
        return s.isEmpty() ? fallback : s;
    }

    @FunctionalInterface
    private interface Fn {
        Object apply(Object[] args);
    }

    private static JSObject fn(final String name, final Fn f) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return f.apply(args);
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:path] }";
            }
        };
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
            public Set<String> keySet() {
                return members.keySet();
            }
        };
    }
}
