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

package org.monflabs.nashorn.internal.ir;

import org.monflabs.nashorn.internal.codegen.types.Type;
import org.monflabs.nashorn.internal.parser.Token;
import org.monflabs.nashorn.internal.ir.annotations.Immutable;
import org.monflabs.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of an ES2020 dynamic import call {@code import(specifier)}
 * (13.3.10). It evaluates its specifier argument and answers a promise of the
 * imported module's namespace object.
 */
@Immutable
public final class ImportCallNode extends Expression {
    private static final long serialVersionUID = 1L;

    /** The module-specifier expression. */
    private final Expression argument;

    /**
     * Constructor.
     * @param token    token
     * @param finish   finish
     * @param argument the module-specifier expression
     */
    public ImportCallNode(final long token, final int finish, final Expression argument) {
        super(token, Token.descPosition(token), finish);
        this.argument = argument;
    }

    private ImportCallNode(final ImportCallNode node, final Expression argument) {
        super(node);
        this.argument = argument;
    }

    /**
     * Get the module-specifier expression.
     * @return the specifier expression
     */
    public Expression getArgument() {
        return argument;
    }

    /**
     * Reset the module-specifier expression.
     * @param argument the new specifier expression
     * @return this or a new node
     */
    public ImportCallNode setArgument(final Expression argument) {
        if (this.argument == argument) {
            return this;
        }
        return new ImportCallNode(this, argument);
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterImportCallNode(this)) {
            return visitor.leaveImportCallNode(setArgument((Expression)argument.accept(visitor)));
        }
        return this;
    }

    @Override
    public Type getType() {
        return Type.OBJECT;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("import(");
        argument.toString(sb, printType);
        sb.append(')');
    }
}
