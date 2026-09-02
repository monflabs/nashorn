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

package org.monflabs.nashorn.api.tree.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.api.tree.BinaryTree;
import org.monflabs.nashorn.api.tree.CompilationUnitTree;
import org.monflabs.nashorn.api.tree.CompoundAssignmentTree;
import org.monflabs.nashorn.api.tree.ExpressionStatementTree;
import org.monflabs.nashorn.api.tree.FunctionDeclarationTree;
import org.monflabs.nashorn.api.tree.Parser;
import org.monflabs.nashorn.api.tree.SimpleTreeVisitorES6;
import org.monflabs.nashorn.api.tree.Tree;
import org.monflabs.nashorn.api.tree.UnaryTree;
import org.testng.annotations.Test;

/**
 * The public parser API represents the syntax the two editions after ES2015
 * added: the exponentiation operator and await. It used to fail an assertion
 * on either.
 */
public class Es2016And2017SyntaxTest {

    private static CompilationUnitTree parse(final String source) {
        final List<String> errors = new ArrayList<>();
        final CompilationUnitTree unit = Parser.create().parse("test.js", source, d -> errors.add(d.getMessage()));
        assertEquals(errors, List.of());
        assertNotNull(unit);
        return unit;
    }

    private static Tree firstExpression(final CompilationUnitTree unit) {
        return ((ExpressionStatementTree)unit.getSourceElements().get(0)).getExpression();
    }

    @Test
    public void exponentiationIsABinaryTree() {
        final Tree expr = firstExpression(parse("2 ** 3 ** 2;"));
        assertEquals(expr.getKind(), Tree.Kind.EXPONENT);
        final BinaryTree outer = (BinaryTree)expr;
        // right associative: 2 ** (3 ** 2)
        assertEquals(outer.getRightOperand().getKind(), Tree.Kind.EXPONENT);
    }

    @Test
    public void exponentiationAssignmentIsACompoundAssignment() {
        final Tree expr = firstExpression(parse("var x = 2; x **= 3;".replace("var x = 2; ", "")));
        assertEquals(expr.getKind(), Tree.Kind.EXPONENT_ASSIGNMENT);
        final CompoundAssignmentTree assignment = (CompoundAssignmentTree)expr;
        assertEquals(assignment.getVariable().getKind(), Tree.Kind.IDENTIFIER);
    }

    @Test
    public void awaitIsAUnaryTree() {
        final CompilationUnitTree unit = parse("async function f(p) { return await p; }");
        final List<Tree> awaits = new ArrayList<>();
        unit.accept(new SimpleTreeVisitorES6<Void, Void>() {
            @Override
            public Void visitUnary(final UnaryTree node, final Void r) {
                if (node.getKind() == Tree.Kind.AWAIT) {
                    awaits.add(node);
                    assertEquals(node.getExpression().getKind(), Tree.Kind.IDENTIFIER);
                }
                return super.visitUnary(node, r);
            }
        }, null);
        assertEquals(awaits.size(), 1);
        assertEquals(unit.getSourceElements().get(0).getKind(), Tree.Kind.FUNCTION);
        assertEquals(((FunctionDeclarationTree)unit.getSourceElements().get(0)).getName().getName(), "f");
    }
}
