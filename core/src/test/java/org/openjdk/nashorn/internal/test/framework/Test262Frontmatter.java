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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * The YAML header a test262 test carries, as described by the suite's
 * INTERPRETING.md.
 *
 * <pre>
 * /*---
 * description: what the test asserts
 * esid: sec-array.prototype.map
 * negative:
 *   phase: parse
 *   type: SyntaxError
 * includes: [compareArray.js]
 * flags: [onlyStrict]
 * features: [Symbol.iterator]
 * ---&#42;/
 * </pre>
 *
 * Everything the runner needs to decide <em>whether</em> to run a test, <em>how
 * many times</em>, and <em>what counts as passing</em> lives in this header.
 */
public final class Test262Frontmatter {
    /** Marks a test that must not be evaluated at all - only parsed. */
    private static final String FLAG_RAW = "raw";
    private static final String FLAG_MODULE = "module";
    private static final String FLAG_ASYNC = "async";
    private static final String FLAG_ONLY_STRICT = "onlyStrict";
    private static final String FLAG_NO_STRICT = "noStrict";

    private final String description;
    private final Set<String> flags;
    private final Set<String> features;
    private final List<String> includes;
    private final String negativePhase;
    private final String negativeType;
    private final boolean hasEs6id;

    private Test262Frontmatter(final Map<?, ?> header) {
        this.description   = string(header.get("description"));
        this.flags         = set(header.get("flags"));
        this.features      = set(header.get("features"));
        this.includes      = list(header.get("includes"));
        this.hasEs6id      = header.containsKey("es6id");

        final Object negative = header.get("negative");
        if (negative instanceof Map<?, ?> n) {
            this.negativePhase = string(n.get("phase"));
            this.negativeType  = string(n.get("type"));
        } else {
            this.negativePhase = null;
            this.negativeType  = null;
        }
    }

    /**
     * Parses the header out of a test's source.
     *
     * @param source the whole test file
     * @return the header, or null if the file carries none
     * @throws IllegalArgumentException if the header is present but unparseable
     */
    public static Test262Frontmatter parse(final String source) {
        final int start = source.indexOf("/*---");
        if (start < 0) {
            return null;
        }
        final int end = source.indexOf("---*/", start);
        if (end < 0) {
            return null;
        }

        final Object header = new Yaml().load(source.substring(start + "/*---".length(), end));
        if (!(header instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("frontmatter is not a mapping: " + header);
        }
        return new Test262Frontmatter(map);
    }

    /** What the test says it is checking. */
    public String getDescription() {
        return description;
    }

    /** The {@code features:} the test needs, empty if it declares none. */
    public Set<String> getFeatures() {
        return features;
    }

    /** Harness files to load before the test, beyond the always-on ones. */
    public List<String> getIncludes() {
        return includes;
    }

    /** Whether the header carries the legacy ES2015-era {@code es6id:} marker. */
    public boolean hasEs6id() {
        return hasEs6id;
    }

    /** A test with no strictness flag has to run both ways. */
    public boolean runsSloppy() {
        return !flags.contains(FLAG_ONLY_STRICT);
    }

    public boolean runsStrict() {
        // raw tests are given to the engine verbatim, so no "use strict" may be prepended
        return !flags.contains(FLAG_NO_STRICT) && !flags.contains(FLAG_RAW) && !isModule();
    }

    /** Raw tests get no harness and no strictness prologue. */
    public boolean isRaw() {
        return flags.contains(FLAG_RAW);
    }

    /** Module tests are parsed under the module goal; modules are always strict. */
    public boolean isModule() {
        return flags.contains(FLAG_MODULE);
    }

    /** Async tests signal completion by calling $DONE rather than by returning. */
    public boolean isAsync() {
        return flags.contains(FLAG_ASYNC);
    }

    /** Whether the test is expected to fail, and where. */
    public boolean isNegative() {
        return negativeType != null;
    }

    /** {@code parse}, {@code resolution} or {@code runtime}; null when not negative. */
    public String getNegativePhase() {
        return negativePhase;
    }

    /** The error constructor the test must fail with, e.g. SyntaxError. */
    public String getNegativeType() {
        return negativeType;
    }

    public boolean hasFlag(final String flag) {
        return flags.contains(flag);
    }

    private static String string(final Object value) {
        return value == null ? null : value.toString().trim();
    }

    private static Set<String> set(final Object value) {
        return value instanceof List<?> l
            ? Collections.unmodifiableSet(new LinkedHashSet<>(list(value)))
            : Set.of();
    }

    private static List<String> list(final Object value) {
        if (!(value instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().map(Object::toString).map(String::trim).toList();
    }
}
