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

package org.monflabs.nashorn.playground.test;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.monflabs.nashorn.playground.Sample;
import org.monflabs.nashorn.playground.ScriptRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Stop ends a script that would never end by itself.
 */
public class StopTest {
    private ScriptRunner runner;

    @BeforeClass
    public void start() {
        runner = new ScriptRunner();
    }

    @AfterClass
    public void stop() {
        runner.close();
    }

    private ScriptRunner.Result stopped(final String source) throws InterruptedException {
        final Sample s = new Sample("stop", List.of(), "stop", source, null, Map.of(), List.of());
        final Recorder recorder = new Recorder();
        runner.run(s, source, false, recorder, recorder);
        assertTrue(recorder.started.await(30, TimeUnit.SECONDS));
        Thread.sleep(200);
        runner.stop();
        return recorder.await(10);
    }

    @Test
    public void anEndlessLoopStops() throws InterruptedException {
        final ScriptRunner.Result result = stopped("var i = 0; while (true) { i++; }");
        assertTrue(result.terminated(), String.valueOf(result.failure()));
        assertTrue(!runner.isRunning());
    }

    @Test
    public void aCatchAllDoesNotKeepTheScriptAlive() throws InterruptedException {
        final ScriptRunner.Result result = stopped("while (true) { try { var x = 1; } catch (e) { } }");
        assertTrue(result.terminated(), String.valueOf(result.failure()));
    }

    @Test
    public void aScriptBlockedInJavaIsInterrupted() throws InterruptedException {
        final ScriptRunner.Result result = stopped("java.lang.Thread.sleep(60000); print('not reached');");
        assertTrue(result.terminated(), String.valueOf(result.failure()));
    }

    @Test
    public void theRunnerRunsAgainAfterAStop() throws InterruptedException {
        stopped("while (true) {}");
        final Sample s = new Sample("after", List.of(), "after", "print('alive')", null, Map.of(), List.of());
        final Recorder recorder = new Recorder();
        runner.run(s, s.source(), false, recorder, recorder);
        assertTrue(recorder.await(30).ok());
        assertTrue(recorder.out.toString().equals("alive\n"), recorder.out.toString());
    }
}
