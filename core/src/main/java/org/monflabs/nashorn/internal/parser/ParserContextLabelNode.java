/**
/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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
package org.monflabs.nashorn.internal.parser;

/**
 * ParserContextNode that represents a LabelNode
 */
class ParserContextLabelNode extends ParserContextBaseNode {

    /** Name for label */
    private final String name;

    /** Whether what this labels is an iteration statement, which continue needs. */
    private final boolean labelsIterationStatement;

    /**
     * Constructor
     *
     * @param name The name of the label
     * @param labelsIterationStatement whether the statement it labels is one continue may name
     */
    public ParserContextLabelNode(final String name, final boolean labelsIterationStatement) {
        this.name = name;
        this.labelsIterationStatement = labelsIterationStatement;
    }

    /**
     * Whether the statement this labels is an iteration statement.
     *
     * 13.8.1 lets continue name a label of one of those and of nothing else,
     * which is a question about what the label was written in front of rather
     * than about what turned out to be inside it.
     *
     * @return true if it labels an iteration statement
     */
    public boolean labelsIterationStatement() {
        return labelsIterationStatement;
    }

    /**
     * Returns the name of the label
     * @return name of label
     */
    public String getLabelName() {
        return name;
    }
}
