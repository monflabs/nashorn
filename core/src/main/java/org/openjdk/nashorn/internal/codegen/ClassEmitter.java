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

package org.openjdk.nashorn.internal.codegen;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.classfile.ClassFile.ACC_SUPER;
import static java.lang.classfile.ClassFile.ACC_VARARGS;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.CONSTANTS;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.GET_ARRAY_PREFIX;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.GET_ARRAY_SUFFIX;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.GET_MAP;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.GET_STRING;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.INIT;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.SET_MAP;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.STRICT_MODE;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.className;
import static org.openjdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openjdk.nashorn.internal.codegen.types.Type;
import org.openjdk.nashorn.internal.ir.FunctionNode;
import org.openjdk.nashorn.internal.ir.debug.BytecodePrinter;
import org.openjdk.nashorn.internal.runtime.Context;
import org.openjdk.nashorn.internal.runtime.PropertyMap;
import org.openjdk.nashorn.internal.runtime.RewriteException;
import org.openjdk.nashorn.internal.runtime.ScriptObject;
import org.openjdk.nashorn.internal.runtime.Source;

/**
 * The interface responsible for speaking to ASM, emitting classes,
 * fields and methods.
 * <p>
 * This file contains the ClassEmitter, which is the master object
 * responsible for writing byte codes. It utilizes a MethodEmitter
 * for method generation, which also the NodeVisitors own, to keep
 * track of the current code generator and what it is doing.
 * <p>
 * There is, however, nothing stopping you from using this in a
 * completely self contained environment, for example in ObjectGenerator
 * where there are no visitors or external hooks.
 * <p>
 * MethodEmitter makes it simple to generate code for methods without
 * having to do arduous type checking. It maintains a type stack
 * and will pick the appropriate operation for all operations sent to it
 * We also allow chained called to a MethodEmitter for brevity, e.g.
 * it is legal to write _new(className).dup() or
 * load(slot).load(slot2).xor().store(slot3);
 * <p>
 * If running with assertions enabled, any type conflict, such as different
 * bytecode stack sizes or operating on the wrong type will be detected
 * and an error thrown.
 * <p>
 * There is also a very nice debug interface that can emit formatted
 * bytecodes that have been written. This is enabled by setting the
 * environment "nashorn.codegen.debug" to true, or --log=codegen:{@literal <level>}
 *
 * @see Compiler
 */
public class ClassEmitter {
    /** Default flags for class generation - public class */
    private static final EnumSet<Flag> DEFAULT_METHOD_FLAGS = EnumSet.of(Flag.PUBLIC);

    /** Sanity check flag - have we started on a class? */
    private boolean classStarted;

    /** Sanity check flag - have we ended this emission? */
    private boolean classEnded;

    /**
     * Sanity checks - which methods have we currently
     * started for generation in this class?
     */
    private final HashSet<MethodEmitter> methodsStarted;

    /**
     * Class file version of the code Nashorn generates. Not the version it runs
     * on: the bytecode uses nothing newer, and keeping it low keeps the classes
     * loadable by anything that can host Nashorn at all.
     */
    private static final int CLASS_VERSION = ClassFile.JAVA_7_VERSION;

    private static final ClassDesc SCRIPT_OBJECT = Type.classDesc(ScriptObject.class);

    /**
     * Nashorn's own types as the stack map generator sees them.
     *
     * Classes that cannot be loaded - the compile unit being generated, and the
     * structure classes that {@link ObjectClassGenerator} makes at runtime - are
     * answered from their name: anything that lives in Nashorn's scripts or
     * objects package is reported as a ScriptObject subtype, so that merging two
     * of them yields ScriptObject rather than Object, and everything else falls
     * back to Object.
     */
    private static final ClassHierarchyResolver CLASS_HIERARCHY =
        ClassHierarchyResolver.ofClassLoading(ClassEmitter.class.getClassLoader())
            .orElse(classDesc -> ClassHierarchyInfo.ofClass(
                isScriptObject(internalName(classDesc)) ? SCRIPT_OBJECT : ConstantDescs.CD_Object))
            .cached();

