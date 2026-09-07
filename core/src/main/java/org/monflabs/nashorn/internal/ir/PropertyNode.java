/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Philippe Riand.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Modifications beginning 2026-08-17 by Philippe Riand:
 * moved to a new package and adapted for Nashorn-monflabs.
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

package org.monflabs.nashorn.internal.ir;

import org.monflabs.nashorn.internal.ir.annotations.Immutable;
import org.monflabs.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of an object literal property.
 */
@Immutable
public final class PropertyNode extends Node {
    private static final long serialVersionUID = 1L;

    /** Property key. */
    private final Expression key;

    /** Property value. */
    private final Expression value;

    /** Property getter. */
    private final FunctionNode getter;

    /** Property getter. */
    private final FunctionNode setter;

    /** static property flag */
    private final boolean isStatic;

    /** Computed property flag */
    private final boolean computed;

    /** A method, accessor, or ordinary object-literal property. */
    public static final int KIND_NORMAL = 0;
    /** ES2022 class field: {@link #getValue()} is its initializer (or null). */
    public static final int KIND_FIELD = 1;
    /** ES2022 static initializer block: {@link #getValue()} is the block as a function. */
    public static final int KIND_STATIC_BLOCK = 2;

    /** Which of the kinds above this element is. */
    private final int kind;

    /** ES2022 private class element ({@code #x})? */
    private final boolean isPrivate;

    /** For a private element, a read of the synthetic binding that holds its PrivateName; else null. */
    private final Expression privateNameBinding;

    /**
     * Constructor for an ordinary property or a public method/accessor.
     *
     * @param token   token
     * @param finish  finish
     * @param key     the key of this property
     * @param value   the value of this property
     * @param getter  getter function body
     * @param setter  setter function body
     * @param isStatic is this a static property?
     * @param computed is this a computed property?
     */
    public PropertyNode(final long token, final int finish, final Expression key, final Expression value, final FunctionNode getter, final FunctionNode setter, final boolean isStatic, final boolean computed) {
        this(token, finish, key, value, getter, setter, isStatic, computed, KIND_NORMAL, false, null);
    }

    /**
     * Constructor for a class element, carrying the ES2022 kind and privacy.
     *
     * @param token   token
     * @param finish  finish
     * @param key     the key of this property (null for a static block)
     * @param value   the value: a method function, a field initializer, or a static block function
     * @param getter  getter function body
     * @param setter  setter function body
     * @param isStatic is this a static element?
     * @param computed is the key computed?
     * @param kind    one of {@link #KIND_NORMAL}, {@link #KIND_FIELD}, {@link #KIND_STATIC_BLOCK}
     * @param isPrivate is this a private element?
     * @param privateNameBinding read of the binding holding the PrivateName, or null
     */
    public PropertyNode(final long token, final int finish, final Expression key, final Expression value, final FunctionNode getter, final FunctionNode setter, final boolean isStatic, final boolean computed, final int kind, final boolean isPrivate, final Expression privateNameBinding) {
        super(token, finish);
        this.key    = key;
        this.value  = value;
        this.getter = getter;
        this.setter = setter;
        this.isStatic = isStatic;
        this.computed = computed;
        this.kind = kind;
        this.isPrivate = isPrivate;
        this.privateNameBinding = privateNameBinding;
    }

    private PropertyNode(final PropertyNode propertyNode, final Expression key, final Expression value, final FunctionNode getter, final FunctionNode setter, final boolean isStatic, final boolean computed, final Expression privateNameBinding) {
        super(propertyNode);
        this.key    = key;
        this.value  = value;
        this.getter = getter;
        this.setter = setter;
        this.isStatic = isStatic;
        this.computed = computed;
        this.kind = propertyNode.kind;
        this.isPrivate = propertyNode.isPrivate;
        this.privateNameBinding = privateNameBinding;
    }

