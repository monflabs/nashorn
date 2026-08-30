/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.monflabs.nashorn.internal.runtime.test;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.monflabs.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * @bug 8081204
 * @summary adding and removing elements to a ListAdapter outside of JS context should work.
 */
@SuppressWarnings("javadoc")
public class AddAndRemoveOnListAdapterOutsideOfJavaScriptContextTest {

    @SuppressWarnings("unchecked")
    private static <T> T getListAdapter() throws ScriptException {
        final ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
        return (T)engine.eval("Java.to([1, 2, 3, 4], 'java.util.List')");
    }

    @Test
    public void testInvokePush() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.addLast(5);
        assertEquals(l.size(), 5);
        assertEquals(l.getLast(), 5);
        assertEquals(l.getFirst(), 1);
    }

    @Test
    public void testPop() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        assertEquals(l.removeLast(), 4);
        assertEquals(l.size(), 3);
        assertEquals(l.getLast(), 3);
    }

    @Test
    public void testUnshift() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.addFirst(0);
        assertEquals(l.getFirst(), 0);
        assertEquals(l.getLast(), 4);
        assertEquals(l.size(), 5);
    }

    @Test
    public void testShift() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        l.removeFirst();
        assertEquals(l.getFirst(), 2);
        assertEquals(l.getLast(), 4);
        assertEquals(l.size(), 3);
    }

    @Test
    public void testSpliceAdd() throws ScriptException {
        final List<Object> l = getListAdapter();
        assertEquals(l, Arrays.asList(1, 2, 3, 4));
        l.add(2, "foo");
        assertEquals(l, Arrays.asList(1, 2, "foo", 3, 4));
    }


    @Test
    public void testSpliceRemove() throws ScriptException {
        final List<Object> l = getListAdapter();
        assertEquals(l, Arrays.asList(1, 2, 3, 4));
        l.remove(2);
        assertEquals(l, Arrays.asList(1, 2, 4));
    }

    @Test
    public void testReversedIsAView() throws ScriptException {
        final List<Object> l = getListAdapter();
        final List<Object> r = l.reversed();
        assertEquals(r, Arrays.asList(4, 3, 2, 1));
        assertEquals(r.reversed(), l);

        // writes through in both directions
        r.add(0);
        assertEquals(l, Arrays.asList(0, 1, 2, 3, 4));
        r.add(0, 5);
        assertEquals(l, Arrays.asList(0, 1, 2, 3, 4, 5));
        r.remove(0);
        assertEquals(l, Arrays.asList(0, 1, 2, 3, 4));
        r.set(0, "foo");
        assertEquals(l, Arrays.asList(0, 1, 2, 3, "foo"));
        l.add("bar");
        assertEquals(r, Arrays.asList("bar", "foo", 3, 2, 1, 0));
    }

    @Test
    public void testReversedAsDeque() throws ScriptException {
        final Deque<Object> l = getListAdapter();
        final Deque<Object> d = l.reversed();
        assertEquals(d.getFirst(), 4);
        assertEquals(d.getLast(), 1);
        d.addFirst(5);
        d.addLast(0);
        assertEquals(d.peekFirst(), 5);
        assertEquals(d.peekLast(), 0);
        assertEquals(d.removeFirst(), 5);
        assertEquals(d.removeLast(), 0);
        assertEquals(d.size(), 4);
    }
}
