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
 * Decides which test262 tests are in scope for ECMAScript 2017 conformance.
 *
 * test262 has no branch or tag for any edition - only the frozen
 * {@code es5-tests} branch and {@code main}, which tracks the current draft
 * spec. The suite for an edition has to be selected out of {@code main}, and the
 * selection is a <em>deny</em> rule rather than an allow rule:
 *
 * <p><b>A test is in scope unless it needs a feature that postdates ES2017.</b>
 *
 * <p>That is deliberate. An allow rule - take only tests tagged with a feature
 * of the edition - would quietly drop the thousands of untagged tests covering
 * the core as later editions amended it, and each edition contains all of the
 * ones before it, so those tests are part of conformance too. Several ES2017
 * additions carry no tag at all: {@code Object.values}, {@code Object.entries},
 * {@code Object.getOwnPropertyDescriptors}, {@code String.prototype.padStart}
 * and {@code padEnd} are only ever tagged with what they happen to use.
 *
 * <p>{@code features:} is the only mechanism that reliably marks a test as
 * needing something newer, because the legacy {@code es6id:} marker is only
 * present on tests written during the ES2015 push (many ES2015 tests carry
 * {@code es6id:} and no {@code features:} at all, and vice versa).
 */
public final class Test262Selector {
    /**
     * Every {@code features:} tag that ES2015, ES2016 or ES2017 introduced. A
     * test tagged only with these - or with none at all - is in scope.
     *
     * Deliberately absent: {@code tail-call-optimization}. Proper tail calls are
     * normative from ES2015 on, but implementing them on the JVM costs a
     * trampoline in tail position, so they are an explicit, documented
     * exclusion - as they are in every engine but JavaScriptCore.
     */
    private static final Set<String> FEATURES = Set.of(
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
        "caller-no-arguments-strict",
        // ES2016
        "exponentiation", "Array.prototype.includes",
        // ES2017. The library additions of that edition carry no tag of their
        // own and are in scope by the deny rule alone.
        "async-functions", "SharedArrayBuffer", "Atomics");

    /**
     * Suite directories that are out of scope regardless of tags.
     *
     * {@code intl402} is ECMA-402, a separate standard. {@code staging} is not
     * normative. {@code annexB} is normative-optional and aimed at browser
     * hosts, which Nashorn is not.
     *
     * The async-generator directories are ECMAScript 2018 - async iteration,
     * not async functions. They are named here rather than caught by the feature
     * rule because the tests in them predate the {@code features:} convention
     * and declare nothing.
     */
    private static final Set<String> EXCLUDED_DIRS = Set.of("intl402", "staging", "annexB",
            "async-generator", "async-generators");

    private Test262Selector() {
    }

    /**
     * Whether a test file is one we hold ourselves to.
     *
     * @param suiteRoot   the root of the test262 checkout
     * @param testFile    the test
     * @param frontmatter its parsed header, or null if it has none
     * @return true if the test counts towards ES2017 conformance
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

        return FEATURES.containsAll(frontmatter.getFeatures());
    }

    /**
     * The features a test needs that ES2017 does not have. Used to explain why a
     * test was skipped.
     *
     * @param frontmatter a parsed header
     * @return the offending tags, empty when the test is in scope
     */
    public static Set<String> laterFeatures(final Test262Frontmatter frontmatter) {
        if (frontmatter == null) {
            return Set.of();
        }
        return frontmatter.getFeatures().stream()
                .filter(f -> !FEATURES.contains(f))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
