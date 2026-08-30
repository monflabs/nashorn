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

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static org.monflabs.nashorn.internal.tools.nasgen.ScriptClassInfo.PROPERTY_ANNO;
import static org.monflabs.nashorn.internal.tools.nasgen.ScriptClassInfo.SCRIPT_CLASS_ANNO;
import static org.monflabs.nashorn.internal.tools.nasgen.ScriptClassInfo.WHERE_ENUM;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.monflabs.nashorn.internal.tools.nasgen.MemberInfo.Kind;

/**
 * Collects all @ScriptClass and other annotation information from a compiled
 * .class file. Enforces that @Function/@Getter/@Setter/@Constructor methods are
 * declared to be 'static'.
 */
public final class ScriptClassInfoCollector {
    private ScriptClassInfoCollector() {
    }

    /**
     * Reads the nasgen annotations off a class.
     *
     * @param cm the parsed class
     * @return its script class info, or null if the class is not a {@code @ScriptClass}
     */
    static ScriptClassInfo collect(final ClassModel cm) {
        final Optional<Annotation> scriptClass = findAnnotation(cm, SCRIPT_CLASS_ANNO);
        if (scriptClass.isEmpty()) {
            return null;
        }

        final ClassDesc javaType = cm.thisClass().asSymbol();
        final String scriptClassName = stringElement(scriptClass.get(), "value").orElse(null);

        final List<MemberInfo> members = new ArrayList<>();
        for (final FieldModel field : cm.fields()) {
            findAnnotation(field, PROPERTY_ANNO)
                .map(anno -> propertyMember(field, anno))
                .ifPresent(members::add);
        }
        for (final MethodModel method : cm.methods()) {
            methodMember(javaType, scriptClassName, method).ifPresent(members::add);
        }

        final ScriptClassInfo sci = new ScriptClassInfo();
        sci.setName(scriptClassName);
        sci.setMembers(members);
        sci.setJavaType(javaType);
        return sci;
    }

    /** A {@code @Property} field. */
    private static MemberInfo propertyMember(final FieldModel field, final Annotation anno) {
        final MemberInfo memInfo = new MemberInfo();
        final String fieldName = field.fieldName().stringValue();

        memInfo.setKind(Kind.PROPERTY);
        memInfo.setJavaName(fieldName);
        memInfo.setJavaDesc(field.fieldType().stringValue());
        memInfo.setJavaAccess(field.flags().flagsMask());

        if ((field.flags().flagsMask() & ACC_STATIC) != 0) {
            field.findAttribute(Attributes.constantValue())
                 .ifPresent(cv -> memInfo.setValue(cv.constant().constantValue()));
        }

        memInfo.setName(stringElement(anno, "name").orElse(fieldName));
        memInfo.setAttributes(intElement(anno, "attributes").orElse(MemberInfo.DEFAULT_ATTRIBUTES));
        memInfo.setInitClass(stringElement(anno, "clazz").orElse(""));
        memInfo.setWhere(whereElement(anno).orElse(Where.INSTANCE));
        return memInfo;
    }

    /** A method carrying any of the nasgen method annotations. */
    private static Optional<MemberInfo> methodMember(final ClassDesc javaType, final String scriptClassName,
            final MethodModel method) {
        final String methodName = method.methodName().stringValue();
        final String methodDesc = method.methodType().stringValue();

        for (final Annotation anno : annotationsOf(method)) {
            final Kind annoKind = ScriptClassInfo.annotations.get(anno.classSymbol());
            if (annoKind == null) {
                continue;
            }
            if (!method.flags().has(java.lang.reflect.AccessFlag.STATIC)) {
                throw new RuntimeException(javaType.displayName() + "." + methodName + methodDesc
                        + " : nasgen method annotations cannot be on instance methods");
            }

            final MemberInfo memInfo = new MemberInfo();
            memInfo.setKind(annoKind);
            memInfo.setJavaName(methodName);
            memInfo.setJavaDesc(methodDesc);
            memInfo.setJavaAccess(method.flags().flagsMask());

            // an empty @Function(name="") means "use the Java name"
            final String name = stringElement(anno, "name").filter(s -> !s.isEmpty()).orElse(null);
            memInfo.setName(name != null ? name
                    : annoKind == Kind.CONSTRUCTOR ? scriptClassName : methodName);
            memInfo.setAttributes(intElement(anno, "attributes").orElse(MemberInfo.DEFAULT_ATTRIBUTES));
            memInfo.setArity(intElement(anno, "arity").orElse(MemberInfo.DEFAULT_ARITY));

            final boolean isSpecializedConstructor = booleanElement(anno, "isConstructor");
            memInfo.setIsSpecializedConstructor(isSpecializedConstructor);
            memInfo.setIsOptimistic(booleanElement(anno, "isOptimistic"));
            memInfo.setConvertsNumericArgs(booleanElement(anno, "convertsNumericArgs"));
            memInfo.setLinkLogicClass(element(anno, "linkLogic")
                    .filter(AnnotationValue.OfClass.class::isInstance)
                    .map(v -> ((AnnotationValue.OfClass)v).classSymbol())
                    .orElse(MethodGenerator.EMPTY_LINK_LOGIC_TYPE));
            memInfo.setWhere(whereElement(anno)
                    .orElseGet(() -> defaultWhere(annoKind, isSpecializedConstructor)));

            return Optional.of(memInfo);
        }

        return Optional.empty();
    }

