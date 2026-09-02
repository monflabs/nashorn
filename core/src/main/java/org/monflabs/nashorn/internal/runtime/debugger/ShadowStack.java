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

package org.monflabs.nashorn.internal.runtime.debugger;

import java.util.Arrays;

/**
 * The script frames of one thread, maintained by the entry and exit hooks,
 * with the thread's stepping state. Thread-confined; a virtual thread running
 * a generator body has one of its own.
 */
final class ShadowStack {
    private static final ThreadLocal<ShadowStack> CURRENT = ThreadLocal.withInitial(ShadowStack::new);

    enum StepMode { NONE, INTO, OVER, OUT }

    private Frame[] frames = new Frame[16];
    private int depth;

    StepMode step = StepMode.NONE;
    int stepDepth;
    /** The debugger that owns the pending step, so that it can be told when the step ends. */
    DebuggerImpl stepping;
    /** The pause the thread is in, or null. */
    PausedEventImpl paused;
    /** Whether the thread is running a command on behalf of a pause, in which case hooks must not pause again. */
    boolean inCommand;
    /** The last thrown value paused on, so that unwinding it does not pause a second time. */
    Object pausedThrown;
    /** Set by terminate: every statement hook throws until the stack is empty. */
    volatile boolean terminating;

    static ShadowStack current() {
        return CURRENT.get();
    }

    void push(final Frame frame) {
        if (depth == frames.length) {
            frames = Arrays.copyOf(frames, depth * 2);
        }
        frames[depth++] = frame;
    }

    Frame pop() {
        if (depth == 0) {
            return null;
        }
        final Frame frame = frames[--depth];
        frames[depth] = null;
        return frame;
    }

    Frame top() {
        return depth == 0 ? null : frames[depth - 1];
    }

    int depth() {
        return depth;
    }

    /** The frame at an index counted from the top: 0 is the innermost. */
    Frame frame(final int fromTop) {
        return frames[depth - 1 - fromTop];
    }

    void clearStep() {
        if (step != StepMode.NONE) {
            step = StepMode.NONE;
            final DebuggerImpl owner = stepping;
            stepping = null;
            if (owner != null) {
                owner.stepEnded();
            }
        }
    }
}
