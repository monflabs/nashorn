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

import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * Names and descriptors used for code generation/instrumentation.
 *
 * Class references are {@link ClassDesc} and method signatures are
 * {@link MethodTypeDesc}, the currency of {@code java.lang.classfile}. The
 * {@code CD_} / {@code MTD_} prefixes follow {@link java.lang.constant.ConstantDescs}.
 */
@SuppressWarnings("javadoc")
public interface StringConstants {
    String OBJ_PKG      = "org.openjdk.nashorn.internal.objects";
    String OBJ_ANNO_PKG = OBJ_PKG + ".annotations";
    String RUNTIME_PKG  = "org.openjdk.nashorn.internal.runtime";
    String SCRIPTS_PKG  = "org.openjdk.nashorn.internal.scripts";

    // standard jdk types
    ClassDesc CD_Collection  = ClassDesc.of("java.util.Collection");
    ClassDesc CD_Collections = ClassDesc.of("java.util.Collections");
    ClassDesc CD_ArrayList   = ClassDesc.of("java.util.ArrayList");
    ClassDesc CD_List        = ClassDesc.of("java.util.List");
    ClassDesc CD_ObjectArray = CD_Object.arrayType();

    String CLINIT = "<clinit>";
    String INIT   = "<init>";
    MethodTypeDesc MTD_void = MethodTypeDesc.of(CD_void);

    // Nashorn types
    ClassDesc CD_AccessorProperty     = ClassDesc.of(RUNTIME_PKG, "AccessorProperty");
    ClassDesc CD_PropertyMap          = ClassDesc.of(RUNTIME_PKG, "PropertyMap");
    ClassDesc CD_PrototypeObject      = ClassDesc.of(RUNTIME_PKG, "PrototypeObject");
    ClassDesc CD_ScriptFunction       = ClassDesc.of(RUNTIME_PKG, "ScriptFunction");
    ClassDesc CD_ScriptObject         = ClassDesc.of(RUNTIME_PKG, "ScriptObject");
    ClassDesc CD_Specialization       = ClassDesc.of(RUNTIME_PKG, "Specialization");
    ClassDesc CD_SpecializationArray  = CD_Specialization.arrayType();
    ClassDesc CD_Symbol               = ClassDesc.of(RUNTIME_PKG, "Symbol");
    ClassDesc CD_NativeSymbol         = ClassDesc.of(OBJ_PKG, "NativeSymbol");

    String PROTOTYPE_SUFFIX   = "$Prototype";
    String CONSTRUCTOR_SUFFIX = "$Constructor";

    // This field name is known to Nashorn runtime (Context).
    // Synchronize the name change, if needed at all.
    String PROPERTYMAP_FIELD_NAME = "$nasgenmap$";
    String $CLINIT$               = "$clinit$";

    // java.util.Collection.add(Object)
    String COLLECTION_ADD = "add";
    MethodTypeDesc MTD_Collection_add = MethodTypeDesc.of(CD_boolean, CD_Object);
    // java.util.ArrayList.<init>(int)
    MethodTypeDesc MTD_ArrayList_init = MethodTypeDesc.of(CD_void, CD_int);
    // java.util.Collections.EMPTY_LIST
    String COLLECTIONS_EMPTY_LIST = "EMPTY_LIST";

    // Specialization.<init>
    MethodTypeDesc MTD_Specialization_init2 = MethodTypeDesc.of(CD_void, CD_MethodHandle, CD_boolean, CD_boolean);
    MethodTypeDesc MTD_Specialization_init3 = MethodTypeDesc.of(CD_void, CD_MethodHandle, CD_Class, CD_boolean, CD_boolean);

    // AccessorProperty
    String ACCESSORPROPERTY_CREATE = "create";
    MethodTypeDesc MTD_AccessorProperty_create =
        MethodTypeDesc.of(CD_AccessorProperty, CD_Object, CD_int, CD_MethodHandle, CD_MethodHandle);

    // PropertyMap
    String PROPERTYMAP_NEWMAP = "newMap";
    MethodTypeDesc MTD_PropertyMap_newMap = MethodTypeDesc.of(CD_PropertyMap, CD_Collection);

    // PrototypeObject
    String PROTOTYPEOBJECT_SETCONSTRUCTOR = "setConstructor";
    MethodTypeDesc MTD_PrototypeObject_setConstructor = MethodTypeDesc.of(CD_void, CD_Object, CD_Object);

    // ScriptFunction
    String SCRIPTFUNCTION_SETARITY = "setArity";
    MethodTypeDesc MTD_ScriptFunction_setArity = MethodTypeDesc.of(CD_void, CD_int);
    String SCRIPTFUNCTION_SETDOCUMENTATIONKEY = "setDocumentationKey";
    MethodTypeDesc MTD_ScriptFunction_setDocumentationKey = MethodTypeDesc.of(CD_void, CD_String);
    String SCRIPTFUNCTION_SETPROTOTYPE = "setPrototype";
    MethodTypeDesc MTD_ScriptFunction_setPrototype = MethodTypeDesc.of(CD_void, CD_Object);
    String SCRIPTFUNCTION_CREATEBUILTIN = "createBuiltin";
    MethodTypeDesc MTD_ScriptFunction_createBuiltin =
        MethodTypeDesc.of(CD_ScriptFunction, CD_String, CD_MethodHandle);
    MethodTypeDesc MTD_ScriptFunction_createBuiltinSpecs =
        MethodTypeDesc.of(CD_ScriptFunction, CD_String, CD_MethodHandle, CD_SpecializationArray);
    MethodTypeDesc MTD_ScriptFunction_init3 =
        MethodTypeDesc.of(CD_void, CD_String, CD_MethodHandle, CD_SpecializationArray);
    MethodTypeDesc MTD_ScriptFunction_init4 =
        MethodTypeDesc.of(CD_void, CD_String, CD_MethodHandle, CD_PropertyMap, CD_SpecializationArray);

    // ScriptObject
    MethodTypeDesc MTD_ScriptObject_init = MethodTypeDesc.of(CD_void, CD_PropertyMap);

    String GETTER_PREFIX = "G$";
    String SETTER_PREFIX = "S$";

    // ScriptObject.getClassName() method.
    String GET_CLASS_NAME = "getClassName";
    MethodTypeDesc MTD_getClassName = MethodTypeDesc.of(CD_String);

    String SYMBOL_PREFIX = "@@";
}
