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

package org.openjdk.nashorn.internal.test.framework;

import java.nio.file.Path;
import java.util.Set;

/**
 * Decides which test262 tests are in scope for ECMAScript 2015 conformance.
 *
 * test262 has no ES2015 branch or tag - only the frozen {@code es5-tests} branch
 * and {@code main}, which tracks the current draft spec. The ES2015 suite has to
 * be selected out of {@code main}, and the selection is a <em>deny</em> rule
 * rather than an allow rule:
 *
 * <p><b>A test is in scope unless it needs a feature that postdates ES2015.</b>
 *
 * <p>That is deliberate. An allow rule - take only tests tagged with an ES2015
 * feature - would select about 10,600 tests and quietly drop the ~15,000
 * untagged ones that cover the ES5.1 core as ES2015 amended it. ES2015 contains
 * all of ES5.1, so those tests are part of conformance too. The deny rule keeps
 * them and selects about 25,000.
 *
 * <p>{@code features:} is the only mechanism that reliably marks a test as
 * needing something newer, because the legacy {@code es6id:} marker is only
 * present on tests written during the ES2015 push (many ES2015 tests carry
 * {@code es6id:} and no {@code features:} at all, and vice versa).
 */
public final class Test262Selector {
    /**
     * Every {@code features:} tag that ES2015 introduced. A test tagged only
     * with these - or with none at all - is in scope.
     *
     * Deliberately absent: {@code tail-call-optimization}. Proper tail calls are
     * ES2015, but implementing them on the JVM costs a trampoline in tail
     * position, so they are an explicit, documented exclusion.
     */
    private static final Set<String> ES2015_FEATURES = Set.of(
        // syntax
        "arrow-function", "class", "computed-property-names", "const", "let",
        "default-parameters", "destructuring-binding", "destructuring-assignment",
        "generators", "new.target", "super", "template",
        // symbols
        "Symbol", "Symbol.iterator", "Symbol.species", "Symbol.hasInstance",
        "Symbol.toPrimitive", "Symbol.toStringTag", "Symbol.unscopables",
        "Symbol.match", "Symbol.replace", "Symbol.search", "Symbol.split",
        "Symbol.isConcatSpreadable",
        // library
        "Proxy", "Reflect", "Reflect.construct", "Reflect.set", "Reflect.setPrototypeOf",
        "Promise", "Map", "Set", "WeakMap", "WeakSet",
        "TypedArray", "ArrayBuffer", "DataView",
        // a property of ES2015 strict functions rather than a new feature
        "caller-no-arguments-strict");

    /**
     * Suite directories that are out of scope regardless of tags.
     *
     * {@code intl402} is ECMA-402, a separate standard. {@code staging} is not
     * normative. {@code annexB} is normative-optional and aimed at browser
     * hosts, which Nashorn is not.
     */
    private static final Set<String> EXCLUDED_DIRS = Set.of("intl402", "staging", "annexB");

    private Test262Selector() {
    }

    /**
     * Whether a test file is one we hold ourselves to.
     *
     * @param suiteRoot   the root of the test262 checkout
     * @param testFile    the test
     * @param frontmatter its parsed header, or null if it has none
     * @return true if the test counts towards ES2015 conformance
     */
    public static boolean isInScope(final Path suiteRoot, final Path testFile, final Test262Frontmatter frontmatter) {
        final Path relative = suiteRoot.relativize(testFile);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (EXCLUDED_DIRS.contains(relative.getName(i).toString())) {
                return false;
            }
        }

        // _FIXTURE files are imported by module tests, never run on their own
        if (testFile.getFileName().toString().endsWith("_FIXTURE.js")) {
            return false;
        }

        if (frontmatter == null) {
            return true;
        }

        return ES2015_FEATURES.containsAll(frontmatter.getFeatures());
    }

    /**
     * The features a test needs that ES2015 does not have. Used to explain why a
     * test was skipped.
     *
     * @param frontmatter a parsed header
     * @return the offending tags, empty when the test is in scope
     */
    public static Set<String> postEs2015Features(final Test262Frontmatter frontmatter) {
        if (frontmatter == null) {
            return Set.of();
        }
        return frontmatter.getFeatures().stream()
                .filter(f -> !ES2015_FEATURES.contains(f))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
