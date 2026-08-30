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

package org.monflabs.nashorn.internal.objects;

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.GeneratorSupport;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;

/**
 * The object a generator function returns.
 *
 * It is a thin shell over {@link GeneratorSupport}, which owns the suspended
 * body; this class only turns the three operations of the iterator protocol into
 * the {value, done} objects the protocol expects.
 */
@ScriptClass("Generator")
public final class NativeGenerator extends ScriptObject {
    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private final GeneratorSupport support;
    private final Global global;

    /** Unwinds generators nobody can advance any more, so their threads can exit. */
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();

    public NativeGenerator(final GeneratorSupport support, final Global global, final ScriptObject prototype) {
        super(prototype, $nasgenmap$);
        this.support = support;
        this.global = global;
        // the action must not capture this, or the generator is never collected
        CLEANER.register(this, support::abandon);
    }

    /**
     * The body this generator advances.
     *
     * @return the support object running it
     */
    public GeneratorSupport getSupport() {
        return support;
    }

    @Override
    public String getClassName() {
        return "Generator";
    }

    /**
     * ECMAScript 2015 25.3.1.2 Generator.prototype.next(value)
     *
     * @param self  the generator
     * @param value what to resume with
     * @return an iterator result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object next(final Object self, final Object value) {
        final NativeGenerator generator = check(self);
        return generator.result(generator.support.next(value));
    }

    /**
     * ECMAScript 2015 25.3.1.3 Generator.prototype.return(value)
     *
     * The body is unwound rather than abandoned, so its finally blocks run.
     *
     * @param self  the generator
     * @param value the value to finish with
     * @return an iterator result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "return")
    public static Object _return(final Object self, final Object value) {
        final NativeGenerator generator = check(self);
        return generator.result(generator.support.doReturn(value));
    }

    /**
     * ECMAScript 2015 25.3.1.4 Generator.prototype.throw(exception)
     *
     * @param self      the generator
     * @param exception the value to throw at the suspension point
     * @return an iterator result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "throw")
    public static Object _throw(final Object self, final Object exception) {
        final NativeGenerator generator = check(self);
        return generator.result(generator.support.doThrow(exception));
    }

    private ScriptObject result(final Object[] step) {
        if (step.length > 2 && step[0] instanceof ScriptObject delegated) {
            // yield* hands the inner iterator's own result object through
            return delegated;
        }
        return new IteratorResult(step[0], (Boolean)step[1], global);
    }

    private static NativeGenerator check(final Object self) {
        if (self instanceof NativeGenerator generator) {
            return generator;
        }
        throw org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError(
                "not.a.generator", ScriptRuntime.safeToString(self));
    }

    /**
     * ES2015 25.3.1.5 %GeneratorPrototype% [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "Generator";

}
