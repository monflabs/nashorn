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

package org.monflabs.nashorn.debugger.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.monflabs.nashorn.debugger.CdpServer;
import org.monflabs.nashorn.debugger.json.Json;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The HTTP side: discovery, the Host check, the WebSocket handshake, and the
 * frame codec through a real client.
 */
@SuppressWarnings({"javadoc", "deprecation"})   // the factory overloads stay tested for compatibility
public class CdpServerTest {
    private ScriptEngine engine;
    private CdpServer.Handle server;

    @BeforeMethod
    public void setUp() throws Exception {
        engine = new NashornScriptEngineFactory().getScriptEngine("--debugger");
        server = CdpServer.open(Debugger.of(engine), InspectOptions.parse("127.0.0.1:0", false));
    }

    @AfterMethod
    public void tearDown() {
        server.close();
        Debugger.of(engine).close();
    }

    private String get(final String path) throws Exception {
        final HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode() + " " + r.body();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void discoveryDocuments() throws Exception {
        final String version = get("/json/version");
        assertTrue(version.startsWith("200 "), version);
        final Map<String, Object> v = (Map<String, Object>)Json.parse(version.substring(4));
        assertEquals(v.get("Protocol-Version"), "1.3");
        assertTrue(String.valueOf(v.get("Browser")).startsWith("Nashorn/"));

        for (final String path : new String[] { "/json", "/json/list" }) {
            final String list = get(path);
            assertTrue(list.startsWith("200 "), list);
            final List<Object> targets = (List<Object>)Json.parse(list.substring(4));
            assertEquals(targets.size(), 1);
            final Map<String, Object> target = (Map<String, Object>)targets.get(0);
            assertEquals(target.get("type"), "node");
            assertEquals(target.get("webSocketDebuggerUrl"), server.webSocketUrl());
            assertTrue(String.valueOf(target.get("devtoolsFrontendUrl")).startsWith("devtools://devtools/bundled/js_app.html?"));
        }
        assertTrue(get("/elsewhere").startsWith("404 "));
    }

    @Test
    public void hostHeaderMustBeAnAddress() throws Exception {
        assertTrue(rawRequest("GET /json HTTP/1.1\r\nHost: evil.example.com\r\n\r\n").startsWith("HTTP/1.1 400 "));
        assertTrue(rawRequest("GET /json HTTP/1.1\r\nHost: localhost:" + server.port() + "\r\n\r\n").startsWith("HTTP/1.1 200 "));
        assertTrue(rawRequest("GET /json HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").startsWith("HTTP/1.1 200 "));
        assertTrue(rawRequest("GET /json HTTP/1.1\r\nHost: [::1]:9229\r\n\r\n").startsWith("HTTP/1.1 200 "));
        assertTrue(rawRequest("GET /json HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400 "));
    }

    @Test
    public void handshakeComputesTheAcceptKey() throws Exception {
        // the example from RFC 6455 section 1.3
        final String response = rawRequest("GET " + path() + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 101 "), response);
        assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="), response);
    }

    @Test
    public void framesRoundTripThroughARealClient() throws Exception {
        try (CdpClient client = new CdpClient(server.webSocketUrl())) {
            // a message larger than a 16-bit frame length, both ways
            final String big = "x".repeat(70000);
            final Map<String, Object> result = client.call("Runtime.evaluate", "expression", "'" + big + "'.length");
            @SuppressWarnings("unchecked")
            final Map<String, Object> value = (Map<String, Object>)result.get("result");
            assertEquals(((Number)value.get("value")).intValue(), 70000);
            final Map<String, Object> echoed = client.call("Runtime.evaluate", "expression", "'" + big + "'");
            @SuppressWarnings("unchecked")
            final Map<String, Object> str = (Map<String, Object>)echoed.get("result");
            assertEquals(str.get("value"), big);
        }
    }

    @Test
    public void secondClientIsRefused() throws Exception {
        try (CdpClient first = new CdpClient(server.webSocketUrl())) {
            assertNotNull(first.call("Runtime.getIsolateId").get("id"), "the first client is served");
            final String response = rawRequest("GET " + path() + " HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 403 "), response);
        }
    }

    private String path() {
        final String url = server.webSocketUrl();
        return url.substring(url.lastIndexOf('/'));
    }

    private String rawRequest(final String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            final OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            final InputStream in = socket.getInputStream();
            final StringBuilder sb = new StringBuilder();
            final byte[] buf = new byte[4096];
            try {
                for (int n; (n = in.read(buf)) > 0;) {
                    sb.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                    if (sb.indexOf("\r\n\r\n") >= 0 && (sb.toString().startsWith("HTTP/1.1 101") || sb.indexOf("Content-Length") < 0 || bodyComplete(sb))) {
                        break;
                    }
                }
            } catch (final java.net.SocketTimeoutException e) {
                // whatever arrived
            }
            return sb.toString();
        }
    }

    private static boolean bodyComplete(final StringBuilder sb) {
        final String s = sb.toString();
        final int headerEnd = s.indexOf("\r\n\r\n");
        final int lengthAt = s.indexOf("Content-Length: ");
        if (lengthAt < 0) {
            return true;
        }
        final int length = Integer.parseInt(s.substring(lengthAt + 16, s.indexOf("\r\n", lengthAt)).trim());
        return s.length() - headerEnd - 4 >= length;
    }
}
