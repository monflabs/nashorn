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

package org.monflabs.nashorn.internal.runtime;

import static org.monflabs.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static org.monflabs.nashorn.internal.codegen.CompilerConstants.CREATE_PROGRAM_FUNCTION;
import static org.monflabs.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static org.monflabs.nashorn.internal.codegen.CompilerConstants.STRICT_MODE;
import static org.monflabs.nashorn.internal.runtime.CodeStore.newCodeStore;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.EVAL_NEW_TARGET_KEY;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static org.monflabs.nashorn.internal.runtime.Source.sourceFor;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import org.monflabs.nashorn.internal.runtime.debugger.DebuggerImpl;
import org.monflabs.nashorn.internal.runtime.debugger.DebugLocations;
import org.monflabs.nashorn.api.debugger.DebuggerFrontend;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import java.util.ServiceLoader;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.script.ScriptEngine;
import jdk.dynalink.DynamicLinker;
import org.monflabs.nashorn.api.scripting.ClassFilter;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.internal.WeakValueCache;
import org.monflabs.nashorn.internal.codegen.Compiler;
import org.monflabs.nashorn.internal.codegen.Compiler.CompilationPhases;
import org.monflabs.nashorn.internal.codegen.ObjectClassGenerator;
import org.monflabs.nashorn.internal.ir.FunctionNode;
import org.monflabs.nashorn.internal.ir.LexicalContext;
import org.monflabs.nashorn.internal.ir.VarNode;
import org.monflabs.nashorn.internal.ir.debug.ASTWriter;
import org.monflabs.nashorn.internal.ir.debug.PrintVisitor;
import org.monflabs.nashorn.internal.ir.visitor.NodeVisitor;
import org.monflabs.nashorn.internal.lookup.MethodHandleFactory;
import org.monflabs.nashorn.internal.objects.Global;
import org.monflabs.nashorn.internal.parser.Parser;
import org.monflabs.nashorn.internal.runtime.events.RuntimeEvent;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.logging.DebugLogger;
import org.monflabs.nashorn.internal.runtime.logging.Loggable;
import org.monflabs.nashorn.internal.runtime.logging.Logger;
import org.monflabs.nashorn.internal.runtime.options.LoggingOption.LoggerInfo;
import org.monflabs.nashorn.internal.runtime.options.Options;
import sun.misc.Unsafe;

/**
 * This class manages the global state of execution. Context is immutable.
 */
public final class Context {
    // nashorn load psuedo URL prefixes
    private static final String LOAD_CLASSPATH = "classpath:";
    private static final String LOAD_FX = "fx:";
    private static final String LOAD_NASHORN = "nashorn:";

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType CREATE_PROGRAM_FUNCTION_TYPE = MethodType.methodType(ScriptFunction.class, ScriptObject.class);

    private static final LongAdder NAMED_INSTALLED_SCRIPT_COUNT = new LongAdder();
    private static final LongAdder ANONYMOUS_INSTALLED_SCRIPT_COUNT = new LongAdder();

    /**
     * Should scripts use only object slots for fields, or dual long/object slots? The default
     * behaviour is to couple this to optimistic types, using dual representation if optimistic types are enabled
     * and single field representation otherwise. This can be overridden by setting either the "nashorn.fields.objects"
     * or "nashorn.fields.dual" system property.
     */
    private final FieldMode fieldMode;

    private enum FieldMode {
        /** Value for automatic field representation depending on optimistic types setting */
        AUTO,
        /** Value for object field representation regardless of optimistic types setting */
        OBJECTS,
        /** Value for dual primitive/object field representation regardless of optimistic types setting */
        DUAL
    }

    /**
     * Keeps track of which builtin prototypes and properties have been relinked
     * Currently we are conservative and associate the name of a builtin class with all
     * its properties, so it's enough to invalidate a property to break all assumptions
     * about a prototype. This can be changed to a more fine grained approach, but no one
     * ever needs this, given the very rare occurrence of swapping out only parts of
     * a builtin v.s. the entire builtin object
     */
    private final Map<String, SwitchPoint> builtinSwitchPoints = new ConcurrentHashMap<>();

    /* Force DebuggerSupport to be loaded. */
    static {
        DebuggerSupport.FORCELOAD = true;
    }

    static long getNamedInstalledScriptCount() {
        return NAMED_INSTALLED_SCRIPT_COUNT.sum();
    }

    static long getAnonymousInstalledScriptCount() {
        return ANONYMOUS_INSTALLED_SCRIPT_COUNT.sum();
    }

    /**
     * ContextCodeInstaller that has the privilege of installing classes in the Context.
     * Can only be instantiated from inside the context and is opaque to other classes
     */
    private abstract static class ContextCodeInstaller implements CodeInstaller {
        final Context context;

