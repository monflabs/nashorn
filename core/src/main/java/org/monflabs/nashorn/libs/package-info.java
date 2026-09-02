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

/**
 * The standard libraries: what a script expects from its host beyond the
 * language, as {@link org.monflabs.nashorn.api.scripting.ScriptLibrary}
 * implementations - the {@code host} library (timers, {@code queueMicrotask},
 * {@code atob}/{@code btoa}) and the {@code fetch} library ({@code fetch},
 * {@code Headers}, {@code Request}, {@code Response}). Neither is installed
 * automatically; hand the one you want to the engine builder's
 * {@code library(...)}.
 *
 * @since 2017.0.0
 */
package org.monflabs.nashorn.libs;
