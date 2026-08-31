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

package org.monflabs.nashorn.internal.runtime.debugger;

import org.monflabs.nashorn.internal.runtime.ScriptFunction;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.Source;

/**
 * One script invocation on the shadow stack of a thread: what the debugger
 * knows about a JVM frame it cannot look into.
 */
final class Frame {
    final Source source;
    final String functionName;
    final int functionLine;
    final int functionColumn;
    final ScriptFunction callee;
    Object self;
    ScriptObject scope;
    /** The statement being executed, or null before the first one. */
    ScriptInfo.Entry site;

    Frame(final Source source, final String functionName, final int functionLine, final int functionColumn,
            final ScriptFunction callee, final Object self, final ScriptObject scope) {
        this.source = source;
        this.functionName = functionName;
        this.functionLine = functionLine;
        this.functionColumn = functionColumn;
        this.callee = callee;
        this.self = self;
        this.scope = scope;
    }
}
