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

package org.monflabs.js.debugger.ui.test;

import org.monflabs.js.debugger.ui.cdp.CdpConnection;
import org.monflabs.nashorn.api.debugger.Debugger;
import org.monflabs.nashorn.api.debugger.InspectOptions;
import org.monflabs.nashorn.debugger.inprocess.InProcessCdpServer;
import org.testng.annotations.Test;

/**
 * {@link CdpConnectionTest}'s own scenarios - request/response, protocol
 * errors, events, close - against {@link InProcessCdpServer} instead of a real
 * socket. The same {@link CdpConnection} drives both; only the channel
 * underneath it differs.
 */
@SuppressWarnings({"javadoc", "deprecation"})
public class InProcessCdpConnectionTest extends CdpConnectionTest {

    @Override
    protected void openServer() {
        server = InProcessCdpServer.open(Debugger.of(engine), InspectOptions.parse("", false));
    }

    @Override
    protected CdpConnection connect(final Events events) {
        return CdpConnection.open(((InProcessCdpServer.Handle)server).clientChannel(), events);
    }

    /**
     * Not a gap to fill in later: the refusal this asserts needs a discoverable
     * {@code ws://} url a second party can dial while the first is attached. An
     * in-process channel has no url at all - the only way to reach the session
     * is the one handle {@code open()} returned - so the failure mode does not
     * exist for this transport.
     */
    @Override
    @Test(enabled = false)
    public void aSecondClientIsRefusedAsBusy() {
        // see the javadoc
    }
}
