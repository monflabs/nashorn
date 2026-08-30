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

package org.monflabs.nashorn.internal.runtime;

import static org.monflabs.nashorn.internal.lookup.Lookup.MH;
import static org.monflabs.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import jdk.dynalink.CallSiteDescriptor;
import org.monflabs.nashorn.internal.objects.NativeSymbol;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import org.monflabs.nashorn.api.scripting.AbstractJSObject;
import org.monflabs.nashorn.api.scripting.ScriptObjectMirror;
import org.monflabs.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import org.monflabs.nashorn.internal.runtime.linker.NashornGuards;

/**
 * This class supports the handling of scope in a with body.
 *
 */
public final class WithObject extends Scope {
    private static final MethodHandle WITHEXPRESSIONGUARD    = findOwnMH("withExpressionGuard",  boolean.class, Object.class, PropertyMap.class, SwitchPoint[].class);
    private static final MethodHandle WITHEXPRESSIONFILTER   = findOwnMH("withFilterExpression", Object.class, Object.class);
    private static final MethodHandle WITHSCOPEFILTER        = findOwnMH("withFilterScope",      Object.class, Object.class);
    private static final MethodHandle BIND_TO_EXPRESSION_OBJ = findOwnMH("bindToExpression",     Object.class, Object.class, Object.class);
    private static final MethodHandle BIND_TO_EXPRESSION_FN  = findOwnMH("bindToExpression",     Object.class, ScriptFunction.class, Object.class);

    /** With expression object. */
    private final ScriptObject expression;

    /**
     * Constructor
     *
     * @param scope scope object
     * @param expression with expression
     */
    WithObject(final ScriptObject scope, final ScriptObject expression) {
        super(scope, null);
        this.expression = expression;
        setIsInternal();
    }

    /**
     * Delete a property based on a key.
     * @param key Any valid JavaScript value.
     * @param strict strict mode execution.
     * @return True if deleted.
     */
    @Override
    public boolean isBlockScope() {
        // the object a with names is not a variable environment either: a var
        // an eval declares inside it belongs to the function around it
        return true;
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final ScriptObject self = expression;
        final String propName = JSType.toString(key);

        final FindProperty find = self.findProperty(propName, true);

        if (find != null) {
            return self.delete(propName, strict);
        }

        return false;
    }


