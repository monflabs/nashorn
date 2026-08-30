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
package org.monflabs.nashorn.internal.tools.nasgen;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_ObjectArray;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_Symbol;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.OBJ_PKG;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.RUNTIME_PKG;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.SCRIPTS_PKG;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * Details about a Java method or field annotated with any of the field/method
 * annotations from the org.monflabs.nashorn.internal.objects.annotations package.
 */
public final class MemberInfo implements Cloneable {
    /**
     * The different kinds of available class annotations
     */
    public static enum Kind {

        /**
         * This is a script class
         */
        SCRIPT_CLASS,
        /**
         * This is a constructor
         */
        CONSTRUCTOR,
        /**
         * This is a function
         */
        FUNCTION,
        /**
         * This is a getter
         */
        GETTER,
        /**
         * This is a setter
         */
        SETTER,
        /**
         * This is a property
         */
        PROPERTY,
        /**
         * This is a specialized version of a function
         */
        SPECIALIZED_FUNCTION,
    }

    // keep in sync with org.monflabs.nashorn.internal.objects.annotations.Attribute
    static final int DEFAULT_ATTRIBUTES = 0x0;

    static final int DEFAULT_ARITY = -2;

    // the kind of the script annotation - one of the above constants
    private MemberInfo.Kind kind;
    // script property name
    private String name;
    // script property attributes
    private int attributes;
    // name of the java member
    private String javaName;
    // type descriptor of the java member
    private String javaDesc;
    // access bits of the Java field or method
    private int javaAccess;
    // initial value for static @Property fields
    private ConstantDesc value;
    // class whose object is created to fill property value
    private String initClass;
    // arity of the Function or Constructor
    private int arity;

    private Where where;

    private ClassDesc linkLogicClass;

    private boolean isSpecializedConstructor;

    private boolean isOptimistic;