    /** The context every class Nashorn generates goes through. */
    private static final ClassFile CLASS_FILE =
        ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(CLASS_HIERARCHY));

    /** This class */
    private final ClassDesc className;

    /** Super class of this class */
    private final ClassDesc superClass;

    /** Interfaces this class implements */
    private final List<ClassDesc> interfaces;

    /** Source file name, for the SourceFile attribute, or null */
    private String sourceFile;

    /** Fields to write, in declaration order */
    private final List<FieldDef> fieldDefs = new ArrayList<>();

    /** Methods to write, in declaration order */
    private final List<MethodDef> methodDefs = new ArrayList<>();

    /** The script environment */
    protected final Context context;

    /** The internal (slash separated) name of a class or interface. */
    private static String internalName(final ClassDesc classDesc) {
        final String pkg = classDesc.packageName();
        return pkg.isEmpty() ? classDesc.displayName() : pkg.replace('.', '/') + '/' + classDesc.displayName();
    }

    /** Compile unit class name. */
    private String unitClassName;

    /** Set of constants access methods required. */
    private Set<Class<?>> constantMethodNeeded;

    private int methodCount;

    private int initCount;

    private int fieldCount;

    private final Set<String> methodNames;

    /**
     * Constructor - only used internally in this class as it breaks
     * abstraction towards ASM or other code generator below.
     *
     * @param context script context
     * @param cw  ASM classwriter
     */
    private ClassEmitter(final Context context, final String className, final String superClassName,
            final String[] interfaceNames, final String sourceFile) {
        this.context        = context;
        this.sourceFile     = sourceFile;
        this.className      = CompilerConstants.classDesc(className);
        this.superClass     = CompilerConstants.classDesc(superClassName);
        this.interfaces     = new ArrayList<>(interfaceNames.length);
        for (final String interfaceName : interfaceNames) {
            interfaces.add(CompilerConstants.classDesc(interfaceName));
        }
        this.methodsStarted = new HashSet<>();
        this.methodNames    = new HashSet<>();
    }

    /** A field waiting to be written. */
    private record FieldDef(int flags, String name, ClassDesc type, ConstantDesc value) {}

    /** A method waiting to be written, with its recorded body. */
    private record MethodDef(int flags, String name, MethodTypeDesc type, CodeBuffer code) {}

    /**
     * Return the method names encountered.
     *
     * @return method names
     */
    public Set<String> getMethodNames() {
        return Collections.unmodifiableSet(methodNames);
    }

    /**
     * Constructor.
     *
     * @param context         script context
     * @param className       name of class to weave
     * @param superClassName  super class name for class
     * @param interfaceNames  names of interfaces implemented by this class, or
     *        {@code null} if none
     */
    ClassEmitter(final Context context, final String className, final String superClassName, final String... interfaceNames) {
        this(context, className, superClassName, interfaceNames, (String)null);
    }

    /**
     * Constructor from the compiler.
     *
     * @param context       Script context
     * @param sourceName    Source name
     * @param unitClassName Compile unit class name.
     * @param strictMode    Should we generate this method in strict mode
     */
    ClassEmitter(final Context context, final String sourceName, final String unitClassName, final boolean strictMode) {
        this(context, unitClassName, pathName(org.openjdk.nashorn.internal.scripts.JS.class.getName()),
             NO_INTERFACES, sourceName);

        this.unitClassName        = unitClassName;
        this.constantMethodNeeded = new HashSet<>();

        defineCommonStatics(strictMode);
    }

    private static final String[] NO_INTERFACES = new String[0];

    Context getContext() {
        return context;
    }

    /**
     * @return the name of the compile unit class name.
     */
    String getUnitClassName() {
        return unitClassName;
    }

    /**
     * Get the method count, including init and clinit methods.
     *
     * @return method count
     */
    public int getMethodCount() {
        return methodCount;
    }

    /**
     * Get the init count.
     *
     * @return init count
     */
    public int getInitCount() {
        return initCount;
    }

    /**
     * Get the field count.
     *
     * @return field count
     */
    public int getFieldCount() {
        return fieldCount;
    }

    /**
     * Convert a binary name to a package/class name.
     *
     * @param name Binary name.
     *
     * @return Package/class name.
     */
    private static String pathName(final String name) {
        return name.replace('.', '/');
    }

    /**
     * Define the static fields common in all scripts.
     *
     * @param strictMode Should we generate this method in strict mode
     */
    private void defineCommonStatics(final boolean strictMode) {
        // source - used to store the source data (text) for this script.  Shared across
        // compile units.  Set externally by the compiler.
        field(EnumSet.of(Flag.PRIVATE, Flag.STATIC), SOURCE.symbolName(), Source.class);

        // constants - used to the constants array for this script.  Shared across
        // compile units.  Set externally by the compiler.
        field(EnumSet.of(Flag.PRIVATE, Flag.STATIC), CONSTANTS.symbolName(), Object[].class);

        // strictMode - was this script compiled in strict mode.  Set externally by the compiler.
        field(EnumSet.of(Flag.PUBLIC, Flag.STATIC, Flag.FINAL), STRICT_MODE.symbolName(), boolean.class, strictMode);
    }

    /**
     * Define static utilities common needed in scripts. These are per compile
     * unit and therefore have to be defined here and not in code gen.
     */
    private void defineCommonUtilities() {
        assert unitClassName != null;

        if (constantMethodNeeded.contains(String.class)) {
            // $getString - get the ith entry from the constants table and cast to String.
            final MethodEmitter getStringMethod = method(EnumSet.of(Flag.PRIVATE, Flag.STATIC), GET_STRING.symbolName(), String.class, int.class);
            getStringMethod.begin();
            getStringMethod.getStatic(unitClassName, CONSTANTS.symbolName(), CONSTANTS.descriptor())
                        .load(Type.INT, 0)
                        .arrayload()
                        .checkcast(String.class)
                        ._return();
            getStringMethod.end();
        }

        if (constantMethodNeeded.contains(PropertyMap.class)) {
            // $getMap - get the ith entry from the constants table and cast to PropertyMap.
            final MethodEmitter getMapMethod = method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), GET_MAP.symbolName(), PropertyMap.class, int.class);
            getMapMethod.begin();
            getMapMethod.loadConstants()
                        .load(Type.INT, 0)
                        .arrayload()
                        .checkcast(PropertyMap.class)
                        ._return();
            getMapMethod.end();

            // $setMap - overwrite an existing map.
            final MethodEmitter setMapMethod = method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), SET_MAP.symbolName(), void.class, int.class, PropertyMap.class);
            setMapMethod.begin();
            setMapMethod.loadConstants()
                        .load(Type.INT, 0)
                        .load(Type.OBJECT, 1)
                        .arraystore();
            setMapMethod.returnVoid();
            setMapMethod.end();
        }

        // $getXXXX$array - get the ith entry from the constants table and cast to XXXX[].
        for (final Class<?> clazz : constantMethodNeeded) {
            if (clazz.isArray()) {
                defineGetArrayMethod(clazz);
            }
        }
    }

    /**
     * Constructs a primitive specific method for getting the ith entry from the
     * constants table as an array.
     *
     * @param clazz Array class.
     */
    private void defineGetArrayMethod(final Class<?> clazz) {
        assert unitClassName != null;

        final String        methodName     = getArrayMethodName(clazz);
        final MethodEmitter getArrayMethod = method(EnumSet.of(Flag.PRIVATE, Flag.STATIC), methodName, clazz, int.class);

        getArrayMethod.begin();
        getArrayMethod.getStatic(unitClassName, CONSTANTS.symbolName(), CONSTANTS.descriptor())
                      .load(Type.INT, 0)
                      .arrayload()
                      .checkcast(clazz)
                      .invoke(virtualCallNoLookup(clazz, "clone", Object.class))
                      .checkcast(clazz)
                      ._return();
        getArrayMethod.end();
    }


    /**
     * Generate the name of a get array from constant pool method.
     *
     * @param clazz Name of array class.
     *
     * @return Method name.
     */
    static String getArrayMethodName(final Class<?> clazz) {
        assert clazz.isArray();
        return GET_ARRAY_PREFIX.symbolName() + clazz.getComponentType().getSimpleName() + GET_ARRAY_SUFFIX.symbolName();
    }

    /**
     * Ensure a get constant method is issued for the class.
     *
     * @param clazz Class of constant.
     */
    void needGetConstantMethod(final Class<?> clazz) {
        constantMethodNeeded.add(clazz);
    }

    /**
     * Inspect class name and decide whether we are generating a ScriptObject class.
     *
     * @param type         the type to check
     *
     * @return {@code true} if type is ScriptObject
     */
    private static boolean isScriptObject(final String type) {
        return
            type.startsWith(Compiler.SCRIPTS_PACKAGE) ||
            type.equals(CompilerConstants.className(ScriptObject.class)) ||
            type.startsWith(Compiler.OBJECTS_PACKAGE)
        ;
    }

    /**
     * Call at beginning of class emission.
     */
    public void begin() {
        classStarted = true;
    }

    /**
     * Call at end of class emission.
     */
    public void end() {
        assert classStarted : "class not started for " + unitClassName;

        if (unitClassName != null) {
            final MethodEmitter initMethod = init(EnumSet.of(Flag.PRIVATE));
            initMethod.begin();
            initMethod.load(Type.OBJECT, 0);
            initMethod.newInstance(org.openjdk.nashorn.internal.scripts.JS.class);
            initMethod.returnVoid();
            initMethod.end();

            defineCommonUtilities();
        }

        classStarted = false;
        classEnded   = true;
        assert methodsStarted.isEmpty() : "methodsStarted not empty " + methodsStarted;
    }

    /**
     * Disassemble an array of byte code.
     *
     * @param bytecode  byte array representing bytecode
     *
     * @return disassembly as human readable string
     */
    static String disassemble(final byte[] bytecode) {
        return BytecodePrinter.disassemble(Context.getContext().getEnv(), bytecode);
    }

    /**
     * Call back from MethodEmitter for method start.
     *
     * @see MethodEmitter
     *
     * @param method method emitter.
     */
    void beginMethod(final MethodEmitter method) {
        assert !methodsStarted.contains(method);
        methodsStarted.add(method);
    }

    /**
     * Call back from MethodEmitter for method end.
     *
     * @see MethodEmitter
     *
     * @param method
     */
    void endMethod(final MethodEmitter method) {
        assert methodsStarted.contains(method);
        methodsStarted.remove(method);
    }

    /**
     * Add a new method to the class - defaults to public method.
     *
     * @param methodName name of method
     * @param rtype      return type of the method
     * @param ptypes     parameter types the method
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter method(final String methodName, final Class<?> rtype, final Class<?>... ptypes) {
        return method(DEFAULT_METHOD_FLAGS, methodName, rtype, ptypes); //TODO why public default ?
    }

    /**
     * Add a new method to the class - defaults to public method.
     *
     * @param methodFlags access flags for the method
     * @param methodName  name of method
     * @param rtype       return type of the method
     * @param ptypes      parameter types the method
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter method(final EnumSet<Flag> methodFlags, final String methodName, final Class<?> rtype, final Class<?>... ptypes) {
        return newMethod(Flag.getValue(methodFlags), methodName, Type.methodType(rtype, ptypes), null);
    }

    /**
     * Add a new method to the class - defaults to public method.
     *
     * @param methodName name of method
     * @param descriptor descriptor of method
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter method(final String methodName, final String descriptor) {
        return method(DEFAULT_METHOD_FLAGS, methodName, descriptor);
    }

    /**
     * Add a new method to the class - defaults to public method.
     *
     * @param methodFlags access flags for the method
     * @param methodName  name of method
     * @param descriptor  descriptor of method
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter method(final EnumSet<Flag> methodFlags, final String methodName, final String descriptor) {
        return newMethod(Flag.getValue(methodFlags), methodName, CompilerConstants.methodType(descriptor), null);
    }

    /**
     * Add a new method to the class, representing a function node.
     *
     * @param functionNode the function node to generate a method for
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter method(final FunctionNode functionNode) {
        return newMethod(
            ACC_PUBLIC | ACC_STATIC | (functionNode.isVarArg() ? ACC_VARARGS : 0),
            functionNode.getName(),
            new FunctionSignature(functionNode).getMethodTypeDesc(),
            functionNode);
    }

    /**
     * Add a new method to the class, representing a rest-of version of the
     * function node.
     *
     * @param functionNode the function node to generate a method for
     *
     * @return method emitter to use for weaving this method
     */
    MethodEmitter restOfMethod(final FunctionNode functionNode) {
        return newMethod(
            ACC_PUBLIC | ACC_STATIC,
            functionNode.getName(),
            Type.methodType(functionNode.getReturnType().getTypeClass(), RewriteException.class),
            functionNode);
    }

    /**
     * Registers a method and returns the emitter that records its body.
     */
    private MethodEmitter newMethod(final int flags, final String methodName, final MethodTypeDesc type,
            final FunctionNode functionNode) {
        methodCount++;
        methodNames.add(methodName);
        final CodeBuffer code = new CodeBuffer();
        methodDefs.add(new MethodDef(flags, methodName, type, code));
        return new MethodEmitter(this, code, functionNode);
    }

    /**
     * Start generating an <init>()V method in the class.
     *
     * @return method emitter to use for weaving <init>()V
     */
    MethodEmitter init() {
        initCount++;
        return method(INIT.symbolName(), void.class);
    }

    /**
     * Start generating an <init>()V method in the class.
     *
     * @param ptypes parameter types for constructor
     * @return method emitter to use for weaving <init>()V
     */
    MethodEmitter init(final Class<?>... ptypes) {
        initCount++;
        return method(INIT.symbolName(), void.class, ptypes);
    }

    /**
     * Start generating an <init>(...)V method in the class.
     *
     * @param flags  access flags for the constructor
     * @param ptypes parameter types for the constructor
     *
     * @return method emitter to use for weaving <init>(...)V
     */
    MethodEmitter init(final EnumSet<Flag> flags, final Class<?>... ptypes) {
        initCount++;
        return method(flags, INIT.symbolName(), void.class, ptypes);
    }

    /**
     * Add a field to the class, initialized to a value.
     *
     * @param fieldFlags flags, e.g. should it be static or public etc
     * @param fieldName  name of field
     * @param fieldType  the type of the field
     * @param value      the value
     *
     * @see ClassEmitter.Flag
     */
    final void field(final EnumSet<Flag> fieldFlags, final String fieldName, final Class<?> fieldType, final Object value) {
        fieldCount++;
        fieldDefs.add(new FieldDef(Flag.getValue(fieldFlags), fieldName, Type.classDesc(fieldType), constantValue(value)));
    }

    /**
     * The ConstantValue attribute takes an int for the small integral types,
     * booleans among them.
     */
    private static ConstantDesc constantValue(final Object value) {
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return (ConstantDesc)value;
    }

    /**
     * Add a field to the class.
     *
     * @param fieldFlags access flags for the field
     * @param fieldName  name of field
     * @param fieldType  type of the field
     *
     * @see ClassEmitter.Flag
     */
    final void field(final EnumSet<Flag> fieldFlags, final String fieldName, final Class<?> fieldType) {
        field(fieldFlags, fieldName, fieldType, null);
    }

    /**
     * Add a field to the class - defaults to public.
     *
     * @param fieldName  name of field
     * @param fieldType  type of field
     */
    final void field(final String fieldName, final Class<?> fieldType) {
        field(EnumSet.of(Flag.PUBLIC), fieldName, fieldType, null);
    }

    /**
     * Return a bytecode array from this ClassEmitter. The ClassEmitter must
     * have been ended (having its end function called) for this to work.
     *
     * @return byte code array for generated class, {@code null} if class
     *         generation hasn't been ended with {@link ClassEmitter#end()}.
     */
    byte[] toByteArray() {
        if (!classEnded) {
            throw new AssertionError();
        }

        return CLASS_FILE.build(className, clb -> {
            clb.withVersion(CLASS_VERSION, 0);
            clb.withFlags(ACC_PUBLIC | ACC_SUPER);
            clb.withSuperclass(superClass);
            if (!interfaces.isEmpty()) {
                clb.withInterfaceSymbols(interfaces);
            }
            if (sourceFile != null) {
                clb.with(SourceFileAttribute.of(sourceFile));
            }

            for (final FieldDef field : fieldDefs) {
                clb.withField(field.name(), field.type(), fb -> {
                    fb.withFlags(field.flags());
                    if (field.value() != null) {
                        fb.with(ConstantValueAttribute.of(field.value()));
                    }
                });
            }

            for (final MethodDef method : methodDefs) {
                clb.withMethodBody(method.name(), method.type(), method.flags(), method.code()::writeTo);
            }
        });
    }

    /**
     * Abstraction for flags used in class emission. We provide abstraction
     * separating these from the underlying bytecode emitter. Flags are provided
     * for method handles, protection levels, static/virtual fields/methods.
     */
    enum Flag {
        /** final access */
        FINAL(ACC_FINAL),
        /** static access */
        STATIC(ACC_STATIC),
        /** public access */
        PUBLIC(ACC_PUBLIC),
        /** private access */
        PRIVATE(ACC_PRIVATE);

        private final int value;

        Flag(final int value) {
            this.value = value;
        }

        /**
         * Get the value of this flag
         * @return the int value
         */
        int getValue() {
            return value;
        }

        /**
         * Return the corresponding class file flag value for an enum set of flags.
         *
         * @param flags enum set of flags
         *
         * @return an integer value representing the flags intrinsic values
         *         or:ed together
         */
        static int getValue(final EnumSet<Flag> flags) {
            int v = 0;
            for (final Flag flag : flags) {
                v |= flag.getValue();
            }
            return v;
        }
    }

}