        ContextCodeInstaller(final Context context) {
            this.context = context;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void initialize(final Collection<Class<?>> classes, final Source source, final Object[] constants) {
            try {
                for (final Class<?> clazz : classes) {
                    //use reflection to write source and constants table to installed classes
                    final Field sourceField = clazz.getDeclaredField(SOURCE.symbolName());
                    sourceField.setAccessible(true);
                    sourceField.set(null, source);

                    final Field constantsField = clazz.getDeclaredField(CONSTANTS.symbolName());
                    constantsField.setAccessible(true);
                    constantsField.set(null, constants);
                }
            } catch (final ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void verify(final byte[] code) {
            context.verify(code);
        }

        @Override
        public long getUniqueScriptId() {
            return context.getUniqueScriptId();
        }

        @Override
        public void storeScript(final String cacheKey, final Source source, final String mainClassName,
                                final Map<String,byte[]> classBytes, final Map<Integer, FunctionInitializer> initializers,
                                final Object[] constants, final int compilationId) {
            if (context.codeStore != null) {
                context.codeStore.store(cacheKey, source, mainClassName, classBytes, initializers, constants, compilationId);
            }
        }

        @Override
        public StoredScript loadScript(final Source source, final String functionKey) {
            if (context.codeStore != null) {
                return context.codeStore.load(source, functionKey);
            }
            return null;
        }

        @Override
        public boolean isCompatibleWith(final CodeInstaller other) {
            if (other instanceof ContextCodeInstaller) {
                final ContextCodeInstaller cci = (ContextCodeInstaller)other;
                return cci.context == context;
            }
            return false;
        }
    }

    private static class NamedContextCodeInstaller extends ContextCodeInstaller {
        private final ScriptLoader loader;
        private int usageCount = 0;
        private int bytesDefined = 0;

        // We reuse this installer for 10 compilations or 200000 defined bytes. Usually the first condition
        // will occur much earlier, the second is a safety measure for very large scripts/functions.
        private final static int MAX_USAGES = 10;
        private final static int MAX_BYTES_DEFINED = 200_000;

        private NamedContextCodeInstaller(final Context context, final ScriptLoader loader) {
            super(context);
            this.loader = loader;
        }

        @Override
        public Class<?> install(final String className, final byte[] bytecode) {
            usageCount++;
            bytesDefined += bytecode.length;
            NAMED_INSTALLED_SCRIPT_COUNT.increment();
            return loader.installClass(Compiler.binaryName(className), bytecode);
        }

        @Override
        public CodeInstaller getOnDemandCompilationInstaller() {
            // Reuse this installer if we're within our limits.
            if (usageCount < MAX_USAGES && bytesDefined < MAX_BYTES_DEFINED) {
                return this;
            }
            return new NamedContextCodeInstaller(context, context.createNewLoader());
        }

        @Override
        public CodeInstaller getMultiClassCodeInstaller() {
            // This installer is perfectly suitable for installing multiple classes that reference each other
            // as it produces classes with resolvable names, all defined in a single class loader.
            return this;
        }
    }

    private final WeakValueCache<URL, Class<?>> anonymousHostClasses = new WeakValueCache<>();

    private static final class AnonymousContextCodeInstaller extends ContextCodeInstaller {
        private static final MethodHandle DEFINE_ANONYMOUS_CLASS = getDefineAnonymousClass();
        private static final String ANONYMOUS_HOST_CLASS_NAME = Compiler.SCRIPTS_PACKAGE.replace('/', '.') + ".AnonymousHost";
        private static final byte[] ANONYMOUS_HOST_CLASS_BYTES = getAnonymousHostClassBytes();
        static volatile Exception initFailure;

        private final Class<?> hostClass;

        private static MethodHandle getDefineAnonymousClass() {
            try {
                final MethodHandle mh = MethodHandles.lookup().findVirtual(Unsafe.class, "defineAnonymousClass",
                    MethodType.methodType(Class.class, Class.class, byte[].class, Object[].class));
                final Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return mh.bindTo(f.get(null));
            } catch (Exception e) {
                initFailure = e;
                return null;
            }
        }

        private AnonymousContextCodeInstaller(final Context context, final Class<?> hostClass) {
            super(context);
            this.hostClass = hostClass;
        }

        @Override
        public Class<?> install(final String className, final byte[] bytecode) {
            ANONYMOUS_INSTALLED_SCRIPT_COUNT.increment();
            try {
                return (Class<?>)DEFINE_ANONYMOUS_CLASS.invokeExact(hostClass, bytecode, (Object[])null);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public CodeInstaller getOnDemandCompilationInstaller() {
            // This code loader can be indefinitely reused for on-demand recompilations for the same code source.
            return this;
        }

        @Override
        public CodeInstaller getMultiClassCodeInstaller() {
            // This code loader can not be used to install multiple classes that reference each other, as they
            // would have no resolvable names. Therefore, in such situation we must revert to an installer that
            // produces named classes.
            return new NamedContextCodeInstaller(context, context.createNewLoader());
        }

        private static byte[] getAnonymousHostClassBytes() {
            return ClassFile.of().build(ClassDesc.of(ANONYMOUS_HOST_CLASS_NAME), clb -> {
                clb.withVersion(ClassFile.JAVA_7_VERSION, 0);
                clb.withFlags(ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            });
        }
    }

    /** Is Context global debug mode enabled ? */
    public static final boolean DEBUG = Options.getBooleanProperty("nashorn.debug");

    /**
     * The realm the current thread is running in.
     *
     * A scoped value rather than a thread local: a realm is only ever
     * established for the duration of something - an evaluation, a mirror
     * operation, a generator's body - and a binding that cannot outlive its
     * scope cannot leak to the next task on a pooled thread either. Reads are
     * also cheaper, which matters on the virtual threads generators and async
     * functions run their bodies on.
     */
    private static final ScopedValue<Global> currentGlobal = ScopedValue.newInstance();

    // in-memory cache for loaded classes
    private ClassCache classCache;

    // persistent code store
    private CodeStore codeStore;

    // the debugger, when the context was created with --debugger
    private final DebuggerImpl debugger;

    // the context behind each script engine, for the debugger API
    private static final Map<ScriptEngine, Context> ENGINE_CONTEXTS = Collections.synchronizedMap(new WeakHashMap<>());

    // A factory for linking global properties as constant method handles. It is created when the first Global
    // is created, and invalidated forever once the second global is created.
    private final AtomicReference<GlobalConstants> globalConstantsRef = new AtomicReference<>();

    // Are java.sql, java.sql.rowset modules found in the system?
    static final boolean javaSqlFound, javaSqlRowsetFound;

    static {
        final ModuleLayer boot = ModuleLayer.boot();
        javaSqlFound = boot.findModule("java.sql").isPresent();
        javaSqlRowsetFound = boot.findModule("java.sql.rowset").isPresent();
    }

    /**
     * Get the current global scope
     * @return the current global scope, or null when no realm is established
     */
    public static Global getGlobal() {
        // This class in a package.access protected package.
        // Trusted code only can call this method.
        return currentGlobal.isBound() ? currentGlobal.get() : null;
    }

    /**
     * A void operation to be run within a realm, permitted to throw anything.
     *
     * @param <X> what the operation throws
     */
    @FunctionalInterface
    public interface GlobalOp<X extends Throwable> {
        /**
         * Runs the operation.
         * @throws X whatever the operation throws
         */
        void run() throws X;
    }

    /**
     * Runs an operation with the given global as the current realm.
     *
     * The realm is bound for exactly the duration of the operation and the
     * previous one - or none - resumes afterwards, however the operation ends.
     * When the realm is already the current one the operation simply runs:
     * nothing observes the difference, and the common nested case - a mirror
     * used inside its own realm - costs a comparison.
     *
     * @param global the realm to run in
     * @param op     what to run
     * @param <X>    what the operation throws
     * @throws X what the operation threw
     */
    public static <X extends Throwable> void runWithGlobal(final Global global, final GlobalOp<X> op) throws X {
        callWithGlobal(global, () -> {
            op.run();
            return null;
        });
    }

    /**
     * Calls an operation with the given global as the current realm.
     *
     * @param global the realm to run in
     * @param op     what to call
     * @param <T>    what the operation returns
     * @param <X>    what the operation throws
     * @return what the operation returned
     * @throws X what the operation threw
     */
    public static <T, X extends Throwable> T callWithGlobal(final Global global, final ScopedValue.CallableOp<T, X> op) throws X {
        final Global current = getGlobal();
        if (current == global) {
            return op.call();
        }
        // the same code can be cached between globals, so the method handle
        // constants a switch would make stale are dropped - on the way in for
        // the realm being entered, and on the way out for the one resumed,
        // exactly as the imperative set-and-restore pair used to
        invalidateConstants(global);
        try {
            return ScopedValue.where(currentGlobal, global).call(op);
        } finally {
            invalidateConstants(current);
        }
    }

    private static void invalidateConstants(final Global global) {
        if (global != null) {
            final GlobalConstants globalConstants = getContext(global).getGlobalConstants();
            if (globalConstants != null) {
                globalConstants.invalidateAll();
            }
        }
    }

    /**
     * Get context of the current global
     * @return current global scope's context.
     */
    public static Context getContext() {
        return getContext(getGlobal());
    }

    /**
     * Get current context's error writer
     *
     * @return error writer of the current context
     */
    public static PrintWriter getCurrentErr() {
        final ScriptObject global = getGlobal();
        return (global != null)? global.getContext().getErr() : new PrintWriter(System.err);
    }

    /**
     * Output text to this Context's error stream
     * @param str text to write
     */
    public static void err(final String str) {
        err(str, true);
    }

    /**
     * Output text to this Context's error stream, optionally with
     * a newline afterwards
     *
     * @param str  text to write
     * @param crlf write a carriage return/new line after text
     */
    public static void err(final String str, final boolean crlf) {
        final PrintWriter err = Context.getCurrentErr();
        if (err != null) {
            if (crlf) {
                err.println(str);
            } else {
                err.print(str);
            }
        }
    }

    /** Current environment. */
    private final ScriptEnvironment env;

    /** is this context in strict mode? Cached from env. as this is used heavily. */
    final boolean _strict;

    /** class loader to resolve classes from script. */
    private final ClassLoader appLoader;

    /*package-private*/
    ClassLoader getAppLoader() {
        return appLoader;
    }

    /** Class loader to load classes compiled from scripts. */
    private final ScriptLoader scriptLoader;

    /** Dynamic linker for linking call sites in script code loaded by this context */
    private final DynamicLinker dynamicLinker;

    /** Current error manager. */
    private final ErrorManager errors;

    /** Unique id for script. Used only when --loader-per-compile=false */
    private final AtomicLong uniqueScriptId;

    /** Optional class filter to use for Java classes. Can be null. */
    private final ClassFilter classFilter;

    /** The script libraries every global of this context gets, in order. */
    private final List<ScriptLibrary> libraries;

    /**
     * The module-loading chain, in the order asked: the loaders the embedder handed the builder.
     * Empty means imports resolve as filesystem paths relative to the importing module.
     */
    private final List<org.monflabs.nashorn.api.modules.ModuleLoader> moduleLoaders;

    /** Process-wide singleton structure loader */
    private static final StructureLoader theStructLoader;
    private static final ConcurrentMap<String, Class<?>> structureClasses = new ConcurrentHashMap<>();

    /*package-private*/ @SuppressWarnings("static-method")
    StructureLoader getStructLoader() {
        return theStructLoader;
    }

    static {
        theStructLoader = new StructureLoader(Context.class.getClassLoader());
    }

    /**
     * ThrowErrorManager that throws ParserException upon error conditions.
     */
    public static class ThrowErrorManager extends ErrorManager {
        @Override
        public void error(final String message) {
            throw new ParserException(message);
        }

        @Override
        public void error(final ParserException e) {
            throw e;
        }
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param appLoader application class loader
     */
    public Context(final Options options, final ErrorManager errors, final ClassLoader appLoader) {
        this(options, errors, appLoader, null);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param appLoader application class loader
     * @param classFilter class filter to use
     */
    public Context(final Options options, final ErrorManager errors, final ClassLoader appLoader, final ClassFilter classFilter) {
        this(options, errors, new PrintWriter(System.out, true), new PrintWriter(System.err, true), appLoader, classFilter);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader) {
        this(options, errors, out, err, appLoader, null);
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     * @param classFilter class filter to use
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader, final ClassFilter classFilter) {
        this(options, errors, out, err, appLoader, classFilter, List.of());
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     * @param classFilter class filter to use
     * @param libraries script libraries to apply to every global
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader, final ClassFilter classFilter, final List<ScriptLibrary> libraries) {
        this(options, errors, out, err, appLoader, classFilter, libraries, List.of());
    }

    /**
     * Constructor
     *
     * @param options options from command line or Context creator
     * @param errors  error manger
     * @param out     output writer for this Context
     * @param err     error writer for this Context
     * @param appLoader application class loader
     * @param classFilter class filter to use
     * @param libraries script libraries to apply to every global
     * @param moduleLoaders the module-loading chain; empty for the default filesystem loading
     */
    public Context(final Options options, final ErrorManager errors, final PrintWriter out, final PrintWriter err, final ClassLoader appLoader, final ClassFilter classFilter, final List<ScriptLibrary> libraries, final List<org.monflabs.nashorn.api.modules.ModuleLoader> moduleLoaders) {
        this.moduleLoaders = moduleLoaders == null ? List.of() : List.copyOf(moduleLoaders);
        this.classFilter = classFilter;
        this.env       = new ScriptEnvironment(options, out, err);
        this._strict   = env._strict;
        this.debugger  = env._debugger ? new DebuggerImpl(this) : null;
        if (env._loader_per_compile) {
            this.scriptLoader = null;
            this.uniqueScriptId = null;
        } else {
            this.scriptLoader = createNewLoader();
            this.uniqueScriptId = new AtomicLong();
        }
        this.errors    = errors;

        // if user passed --module-path, we create a module class loader with
        // passed appLoader as the parent.
        final String modulePath = env._module_path;
        ClassLoader appCl;
        if (!env._compile_only && modulePath != null && !modulePath.isEmpty()) {
            appCl = createModuleLoader(appLoader, modulePath, env._add_modules);
        } else {
            appCl = appLoader;
        }

        // if user passed -classpath option, make a URLClassLoader with that and
        // the app loader or module app loader as the parent.
        final String classPath = env._classpath;
        if (!env._compile_only && classPath != null && !classPath.isEmpty()) {
            appCl = NashornLoader.createClassLoader(classPath, appCl);
        }

        this.appLoader = appCl;
        this.dynamicLinker = Bootstrap.createDynamicLinker(this.appLoader, env._unstable_relink_threshold);
        this.libraries = env._compile_only ? List.of() : ScriptLibraries.resolve(libraries);

        final int cacheSize = env._class_cache_size;
        if (cacheSize > 0) {
            classCache = new ClassCache(this, cacheSize);
        }

        if (env._persistent_cache) {
            codeStore = newCodeStore(this);
        }

        // print version info if asked.
        if (env._version) {
            getErr().println("nashorn-monflabs " + Version.version());
        }

        if (env._fullversion) {
            getErr().println("nashorn-monflabs full version " + Version.fullVersion());
        }

        if (Options.getBooleanProperty("nashorn.fields.dual")) {
            fieldMode = FieldMode.DUAL;
        } else if (Options.getBooleanProperty("nashorn.fields.objects")) {
            fieldMode = FieldMode.OBJECTS;
        } else {
            fieldMode = FieldMode.AUTO;
        }

        initLoggers();

        if (env._inspect != null) {
            inspector = startInspector();
        } else {
            inspector = null;
        }
    }

    // the debugger frontend --inspect started, or null
    private final AutoCloseable inspector;

    /**
     * Starts the debugger frontend {@code --inspect} asked for: whichever
     * {@link DebuggerFrontend} the module path or class path offers.
     */
    private AutoCloseable startInspector() {
        DebuggerFrontend frontend = null;
        for (final DebuggerFrontend candidate : ServiceLoader.load(DebuggerFrontend.class)) {
            frontend = candidate;
            break;
        }
        if (frontend == null) {
            for (final DebuggerFrontend candidate : ServiceLoader.load(DebuggerFrontend.class, Thread.currentThread().getContextClassLoader())) {
                frontend = candidate;
                break;
            }
        }
        if (frontend == null) {
            throw new IllegalArgumentException("--inspect needs a debugger frontend, and none is on the module path or class path: add the nashorn-debugger artifact");
        }
        try {
            return frontend.start(debugger, InspectOptions.parse(env._inspect, env._inspect_brk));
        } catch (final IOException e) {
            throw new IllegalArgumentException("--inspect: " + frontend.name() + " could not start: " + e.getMessage(), e);
        }
    }

    /**
     * Stops the debugger frontend {@code --inspect} started, if any. The
     * debugger itself stays; a frontend can be started again.
     */
    public void closeInspector() {
        if (inspector != null) {
            try {
                inspector.close();
            } catch (final Exception e) {
                // it is being discarded
            }
        }
    }

    /**
     * The debugger of this context, or null when it was created without {@code --debugger}.
     * @return the debugger
     */
    public DebuggerImpl getDebugger() {
        return debugger;
    }

    /**
     * The context behind a script engine.
     * @param engine the engine
     * @return its context, or null if the engine is not a Nashorn engine whose global has been created
     */
    public static Context getContext(final ScriptEngine engine) {
        return ENGINE_CONTEXTS.get(engine);
    }

    /**
     * Get the class filter for this context
     * @return class filter
     */
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    /**
     * Returns the factory for constant method handles for global properties. The returned factory can be
     * invalidated if this Context has more than one Global.
     * @return the factory for constant method handles for global properties.
     */
    GlobalConstants getGlobalConstants() {
        return globalConstantsRef.get();
    }

    /**
     * Get the error manager for this context
     * @return error manger
     */
    public ErrorManager getErrorManager() {
        return errors;
    }

    /**
     * Get the script environment for this context
     * @return script environment
     */
    public ScriptEnvironment getEnv() {
        return env;
    }

    /**
     * Get the output stream for this context
     * @return output print writer
     */
    public PrintWriter getOut() {
        return env.getOut();
    }

    /**
     * Get the error stream for this context
     * @return error print writer
     */
    public PrintWriter getErr() {
        return env.getErr();
    }

    /**
     * Should scripts compiled by this context use dual field representation?
     * @return true if using dual fields, false for object-only fields
     */
    public boolean useDualFields() {
        return fieldMode == FieldMode.DUAL || (fieldMode == FieldMode.AUTO && env._optimistic_types);
    }

    /**
     * Get the PropertyMap of the current global scope
     * @return the property map of the current global scope
     */
    public static PropertyMap getGlobalMap() {
        return Context.getGlobal().getMap();
    }

    /**
     * Compiles and runs a module, and everything it imports.
     *
     * @param source the module's source
     * @return the module record, once its body has finished
     */
    public ModuleRecord evaluateModule(final Source source) {
        return loadModule(source, source.getName(), null).link().evaluate();
    }

    /**
     * Evaluates a module without registering it: what an eval'd entry module
     * needs, since every eval is its own program - two evaluations of one
     * source are two modules, and none of them answers to a name an import
     * could collide with. Its own imports still resolve and deduplicate
     * normally.
     *
     * @param source the module source
     * @return the evaluated module
     */
    public ModuleRecord evaluateModuleDetached(final Source source) {
        final Global global = getGlobal();
        final FunctionNode moduleNode = compileModuleNode(source);
        final ScriptFunction body = getProgramFunction(compileModule(source, moduleNode), global);
        return new ModuleRecord(source.getName(), moduleNode.getModule(), body, global, null).link().evaluate();
    }

    /**
     * Resolves one module specifier against the module that wrote it.
     *
     * ES2015 leaves resolution to the host. A specifier here names a file
     * relative to the source that imported it, which is what a module written on
     * disk means by it and what the conformance suite's fixtures rely on.
     *
     * @param specifier the text between the quotes
     * @param referrer  the module the import was written in
     * @return the module it names, already loaded if it has been asked for before
     */
    public ModuleRecord loadModule(final String specifier, final ModuleRecord referrer) {
        if (!moduleLoaders.isEmpty()) {
            final org.monflabs.nashorn.api.modules.Module referrerView = referrer == null ? null : referrer.moduleView();
            for (final org.monflabs.nashorn.api.modules.ModuleLoader loader : moduleLoaders) {
                final org.monflabs.nashorn.api.modules.Module loaded = loader.load(specifier, referrerView);
                if (loaded != null) {
                    return record(loaded);
                }
            }
            return null;
        }
        final String base = referrer == null ? null : referrer.getName();
        final Path resolved = resolveModule(specifier, base);
        if (resolved == null) {
            return null;
        }
        try {
            return loadModule(Source.sourceFor(resolved.toString(), resolved.toFile()), resolved.toString(), resolved);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The record for what a loader answered: known already, Java values, or script to compile. */
    private ModuleRecord record(final org.monflabs.nashorn.api.modules.Module loaded) {
        final Global global = getGlobal();
        final ModuleRecord known = global.getModule(loaded.name());
        if (known != null) {
            return known;
        }
        if (loaded.exports() != null) {
            final ModuleRecord record = new ModuleRecord(loaded.name(), loaded.exports(), global);
            global.registerModule(loaded.name(), record);
            return record;
        }
        return loadModule(Source.sourceFor(loaded.name(), loaded.text()), loaded.name(), loaded.origin());
    }

    private static Path resolveModule(final String specifier, final String base) {
        final Path path = Paths.get(specifier);
        if (path.isAbsolute()) {
            return Files.isReadable(path) ? path : null;
        }
        final Path from = base == null ? null : Paths.get(base).toAbsolutePath().getParent();
        final Path candidate = from == null ? path.toAbsolutePath() : from.resolve(specifier).normalize();
        return Files.isReadable(candidate) ? candidate : null;
    }

    private ModuleRecord loadModule(final Source source, final String name, final Object origin) {
        final Global global = getGlobal();
        final ModuleRecord known = global.getModule(name);
        if (known != null) {
            return known;
        }

        final FunctionNode moduleNode = compileModuleNode(source);
        final ScriptFunction body = getProgramFunction(compileModule(source, moduleNode), global);
        final ModuleRecord record = new ModuleRecord(name, moduleNode.getModule(), body, global, origin);
        global.registerModule(name, record);
        return record;
    }

    /**
     * Compile a top level script.
     *
     * @param source the source
     * @param scope  the scope
     *
     * @return top level function for script
     */
    public ScriptFunction compileScript(final Source source, final ScriptObject scope) {
        return compileScript(source, scope, this.errors);
    }

    /**
     * Interface to represent compiled code that can be re-used across many
     * global scope instances
     */
    public interface MultiGlobalCompiledScript {
        /**
         * Obtain script function object for a specific global scope object.
         *
         * @param newGlobal global scope for which function object is obtained
         * @return script function for script level expressions
         */
        ScriptFunction getFunction(final Global newGlobal);
    }

    /**
     * Compile a top level script.
     *
     * @param source the script source
     * @return reusable compiled script across many global scopes.
     */
    public MultiGlobalCompiledScript compileScript(final Source source) {
        final Class<?> clazz = compile(source, this.errors, this._strict);
        final MethodHandle createProgramFunctionHandle = getCreateProgramFunctionHandle(clazz);

        return newGlobal -> invokeCreateProgramFunctionHandle(createProgramFunctionHandle, newGlobal);
    }

    /**
     * Entry point for {@code eval}
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location) {
        return eval(initialScope, string, callThis, location, false, false);
    }

    /**
     * Entry point for {@code eval}
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     * @param evalCall     is this called from "eval" builtin?
     *
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location, final boolean strict, final boolean evalCall) {
        return eval(initialScope, string, callThis, location, strict, evalCall, false);
    }

    /**
     * Whether a piece of eval code declares a var called "arguments".
     *
     * Only asked of a direct eval in a parameter expression, which is rare
     * enough that parsing the code twice - once here and once to compile it -
     * costs nothing anybody will see. A let or const of that name is a
     * different thing and is allowed.
     */
    private boolean declaresArguments(final String code) {
        final FunctionNode program;
        try {
            program = new Parser(env, sourceFor("<eval>", code), new ThrowErrorManager(),
                    false, getLogger(Parser.class)).parse();
        } catch (final ParserException e) {
            // it will not compile either, and says so with its own message
            return false;
        }
        final boolean[] found = new boolean[1];
        program.getBody().accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode nested) {
                // a nested function declares into its own environment, except
                // for its own name, which the var check below already covers
                return false;
            }

            @Override
            public boolean enterVarNode(final VarNode varNode) {
                if (!varNode.isBlockScoped() && "arguments".equals(varNode.getName().getName())) {
                    found[0] = true;
                }
                return false;
            }
        });
        return found[0];
    }

    /**
     * Entry point for {@code eval}, from a call site that knows whether it sits
     * in a parameter list.
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     * @param evalCall     is this called from "eval" builtin?
     * @param inParameters is the call in a parameter expression?
     *
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location, final boolean strict, final boolean evalCall,
            final boolean inParameters) {
        return eval(initialScope, string, callThis, location, strict, evalCall, inParameters, null);
    }

    /**
     * Entry point for {@code eval}, from a call site that knows what function it
     * sits in.
     *
     * @param initialScope The scope of this eval call
     * @param string       Evaluated code as a String
     * @param callThis     "this" to be passed to the evaluated code
     * @param location     location of the eval call
     * @param strict       is this {@code eval} call from a strict mode code?
     * @param evalCall     is this called from "eval" builtin?
     * @param inParameters is the call in a parameter expression?
     * @param callerCallee the function the call was written in, or null
     *
     * @return the return value of the {@code eval}
     */
    public Object eval(final ScriptObject initialScope, final String string,
            final Object callThis, final Object location, final boolean strict, final boolean evalCall,
            final boolean inParameters, final Object callerCallee) {
        if (inParameters && declaresArguments(string)) {
            // ES2017 18.2.1.1: a direct eval in a parameter expression declares
            // into the parameter environment, where "arguments" is already
            // spoken for, so declaring it there is an early error
            throw ECMAErrors.syntaxError("redeclare.variable", "arguments");
        }
        final String  file       = location == UNDEFINED || location == null ? "<eval>" : location.toString();
        final Source  source     = sourceFor(file, string, evalCall);
        // is this direct 'eval' builtin call?
        final boolean directEval = evalCall && (location != UNDEFINED);
        final Global  global = Context.getGlobal();
        ScriptObject scope = initialScope;

        // ECMA section 10.1.1 point 2 says eval code is strict if it begins
        // with "use strict" directive or eval direct call itself is made
        // from from strict mode code. We are passed with caller's strict mode.
        // Nashorn extension: any 'eval' is unconditionally strict when -strict is specified.
        boolean strictFlag = strict || this._strict;

        // ES2015 18.2.1.1: direct eval code is evaluated where it stands, so
        // new.target and super mean there what they mean at the call - which
        // makes them a syntax error unless the caller is a function, and a
        // method, respectively
        final ScriptFunction caller = directEval && callerCallee instanceof ScriptFunction f ? f : null;
        // an arrow reads the new.target of whatever made it, which the eval
        // cannot see from here, so it is treated as not having one - which is
        // right for an arrow written at the top level, the case the tests pin
        final boolean newTargetAllowed = caller != null && !caller.isProgramFunction() && !caller.isArrowFunction();
        final ScriptObject homeObject = caller == null ? null : caller.getHomeObject();

        Class<?> clazz;
        try {
            clazz = compile(source, new ThrowErrorManager(), strictFlag, newTargetAllowed, homeObject != null);
        } catch (final ParserException e) {
            e.throwAsEcmaException(global);
            return null;
        }

        if (!strictFlag) {
            // We need to get strict mode flag from compiled class. This is
            // because eval code may start with "use strict" directive.
            try {
                strictFlag = clazz.getField(STRICT_MODE.symbolName()).getBoolean(null);
            } catch (final NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                //ignored
                strictFlag = false;
            }
        }

        // In strict mode, eval does not instantiate variables and functions
        // in the caller's environment. A new environment is created!
        if (strictFlag) {
            // Create a new scope object with given scope as its prototype
            scope = newScope(scope);
        }

        final ScriptFunction func = getProgramFunction(clazz, scope);
        if (homeObject != null) {
            // super in the eval code resolves against the caller's home object,
            // which is where the program function carries it
            func.setHomeObject(homeObject);
        }
        if (newTargetAllowed) {
            final Object newTarget = ScriptRuntime.NEW_TARGET(callerCallee, callThis);
            if (newTarget != UNDEFINED) {
                func.set(EVAL_NEW_TARGET_KEY, newTarget, 0);
            }
        }
        Object evalThis;
        if (directEval) {
            evalThis = (callThis != UNDEFINED && callThis != null) || strictFlag ? callThis : global;
        } else {
            // either indirect evalCall or non-eval (Function, engine.eval, ScriptObjectMirror.eval..)
            evalThis = callThis;
        }

        return ScriptRuntime.apply(func, evalThis);
    }

    private static ScriptObject newScope(final ScriptObject callerScope) {
        return new Scope(callerScope, PropertyMap.newMap(Scope.class));
    }

    private static Source loadInternal(final String srcStr, final String prefix, final String resourcePath) {
        if (srcStr.startsWith(prefix)) {
            final String resource = resourcePath + srcStr.substring(prefix.length());
            // NOTE: scripts in nashorn: scheme are always available and are loaded from nashorn.jar's resources.
            try {
                final InputStream resStream = Context.class.getResourceAsStream(resource);
                if (resStream == null) {
                    return null;
                }
                try (resStream) {
                    return sourceFor(srcStr, Source.readFully(resStream));
                }
            } catch (final IOException exp) {
                return null;
            }
        }

        return null;
    }

    /**
     * Implementation of {@code load} Nashorn extension. Load a script file from a source
     * expression
     *
     * @param scope  the scope
     * @param from   source expression for script
     *
     * @return return value for load call (undefined)
     *
     * @throws IOException if source cannot be found or loaded
     */
    public Object load(final Object scope, final Object from) throws IOException {
        final Object src = from instanceof ConsString ? from.toString() : from;
        Source source = null;

        // load accepts a String (which could be a URL or a file name), a File, a URL
        // or a ScriptObject that has "name" and "source" (string valued) properties.
        if (src instanceof String) {
            final String srcStr = (String)src;
            if (srcStr.startsWith(LOAD_CLASSPATH)) {
                final URL url = getResourceURL(srcStr.substring(LOAD_CLASSPATH.length()));
                source = url != null ? sourceFor(url.toString(), url) : null;
            } else {
                final File file = new File(srcStr);
                if (srcStr.indexOf(':') != -1) {
                    if ((source = loadInternal(srcStr, LOAD_NASHORN, "resources/")) == null &&
                        (source = loadInternal(srcStr, LOAD_FX, "resources/fx/")) == null) {
                        URL url;
                        try {
                            //check for malformed url. if malformed, it may still be a valid file
                            url = new URL(srcStr);
                        } catch (final MalformedURLException e) {
                            url = file.toURI().toURL();
                        }
                        source = sourceFor(url.toString(), url);
                    }
                } else if (file.isFile()) {
                    source = sourceFor(srcStr, file);
                }
            }
        } else if (src instanceof File && ((File)src).isFile()) {
            final File file = (File)src;
            source = sourceFor(file.getName(), file);
        } else if (src instanceof URL) {
            final URL url = (URL)src;
            source = sourceFor(url.toString(), url);
        } else if (src instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)src;
            if (sobj.has("script") && sobj.has("name")) {
                final String script = JSType.toString(sobj.get("script"));
                final String name   = JSType.toString(sobj.get("name"));
                source = sourceFor(name, script);
            }
        } else if (src instanceof Map) {
            final Map<?,?> map = (Map<?,?>)src;
            if (map.containsKey("script") && map.containsKey("name")) {
                final String script = JSType.toString(map.get("script"));
                final String name   = JSType.toString(map.get("name"));
                source = sourceFor(name, script);
            }
        }

        if (source != null) {
            if (scope instanceof ScriptObject && ((ScriptObject)scope).isScope()) {
                final ScriptObject sobj = (ScriptObject)scope;
                // passed object is a script object
                // Global is the only user accessible scope ScriptObject
                assert sobj.isGlobal() : "non-Global scope object!!";
                return evaluateSource(source, sobj, sobj);
            } else if (scope == null || scope == UNDEFINED) {
                // undefined or null scope. Use current global instance.
                final Global global = getGlobal();
                return evaluateSource(source, global, global);
            } else {
                /*
                 * Arbitrary object passed for scope.
                 * Indirect load that is equivalent to:
                 *
                 *    (function(scope, source) {
                 *        with (scope) {
                 *            eval(<script_from_source>);
                 *        }
                 *    })(scope, source);
                 */
                final Global global = getGlobal();
                // Create a new object. This is where all declarations
                // (var, function) from the evaluated code go.
                // make global to be its __proto__ so that global
                // definitions are accessible to the evaluated code.
                final ScriptObject evalScope = newScope(global);

                // finally, make a WithObject around user supplied scope object
                // so that it's properties are accessible as variables.
                final ScriptObject withObj = ScriptRuntime.openWith(evalScope, scope);

                // evaluate given source with 'withObj' as scope
                // but use global object as "this".
                return evaluateSource(source, withObj, global);
            }
        }

        throw typeError("cant.load.script", ScriptRuntime.safeToString(from));
    }

    /**
     * Implementation of {@code loadWithNewGlobal} Nashorn extension. Load a script file from a source
     * expression, after creating a new global scope.
     *
     * @param from source expression for script
     * @param args (optional) arguments to be passed to the loaded script
     *
     * @return return value for load call (undefined)
     *
     * @throws IOException if source cannot be found or loaded
     */
    public Object loadWithNewGlobal(final Object from, final Object...args) throws IOException {
        final Global oldGlobal = getGlobal();
        final Global newGlobal;
        try {
            newGlobal =  newGlobal();
        } catch (final RuntimeException e) {
            if (Context.DEBUG) {
                e.printStackTrace();
            }
            throw e;
        }
        // initialize newly created Global instance
        initGlobal(newGlobal);
        return callWithGlobal(newGlobal, () -> {
            final Object[] wrapped = args == null? ScriptRuntime.EMPTY_ARRAY :  ScriptObjectMirror.wrapArray(args, oldGlobal);
            newGlobal.put("arguments", newGlobal.wrapAsObject(wrapped), env._strict);

            // wrap objects from newGlobal's world as mirrors - but if result
            // is from oldGlobal's world, unwrap it!
            return ScriptObjectMirror.unwrap(ScriptObjectMirror.wrap(load(newGlobal, from), newGlobal), oldGlobal);
        });
    }

    /**
     * Load or get a structure class. Structure class names are based on the number of parameter fields
     * and {@link AccessorProperty} fields in them. Structure classes are used to represent ScriptObjects
     *
     * @see ObjectClassGenerator
     * @see AccessorProperty
     * @see ScriptObject
     *
     * @param fullName  full name of class, e.g. org.monflabs.nashorn.internal.objects.JO2P1 contains 2 fields and 1 parameter.
     *
     * @return the {@code Class<?>} for this structure
     *
     * @throws ClassNotFoundException if structure class cannot be resolved
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends ScriptObject> forStructureClass(final String fullName) throws ClassNotFoundException {
        return (Class<? extends ScriptObject>)structureClasses.computeIfAbsent(fullName, (name) -> {
            try {
                return Class.forName(name, true, theStructLoader);
            } catch (final ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        });
    }

    /**
     * Is {@code className} the name of a structure class?
     *
     * @param className a class name
     * @return true if className is a structure class name
     */
    public static boolean isStructureClass(final String className) {
        return StructureLoader.isStructureClass(className);
    }

    /**
     * Checks that the given Class is public.
     *
     * @param clazz Class object to check
     * @return true if Class is accessible, false otherwise
     */
    public static boolean isAccessibleClass(final Class<?> clazz) {
        return Modifier.isPublic(clazz.getModifiers());
    }

    /**
     * Lookup a Java class. This is used for JSR-223 stuff linking in from
     * {@code org.monflabs.nashorn.internal.objects.NativeJava} and {@code org.monflabs.nashorn.internal.runtime.NativeJavaPackage}
     *
     * @param fullName full name of class to load
     *
     * @return the {@code Class<?>} for the name
     *
     * @throws ClassNotFoundException if class cannot be resolved
     */
    public Class<?> findClass(final String fullName) throws ClassNotFoundException {
        if (fullName.indexOf('[') != -1 || fullName.indexOf('/') != -1) {
            // don't allow array class names or internal names.
            throw new ClassNotFoundException(fullName);
        }

        // give chance to ClassFilter to filter out, if present
        if (classFilter != null && !classFilter.exposeToScripts(fullName)) {
            throw new ClassNotFoundException(fullName);
        }

        // Try finding using the "app" loader.
        if (appLoader != null) {
            return Class.forName(fullName, true, appLoader);
        } else {
            final Class<?> cl = Class.forName(fullName);
            // return the Class only if it was loaded by the boot loader
            if (cl.getClassLoader() == null) {
                return cl;
            } else {
                throw new ClassNotFoundException(fullName);
            }
        }
    }

    /**
     * Hook to print stack trace for a {@link Throwable} that occurred during
     * execution
     *
     * @param t throwable for which to dump stack
     */
    public static void printStackTrace(final Throwable t) {
        if (Context.DEBUG) {
            t.printStackTrace(Context.getCurrentErr());
        }
    }

    /**
     * Verify generated bytecode before emission. This is called back from the
     * {@link ObjectClassGenerator} or the {@link Compiler}. If the "--verify-code" parameter
     * hasn't been given, this is a nop
     *
     * Note that verification may load classes -- we don't want to do that unless
     * user specified verify option. We check it here even though caller
     * may have already checked that flag
     *
     * @param bytecode bytecode to verify
     */
    public void verify(final byte[] bytecode) {
        if (env._verify_code) {
            final ClassFile classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                    ClassHierarchyResolver.ofClassLoading(theStructLoader).cached()));
            final PrintWriter err = new PrintWriter(System.err, true);
            for (final VerifyError error : classFile.verify(bytecode)) {
                err.println(error.getMessage());
            }
        }
    }

    /**
     * Create and initialize a new global scope object.
     *
     * @return the initialized global scope object.
     */
    public Global createGlobal() {
        return initGlobal(newGlobal());
    }

    /**
     * Create a new uninitialized global scope object
     * @return the global script object
     */
    public Global newGlobal() {
        createOrInvalidateGlobalConstants();
        return new Global(this);
    }

    private void createOrInvalidateGlobalConstants() {
        for (;;) {
            final GlobalConstants currentGlobalConstants = getGlobalConstants();
            if (currentGlobalConstants != null) {
                // Subsequent invocation; we're creating our second or later Global. GlobalConstants is not safe to use
                // with more than one Global, as the constant method handle linkages it creates create a coupling
                // between the Global and the call sites in the compiled code.
                currentGlobalConstants.invalidateForever();
                return;
            }
            final GlobalConstants newGlobalConstants = new GlobalConstants(getLogger(GlobalConstants.class));
            if (globalConstantsRef.compareAndSet(null, newGlobalConstants)) {
                // First invocation; we're creating the first Global in this Context. Create the GlobalConstants object
                // for this Context.
                return;
            }

            // If we reach here, then we started out as the first invocation, but another concurrent invocation won the
            // CAS race. We'll just let the loop repeat and invalidate the CAS race winner.
        }
    }

    /**
     * Initialize given global scope object.
     *
     * @param global the global
     * @param engine the associated ScriptEngine instance, can be null
     * @return the initialized global scope object.
     */
    public Global initGlobal(final Global global, final ScriptEngine engine) {
        if (engine != null) {
            ENGINE_CONTEXTS.put(engine, this);
        }
        // Need only minimal global object, if we are just compiling.
        if (!env._compile_only) {
            // initialize global scope with builtin global objects, then the libraries' contributions
            Context.runWithGlobal(global, () -> {
                global.initBuiltinObjects(engine);
                ScriptLibraries.install(this, global, libraries);
            });
        }
        if (debugger != null) {
            debugger.globalCreated(global);
        }

        return global;
    }

    /**
     * Initialize given global scope object.
     *
     * @param global the global
     * @return the initialized global scope object.
     */
    public Global initGlobal(final Global global) {
        return initGlobal(global, null);
    }

    /**
     * Gets the Nashorn dynamic linker for the specified class. If the class is
     * a script class, the dynamic linker associated with its context is
     * returned. Otherwise the dynamic linker associated with the current
     * context is returned.
     * @param clazz the class for which we want to retrieve a dynamic linker.
     * @return the Nashorn dynamic linker for the specified class.
     */
    public static DynamicLinker getDynamicLinker(final Class<?> clazz) {
        return fromClass(clazz).dynamicLinker;
    }

    /**
     * Gets the Nashorn dynamic linker associated with the current context.
     * @return the Nashorn dynamic linker for the current context.
     */
    public static DynamicLinker getDynamicLinker() {
        return getContext().dynamicLinker;
    }

    /**
     * Creates a module layer with one module that is defined to the given class
     * loader.
     *
     * @param parent the parent layer of the new module
     * @param descriptor the module descriptor for the newly created module
     * @param loader the class loader of the module
     * @return the new Module
     */
    static Module createModuleTrusted(final ModuleLayer parent, final ModuleDescriptor descriptor, final ClassLoader loader) {
        final String mn = descriptor.name();

        final ModuleReference mref = new ModuleReference(descriptor, null) {
            @Override
            public ModuleReader open() {
                throw new UnsupportedOperationException();
            }
        };

        final ModuleFinder finder = new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(final String name) {
                if (name.equals(mn)) {
                    return Optional.of(mref);
                } else {
                    return Optional.empty();
                }
            }
            @Override
            public Set<ModuleReference> findAll() {
                return Set.of(mref);
            }
        };

        final Configuration cf = parent.configuration()
                .resolve(finder, ModuleFinder.of(), Set.of(mn));

        final ModuleLayer layer = parent.defineModules(cf, name -> loader);

        final Module m = layer.findModule(mn).get();
        assert m.getLayer() == layer;

        return m;
    }

    public static Context getContextTrustedOrNull() {
        final Global global = Context.getGlobal();
        return global == null ? null : getContext(global);
    }

    private static Context getContext(final Global global) {
        // We can't invoke Global.getContext() directly, as it's a protected override, and Global isn't in our package.
        // In order to access the method, we must cast it to ScriptObject first (which is in our package) and then let
        // virtual invocation do its thing.
        return ((ScriptObject)global).getContext();
    }

    /**
     * Try to infer Context instance from the Class. If we cannot,
     * then get it from the thread local variable.
     *
     * @param clazz the class
     * @return context
     */
    static Context fromClass(final Class<?> clazz) {
        ClassLoader loader = null;
        try {
            loader = clazz.getClassLoader();
        } catch (final SecurityException ignored) {
            // This could fail because of anonymous classes being used.
            // Accessing loader of anonymous class fails (for extension
            // loader class too?). In any case, for us fetching Context
            // from class loader is just an optimization. We can always
            // get Context from thread local storage (below).
        }

        if (loader instanceof ScriptLoader) {
            return ((ScriptLoader)loader).getContext();
        }

        return getContext();
    }

    private URL getResourceURL(final String resName) {
        if (appLoader != null) {
            return appLoader.getResource(resName);
        }
        return ClassLoader.getSystemResource(resName);
    }

    Object evaluateSource(final Source source, final ScriptObject scope, final ScriptObject thiz) {
        ScriptFunction script = null;

        try {
            script = compileScript(source, scope, new Context.ThrowErrorManager());
        } catch (final ParserException e) {
            e.throwAsEcmaException();
        }

        return ScriptRuntime.apply(script, thiz);
    }

    private static ScriptFunction getProgramFunction(final Class<?> script, final ScriptObject scope) {
        if (script == null) {
            return null;
        }
        return invokeCreateProgramFunctionHandle(getCreateProgramFunctionHandle(script), scope);
    }

    private static MethodHandle getCreateProgramFunctionHandle(final Class<?> script) {
        try {
            return LOOKUP.findStatic(script, CREATE_PROGRAM_FUNCTION.symbolName(), CREATE_PROGRAM_FUNCTION_TYPE);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Failed to retrieve a handle for the program function for " + script.getName(), e);
        }
    }

    private static ScriptFunction invokeCreateProgramFunctionHandle(final MethodHandle createProgramFunctionHandle, final ScriptObject scope) {
        try {
            return (ScriptFunction)createProgramFunctionHandle.invokeExact(scope);
        } catch (final RuntimeException|Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new AssertionError("Failed to create a program function", t);
        }
    }

    private ScriptFunction compileScript(final Source source, final ScriptObject scope, final ErrorManager errMan) {
        return getProgramFunction(compile(source, errMan, this._strict), scope);
    }

    /**
     * Parses a module. Its body is a function rather than a program: its top
     * level declarations belong to the module, not to the global object.
     */
    private FunctionNode compileModuleNode(final Source source) {
        // start with no errors, no warnings, as script compilation does: the
        // error manager belongs to the context and outlives any one parse, so
        // an error left by an earlier one is not this module's
        errors.reset();
        final FunctionNode moduleNode =
                // the name becomes a method name, so it is the kind that programs use
                // rather than the path the module was resolved to
                new Parser(env, source, errors, true, getLogger(Parser.class)).parseModule(":module");
        if (errors.hasErrors()) {
            throw new ParserException(errors.getNumberOfErrors() + " module parse error(s) in " + source.getName());
        }
        if (debugger != null) {
            DebugLocations.collect(moduleNode, source);
            debugger.scriptCompiled(source, true);
        }
        return moduleNode;
    }

    private synchronized Class<?> compileModule(final Source source, final FunctionNode moduleNode) {
        final ScriptLoader loader = env._loader_per_compile ? createNewLoader() : scriptLoader;
        final CodeInstaller installer = new NamedContextCodeInstaller(this, loader);
        final Compiler compiler = Compiler.forInitialCompilation(installer, source, errors, true);
        final FunctionNode compiled = compiler.compile(moduleNode, Compiler.CompilationPhases.COMPILE_ALL);
        if (errors.hasErrors()) {
            throw new ParserException(errors.getNumberOfErrors() + " module compile error(s) in " + source.getName());
        }
        return compiled.getRootClass();
    }

    private synchronized Class<?> compile(final Source source, final ErrorManager errMan, final boolean strict) {
        return compile(source, errMan, strict, false, false);
    }

    private synchronized Class<?> compile(final Source source, final ErrorManager errMan, final boolean strict,
            final boolean newTargetAllowed, final boolean superAllowed) {
        // start with no errors, no warnings.
        errMan.reset();

        // what the same text parses to depends on the eval it was written in,
        // so a class compiled for one is not the class another one wants
        final boolean cacheable = !newTargetAllowed && !superAllowed && !mayTagATemplate(source);

        Class<?> script = cacheable ? findCachedClass(source) : null;
        if (script != null) {
            final DebugLogger log = getLogger(Compiler.class);
            if (log.isEnabled()) {
                log.fine(new RuntimeEvent<>(Level.INFO, source), "Code cache hit for ", source, " avoiding recompile.");
            }
            if (debugger != null) {
                debugger.scriptCompiled(source, false);
            }
            return script;
        }

        StoredScript storedScript = null;
        FunctionNode functionNode = null;
        // Don't use code store if optimistic types is enabled but lazy compilation is not.
        // This would store a full script compilation with many wrong optimistic assumptions that would
        // do more harm than good on later runs with both optimistic types and lazy compilation enabled.
        final boolean useCodeStore = cacheable && codeStore != null && !env._parse_only && (!env._optimistic_types || env._lazy_compilation);
        final String cacheKey = useCodeStore ? CodeStore.getCacheKey("script", null) : null;

        if (useCodeStore) {
            storedScript = codeStore.load(source, cacheKey);
        }

        if (storedScript == null) {
            if (env._dest_dir != null) {
                source.dump(env._dest_dir);
            }

            final Parser parser = new Parser(env, source, errMan, strict, getLogger(Parser.class));
            parser.setEvalContext(newTargetAllowed, superAllowed);
            functionNode = parser.parse();

            if (errMan.hasErrors()) {
                return null;
            }

            if (debugger != null) {
                // the whole program is parsed here, nested functions included,
                // which is where the breakable positions come from: what gets
                // compiled later, lazily, is only ever a part of this tree
                DebugLocations.collect(functionNode, source);
            }

            if (env._print_ast || functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_AST)) {
                getErr().println(new ASTWriter(functionNode));
            }

            if (env._print_parse || functionNode.getDebugFlag(FunctionNode.DEBUG_PRINT_PARSE)) {
                getErr().println(new PrintVisitor(functionNode, true, false));
            }
        }

        if (env._parse_only) {
            return null;
        }

        final CodeInstaller installer;
        if (env._persistent_cache || !env._lazy_compilation || !env.useAnonymousClasses(source.getLength(), () -> AnonymousContextCodeInstaller.initFailure) ) {
            // Persistent code cache, eager compilation, or inability to use Unsafe.defineAnonymousClass (typically, JDK 17+)
            // preclude use of VM anonymous classes
            final ScriptLoader loader = env._loader_per_compile ? createNewLoader() : scriptLoader;
            installer = new NamedContextCodeInstaller(this, loader);
        } else {
            installer = new AnonymousContextCodeInstaller(this,
                    anonymousHostClasses.getOrCreate(source.getURL(), key ->
                            createNewLoader().installClass(
                                    // NOTE: we're defining these constants in AnonymousContextCodeInstaller so they are not
                                    // initialized if we don't use AnonymousContextCodeInstaller. As this method is only ever
                                    // invoked from AnonymousContextCodeInstaller, this is okay.
                                    AnonymousContextCodeInstaller.ANONYMOUS_HOST_CLASS_NAME,
                                    AnonymousContextCodeInstaller.ANONYMOUS_HOST_CLASS_BYTES)));
        }

        if (storedScript == null) {
            final CompilationPhases phases = Compiler.CompilationPhases.COMPILE_ALL;

            final Compiler compiler = Compiler.forInitialCompilation(
                    installer,
                    source,
                    errMan,
                    strict | functionNode.isStrict());

            final FunctionNode compiledFunction = compiler.compile(functionNode, phases);
            if (errMan.hasErrors()) {
                return null;
            }
            script = compiledFunction.getRootClass();
            compiler.persistClassInfo(cacheKey, compiledFunction);
        } else {
            Compiler.updateCompilationId(storedScript.getCompilationId());
            script = storedScript.installScript(source, installer);
        }

        if (cacheable) {
            cacheClass(source, script);
        }
        if (debugger != null) {
            debugger.scriptCompiled(source, false);
        }
        return script;
    }

    /**
     * Whether this is eval code that may hold a template literal.
     *
     * 12.2.9.3 hands out one template object per parse node, and evaluating the
     * same string twice parses it twice, so the two are different sites and get
     * different objects. Reusing the compiled class would make them one site,
     * because the site is the source the class was compiled from. Code that
     * holds no backquote cannot tag a template, and is cached as before.
     *
     * @param source the source about to be compiled
     * @return true if the class must not be cached for it
     */
    private static boolean mayTagATemplate(final Source source) {
        if (!source.isEvalCode()) {
            return false;
        }
        for (final char c : source.getContent()) {
            if (c == '`') {
                return true;
            }
        }
        return false;
    }

    private ScriptLoader createNewLoader() {
        return new ScriptLoader(Context.this);
    }

    private long getUniqueScriptId() {
        return uniqueScriptId.getAndIncrement();
    }

    /**
     * Cache for compiled script classes.
     */
    @SuppressWarnings("serial")
    @Logger(name="classcache")
    private static class ClassCache extends LinkedHashMap<Source, ClassReference> implements Loggable {
        private final int size;
        private final ReferenceQueue<Class<?>> queue;
        private final DebugLogger log;

        ClassCache(final Context context, final int size) {
            super(size, 0.75f, true);
            this.size = size;
            this.queue = new ReferenceQueue<>();
            this.log   = initLogger(context);
        }

        void cache(final Source source, final Class<?> clazz) {
            if (log.isEnabled()) {
                log.info("Caching ", source, " in class cache");
            }
            put(source, new ClassReference(clazz, queue, source));
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<Source, ClassReference> eldest) {
            return size() > size;
        }

        @Override
        public ClassReference get(final Object key) {
            for (ClassReference ref; (ref = (ClassReference)queue.poll()) != null; ) {
                final Source source = ref.source;
                if (log.isEnabled()) {
                    log.info("Evicting ", source, " from class cache.");
                }
                remove(source);
            }

            final ClassReference ref = super.get(key);
            if (ref != null && log.isEnabled()) {
                log.info("Retrieved class reference for ", ref.source, " from class cache");
            }
            return ref;
        }

        @Override
        public DebugLogger initLogger(final Context context) {
            return context.getLogger(getClass());
        }

        @Override
        public DebugLogger getLogger() {
            return log;
        }

    }

    private static class ClassReference extends SoftReference<Class<?>> {
        private final Source source;

        ClassReference(final Class<?> clazz, final ReferenceQueue<Class<?>> queue, final Source source) {
            super(clazz, queue);
            this.source = source;
        }
    }

    // Class cache management
    private Class<?> findCachedClass(final Source source) {
        final ClassReference ref = classCache == null ? null : classCache.get(source);
        return ref != null ? ref.get() : null;
    }

    private void cacheClass(final Source source, final Class<?> clazz) {
        if (classCache != null) {
            classCache.cache(source, clazz);
        }
    }

    // logging
    private final Map<String, DebugLogger> loggers = new HashMap<>();

    private void initLoggers() {
        ((Loggable)MethodHandleFactory.getFunctionality()).initLogger(this);
    }

    /**
     * Get a logger, given a loggable class
     * @param clazz a Loggable class
     * @return debuglogger associated with that class
     */
    public DebugLogger getLogger(final Class<? extends Loggable> clazz) {
        return getLogger(clazz, null);
    }

    /**
     * Get a logger, given a loggable class
     * @param clazz a Loggable class
     * @param initHook an init hook - if this is the first time the logger is created in the context, run the init hook
     * @return debuglogger associated with that class
     */
    public DebugLogger getLogger(final Class<? extends Loggable> clazz, final Consumer<DebugLogger> initHook) {
        final String name = getLoggerName(clazz);
        DebugLogger logger = loggers.get(name);
        if (logger == null) {
            if (!env.hasLogger(name)) {
                return DebugLogger.DISABLED_LOGGER;
            }
            final LoggerInfo info = env._loggers.get(name);
            logger = new DebugLogger(name, info.getLevel(), info.isQuiet());
            if (initHook != null) {
                initHook.accept(logger);
            }
            loggers.put(name, logger);
        }
        return logger;
    }

    /**
     * Given a Loggable class, weave debug info info a method handle for that logger.
     * Level.INFO is used
     *
     * @param clazz loggable
     * @param mh    method handle
     * @param text  debug printout to add
     *
     * @return instrumented method handle, or null if logger not enabled
     */
    public MethodHandle addLoggingToHandle(final Class<? extends Loggable> clazz, final MethodHandle mh, final Supplier<String> text) {
        return addLoggingToHandle(clazz, Level.INFO, mh, Integer.MAX_VALUE, false, text);
    }

    /**
     * Given a Loggable class, weave debug info info a method handle for that logger.
     *
     * @param clazz            loggable
     * @param level            log level
     * @param mh               method handle
     * @param paramStart       first parameter to print
     * @param printReturnValue should we print the return value?
     * @param text             debug printout to add
     *
     * @return instrumented method handle, or null if logger not enabled
     */
    public MethodHandle addLoggingToHandle(final Class<? extends Loggable> clazz, final Level level, final MethodHandle mh, final int paramStart, final boolean printReturnValue, final Supplier<String> text) {
        final DebugLogger log = getLogger(clazz);
        if (log.isEnabled()) {
            return MethodHandleFactory.addDebugPrintout(log, level, mh, paramStart, printReturnValue, text.get());
        }
        return mh;
    }

    private static String getLoggerName(final Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            final Logger log = current.getAnnotation(Logger.class);
            if (log != null) {
                assert !"".equals(log.name());
                return log.name();
            }
            current = current.getSuperclass();
        }
        assert false;
        return null;
    }

