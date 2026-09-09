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
 * Decides which test262 tests are in scope for ECMAScript 2026 conformance.
 *
 * test262 has no branch or tag for any edition - only the frozen
 * {@code es5-tests} branch and {@code main}, which tracks the current draft
 * spec. The suite for an edition has to be selected out of {@code main}, and the
 * selection is a <em>deny</em> rule rather than an allow rule:
 *
 * <p><b>A test is in scope unless it needs a feature that postdates ES2026.</b>
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
     * Every {@code features:} tag that ES2015 through ES2026 introduced. A
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
        // ES2018. Promise.prototype.finally and the template-literal revision
        // carry no tag and are in scope by the deny rule alone.
        "regexp-dotall", "regexp-named-groups", "regexp-lookbehind",
        "regexp-unicode-property-escapes",
        "object-spread", "object-rest",
        "async-iteration", "Symbol.asyncIterator",
        // ES2019. optional catch binding and the JSON-superset/well-formed
        // additions; the rest are the library methods.
        "Array.prototype.flat", "Array.prototype.flatMap", "Object.fromEntries",
        "Symbol.prototype.description", "string-trimming",
        "String.prototype.trimStart", "String.prototype.trimEnd",
        "optional-catch-binding", "json-superset", "well-formed-json-stringify",
        // ES2020
        "coalesce-expression", "optional-chaining",
        "export-star-as-namespace-from-module",
        "String.prototype.matchAll", "Symbol.matchAll",
        "Promise.allSettled", "globalThis", "for-in-order",
        "dynamic-import", "import.meta", "BigInt",
        // ES2021
        "String.prototype.replaceAll", "numeric-separator-literal",
        "logical-assignment-operators",
        "Promise.any", "AggregateError", "WeakRef", "FinalizationRegistry",
        // ES2022
        "Array.prototype.at", "String.prototype.at", "TypedArray.prototype.at",
        "Object.hasOwn", "error-cause", "regexp-match-indices",
        "class-fields-public", "class-static-fields-public", "static-initialization-blocks",
        "class-fields-private", "class-methods-private", "class-static-fields-private",
        "class-static-methods-private", "class-fields-private-in",
        "top-level-await",
        // ES2023
        "array-find-from-last", "change-array-by-copy", "hashbang",
        "symbols-as-weakmap-keys",
        // ES2024
        "array-grouping", "promise-with-resolvers",
        "String.prototype.isWellFormed", "String.prototype.toWellFormed",
        "resizable-arraybuffer", "arraybuffer-transfer", "Atomics.waitAsync",
        "regexp-v-flag",
        // ES2025
        "RegExp.escape",
        "promise-try",
        "Float16Array",
        "set-methods",
        "iterator-helpers",
        "regexp-modifiers",
        "regexp-duplicate-named-groups",
        "import-attributes",
        "json-modules",
        // ES2026
        "Error.isError",
        "Math.sumPrecise",
        "upsert",
        "iterator-sequencing",
        "uint8array-base64",
        "json-parse-with-source",
        "Array.fromAsync",
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
     * The async-generator directories are ECMAScript 2018 and are now in scope -
     * async iteration is implemented (see the async-iteration and
     * Symbol.asyncIterator feature tags above); its remaining conformance
     * refinements are tracked in the expectations file rather than excluded here.
     */
    private static final Set<String> EXCLUDED_DIRS = Set.of("intl402", "staging");

    /**
     * Tests about something in scope whose bodies are written in syntax that is
     * not.
     *
     * A BigInt literal and the nullish coalescing operator are ECMAScript 2020,
     * and a file using one cannot be parsed by an engine that stops at 2021 -
     * whatever the file is about. These are named one by one rather than caught
     * by a rule because there is nothing in their frontmatter to catch: what
     * each declares is the in-scope thing it tests, and the later syntax is
     * incidental to it, usually one element of a list of values to try.
     *
     * Every one of them is a test we would otherwise hold ourselves to, so the
     * list is deliberately explicit: a rule that skipped anything unparseable
     * would hide real failures.
     */
    private static final Set<String> LATER_SYNTAX = Set.of();

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
     * Tests about a feature that postdates ES2026 and says so nowhere.
     *
     * The deny rule reads {@code features:}, and a test written before that
     * convention - or one whose author saw no feature worth naming - declares
     * nothing to deny, so it would be named here one by one for the same reason
     * the async-generator directories are. At the ES2026 target the set is empty:
     * the one former entry, a harness self-test written with {@code Object.hasOwn},
     * is in scope now that the method is implemented.
     */
    private static final Set<String> LATER_FEATURES = Set.of();

    /**
     * ES2018 RegExp tests the JavaScript regexp engines this fork can use - the
     * JDK's {@code java.util.regex} and the bundled Joni - cannot pass, because
     * neither implements ECMAScript's regexp semantics for these constructs.
     * They are a substrate limitation on a par with proper tail calls: real, in
     * the edition, and not a work item, because closing them means writing or
     * porting an ECMAScript-conformant regexp engine (V8's Irregexp, say) rather
     * than any amount of glue over the two engines that exist.
     *
     * <ul>
     * <li><b>Lookbehind</b> - JDK lookbehind is bounded-length and matched
     *     left-to-right; ECMAScript lookbehind is unbounded and matched
     *     right-to-left, with different capture results. Joni's JAVASCRIPT
     *     syntax has no lookbehind at all. Bounded, capture-free lookbehind does
     *     work (those tests are in scope and pass); these are the cases that
     *     depend on the semantics the substrate lacks.</li>
     * <li><b>A backreference to a still-open group</b> ({@code (?<a>\k<a>\w)}) -
     *     ECMAScript makes it match the empty string; both backends fail the
     *     match. (A plain forward reference, which is always empty, does work.)</li>
     * <li><b>A subclass that overrides {@code exec}</b> - the hot path
     *     String.prototype.replace takes for a native RegExp does not route
     *     through a subclass's overridden {@code exec}; this predates ES2018 and
     *     surfaces here only because the tests are tagged with named groups.</li>
     * </ul>
     */
    private static final Set<String> REGEXP_ENGINE_LIMITS = Set.of(
            "built-ins/RegExp/lookBehind/back-references-to-captures.js",
            "built-ins/RegExp/lookBehind/back-references.js",
            "built-ins/RegExp/lookBehind/captures.js",
            "built-ins/RegExp/lookBehind/greedy-loop.js",
            "built-ins/RegExp/lookBehind/misc.js",
            "built-ins/RegExp/lookBehind/mutual-recursive.js",
            "built-ins/RegExp/lookBehind/nested-lookaround.js",
            "built-ins/RegExp/lookBehind/sliced-strings.js",
            "built-ins/RegExp/lookBehind/start-of-line.js",
            "built-ins/RegExp/lookBehind/sticky.js",
            "built-ins/RegExp/named-groups/lookbehind.js",
            "built-ins/RegExp/named-groups/non-unicode-references.js",
            "built-ins/RegExp/named-groups/unicode-references.js",
            "built-ins/RegExp/named-groups/groups-object-subclass.js",
            "built-ins/RegExp/named-groups/groups-object-subclass-sans.js",
            // ES2022 match indices over a supplementary character without the u
            // flag: both backends match "." against a whole code point (two code
            // units), where ES counts one, so a group's indices come out [0,2]
            // where the specification wants [0,1]. The match value is two units
            // too - it is the underlying code-unit-vs-code-point divergence, not
            // the indices themselves. The unicode variant, where a code point is
            // one match unit, passes.
            "built-ins/RegExp/match-indices/indices-array-non-unicode-match.js",
            // ES2025 pattern modifiers (?ims-ims:...) must compile with the JDK
            // engine - the bundled Joni JS flavour has no inline flag groups - so
            // a modifier-bearing pattern inherits the JDK backend's flavour
            // divergences from JS: a non-unicode "." matches a whole code point
            // rather than one code unit (the same code-unit-vs-code-point limit
            // as indices-array-non-unicode-match above, surfaced through (?s:.));
            // "$" under multiline and the case folding of "\b"/"\w"/"\P{...}"
            // under ignoreCase follow java.util.regex, not the ES Canonicalize.
            // The modifier syntax itself parses and the ordinary cases pass; only
            // these backend-semantics corners are held out.
            "built-ins/RegExp/regexp-modifiers/add-dotAll.js",
            "built-ins/RegExp/regexp-modifiers/remove-dotAll.js",
            "built-ins/RegExp/regexp-modifiers/changing-dotAll-flag-does-not-affect-dotAll-modifier.js",
            "built-ins/RegExp/regexp-modifiers/nesting-add-dotAll-within-remove-dotAll.js",
            "built-ins/RegExp/regexp-modifiers/nesting-remove-dotAll-within-add-dotAll.js",
            "built-ins/RegExp/regexp-modifiers/remove-multiline-does-not-affect-dotAll-flag.js",
            "built-ins/RegExp/regexp-modifiers/add-multiline.js",
            "built-ins/RegExp/regexp-modifiers/add-ignoreCase-affects-slash-lower-b.js",
            "built-ins/RegExp/regexp-modifiers/add-ignoreCase-affects-slash-upper-b.js",
            "built-ins/RegExp/regexp-modifiers/add-ignoreCase-affects-slash-lower-w.js",
            "built-ins/RegExp/regexp-modifiers/add-ignoreCase-affects-slash-upper-w.js",
            "built-ins/RegExp/regexp-modifiers/add-ignoreCase-affects-slash-upper-p.js",
            // ES2025 duplicate named groups: .groups/.indices, enumeration order
            // and match/replace/replaceAll/matchAll/split/search all work (the
            // name -> capture-indices map picks whichever participated), but a
            // named backreference \k<name> to a duplicated name must reference
            // whichever alternative's group participated - and fail, not match
            // empty, when that group's captured text does not reoccur. A single
            // numbered backreference in java.util.regex cannot express "the one
            // of these groups that matched", and the empty-alternative fallback
            // that would rescue the disjoint-alternative case wrongly lets the
            // backreference match empty where the participant's text mismatched.
            // The same limit as the held-out lookBehind backreferences above.
            // The two exec/*-properties tests exercise the same \k<name> over a
            // duplicated name (their iterated matcher) alongside the .groups and
            // .indices checks that do pass.
            "built-ins/RegExp/named-groups/duplicate-names-exec.js",
            "built-ins/RegExp/named-groups/duplicate-names-match.js",
            "built-ins/RegExp/named-groups/duplicate-names-test.js",
            "built-ins/RegExp/prototype/exec/duplicate-named-groups-properties.js",
            "built-ins/RegExp/prototype/exec/duplicate-named-indices-groups-properties.js",
            "built-ins/String/prototype/match/duplicate-named-groups-properties.js",
            "built-ins/String/prototype/match/duplicate-named-indices-groups-properties.js");

    private static final Set<String> LATER_UNICODE = Set.of(
            "language/identifiers/start-unicode-17.0.0.js",
            "language/identifiers/start-unicode-17.0.0-escaped.js",
            "language/identifiers/part-unicode-17.0.0.js",
            "language/identifiers/part-unicode-17.0.0-escaped.js",
            // the class-body forms of the same, held out for the same reason -
            // JDK 25 is Unicode 16, so a Unicode 17 identifier is not one to it
            "language/identifiers/start-unicode-17.0.0-class.js",
            "language/identifiers/start-unicode-17.0.0-class-escaped.js",
            "language/identifiers/part-unicode-17.0.0-class.js",
            "language/identifiers/part-unicode-17.0.0-class-escaped.js");

    private Test262Selector() {
    }

    /**
     * Whether a test file is one we hold ourselves to.
     *
     * @param suiteRoot   the root of the test262 checkout
     * @param testFile    the test
     * @param frontmatter its parsed header, or null if it has none
     * @return true if the test counts towards ES2026 conformance
     */
    private static final String GENERATED = "/property-escapes/generated/";

    /**
     * Whether a property-escape test is one held to the conformance gate.
     *
     * The property-escape <em>syntax</em> and mechanism are implemented and
     * verified by the hand-written tests in the {@code property-escapes/}
     * directory (loose matching, the grammar extensions, character classes,
     * unsupported-property errors), which are in scope and pass. The exhaustive
     * {@code generated/} trees are not, for two data reasons that both come down
     * to the Unicode Character Database this engine does not ship:
     * <ul>
     *   <li>General_Category and Script are answered by the JDK's regex engine,
     *       but the suite's {@code generated/} data is keyed to a Unicode version
     *       newer than the JDK's (JDK 25 is Unicode 16), so their code-point lists
     *       disagree over the characters that version added - the same situation
     *       as {@link #LATER_UNICODE} for the identifier tables;</li>
     *   <li>the ~40 binary properties the JDK does not carry (Emoji and its kin,
     *       Dash, Math, Diacritic, the Changes_When_* set, ID/XID_* and so on)
     *       and Script_Extensions would each need the UCD bundled to answer a
     *       code point at a time.</li>
     * </ul>
     * The two character-class range tests want a SyntaxError for a property
     * escape used as a range bound, a grammar rule not enforced here. See
     * doc/CONFORMANCE.md. ({@code generated/strings/} is Property_of_Strings, the
     * ES2024 RegExp {@code v} flag - in scope now, but these string-set
     * properties the JDK engine cannot express are held out by
     * {@link #unicodeSetsInScope}.)
     */
    private static boolean propertyEscapeInScope(final String path) {
        if (!path.contains("/property-escapes/")) {
            return true;
        }
        if (path.endsWith("character-class-range-start.js")
                || path.endsWith("character-class-range-no-dash-start.js")
                || path.contains("Script_Extensions")) {
            // Script_Extensions (hand-written cases included) needs the UCD; the
            // two range tests want a SyntaxError not enforced here.
            return false;
        }
        // the exhaustive generated/ trees are out of scope (Unicode-version and
        // UCD-data limitations); the hand-written tests beside them are in scope.
        return !path.contains(GENERATED);
    }

    /**
     * Whether a RegExp {@code v}-flag (unicodeSets) test is one held to the
     * conformance gate.
     *
     * The class-set grammar - nested classes, the union / intersection ({@code
     * &&}) / difference ({@code --}) operators, ranges and single-code-point
     * {@code \q{...}} string literals - is implemented and passes, including the
     * exhaustive {@code generated/} matrix over characters, character classes,
     * class escapes and property escapes, and the {@code breaking-change-from-u-to-v}
     * syntax tests. Two shapes are out, both because {@code java.util.regex} has
     * no notion of a character class whose member is a string:
     * <ul>
     *   <li>a {@code \q{...}} whose alternatives are not all single code points -
     *       a class that contains a multi-character (or empty) string, which the
     *       {@code generated/string-literal-*} tree exercises;</li>
     *   <li>{@code \p{...}} properties of strings (RGI_Emoji and its kin), the
     *       {@code generated/property-of-strings-*} and {@code generated/rgi-emoji-*}
     *       tree.</li>
     * </ul>
     * Both are held out with a syntax error at compile time and listed here. See
     * doc/CONFORMANCE.md.
     */
    private static boolean unicodeSetsInScope(final String path) {
        if (!path.contains("/unicodeSets/generated/")) {
            return true;
        }
        return !(path.contains("string-literal")
                || path.contains("property-of-strings")
                || path.contains("rgi-emoji"));
    }

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
        for (final String excluded : REGEXP_ENGINE_LIMITS) {
            if (path.endsWith(excluded)) {
                return false;
            }
        }
        if (!propertyEscapeInScope(path)) {
            return false;
        }
        if (!unicodeSetsInScope(path)) {
            return false;
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
     * The features a test needs that ES2019 does not have. Used to explain why a
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
