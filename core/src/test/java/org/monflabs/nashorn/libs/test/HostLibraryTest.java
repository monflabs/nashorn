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

package org.monflabs.nashorn.libs.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.libs.HostLibrary;
import org.testng.annotations.Test;

/**
 * The host library: timers on the event loop, queueMicrotask, atob and btoa.
 */
@SuppressWarnings("deprecation")   // the factory overloads stay tested for compatibility
public class HostLibraryTest {

    private static ScriptEngine engine() {
        // discovered from META-INF/services on the test class path, as an embedder would have it
        return new NashornScriptEngineFactory().getScriptEngine();
    }

    @Test(timeOut = 30_000)
    public void theLibraryIsDiscovered() throws ScriptException {
        final ScriptEngine e = engine();
        assertEquals(e.eval("[typeof setTimeout, typeof clearTimeout, typeof setInterval, typeof clearInterval, typeof queueMicrotask, typeof atob, typeof btoa].join()"),
                "function,function,function,function,function,function,function");
        assertEquals(e.eval("setTimeout.name"), "setTimeout");
        // real functions: Function.prototype applies, and they are non-enumerable like the language's own
        assertEquals(e.eval("typeof setTimeout.call + ':' + (Object.getPrototypeOf(btoa) === Function.prototype)"), "function:true");
        assertEquals(e.eval("btoa.call(null, 'x')"), "eA==");
        assertEquals(e.eval("Object.getOwnPropertyDescriptor(this, 'setTimeout').enumerable"), false);
        assertEquals(e.eval("var seen = []; for (var k in this) { if (k === 'fetch' || k === 'setTimeout') seen.push(k); } seen.length"), 0);
        assertEquals(new NashornScriptEngineFactory().getScriptEngine("--libraries=none").eval("typeof setTimeout"), "undefined");
        assertEquals(new NashornScriptEngineFactory().getScriptEngine(new String[] { "--libraries=none" }, new HostLibrary()).eval("typeof setTimeout"), "function");
    }

    @Test(timeOut = 30_000)
    public void setTimeoutRunsAfterTheSynchronousCodeInDelayOrderAndEvalWaits() throws ScriptException {
        final ScriptEngine e = engine();
        final long start = System.nanoTime();
        e.eval("var log = []; setTimeout(function () { log.push('later'); }, 40); setTimeout(function () { log.push('soon'); }, 5); setTimeout(function () { log.push('now'); }); log.push('sync');");
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 40);
        assertEquals(e.eval("log.join()"), "sync,now,soon,later");
    }

    @Test(timeOut = 30_000)
    public void extraArgumentsReachTheCallback() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var got; setTimeout(function (a, b) { got = a + b; }, 1, 'x', 'y');");
        assertEquals(e.eval("got"), "xy");
    }

    @Test(timeOut = 30_000)
    public void idsAreSmallPositiveIntegersPerGlobal() throws ScriptException {
        final ScriptEngine e = engine();
        // the interval is cleared in the same eval: an eval returns only once the script is idle
        assertEquals(e.eval("var ids = [setTimeout(function () {}), setTimeout(function () {}), setInterval(function () {}, 1000)]; clearInterval(ids[2]); ids.join()"), "1,2,3");
        assertEquals(e.eval("setTimeout(function () {})", e.createBindings()), 1);
        // no callback: 0, which cancels nothing
        assertEquals(e.eval("setTimeout('not a function', 1)"), 0);
        assertEquals(e.eval("setTimeout()"), 0);
    }

    @Test(timeOut = 30_000)
    public void clearTimeoutCancels() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var log = []; var t = setTimeout(function () { log.push('no'); }, 5); clearTimeout(t); clearTimeout(t); clearTimeout(999); clearTimeout(); clearTimeout(null);"
                + "var u = setTimeout(function () { log.push('yes'); }, 10); setTimeout(function () { clearTimeout(v); }, 1); var v = setTimeout(function () { log.push('never'); }, 30);");
        assertEquals(e.eval("log.join()"), "yes");
    }

    @Test(timeOut = 30_000)
    public void setIntervalRepeatsUntilCleared() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var ticks = 0; var id = setInterval(function () { if (++ticks === 3) clearInterval(id); }, 2);");
        assertEquals(((Number)e.eval("ticks")).intValue(), 3);
        // clearTimeout clears an interval too, as the specification says
        e.eval("var n = 0; var i = setInterval(function () { if (++n === 2) clearTimeout(i); }, 1);");
        assertEquals(((Number)e.eval("n")).intValue(), 2);
    }

    @Test(timeOut = 30_000)
    public void queueMicrotaskRunsBeforeTimersInOrderWithPromises() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var log = []; setTimeout(function () { log.push('timer'); }, 0); Promise.resolve().then(function () { log.push('promise'); });"
                + "queueMicrotask(function () { log.push('micro'); }); log.push('sync');");
        assertEquals(e.eval("log.join()"), "sync,promise,micro,timer");
        assertEquals(e.eval("try { queueMicrotask('x'); } catch (x) { x.name }"), "TypeError");
    }

    @Test(timeOut = 30_000)
    public void aTimerCallbackRunsOnTheScriptThreadWithTheRealm() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var thread, fromScope; var scoped = 'seen'; setTimeout(function () { thread = java.lang.Thread.currentThread().getName(); fromScope = scoped + ' ' + typeof Math; }, 1);");
        assertEquals(e.eval("thread"), Thread.currentThread().getName());
        assertEquals(e.eval("fromScope"), "seen object");
    }

    @Test(timeOut = 30_000)
    public void base64RoundTrips() throws ScriptException {
        final ScriptEngine e = engine();
        assertEquals(e.eval("btoa('Hello, world')"), "SGVsbG8sIHdvcmxk");
        assertEquals(e.eval("atob('SGVsbG8sIHdvcmxk')"), "Hello, world");
        assertEquals(e.eval("atob(btoa('\\u00e9\\u00ff'))"), "éÿ");
        assertEquals(e.eval("btoa('')"), "");
        assertEquals(e.eval("btoa(123)"), "MTIz");
        // forgiving: whitespace and missing padding
        assertEquals(e.eval("atob(' SGVs bG8= ')"), "Hello");
        assertEquals(e.eval("atob('SGVsbG8')"), "Hello");
        assertEquals(e.eval("atob('SGk')"), "Hi");
        assertEquals(e.eval("try { atob('SGVsbG8=!'); } catch (x) { x.name + ': ' + x.message }"), "Error: atob: the string to be decoded is not correctly encoded");
        assertEquals(e.eval("try { atob('A'); } catch (x) { x.name }"), "Error");
        assertEquals(e.eval("try { btoa('\\u4e2d'); } catch (x) { x.name }"), "Error");
        assertEquals(e.eval("try { atob(); } catch (x) { x.name }"), "TypeError");
    }
}
