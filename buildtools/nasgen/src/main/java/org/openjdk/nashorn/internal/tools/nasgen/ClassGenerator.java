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

package org.openjdk.nashorn.internal.tools.nasgen;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.ACCESSORPROPERTY_CREATE;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_AccessorProperty;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_ArrayList;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_Collection;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_Collections;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_List;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_NativeSymbol;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_PropertyMap;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_ScriptFunction;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_ScriptObject;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.CD_Symbol;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTIONS_EMPTY_LIST;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.COLLECTION_ADD;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.GETTER_PREFIX;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.GET_CLASS_NAME;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_AccessorProperty_create;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_ArrayList_init;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_Collection_add;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_PropertyMap_newMap;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_createBuiltin;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_createBuiltinSpecs;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_setArity;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_setDocumentationKey;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_getClassName;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.MTD_void;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_FIELD_NAME;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_NEWMAP;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_CREATEBUILTIN;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETDOCUMENTATIONKEY;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.SETTER_PREFIX;
import static org.openjdk.nashorn.internal.tools.nasgen.StringConstants.SYMBOL_PREFIX;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassHierarchyResolver.ClassHierarchyInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.openjdk.nashorn.internal.tools.nasgen.MemberInfo.Kind;

/**
 * Base class for class generator classes.
 *
 */
public class ClassGenerator {
    /**
     * Stack map generation needs to know the class hierarchy, but nasgen runs with
     * only its own classes on the class path - none of the Nashorn types it writes
     * code against can be loaded. Anything unknown that looks like a script object
     * is reported as a ScriptObject subtype, so that merging two of them yields
     * ScriptObject; everything else falls back to Object.
     */
    private static final ClassHierarchyResolver NASHORN_TYPES = classDesc -> {
        if (CD_ScriptObject.equals(classDesc)) {
            return ClassHierarchyInfo.ofClass(CD_Object);
        }
        return ClassHierarchyInfo.ofClass(MemberInfo.isScriptObject(classDesc) ? CD_ScriptObject : CD_Object);
    };

