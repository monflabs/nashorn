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
 * String.prototype.match and replace, and the @@match and @@replace they are
 * defined in terms of, run the direct matcher for an ordinary regular
 * expression and the property-driven algorithm for anything else. What this
 * pins is that the answer does not depend on which one runs.
 *
 * The switch between them is not local: storing any of the five well-known
 * string symbols as a key anywhere in the process latches a flag that sends
 * every String.prototype.match and replace through the symbol protocol from
 * then on, for the life of the JVM. So each case runs twice, once on either
 * side of that latch, and the two must agree.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

function cases() {
    var out = [];
    out.push(JSON.stringify("a1b22c".match(/\d/g)));
    out.push(JSON.stringify("a1b22c".match(/(\d)(\d)?/)));
    out.push(JSON.stringify("abc".match(/x/g)));
    out.push(JSON.stringify("abc".match(/(?:)/g)));
    out.push(JSON.stringify("a\u{1F600}b".match(/(?:)/gu)));
    out.push("a1b2".replace(/\d/g, "#"));
    out.push("John Smith".replace(/(\w+)\s(\w+)/, "$2 $1"));
    out.push("2026-09-12".replace(/(?<y>\d{4})-(?<m>\d\d)-(?<d>\d\d)/, "$<d>/$<m>/$<y>"));
    out.push("a1b2".replace(/(\d)/g, function (m, c, off) { return "[" + c + "@" + off + "]"; }));
    out.push("abc".replace(/(?:)/g, "-"));
    // an empty match steps a whole code point, so no dash lands inside the pair
    out.push("a\u{1F600}b".replace(/(?:)/gu, "-"));
    out.push("abc".replace(/b/, "[$&|$`|$']"));
    out.push("a.b.c".replace(".", "!"));
    var sloppyThis = "x".replace(/x/, function () { return this === globalThis ? "global" : "other"; });
    out.push(sloppyThis);
    out.push("x".replace(/x/, function () { "use strict"; return this === undefined ? "undefined" : "other"; }));
    var re = /a/g;
    re.lastIndex = 3;
    "aaa".match(re);
    out.push("match leaves lastIndex " + re.lastIndex);
    var re2 = /a/g;
    re2.lastIndex = 3;
    "aaa".replace(re2, "b");
    out.push("replace leaves lastIndex " + re2.lastIndex);
    return out;
}

var before = cases();

// this single assignment is what latches the flag, process-wide and for good
var latch = {};
latch[Symbol.match] = function () { return null; };

var after = cases();

for (var i = 0; i < before.length; i++) {
    if (before[i] !== after[i]) {
        print("DIFFERS at " + i + ": " + before[i] + " vs " + after[i]);
    }
}
print("cases:", before.length);
before.forEach(function (line) { print("  " + line); });

// and the cases that must take the generic route either way
class Sub extends RegExp {}
print("subclass match:", JSON.stringify("a1b".match(new Sub("\\d", "g"))));
var ownExec = /x/g;
ownExec.exec = function () { return null; };
print("own exec honoured:", "xxx".match(ownExec));
var replaced = 0;
var protoExec = RegExp.prototype.exec;
RegExp.prototype.exec = function (s) { replaced++; return protoExec.call(this, s); };
var viaProto = "a1".replace(/\d/, "#");
RegExp.prototype.exec = protoExec;
print("replaced exec honoured:", viaProto, replaced > 0);
