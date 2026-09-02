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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.ScriptRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The mode that shows what each top-level statement evaluates to.
 */
public class EchoModeTest {
    private ScriptRunner runner;

    @BeforeClass
    public void start() {
        runner = new ScriptRunner();
    }

    @AfterClass
    public void stop() {
        runner.close();
    }

    private static Sample sample(final String source, final String... options) {
        return new Sample("echo", List.of(), "echo", source, null, Map.of(), List.of(options));
    }

    @Test
    public void statementsEchoTheirValueOnTheirLine() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("var a = 1;\na + 1;\nfunction f() { return 'f'; }\nf();\n'str';\n[1, 2];\n({ x: 1 });\nprint('side effect');\n");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertEquals(recorder.values, List.of("1:2", "3:\"f\"", "4:\"str\"", "5:[1,2]", "6:{\"x\":1}"));
        assertEquals(recorder.out.toString(), "side effect\n");
    }

    @Test
    public void aHoistedForHeadDeclarationDoesNotSplitTheLoop() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("var values = [1, 2];\nfor (var v of values) {\n    print(v);\n}\nfor (var k in { a: 1 }) { print(k); }\n{\n    var inBlock = 3;\n}\ninBlock;\n");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), result.failure() == null ? "" : ScriptRunner.describe(result.failure()));
        assertEquals(recorder.out.toString(), "1\n2\na\n");
        assertEquals(recorder.values, List.of("8:3"));
    }

    @Test
    public void functionsAreHoistedAboveTheCallsThatPrecedeThem() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("g(2);\nfunction g(n) { return n * 21; }\n");
        runner.run(s, s.source(), true, recorder, recorder);
        assertTrue(recorder.await(30).ok());
        assertEquals(recorder.values, List.of("0:42"));
    }

    @Test
    public void printsInsideALoopAlignToTheirOwnLine() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("var xs = [1, 2];\nfor (var x of xs) {\n    print('tick', x);\n}\nprint('done');\n");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), result.failure() == null ? "" : ScriptRunner.describe(result.failure()));
        // each print is announced by a statement event on the print's own line -
        // the loop body's (2) per iteration, not the loop head's (1) - so a
        // console that mirrors the script aligns it without rewriting the code
        final List<String> interesting = recorder.events.stream()
                .filter(e -> e.equals("@2") || e.equals("@4") || e.startsWith("tick") || e.startsWith("done"))
                .toList();
        assertEquals(interesting, List.of("@2", "tick 1", "@2", "tick 2", "@4", "done"));
    }

    @Test
    public void aModuleRunsWholeWithNoPerStatementValues() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = new Sample("echo-module", List.of(), "echo-module",
                "import { x } from './dep.js';\nprint('got ' + x);\nexport const y = x + 1;\n",
                null, Map.of("dep.js", "export const x = 41;"), List.of());
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), result.failure() == null ? "" : ScriptRunner.describe(result.failure()));
        assertEquals(recorder.out.toString(), "got 41\n");
        assertEquals(recorder.values, List.of());
    }

    @Test
    public void aSyntaxErrorIsReportedNotThrownAsAnInternalError() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("var x = ;\n");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.failure() != null);
        assertTrue(ScriptRunner.describe(result.failure()).contains("Expected"), ScriptRunner.describe(result.failure()));
    }

    @Test
    public void scriptingModeHeredocsEchoTheirValues() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("// @option -scripting\nvar t = <<EOF\nheredoc\nEOF\nt.trim();\n", "-scripting");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertEquals(recorder.values, List.of("4:\"heredoc\""));
    }

    @Test
    public void letAndConstCarryAcrossStatements() throws InterruptedException {
        final Recorder recorder = new Recorder();
        final Sample s = sample("let a = 2;\nconst b = a * 3;\na + b;\n");
        runner.run(s, s.source(), true, recorder, recorder);
        final ScriptRunner.Result result = recorder.await(30);
        assertTrue(result.ok(), result.failure() == null ? "" : ScriptRunner.describe(result.failure()));
        assertEquals(recorder.values, List.of("2:8"));
    }
}
