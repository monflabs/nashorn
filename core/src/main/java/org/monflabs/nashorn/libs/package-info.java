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

/**
 * The standard libraries: what a script expects from its host beyond the
 * language, as {@link org.monflabs.nashorn.api.scripting.ScriptLibrary}
 * services the engine itself provides - the {@code host} library (timers,
 * {@code queueMicrotask}, {@code atob}/{@code btoa}) and the {@code fetch}
 * library ({@code fetch}, {@code Headers}, {@code Request}, {@code Response}).
 * Every engine has them unless {@code --libraries} says otherwise; either can
 * also be passed to the engine factory explicitly.
 *
 * @since 2017.0.0
 */
package org.monflabs.nashorn.libs;