    private boolean convertsNumericArgs;

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @param kind the kind to set
     */
    public void setKind(final Kind kind) {
        this.kind = kind;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Tag something as specialized constructor or not
     * @param isSpecializedConstructor boolean, true if specialized constructor
     */
    public void setIsSpecializedConstructor(final boolean isSpecializedConstructor) {
        this.isSpecializedConstructor = isSpecializedConstructor;
    }

    /**
     * Check if something is a specialized constructor
     * @return true if specialized constructor
     */
    public boolean isSpecializedConstructor() {
        return isSpecializedConstructor;
    }

    /**
     * Check if this is an optimistic builtin function
     * @return true if optimistic builtin
     */
    public boolean isOptimistic() {
        return isOptimistic;
    }

    /**
     * Tag something as optimistic builtin or not
     * @param isOptimistic boolean, true if builtin constructor
     */
    public void setIsOptimistic(final boolean isOptimistic) {
        this.isOptimistic = isOptimistic;
    }

    /**
     * Check if this function converts arguments for numeric parameters to numbers
     * so it's safe to pass booleans as 0 and 1
     * @return true if it is safe to convert arguments to numbers
     */
    public boolean convertsNumericArgs() {
        return convertsNumericArgs;
    }

    /**
     * Tag this as a function that converts arguments for numeric params to numbers
     * @param convertsNumericArgs if true args can be safely converted to numbers
     */
    public void setConvertsNumericArgs(final boolean convertsNumericArgs) {
        this.convertsNumericArgs = convertsNumericArgs;
    }

    /**
     * Get the SpecializedFunction guard for specializations, i.e. optimistic
     * builtins
     * @return specialization, null if none
     */
    public ClassDesc getLinkLogicClass() {
        return linkLogicClass;
    }

    /**
     * Set the SpecializedFunction link logic class for specializations, i.e. optimistic
     * builtins
     * @param linkLogicClass link logic class
     */

    public void setLinkLogicClass(final ClassDesc linkLogicClass) {
        this.linkLogicClass = linkLogicClass;
    }

    /**
     * @return the attributes
     */
    public int getAttributes() {
        return attributes;
    }

    /**
     * @param attributes the attributes to set
     */
    public void setAttributes(final int attributes) {
        this.attributes = attributes;
    }

    /**
     * @return the javaName
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * @param javaName the javaName to set
     */
    public void setJavaName(final String javaName) {
        this.javaName = javaName;
    }

    /**
     * @return the javaDesc
     */
    public String getJavaDesc() {
        return javaDesc;
    }

    void setJavaDesc(final String javaDesc) {
        this.javaDesc = javaDesc;
    }

    int getJavaAccess() {
        return javaAccess;
    }

    void setJavaAccess(final int access) {
        this.javaAccess = access;
    }

    ConstantDesc getValue() {
        return value;
    }

    void setValue(final ConstantDesc value) {
        this.value = value;
    }

    Where getWhere() {
        return where;
    }

    void setWhere(final Where where) {
        this.where = where;
    }

    boolean isFinal() {
        return (javaAccess & ACC_FINAL) != 0;
    }

    boolean isStatic() {
        return (javaAccess & ACC_STATIC) != 0;
    }

    boolean isStaticFinal() {
        return isStatic() && isFinal();
    }

    boolean isInstanceGetter() {
        return kind == Kind.GETTER && where == Where.INSTANCE;
    }

    /**
     * Check whether this MemberInfo is a getter that resides in the instance
     *
     * @return true if instance setter
     */
    boolean isInstanceSetter() {
        return kind == Kind.SETTER && where == Where.INSTANCE;
    }

    boolean isInstanceProperty() {
        return kind == Kind.PROPERTY && where == Where.INSTANCE;
    }

    boolean isInstanceFunction() {
        return kind == Kind.FUNCTION && where == Where.INSTANCE;
    }

    boolean isPrototypeGetter() {
        return kind == Kind.GETTER && where == Where.PROTOTYPE;
    }

    boolean isPrototypeSetter() {
        return kind == Kind.SETTER && where == Where.PROTOTYPE;
    }

    boolean isPrototypeProperty() {
        return kind == Kind.PROPERTY && where == Where.PROTOTYPE;
    }

    boolean isPrototypeFunction() {
        return kind == Kind.FUNCTION && where == Where.PROTOTYPE;
    }

    boolean isConstructorGetter() {
        return kind == Kind.GETTER && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorSetter() {
        return kind == Kind.SETTER && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorProperty() {
        return kind == Kind.PROPERTY && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorFunction() {
        return kind == Kind.FUNCTION && where == Where.CONSTRUCTOR;
    }

    boolean isConstructor() {
        return kind == Kind.CONSTRUCTOR;
    }

    /**
     * Checks that the Java signature is one the generated code can call.
     *
     * The "is this a valid JS type" checks that used to sit alongside these are
     * gone: they compared ASM {@code Type} instances by reference, so they never
     * rejected anything, and the objects package has grown to rely on that - it
     * has a {@code Symbol} typed {@code @Property} and a {@code @SpecializedFunction}
     * taking a long. Reinstating them is a change of its own, not a port.
     */
    void verify() {
        switch (kind) {
            case CONSTRUCTOR: {
                final MethodTypeDesc type = getMethodType();
                if (!isJSObjectType(type.returnType())) {
                    error("return value of a @Constructor method should be of Object type, found " + type.returnType());
                }
                if (type.parameterCount() < 2) {
                    error("@Constructor methods should have at least 2 args");
                }
                if (!CD_boolean.equals(type.parameterType(0))) {
                    error("first argument of a @Constructor method should be of boolean type, found " + type.parameterType(0));
                }
                if (!CD_Object.equals(type.parameterType(1))) {
                    error("second argument of a @Constructor method should be of Object type, found " + type.parameterType(1));
                }
                verifyTrailingObjectArgs(type, 2, "@Constructor", 3);
            }
            break;
            case FUNCTION: {
                final MethodTypeDesc type = getMethodType();
                if (type.parameterCount() < 1) {
                    error("@Function methods should have at least 1 arg");
                }
                if (!CD_Object.equals(type.parameterType(0))) {
                    error("first argument of a @Function method should be of Object type, found " + type.parameterType(0));
                }
                verifyTrailingObjectArgs(type, 1, "@Function", 2);
            }
            break;
            case GETTER: {
                final MethodTypeDesc type = getMethodType();
                if (type.parameterCount() != 1) {
                    error("@Getter methods should have one argument");
                }
                if (!CD_Object.equals(type.parameterType(0))) {
                    error("first argument of a @Getter method should be of Object type, found: " + type.parameterType(0));
                }
                if (CD_void.equals(type.returnType())) {
                    error("return type of getter should not be void");
                }
            }
            break;
            case SETTER: {
                final MethodTypeDesc type = getMethodType();
                if (type.parameterCount() != 2) {
                    error("@Setter methods should have two arguments");
                }
                if (!CD_Object.equals(type.parameterType(0))) {
                    error("first argument of a @Setter method should be of Object type, found: " + type.parameterType(0));
                }
                if (!CD_void.equals(type.returnType())) {
                    error("return type of of a @Setter method should be void, found: " + type.returnType());
                }
            }
            break;
            case PROPERTY: {
                if ((where == Where.CONSTRUCTOR || where == Where.PROTOTYPE) && isStatic() && !isFinal()) {
                    error("static Where." + where + " @Property should be final");
                }
            }
            break;

            default:
            break;
        }
    }

    /**
     * Verifies that every argument from {@code from} on is Object, except the last one,
     * which may also be Object[] - in which case the method is a vararg and may not have
     * more than {@code maxVarArgCount} arguments in total.
     */
    private void verifyTrailingObjectArgs(final MethodTypeDesc type, final int from, final String anno, final int maxVarArgCount) {
        final int count = type.parameterCount();
        if (count <= from) {
            return;
        }

        for (int i = from; i < count - 1; i++) {
            if (!CD_Object.equals(type.parameterType(i))) {
                error(i + "'th argument of a " + anno + " method should be of Object type, found " + type.parameterType(i));
            }
        }

        final ClassDesc lastArgType = type.parameterType(count - 1);
        final boolean isVarArg = CD_ObjectArray.equals(lastArgType);
        if (!CD_Object.equals(lastArgType) && !isVarArg) {
            error("last argument of a " + anno + " method is neither Object nor Object[] type: " + lastArgType.descriptorString());
        }
        if (isVarArg && count > maxVarArgCount) {
            error("vararg of a " + anno + " method has more than " + maxVarArgCount + " arguments");
        }
    }

    /**
     * The type of the Java method this member describes.
     */
    MethodTypeDesc getMethodType() {
        return MethodTypeDesc.ofDescriptor(javaDesc);
    }

    /**
     * The type of the Java field this member describes.
     */
    ClassDesc getFieldType() {
        return ClassDesc.ofDescriptor(javaDesc);
    }

    /**
     * Returns whether the given class represents a ScriptObject subtype.
     */
    public static boolean isScriptObject(final ClassDesc type) {
        if (!type.isClassOrInterface()) {
            return false;
        }

        // very crude check for ScriptObject subtype!
        final String pkg = type.packageName();
        final String simpleName = type.displayName();

        if (pkg.equals(OBJ_PKG)) {
            return simpleName.startsWith("Native")
                || simpleName.equals("Global")
                || simpleName.equals("ArrayBufferView");
        }

        if (pkg.equals(RUNTIME_PKG)) {
            return switch (simpleName) {
                case "ScriptObject", "ScriptFunction", "NativeJavaPackage", "Scope" -> true;
                default -> false;
            };
        }

        if (pkg.equals(SCRIPTS_PKG)) {
            return simpleName.equals("JD") || simpleName.equals("JO");
        }

        return false;
    }

    private static boolean isJSObjectType(final ClassDesc type) {
        return CD_Object.equals(type) || CD_String.equals(type) || isScriptObject(type);
    }

    private void error(final String msg) {
        throw new RuntimeException(javaName + " of type " + javaDesc + " : " + msg);
    }

    /**
     * @return the initClass
     */
    String getInitClass() {
        return initClass;
    }

    /**
     * @param initClass the initClass to set
     */
    void setInitClass(final String initClass) {
        this.initClass = initClass;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            assert false : "clone not supported " + e;
            return null;
        }
    }

    /**
     * @return the arity
     */
    int getArity() {
        return arity;
    }

    /**
     * @param arity the arity to set
     */
    void setArity(final int arity) {
        this.arity = arity;
    }

    String getDocumentationKey(final String objName) {
        if (kind == Kind.FUNCTION) {
            final StringBuilder buf = new StringBuilder(objName);
            switch (where) {
                case CONSTRUCTOR:
                    break;
                case PROTOTYPE:
                    buf.append(".prototype");
                    break;
                case INSTANCE:
                    buf.append(".this");
                    break;
            }
            buf.append('.');
            buf.append(name);
            return buf.toString();
        }

        return null;
    }
}