    @Override
    public GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request) {
        if (request.isCallSiteUnstable()) {
            // Fall back to megamorphic invocation which performs a complete lookup each time without further relinking.
            return super.lookup(desc, request);
        }

        GuardedInvocation link = null;
        final Operation op = desc.getOperation();

        assert op instanceof NamedOperation; // WithObject is a scope object, access is always named
        final String name = ((NamedOperation)op).getName().toString();

        // ES2015 8.1.1.2.1 HasBinding asks whether the object has the name
        // before it asks whether the name is unscopable, and the second question
        // is not asked at all when the answer to the first is no
        FindProperty find = isInternalName(name) ? null : expression.findProperty(name, true);
        if (find != null && (!find.getOwner().answersForName(name) || unscopable(name))) {
            find = null;
        }
        // 8.1.1.2.5 SetMutableBinding and 8.1.1.2.6 GetBindingValue each ask a
        // second time whether the object has the name. It is not a repeat of
        // what HasBinding asked: the @@unscopables read above runs script,
        // which may have deleted the property in the meantime, and a proxy is
        // entitled to be asked once for each of the two steps. Only strict code
        // acts on the answer - the two of them throw a ReferenceError where
        // sloppy code reads undefined and writes the property back.
        if (find != null && !expression.hasProperty(name, true)
                && NashornCallSiteDescriptor.isStrict(desc)) {
            find = null;
        }

        if (find != null) {
            link = expression.lookup(desc, request);
            if (link != null) {
                return fixExpressionCallSite(desc, link);
            }
        }

        final ScriptObject scope = getProto();
        find = scope.findProperty(name, true);

        if (find != null) {
            return fixScopeCallSite(scope.lookup(desc, request), name, find.getOwner());
        }

        // the property is not found - now check for
        // __noSuchProperty__ and __noSuchMethod__ in expression
        final String fallBack;

        final Operation firstOp = NashornCallSiteDescriptor.getBaseOperation(desc);
        if (firstOp == StandardOperation.GET) {
            if (NashornCallSiteDescriptor.isMethodFirstOperation(desc)) {
                fallBack = NO_SUCH_METHOD_NAME;
            } else {
                fallBack = NO_SUCH_PROPERTY_NAME;
            }
        } else {
            fallBack = null;
        }

        if (fallBack != null) {
            find = expression.findProperty(fallBack, true);
            if (find != null && find.getOwner().answersForName(fallBack)) {
                if (NO_SUCH_METHOD_NAME.equals(fallBack)) {
                    link = expression.noSuchMethod(desc, request).addSwitchPoint(getProtoSwitchPoint(name));
                } else if (NO_SUCH_PROPERTY_NAME.equals(fallBack)) {
                    link = expression.noSuchProperty(desc, request).addSwitchPoint(getProtoSwitchPoint(name));
                }
            }
        }

        if (link != null) {
            return fixExpressionCallSite(desc, link);
        }

        // still not found, may be scope can handle with it's own
        // __noSuchProperty__, __noSuchMethod__ etc.
        link = scope.lookup(desc, request);

        if (link != null) {
            return fixScopeCallSite(link, name, null);
        }

        return null;
    }

    /**
     * Overridden to try to find the property first in the expression object (and its prototypes), and only then in this
     * object (and its prototypes).
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     * @param isScope true if is this a scope access
     * @param start the object on which the lookup was originally initiated
     * @return FindPropertyData or null if not found.
     */
    @Override
    protected FindProperty findProperty(final Object key, final boolean deep, final boolean isScope, final ScriptObject start) {
        if (isInternalName(key)) {
            // a name the compiler made for itself - a destructuring temporary,
            // an iterator - is not one a program can write, so a with block
            // never binds it and is never asked about it
            return super.findProperty(key, deep, isScope, start);
        }
        // We call findProperty on 'expression' with 'expression' itself as start parameter.
        // This way in ScriptObject.setObject we can tell the property is from a 'with' expression
        // (as opposed from another non-scope object in the proto chain such as Object.prototype).
        final FindProperty exprProperty = expression.findProperty(key, true, false, expression);
        if (exprProperty != null && exprProperty.getOwner().answersForName(key) && !unscopable(key)) {
            return exprProperty;
        }
        return super.findProperty(key, deep, isScope, start);
    }

    /** Whether a name is one the compiler made rather than one a program wrote. */
    private static boolean isInternalName(final Object key) {
        return key instanceof String name && !name.isEmpty() && name.charAt(0) == ':';
    }

    /**
     * ES2015 8.1.1.2.1 HasBinding: an object can hide names from a with block
     * through its @@unscopables, which is how Array.prototype's own additions
     * were made not to shadow a variable in code written before them.
     *
     * The lookup only happens once some script has installed the symbol
     * somewhere, which is what Array.prototype's own does at start-up - so the
     * check costs a property read on a with block, and with blocks are already
     * the slow way to write anything.
     */
    private boolean unscopable(final Object key) {
        if (!(key instanceof String name)) {
            return false;
        }
        // found rather than read: reading a property an object does not have
        // calls its __noSuchProperty__, which has no business being asked about
        // a symbol it never mentioned
        final FindProperty found = expression.findProperty(NativeSymbol.unscopables, true);
        if (found == null) {
            return false;
        }
        return found.getObjectValue() instanceof ScriptObject list && JSType.toBoolean(list.get(name));
    }

    @Override
    protected Object invokeNoSuchProperty(final Object key, final boolean isScope, final int programPoint) {
        final FindProperty find = expression.findProperty(NO_SUCH_PROPERTY_NAME, true);
        if (find != null) {
            final Object func = find.getObjectValue();
            if (func instanceof ScriptFunction) {
                final ScriptFunction sfunc = (ScriptFunction)func;
                final Object self = isScope && sfunc.isStrict()? UNDEFINED : expression;
                return ScriptRuntime.apply(sfunc, self, key);
            }
        }

        return getProto().invokeNoSuchProperty(key, isScope, programPoint);
    }

    @Override
    public void setSplitState(final int state) {
        ((Scope) getNonWithParent()).setSplitState(state);
    }

    @Override
    public int getSplitState() {
        return ((Scope) getNonWithParent()).getSplitState();
    }

    @Override
    public void addBoundProperties(final ScriptObject source, final Property[] properties) {
        // Declared variables in nested eval go to first normal (non-with) parent scope.
        getNonWithParent().addBoundProperties(source, properties);
    }

    /**
     * Get first parent scope that is not an instance of WithObject.
     */
    private ScriptObject getNonWithParent() {
        ScriptObject proto = getProto();

        while (proto != null && proto instanceof WithObject) {
            proto = proto.getProto();
        }

        return proto;
    }

    private static GuardedInvocation fixReceiverType(final GuardedInvocation link, final MethodHandle filter) {
        // The receiver may be an Object or a ScriptObject.
        final MethodType invType = link.getInvocation().type();
        final MethodType newInvType = invType.changeParameterType(0, filter.type().returnType());
        return link.asType(newInvType);
    }

    private static GuardedInvocation fixExpressionCallSite(final CallSiteDescriptor desc, final GuardedInvocation link) {
        // If it's not a getMethod, just add an expression filter that converts WithObject in "this" position to its
        // expression.
        if (NashornCallSiteDescriptor.getBaseOperation(desc) != StandardOperation.GET || !NashornCallSiteDescriptor.isMethodFirstOperation(desc)) {
            return fixReceiverType(link, WITHEXPRESSIONFILTER).filterArguments(0, WITHEXPRESSIONFILTER);
        }

        final MethodHandle linkInvocation      = link.getInvocation();
        final MethodType   linkType            = linkInvocation.type();
        final boolean      linkReturnsFunction = ScriptFunction.class.isAssignableFrom(linkType.returnType());

        return link.replaceMethods(
                // Make sure getMethod will bind the script functions it receives to WithObject.expression
                MH.foldArguments(
                        linkReturnsFunction ?
                                BIND_TO_EXPRESSION_FN :
                                BIND_TO_EXPRESSION_OBJ,
                        filterReceiver(
                                linkInvocation.asType(
                                        linkType.changeReturnType(
                                                linkReturnsFunction ?
                                                        ScriptFunction.class :
                                                        Object.class).
                                                            changeParameterType(
                                                                    0,
                                                                    Object.class)),
                                        WITHEXPRESSIONFILTER)),
                         filterGuardReceiver(link, WITHEXPRESSIONFILTER));
     // No clever things for the guard -- it is still identically filtered.

    }

    private GuardedInvocation fixScopeCallSite(final GuardedInvocation link, final String name, final ScriptObject owner) {
        final GuardedInvocation newLink             = fixReceiverType(link, WITHSCOPEFILTER);
        final MethodHandle      expressionGuard     = expressionGuard(name, owner);
        final MethodHandle      filteredGuard       = filterGuardReceiver(newLink, WITHSCOPEFILTER);
        return link.replaceMethods(
                filterReceiver(
                        newLink.getInvocation(),
                        WITHSCOPEFILTER),
                NashornGuards.combineGuards(
                        expressionGuard,
                        filteredGuard));
    }

    private static MethodHandle filterGuardReceiver(final GuardedInvocation link, final MethodHandle receiverFilter) {
        final MethodHandle test = link.getGuard();
        if (test == null) {
            return null;
        }

        final Class<?> receiverType = test.type().parameterType(0);
        final MethodHandle filter = MH.asType(receiverFilter,
                receiverFilter.type().changeParameterType(0, receiverType).
                changeReturnType(receiverType));

        return filterReceiver(test, filter);
    }

    private static MethodHandle filterReceiver(final MethodHandle mh, final MethodHandle receiverFilter) {
        //With expression filter == receiverFilter, i.e. receiver is cast to withobject and its expression returned
        return MH.filterArguments(mh, 0, receiverFilter.asType(receiverFilter.type().changeReturnType(mh.type().parameterType(0))));
    }

    /**
     * Drops the WithObject wrapper from the expression.
     * @param receiver WithObject wrapper.
     * @return The with expression.
     */
    public static Object withFilterExpression(final Object receiver) {
        return ((WithObject)receiver).expression;
    }

    @SuppressWarnings("unused")
    private static Object bindToExpression(final Object fn, final Object receiver) {
        if (fn instanceof ScriptFunction) {
            return bindToExpression((ScriptFunction) fn, receiver);
        } else if (fn instanceof ScriptObjectMirror) {
            final ScriptObjectMirror mirror = (ScriptObjectMirror)fn;
            if (mirror.isFunction()) {
                // We need to make sure correct 'this' is used for calls with Ident call
                // expressions. We do so here using an AbstractJSObject instance.
                return new AbstractJSObject() {
                    @Override
                    public Object call(final Object thiz, final Object... args) {
                        return mirror.call(withFilterExpression(receiver), args);
                    }
                };
            }
        }

        return fn;
    }

    private static Object bindToExpression(final ScriptFunction fn, final Object receiver) {
        return fn.createBound(withFilterExpression(receiver), ScriptRuntime.EMPTY_ARRAY);
    }

    private MethodHandle expressionGuard(final String name, final ScriptObject owner) {
        final PropertyMap map = expression.getMap();
        final SwitchPoint[] sp = expression.getProtoSwitchPoints(name, owner);
        return MH.insertArguments(WITHEXPRESSIONGUARD, 1, map, sp);
    }

    @SuppressWarnings("unused")
    private static boolean withExpressionGuard(final Object receiver, final PropertyMap map, final SwitchPoint[] sp) {
        return ((WithObject)receiver).expression.getMap() == map && !hasBeenInvalidated(sp);
    }

    private static boolean hasBeenInvalidated(final SwitchPoint[] switchPoints) {
        if (switchPoints != null) {
            for (final SwitchPoint switchPoint : switchPoints) {
                if (switchPoint.hasBeenInvalidated()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Drops the WithObject wrapper from the scope.
     * @param receiver WithObject wrapper.
     * @return The with scope.
     */
    public static Object withFilterScope(final Object receiver) {
        return ((WithObject)receiver).getProto();
    }

    /**
     * Get the with expression for this {@code WithObject}
     * @return the with expression
     */
    public ScriptObject getExpression() {
        return expression;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), WithObject.class, name, MH.type(rtype, types));
    }
}