    /**
     * Get the name of the property key, or {@code null} if key is a computed name.
     * @return key name or null
     */
    public String getKeyName() {
        return !computed && key instanceof PropertyKey ? ((PropertyKey) key).getPropertyName() : null;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterPropertyNode(this)) {
            return visitor.leavePropertyNode(
                setKey(key == null ? null : (Expression) key.accept(visitor)).
                setValue(value == null ? null : (Expression)value.accept(visitor)).
                setGetter(getter == null ? null : (FunctionNode)getter.accept(visitor)).
                setSetter(setter == null ? null : (FunctionNode)setter.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        if (value instanceof FunctionNode && ((FunctionNode)value).getIdent() != null) {
            value.toString(sb);
        }

        if (value != null && key != null) {
            key.toString(sb, printType);
            sb.append(": ");
            value.toString(sb, printType);
        }

        if (getter != null) {
            sb.append(' ');
            getter.toString(sb, printType);
        }

        if (setter != null) {
            sb.append(' ');
            setter.toString(sb, printType);
        }
    }

    /**
     * Get the getter for this property
     * @return getter or null if none exists
     */
    public FunctionNode getGetter() {
        return getter;
    }

    /**
     * Set the getter of this property, null if none
     * @param getter getter
     * @return same node or new node if state changed
     */
    public PropertyNode setGetter(final FunctionNode getter) {
        if (this.getter == getter) {
            return this;
        }
        return new PropertyNode(this, key, value, getter, setter, isStatic, computed, privateNameBinding);
    }

    /**
     * Return the key for this property node
     * @return the key
     */
    public Expression getKey() {
        return key;
    }

    private PropertyNode setKey(final Expression key) {
        if (this.key == key) {
            return this;
        }
        return new PropertyNode(this, key, value, getter, setter, isStatic, computed, privateNameBinding);
    }

    /**
     * Get the setter for this property
     * @return setter or null if none exists
     */
    public FunctionNode getSetter() {
        return setter;
    }

    /**
     * Set the setter for this property, null if none
     * @param setter setter
     * @return same node or new node if state changed
     */
    public PropertyNode setSetter(final FunctionNode setter) {
        if (this.setter == setter) {
            return this;
        }
        return new PropertyNode(this, key, value, getter, setter, isStatic, computed, privateNameBinding);
    }

    /**
     * Get the value of this property
     * @return property value
     */
    public Expression getValue() {
        return value;
    }

    /**
     * Set the value of this property
     * @param value new value
     * @return same node or new node if state changed
     */
    public PropertyNode setValue(final Expression value) {
        if (this.value == value) {
            return this;
        }
        return new PropertyNode(this, key, value, getter, setter, isStatic, computed, privateNameBinding);
    }

    /**
     * Returns true if this is a static property.
     *
     * @return true if static flag is set
     */
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * Returns true if this is a computed property.
     *
     * @return true if the computed flag is set
     */
    public boolean isComputed() {
        return computed;
    }

    /**
     * The ES2022 element kind.
     * @return one of {@link #KIND_NORMAL}, {@link #KIND_FIELD}, {@link #KIND_STATIC_BLOCK}
     */
    public int getKind() {
        return kind;
    }

    /**
     * @return true if this is a class field
     */
    public boolean isField() {
        return kind == KIND_FIELD;
    }

    /**
     * @return true if this is a static initializer block
     */
    public boolean isStaticBlock() {
        return kind == KIND_STATIC_BLOCK;
    }

    /**
     * @return true if this is a private class element ({@code #x})
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * For a private element, a read of the synthetic binding holding its
     * PrivateName; null for a public one.
     * @return the private-name binding read, or null
     */
    public Expression getPrivateNameBinding() {
        return privateNameBinding;
    }

    /**
     * Set the private-name binding read.
     * @param privateNameBinding the binding read
     * @return same node or new node if state changed
     */
    public PropertyNode setPrivateNameBinding(final Expression privateNameBinding) {
        if (this.privateNameBinding == privateNameBinding) {
            return this;
        }
        return new PropertyNode(this, key, value, getter, setter, isStatic, computed, privateNameBinding);
    }
}
