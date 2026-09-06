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
import org.monflabs.nashorn.internal.ir.annotations.Immutable;
import org.monflabs.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of an ES2020 optional chain (12.3.9). It wraps the outermost
 * member/call expression that contains at least one {@code ?.} link, marking the
 * boundary at which the chain short-circuits to {@code undefined} when one of its
 * optional links sees a nullish base. The wrapped expression is an ordinary tree
 * of {@link AccessNode}/{@link IndexNode}/{@link CallNode}s whose optional links
 * are flagged (see {@link BaseNode#isOptional()} and {@link CallNode#isOptional()}).
 */
@Immutable
public final class OptionalChainNode extends Expression {
    private static final long serialVersionUID = 1L;

    /** The wrapped chain expression. */
    private final Expression expression;

    /**
     * Constructor.
     * @param expression the outermost expression of the optional chain
     */
    public OptionalChainNode(final Expression expression) {
        super(expression.getToken(), expression.getStart(), expression.getFinish());
        this.expression = expression;
    }

    private OptionalChainNode(final OptionalChainNode chain, final Expression expression) {
        super(chain);
        this.expression = expression;
    }

    /**
     * Get the wrapped chain expression.
     * @return the wrapped expression
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Reset the wrapped chain expression.
     * @param expression the new wrapped expression
     * @return this or a new node
     */
    public OptionalChainNode setExpression(final Expression expression) {
        if (this.expression == expression) {
            return this;
        }
        return new OptionalChainNode(this, expression);
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterOptionalChainNode(this)) {
            return visitor.leaveOptionalChainNode(setExpression((Expression)expression.accept(visitor)));
        }
        return this;
    }

    @Override
    public Type getType() {
        // The chain either yields its member/call result or undefined, so its
        // static type is object.
        return Type.OBJECT;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        expression.toString(sb, printType);
    }
}