    /**
     * This is a special kind of switchpoint used to guard builtin
     * properties and prototypes. In the future it might contain
     * logic to e.g. multiple switchpoint classes.
     */
    public static final class BuiltinSwitchPoint extends SwitchPoint {
        //empty
    }

    /**
     * Return the builtin switchpoint for a particular key name. A new switchpoint
     * is atomically created if it doesn't exist yet.
     * @param name key name
     * @return builtin switchpoint
     */
    public SwitchPoint getBuiltinSwitchPoint(final String name) {
        return builtinSwitchPoints.computeIfAbsent(name, n -> new BuiltinSwitchPoint());
    }

    private static ClassLoader createModuleLoader(final ClassLoader cl,
            final String modulePath, final String addModules) {
        if (addModules == null) {
            throw new IllegalArgumentException("--module-path specified with no --add-modules");
        }

        final Path[] paths = Stream.of(modulePath.split(File.pathSeparator))
            .map(Paths::get)
            .toArray(Path[]::new);
        final ModuleFinder mf = ModuleFinder.of(paths);
        final Set<ModuleReference> mrefs = mf.findAll();
        if (mrefs.isEmpty()) {
            throw new RuntimeException("No modules in script --module-path: " + modulePath);
        }

        final Set<String> rootMods;
        if (addModules.equals("ALL-MODULE-PATH")) {
            rootMods = mrefs.stream().
                map(mr->mr.descriptor().name()).
                collect(Collectors.toSet());
        } else {
            rootMods = Stream.of(addModules.split(",")).
                map(String::trim).
                collect(Collectors.toSet());
        }

        final ModuleLayer boot = ModuleLayer.boot();
        final Configuration conf = boot.configuration().
            resolve(mf, ModuleFinder.of(), rootMods);
        final String firstMod = rootMods.iterator().next();
        return boot.defineModulesWithOneLoader(conf, cl).findLoader(firstMod);
    }
}
