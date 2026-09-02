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

package org.monflabs.nashorn.internal.runtime.linker;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.ACC_SUPER;
import static java.lang.classfile.ClassFile.ACC_VARARGS;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.monflabs.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static org.monflabs.nashorn.internal.lookup.Lookup.MH;

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.monflabs.nashorn.internal.codegen.CompilerConstants.Call;
import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;

/**
 * Generates bytecode for a Java adapter class. Used by the {@link JavaAdapterFactory}.
 * </p><p>
 * For every protected or public constructor in the extended class, the adapter class will have either one or two
 * public constructors (visibility of protected constructors in the extended class is promoted to public).
 * <li>
 * <li>For adapter classes with instance-level overrides, a constructor taking a trailing ScriptObject argument preceded
 * by original constructor arguments is always created on the adapter class. When such a constructor is invoked, the
 * passed ScriptObject's member functions are used to implement and/or override methods on the original class,
 * dispatched by name. A single JavaScript function will act as the implementation for all overloaded methods of the
 * same name. When methods on an adapter instance are invoked, the functions are invoked having the ScriptObject passed
 * in the instance constructor as their "this". Subsequent changes to the ScriptObject (reassignment or removal of its
 * functions) will be reflected in the adapter instance as it is live dispatching to its members on every method invocation.
 * {@code java.lang.Object} methods {@code equals}, {@code hashCode}, and {@code toString} can also be overridden. The
 * only restriction is that since every JavaScript object already has a {@code toString} function through the
 * {@code Object.prototype}, the {@code toString} in the adapter is only overridden if the passed ScriptObject has a
 * {@code toString} function as its own property, and not inherited from a prototype. All other adapter methods can be
 * implemented or overridden through a prototype-inherited function of the ScriptObject passed to the constructor too.
 * </li>
 * <li>
 * If the original types collectively have only one abstract method, or have several of them, but all share the
 * same name, an additional constructor for instance-level override adapter is provided for every original constructor;
 * this one takes a ScriptFunction as its last argument preceded by original constructor arguments. This constructor
 * will use the passed function as the implementation for all abstract methods. For consistency, any concrete methods
 * sharing the single abstract method name will also be overridden by the function. When methods on the adapter instance
 * are invoked, the ScriptFunction is invoked with UNDEFINED or Global as its "this" depending whether the function is
 * strict or not.
 * </li>
 * <li>
 * If the adapter being generated has class-level overrides, constructors taking same arguments as the superclass
 * constructors are created. These constructors simply delegate to the superclass constructor. They are simply used to
 * create instances of the adapter class, with no instance-level overrides, as they don't have them. If the original
 * class' constructor was variable arity, the adapter constructor will also be variable arity. Protected constructors
 * are exposed as public.
 * </li>
 * </ul>
 * </p><p>
 * For adapter methods that return values, all the JavaScript-to-Java conversions supported by Nashorn will be in effect
 * to coerce the JavaScript function return value to the expected Java return type.
 * </p><p>
 * Since we are adding a trailing argument to the generated constructors in the adapter class with instance-level overrides, they will never be
 * declared as variable arity, even if the original constructor in the superclass was declared as variable arity. The
 * reason we are passing the additional argument at the end of the argument list instead at the front is that the
 * source-level script expression <code>new X(a, b) { ... }</code> (which is a proprietary syntax extension Nashorn uses
 * to resemble Java anonymous classes) is actually equivalent to <code>new X(a, b, { ... })</code>.
 * </p><p>
 * It is possible to create two different adapter classes: those that can have class-level overrides, and those that can
 * have instance-level overrides. When {@link JavaAdapterFactory#getAdapterClassFor(Class[], ScriptObject)}
 * or {@link JavaAdapterFactory#getAdapterClassFor(Class[], ScriptObject)} is invoked
 * with non-null {@code classOverrides} parameter, an adapter class is created that can have class-level overrides, and
 * the passed script object will be used as the implementations for its methods, just as in the above case of the
 * constructor taking a script object. Note that in the case of class-level overrides, a new adapter class is created on
 * every invocation, and the implementation object is bound to the class, not to any instance. All created instances
 * will share these functions. If it is required to have both class-level overrides and instance-level overrides, the
 * class-level override adapter class should be subclassed with an instance-override adapter. Since adapters delegate to
 * super class when an overriding method handle is not specified, this will behave as expected.
 * TODO: see if below described limitation could be lifted now that java.security.ProtectionDomain is no longer used.
 * It is not possible to
 * have both class-level and instance-level overrides in the same class for security reasons: adapter classes are
 * defined with a protection domain of their creator code, and an adapter class that has both class and instance level
 * overrides would need to have two potentially different protection domains: one for class-based behavior and one for
 * instance-based behavior; since Java classes can only belong to a single protection domain, this could not be
 * implemented securely.
 */
final class JavaAdapterBytecodeGenerator {
    // Field names in adapters
    private static final String GLOBAL_FIELD_NAME = "global";
    private static final String DELEGATE_FIELD_NAME = "delegate";
    private static final String IS_FUNCTION_FIELD_NAME = "isFunction";
    private static final String CALL_THIS_FIELD_NAME = "callThis";

    // Initializer names
    private static final String INIT = "<init>";
    private static final String CLASS_INIT = "<clinit>";

