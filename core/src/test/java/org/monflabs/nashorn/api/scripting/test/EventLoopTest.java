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

package org.monflabs.nashorn.api.scripting.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.EventLoop;
import org.monflabs.nashorn.api.scripting.JSObject;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.api.scripting.ScriptLibrary;
import org.monflabs.nashorn.api.scripting.ScriptUtils;
import org.testng.annotations.Test;

/**
 * The event loop behind a realm: microtasks first, timers and posted tasks
 * after, eval returning only when the script is idle.
 */
@SuppressWarnings("deprecation")   // the factory overloads stay tested for compatibility
public class EventLoopTest {

    /** A tiny host: later(fn, ms) schedules, cancel(t) cancels, soon(fn) queues a microtask, request(fn) completes off-thread. */
    private static ScriptEngine engine() {
        final JSObject later = fn((thiz, args) -> {
            final JSObject callback = (JSObject)args[0];
            return EventLoop.current().schedule(() -> callback.call(ScriptUtils.undefined()), args.length > 1 ? ScriptUtils.toLong(args[1]) : 0);
        });
        final JSObject cancel = fn((thiz, args) -> {
            ((EventLoop.Timer)args[0]).cancel();
            return ScriptUtils.undefined();
        });
        final JSObject soon = fn((thiz, args) -> {
            final JSObject callback = (JSObject)args[0];
            EventLoop.current().queueMicrotask(() -> callback.call(ScriptUtils.undefined()));
            return ScriptUtils.undefined();
        });
        final JSObject request = fn((thiz, args) -> {
            final JSObject callback = (JSObject)args[0];
            final EventLoop.Pending pending = EventLoop.current().pending();
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException ignored) {
                    // the sleep is the point
                }
                pending.complete(() -> callback.call(ScriptUtils.undefined(), "result from " + Thread.currentThread().getName()));
            });
            return ScriptUtils.undefined();
        });
        return new NashornScriptEngineFactory().getScriptEngine(ScriptLibrary.of("loop", Map.of("later", later, "cancel", cancel, "soon", soon, "request", request)));
    }

    private interface Body {
        Object call(Object thiz, Object... args);
    }

    private static JSObject fn(final Body body) {
        return new AbstractJSObject() {
            @Override
            public Object call(final Object thiz, final Object... args) {
                return body.call(thiz, args);
            }

            @Override
            public boolean isFunction() {
                return true;
            }
        };
    }

    @Test
    public void evalReturnsOnceTheTimersHaveRun() throws ScriptException {
        final ScriptEngine e = engine();
        final long start = System.nanoTime();
        e.eval("var log = []; later(function () { log.push('40'); }, 40); later(function () { log.push('10'); }, 10); log.push('sync');");
        final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertEquals(e.eval("log.join(',')"), "sync,10,40");
        assertTrue(millis >= 40, "took " + millis + " ms");
    }

    @Test
    public void microtasksRunBeforeTimersAndAfterSynchronousCode() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var log = []; later(function () { log.push('timer'); soon(function () { log.push('micro-in-timer'); }); }, 0);"
                + "Promise.resolve().then(function () { log.push('promise'); }); soon(function () { log.push('micro'); }); log.push('sync');");
        assertEquals(e.eval("log.join(',')"), "sync,promise,micro,timer,micro-in-timer");
    }

    @Test
    public void aCancelledTimerDoesNotRun() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var log = []; var t = later(function () { log.push('no'); }, 10); cancel(t); later(function () { log.push('yes'); }, 20);");
        assertEquals(e.eval("log.join(',')"), "yes");
    }

    @Test
    public void aTimerMaySchedule() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var n = 0; function tick() { if (++n < 5) later(tick, 1); } later(tick, 1);");
        assertEquals(((Number)e.eval("n")).intValue(), 5);
    }

    @Test
    public void aPendingOperationKeepsTheLoopOpenAndCompletesOnTheScriptThread() throws ScriptException {
        final ScriptEngine e = engine();
        final String thread = Thread.currentThread().getName();
        e.eval("var got = null; request(function (r) { got = r; });");
        // eval waited for the completion, and the continuation ran here, in the realm
        assertTrue(String.valueOf(e.eval("got")).startsWith("result from "), String.valueOf(e.eval("got")));
        e.eval("var where = null; request(function () { where = java.lang.Thread.currentThread().getName(); });");
        assertEquals(e.eval("where"), thread);
    }

    @Test
    public void aScriptWithNothingScheduledReturnsAtOnce() throws ScriptException {
        final ScriptEngine e = engine();
        final long start = System.nanoTime();
        assertEquals(e.eval("1 + 1"), 2);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
    }

    @Test
    public void interruptingTheThreadEndsALoopThatNeverGoesIdle() throws Exception {
        final ScriptEngine e = engine();
        final CountDownLatch running = new CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];
        final Thread t = new Thread(() -> {
            try {
                e.put("started", (Runnable)running::countDown);
                e.eval("function forever() { later(forever, 5); } forever(); started.run();");
            } catch (final Throwable x) {
                failure[0] = x;
            }
        });
        t.start();
        assertTrue(running.await(10, TimeUnit.SECONDS));
        Thread.sleep(50);
        t.interrupt();
        t.join(10_000);
        assertTrue(!t.isAlive(), "the loop should have ended");
        assertEquals(failure[0], null, String.valueOf(failure[0]));
    }

    @Test
    public void theLoopIsPerRealm() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var log = []; later(function () { log.push('a'); }, 5);");
        assertEquals(e.eval("log.join(',')"), "a");
        assertEquals(e.eval("var log = []; later(function () { log.push('b'); }, 5); log.join(',')", e.createBindings()), "");
    }

    @Test
    public void currentNeedsARealm() {
        try {
            EventLoop.current();
            fail("expected a refusal");
        } catch (final IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("realm"));
        }
    }

    @Test
    public void aTimerCallbackThatThrowsEndsTheEvalWithTheError() {
        final ScriptEngine e = engine();
        try {
            e.eval("later(function () { throw new Error('from a timer'); }, 1);");
            fail("expected the error");
        } catch (final ScriptException x) {
            assertTrue(x.getMessage().contains("from a timer"), x.getMessage());
        }
    }
}
