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

import static java.lang.constant.ConstantDescs.CD_Object;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.$CLINIT$;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CD_ScriptObject;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.CLINIT;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.INIT;
import static org.monflabs.nashorn.internal.tools.nasgen.StringConstants.MTD_void;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.monflabs.nashorn.internal.tools.nasgen.MemberInfo.Kind;

/**
 * This class instruments the java class annotated with @ScriptClass.
 *
 * Changes done are:
 *
 * 1) remove all org.monflabs.nashorn.internal.objects.annotations.* annotations.
 * 2) static final @Property fields stay here. Other @Property fields moved to
 *    respective classes depending on 'where' value of annotation.
 * 2) add "Map" type static field named "$map".
 * 3) add static initializer block to initialize map.
 */
public final class ScriptClassInstrumentor implements ClassTransform {
    private final ScriptClassInfo scriptClassInfo;
    private final ClassDesc className;
    private final int memberCount;
    private final boolean staticInitFound;

    private ScriptClassInstrumentor(final ClassModel cm, final ScriptClassInfo sci) {
        if (sci == null) {
            throw new IllegalArgumentException("Null ScriptClassInfo, is the class annotated?");
        }
        this.scriptClassInfo = sci;
        this.className = sci.getJavaType();
        this.memberCount = sci.getInstancePropertyCount();
        this.staticInitFound = cm.methods().stream()
                                 .anyMatch(m -> m.methodName().equalsString(CLINIT));
    }

    /**
     * Instruments a {@code @ScriptClass} annotated class.
     *
     * @param cm  the parsed class
     * @param sci the nasgen annotations found on it
     * @return the instrumented class
     */
    static byte[] instrument(final ClassModel cm, final ScriptClassInfo sci) {
        final ScriptClassInstrumentor instrumentor = new ScriptClassInstrumentor(cm, sci);
        return ClassGenerator.CLASS_FILE.transformClass(cm,
                instrumentor.andThen(ClassTransform.endHandler(instrumentor::emitAdditions)));
    }

    @Override
    public void accept(final ClassBuilder clb, final ClassElement element) {
        switch (element) {
            case RuntimeVisibleAnnotationsAttribute annos -> stripAnnotations(annos, clb::with);
            case FieldModel field -> transformField(clb, field);
            case MethodModel method -> transformMethod(clb, method);
            default -> clb.with(element);
        }
    }

    private void transformField(final ClassBuilder clb, final FieldModel field) {
        final MemberInfo memInfo = scriptClassInfo.find(field.fieldName().stringValue(),
                field.fieldType().stringValue(), field.flags().flagsMask());
        if (memInfo != null && memInfo.getKind() == Kind.PROPERTY &&
                memInfo.getWhere() != Where.INSTANCE && !memInfo.isStaticFinal()) {
            // non-instance @Property fields - these have to go elsewhere unless 'static final'
            return;
        }

        clb.transformField(field, (fb, element) -> {
            switch (element) {
                case RuntimeVisibleAnnotationsAttribute annos -> stripAnnotations(annos, fb::with);
                default -> fb.with(element);
            }
        });
    }

    private void transformMethod(final ClassBuilder clb, final MethodModel method) {
        final boolean isConstructor = method.methodName().equalsString(INIT);
        final boolean isStaticInit  = method.methodName().equalsString(CLINIT);

        clb.transformMethod(method, (mb, element) -> {
            switch (element) {
                case RuntimeVisibleAnnotationsAttribute annos -> stripAnnotations(annos, mb::with);
                case CodeModel code -> mb.transformCode(code, bodyTransform(isConstructor, isStaticInit));
                default -> mb.with(element);
            }
        });
    }

    /**
     * Hooks the two places nasgen injects code into hand written methods: the
     * end of {@code <clinit>}, and the point in a constructor right after the
     * super call.
     */
    private CodeTransform bodyTransform(final boolean isConstructor, final boolean isStaticInit) {
        return (CodeBuilder cb, CodeElement element) -> {
            // call $clinit$ just before return from <clinit>
            if (isStaticInit && element instanceof ReturnInstruction) {
                cb.invokestatic(className, $CLINIT$, MTD_void);
                cb.with(element);
                return;
            }

            cb.with(element);

            if (isConstructor && memberCount > 0 && isSuperConstructorCall(element)) {
                initInstanceMembers(cb);
            }
        };
    }

    private static boolean isSuperConstructorCall(final CodeElement element) {
        return element instanceof InvokeInstruction invoke
            && invoke.opcode() == Opcode.INVOKESPECIAL
            && invoke.name().equalsString(INIT)
            && CD_ScriptObject.equals(invoke.owner().asSymbol());
    }

