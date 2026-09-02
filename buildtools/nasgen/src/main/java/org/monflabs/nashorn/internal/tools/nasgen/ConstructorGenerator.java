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

package org.monflabs.nashorn.internal.tools.nasgen;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_PropertyMap;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_PrototypeObject;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_ScriptFunction;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_ScriptObject;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CLINIT;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CONSTRUCTOR_SUFFIX;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_PrototypeObject_setConstructor;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_init3;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_init4;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_setArity;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_setDocumentationKey;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptFunction_setPrototype;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_ScriptObject_init;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_void;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.PROPERTYMAP_FIELD_NAME;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.PROTOTYPEOBJECT_SETCONSTRUCTOR;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETARITY;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETDOCUMENTATIONKEY;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.SCRIPTFUNCTION_SETPROTOTYPE;

import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * This class generates constructor class for a @ScriptClass annotated class.
 *
 */
public class ConstructorGenerator extends ClassGenerator {
    private final ScriptClassInfo scriptClassInfo;
    private final ClassDesc className;
    private final MemberInfo constructor;
    private final int memberCount;
    private final List<MemberInfo> specs;

    ConstructorGenerator(final ScriptClassInfo sci) {
        this.scriptClassInfo = sci;

        this.className = scriptClassInfo.getConstructorClass();
        this.constructor = scriptClassInfo.getConstructor();
        this.memberCount = scriptClassInfo.getConstructorMemberCount();
        this.specs = scriptClassInfo.getSpecializedConstructors();
    }

    byte[] getClassBytes() {
        // new class extending from ScriptObject
        final ClassDesc superClass = (constructor != null) ? CD_ScriptFunction : CD_ScriptObject;
        return CLASS_FILE.build(className, clb -> {
            clb.withVersion(CLASS_VERSION, 0);
            clb.withFlags(ACC_FINAL);
            clb.withSuperclass(superClass);

            if (memberCount > 0) {
                emitFields(clb);
                emitStaticInitializer(clb);
            }
            emitConstructor(clb);

            if (constructor == null) {
                emitGetClassName(clb, scriptClassInfo.getName());
            }
        });
    }

    // --Internals only below this point
    private void emitFields(final ClassBuilder clb) {
        // Introduce "Function" type instance fields for each
        // constructor @Function in script class and introduce instance
        // fields for each constructor @Property in the script class.
        for (MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (memInfo.isConstructorFunction()) {
                addFunctionField(clb, memInfo.getJavaName());
                memInfo = (MemberInfo)memInfo.clone();
                memInfo.setJavaDesc(CD_Object.descriptorString());
                memInfo.setJavaAccess(ACC_PUBLIC);
                addGetter(clb, className, memInfo);
                addSetter(clb, className, memInfo);
            } else if (memInfo.isConstructorProperty()) {
                if (memInfo.isStaticFinal()) {
                    addGetter(clb, scriptClassInfo.getJavaType(), memInfo);
                } else {
                    addField(clb, memInfo.getJavaName(), memInfo.getFieldType());
                    memInfo = (MemberInfo)memInfo.clone();
                    memInfo.setJavaAccess(ACC_PUBLIC);
                    addGetter(clb, className, memInfo);
                    addSetter(clb, className, memInfo);
                }
            }
        }

        addMapField(clb);
    }