    /** The context every class nasgen reads or writes goes through. */
    static final ClassFile CLASS_FILE = ClassFile.of(
        ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.defaultResolver().orElse(NASHORN_TYPES).cached()));

    /** Class file version of the generated classes. */
    static final int CLASS_VERSION = ClassFile.JAVA_7_VERSION;

    static void emitGetClassName(final ClassBuilder clb, final String name) {
        clb.withMethodBody(GET_CLASS_NAME, MTD_getClassName, ACC_PUBLIC, cb -> {
            final MethodGenerator mi = new MethodGenerator(cb, MTD_getClassName);
            mi.loadLiteral(name);
            mi.returnValue();
        });
    }

    /**
     * Adds a {@code static void} method, and hands its body to {@code body}.
     */
    static void withStaticInitializer(final ClassBuilder clb, final String name, final Consumer<MethodGenerator> body) {
        clb.withMethodBody(name, MTD_void, ACC_PUBLIC | ACC_STATIC,
            cb -> body.accept(new MethodGenerator(cb, MTD_void)));
    }

    /**
     * Adds a package private no-arg constructor, and hands its body to {@code body}.
     */
    static void withConstructor(final ClassBuilder clb, final Consumer<MethodGenerator> body) {
        clb.withMethodBody(INIT, MTD_void, 0, cb -> body.accept(new MethodGenerator(cb, MTD_void)));
    }

    /**
     * Opens the property collection the static initializer fills: an ArrayList
     * sized for the members, or the empty list when there are none.
     */
    static void emitStaticInitPrefix(final MethodGenerator mi, final int memberCount) {
        if (memberCount > 0) {
            // new ArrayList(int)
            mi.newObject(CD_ArrayList);
            mi.dup();
            mi.push(memberCount);
            mi.invokeSpecial(CD_ArrayList, INIT, MTD_ArrayList_init);
            // stack: ArrayList
        } else {
            // java.util.Collections.EMPTY_LIST
            mi.getStatic(CD_Collections, COLLECTIONS_EMPTY_LIST, CD_List);
            // stack: List
        }
    }

    /**
     * Turns the collection left on the stack into the class's property map.
     */
    static void emitStaticInitSuffix(final MethodGenerator mi, final ClassDesc className) {
        // stack: Collection
        // pmap = PropertyMap.newMap(Collection<Property>);
        mi.invokeStatic(CD_PropertyMap, PROPERTYMAP_NEWMAP, MTD_PropertyMap_newMap);
        // $nasgenmap$ = pmap;
        mi.putStatic(className, PROPERTYMAP_FIELD_NAME, CD_PropertyMap);
        mi.returnVoid();
    }

    /**
     * The type an accessor for this member deals in. Only the primitive @Property
     * cases are accessed as themselves; everything else - including @Function
     * members, whose descriptor is a method's rather than a field's - is Object.
     */
    private static ClassDesc memInfoType(final MemberInfo memInfo) {
        return switch (memInfo.getJavaDesc().charAt(0)) {
            case 'I' -> CD_int;
            case 'J' -> CD_long;
            case 'D' -> CD_double;
            default  -> CD_Object;
        };
    }

    private static MethodTypeDesc getterDesc(final MemberInfo memInfo) {
        return MethodTypeDesc.of(memInfoType(memInfo));
    }

    private static MethodTypeDesc setterDesc(final MemberInfo memInfo) {
        return MethodTypeDesc.of(CD_void, memInfoType(memInfo));
    }

    static void addGetter(final ClassBuilder clb, final ClassDesc owner, final MemberInfo memInfo) {
        final MethodTypeDesc desc = getterDesc(memInfo);
        clb.withMethodBody(GETTER_PREFIX + memInfo.getJavaName(), desc, ACC_PUBLIC, cb -> {
            final MethodGenerator mi = new MethodGenerator(cb, desc);
            if (memInfo.isStatic() && memInfo.getKind() == Kind.PROPERTY) {
                mi.getStatic(owner, memInfo.getJavaName(), memInfo.getFieldType());
            } else {
                mi.loadLocal(0);
                mi.getField(owner, memInfo.getJavaName(), memInfo.getFieldType());
            }
            mi.returnValue();
        });
    }

    static void addSetter(final ClassBuilder clb, final ClassDesc owner, final MemberInfo memInfo) {
        final MethodTypeDesc desc = setterDesc(memInfo);
        clb.withMethodBody(SETTER_PREFIX + memInfo.getJavaName(), desc, ACC_PUBLIC, cb -> {
            final MethodGenerator mi = new MethodGenerator(cb, desc);
            if (memInfo.isStatic() && memInfo.getKind() == Kind.PROPERTY) {
                mi.loadLocal(1);
                mi.putStatic(owner, memInfo.getJavaName(), memInfo.getFieldType());
            } else {
                mi.loadLocal(0);
                mi.loadLocal(1);
                mi.putField(owner, memInfo.getJavaName(), memInfo.getFieldType());
            }
            mi.returnVoid();
        });
    }

    static void addMapField(final ClassBuilder clb) {
        // add a PropertyMap static field
        clb.withField(PROPERTYMAP_FIELD_NAME, CD_PropertyMap, ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
    }

    static void addField(final ClassBuilder clb, final String name, final ClassDesc type) {
        clb.withField(name, type, ACC_PRIVATE);
    }

    static void addFunctionField(final ClassBuilder clb, final String name) {
        addField(clb, name, CD_Object);
    }

    static void newFunction(final MethodGenerator mi, final String objName, final ClassDesc className,
            final MemberInfo memInfo, final List<MemberInfo> specs) {
        final boolean arityFound = (memInfo.getArity() != MemberInfo.DEFAULT_ARITY);

        loadFunctionName(mi, memInfo.getName());
        mi.loadStaticHandle(className, memInfo.getJavaName(), memInfo.getMethodType());

        assert specs != null;
        if (!specs.isEmpty()) {
            mi.memberInfoArray(className, specs);
            mi.invokeStatic(CD_ScriptFunction, SCRIPTFUNCTION_CREATEBUILTIN, MTD_ScriptFunction_createBuiltinSpecs);
        } else {
            mi.invokeStatic(CD_ScriptFunction, SCRIPTFUNCTION_CREATEBUILTIN, MTD_ScriptFunction_createBuiltin);
        }

        if (arityFound) {
            mi.dup();
            mi.push(memInfo.getArity());
            mi.invokeVirtual(CD_ScriptFunction, SCRIPTFUNCTION_SETARITY, MTD_ScriptFunction_setArity);
        }

        mi.dup();
        mi.loadLiteral(memInfo.getDocumentationKey(objName));
        mi.invokeVirtual(CD_ScriptFunction, SCRIPTFUNCTION_SETDOCUMENTATIONKEY, MTD_ScriptFunction_setDocumentationKey);
    }

    /**
     * Adds an AccessorProperty for a member whose accessors this generator wrote,
     * to the collection on the top of the stack.
     */
    static void linkerAddGetterSetter(final MethodGenerator mi, final ClassDesc className, final MemberInfo memInfo) {
        beginProperty(mi, memInfo.getName(), memInfo.getAttributes());
        // setup getter method handle
        mi.loadVirtualHandle(className, GETTER_PREFIX + memInfo.getJavaName(), getterDesc(memInfo));
        // setup setter method handle
        if (memInfo.isFinal()) {
            mi.pushNull();
        } else {
            mi.loadVirtualHandle(className, SETTER_PREFIX + memInfo.getJavaName(), setterDesc(memInfo));
        }
        endProperty(mi);
    }

    /**
     * Adds an AccessorProperty for an explicit {@code @Getter}/{@code @Setter} pair,
     * to the collection on the top of the stack.
     */
    static void linkerAddGetterSetter(final MethodGenerator mi, final ClassDesc className,
            final MemberInfo getter, final MemberInfo setter) {
        beginProperty(mi, getter.getName(), getter.getAttributes());
        // setup getter method handle
        mi.loadStaticHandle(className, getter.getJavaName(), getter.getMethodType());
        // setup setter method handle
        if (setter == null) {
            mi.pushNull();
        } else {
            mi.loadStaticHandle(className, setter.getJavaName(), setter.getMethodType());
        }
        endProperty(mi);
    }

    // stack: Collection -> Collection, Collection, key, flags
    private static void beginProperty(final MethodGenerator mi, final String propertyName, final int attributes) {
        // dup of Collection instance
        mi.dup();
        // Load property name, converting to Symbol if it begins with "@@"
        loadPropertyKey(mi, propertyName);
        // setup flags
        mi.push(attributes);
    }

    // stack: Collection, Collection, key, flags, getter, setter -> Collection
    private static void endProperty(final MethodGenerator mi) {
        // property = AccessorProperty.create(key, flags, getter, setter);
        mi.invokeStatic(CD_AccessorProperty, ACCESSORPROPERTY_CREATE, MTD_AccessorProperty_create);
        // boolean Collection.add(property)
        mi.invokeInterface(CD_Collection, COLLECTION_ADD, MTD_Collection_add);
        // pop return value of Collection.add
        mi.pop();
    }

    static ScriptClassInfo getScriptClassInfo(final Path fileName) throws IOException {
        try (InputStream in = Files.newInputStream(fileName)) {
            return ScriptClassInfoCollector.collect(CLASS_FILE.parse(in.readAllBytes()));
        }
    }

    static ScriptClassInfo getScriptClassInfo(final byte[] classBuf) {
        return ScriptClassInfoCollector.collect(CLASS_FILE.parse(classBuf));
    }

    private static void loadFunctionName(final MethodGenerator mi, final String propertyName) {
        if (propertyName.startsWith(SYMBOL_PREFIX)) {
            mi.loadLiteral("Symbol[" + propertyName.substring(2) + "]");
        } else {
            mi.loadLiteral(propertyName);
        }
    }

    private static void loadPropertyKey(final MethodGenerator mi, final String propertyName) {
        if (propertyName.startsWith(SYMBOL_PREFIX)) {
            mi.getStatic(CD_NativeSymbol, propertyName.substring(2), CD_Symbol);
        } else {
            mi.loadLiteral(propertyName);
        }
    }
}
