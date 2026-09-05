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
 * ES2018 RegExp named capture groups, named backreferences, the .groups
 * object, $<name> in replace, and (bounded) lookbehind.
 *
 * @test
 * @run
 */

var m = "2024-01-15".match(/(?<y>\d{4})-(?<mo>\d{2})-(?<d>\d{2})/);
print("groups: " + m.groups.y + " " + m.groups.mo + " " + m.groups.d);
print("null proto: " + (Object.getPrototypeOf(m.groups) === null));
print("no groups: " + /(a)/.exec("a").groups);

print("backref: " + /(?<c>.)\k<c>/.test("aa") + " " + /(?<c>.)\k<c>/.test("ab"));
print("forward: " + JSON.stringify("bab".match(/\k<a>(?<a>b)\w\k<a>/)));
print("unicode name: " + "ab".match(/(?<π>a)b/u).groups["π"]);

print("replace: " + "2024-01-15".replace(/(?<y>\d+)-(?<mo>\d+)-(?<d>\d+)/, "$<d>/$<mo>/$<y>"));
print("lookbehind: " + "price $5".match(/(?<=\$)\d+/)[0]);
print("neg lookbehind: " + "a1b2".replace(/(?<!\d)\d/g, "#"));

try { new RegExp("(?<n>a)(?<n>b)"); print("FAIL dup"); } catch (e) { print("dup: " + (e instanceof SyntaxError)); }
try { new RegExp("(?<1bad>a)"); print("FAIL name"); } catch (e) { print("badname: " + (e instanceof SyntaxError)); }
