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
 * IR representation of an ES2020 dynamic import call {@code import(specifier)},
 * with the ES2025 second options argument {@code import(specifier, options)}
 * (13.3.10). It evaluates its specifier (and options) argument and answers a
 * promise of the imported module's namespace object.
 */
@Immutable
public final class ImportCallNode extends Expression {
    private static final long serialVersionUID = 1L;

    /** The module-specifier expression. */
    private final Expression argument;

    /** ES2025 the optional second argument (the options bag), or null. */
    private final Expression options;

    /**
     * Constructor.
     * @param token    token
     * @param finish   finish
     * @param argument the module-specifier expression
     */
    public ImportCallNode(final long token, final int finish, final Expression argument) {
        this(token, finish, argument, null);
    }

    /**
     * Constructor with the ES2025 options argument.
     * @param token    token
     * @param finish   finish
     * @param argument the module-specifier expression
     * @param options  the options-bag expression, or null
     */
    public ImportCallNode(final long token, final int finish, final Expression argument, final Expression options) {
        super(token, Token.descPosition(token), finish);
        this.argument = argument;
        this.options = options;
    }

    private ImportCallNode(final ImportCallNode node, final Expression argument, final Expression options) {
        super(node);
        this.argument = argument;
        this.options = options;
    }

    /**
     * Get the module-specifier expression.
     * @return the specifier expression
     */
    public Expression getArgument() {
        return argument;
    }

    /**
     * Get the ES2025 options-bag expression.
     * @return the options expression, or null if none was written
     */
    public Expression getOptions() {
        return options;
    }

    /**
     * Reset the specifier and options expressions.
     * @param argument the new specifier expression
     * @param options  the new options expression, or null
     * @return this or a new node
     */
    public ImportCallNode setArguments(final Expression argument, final Expression options) {
        if (this.argument == argument && this.options == options) {
            return this;
        }
        return new ImportCallNode(this, argument, options);
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterImportCallNode(this)) {
            return visitor.leaveImportCallNode(setArguments(
                    (Expression) argument.accept(visitor),
                    options == null ? null : (Expression) options.accept(visitor)));
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
        if (options != null) {
            sb.append(", ");
            options.toString(sb, printType);
        }
        sb.append(')');
    }
}
