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

package org.openjdk.nashorn.internal.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.openjdk.nashorn.internal.objects.Global;

/**
 * Runs a generator body on its own virtual thread, so that {@code yield} can be
 * an ordinary blocking call.
 *
 * The alternative - rewriting the body into a state machine - founders on
 * Nashorn's IR, which has no goto, so try/finally would need a hand-built entry
 * table; it would have to synthesise functions, which breaks lazy recompilation;
 * and hoisting every local into a state object would make them all Object and
 * defeat optimistic typing. On a virtual thread the body compiles as an entirely
 * ordinary function, so arbitrary control flow, try/finally, labelled breaks and
 * deoptimisation all work with no special handling.
 *
 * Control is handed back and forth over two one-slot queues, so exactly one of
 * the two threads runs at a time and the generator is never concurrent.
 */
public final class GeneratorSupport {
    /**
     * Set on a generator's own thread and consumed by the first thing the body
     * does, so that exactly one call falls through into the body.
     *
     * It cannot simply stay set for the thread's lifetime: a generator body that
     * calls another generator function - which is what "yield* inner()" does -
     * would then run that one's body inline instead of getting a generator back.
     */
    private static final ThreadLocal<GeneratorSupport> ENTERING = new ThreadLocal<>();

    /** The generator whose body this thread is running, for yield to find. */
    private static final ThreadLocal<GeneratorSupport> RUNNING = new ThreadLocal<>();

    /** What the caller sends in: a value to resume with, or a request to finish. */
    private sealed interface Resume {
        record Next(Object value) implements Resume { }
        record Return(Object value) implements Resume { }
        record Throw(Object error) implements Resume { }
    }

    /** What the body sends back: a yielded value, a return, or a failure. */
    private sealed interface Step {
        record Yielded(Object value) implements Step { }
        record Returned(Object value) implements Step { }
        record Failed(RuntimeException error) implements Step { }
    }

    /** Thrown inside the body to unwind it when the caller calls return(). */
    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Object value;

        Abort(final Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private final BlockingQueue<Resume> toBody = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<Step> toCaller = new ArrayBlockingQueue<>(1);

    private final ScriptFunction body;
    private final Object self;
    private final Object[] args;
    private final Global global;

    private Thread thread;
    private boolean done;

    /**
     * @param body   the generator function, which is re-entered on the generator's thread
     * @param self   its this value
     * @param args   its arguments
     * @param global the realm the generator belongs to
     */
    public GeneratorSupport(final ScriptFunction body, final Object self, final Object[] args, final Global global) {
        this.body = body;
        this.self = self;
        this.args = args;
        this.global = global;
    }

    /**
     * Whether this call is the one that should run a generator body, rather than
     * create a generator. Consumes the marker, so only the first call on a
     * generator's thread says yes.
     *
     * @return true if the caller should fall through into the body
     */
    public static boolean entering() {
        if (ENTERING.get() == null) {
            return false;
        }
        ENTERING.remove();
        return true;
    }

    /**
     * The generator whose body the calling thread is running.
     *
     * @return the generator, or null outside any generator body
     */
    public static GeneratorSupport running() {
        return RUNNING.get();
    }

    /** Whether the generator has finished, by returning or throwing. */
    public boolean isDone() {
        return done;
    }

    /**
     * Suspends the body and hands a value to whoever is advancing the generator.
     *
     * @param value the yielded value
     * @return the value the generator is resumed with
     */
    public Object yield(final Object value) {
        put(toCaller, new Step.Yielded(value));
        final Resume resume = take(toBody);
        if (resume instanceof Resume.Return ret) {
            // return() unwinds the body so that its finally blocks run
            throw new Abort(ret.value());
        }
        if (resume instanceof Resume.Throw thrown) {
            throw ECMAException.create(thrown.error(), null, -1, -1);
        }
        return ((Resume.Next)resume).value();
    }

    /**
     * Advances the generator.
     *
     * @param resumeValue what to send in
     * @return the step the body reached
     */
    public Object[] next(final Object resumeValue) {
        return advance(new Resume.Next(resumeValue));
    }

    /**
     * Asks the generator to finish, running its finally blocks.
     *
     * @param value the value to return
     * @return the step the body reached
     */
    public Object[] doReturn(final Object value) {
        if (thread == null || done) {
            done = true;
            return new Object[] { value, Boolean.TRUE };
        }
        return advance(new Resume.Return(value));
    }

    /**
     * Throws into the generator at its suspension point.
     *
     * @param error the value to throw
     * @return the step the body reached
     */
    public Object[] doThrow(final Object error) {
        if (thread == null || done) {
            done = true;
            throw ECMAException.create(error, null, -1, -1);
        }
        return advance(new Resume.Throw(error));
    }

    /** Runs the body until it yields, returns or throws. Result is {value, done}. */
    private Object[] advance(final Resume resume) {
        if (done) {
            return new Object[] { ScriptRuntime.UNDEFINED, Boolean.TRUE };
        }
        if (thread == null) {
            start();
        } else {
            put(toBody, resume);
        }

        final Step step = take(toCaller);
        if (step instanceof Step.Yielded yielded) {
            return new Object[] { yielded.value(), Boolean.FALSE };
        }
        done = true;
        if (step instanceof Step.Failed failed) {
            throw failed.error();
        }
        return new Object[] { ((Step.Returned)step).value(), Boolean.TRUE };
    }

    private void start() {
        thread = Thread.ofVirtual().name("nashorn-generator").unstarted(() -> {
            ENTERING.set(this);
            RUNNING.set(this);
            // Context.getGlobal is a thread local, so the body's realm has to be
            // established on this thread before anything script-visible runs.
            final Global previous = Context.getGlobal();
            Context.setGlobal(global);
            try {
                put(toCaller, new Step.Returned(ScriptRuntime.apply(body, self, args)));
            } catch (final Abort abort) {
                put(toCaller, new Step.Returned(abort.value));
            } catch (final RuntimeException e) {
                put(toCaller, new Step.Failed(e));
            } finally {
                Context.setGlobal(previous);
                RUNNING.remove();
                ENTERING.remove();
            }
        });
        thread.start();
    }

    /**
     * Unwinds a generator nobody can advance any more.
     *
     * A suspended body owns a parked thread, and that thread keeps itself alive,
     * so an abandoned generator would leak one for the life of the program. When
     * the generator object becomes unreachable the body is asked to return,
     * which lets it unwind and its thread exit.
     *
     * The observable consequence is that a finally block in an abandoned
     * generator runs at collection time rather than never - which the spec does
     * not describe, but which no program can distinguish from never having run,
     * since nothing holds a reference to observe it with.
     */
    public void abandon() {
        if (thread == null || done) {
            return;
        }
        done = true;
        // offer, not put: if the body is not waiting there is nothing to unwind
        toBody.offer(new Resume.Return(ScriptRuntime.UNDEFINED));
    }

    private static <T> void put(final BlockingQueue<T> queue, final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("generator handoff interrupted", e);
        }
    }

    private static <T> T take(final BlockingQueue<T> queue) {
        try {
            return queue.take();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("generator handoff interrupted", e);
        }
    }
}
