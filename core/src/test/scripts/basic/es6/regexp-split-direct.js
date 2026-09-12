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
 * String.prototype.split over a regular expression runs the direct matcher
 * when nothing observable can intervene, and the property-driven 22.2.6.14
 * algorithm when something can. Every case below is written so that the two
 * must agree, or so that the guard is forced off and the generic route has to
 * take over: a subclass, a replaced species, a replaced exec, a replaced flags
 * getter, an own exec on the instance. The direct route never touches the
 * receiver's lastIndex, which one case pins, and it must coerce its limit
 * exactly once.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

print("captures:", JSON.stringify("a1b22c".split(/(\d)(\d)?/)));
print("no match:", JSON.stringify("abc".split(/x/)));
print("empty subject:", JSON.stringify("".split(/x/)), JSON.stringify("".split(/(?:)/)));
print("empty match:", JSON.stringify("abc".split(/(?:)/)));
print("limit 0:", JSON.stringify("a,b".split(/,/, 0)));
print("limit cuts a capture:", JSON.stringify("a1b2c".split(/(\d)/, 2)));
print("leading and trailing:", JSON.stringify(",a,".split(/,/)));
print("code points:", JSON.stringify("a\u{1F600}b".split(/(?:)/u)));

var counted = 0;
var limit = { valueOf: function () { counted++; return 3; } };
print("limit coerced once:", JSON.stringify("a,b,c,d".split(/,/, limit)), counted);

var re = /,/;
re.lastIndex = 7;
"a,b,c".split(re);
print("receiver lastIndex untouched:", re.lastIndex);

// each of these makes the direct route unsafe, so the generic one must run
function withSpecies() {
    var saved = Object.getOwnPropertyDescriptor(RegExp, Symbol.species);
    var seen = 0;
    Object.defineProperty(RegExp, Symbol.species, {
        configurable: true,
        get: function () { seen++; return RegExp; }
    });
    var result = "a,b".split(/,/);
    Object.defineProperty(RegExp, Symbol.species, saved);
    return JSON.stringify(result) + " species read " + (seen > 0);
}
print("replaced species:", withSpecies());

function withExec() {
    var saved = RegExp.prototype.exec;
    var seen = 0;
    RegExp.prototype.exec = function (s) { seen++; return saved.call(this, s); };
    var result = "a,b".split(/,/);
    RegExp.prototype.exec = saved;
    return JSON.stringify(result) + " exec called " + (seen > 0);
}
print("replaced exec:", withExec());

function withOwnExec() {
    var own = /,/;
    var seen = 0;
    own.exec = function (s) { seen++; return RegExp.prototype.exec.call(this, s); };
    var result = "a,b".split(own);
    return JSON.stringify(result) + " own exec called " + (seen > 0);
}
print("own exec:", withOwnExec());

function withFlags() {
    var saved = Object.getOwnPropertyDescriptor(RegExp.prototype, "flags");
    var seen = 0;
    Object.defineProperty(RegExp.prototype, "flags", {
        configurable: true,
        get: function () { seen++; return saved.get.call(this); }
    });
    var result = "a,b".split(/,/);
    Object.defineProperty(RegExp.prototype, "flags", saved);
    return JSON.stringify(result) + " flags read " + (seen > 0);
}
print("replaced flags:", withFlags());

class Sub extends RegExp {}
print("subclass:", JSON.stringify("a,b,c".split(new Sub(","))));

// a lastIndex made non-writable is a TypeError on a global match, and the
// answer must not depend on a match having been run before it was frozen
var frozen = /a/g;
frozen.exec("aaa");
Object.defineProperty(frozen, "lastIndex", { writable: false });
try {
    frozen.exec("aaa");
    print("frozen lastIndex:", "no error");
} catch (e) {
    print("frozen lastIndex:", e instanceof TypeError);
}

print("legacy statics:", "x1y".split(/(\d)/).length, RegExp.$1);

// Annex B compile() can change the pattern from inside the limit's valueOf,
// which 22.2.6.14 runs only after it has fixed what to match with
var recompiled = /a/;
var lateLimit = { valueOf: function () { recompiled.compile("b"); return -1; } };
print("recompiled mid-split:", JSON.stringify(recompiled[Symbol.split]("abba", lateLimit)));
var recompiled2 = /a/;
var lateLimit2 = { valueOf: function () { recompiled2.compile("b"); return -1; } };
print("recompiled mid-split via String:", JSON.stringify("abba".split(recompiled2, lateLimit2)));