    // Types often used in generated bytecode
    private static final ClassDesc SCRIPT_OBJECT_TYPE = classDesc(ScriptObject.class);
    private static final ClassDesc SCRIPT_FUNCTION_TYPE = classDesc(ScriptFunction.class);
    private static final ClassDesc SCRIPT_OBJECT_MIRROR_TYPE = classDesc(ScriptObjectMirror.class);
    private static final MethodTypeDesc DISPATCH_BRIDGE_TYPE = MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)Ljava/lang/Object;");

    // JavaAdapterServices methods used in generated bytecode
    private static final Call CHECK_FUNCTION = lookupServiceMethod("checkFunction", ScriptFunction.class, Object.class, String.class);
    private static final Call EXPORT_RETURN_VALUE = lookupServiceMethod("exportReturnValue", Object.class, Object.class);
    private static final Call GET_CALL_THIS = lookupServiceMethod("getCallThis", Object.class, ScriptFunction.class, Object.class);
    private static final Call GET_CLASS_OVERRIDES = lookupServiceMethod("getClassOverrides", ScriptObject.class);
    private static final Call GET_NON_NULL_GLOBAL = lookupServiceMethod("getNonNullGlobal", ScriptObject.class);
    private static final Call HAS_OWN_TO_STRING = lookupServiceMethod("hasOwnToString", boolean.class, ScriptObject.class);
    private static final Call NOT_AN_OBJECT = lookupServiceMethod("notAnObject", void.class, Object.class);
    private static final Call SAME_GLOBAL = lookupServiceMethod("sameGlobal", boolean.class, ScriptObject.class);
    private static final Call CALL_IN_GLOBAL = lookupServiceMethod("callInGlobal", Object.class, ScriptObject.class, MethodHandle.class, Object[].class);
    private static final Call TO_CHAR_PRIMITIVE = lookupServiceMethod("toCharPrimitive", char.class, Object.class);
    private static final Call UNSUPPORTED = lookupServiceMethod("unsupported", UnsupportedOperationException.class);
    private static final Call WRAP_THROWABLE = lookupServiceMethod("wrapThrowable", RuntimeException.class, Throwable.class);
    private static final Call UNWRAP_MIRROR = lookupServiceMethod("unwrapMirror", ScriptObject.class, Object.class, boolean.class);

    // Other methods invoked by the generated bytecode
    private static final Call UNWRAP = staticCallNoLookup(ScriptUtils.class, "unwrap", Object.class, Object.class);
    private static final Call CHAR_VALUE_OF = staticCallNoLookup(Character.class, "valueOf", Character.class, char.class);
    private static final Call DOUBLE_VALUE_OF = staticCallNoLookup(Double.class, "valueOf", Double.class, double.class);
    private static final Call LONG_VALUE_OF = staticCallNoLookup(Long.class, "valueOf", Long.class, long.class);

    // Bootstrap methods of the call sites in the generated bytecode
    private static final DirectMethodHandleDesc BOOTSTRAP_HANDLE = serviceBootstrap("bootstrap",
            MethodType.methodType(CallSite.class, Lookup.class, String.class, MethodType.class, int.class));

    private static final DirectMethodHandleDesc CREATE_ARRAY_BOOTSTRAP_HANDLE = serviceBootstrap("createArrayBootstrap",
            MethodType.methodType(CallSite.class, Lookup.class, String.class, MethodType.class));

    // Throwables caught by the generated bytecode
    private static final ClassDesc RUNTIME_EXCEPTION_TYPE = classDesc(RuntimeException.class);
    private static final ClassDesc ERROR_TYPE = classDesc(Error.class);
    private static final ClassDesc THROWABLE_TYPE = classDesc(Throwable.class);

    // Some more frequently used method descriptors
    private static final MethodTypeDesc GET_METHOD_PROPERTY_METHOD_TYPE = MethodTypeDesc.of(CD_Object, SCRIPT_OBJECT_TYPE);
    private static final MethodTypeDesc VOID_METHOD_TYPE = MethodTypeDesc.of(CD_void);

    private static final String ADAPTER_PACKAGE_INTERNAL = "org/monflabs/nashorn/javaadapters/";
    private static final int MAX_GENERATED_TYPE_NAME_LENGTH = 255;

    // Method name prefix for invoking super-methods
    static final String SUPER_PREFIX = "super$";
    private static final String IMPL_PREFIX = "$$nashorn$impl$";
    private static final String BRIDGE_PREFIX = "$$nashorn$bridge$";

    private static final String CALLER_SENSITIVE_CLASS_NAME = "jdk.internal.reflect.CallerSensitive";

    /**
     * Collection of methods we never override: Object.clone(), Object.finalize().
     */
    private static final Collection<MethodInfo> EXCLUDED = getExcludedMethods();

    // This is the superclass for our generated adapter.
    private final Class<?> superClass;
    // Interfaces implemented by our generated adapter.
    private final List<Class<?>> interfaces;
    // Class loader used as the parent for the class loader we'll create to load the generated class. It will be a class
    // loader that has the visibility of all original types (class to extend and interfaces to implement) and of the
    // Nashorn classes.
    private final ClassLoader commonLoader;
    // Is this a generator for the version of the class that can have overrides on the class level?
    private final boolean classOverride;
    // Binary name of the superClass
    private final String superClassName;
    // Binary name of the generated class.
    private final String generatedClassName;
    // The superclass and the generated class, as the class file writer sees them
    private final ClassDesc superType;
    private final ClassDesc generatedType;
    private final Set<String> abstractMethodNames = new HashSet<>();
    private final String samName;
    private final Set<MethodInfo> finalMethods = new HashSet<>(EXCLUDED);
    private final Set<MethodInfo> methodInfos = new HashSet<>();
    private final boolean autoConvertibleFromFunction;

    /** The generated class. */
    private final byte[] classBytes;

    /** The builder for {@link #classBytes}; only valid while the constructor runs. */
    private ClassBuilder clb;

    /**
     * Creates a generator for the bytecode for the adapter for the specified superclass and interfaces.
     * @param superClass the superclass the adapter will extend.
     * @param interfaces the interfaces the adapter will implement.
     * @param commonLoader the class loader that can see all of superClass, interfaces, and Nashorn classes.
     * @param classOverride true to generate the bytecode for the adapter that has class-level overrides, false to
     * generate the bytecode for the adapter that has instance-level overrides.
     */
    JavaAdapterBytecodeGenerator(final Class<?> superClass, final List<Class<?>> interfaces,
                                 final ClassLoader commonLoader, final boolean classOverride) {
        assert superClass != null && !superClass.isInterface();
        assert interfaces != null;

        this.superClass = superClass;
        this.interfaces = interfaces;
        this.classOverride = classOverride;
        this.commonLoader = commonLoader;
        superClassName = internalName(superClass);
        generatedClassName = getGeneratedClassName(superClass, interfaces);
        superType = classDesc(superClass);
        generatedType = ClassDesc.ofInternalName(generatedClassName);

        gatherMethods(superClass);
        gatherMethods(interfaces);
        samName = abstractMethodNames.size() == 1 ? abstractMethodNames.iterator().next() : null;

        // one pass over the whole class: every method below writes straight into clb
        final boolean[] autoConvertible = new boolean[1];
        classBytes = classFile().build(generatedType, builder -> {
            clb = builder;
            clb.withVersion(ClassFile.JAVA_8_VERSION, 0);
            clb.withFlags(ACC_PUBLIC | ACC_SUPER);
            clb.withSuperclass(superType);
            clb.withInterfaceSymbols(classDescs(interfaces));

            generateField(GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);
            generateField(DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
            if (samName != null) {
                generateField(CALL_THIS_FIELD_NAME, CD_Object);
                generateField(IS_FUNCTION_FIELD_NAME, CD_boolean);
            }
            if (classOverride) {
                generateClassInit();
            }
            autoConvertible[0] = generateConstructors();
            generateMethods();
            generateSuperMethods();
            clb = null;
        });
        autoConvertibleFromFunction = autoConvertible[0];
    }

    /**
     * Stack map generation has to be able to find the common supertype of two
     * types, and only this factory's loader can see all of them.
     */
    private ClassFile classFile() {
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofClassLoading(commonLoader)
                .orElse(classDesc -> ClassHierarchyInfo.ofClass(CD_Object))
                .cached()));
    }

    private void generateField(final String name, final ClassDesc fieldType) {
        clb.withField(name, fieldType, ACC_PRIVATE | ACC_FINAL | (classOverride ? ACC_STATIC : 0));
    }

    JavaAdapterClassLoader createAdapterClassLoader() {
        return new JavaAdapterClassLoader(generatedClassName, classBytes);
    }

    /**
     * Adds a method to the adapter, with the given declared exceptions.
     */
    private void withMethod(final int flags, final String name, final MethodTypeDesc type,
            final List<ClassDesc> exceptions, final java.util.function.Consumer<CodeBuilder> body) {
        clb.withMethod(name, type, flags, mb -> {
            if (!exceptions.isEmpty()) {
                mb.with(ExceptionsAttribute.ofSymbols(exceptions));
            }
            mb.withCode(body);
        });
    }

    private static ClassDesc classDesc(final Class<?> clazz) {
        return org.monflabs.nashorn.internal.codegen.types.Type.classDesc(clazz);
    }

    private static List<ClassDesc> classDescs(final List<Class<?>> classes) {
        final List<ClassDesc> descs = new ArrayList<>(classes.size());
        for (final Class<?> clazz : classes) {
            descs.add(classDesc(clazz));
        }
        return descs;
    }

    private static DirectMethodHandleDesc serviceBootstrap(final String name, final MethodType type) {
        return MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
                classDesc(JavaAdapterServices.class), name,
                MethodTypeDesc.ofDescriptor(type.toMethodDescriptorString()));
    }

    boolean isAutoConvertibleFromFunction() {
        return autoConvertibleFromFunction;
    }

    private static String getGeneratedClassName(final Class<?> superType, final List<Class<?>> interfaces) {
        // The class we use to primarily name our adapter is either the superclass, or if it is Object (meaning we're
        // just implementing interfaces or extending Object), then the first implemented interface or Object.
        final Class<?> namingType = superType == Object.class ? (interfaces.isEmpty()? Object.class : interfaces.get(0)) : superType;
        final String namingTypeName = internalName(namingType);
        final StringBuilder buf = new StringBuilder();
        buf.append(ADAPTER_PACKAGE_INTERNAL).append(namingTypeName.replace('/', '_'));
        final Iterator<Class<?>> it = interfaces.iterator();
        if(superType == Object.class && it.hasNext()) {
            it.next(); // Skip first interface, it was used to primarily name the adapter
        }
        // Append interface names to the adapter name
        while(it.hasNext()) {
            buf.append("$$").append(it.next().getSimpleName());
        }
        return buf.substring(0, Math.min(MAX_GENERATED_TYPE_NAME_LENGTH, buf.length()));
    }

    private static String internalName(final Class<?> clazz) {
        return org.monflabs.nashorn.internal.codegen.types.Type.getInternalName(clazz);
    }

    private void generateClassInit() {
        withMethod(ACC_STATIC, CLASS_INIT, VOID_METHOD_TYPE, List.of(), cb -> {
            // Assign "global = Context.getGlobal()"
            GET_NON_NULL_GLOBAL.invoke(cb);
            cb.putstatic(generatedType, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);

            GET_CLASS_OVERRIDES.invoke(cb);
            if (samName != null) {
                // If the class is a SAM, allow having ScriptFunction passed as class overrides
                cb.dup();
                cb.instanceOf(SCRIPT_FUNCTION_TYPE);
                cb.dup();
                cb.putstatic(generatedType, IS_FUNCTION_FIELD_NAME, CD_boolean);
                final Label notFunction = cb.newLabel();
                cb.ifeq(notFunction);
                cb.dup();
                cb.checkcast(SCRIPT_FUNCTION_TYPE);
                emitInitCallThis(cb);
                cb.labelBinding(notFunction);
            }
            cb.putstatic(generatedType, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);

            cb.return_();
        });
    }

    /**
     * Emit bytecode for initializing the "callThis" field.
     */
    private void emitInitCallThis(final CodeBuilder cb) {
        loadField(cb, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);
        GET_CALL_THIS.invoke(cb);
        if(classOverride) {
            cb.putstatic(generatedType, CALL_THIS_FIELD_NAME, CD_Object);
        } else {
            // It is presumed ALOAD 0 was already executed
            cb.putfield(generatedType, CALL_THIS_FIELD_NAME, CD_Object);
        }
    }

    private boolean generateConstructors() {
        boolean gotCtor = false;
        boolean canBeAutoConverted = false;
        for (final Constructor<?> ctor: superClass.getDeclaredConstructors()) {
            final int modifier = ctor.getModifiers();
            if((modifier & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0 && !isCallerSensitive(ctor)) {
                canBeAutoConverted = generateConstructors(ctor) | canBeAutoConverted;
                gotCtor = true;
            }
        }
        if(!gotCtor) {
            throw JavaAdapterFactory.adaptationException(
                JavaAdapterFactory.ErrorOutcome.NO_ACCESSIBLE_CONSTRUCTOR, superClass.getCanonicalName());
        }
        return canBeAutoConverted;
    }

    private boolean generateConstructors(final Constructor<?> ctor) {
        if(classOverride) {
            // Generate a constructor that just delegates to ctor. This is used with class-level overrides, when we want
            // to create instances without further per-instance overrides.
            generateDelegatingConstructor(ctor);
            return false;
        }

        // Generate a constructor that delegates to ctor, but takes an additional ScriptObject parameter at the
        // beginning of its parameter list.
        generateOverridingConstructor(ctor, false);

        if (samName == null) {
            return false;
        }
        // If all our abstract methods have a single name, generate an additional constructor, one that takes a
        // ScriptFunction as its first parameter and assigns it as the implementation for all abstract methods.
        generateOverridingConstructor(ctor, true);
        // If the original type only has a single abstract method name, as well as a default ctor, then it can
        // be automatically converted from JS function.
        return ctor.getParameterTypes().length == 0;
    }

    private void generateDelegatingConstructor(final Constructor<?> ctor) {
        final MethodTypeDesc originalCtorType = constructorType(ctor);

        // All constructors must be public, even if in the superclass they were protected.
        withMethod(ACC_PUBLIC | (ctor.isVarArgs() ? ACC_VARARGS : 0), INIT, originalCtorType, List.of(), cb -> {
            emitSuperConstructorCall(cb, originalCtorType);
            cb.return_();
        });
    }

    /** The type of a constructor, as a {@code void} returning method. */
    private static MethodTypeDesc constructorType(final Constructor<?> ctor) {
        final Class<?>[] params = ctor.getParameterTypes();
        final ClassDesc[] paramTypes = new ClassDesc[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = classDesc(params[i]);
        }
        return MethodTypeDesc.of(CD_void, paramTypes);
    }

    /**
     * Generates a constructor for the instance adapter class. This constructor will take the same arguments as the supertype
     * constructor passed as the argument here, and delegate to it. However, it will take an additional argument of
     * either ScriptObject or ScriptFunction type (based on the value of the "fromFunction" parameter), and initialize
     * all the method handle fields of the adapter instance with functions from the script object (or the script
     * function itself, if that's what's passed). Additionally, it will create another constructor with an additional
     * Object type parameter that can be used for ScriptObjectMirror objects.
     * The constructor will also store the Nashorn global that was current at the constructor
     * invocation time in a field named "global". The generated constructor will be public, regardless of whether the
     * supertype constructor was public or protected. The generated constructor will not be variable arity, even if the
     * supertype constructor was.
     * @param ctor the supertype constructor that is serving as the base for the generated constructor.
     * @param fromFunction true if we're generating a constructor that initializes SAM types from a single
     * ScriptFunction passed to it, false if we're generating a constructor that initializes an arbitrary type from a
     * ScriptObject passed to it.
     */
    private void generateOverridingConstructor(final Constructor<?> ctor, final boolean fromFunction) {
        final MethodTypeDesc originalCtorType = constructorType(ctor);

        // Insert ScriptFunction|ScriptObject as the last argument to the constructor
        final MethodTypeDesc newCtorType = originalCtorType.insertParameterTypes(
                originalCtorType.parameterCount(), fromFunction ? SCRIPT_FUNCTION_TYPE : SCRIPT_OBJECT_TYPE);

        // All constructors must be public, even if in the superclass they were protected.
        // Existing super constructor <init>(this, args...) triggers generating <init>(this, args..., delegate).
        // Any variable arity constructors become fixed-arity with explicit array arguments.
        withMethod(ACC_PUBLIC, INIT, newCtorType, List.of(), cb -> {
            // First, invoke super constructor with original arguments.
            final int extraArgOffset = emitSuperConstructorCall(cb, originalCtorType);

            // Assign "this.global = Context.getGlobal()"
            cb.aload(0);
            GET_NON_NULL_GLOBAL.invoke(cb);
            cb.putfield(generatedType, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);

            // Assign "this.delegate = delegate"
            cb.aload(0);
            cb.aload(extraArgOffset);
            cb.putfield(generatedType, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);

            if (fromFunction) {
                // Assign "isFunction = true"
                cb.aload(0);
                cb.iconst_1();
                cb.putfield(generatedType, IS_FUNCTION_FIELD_NAME, CD_boolean);

                cb.aload(0);
                cb.aload(extraArgOffset);
                emitInitCallThis(cb);
            }

            cb.return_();
        });

        if (! fromFunction) {
            final MethodTypeDesc objectParamCtorType = originalCtorType.insertParameterTypes(
                    originalCtorType.parameterCount(), CD_Object);
            withMethod(ACC_PUBLIC, INIT, objectParamCtorType, List.of(),
                    cb -> generateOverridingConstructorWithObjectParam(cb, originalCtorType));
        }
    }

    // Object additional param accepting constructor for handling ScriptObjectMirror objects, which are
    // unwrapped to work as ScriptObjects or ScriptFunctions. This also handles null and undefined values for
    // script adapters by throwing TypeError on such script adapters.
    private void generateOverridingConstructorWithObjectParam(final CodeBuilder cb, final MethodTypeDesc ctorType) {
        final int extraArgOffset = emitSuperConstructorCall(cb, ctorType);

        // Check for ScriptObjectMirror
        cb.aload(extraArgOffset);
        cb.instanceOf(SCRIPT_OBJECT_MIRROR_TYPE);
        final Label notMirror = cb.newLabel();
        cb.ifeq(notMirror);

        cb.aload(0);
        cb.aload(extraArgOffset);
        cb.iconst_0();
        UNWRAP_MIRROR.invoke(cb);
        cb.putfield(generatedType, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);

        cb.aload(0);
        cb.aload(extraArgOffset);
        cb.iconst_1();
        UNWRAP_MIRROR.invoke(cb);
        cb.putfield(generatedType, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);

        final Label done = cb.newLabel();

        if (samName != null) {
            cb.aload(0);
            cb.getfield(generatedType, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
            cb.instanceOf(SCRIPT_FUNCTION_TYPE);
            cb.ifeq(done);

            // Assign "isFunction = true"
            cb.aload(0);
            cb.iconst_1();
            cb.putfield(generatedType, IS_FUNCTION_FIELD_NAME, CD_boolean);

            cb.aload(0);
            cb.dup();
            cb.getfield(generatedType, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
            cb.checkcast(SCRIPT_FUNCTION_TYPE);
            emitInitCallThis(cb);
            cb.goto_(done);
        }

        cb.labelBinding(notMirror);

        // Throw error if not a ScriptObject
        cb.aload(extraArgOffset);
        NOT_AN_OBJECT.invoke(cb);

        cb.labelBinding(done);
        cb.return_();
    }

    /**
     * Encapsulation of the information used to generate methods in the adapter classes. Basically, a wrapper around the
     * reflective Method object, a cached MethodType, and the name of the field in the adapter class that will hold the
     * method handle serving as the implementation of this method in adapter instances.
     *
     */
    private static class MethodInfo {
        private final Method method;
        private final MethodType type;

        private MethodInfo(final Class<?> clazz, final String name, final Class<?>... argTypes) throws NoSuchMethodException {
            this(clazz.getDeclaredMethod(name, argTypes));
        }

        private MethodInfo(final Method method) {
            this.method = method;
            this.type   = MH.type(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof MethodInfo && equals((MethodInfo)obj);
        }

        private boolean equals(final MethodInfo other) {
            // Only method name and type are used for comparison; method handle field name is not.
            return getName().equals(other.getName()) && type.equals(other.type);
        }

        String getName() {
            return method.getName();
        }

        @Override
        public int hashCode() {
            return getName().hashCode() ^ type.hashCode();
        }
    }

    private void generateMethods() {
        int methodIndex = 0;
        for(final MethodInfo mi: methodInfos) {
            generateMethod(mi, methodIndex++);
        }
    }

    /**
     * Generates a method in the adapter class that adapts a method from the
     * original class. The generated method will either invoke the delegate
     * using a CALL dynamic operation call site (if it is a SAM method and the
     * delegate is a ScriptFunction), or invoke GET_METHOD_PROPERTY dynamic
     * operation with the method name as the argument and then invoke the
     * returned ScriptFunction using the CALL dynamic operation. If
     * GET_METHOD_PROPERTY returns null or undefined (that is, the JS object
     * doesn't provide an implementation for the method) then the method will
     * either do a super invocation to base class, or if the method is abstract,
     * throw an {@link UnsupportedOperationException}. Finally, if
     * GET_METHOD_PROPERTY returns something other than a ScriptFunction, null,
     * or undefined, a TypeError is thrown. The Global used to create the
     * adapter is checked before the dynamic operations, and if it is not the
     * current Global, the invocation runs with the creating Global bound as
     * the current one, scoped to the invocation.
     * If CALL results in a Throwable that is not one of the
     * method's declared exceptions, and is not an unchecked throwable, then it
     * is wrapped into a {@link RuntimeException} and the runtime exception is
     * thrown.
     * @param mi the method info describing the method to be generated.
     */
    private void generateMethod(final MethodInfo mi, final int methodIndex) {
        final Method method = mi.method;
        final Class<?>[] exceptions = method.getExceptionTypes();
        final List<ClassDesc> exceptionTypes = classDescs(List.of(exceptions));
        final MethodType type = mi.type;
        final String methodDesc = type.toMethodDescriptorString();
        final String name = mi.getName();
        final MethodTypeDesc methodType = MethodTypeDesc.ofDescriptor(methodDesc);
        final List<ClassDesc> argTypes = methodType.parameterList();
        // Both are indexed rather than named after the method alone: the
        // adapted methods can be overloads of one name, and the bridges all
        // share one descriptor.
        final String implName = IMPL_PREFIX + methodIndex + "$" + name;
        final String bridgeName = BRIDGE_PREFIX + methodIndex + "$" + name;

        // The adapter method the world sees: dispatches to the private
        // implementation, either directly when the adapter's realm is already
        // the current one - every invocation from script - or through a scoped
        // rebinding of the realm when a foreign thread calls in.
        withMethod(getAccessModifiers(method), name, methodType, exceptionTypes,
                cb -> generateRealmDispatch(cb, bridgeName, implName, methodType, argTypes));

        // The implementation, running in an already-established realm.
        withMethod(ACC_PRIVATE, implName, methodType, exceptionTypes,
                cb -> generateMethodBody(cb, method, exceptions, type, methodType, argTypes, name));

        // The slow path's target: unpacks the boxed arguments and invokes the
        // implementation. A separate method with this fixed (Object[])Object
        // shape because a method handle - which is how the slow path arrives -
        // cannot be made for a method of maximal parameter count, while a
        // plain bytecode invocation of one is fine.
        withMethod(ACC_PRIVATE, bridgeName, DISPATCH_BRIDGE_TYPE, List.of(),
                cb -> generateDispatchBridge(cb, implName, methodType, argTypes));
    }

    /**
     * Generates the body of a public adapter method: check whether the
     * adapter's creating global is already the current one and invoke the
     * implementation method directly if so; otherwise hand a method handle to
     * the dispatch bridge, together with the boxed arguments, to
     * {@code JavaAdapterServices.callInGlobal}, which runs it with the
     * adapter's realm bound as a scoped value. The slow path only runs when a
     * foreign thread - one where a different realm, or none, is current -
     * calls into the adapter, so its boxing costs nothing on the script path.
     */
    private void generateRealmDispatch(final CodeBuilder cb, final String bridgeName, final String implName,
            final MethodTypeDesc methodType, final List<ClassDesc> argTypes) {
        final TypeKind returnKind = TypeKind.from(methodType.returnType());
        final Label slow = cb.newLabel();
        loadField(cb, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);
        SAME_GLOBAL.invoke(cb);
        cb.ifeq(slow);

        // Fast path: the realm is already current, plain call.
        cb.aload(0);
        int varOffset = 1;
        for (final ClassDesc t : argTypes) {
            final TypeKind kind = TypeKind.from(t);
            cb.loadLocal(kind, varOffset);
            varOffset += kind.slotSize();
        }
        cb.invokespecial(generatedType, implName, methodType);
        cb.return_(returnKind);

        // Slow path: bind the adapter's realm around a bridge invocation.
        cb.labelBinding(slow);
        loadField(cb, GLOBAL_FIELD_NAME, SCRIPT_OBJECT_TYPE);
        cb.ldc(MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.SPECIAL, generatedType, bridgeName, DISPATCH_BRIDGE_TYPE));
        // stack: [handle, global]
        // callInGlobal's arguments: the receiver, then the boxed parameters in an array
        cb.iconst_2();
        cb.anewarray(CD_Object);
        cb.dup();
        cb.iconst_0();
        cb.aload(0);
        cb.aastore();
        cb.dup();
        cb.iconst_1();
        cb.loadConstant(argTypes.size());
        cb.anewarray(CD_Object);
        varOffset = 1;
        int argIndex = 0;
        for (final ClassDesc t : argTypes) {
            final TypeKind kind = TypeKind.from(t);
            cb.dup();
            cb.loadConstant(argIndex++);
            cb.loadLocal(kind, varOffset);
            boxStackValue(cb, kind);
            cb.aastore();
            varOffset += kind.slotSize();
        }
        cb.aastore();
        // stack: [args, handle, global]
        CALL_IN_GLOBAL.invoke(cb);
        // stack: [boxedResult]
        unboxStackValue(cb, methodType.returnType());
        cb.return_(returnKind);
    }

    /**
     * Generates the body of the slow path's bridge: unbox every element of the
     * argument array to its declared type, invoke the implementation, box the
     * result.
     */
    private void generateDispatchBridge(final CodeBuilder cb, final String implName,
            final MethodTypeDesc methodType, final List<ClassDesc> argTypes) {
        cb.aload(0);
        int argIndex = 0;
        for (final ClassDesc t : argTypes) {
            cb.aload(1);
            cb.loadConstant(argIndex++);
            cb.aaload();
            unboxStackValue(cb, t);
        }
        cb.invokespecial(generatedType, implName, methodType);
        final TypeKind returnKind = TypeKind.from(methodType.returnType());
        if (returnKind == TypeKind.VOID) {
            cb.aconst_null();
        } else {
            boxStackValue(cb, returnKind);
        }
        cb.areturn();
    }

    private static void boxStackValue(final CodeBuilder cb, final TypeKind kind) {
        switch (kind) {
        case BOOLEAN -> cb.invokestatic(classDesc(Boolean.class), "valueOf", MethodTypeDesc.ofDescriptor("(Z)Ljava/lang/Boolean;"));
        case BYTE    -> cb.invokestatic(classDesc(Byte.class), "valueOf", MethodTypeDesc.ofDescriptor("(B)Ljava/lang/Byte;"));
        case CHAR    -> cb.invokestatic(classDesc(Character.class), "valueOf", MethodTypeDesc.ofDescriptor("(C)Ljava/lang/Character;"));
        case SHORT   -> cb.invokestatic(classDesc(Short.class), "valueOf", MethodTypeDesc.ofDescriptor("(S)Ljava/lang/Short;"));
        case INT     -> cb.invokestatic(classDesc(Integer.class), "valueOf", MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Integer;"));
        case LONG    -> cb.invokestatic(classDesc(Long.class), "valueOf", MethodTypeDesc.ofDescriptor("(J)Ljava/lang/Long;"));
        case FLOAT   -> cb.invokestatic(classDesc(Float.class), "valueOf", MethodTypeDesc.ofDescriptor("(F)Ljava/lang/Float;"));
        case DOUBLE  -> cb.invokestatic(classDesc(Double.class), "valueOf", MethodTypeDesc.ofDescriptor("(D)Ljava/lang/Double;"));
        default      -> { /* references need no boxing */ }
        }
    }

    private static void unboxStackValue(final CodeBuilder cb, final ClassDesc type) {
        final TypeKind kind = TypeKind.from(type);
        switch (kind) {
        case VOID -> cb.pop();
        case BOOLEAN -> { cb.checkcast(classDesc(Boolean.class)); cb.invokevirtual(classDesc(Boolean.class), "booleanValue", MethodTypeDesc.ofDescriptor("()Z")); }
        case BYTE    -> { cb.checkcast(classDesc(Byte.class)); cb.invokevirtual(classDesc(Byte.class), "byteValue", MethodTypeDesc.ofDescriptor("()B")); }
        case CHAR    -> { cb.checkcast(classDesc(Character.class)); cb.invokevirtual(classDesc(Character.class), "charValue", MethodTypeDesc.ofDescriptor("()C")); }
        case SHORT   -> { cb.checkcast(classDesc(Short.class)); cb.invokevirtual(classDesc(Short.class), "shortValue", MethodTypeDesc.ofDescriptor("()S")); }
        case INT     -> { cb.checkcast(classDesc(Integer.class)); cb.invokevirtual(classDesc(Integer.class), "intValue", MethodTypeDesc.ofDescriptor("()I")); }
        case LONG    -> { cb.checkcast(classDesc(Long.class)); cb.invokevirtual(classDesc(Long.class), "longValue", MethodTypeDesc.ofDescriptor("()J")); }
        case FLOAT   -> { cb.checkcast(classDesc(Float.class)); cb.invokevirtual(classDesc(Float.class), "floatValue", MethodTypeDesc.ofDescriptor("()F")); }
        case DOUBLE  -> { cb.checkcast(classDesc(Double.class)); cb.invokevirtual(classDesc(Double.class), "doubleValue", MethodTypeDesc.ofDescriptor("()D")); }
        default      -> {
            if (!CD_Object.equals(type)) {
                cb.checkcast(type);
            }
        }
        }
    }

    private void generateMethodBody(final CodeBuilder cb, final Method method,
            final Class<?>[] exceptions, final MethodType type, final MethodTypeDesc methodType,
            final List<ClassDesc> argTypes, final String name) {
        final Class<?> returnType = type.returnType();

        final Label tryBlockStart = cb.newBoundLabel();

        final Label callCallee = cb.newLabel();
        final Label defaultBehavior = cb.newLabel();
        // If this is a SAM type...
        if (samName != null) {
            // ...every method will be checking whether we're initialized with a
            // function.
            loadField(cb, IS_FUNCTION_FIELD_NAME, CD_boolean);
            // stack: [isFunction]
            if (name.equals(samName)) {
                final Label notFunction = cb.newLabel();
                cb.ifeq(notFunction);
                // stack: []
                // If it's a SAM method, it'll load delegate as the "callee" and
                // "callThis" as "this" for the call if delegate is a function.
                loadField(cb, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
                // NOTE: if we added "mv.checkcast(SCRIPT_FUNCTION_TYPE);" here
                // we could emit the invokedynamic CALL instruction with signature
                // (ScriptFunction, Object, ...) instead of (Object, Object, ...).
                // We could combine this with an optimization in
                // ScriptFunction.findCallMethod where it could link a call with a
                // thinner guard when the call site statically guarantees that the
                // callee argument is a ScriptFunction. Additionally, we could use
                // a "ScriptFunction function" field in generated classes instead
                // of a "boolean isFunction" field to avoid the checkcast.
                loadField(cb, CALL_THIS_FIELD_NAME, CD_Object);
                // stack: [callThis, delegate]
                cb.goto_(callCallee);
                cb.labelBinding(notFunction);
            } else {
                // If it's not a SAM method, and the delegate is a function,
                // it'll fall back to default behavior
                cb.ifne(defaultBehavior);
                // stack: []
            }
        }

        // At this point, this is either not a SAM method or the delegate is
        // not a ScriptFunction. We need to emit a GET_METHOD_PROPERTY Nashorn
        // invokedynamic.

        if(name.equals("toString")) {
            // Since every JS Object has a toString, we only override
            // "String toString()" it if it's explicitly specified on the object.
            loadField(cb, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
            // stack: [delegate]
            HAS_OWN_TO_STRING.invoke(cb);
            // stack: [hasOwnToString]
            cb.ifeq(defaultBehavior);
        }

        loadField(cb, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
        //For the cases like scripted overridden methods invoked from super constructors get adapter global/delegate fields as null, since we
        //cannot set these fields before invoking super constructor better solution is opt out of scripted overridden method if global/delegate fields
        //are null and invoke super method instead
        cb.ifnull(defaultBehavior);
        loadField(cb, DELEGATE_FIELD_NAME, SCRIPT_OBJECT_TYPE);
        cb.dup();
        // stack: [delegate, delegate]
        final String encodedName = NameCodec.encode(name);
        cb.invokedynamic(DynamicCallSiteDesc.of(BOOTSTRAP_HANDLE, encodedName,
                GET_METHOD_PROPERTY_METHOD_TYPE, NashornCallSiteDescriptor.GET_METHOD_PROPERTY));
        // stack: [callee, delegate]
        cb.loadConstant(name);
        // stack: [name, callee, delegate]
        CHECK_FUNCTION.invoke(cb);
        // stack: [fnCalleeOrNull, delegate]
        final Label hasFunction = cb.newLabel();
        cb.dup();
        // stack: [fnCalleeOrNull, fnCalleeOrNull, delegate]
        cb.ifnonnull(hasFunction);
        // stack: [null, delegate]
        // If it's null or undefined, clear stack and fall back to default
        // behavior.
        cb.pop2();
        // stack: []

        // We can also arrive here from check for "delegate instanceof ScriptFunction"
        // in a non-SAM method as well as from a check for "hasOwnToString(delegate)"
        // for a toString delegate.
        cb.labelBinding(defaultBehavior);
        final Label tryBlockEnd = cb.newLabel();
        if(Modifier.isAbstract(method.getModifiers())) {
            // If the super method is abstract, throw UnsupportedOperationException
            UNSUPPORTED.invoke(cb);
            cb.athrow();
        } else {
            // If the super method is not abstract, delegate to it.
            emitSuperCall(cb, method.getDeclaringClass(), name, methodType);
            cb.goto_(tryBlockEnd);
        }

        cb.labelBinding(hasFunction);
        // stack: [callee, delegate]
        cb.swap();
        // stack [delegate, callee]
        cb.labelBinding(callCallee);


        // Load all parameters back on stack for dynamic invocation.

        int varOffset = 1;
        // If the param list length is more than 253 slots, we can't invoke it
        // directly as with (callee, this) it'll exceed 255.
        final boolean isVarArgCall = getParamListLengthInSlots(argTypes) > 253;
        for (final ClassDesc t : argTypes) {
            final TypeKind kind = TypeKind.from(t);
            cb.loadLocal(kind, varOffset);
            convertParam(cb, t, isVarArgCall);
            varOffset += kind.slotSize();
        }
        // stack: [args..., callee, delegate]

        // If the resulting parameter list length is too long...
        if (isVarArgCall) {
            // ... we pack the parameters (except callee and this) into an array
            // and use Nashorn vararg invocation.
            cb.invokedynamic(DynamicCallSiteDesc.of(CREATE_ARRAY_BOOTSTRAP_HANDLE, NameCodec.EMPTY_NAME,
                    methodTypeDesc(getArrayCreatorMethodType(type))));
        }

        // Invoke the target method handle
        cb.invokedynamic(DynamicCallSiteDesc.of(BOOTSTRAP_HANDLE, encodedName,
                methodTypeDesc(getCallMethodType(isVarArgCall, type)), NashornCallSiteDescriptor.CALL));
        // stack: [returnValue]
        convertReturnValue(cb, returnType);
        cb.labelBinding(tryBlockEnd);
        cb.return_(TypeKind.from(methodType.returnType()));

        // If Throwable is not declared, we need an adapter from Throwable to
        // RuntimeException. RuntimeException, Error and the declared exception
        // types are directed at a plain rethrow handler so the wrapping
        // Throwable handler cannot claim them; with Throwable declared no
        // handlers are needed at all, as everything may propagate as it is.
        final boolean throwableDeclared = isThrowableDeclared(exceptions);
        if (!throwableDeclared) {
            // Add "throw new RuntimeException(Throwable)" handler for Throwable
            final Label throwableHandler = cb.newBoundLabel();
            WRAP_THROWABLE.invoke(cb);
            // Fall through to rethrow handler
            final Label rethrowHandler = cb.newBoundLabel();
            cb.athrow();

            cb.exceptionCatch(tryBlockStart, tryBlockEnd, rethrowHandler, RUNTIME_EXCEPTION_TYPE);
            cb.exceptionCatch(tryBlockStart, tryBlockEnd, rethrowHandler, ERROR_TYPE);
            for(final ClassDesc excType: classDescs(List.of(exceptions))) {
                cb.exceptionCatch(tryBlockStart, tryBlockEnd, rethrowHandler, excType);
            }
            cb.exceptionCatch(tryBlockStart, tryBlockEnd, throwableHandler, THROWABLE_TYPE);
        }
    }

    private static MethodTypeDesc methodTypeDesc(final MethodType type) {
        return MethodTypeDesc.ofDescriptor(type.toMethodDescriptorString());
    }

    private static MethodType getCallMethodType(final boolean isVarArgCall, final MethodType type) {
        final Class<?>[] callParamTypes;
        if (isVarArgCall) {
            // Variable arity calls are always (Object callee, Object this, Object[] params)
            callParamTypes = new Class<?>[] { Object.class, Object.class, Object[].class };
        } else {
            // Adjust invocation type signature for conversions we instituted in
            // convertParam; also, byte and short get passed as ints.
            final Class<?>[] origParamTypes = type.parameterArray();
            callParamTypes = new Class<?>[origParamTypes.length + 2];
            callParamTypes[0] = Object.class; // callee; could be ScriptFunction.class ostensibly
            callParamTypes[1] = Object.class; // this
            for(int i = 0; i < origParamTypes.length; ++i) {
                callParamTypes[i + 2] = getNashornParamType(origParamTypes[i], false);
            }
        }
        return MethodType.methodType(getNashornReturnType(type.returnType()), callParamTypes);
    }

    private static MethodType getArrayCreatorMethodType(final MethodType type) {
        final Class<?>[] callParamTypes = type.parameterArray();
        for(int i = 0; i < callParamTypes.length; ++i) {
            callParamTypes[i] = getNashornParamType(callParamTypes[i], true);
        }
        return MethodType.methodType(Object[].class, callParamTypes);
    }

    private static Class<?> getNashornParamType(final Class<?> clazz, final boolean varArg) {
        if (clazz == byte.class || clazz == short.class) {
            return int.class;
        } else if (clazz == float.class) {
            // If using variable arity, we'll pass a Double instead of double
            // so that floats don't extend the length of the parameter list.
            // We return Object.class instead of Double.class though as the
            // array collector will anyway operate on Object.
            return varArg ? Object.class : double.class;
        } else if (!clazz.isPrimitive() || clazz == long.class || clazz == char.class) {
            return Object.class;
        }
        return clazz;
    }

    private static Class<?> getNashornReturnType(final Class<?> clazz) {
        if (clazz == byte.class || clazz == short.class) {
            return int.class;
        } else if (clazz == float.class) {
            return double.class;
        } else if (clazz == void.class || clazz == char.class) {
            return Object.class;
        }
        return clazz;
    }


    private void loadField(final CodeBuilder cb, final String name, final ClassDesc type) {
        if(classOverride) {
            cb.getstatic(generatedType, name, type);
        } else {
            cb.aload(0);
            cb.getfield(generatedType, name, type);
        }
    }

    private static void convertReturnValue(final CodeBuilder cb, final Class<?> origReturnType) {
        if (origReturnType == void.class) {
            cb.pop();
        } else if (origReturnType == Object.class) {
            // Must hide ConsString (and potentially other internal Nashorn types) from callers
            EXPORT_RETURN_VALUE.invoke(cb);
        } else if (origReturnType == byte.class) {
            cb.i2b();
        } else if (origReturnType == short.class) {
            cb.i2s();
        } else if (origReturnType == float.class) {
            cb.d2f();
        } else if (origReturnType == char.class) {
            TO_CHAR_PRIMITIVE.invoke(cb);
        }
    }

    /**
     * Emits instruction for converting a parameter on the top of the stack to
     * a type that is understood by Nashorn.
     * @param cb the builder for the method being generated
     * @param t the type on the top of the stack
     * @param varArg if the invocation will be variable arity
     */
    private static void convertParam(final CodeBuilder cb, final ClassDesc t, final boolean varArg) {
        // We perform conversions of some primitives to accommodate types that
        // Nashorn can handle.
        switch(TypeKind.from(t)) {
        case CHAR:
            // Chars are boxed, as we don't know if the JS code wants to treat
            // them as an effective "unsigned short" or as a single-char string.
            CHAR_VALUE_OF.invoke(cb);
            break;
        case FLOAT:
            // Floats are widened to double.
            cb.f2d();
            if (varArg) {
                // We'll be boxing everything anyway for the vararg invocation,
                // so we might as well do it proactively here and thus not cause
                // a widening in the number of slots, as that could even make
                // the array creation invocation go over 255 param slots.
                DOUBLE_VALUE_OF.invoke(cb);
            }
            break;
        case LONG:
            // Longs are boxed as Nashorn can't represent them precisely as a
            // primitive number.
            LONG_VALUE_OF.invoke(cb);
            break;
        case REFERENCE:
            if(CD_Object.equals(t)) {
                // Object can carry a ScriptObjectMirror and needs to be unwrapped
                // before passing into a Nashorn function.
                UNWRAP.invoke(cb);
            }
            break;
        default:
            break;
        }
    }

    private static int getParamListLengthInSlots(final List<ClassDesc> paramTypes) {
        int len = paramTypes.size();
        for(final ClassDesc t: paramTypes) {
            final TypeKind kind = TypeKind.from(t);
            if (kind == TypeKind.FLOAT || kind == TypeKind.DOUBLE) {
                // Floats are widened to double, so they'll take up two slots.
                // Longs on the other hand are always boxed, so their width
                // becomes 1 and thus they don't contribute an extra slot here.
                ++len;
            }
        }
        return len;
    }

    private static boolean isThrowableDeclared(final Class<?>[] exceptions) {
        for (final Class<?> exception : exceptions) {
            if (exception == Throwable.class) {
                return true;
            }
        }
        return false;
    }

    private void generateSuperMethods() {
        for(final MethodInfo mi: methodInfos) {
            if(!Modifier.isAbstract(mi.method.getModifiers())) {
                generateSuperMethod(mi);
            }
        }
    }

    private void generateSuperMethod(final MethodInfo mi) {
        final Method method = mi.method;

        final MethodTypeDesc methodType = methodTypeDesc(mi.type);
        final String name = mi.getName();

        withMethod(getAccessModifiers(method), SUPER_PREFIX + name, methodType,
                classDescs(List.of(method.getExceptionTypes())), cb -> {
            emitSuperCall(cb, method.getDeclaringClass(), name, methodType);
            cb.return_(TypeKind.from(methodType.returnType()));
        });
    }

    // find the appropriate super type to use for invokespecial on the given interface
    private Class<?> findInvokespecialOwnerFor(final Class<?> cl) {
        assert Modifier.isInterface(cl.getModifiers()) : cl + " is not an interface";

        if (cl.isAssignableFrom(superClass)) {
            return superClass;
        }

        for (final Class<?> iface : interfaces) {
            if (cl.isAssignableFrom(iface)) {
                return iface;
            }
        }

        // we better that interface that extends the given interface!
        throw new AssertionError("can't find the class/interface that extends " + cl);
    }

    private int emitSuperConstructorCall(final CodeBuilder cb, final MethodTypeDesc methodType) {
        return emitSuperCall(cb, null, INIT, methodType, true);
    }

    private void emitSuperCall(final CodeBuilder cb, final Class<?> owner, final String name, final MethodTypeDesc methodType) {
        emitSuperCall(cb, owner, name, methodType, false);
    }

    private int emitSuperCall(final CodeBuilder cb, final Class<?> owner, final String name,
            final MethodTypeDesc methodType, final boolean constructor) {
        cb.aload(0);
        int nextParam = 1;
        for(final ClassDesc t: methodType.parameterList()) {
            final TypeKind kind = TypeKind.from(t);
            cb.loadLocal(kind, nextParam);
            nextParam += kind.slotSize();
        }

        // default method - non-abstract, interface method
        if (!constructor && Modifier.isInterface(owner.getModifiers())) {
            // we should call default method on the immediate "super" type - not on (possibly)
            // the indirectly inherited interface class!
            final Class<?> superType = findInvokespecialOwnerFor(owner);
            cb.invoke(Opcode.INVOKESPECIAL, classDesc(superType), name, methodType,
                Modifier.isInterface(superType.getModifiers()));
        } else {
            cb.invokespecial(this.superType, name, methodType);
        }
        return nextParam;
    }

    private static int getAccessModifiers(final Method method) {
        return ACC_PUBLIC | (method.isVarArgs() ? ACC_VARARGS : 0);
    }

    /**
     * Gathers methods that can be implemented or overridden from the specified type into this factory's
     * {@link #methodInfos} set. It will add all non-final, non-static methods that are either public or protected from
     * the type if the type itself is public. If the type is a class, the method will recursively invoke itself for its
     * superclass and the interfaces it implements, and add further methods that were not directly declared on the
     * class.
     * @param type the type defining the methods.
     */
    private void gatherMethods(final Class<?> type) {
        if (Modifier.isPublic(type.getModifiers())) {
            final Method[] typeMethods = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();

            for (final Method typeMethod: typeMethods) {
                final String name = typeMethod.getName();
                if(name.startsWith(SUPER_PREFIX)) {
                    continue;
                }
                final int m = typeMethod.getModifiers();
                if (Modifier.isStatic(m)) {
                    continue;
                }
                if (Modifier.isPublic(m) || Modifier.isProtected(m)) {
                    if(name.equals("finalize") && typeMethod.getParameterCount() == 0) {
                        // We intentionally don't support "finalize"
                        continue;
                    }

                    final MethodInfo mi = new MethodInfo(typeMethod);
                    if (Modifier.isFinal(m) || isCallerSensitive(typeMethod)) {
                        finalMethods.add(mi);
                    } else if (!finalMethods.contains(mi) && methodInfos.add(mi) && Modifier.isAbstract(m)) {
                        abstractMethodNames.add(mi.getName());
                    }
                }
            }
        }
        // If the type is a class, visit its superclasses and declared interfaces. If it's an interface, we're done.
        // Needing to invoke the method recursively for a non-interface Class object is the consequence of needing to
        // see all declared protected methods, and Class.getDeclaredMethods() doesn't provide those declared in a
        // superclass. For interfaces, we used Class.getMethods(), as we're only interested in public ones there, and
        // getMethods() does provide those declared in a superinterface.
        if (!type.isInterface()) {
            final Class<?> superType = type.getSuperclass();
            if (superType != null) {
                gatherMethods(superType);
            }
            for (final Class<?> itf: type.getInterfaces()) {
                gatherMethods(itf);
            }
        }
    }

    private void gatherMethods(final List<Class<?>> classes) {
        for(final Class<?> c: classes) {
            gatherMethods(c);
        }
    }

    /**
     * Creates a collection of methods that are not final, but we still never allow them to be overridden in adapters,
     * as explicitly declaring them automatically is a bad idea. Currently, this means {@code Object.finalize()} and
     * {@code Object.clone()}.
     * @return a collection of method infos representing those methods that we never override in adapter classes.
     */
    private static Collection<MethodInfo> getExcludedMethods() {
        try {
            return List.of(
                    new MethodInfo(Object.class, "finalize"),
                    new MethodInfo(Object.class, "clone"));
        } catch (final NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean isCallerSensitive(final Executable e) {
        for (final Annotation ann: e.getAnnotations()) {
            if (CALLER_SENSITIVE_CLASS_NAME.equals(ann.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Call lookupServiceMethod(final String name, final Class<?> rtype, final Class<?>... ptypes) {
        return staticCallNoLookup(JavaAdapterServices.class, name, rtype, ptypes);
    }
}
