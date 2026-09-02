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

package org.monflabs.nashorn.playground.ui;

import static org.testng.Assert.assertEquals;

import java.awt.Font;
import org.testng.annotations.Test;

/**
 * In the echo mode the console mirrors the script line by line: output and
 * values land on the line of the statement that made them.
 */
public class ConsoleAlignmentTest {

    private static ConsolePane pane() {
        return new ConsolePane(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    @Test
    public void outputAndValuesLandOnTheirStatementsLines() {
        final ConsolePane console = pane();
        console.statementAt(0);
        console.valueAtLine(0, "1");          // line 0: a value
        console.statementAt(2);
        console.out("printed\n");            // line 2: print('printed')
        console.statementAt(4);
        console.valueAtLine(4, "\"x\"");      // line 4: 'x'
        console.flushNow();
        assertEquals(console.getText(), "  // 1\n\nprinted\n\n  // \"x\"\n");
    }

    @Test
    public void outputThatRanPastALineIsNotPulledBack() {
        final ConsolePane console = pane();
        console.statementAt(0);
        console.out("one\ntwo\nthree\n");    // three lines from a statement on line 0
        console.statementAt(1);              // the next statement is on line 1, already passed
        console.valueAtLine(1, "42");
        console.flushNow();
        assertEquals(console.getText(), "one\ntwo\nthree\n  // 42\n");
    }

    @Test
    public void aStatementWithNothingToShowLeavesNoTrace() {
        final ConsolePane console = pane();
        console.statementAt(3);
        console.statementAt(5);
        console.out("late\n");
        console.flushNow();
        assertEquals(console.getText(), "\n\n\n\n\nlate\n");
    }
}
