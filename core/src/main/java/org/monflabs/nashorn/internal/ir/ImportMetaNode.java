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
 * IR representation of the ES2020 {@code import.meta} meta-property (13.3.12),
 * legal only in module code. It carries no children; codegen answers it from the
 * enclosing module's {@code import.meta} object.
 */
@Immutable
public final class ImportMetaNode extends Expression {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     * @param token  token
     * @param finish finish
     */
    public ImportMetaNode(final long token, final int finish) {
        super(token, Token.descPosition(token), finish);
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterImportMetaNode(this)) {
            return visitor.leaveImportMetaNode(this);
        }
        return this;
    }

    @Override
    public Type getType() {
        return Type.OBJECT;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("import.meta");
    }
}
