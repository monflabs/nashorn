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

package org.monflabs.nashorn.internal.objects;

import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Constructor;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JobQueue;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.linker.Bootstrap;

/**
 * ECMAScript 2021 26.2 FinalizationRegistry: registers objects to have a cleanup
 * callback run - on the realm's event loop - after they are garbage collected.
 *
 * <p>Collection is detected with a {@link ReferenceQueue} drained by one shared
 * daemon thread, which posts each surviving cell's cleanup onto its realm's
 * {@link JobQueue} so the callback runs on the loop thread. Timing is
 * necessarily best-effort - it is whenever the collector reclaims the target.
 */
@ScriptClass("FinalizationRegistry")
public final class NativeFinalizationRegistry extends ScriptObject {

    /** Every registry's cells feed this one queue, drained by the reaper below. */
    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();

    static {
        final Thread reaper = new Thread(NativeFinalizationRegistry::reap, "nashorn-finalization-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    /** A registered target: weak on the target, so the registry never keeps it alive. */
    private static final class Cell extends WeakReference<Object> {
        private final NativeFinalizationRegistry registry;
        private final Object heldValue;
        // the unregister token is held weakly too (26.2.1.1) - it does not keep
        // the target registered once the token itself is gone
        private final WeakReference<Object> token;
        private volatile boolean live = true;

        Cell(final Object target, final Object heldValue, final Object token,
                final NativeFinalizationRegistry registry) {
            super(target, QUEUE);
            this.heldValue = heldValue;
            this.registry = registry;
            this.token = NativeWeakMap.canBeHeldWeakly(token) ? new WeakReference<>(token) : null;
        }
    }

    /** The cells this registry still holds - for unregister to find by token. */
    private final Set<Cell> cells = ConcurrentHashMap.newKeySet();
    private final Object cleanupCallback;
    private final JobQueue jobQueue;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeFinalizationRegistry(final ScriptObject proto, final PropertyMap map, final Object cleanupCallback) {
        super(proto, map);
        this.cleanupCallback = cleanupCallback;
        this.jobQueue = Global.instance().getJobQueue();
    }

    /** ES2021 26.2.3.4 FinalizationRegistry.prototype [ @@toStringTag ]. */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "FinalizationRegistry";

    /**
     * ES2021 26.2.1.1 FinalizationRegistry(cleanupCallback).
     *
     * @param isNew           whether the new operator was used
     * @param self            self reference
     * @param cleanupCallback the callback invoked with each collected target's held value
     * @return a new FinalizationRegistry
     */
    @Constructor(arity = 1)
    public static Object construct(final boolean isNew, final Object self, final Object cleanupCallback) {
        // Cleanup callbacks are delivered on the event loop; without it they
        // could never fire, so a registry is of no use and construction throws.
        Global.requireEventLoop("FinalizationRegistry");
        if (!isNew) {
            throw typeError("constructor.requires.new", "FinalizationRegistry");
        }
        if (!Bootstrap.isCallable(cleanupCallback)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(cleanupCallback));
        }
        final Global global = Global.instance();
        return new NativeFinalizationRegistry(global.getFinalizationRegistryPrototype(), $nasgenmap$, cleanupCallback);
    }

    /**
     * ES2021 26.2.3.2 FinalizationRegistry.prototype.register(target, heldValue [, unregisterToken]).
     *
     * @param self self reference
     * @param args target, held value, and optional unregister token
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object register(final Object self, final Object... args) {
        final NativeFinalizationRegistry registry = registry(self);
        final Object target = args.length > 0 ? args[0] : ScriptRuntime.UNDEFINED;
        final Object heldValue = args.length > 1 ? args[1] : ScriptRuntime.UNDEFINED;
        final Object token = args.length > 2 ? args[2] : ScriptRuntime.UNDEFINED;

        if (!NativeWeakMap.canBeHeldWeakly(target)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(target));
        }
        // 26.2.3.2 step 4: a target may not be its own held value
        if (target == heldValue) {
            throw typeError("finalization.target.held.same");
        }
        // step 5: an unregister token, if given, must be an object
        if (token != ScriptRuntime.UNDEFINED && !NativeWeakMap.canBeHeldWeakly(token)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(token));
        }
        registry.cells.add(new Cell(target, heldValue, token, registry));
        return ScriptRuntime.UNDEFINED;
    }

    /**
     * ES2021 26.2.3.3 FinalizationRegistry.prototype.unregister(unregisterToken).
     *
     * @param self  self reference
     * @param token the token given to a previous register
     * @return whether any registration was removed
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean unregister(final Object self, final Object token) {
        final NativeFinalizationRegistry registry = registry(self);
        if (!NativeWeakMap.canBeHeldWeakly(token)) {
            throw typeError("not.an.object", ScriptRuntime.safeToString(token));
        }
        boolean removed = false;
        for (final Cell cell : registry.cells) {
            if (cell.token != null && cell.token.get() == token) {
                cell.live = false;
                registry.cells.remove(cell);
                removed = true;
            }
        }
        return removed;
    }

    private static NativeFinalizationRegistry registry(final Object self) {
        if (self instanceof NativeFinalizationRegistry registry) {
            return registry;
        }
        throw typeError("not.a.finalization.registry", ScriptRuntime.safeToString(self));
    }

    /** The shared reaper: post each collected cell's cleanup onto its realm's loop. */
    private static void reap() {
        while (true) {
            try {
                final Cell cell = (Cell)QUEUE.remove();
                cell.registry.cells.remove(cell);
                if (!cell.live) {
                    continue;
                }
                cell.live = false;
                final NativeFinalizationRegistry registry = cell.registry;
                final Object heldValue = cell.heldValue;
                // run the callback on the realm's loop thread, not this one
                registry.jobQueue.post(() ->
                        ScriptRuntime.call(registry.cleanupCallback, ScriptRuntime.UNDEFINED, new Object[] { heldValue }),
                        false);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final Throwable ignored) {
                // a cleanup that throws must not kill the reaper
            }
        }
    }
}