    /**
     * By default @Getter/@Setter belong to the INSTANCE and @Function to the PROTOTYPE.
     */
    private static Where defaultWhere(final Kind kind, final boolean isSpecializedConstructor) {
        return switch (kind) {
            case GETTER, SETTER -> Where.INSTANCE;
            case CONSTRUCTOR -> Where.CONSTRUCTOR;
            case FUNCTION -> Where.PROTOTYPE;
            case SPECIALIZED_FUNCTION -> isSpecializedConstructor ? Where.CONSTRUCTOR : Where.PROTOTYPE;
            default -> null;
        };
    }

    // -- annotation lookup helpers

    static List<Annotation> annotationsOf(final AttributedElement element) {
        return element.findAttribute(Attributes.runtimeVisibleAnnotations())
                      .map(a -> a.annotations())
                      .orElse(List.of());
    }

    private static Optional<Annotation> findAnnotation(final AttributedElement element, final ClassDesc annoType) {
        return annotationsOf(element).stream()
                                     .filter(anno -> annoType.equals(anno.classSymbol()))
                                     .findFirst();
    }

    private static Optional<AnnotationValue> element(final Annotation anno, final String name) {
        return anno.elements().stream()
                   .filter(e -> e.name().equalsString(name))
                   .map(e -> e.value())
                   .findFirst();
    }

    private static Optional<String> stringElement(final Annotation anno, final String name) {
        return element(anno, name)
                .filter(AnnotationValue.OfString.class::isInstance)
                .map(v -> ((AnnotationValue.OfString)v).stringValue());
    }

    private static Optional<Integer> intElement(final Annotation anno, final String name) {
        return element(anno, name)
                .filter(AnnotationValue.OfInt.class::isInstance)
                .map(v -> ((AnnotationValue.OfInt)v).intValue());
    }

    private static boolean booleanElement(final Annotation anno, final String name) {
        return element(anno, name)
                .filter(AnnotationValue.OfBoolean.class::isInstance)
                .map(v -> ((AnnotationValue.OfBoolean)v).booleanValue())
                .orElse(Boolean.FALSE);
    }

    private static Optional<Where> whereElement(final Annotation anno) {
        return element(anno, "where")
                .filter(AnnotationValue.OfEnum.class::isInstance)
                .map(AnnotationValue.OfEnum.class::cast)
                .filter(v -> WHERE_ENUM.equals(v.classSymbol()))
                .map(v -> Where.valueOf(v.constantName().stringValue()));
    }

    /**
     * External entry point for ScriptClassInfoCollector if invoked from the command line
     * @param args argument vector, args contains a class for which to collect info
     * @throws IOException if there were problems parsing args or class
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: " + ScriptClassInfoCollector.class.getName() + " <class>");
            System.exit(1);
        }

        final Path file = Path.of(args[0].replace('.', '/') + ".class");
        final ScriptClassInfo sci = ClassGenerator.getScriptClassInfo(file);
        final PrintStream out = System.out;
        if (sci != null) {
            out.println("script class: " + sci.getName());
            out.println("===================================");
            for (final MemberInfo memInfo : sci.getMembers()) {
                out.println("kind : " + memInfo.getKind());
                out.println("name : " + memInfo.getName());
                out.println("attributes: " + memInfo.getAttributes());
                out.println("javaName: " + memInfo.getJavaName());
                out.println("javaDesc: " + memInfo.getJavaDesc());
                out.println("where: " + memInfo.getWhere());
                out.println("=====================================");
            }
        } else {
            out.println(file + " is not a @ScriptClass");
        }
    }
}
