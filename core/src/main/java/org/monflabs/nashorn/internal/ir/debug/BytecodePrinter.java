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

package org.monflabs.nashorn.internal.ir.debug;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.internal.runtime.ScriptEnvironment;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;
import org.monflabs.nashorn.internal.runtime.linker.NameCodec;
import org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Pretty printer for {@code --print-code}, and the Graphviz control flow graphs
 * that {@code --print-code=dir:<dir>} writes alongside it.
 *
 * What it adds over a stock disassembler is Nashorn's own vocabulary: an
 * invokedynamic to Nashorn's bootstrap prints as the operation it performs, with
 * its call site flags decoded, rather than as a bootstrap handle and an integer.
 */
public final class BytecodePrinter {
    private static final ClassDesc BOOTSTRAP_CLASS = ClassDesc.of(Bootstrap.class.getName());

    private final ScriptEnvironment env;
    private final StringBuilder sb = new StringBuilder();

    private BytecodePrinter(final ScriptEnvironment env) {
        this.env = env;
    }

    /**
     * Disassembles a class.
     *
     * @param env      the script environment, for the print-code options
     * @param bytecode the class
     *
     * @return the disassembly, as human readable text
     */
    public static String disassemble(final ScriptEnvironment env, final byte[] bytecode) {
        final BytecodePrinter printer = new BytecodePrinter(env);
        printer.printClass(ClassFile.of().parse(bytecode));
        return printer.sb.toString();
    }

    private void printClass(final ClassModel cm) {
        for (final FieldModel field : cm.fields()) {
            sb.append("  ").append(field.fieldType().stringValue()).append(' ')
              .append(field.fieldName().stringValue()).append('\n');
        }
        for (final MethodModel method : cm.methods()) {
            printMethod(method);
        }
    }

    private void printMethod(final MethodModel method) {
        final String name = method.methodName().stringValue();
        sb.append('\n').append("  ").append(name).append(method.methodType().stringValue()).append('\n');

        final CodeModel code = method.code().orElse(null);
        if (code == null) {
            return;
        }

        final List<CodeElement> elements = code.elementList();
        final Map<Label, String> labelNames = nameLabels(elements);
        final ControlFlowGraph graph = env._print_code_dir == null ? null : new ControlFlowGraph(name, labelNames);

        // number of NOPs printed in a row, so that dead code does not drown the listing
        int nops = 0;

        for (final CodeElement element : elements) {
            switch (element) {
                case LabelTarget target -> {
                    sb.append("   ").append(labelNames.get(target.label())).append(":\n");
                    if (graph != null) {
                        graph.startBlock(target.label());
                    }
                }
                case LineNumber line -> sb.append("    // line ").append(line.line()).append('\n');
                case ExceptionCatch handler -> sb.append("    // try ")
                        .append(labelNames.get(handler.tryStart())).append(" - ")
                        .append(labelNames.get(handler.tryEnd())).append(" catch ")
                        .append(handler.catchType().map(t -> t.asInternalName()).orElse("any"))
                        .append(" -> ").append(labelNames.get(handler.handler())).append('\n');
                case LocalVariable local -> sb.append("    // local ").append(local.slot()).append(' ')
                        .append(local.name().stringValue()).append(' ')
                        .append(local.type().stringValue()).append('\n');
                case Instruction insn -> {
                    if (insn.opcode() == Opcode.NOP) {
                        // collapse runs of NOPs to a single one plus an ellipsis
                        if (++nops == 2) {
                            sb.append("    ...\n");
                        }
                        if (nops >= 2) {
                            continue;
                        }
                    } else {
                        nops = 0;
                    }
                    sb.append("    ").append(render(insn, labelNames)).append('\n');
                    if (graph != null) {
                        graph.instruction(insn);
                    }
                }
                default -> { /* attributes and other pseudo-elements are not listed */ }
            }
        }

        if (graph != null && (env._print_code_func == null || env._print_code_func.equals(name))) {
            graph.write(env._print_code_dir);
        }
    }

    /** Gives every label a short name, in the order they appear. */
    private static Map<Label, String> nameLabels(final List<CodeElement> elements) {
        final Map<Label, String> names = new HashMap<>();
        for (final CodeElement element : elements) {
            if (element instanceof LabelTarget target) {
                names.computeIfAbsent(target.label(), unused -> "L" + names.size());
            }
        }
        return names;
    }

