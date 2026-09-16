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
package org.monflabs.nashorn.libs;

import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.internal.objects.Global;

/**
 * A script's reach into the JVM by name, as a library.
 *
 * <pre>
 * ScriptEngine engine = new NashornScriptEngineBuilder()
 *         .library(new JavaLibrary())
 *         .build();
 * engine.eval("var list = new (Java.type('java.util.ArrayList'))()");
 * </pre>
 *
 * What it installs, on every realm of that engine:
 *
 * <ul>
 *   <li>{@code Java} - {@code type}, {@code extend}, {@code super}, {@code from},
 *       {@code to}, {@code isJavaObject} and the rest.</li>
 *   <li>{@code JavaImporter} - a scope object of Java packages, for {@code with}.</li>
 *   <li>{@code Packages} and the package roots {@code java}, {@code javax},
 *       {@code javafx}, {@code com}, {@code org}, {@code edu} - so
 *       {@code java.util.ArrayList} resolves as an expression.</li>
 * </ul>
 *
 * <b>This replaces {@code --no-java}.</b> That option deleted the same properties
 * again after nasgen had written them into every global's map, which is the
 * wrong way round: an engine now has no reach into Java unless an embedder said
 * so, and saying nothing is the safe answer rather than the dangerous one. An
 * embedder that wants Java access but not all of it pairs this with a
 * {@code classFilter}, which decides class by class and is unaffected by any of
 * this.
 *
 * Leaving the library out is not a security boundary on its own. It removes the
 * ways a script can <em>name</em> a Java class; a Java object the embedder puts
 * into the bindings is still a Java object, with its methods reachable. The
 * class filter is what limits that.
 */
public final class JavaLibrary implements ScriptLibrary {

    @Override
    public String name() {
        return "java";
    }

    @Override
    public void initialize(final JSObject global) {
        Global.instance().installJavaAccess();
    }
}
