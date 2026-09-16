/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * JDK-8158467: AccessControlException is thrown on public Java class access if "script app loader" is set to null
 *
 * @test
 * @run
 */

var Factory = Java.type("org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory");
var fac = new Factory();

// This script has to be given RuntimePermission("nashorn.setConfig")
// Java access is a library since 2026.1.0, and the overload that takes libraries
// substitutes the application loader for a null one - so the loader that cannot
// see Nashorn's own classes, which is what this test is about, is made explicitly.
var TestLibs = java.util.List.of(
    new (Java.type('org.monflabs.nashorn.libs.NashornLibrary'))(),
    new (Java.type('org.monflabs.nashorn.libs.JavaLibrary'))());
var bootOnly = new (Java.type('java.net.URLClassLoader'))(Java.to([], "java.net.URL[]"), null);
var e = fac["getScriptEngine(java.lang.String[], java.lang.ClassLoader, org.monflabs.nashorn.api.scripting.ClassFilter, java.util.List)"]([], bootOnly, null, TestLibs);

print(e.eval("java.lang.System"));
print(e.eval("({ foo: 42})").foo);
print((e.eval("function(x) x*x"))(31));

e.put("output", print);
var runnable = e.eval(`    new java.lang.Runnable() {
        run: function() {
            output("hello Runnable");
        }
    }`);

runnable.run();

var obj = e.eval(`new (Java.extend(Java.type("java.lang.Object"))) {
    hashCode: function() 33,
    toString: function() "I'm object"
}`);

print(obj.hashCode());
print(obj.toString());

// should throw ClassNotFoundException!
try {
    e.eval("Java.type('org.monflabs.nashorn.internal.runtime.Context')");
} catch (ex) {
    print(ex);
}

// should throw ClassNotFoundException as null is script
// "app loader" [and not platform loader which loads nashorn]
e.eval(`try {
    Java.type('org.monflabs.nashorn.api.scripting.JSObject');
} catch (ex) {
    output(ex);
}`);
