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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.objects.NativePromise;
import org.monflabs.nashorn.internal.runtime.JobQueue;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * A Java implementation of Node's {@code fs} module, exposed by the
 * {@link NodeModuleLoader}. Every operation comes in the three Node shapes:
 * synchronous ({@code readFileSync}), asynchronous with a Node-style
 * {@code (err, result)} callback ({@code readFile}), and promise-returning
 * ({@code fs.promises.readFile}). The async and promise forms run the I/O on a
 * background thread and deliver the result on the realm's event loop, so a
 * script that awaits them keeps {@code eval} alive until they settle.
 *
 * <p>Files are read and written as text: {@code readFile} returns a string,
 * UTF-8 by default or the encoding given in the options; pass {@code "latin1"}
 * for a byte-preserving round trip. (The engine has no Node {@code Buffer}
 * type.) Errors carry a Node {@code code} such as {@code "ENOENT"}.
 *
 * @since 2017.0.0
 */
public final class NodeFs {

    private static final ExecutorService IO = Executors.newCachedThreadPool(r -> {
        final Thread t = new Thread(r, "node-fs");
        t.setDaemon(true);
        return t;
    });

    private NodeFs() {
    }

    /** One file-system operation, taking the already-stripped data arguments. */
    @FunctionalInterface
    private interface Op {
        Object run(Object[] args) throws IOException;
    }

