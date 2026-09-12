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
 * Two paths that skip work a regular expression does not need.
 *
 * A test never looks at the captures, so its match is kept as a region and the
 * substrings are cut only if the legacy statics are read afterwards - which
 * they must still answer correctly, including for a group that did not
 * participate and one inside a negative lookahead. A pattern with named groups
 * or the d flag keeps the eager form, since the group object and the indices
 * need the captures anyway.
 *
 * And String.prototype.replace over a search string searches for the string:
 * it used to escape it into a pattern and run the regexp engine to find what
 * indexOf finds. The legacy statics are left alone by that one, because there
 * is no RegExpBuiltinExec in it.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

print("statics after test:", /(\w+)=(\w+)/.test("foo=bar"), RegExp.$1, RegExp.$2, RegExp.lastMatch);
print("optional group:", /(a)(b)?/.test("a"), RegExp.$1, String(RegExp.$2));
print("negative lookahead:", /(?!(x))y/.test("y"), String(RegExp.$1));
print("lastParen:", /(\w)(\d)/.test("a1"), RegExp.lastParen);
print("named groups keep the eager form:", /(?<k>\w+)=/.test("a=1"), RegExp.$1);
print("d flag keeps the eager form:", /(a)/d.test("a"), RegExp.$1);

var g = /a/g;
print("global test walks lastIndex:", g.test("aa"), g.lastIndex, g.test("aa"), g.lastIndex, g.test("aa"), g.lastIndex);
var y = /a/y;
y.lastIndex = 1;
print("sticky test:", y.test("ba"), y.lastIndex);
var frozen = /a/g;
frozen.test("a");
Object.defineProperty(frozen, "lastIndex", { writable: false });
try {
    frozen.test("a");
    print("frozen lastIndex on test:", "no error");
} catch (e) {
    print("frozen lastIndex on test:", e instanceof TypeError);
}

// 21.2.5.2.2 step 4 reads lastIndex whatever the flags are, and only then
// decides to ignore it, so a plain regexp's test still calls its valueOf
var read = false;
var plain = new RegExp();
plain.lastIndex = { valueOf: function () { read = true; return 0; } };
plain.test("");
print("a plain test still reads lastIndex:", read);

print("literal:", JSON.stringify("a.b.c".replace(".", "!")));
print("literal substitution:", JSON.stringify("abc".replace("b", "[$&|$`|$']")));
print("literal escape:", JSON.stringify("abc".replace("b", "$$")));
print("literal no match:", JSON.stringify("abc".replace("x", "!")));
print("literal function:", JSON.stringify("abc".replace("b", function (m, i, s) { return "<" + m + "," + i + "," + s + ">"; })));
print("literal first only:", JSON.stringify("aaa".replace("a", "X")));
print("literal empty:", JSON.stringify("abc".replace("", "X")));
print("literal with regexp metacharacters:", JSON.stringify("a$b".replace("$b", "Z")));

var coerced = 0;
print("replacement coerced once, even with no match:",
      JSON.stringify("ab".replace("z", { toString: function () { coerced++; return "Q"; } })), coerced);

var globalObject = this;
print("sloppy replacement this:", "x".replace("x", function () { return this === globalObject ? "global" : "other"; }));
print("strict replacement this:", "x".replace("x", function () { "use strict"; return this === undefined ? "undefined" : "other"; }));

/(\d)/.test("7");
"abc".replace("b", "X");
print("a literal replace leaves the statics alone:", RegExp.$1);