    private void emitStaticInitializer(final ClassBuilder clb) {
        withStaticInitializer(clb, CLINIT, mi -> {
            emitStaticInitPrefix(mi, memberCount);
            for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
                if (memInfo.isConstructorFunction() || memInfo.isConstructorProperty()) {
                    linkerAddGetterSetter(mi, className, memInfo);
                } else if (memInfo.isConstructorGetter()) {
                    final MemberInfo setter = scriptClassInfo.findSetter(memInfo);
                    linkerAddGetterSetter(mi, scriptClassInfo.getJavaType(), memInfo, setter);
                }
            }
            emitStaticInitSuffix(mi, className);
        });
    }

    private void emitConstructor(final ClassBuilder clb) {
        withConstructor(clb, mi -> {
            callSuper(mi);

            if (memberCount > 0) {
                // initialize Function type fields
                initFunctionFields(mi);
                // initialize data fields
                initDataFields(mi);
            }

            if (constructor != null) {
                initPrototype(mi);
                final int arity = constructor.getArity();
                if (arity != MemberInfo.DEFAULT_ARITY) {
                    mi.loadThis();
                    mi.push(arity);
                    mi.invokeVirtual(CD_ScriptFunction, SCRIPTFUNCTION_SETARITY, MTD_ScriptFunction_setArity);
                }

                mi.loadThis();
                mi.loadLiteral(scriptClassInfo.getName());
                mi.invokeVirtual(CD_ScriptFunction, SCRIPTFUNCTION_SETDOCUMENTATIONKEY,
                            MTD_ScriptFunction_setDocumentationKey);
            }
            mi.returnVoid();
        });
    }

    private void loadMap(final MethodGenerator mi) {
        if (memberCount > 0) {
            mi.getStatic(className, PROPERTYMAP_FIELD_NAME, CD_PropertyMap);
        }
    }

    private void callSuper(final MethodGenerator mi) {
        final ClassDesc superClass;
        final MethodTypeDesc superDesc;
        mi.loadThis();
        if (constructor == null) {
            // call ScriptObject.<init>
            superClass = CD_ScriptObject;
            superDesc = (memberCount > 0) ? MTD_ScriptObject_init : MTD_void;
            loadMap(mi);
        } else {
            // call Function.<init>
            superClass = CD_ScriptFunction;
            superDesc = (memberCount > 0) ? MTD_ScriptFunction_init4 : MTD_ScriptFunction_init3;
            mi.loadLiteral(constructor.getName());
            mi.loadStaticHandle(scriptClassInfo.getJavaType(), constructor.getJavaName(), constructor.getMethodType());
            loadMap(mi);
            mi.memberInfoArray(scriptClassInfo.getJavaType(), specs); //pushes null if specs empty
        }

        mi.invokeSpecial(superClass, INIT, superDesc);
    }

    private void initFunctionFields(final MethodGenerator mi) {
        assert memberCount > 0;
        for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (!memInfo.isConstructorFunction()) {
                continue;
            }
            mi.loadThis();
            newFunction(mi, scriptClassInfo.getName(), scriptClassInfo.getJavaType(), memInfo,
                    scriptClassInfo.findSpecializations(memInfo.getJavaName()));
            mi.putField(className, memInfo.getJavaName(), CD_Object);
        }
    }

    private void initDataFields(final MethodGenerator mi) {
        assert memberCount > 0;
        for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
           if (!memInfo.isConstructorProperty() || memInfo.isFinal()) {
               continue;
           }
           final ConstantDesc value = memInfo.getValue();
           if (value != null) {
               mi.loadThis();
               mi.loadLiteral(value);
               mi.putField(className, memInfo.getJavaName(), memInfo.getFieldType());
           } else if (!memInfo.getInitClass().isEmpty()) {
               final ClassDesc clazz = ClassDesc.of(memInfo.getInitClass());
               mi.loadThis();
               mi.newObject(clazz);
               mi.dup();
               mi.invokeSpecial(clazz, INIT, MTD_void);
               mi.putField(className, memInfo.getJavaName(), memInfo.getFieldType());
           }
        }
    }

    private void initPrototype(final MethodGenerator mi) {
        assert constructor != null;
        mi.loadThis();
        final ClassDesc protoName = scriptClassInfo.getPrototypeClass();
        mi.newObject(protoName);
        mi.dup();
        mi.invokeSpecial(protoName, INIT, MTD_void);
        mi.dup();
        mi.loadThis();
        mi.invokeStatic(CD_PrototypeObject, PROTOTYPEOBJECT_SETCONSTRUCTOR,
                MTD_PrototypeObject_setConstructor);
        mi.invokeVirtual(CD_ScriptFunction, SCRIPTFUNCTION_SETPROTOTYPE, MTD_ScriptFunction_setPrototype);
    }

    /**
     * Entry point for ConstructorGenerator run separately as an application. Will display
     * usage. Takes one argument, a class name.
     * @param args args vector
     * @throws IOException if class can't be read
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: " + ConstructorGenerator.class.getName() + " <class>");
            System.exit(1);
        }

        final String className = args[0].replace('.', '/');
        final ScriptClassInfo sci = getScriptClassInfo(Path.of(className + ".class"));
        if (sci == null) {
            System.err.println("No @ScriptClass in " + className);
            System.exit(2);
            throw new IOException(); // get rid of warning for sci.verify() below - may be null
        }

        try {
            sci.verify();
        } catch (final Exception e) {
            System.err.println(e.getMessage());
            System.exit(3);
        }
        final ConstructorGenerator gen = new ConstructorGenerator(sci);
        Files.write(Path.of(className + CONSTRUCTOR_SUFFIX + ".class"), gen.getClassBytes());
    }
}
