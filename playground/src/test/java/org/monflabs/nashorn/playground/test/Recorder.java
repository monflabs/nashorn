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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.monflabs.nashorn.playground.ScriptRunner;

/**
 * A console and listener for the tests: collects the output, waits for the end.
 */
final class Recorder implements ScriptRunner.Console, ScriptRunner.Listener {
    final StringBuffer out = new StringBuffer();
    final StringBuffer err = new StringBuffer();
    final List<String> values = new CopyOnWriteArrayList<>();
    /** Statement announcements ({@code @line}) and output, in arrival order. */
    final List<String> events = new CopyOnWriteArrayList<>();
    final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    volatile ScriptRunner.Result result;

    @Override
    public void out(final String text) {
        out.append(text);
        events.add(text);
    }

    @Override
    public void err(final String text) {
        err.append(text);
    }

    @Override
    public void valueAtLine(final int line, final String text) {
        values.add(line + ":" + text);
    }

    @Override
    public void statementAt(final int line) {
        events.add("@" + line);
    }

    @Override
    public void started() {
        started.countDown();
    }

    @Override
    public void finished(final ScriptRunner.Result r) {
        result = r;
        done.countDown();
    }

    ScriptRunner.Result await(final long seconds) throws InterruptedException {
        if (!done.await(seconds, TimeUnit.SECONDS)) {
            throw new AssertionError("the script did not finish within " + seconds + "s; output so far:\n" + out + err);
        }
        return result;
    }
}