    private String render(final Instruction insn, final Map<Label, String> labelNames) {
        final String op = insn.opcode().name().toLowerCase(java.util.Locale.ROOT);
        // aload_0 and friends already name their slot
        final boolean slotInOpcode = Character.isDigit(op.charAt(op.length() - 1));
        return switch (insn) {
            case LoadInstruction i -> slotInOpcode ? op : op + " " + i.slot();
            case StoreInstruction i -> slotInOpcode ? op : op + " " + i.slot();
            case IncrementInstruction i -> op + " " + i.slot() + ", " + i.constant();
            case BranchInstruction i -> op + " " + labelNames.get(i.target());
            case ConstantInstruction i -> op + " " + constant(i.constantValue());
            case FieldInstruction i -> op + " " + i.owner().asInternalName() + '.' + i.name().stringValue()
                    + ' ' + i.type().stringValue();
            case InvokeInstruction i -> op + " " + i.owner().asInternalName() + '.' + i.name().stringValue()
                    + i.type().stringValue();
            case InvokeDynamicInstruction i -> invokedynamic(i);
            case TypeCheckInstruction i -> op + " " + i.type().asInternalName();
            case NewObjectInstruction i -> op + " " + i.className().asInternalName();
            case NewReferenceArrayInstruction i -> op + " " + i.componentType().asInternalName();
            case NewMultiArrayInstruction i -> op + " " + i.arrayType().asInternalName() + ' ' + i.dimensions();
            case TableSwitchInstruction i -> op + " " + switchCases(i.cases(), i.defaultTarget(), labelNames);
            case LookupSwitchInstruction i -> op + " " + switchCases(i.cases(), i.defaultTarget(), labelNames);
            default -> op;
        };
    }

    /**
     * An invokedynamic. Nashorn's own call sites print as their operation with
     * the flags spelled out; anything else prints as its bootstrap.
     */
    private static String invokedynamic(final InvokeDynamicInstruction insn) {
        final StringBuilder out = new StringBuilder("invokedynamic ");
        final DirectMethodHandleDesc bootstrap = insn.bootstrapMethod();
        final List<ConstantDesc> args = insn.bootstrapArgs();
        final boolean isNashorn = BOOTSTRAP_CLASS.equals(bootstrap.owner())
                && !args.isEmpty() && args.get(0) instanceof Integer;

        if (isNashorn) {
            final int flags = (Integer)args.get(0);
            out.append(NashornCallSiteDescriptor.getOperationName(flags));
            final String decodedName = NameCodec.decode(insn.name().stringValue());
            if (!decodedName.isEmpty()) {
                out.append(':').append(decodedName);
            }
            out.append(insn.typeSymbol().descriptorString()).append(' ');
            NashornCallSiteDescriptor.appendFlags(flags, out);
        } else {
            out.append(insn.name().stringValue()).append(insn.typeSymbol().descriptorString())
               .append(" [").append(bootstrap.owner().displayName()).append('.')
               .append(bootstrap.methodName()).append(' ').append(args).append(']');
        }
        return out.toString();
    }

    private static String switchCases(final List<SwitchCase> cases, final Label defaultTarget,
            final Map<Label, String> labelNames) {
        final StringBuilder out = new StringBuilder("{");
        for (final SwitchCase c : cases) {
            out.append(c.caseValue()).append(": ").append(labelNames.get(c.target())).append(", ");
        }
        return out.append("default: ").append(labelNames.get(defaultTarget)).append('}').toString();
    }

    private static String constant(final ConstantDesc value) {
        if (value instanceof String s) {
            return '"' + s.replace("\"", "\\\"") + '"';
        }
        return String.valueOf(value);
    }

    /**
     * The blocks of one method and the jumps between them, rendered as Graphviz
     * for {@code --print-code=dir:<dir>}.
     */
    private static final class ControlFlowGraph {
        private final String name;
        private final Map<Label, String> labelNames;
        private final List<String> blocks = new ArrayList<>();
        private final List<String> edges = new ArrayList<>();
        private String current;

        ControlFlowGraph(final String name, final Map<Label, String> labelNames) {
            this.name = name;
            this.labelNames = labelNames;
        }

        void startBlock(final Label label) {
            current = labelNames.get(label);
            blocks.add(current);
        }

        void instruction(final Instruction insn) {
            if (current != null && insn instanceof BranchInstruction branch) {
                edges.add(current + " -> " + labelNames.get(branch.target()));
            }
        }

        void write(final String dir) {
            final java.io.File directory = new java.io.File(dir);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException(directory.toString());
            }

            java.io.File file;
            int uniqueId = 0;
            do {
                file = new java.io.File(directory, dottyFriendly(name) + (uniqueId == 0 ? "" : "_" + uniqueId) + ".dot");
                uniqueId++;
            } while (file.exists());

            try (java.io.PrintWriter pw = new java.io.PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(this);
            } catch (final java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static String dottyFriendly(final String name) {
            return name.replace(':', '_').replace('$', '_').replace('/', '_').replace('.', '_');
        }

        @Override
        public String toString() {
            final StringBuilder out = new StringBuilder("digraph ").append(dottyFriendly(name)).append(" {\n");
            for (final String block : blocks) {
                out.append("  ").append(block).append(" [shape=box];\n");
            }
            for (final String edge : edges) {
                out.append("  ").append(edge).append(";\n");
            }
            return out.append('}').toString();
        }
    }
}
