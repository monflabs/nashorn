/*
 * Copyright (c) 2026, Philippe Riand. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * ES2018 object spread {...obj} in object literals and object rest {...rest}
 * in destructuring (declaration, parameters, assignment).
 *
 * @test
 * @run
 */

var a = { x: 1, y: 2 };
print("spread: " + JSON.stringify({ ...a, z: 3 }));
print("override: " + JSON.stringify({ ...a, x: 9 }));
print("skip null: " + JSON.stringify({ ...null, ...undefined, ok: 1 }));

// spread must work inside a function (not just at top level)
function clone(o) { return { ...o }; }
print("fn spread: " + JSON.stringify(clone(a)));

var { x, ...rest } = { x: 1, y: 2, z: 3 };
print("rest decl: x=" + x + " rest=" + JSON.stringify(rest));

function f({ x, ...rest }) { return x + " / " + JSON.stringify(rest); }
print("rest param: " + f({ x: 1, y: 2, z: 3 }));

var p, q;
({ x: p, ...q } = { x: 1, y: 2 });
print("rest assign: p=" + p + " q=" + JSON.stringify(q));

// a rest never runs an excluded getter, but a named read does
var log = [];
var g = { get x() { log.push("x"); return 1; }, get y() { log.push("y"); return 2; } };
var { x: gx, ...gr } = g;
print("getters: " + log.join(",") + " gr=" + JSON.stringify(gr));

try { eval("var {...r,} = {}"); print("FAIL"); } catch (e) { print("rest+comma throws: " + (e instanceof SyntaxError)); }
