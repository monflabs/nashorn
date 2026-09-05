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

package org.monflabs.nashorn.internal.test.framework;

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
     * Deliberately and <b>permanently</b> absent: {@code tail-call-optimization}.
     * Proper tail calls are normative from ES2015 on and have not been removed
     * from the specification, so this is a divergence rather than a technicality
     * - and it is a settled one, not a task waiting for a volunteer. A tail
     * position cannot be recognised at runtime, so honouring the rule costs a
     * trampoline in every tail-shaped function, which is most of them; the
     * performance gate exists to refuse exactly that kind of tax. The ecosystem
     * did not follow the specification either - JavaScriptCore is the only
     * engine that ships them, V8 withdrew its implementation, and the syntactic
     * replacement TC39 spent years on never advanced. See doc/CONFORMANCE.md.
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
        "async-functions", "SharedArrayBuffer", "Atomics",
        // Annex B, which this engine implements behind --annexB. These three
        // tag tests that live in the main tree rather than under annexB/ -
        // B.2.2's accessors on Object.prototype - so without them the directory
        // being in scope would still leave the feature untested.
        "__proto__", "__getter__", "__setter__");

    /**
     * Suite directories that are out of scope regardless of tags.
     *
     * {@code intl402} is ECMA-402, a separate standard.
     *
     * {@code annexB} is <b>not</b> among them any more. Annex B is
     * normative-optional and written for browser hosts, and this engine
     * implements it behind {@code --annexB}, which is on by default - so the
     * directory is in scope and its tests are held to the same standard as the
     * rest. The one part that cannot be implemented is {@code [[IsHTMLDDA]]},
     * which only a web host can produce; those tests are tagged
     * {@code IsHTMLDDA}, a feature this edition does not name, so the deny rule
     * below drops them without anything being said here.
     *
     * {@code staging} is <b>permanently</b> out of scope: it holds tests for
     * Stage 3 proposals and normative pull requests, written to lower standards
     * than the main suite and not counting towards a proposal's Stage 4
     * coverage. This engine implements the latest approved edition, so a
     * proposal becomes its business when the proposal becomes a standard - at
     * which point its tests leave staging for the main suite of their own
     * accord. Widening this set would mean chasing semantics nobody has
     * ratified.
     *
     * The async-generator directories are ECMAScript 2018 - async iteration,
     * not async functions. They are named here rather than caught by the feature
     * rule because the tests in them predate the {@code features:} convention
     * and declare nothing.
     */
    private static final Set<String> EXCLUDED_DIRS = Set.of("intl402", "staging",
            "async-generator", "async-generators");

    /**
     * Tests about something in scope whose bodies are written in syntax that is
     * not.
     *
     * A BigInt literal and the nullish coalescing operator are ECMAScript 2020,
     * and a file using one cannot be parsed by an engine that stops at 2017 -
     * whatever the file is about. These are named one by one rather than caught
     * by a rule because there is nothing in their frontmatter to catch: what
     * each declares is the in-scope thing it tests, and the later syntax is
     * incidental to it, usually one element of a list of values to try.
     *
     * Every one of them is a test we would otherwise hold ourselves to, so the
     * list is deliberately explicit: a rule that skipped anything unparseable
     * would hide real failures.
     */
    private static final Set<String> LATER_SYNTAX = Set.of(
            // BigInt literals
            "built-ins/Iterator/prototype/Symbol.iterator/return-val.js",
            "built-ins/Promise/all/resolve-throws-iterator-return-is-not-callable.js",
            "built-ins/Promise/allSettled/resolve-throws-iterator-return-is-not-callable.js",
            "built-ins/Promise/any/resolve-throws-iterator-return-is-not-callable.js",
            "built-ins/Promise/race/resolve-throws-iterator-return-is-not-callable.js",
            "language/expressions/class/cpn-class-expr-accessors-computed-property-name-from-integer-separators.js",
            "language/expressions/class/cpn-class-expr-computed-property-name-from-integer-separators.js",
            "language/statements/class/cpn-class-decl-accessors-computed-property-name-from-integer-separators.js",
            "language/statements/class/cpn-class-decl-computed-property-name-from-integer-separators.js",
            "language/expressions/object/cpn-obj-lit-computed-property-name-from-integer-separators.js",
            "built-ins/RegExp/prototype/flags/this-val-non-obj.js",
            "built-ins/RegExp/prototype/global/this-val-non-obj.js",
            "built-ins/RegExp/prototype/ignoreCase/this-val-non-obj.js",
            "built-ins/RegExp/prototype/multiline/this-val-non-obj.js",
            "built-ins/RegExp/prototype/source/this-val-non-obj.js",
            "built-ins/RegExp/prototype/sticky/this-val-non-obj.js",
            "built-ins/RegExp/prototype/unicode/this-val-non-obj.js",
            "language/expressions/assignment/destructuring/default-expr-throws-iterator-return-is-not-callable.js",
            "language/expressions/assignment/destructuring/target-assign-throws-iterator-return-is-not-callable.js",
            "built-ins/String/prototype/match/cstm-matcher-on-bigint-primitive.js",
            "built-ins/String/prototype/replace/cstm-replace-on-bigint-primitive.js",
            "built-ins/String/prototype/search/cstm-search-on-bigint-primitive.js",
            "built-ins/String/prototype/split/cstm-split-on-bigint-primitive.js",
            // nullish coalescing
            "language/expressions/class/cpn-class-expr-accessors-computed-property-name-from-expression-coalesce.js",
            "language/expressions/class/cpn-class-expr-computed-property-name-from-expression-coalesce.js",
            "language/expressions/object/cpn-obj-lit-computed-property-name-from-expression-coalesce.js",
            "language/statements/class/cpn-class-decl-accessors-computed-property-name-from-expression-coalesce.js",
            "language/statements/class/cpn-class-decl-computed-property-name-from-expression-coalesce.js",
            // export * as ns from "mod", which is ECMAScript 2020
            "language/module-code/ambiguous-export-bindings/namespace-unambiguous-if-export-star-as-from.js",
            "language/module-code/ambiguous-export-bindings/namespace-unambiguous-if-export-star-as-from-and-import-star-as-and-export.js");

    /**
     * Tests keyed to a Unicode version newer than the one the JDK carries.
     *
     * What counts as an identifier character comes from java.lang.Character,
     * whose tables are the JDK's own: JDK 25 has Unicode 16.0, and a character
     * assigned in 17.0 is unassigned as far as it is concerned. Nothing in the
     * engine can answer for one, so these move when the JDK does rather than
     * when Nashorn does.
     */
    /**
     * Tests about a feature that postdates ES2017 and says so nowhere.
     *
     * The deny rule reads {@code features:}, and a test written before that
     * convention - or one whose author saw no feature worth naming - declares
     * nothing to deny. These are named one by one for the same reason the
     * async-generator directories are.
     */
    private static final Set<String> LATER_FEATURES = Set.of(
            // ES2019 Array.prototype.flat and flatMap
            "built-ins/Array/prototype/flat/call-with-boolean.js",
            "built-ins/Array/prototype/flat/not-a-constructor.js",
            "built-ins/Array/prototype/flat/target-array-with-non-writable-property.js",
            "built-ins/Array/prototype/flatMap/call-with-boolean.js",
            "built-ins/Array/prototype/flatMap/target-array-with-non-writable-property.js",
            // the unscopables list names those two and the ones after them
            "built-ins/Array/prototype/Symbol.unscopables/value.js",
            // ES2020 BigInt, and the typed arrays and views that carry it
            "built-ins/DataView/prototype/getBigUint64/not-a-constructor.js",
            "built-ins/DataView/prototype/setBigUint64/not-a-constructor.js",
            "built-ins/Object/seal/seal-bigint64array.js",
            "built-ins/Object/seal/seal-biguint64array.js",
            "built-ins/TypedArrayConstructors/BigUint64Array/is-a-constructor.js",
            "built-ins/BigInt/prototype/toString/radix-tointegerorinfinity-throws-symbol.js",
            // ES2021 String.prototype.replaceAll
            "built-ins/String/prototype/replaceAll/cstm-replaceall-on-bigint-primitive.js",
            "built-ins/String/prototype/replaceAll/not-a-constructor.js",
            // ES2022 Object.hasOwn, which the harness test is written with
            "harness/asyncHelpers-asyncTest-without-async-flag.js");

    private static final Set<String> LATER_UNICODE = Set.of(
            "language/identifiers/start-unicode-17.0.0.js",
            "language/identifiers/start-unicode-17.0.0-escaped.js",
            "language/identifiers/part-unicode-17.0.0.js",
            "language/identifiers/part-unicode-17.0.0-escaped.js");

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

        // the relative path carries the suite's own "test/" in front of it
        final String path = relative.toString().replace(java.io.File.separatorChar, '/');
        for (final String excluded : LATER_SYNTAX) {
            if (path.endsWith(excluded)) {
                return false;
            }
        }
        for (final String excluded : LATER_FEATURES) {
            if (path.endsWith(excluded)) {
                return false;
            }
        }
        for (final String excluded : LATER_UNICODE) {
            if (path.endsWith(excluded)) {
                return false;
            }
        }

        if (frontmatter == null) {
            return true;
        }

        // A test flagged CanBlockIsFalse is for a host whose main agent cannot
        // be suspended, and says so instead of asking: this one blocks, so what
        // the test pins down is not what this engine does.
        if (frontmatter.hasFlag("CanBlockIsFalse")) {
            return false;
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