    /** A checked failure carrying a Node error code. */
    private static final class FsError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String code;
        FsError(final String code, final String message) {
            super(message);
            this.code = code;
        }
    }

    // ---- the module's exports ----

    /**
     * The {@code fs} module's exports: {@code "default"} is the {@code fs}
     * namespace, and every function is also a named export.
     * @return the export map
     */
    public static Map<String, Object> exports() {
        final Map<String, Object> members = new LinkedHashMap<>();
        // (name, op, hasCallbackForm) - sync as name+"Sync", async as name, promise under promises
        define(members, "readFile", NodeFs::readFile);
        define(members, "writeFile", NodeFs::writeFile);
        define(members, "appendFile", NodeFs::appendFile);
        define(members, "readdir", NodeFs::readdir);
        define(members, "mkdir", NodeFs::mkdir);
        define(members, "rmdir", NodeFs::rmdir);
        define(members, "rm", NodeFs::rm);
        define(members, "unlink", NodeFs::unlink);
        define(members, "rename", NodeFs::rename);
        define(members, "copyFile", NodeFs::copyFile);
        define(members, "stat", a -> stat(a, false));
        define(members, "lstat", a -> stat(a, true));
        define(members, "realpath", NodeFs::realpath);
        define(members, "access", NodeFs::access);
        define(members, "truncate", NodeFs::truncate);
        define(members, "chmod", NodeFs::chmod);
        define(members, "symlink", NodeFs::symlink);
        define(members, "readlink", NodeFs::readlink);
        define(members, "mkdtemp", NodeFs::mkdtemp);

        // existsSync / exists have their own shapes (no error argument)
        members.put("existsSync", fn("existsSync", a -> exists(a)));
        members.put("exists", existsAsync());

        // promises namespace: the promise form of every defined op
        final Map<String, Object> promises = new LinkedHashMap<>();
        for (final Map.Entry<String, Op> e : OPS.entrySet()) {
            promises.put(e.getKey(), promiseFn(e.getKey(), e.getValue()));
        }
        members.put("promises", namespace(promises));

        members.put("constants", constants());

        final JSObject ns = namespace(members);
        final Map<String, Object> exports = new LinkedHashMap<>(members);
        exports.put("default", ns);
        return exports;
    }

    /** Remembers each op so the promises namespace can be built from the same logic. */
    private static final Map<String, Op> OPS = new LinkedHashMap<>();

    private static void define(final Map<String, Object> members, final String name, final Op op) {
        OPS.put(name, op);
        members.put(name + "Sync", fn(name + "Sync", op));
        members.put(name, asyncFn(name, op));
    }

    // ---- the operations ----

    private static Object readFile(final Object[] a) throws IOException {
        final byte[] bytes = Files.readAllBytes(path(a, 0));
        final String enc = encodingOf(a, 1);
        // Node: no encoding yields a Buffer, an encoding yields a string
        return enc == null ? bytes : new String(bytes, charset(enc, StandardCharsets.UTF_8));
    }

    private static Object writeFile(final Object[] a) throws IOException {
        Files.write(path(a, 0), bytes(a[1], encodingOf(a, 2)));
        return Undefined.INSTANCE;
    }

    private static Object appendFile(final Object[] a) throws IOException {
        Files.write(path(a, 0), bytes(a[1], encodingOf(a, 2)),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return Undefined.INSTANCE;
    }

    private static Object readdir(final Object[] a) throws IOException {
        final Path dir = path(a, 0);
        final boolean withTypes = bool(member(opts(a, 1), "withFileTypes"));
        final List<Object> out = new ArrayList<>();
        try (java.util.stream.Stream<Path> list = Files.list(dir)) {
            list.sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(p -> {
                final String name = p.getFileName().toString();
                out.add(withTypes ? dirent(name, p) : name);
            });
        }
        return out.toArray();
    }

    private static Object mkdir(final Object[] a) throws IOException {
        final Path p = path(a, 0);
        if (bool(member(opts(a, 1), "recursive"))) {
            Files.createDirectories(p);
        } else {
            Files.createDirectory(p);
        }
        return Undefined.INSTANCE;
    }

    private static Object rmdir(final Object[] a) throws IOException {
        Files.delete(path(a, 0));
        return Undefined.INSTANCE;
    }

    private static Object rm(final Object[] a) throws IOException {
        final Path p = path(a, 0);
        final Object o = opts(a, 1);
        final boolean recursive = bool(member(o, "recursive"));
        final boolean force = bool(member(o, "force"));
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            if (force) {
                return Undefined.INSTANCE;
            }
            throw new FsError("ENOENT", "ENOENT: no such file or directory, rm '" + p + "'");
        }
        if (recursive && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(p)) {
                walk.sorted(Comparator.reverseOrder()).forEach(child -> {
                    try {
                        Files.delete(child);
                    } catch (final IOException e) {
                        throw new FsError("EIO", e.getMessage());
                    }
                });
            }
        } else {
            Files.delete(p);
        }
        return Undefined.INSTANCE;
    }

    private static Object unlink(final Object[] a) throws IOException {
        Files.delete(path(a, 0));
        return Undefined.INSTANCE;
    }

    private static Object rename(final Object[] a) throws IOException {
        Files.move(path(a, 0), path(a, 1), StandardCopyOption.REPLACE_EXISTING);
        return Undefined.INSTANCE;
    }

    private static Object copyFile(final Object[] a) throws IOException {
        final int mode = a.length > 2 ? (int)toLong(a[2]) : 0;
        if ((mode & 1) != 0 && Files.exists(path(a, 1))) {   // COPYFILE_EXCL
            throw new FsError("EEXIST", "EEXIST: file already exists, copyfile '" + path(a, 0) + "' -> '" + path(a, 1) + "'");
        }
        Files.copy(path(a, 0), path(a, 1), StandardCopyOption.REPLACE_EXISTING);
        return Undefined.INSTANCE;
    }

    private static Object stat(final Object[] a, final boolean noFollow) throws IOException {
        final Path p = path(a, 0);
        final LinkOption[] opts = noFollow ? new LinkOption[] {LinkOption.NOFOLLOW_LINKS} : new LinkOption[0];
        final BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class, opts);
        return stats(attrs);
    }

    private static Object realpath(final Object[] a) throws IOException {
        return path(a, 0).toRealPath().toString();
    }

    private static Object access(final Object[] a) throws IOException {
        final Path p = path(a, 0);
        final int mode = a.length > 1 ? (int)toLong(a[1]) : 0;   // F_OK default
        if (!Files.exists(p)) {
            throw new FsError("ENOENT", "ENOENT: no such file or directory, access '" + p + "'");
        }
        if ((mode & 4) != 0 && !Files.isReadable(p)) {
            throw new FsError("EACCES", "EACCES: permission denied, access '" + p + "'");
        }
        if ((mode & 2) != 0 && !Files.isWritable(p)) {
            throw new FsError("EACCES", "EACCES: permission denied, access '" + p + "'");
        }
        if ((mode & 1) != 0 && !Files.isExecutable(p)) {
            throw new FsError("EACCES", "EACCES: permission denied, access '" + p + "'");
        }
        return Undefined.INSTANCE;
    }

    private static Object truncate(final Object[] a) throws IOException {
        final long len = a.length > 1 ? toLong(a[1]) : 0;
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(path(a, 0), java.nio.file.StandardOpenOption.WRITE)) {
            ch.truncate(len);
        }
        return Undefined.INSTANCE;
    }

    private static Object chmod(final Object[] a) throws IOException {
        final Path p = path(a, 0);
        final int mode = (int)toLong(a[1]);
        try {
            Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString(rwx(mode)));
        } catch (final UnsupportedOperationException notPosix) {
            final java.io.File f = p.toFile();
            f.setReadable((mode & 0400) != 0);
            f.setWritable((mode & 0200) != 0);
            f.setExecutable((mode & 0100) != 0);
        }
        return Undefined.INSTANCE;
    }

    private static Object symlink(final Object[] a) throws IOException {
        Files.createSymbolicLink(path(a, 1), path(a, 0));   // Node: symlink(target, path)
        return Undefined.INSTANCE;
    }

    private static Object readlink(final Object[] a) throws IOException {
        return Files.readSymbolicLink(path(a, 0)).toString();
    }

    private static Object mkdtemp(final Object[] a) throws IOException {
        final String prefix = str(a[0]);
        final Path parent = Path.of(prefix).getParent();
        final String name = Path.of(prefix).getFileName().toString();
        final Path dir = parent != null ? Files.createTempDirectory(parent, name) : Files.createTempDirectory(name);
        return dir.toString();
    }

    private static Object exists(final Object[] a) {
        return Files.exists(path(a, 0));
    }

    // ---- function wrappers ----

    private static JSObject fn(final String name, final Op op) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                try {
                    return toJs(op.run(args));
                } catch (final FsError e) {
                    throw ecma(e.code, e.getMessage());
                } catch (final IOException e) {
                    throw ecma(codeFor(e), messageFor(e, name));
                }
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:fs] }";
            }
        };
    }

    private static JSObject asyncFn(final String name, final Op op) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                if (args.length == 0 || !(args[args.length - 1] instanceof JSObject cb) || !cb.isFunction()) {
                    throw ecma("ERR_INVALID_CALLBACK", name + ": a callback function is required");
                }
                final JSObject callback = (JSObject)args[args.length - 1];
                final Object[] rest = Arrays.copyOf(args, args.length - 1);
                final Global global = Global.instance();
                final JobQueue loop = global.getJobQueue();
                loop.begin();
                IO.submit(() -> {
                    Object result = null;
                    RuntimeException failure = null;
                    try {
                        result = op.run(rest);
                    } catch (final FsError e) {
                        failure = new FsError(e.code, e.getMessage());
                    } catch (final IOException e) {
                        failure = new FsError(codeFor(e), messageFor(e, name));
                    }
                    final Object produced = result;
                    final RuntimeException err = failure;
                    loop.post(() -> {
                        if (err instanceof FsError fe) {
                            callback.call(Undefined.INSTANCE, errorObject(fe.code, fe.getMessage()), Undefined.INSTANCE);
                        } else {
                            callback.call(Undefined.INSTANCE, Undefined.INSTANCE, toJs(produced));
                        }
                    }, true);
                });
                return Undefined.INSTANCE;
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:fs async] }";
            }
        };
    }

    private static JSObject promiseFn(final String name, final Op op) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                final Global global = Global.instance();
                final NativePromise promise = NativePromise.newAsyncPromise(global);
                final JobQueue loop = global.getJobQueue();
                loop.begin();
                IO.submit(() -> {
                    Object result = null;
                    RuntimeException failure = null;
                    try {
                        result = op.run(args);
                    } catch (final FsError e) {
                        failure = new FsError(e.code, e.getMessage());
                    } catch (final IOException e) {
                        failure = new FsError(codeFor(e), messageFor(e, name));
                    }
                    final Object produced = result;
                    final RuntimeException err = failure;
                    loop.post(() -> {
                        if (err instanceof FsError fe) {
                            NativePromise.rejectAsyncPromise(promise, errorObject(fe.code, fe.getMessage()));
                        } else {
                            NativePromise.resolveAsyncPromise(promise, toJs(produced));
                        }
                    }, true);
                });
                return promise;
            }

            @Override
            public String toString() {
                return "function " + name + "() { [node:fs promise] }";
            }
        };
    }

    private static JSObject existsAsync() {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                final JSObject callback = (JSObject)args[args.length - 1];
                final Object[] rest = Arrays.copyOf(args, args.length - 1);
                final Global global = Global.instance();
                final JobQueue loop = global.getJobQueue();
                loop.begin();
                IO.submit(() -> {
                    final boolean e = Files.exists(path(rest, 0));
                    loop.post(() -> callback.call(Undefined.INSTANCE, e), true);
                });
                return Undefined.INSTANCE;
            }
        };
    }

    // ---- Stats and Dirent ----

    private static JSObject stats(final BasicFileAttributes attrs) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", (double)attrs.size());
        m.put("mtimeMs", (double)attrs.lastModifiedTime().toMillis());
        m.put("atimeMs", (double)attrs.lastAccessTime().toMillis());
        m.put("ctimeMs", (double)attrs.lastModifiedTime().toMillis());
        m.put("birthtimeMs", (double)attrs.creationTime().toMillis());
        m.put("isFile", constFn(attrs.isRegularFile()));
        m.put("isDirectory", constFn(attrs.isDirectory()));
        m.put("isSymbolicLink", constFn(attrs.isSymbolicLink()));
        m.put("isBlockDevice", constFn(false));
        m.put("isCharacterDevice", constFn(false));
        m.put("isFIFO", constFn(false));
        m.put("isSocket", constFn(false));
        return namespace(m);
    }

    private static JSObject dirent(final String name, final Path p) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("isFile", constFn(Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)));
        m.put("isDirectory", constFn(Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)));
        m.put("isSymbolicLink", constFn(Files.isSymbolicLink(p)));
        m.put("isBlockDevice", constFn(false));
        m.put("isCharacterDevice", constFn(false));
        m.put("isFIFO", constFn(false));
        m.put("isSocket", constFn(false));
        return namespace(m);
    }

    private static JSObject constFn(final boolean value) {
        return new AbstractJSObject() {
            @Override
            public boolean isFunction() {
                return true;
            }

            @Override
            public Object call(final Object thiz, final Object... args) {
                return value;
            }
        };
    }

    private static Object constants() {
        final Map<String, Object> c = new LinkedHashMap<>();
        c.put("F_OK", 0);
        c.put("R_OK", 4);
        c.put("W_OK", 2);
        c.put("X_OK", 1);
        c.put("O_RDONLY", 0);
        c.put("O_WRONLY", 1);
        c.put("O_RDWR", 2);
        c.put("O_CREAT", 64);
        c.put("O_EXCL", 128);
        c.put("O_TRUNC", 512);
        c.put("O_APPEND", 1024);
        c.put("COPYFILE_EXCL", 1);
        return namespace(c);
    }

    // ---- a read-only namespace object over a fixed member map ----

    private static JSObject namespace(final Map<String, Object> members) {
        return new AbstractJSObject() {
            @Override
            public Object getMember(final String name) {
                return members.getOrDefault(name, Undefined.INSTANCE);
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

    // ---- helpers ----

    private static Object toJs(final Object raw) {
        if (raw instanceof Object[] arr) {
            return Global.instance().wrapAsObject(arr);
        }
        if (raw instanceof byte[] bytes) {
            return uint8array(bytes);
        }
        return raw;
    }

    /**
     * A {@code Uint8Array} over the bytes, created through the realm's own
     * {@code Uint8Array}. Node's {@code Buffer} is a {@code Uint8Array}
     * subclass, so a script can use the result directly or upgrade it with
     * {@code Buffer.from(...)} from the {@code buffer} module.
     */
    private static Object uint8array(final byte[] bytes) {
        final Global g = Global.instance();
        final Object ctor = g.get("Uint8Array");
        if (!(ctor instanceof org.monflabs.nashorn.internal.runtime.ScriptObject u8)) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
        final Object from = u8.get("from");
        final int[] ints = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            ints[i] = bytes[i] & 0xff;
        }
        return ScriptRuntime.call(from, ctor, new Object[] {g.wrapAsObject(ints)});
    }

    private static Object errorObject(final String code, final String message) {
        final org.monflabs.nashorn.internal.objects.Global g = Global.instance();
        final org.monflabs.nashorn.internal.runtime.ScriptObject err = g.newError(message);
        err.set("code", code, 0);
        return err;
    }

    private static RuntimeException ecma(final String code, final String message) {
        return new org.monflabs.nashorn.internal.runtime.ECMAException(errorObject(code, message), null);
    }

    private static Path path(final Object[] a, final int i) {
        return Path.of(str(a[i]));
    }

    private static String str(final Object o) {
        return ScriptRuntime.safeToString(o);
    }

    private static Object opts(final Object[] a, final int i) {
        return a.length > i ? a[i] : Undefined.INSTANCE;
    }

    private static Object member(final Object options, final String key) {
        if (options instanceof JSObject j && j.hasMember(key)) {
            return j.getMember(key);
        }
        return Undefined.INSTANCE;
    }

    private static String encodingOf(final Object[] a, final int i) {
        if (a.length <= i) {
            return null;
        }
        final Object o = a[i];
        if (o instanceof CharSequence) {
            return o.toString();
        }
        final Object enc = member(o, "encoding");
        return enc == null || enc == Undefined.INSTANCE ? null : enc.toString();
    }

    private static Charset charset(final String name, final Charset fallback) {
        if (name == null) {
            return fallback;
        }
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
        case "utf8": case "utf-8": return StandardCharsets.UTF_8;
        case "ascii": return StandardCharsets.US_ASCII;
        case "latin1": case "binary": return StandardCharsets.ISO_8859_1;
        case "utf16le": case "ucs2": case "ucs-2": return StandardCharsets.UTF_16LE;
        default:
            try {
                return Charset.forName(name);
            } catch (final RuntimeException unknown) {
                return fallback;
            }
        }
    }

    private static byte[] bytes(final Object data, final String encoding) {
        if (data instanceof CharSequence s) {
            return s.toString().getBytes(charset(encoding, StandardCharsets.UTF_8));
        }
        if (data instanceof JSObject j && j.hasMember("length")) {
            final int n = (int)toLong(j.getMember("length"));
            final byte[] out = new byte[n];
            for (int i = 0; i < n; i++) {
                out[i] = (byte)toLong(j.getSlot(i));
            }
            return out;
        }
        return str(data).getBytes(charset(encoding, StandardCharsets.UTF_8));
    }

    private static boolean bool(final Object o) {
        return Boolean.TRUE.equals(o) || (o instanceof Number n && n.doubleValue() != 0);
    }

    private static long toLong(final Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(str(o));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static String rwx(final int mode) {
        final char[] flags = {'r', 'w', 'x'};
        final StringBuilder sb = new StringBuilder(9);
        for (int shift = 6; shift >= 0; shift -= 3) {
            final int bits = (mode >> shift) & 7;
            for (int b = 0; b < 3; b++) {
                sb.append((bits & (4 >> b)) != 0 ? flags[b] : '-');
            }
        }
        return sb.toString();
    }

    private static String codeFor(final IOException e) {
        if (e instanceof NoSuchFileException) {
            return "ENOENT";
        }
        if (e instanceof java.nio.file.FileAlreadyExistsException) {
            return "EEXIST";
        }
        if (e instanceof java.nio.file.NotDirectoryException) {
            return "ENOTDIR";
        }
        if (e instanceof java.nio.file.DirectoryNotEmptyException) {
            return "ENOTEMPTY";
        }
        if (e instanceof java.nio.file.AccessDeniedException) {
            return "EACCES";
        }
        return "EIO";
    }

    private static String messageFor(final IOException e, final String syscall) {
        final String file = e instanceof java.nio.file.FileSystemException fse && fse.getFile() != null ? ", '" + fse.getFile() + "'" : "";
        return codeFor(e) + ": " + reason(codeFor(e)) + ", " + syscall + file;
    }

    private static String reason(final String code) {
        switch (code) {
        case "ENOENT": return "no such file or directory";
        case "EEXIST": return "file already exists";
        case "ENOTDIR": return "not a directory";
        case "ENOTEMPTY": return "directory not empty";
        case "EACCES": return "permission denied";
        default: return "i/o error";
        }
    }

    /** The engine's {@code undefined}. */
    private static final class Undefined {
        static final Object INSTANCE = ScriptRuntime.UNDEFINED;
        private Undefined() {
        }
    }
}
