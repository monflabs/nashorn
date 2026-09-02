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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import org.monflabs.nashorn.api.debugger.DebugFrame;
import org.monflabs.nashorn.api.debugger.ExecutionContext;
import org.monflabs.nashorn.api.debugger.PauseReason;
import org.monflabs.nashorn.api.debugger.PausedEvent;

/**
 * A pause: the frames as they were, and the queue through which other threads
 * hand the paused thread work.
 */
final class PausedEventImpl implements PausedEvent {
    private static final Runnable WAKE = () -> {};

    final DebuggerImpl debugger;
    private final ShadowStack stack;
    private final Thread thread;
    private final PauseReason reason;
    private final List<DebugFrame> frames;
    private final List<String> hits;
    private final Object exception;
    private final ExecutionContextImpl context;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private volatile boolean resumed;

    PausedEventImpl(final DebuggerImpl debugger, final ShadowStack stack, final PauseReason reason,
            final List<String> hits, final Object exception) {
        this.debugger = debugger;
        this.stack = stack;
        this.thread = Thread.currentThread();
        this.reason = reason;
        this.hits = hits == null ? List.of() : List.copyOf(hits);
        this.exception = exception;
        this.context = debugger.currentContext();
        final List<DebugFrame> list = new ArrayList<>(stack.depth());
        for (int i = 0; i < stack.depth(); i++) {
            list.add(new DebugFrameImpl(this, i, stack.frame(i)));
        }
        this.frames = Collections.unmodifiableList(list);
    }

    @Override
    public Thread thread() {
        return thread;
    }

    @Override
    public PauseReason reason() {
        return reason;
    }

    @Override
    public List<DebugFrame> frames() {
        return frames;
    }

    @Override
    public List<String> hitBreakpoints() {
        return hits;
    }

    @Override
    public Object exception() {
        return exception;
    }

    @Override
    public ExecutionContext context() {
        return context;
    }

    ExecutionContextImpl contextImpl() {
        return context;
    }

    @Override
    public boolean isResumed() {
        return resumed;
    }

    @Override
    public void resume() {
        finish(ShadowStack.StepMode.NONE);
    }

    @Override
    public void stepInto() {
        finish(ShadowStack.StepMode.INTO);
    }

    @Override
    public void stepOver() {
        finish(ShadowStack.StepMode.OVER);
    }

    @Override
    public void stepOut() {
        finish(ShadowStack.StepMode.OUT);
    }

    @Override
    public void terminate() {
        stack.terminating = true;
        finish(ShadowStack.StepMode.NONE);
    }

    private synchronized void finish(final ShadowStack.StepMode mode) {
        if (resumed) {
            return;
        }
        if (mode != ShadowStack.StepMode.NONE) {
            // read by the paused thread once it wakes; the queue hand-off orders the writes
            stack.step = mode;
            stack.stepDepth = stack.depth();
            stack.stepping = debugger;
            debugger.stepStarted();
        }
        resumed = true;
        queue.add(WAKE);
    }

    Runnable take() throws InterruptedException {
        return queue.take();
    }

    /** Fails whatever was queued after the resume. */
    void drain() {
        for (Runnable r; (r = queue.poll()) != null;) {
            if (r instanceof FutureTask<?> task) {
                task.cancel(false);
            }
        }
    }

    @Override
    public <T> T call(final Callable<T> operation) throws Exception {
        if (Thread.currentThread() == thread) {
            final boolean wasInCommand = stack.inCommand;
            stack.inCommand = true;
            try {
                return operation.call();
            } finally {
                stack.inCommand = wasInCommand;
            }
        }
        final FutureTask<T> task = new FutureTask<>(operation);
        synchronized (this) {
            if (resumed) {
                throw new IllegalStateException("the thread has resumed");
            }
            queue.add(task);
        }
        try {
            return task.get();
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        } catch (final java.util.concurrent.CancellationException e) {
            throw new IllegalStateException("the thread has resumed", e);
        }
    }
}
