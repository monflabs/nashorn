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

import org.monflabs.nashorn.internal.objects.annotations.Attribute;
import org.monflabs.nashorn.internal.objects.annotations.Function;
import org.monflabs.nashorn.internal.objects.annotations.Property;
import org.monflabs.nashorn.internal.objects.annotations.ScriptClass;
import org.monflabs.nashorn.internal.objects.annotations.Where;
import org.monflabs.nashorn.internal.runtime.JSType;
import org.monflabs.nashorn.internal.runtime.PropertyMap;
import org.monflabs.nashorn.internal.runtime.ScriptObject;
import org.monflabs.nashorn.internal.runtime.ScriptRuntime;
import org.monflabs.nashorn.internal.runtime.Undefined;

import static org.monflabs.nashorn.internal.objects.NativeRegExp.advanceStringIndex;
import static org.monflabs.nashorn.internal.objects.NativeRegExp.lastIndex;
import static org.monflabs.nashorn.internal.objects.NativeRegExp.regExpExec;
import static org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;
import static org.monflabs.nashorn.internal.runtime.ECMAErrors.typeError;

/**
 * ES2020 22.2.7.1 %RegExpStringIteratorPrototype%, the iterator
 * {@code String.prototype.matchAll} / {@code RegExp.prototype[@@matchAll]}
 * produce over the matches of a global (or single-shot) regular expression.
 */
@ScriptClass("RegExpStringIterator")
public class RegExpStringIterator extends AbstractIterator {

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private final ScriptObject regexp;
    private final String iteratedString;
    private final boolean global;
    private final boolean unicode;
    private boolean done;
    private final Global global_;

    RegExpStringIterator(final ScriptObject regexp, final String iteratedString, final boolean global,
            final boolean unicode, final Global global_) {
        super(global_.getRegExpStringIteratorPrototype(), $nasgenmap$);
        this.regexp = regexp;
        this.iteratedString = iteratedString;
        this.global = global;
        this.unicode = unicode;
        this.global_ = global_;
    }

    /**
     * ES2020 22.2.7.1.1 %RegExpStringIteratorPrototype%.next()
     *
     * @param self the self reference
     * @param arg the argument
     * @return the next result
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 0)
    public static Object next(final Object self, final Object arg) {
        if (!(self instanceof RegExpStringIterator)) {
            throw typeError("not.a.regexp", ScriptRuntime.safeToString(self));
        }
        return ((RegExpStringIterator)self).next(arg);
    }

    @Override
    public String getClassName() {
        return "RegExp String Iterator";
    }

    @Override
    protected IteratorResult next(final Object arg) {
        if (done) {
            return makeResult(Undefined.getUndefined(), Boolean.TRUE, global_);
        }
        final ScriptObject match = regExpExec(regexp, iteratedString);
        if (match == null) {
            done = true;
            return makeResult(Undefined.getUndefined(), Boolean.TRUE, global_);
        }
        if (!global) {
            done = true;
            return makeResult(match, Boolean.FALSE, global_);
        }
        // In global mode an empty match must advance lastIndex, or the walk stalls.
        final String matched = JSType.toString(match.get(0));
        if (matched.isEmpty()) {
            regexp.set("lastIndex", (double)advanceStringIndex(iteratedString, lastIndex(regexp), unicode),
                    CALLSITE_STRICT);
        }
        return makeResult(match, Boolean.FALSE, global_);
    }

    /**
     * ES2020 22.2.7.1.2 %RegExpStringIteratorPrototype% [ @@toStringTag ].
     */
    @Property(where = Where.PROTOTYPE, attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE, name = "@@toStringTag")
    public static final String toStringTag = "RegExp String Iterator";

}
