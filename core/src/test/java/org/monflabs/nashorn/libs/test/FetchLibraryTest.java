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

package org.monflabs.nashorn.libs.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineBuilder;
import org.monflabs.nashorn.libs.FetchLibrary;
import org.monflabs.nashorn.libs.HostLibrary;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The fetch library against a local server: the promise, the Response,
 * Headers and Request, errors, and async/await over it.
 */
public class FetchLibraryTest {
    private HttpServer server;
    private String base;

    @BeforeClass
    public void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/json", x -> reply(x, 200, "application/json", "{\"answer\": 42, \"list\": [1, 2]}"));
        server.createContext("/missing", x -> reply(x, 404, "text/plain", "no such thing"));
        server.createContext("/latin", x -> reply(x, 200, "text/plain; charset=ISO-8859-1", new String(new byte[] { (byte)0xE9 }, StandardCharsets.ISO_8859_1)));
        server.createContext("/slow", x -> {
            try {
                Thread.sleep(60);
            } catch (final InterruptedException ignored) {
                // just a delay
            }
            reply(x, 200, "text/plain", "slow");
        });
        server.createContext("/echo", x -> {
            final String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final String reply = x.getRequestMethod() + "|" + x.getRequestHeaders().getFirst("X-Test") + "|" + x.getRequestHeaders().getFirst("Content-Type") + "|" + body;
            x.getResponseHeaders().add("X-Reply", "yes");
            x.getResponseHeaders().add("X-Multi", "a");
            x.getResponseHeaders().add("X-Multi", "b");
            reply(x, 201, "text/plain", reply);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void reply(final HttpExchange x, final int status, final String type, final String text) throws IOException {
        final byte[] bytes = text.getBytes(type.contains("8859-1") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", type);
        x.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = x.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterClass
    public void stop() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService)server.getExecutor()).shutdownNow();
    }

    private static ScriptEngine engine() {
        // contributed explicitly to the builder - there is no discovery
        return new NashornScriptEngineBuilder().library(new HostLibrary(), new FetchLibrary()).build();
    }

    @Test(timeOut = 30_000)
    public void theLibraryInstalls() throws ScriptException {
        assertEquals(engine().eval("[typeof fetch, typeof Headers, typeof Request, typeof Response].join()"), "function,function,function,function");
        assertEquals(new NashornScriptEngineBuilder().build().eval("typeof fetch"), "undefined");
    }

    @Test(timeOut = 30_000)
    public void fetchResolvesWithAResponseAndEvalWaitsForIt() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var result = {}; fetch('" + base + "/json').then(function (r) { result.status = r.status; result.ok = r.ok; result.type = r.headers.get('content-type'); result.url = r.url; return r.json(); }).then(function (j) { result.answer = j.answer; });");
        assertEquals(e.eval("result.status"), 200);
        assertEquals(e.eval("result.ok"), true);
        assertEquals(e.eval("result.type"), "application/json");
        assertEquals(e.eval("result.answer"), 42);
        assertEquals(e.eval("result.url"), base + "/json");
    }

    @Test(timeOut = 30_000)
    public void asyncAwaitOverFetch() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var text; (async function () { const r = await fetch('" + base + "/json'); const j = await r.json(); text = j.list.join('+') + ' ' + r.statusText; })();");
        assertEquals(e.eval("text"), "1+2 OK");
    }

    @Test(timeOut = 30_000)
    public void postSendsMethodHeadersAndBody() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var got, reply, multi; fetch('" + base + "/echo', { method: 'post', headers: { 'X-Test': 'hello', 'Content-Type': 'text/plain' }, body: 'payload' })"
                + ".then(function (r) { reply = r.headers.get('x-reply'); multi = r.headers.get('X-MULTI'); return r.text(); }).then(function (t) { got = t; });");
        assertEquals(e.eval("got"), "POST|hello|text/plain|payload");
        assertEquals(e.eval("reply"), "yes");
        assertEquals(e.eval("multi"), "a, b");
    }

    @Test(timeOut = 30_000)
    public void aRequestObjectAndAHeadersObjectAreAcceptedAsInput() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var got; var h = new Headers([['X-Test', 'from-headers']]); var req = new Request('" + base + "/echo', { method: 'PUT', headers: h, body: 'b' });"
                + "fetch(req).then(function (r) { return r.text(); }).then(function (t) { got = t; });");
        assertEquals(e.eval("got"), "PUT|from-headers|null|b");
        assertEquals(e.eval("req.method + ' ' + req.headers.get('x-test') + ' ' + req.url"), "PUT from-headers " + base + "/echo");
    }

    @Test(timeOut = 30_000)
    public void anHttpErrorResolvesWithOkFalse() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var r; fetch('" + base + "/missing').then(function (x) { r = x; });");
        assertEquals(e.eval("[r.status, r.ok, r.statusText].join()"), "404,false,Not Found");
    }

    @Test(timeOut = 30_000)
    public void aNetworkFailureRejectsWithATypeError() throws ScriptException {
        final ScriptEngine e = engine();
        // port 1: nothing listens there without privileges, so the connection is refused at once
        e.eval("var err; fetch('http://127.0.0.1:1/nothing').catch(function (x) { err = x; });");
        assertEquals(e.eval("err instanceof TypeError"), true);
        assertTrue(String.valueOf(e.eval("err.message")).startsWith("fetch: "), String.valueOf(e.eval("err.message")));
        e.eval("var bad; fetch('not a url at all').catch(function (x) { bad = x; });");
        assertEquals(e.eval("bad instanceof TypeError"), true);
        e.eval("var host; fetch('http://no.such.host.invalid/x').catch(function (x) { host = x; });");
        assertEquals(e.eval("host instanceof TypeError"), true);
    }

    @Test(timeOut = 30_000)
    public void severalRequestsInFlightAtOnce() throws ScriptException {
        final ScriptEngine e = engine();
        final long start = System.nanoTime();
        e.eval("var all; Promise.all([fetch('" + base + "/slow'), fetch('" + base + "/slow'), fetch('" + base + "/json')]).then(function (rs) { all = rs.map(function (r) { return r.status; }).join(); });");
        assertEquals(e.eval("all"), "200,200,200");
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5000);
    }

    @Test(timeOut = 30_000)
    public void theBodyIsDecodedByTheResponsesCharsetAndReadOnce() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var t, again, buf; fetch('" + base + "/latin').then(function (r) { return r.text().then(function (x) { t = x; return r.text(); }); }).catch(function (x) { again = x.name; });");
        assertEquals(e.eval("t"), "é");
        assertEquals(e.eval("again"), "TypeError");
        e.eval("var len, first; fetch('" + base + "/json').then(function (r) { return r.arrayBuffer(); }).then(function (b) { len = b.byteLength; first = new Uint8Array(b)[0]; });");
        assertEquals(((Number)e.eval("len")).intValue(), "{\"answer\": 42, \"list\": [1, 2]}".length());
        assertEquals(((Number)e.eval("first")).intValue(), '{');
    }

    @Test(timeOut = 30_000)
    public void headersBehaveAsSpecified() throws ScriptException {
        final ScriptEngine e = engine();
        assertEquals(e.eval("var h = new Headers({ 'Content-Type': 'text/plain', 'X-A': '1' }); h.append('x-a', '2'); h.set('X-B', ' spaced '); [h.get('content-type'), h.get('X-A'), h.has('x-b'), h.get('x-b'), h.get('nope')].join('|')"),
                "text/plain|1, 2|true|spaced|");
        assertEquals(e.eval("Array.from(h.keys()).join()"), "content-type,x-a,x-b");
        assertEquals(e.eval("Array.from(h.values()).join('|')"), "text/plain|1, 2|spaced");
        assertEquals(e.eval("Array.from(h).map(function (p) { return p.join('='); }).join(';')"), "content-type=text/plain;x-a=1, 2;x-b=spaced");
        assertEquals(((Number)e.eval("var n = 0; for (var pair of h) { n += pair.length; } n")).intValue(), 6);
        assertEquals(e.eval("[h instanceof Headers, ({}) instanceof Headers, String(h), Headers.name, typeof Headers].join()"), "true,false,[object Headers],Headers,function");
        assertEquals(e.eval("try { Headers(); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("try { h.get.call({}, 'x'); } catch (x) { x.name }"), "TypeError");
        // the shape the specification gives it: a real prototype, methods inherited, Symbol.iterator
        assertEquals(e.eval("Object.getPrototypeOf(h) === Headers.prototype"), true);
        assertEquals(e.eval("[h.hasOwnProperty('append'), 'append' in h, Headers.prototype.hasOwnProperty('append'), h.constructor === Headers].join()"), "false,true,true,true");
        assertEquals(e.eval("Headers.prototype.append.call(h, 'z', '9'); h.get('z')"), "9");
        assertEquals(e.eval("h[Symbol.iterator] === h.entries"), true);
        assertEquals(e.eval("String(h.entries())"), "[object Headers Iterator]");
        assertEquals(e.eval("Object.getOwnPropertyNames(Headers.prototype).sort().join()"), "append,constructor,delete,entries,forEach,get,has,keys,set,values");
        assertEquals(e.eval("h.delete('x-a'); h.has('X-A')"), false);
        assertEquals(e.eval("var seen = []; h.forEach(function (v, k) { seen.push(k + ':' + v); }); seen.join()"), "content-type:text/plain,x-b:spaced,z:9");
        assertEquals(e.eval("try { new Headers({ 'bad header': 'x' }); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("new Headers(h).get('content-type')"), "text/plain");
    }

    @Test(timeOut = 30_000)
    public void responsesCanBeMadeByHand() throws ScriptException {
        final ScriptEngine e = engine();
        e.eval("var r = new Response('{\"k\": 1}', { status: 201, statusText: 'Created', headers: { 'Content-Type': 'application/json' } }); var j; r.json().then(function (x) { j = x.k; });");
        assertEquals(e.eval("[r.status, r.ok, r.statusText, r.headers.get('content-type'), j].join()"), "201,true,Created,application/json,1");
        assertEquals(e.eval("try { new Response('', { status: 99 }); } catch (x) { x.name }"), "RangeError");
        assertEquals(e.eval("try { new Request('" + base + "', { body: 'x' }); } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("Response.error().ok"), false);
        assertEquals(e.eval("Response.error().status"), 0);
        assertEquals(e.eval("[r instanceof Response, new Request('" + base + "') instanceof Request, r instanceof Request, Object.keys(r).length].join('|')"), "true|true|false|0");
        assertEquals(e.eval("var c = r.clone(); c.status + ':' + c.bodyUsed + ':' + r.bodyUsed"), "201:false:true");
        assertEquals(e.eval("r.extra = 5; r.extra + ':' + ('extra' in r)"), "5:true");
        // status, ok and the rest are read-only accessors on the prototype, as WebIDL says
        assertEquals(e.eval("var d = Object.getOwnPropertyDescriptor(Response.prototype, 'status'); typeof d.get + ':' + typeof d.set + ':' + d.enumerable"), "function:undefined:false");
        assertEquals(e.eval("r.status = 0; r.status"), 201);
        assertEquals(e.eval("try { (function () { 'use strict'; r.ok = true; })(); 'assigned' } catch (x) { x.name }"), "TypeError");
        assertEquals(e.eval("Object.prototype.toString.call(r) + Object.prototype.toString.call(new Request('" + base + "'))"), "[object Response][object Request]");
        assertEquals(e.eval("typeof Response.prototype.text + ':' + typeof Response.error + ':' + typeof r.text.call"), "function:function:function");
    }
}
