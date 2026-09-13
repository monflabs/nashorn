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
 * SpeciesConstructor's default is the %RegExp% intrinsic, not the global
 * "RegExp" binding.
 *
 * ES2026 7.3.24 takes the default constructor from its caller, and
 * RegExp.prototype [ @@split ] and its siblings name the intrinsic. So
 * shadowing the global name must not redirect what a split, match or replace
 * builds its matcher from - and must not turn off the direct paths either,
 * which is what v8's own regexp.js benchmark does in its first line.
 *
 * @test
 * @run
 */

function check(what, actual, expected) {
    if (actual !== expected) {
        throw new Error(what + ": expected " + expected + " but got " + actual);
    }
}

var s = "a1b2c3";

// the ordinary answers, before anything is shadowed
check("split", s.split(/\d/).join("|"), "a|b|c|");
check("match", s.match(/\d/g).join("|"), "1|2|3");
check("replace", s.replace(/\d/g, "-"), "a-b-c-");
check("search", s.search(/2/), 3);

// shadow the global binding with something that is not a constructor at all.
// Every algorithm above must behave exactly as before: none of them reads it.
var RegExpIntrinsic = RegExp;
RegExp = { notAConstructor: true };

check("split after shadowing", s.split(/\d/).join("|"), "a|b|c|");
check("match after shadowing", s.match(/\d/g).join("|"), "1|2|3");
check("replace after shadowing", s.replace(/\d/g, "-"), "a-b-c-");
check("search after shadowing", s.search(/2/), 3);
check("matchAll after shadowing",
      Array.from(s.matchAll(/\d/g), function (m) { return m[0]; }).join("|"), "1|2|3");

// a literal still constructs from the intrinsic, and instanceof still works
check("literal is an intrinsic RegExp", /x/ instanceof RegExpIntrinsic, true);

// Writing the global a second time is what invalidates the switch point the
// name "RegExp" shares with RegExp.prototype's own properties, so the answers
// have to survive a repeated write, not just the first one.
for (var i = 0; i < 3; i++) {
    RegExp = { round: i };
    check("split after write " + i, s.split(/\d/).join("|"), "a|b|c|");
    check("match after write " + i, s.match(/\d/g).join("|"), "1|2|3");
    check("replace after write " + i, s.replace(/\d/g, "-"), "a-b-c-");
    check("search after write " + i, s.search(/2/), 3);
}

// redefining what the algorithms *do* read must still be honoured
RegExp = RegExpIntrinsic;
var execCalls = 0;
var originalExec = RegExp.prototype.exec;
RegExp.prototype.exec = function (str) { execCalls++; return originalExec.call(this, str); };
check("replace through a patched exec", s.replace(/\d/g, "-"), "a-b-c-");
if (execCalls === 0) {
    throw new Error("a patched RegExp.prototype.exec was bypassed");
}
RegExp.prototype.exec = originalExec;

RegExp = RegExpIntrinsic;

// an explicit species is still honoured - the default is only a fallback
var calls = 0;
class MyRegExp extends RegExp {
    static get [Symbol.species]() { calls++; return RegExp; }
}
var re = new MyRegExp("\\d", "g");
check("subclass split", "a1b2".split(re).join("|"), "a|b|");
if (calls === 0) {
    throw new Error("the subclass's @@species was never consulted");
}

// 22.2.6.14 runs its loop while q < size and anchors each attempt at q, so a
// match at the very end of the subject is never reached. The direct walk
// searches forward instead, so it has to decline that position itself.
check("end-anchored split", JSON.stringify("x".split(/$/)), '["x"]');
check("end-anchored split, longer", JSON.stringify("ab".split(/$/)), '["ab"]');
check("end-anchored split, multiline", JSON.stringify("a\nb".split(/$/m)), '["a","\\nb"]');
check("empty subject", JSON.stringify("".split(/$/)), '[]');
check("trailing separator", JSON.stringify("a,b,".split(/,/)), '["a","b",""]');

print("regexp species intrinsic ok");
