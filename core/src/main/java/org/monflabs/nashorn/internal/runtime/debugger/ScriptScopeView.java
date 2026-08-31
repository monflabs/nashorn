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

import java.util.Set;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * The "script" scope of a program frame: the global object seen through a
 * filter that leaves out the built-ins, so that what a script declared at its
 * top level - its vars, its functions - is what a debugger shows first, the
 * way V8's script scope does. Reads and writes go straight to the global.
 *
 * @param global the global object
 * @param builtinKeys the keys the global had before any script ran
 */
public record ScriptScopeView(Global global, Set<String> builtinKeys) {

    /**
     * Whether a global property is one a script made rather than a built-in.
     * @param key the property key
     * @return true if it belongs in the script scope
     */
    public boolean isScriptProperty(final Object key) {
        return key instanceof String s && !builtinKeys.contains(s);
    }
}