    /** Initializes the @Property and @Function fields that live on the instance. */
    private void initInstanceMembers(final CodeBuilder cb) {
        final MethodGenerator mi = new MethodGenerator(cb, MTD_void);
        for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (memInfo.isInstanceProperty() && !memInfo.getInitClass().isEmpty()) {
                final ClassDesc clazz = ClassDesc.of(memInfo.getInitClass());
                mi.loadThis();
                mi.newObject(clazz);
                mi.dup();
                mi.invokeSpecial(clazz, INIT, MTD_void);
                mi.putField(className, memInfo.getJavaName(), memInfo.getFieldType());
            }

            if (memInfo.isInstanceFunction()) {
                mi.loadThis();
                ClassGenerator.newFunction(mi, scriptClassInfo.getName(), className, memInfo,
                        scriptClassInfo.findSpecializations(memInfo.getJavaName()));
                mi.putField(className, memInfo.getJavaName(), CD_Object);
            }
        }
    }

    /** Everything nasgen appends to the class it instruments. */
    private void emitAdditions(final ClassBuilder clb) {
        emitFields(clb);
        emitStaticInitializer(clb);
        emitGettersSetters(clb);
    }

    private void emitFields(final ClassBuilder clb) {
        // introduce "Function" type instance fields for each
        // instance @Function in script class info
        for (MemberInfo memInfo : scriptClassInfo.getMembers()) {
            if (memInfo.isInstanceFunction()) {
                ClassGenerator.addFunctionField(clb, memInfo.getJavaName());
                memInfo = (MemberInfo)memInfo.clone();
                memInfo.setJavaDesc(CD_Object.descriptorString());
                ClassGenerator.addGetter(clb, className, memInfo);
                ClassGenerator.addSetter(clb, className, memInfo);
            }
        }
        // omit addMapField() since instance classes already define a static PropertyMap field
    }

    private void emitGettersSetters(final ClassBuilder clb) {
        if (memberCount > 0) {
            for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
                if (memInfo.isInstanceProperty()) {
                    ClassGenerator.addGetter(clb, className, memInfo);
                    if (! memInfo.isFinal()) {
                        ClassGenerator.addSetter(clb, className, memInfo);
                    }
                }
            }
        }
    }

    private void emitStaticInitializer(final ClassBuilder clb) {
        if (! staticInitFound) {
            // no user written <clinit> and so create one - the same one the
            // body transform would have produced for an empty static initializer
            ClassGenerator.withStaticInitializer(clb, CLINIT, mi -> {
                mi.invokeStatic(className, $CLINIT$, MTD_void);
                mi.returnVoid();
            });
        }

        // Now generate $clinit$
        ClassGenerator.withStaticInitializer(clb, $CLINIT$, mi -> {
            ClassGenerator.emitStaticInitPrefix(mi, memberCount);
            if (memberCount > 0) {
                for (final MemberInfo memInfo : scriptClassInfo.getMembers()) {
                    if (memInfo.isInstanceProperty() || memInfo.isInstanceFunction()) {
                        ClassGenerator.linkerAddGetterSetter(mi, className, memInfo);
                    } else if (memInfo.isInstanceGetter()) {
                        final MemberInfo setter = scriptClassInfo.findSetter(memInfo);
                        ClassGenerator.linkerAddGetterSetter(mi, className, memInfo, setter);
                    }
                }
            }
            ClassGenerator.emitStaticInitSuffix(mi, className);
        });
    }

    /**
     * Relays the annotations nasgen does not own, dropping the attribute entirely
     * if that leaves nothing. Only the runtime visible attribute is filtered: every
     * annotation in {@link ScriptClassInfo#annotations} has RUNTIME retention, so
     * the invisible one cannot hold any of them.
     */
    private static void stripAnnotations(final RuntimeVisibleAnnotationsAttribute annos,
            final Consumer<RuntimeVisibleAnnotationsAttribute> emit) {
        final List<Annotation> kept = annos.annotations().stream()
                .filter(anno -> !ScriptClassInfo.annotations.containsKey(anno.classSymbol()))
                .toList();
        if (!kept.isEmpty()) {
            emit.accept(RuntimeVisibleAnnotationsAttribute.of(kept));
        }
    }

    /**
     * External entry point for ScriptClassInstrumentor if run from the command line
     *
     * @param args arguments - one argument is needed, the name of the class to instrument
     *
     * @throws IOException if there are problems reading class
     */
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: " + ScriptClassInstrumentor.class.getName() + " <class>");
            System.exit(1);
        }

        final Path file = Path.of(args[0].replace('.', '/') + ".class");
        final ClassModel cm = ClassGenerator.CLASS_FILE.parse(Files.readAllBytes(file));
        final ScriptClassInfo sci = ScriptClassInfoCollector.collect(cm);
        if (sci == null) {
            System.err.println("No @ScriptClass in " + file);
            System.exit(2);
            throw new AssertionError(); //guard against warning that sci is null below
        }

        try {
            sci.verify();
        } catch (final Exception e) {
            System.err.println(e.getMessage());
            System.exit(3);
        }

        Files.write(file, instrument(cm, sci));
    }
}
