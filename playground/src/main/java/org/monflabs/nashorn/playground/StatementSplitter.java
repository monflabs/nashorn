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


package org.monflabs.nashorn.playground;

import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.api.tree.CompilationUnitTree;
import org.monflabs.nashorn.api.tree.Diagnostic;
import org.monflabs.nashorn.api.tree.FunctionDeclarationTree;
import org.monflabs.nashorn.api.tree.Parser;
import org.monflabs.nashorn.api.tree.Tree;

/**
 * Cuts a script into its top-level statements, for the mode that shows what
 * each one evaluates to. Function declarations come first, as the engine
 * would hoist them.
 */
final class StatementSplitter {

    /**
     * A top-level statement.
     * @param line the line it starts on, zero based
     * @param text its source
     * @param declaration whether it is a function declaration
     */
    record Statement(int line, String text, boolean declaration) {}

    private StatementSplitter() {
    }

    /**
     * Splits a script.
     * @param name the script's name, for diagnostics
     * @param source the script
     * @param options the engine options the script asks for, so that scripting mode is honoured
     * @return the statements, declarations first
     * @throws IllegalArgumentException with the first diagnostic if the script does not parse
     */
    static List<Statement> split(final String name, final String source, final List<String> options) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final Parser parser = Parser.create(options.toArray(new String[0]));
        final CompilationUnitTree unit = parser.parse(name, source, diagnostics::add);
        for (final Diagnostic d : diagnostics) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalArgumentException(d.getMessage());
            }
        }
        if (unit == null) {
            throw new IllegalArgumentException("cannot parse " + name);
        }
        // The parser hoists a declaration it moves - the var of a for-in/of head, say - into the
        // program's own list, out of order and inside the statement it came from: sort by
        // position and keep only what no earlier statement already covers.
        final List<Tree> elements = new ArrayList<>(unit.getSourceElements());
        elements.sort((a, b) -> Long.compare(a.getStartPosition(), b.getStartPosition()));
        final List<Tree> topLevel = new ArrayList<>();
        long covered = -1;
        for (final Tree tree : elements) {
            if (tree.getStartPosition() < covered) {
                continue;
            }
            topLevel.add(tree);
            covered = Math.max(covered, tree.getEndPosition());
        }
        final List<Statement> declarations = new ArrayList<>();
        final List<Statement> statements = new ArrayList<>();
        for (int i = 0; i < topLevel.size(); i++) {
            final Tree tree = topLevel.get(i);
            final int start = startOf(source, tree);
            // up to the next statement, so that a trailing semicolon and comments stay with it
            final int end = i + 1 < topLevel.size() ? startOf(source, topLevel.get(i + 1)) : source.length();
            if (start < 0 || end <= start) {
                continue;
            }
            final String text = source.substring(start, end);
            final Statement statement = new Statement(lineOf(source, start), text, tree instanceof FunctionDeclarationTree);
            (statement.declaration() ? declarations : statements).add(statement);
        }
        declarations.addAll(statements);
        return declarations;
    }

    /**
     * Where a statement starts. The position of a string or template token
     * is that of its first character, after the quote, so the quote is
     * taken back.
     */
    private static int startOf(final String source, final Tree tree) {
        int start = (int)tree.getStartPosition();
        while (start > 0 && "'\"`".indexOf(source.charAt(start - 1)) >= 0) {
            start--;
        }
        return start;
    }

    private static int lineOf(final String source, final int position) {
        int line = 0;
        for (int i = 0; i < position && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
