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

package org.monflabs.nashorn.playground.test;

import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.SampleLibrary;
import org.monflabs.nashorn.playground.ScriptRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Every bundled sample runs on the engine it is bundled with: the library
 * cannot drift from what the engine supports.
 */
public class SampleRunTest {
    /** The samples that end in an error on purpose, and what the error is. */
    private static final java.util.Map<String, String> EXPECTED_FAILURES = java.util.Map.of(
            "01 - Getting started/10 - Errors", "undefinedFunction",
            "02 - ECMAScript support/01 - ES2015/23 - Proper tail calls", "StackOverflowError");

    private ScriptRunner runner;

    @BeforeClass
    public void start() {
        runner = new ScriptRunner();
    }

    @AfterClass
    public void stop() {
        runner.close();
    }

    @Test
    public void everySampleRuns() throws IOException, InterruptedException {
        final List<String> failures = new ArrayList<>();
        int count = 0;
        for (final Sample sample : SampleLibrary.load().samples()) {
            count++;
            final Recorder recorder = new Recorder();
            runner.run(sample, sample.source(), false, recorder, recorder);
            final ScriptRunner.Result result = recorder.await(60);
            final String expected = EXPECTED_FAILURES.get(sample.id());
            if (expected != null) {
                if (result.failure() == null || !ScriptRunner.describe(result.failure()).contains(expected)) {
                    failures.add(sample.id() + ": expected a failure mentioning " + expected + ", got "
                            + (result.failure() == null ? "success" : ScriptRunner.describe(result.failure())));
                }
            } else if (!result.ok()) {
                failures.add(sample.id() + ": " + (result.terminated() ? "terminated" : ScriptRunner.describe(result.failure()))
                        + (recorder.err.length() == 0 ? "" : "\nstderr: " + recorder.err));
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " of " + count + " samples failed:\n" + String.join("\n\n", failures));
    }

    @Test
    public void helloPrints() throws IOException, InterruptedException {
        final Sample hello = SampleLibrary.load().byId("01 - Getting started/01 - Hello");
        final Recorder recorder = new Recorder();
        runner.run(hello, hello.source(), false, recorder, recorder);
        assertTrue(recorder.await(30).ok());
        assertTrue(recorder.out.toString().startsWith("Hello, Nashorn!\n"), recorder.out.toString());
        assertTrue(recorder.out.toString().contains("info\n"), recorder.out.toString());
        assertTrue(recorder.err.toString().contains("error"), recorder.err.toString());
    }
}
