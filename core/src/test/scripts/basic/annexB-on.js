/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * ECMA-262 Annex B is present when --annexB is on, which it is by default.
 *
 * @test
 * @run
 */

// The two halves of --annexB. Everything ECMA-262 Annex B adds is here when the
// flag is on and gone when it is off, which is the whole of what the flag
// means. B.3.5 - a var taking a simple catch parameter's name - is the one part
// that is not gated, because the flag it turns on is what makes an ordinary
// catch parameter visible at all.

var LIBRARY = {
    "escape":                       function () { return typeof escape; },
    "unescape":                     function () { return typeof unescape; },
    "String.prototype.substr":      function () { return typeof "".substr; },
    "String.prototype.trimLeft":    function () { return typeof "".trimLeft; },
    "String.prototype.trimRight":   function () { return typeof "".trimRight; },
    "String.prototype.anchor":      function () { return typeof "".anchor; },
    "String.prototype.big":         function () { return typeof "".big; },
    "String.prototype.blink":       function () { return typeof "".blink; },
    "String.prototype.bold":        function () { return typeof "".bold; },
    "String.prototype.fixed":       function () { return typeof "".fixed; },
    "String.prototype.fontcolor":   function () { return typeof "".fontcolor; },
    "String.prototype.fontsize":    function () { return typeof "".fontsize; },
    "String.prototype.italics":     function () { return typeof "".italics; },
    "String.prototype.link":        function () { return typeof "".link; },
    "String.prototype.small":       function () { return typeof "".small; },
    "String.prototype.strike":      function () { return typeof "".strike; },
    "String.prototype.sub":         function () { return typeof "".sub; },
    "String.prototype.sup":         function () { return typeof "".sup; },
    "Object.prototype.__proto__":   function () { return Object.prototype.hasOwnProperty("__proto__") ? "function" : "undefined"; },
    "Object.prototype.__defineGetter__":  function () { return typeof Object.prototype.__defineGetter__; },
    "Object.prototype.__defineSetter__":  function () { return typeof Object.prototype.__defineSetter__; },
    "Object.prototype.__lookupGetter__":  function () { return typeof Object.prototype.__lookupGetter__; },
    "Object.prototype.__lookupSetter__":  function () { return typeof Object.prototype.__lookupSetter__; },
    "Date.prototype.getYear":       function () { return typeof Date.prototype.getYear; },
    "Date.prototype.setYear":       function () { return typeof Date.prototype.setYear; },
    "Date.prototype.toGMTString":   function () { return typeof Date.prototype.toGMTString; },
    "RegExp.prototype.compile":     function () { return typeof RegExp.prototype.compile; }
};

// Each of these is legal only under Annex B.
var SYNTAX = [
    "<!-- an HTML-like open comment",
    "1;\n--> an HTML-like close comment",
    "l: function labelledFunctionDeclaration() {}",
    "if (true) function ifClauseFunctionDeclaration() {}",
    "for (var forInInitialiser = 0 in {}) ;",
    "{ function duplicateInABlock() {} function duplicateInABlock() {} }"
];

// Each of these means something different under Annex B, so they are asked what
// they do rather than whether they parse.
function callExpressionTarget() {
    // B.3.4: a runtime ReferenceError where the specification proper has an
    // early SyntaxError
    try {
        eval("(function f() { return {}; })() = 1;");
        return "no error";
    } catch (e) {
        return e.name;
    }
}

function blockFunctionHoisting() {
    // B.3.3: the declaration also binds the name in the variable environment
    return (function () {
        { function hoisted() {} }
        return typeof hoisted;
    })();
}

function legacyOctalEscape() {
    try {
        return new RegExp("\\07").test("\u0007") ? "matches" : "does not match";
    } catch (e) {
        return e.name;
    }
}

function quantifiedAssertion() {
    try {
        // built rather than written as a literal: a literal is compiled
        // when the script is parsed, so its refusal is not catchable here
        return new RegExp(".(?=Z)+").test("bZ") ? "matches" : "does not match";
    } catch (e) {
        return e.name;
    }
}

function parses(source) {
    try {
        eval(source);
        return true;
    } catch (e) {
        if (e instanceof SyntaxError) {
            return false;
        }
        throw e;
    }
}

function fail(what, expected, found) {
    throw new Error(what + ": expected " + expected + ", found " + found);
}

for (var name in LIBRARY) {
    if (LIBRARY[name]() !== "function") {
        fail(name, "function", LIBRARY[name]());
    }
}

for (var i = 0; i < SYNTAX.length; i++) {
    if (!parses(SYNTAX[i])) {
        fail(JSON.stringify(SYNTAX[i]), "to parse", "a SyntaxError");
    }
}

if (callExpressionTarget() !== "ReferenceError") {
    fail("a call expression as an assignment target", "ReferenceError", callExpressionTarget());
}
if (blockFunctionHoisting() !== "function") {
    fail("a block-level function declaration", "function", blockFunctionHoisting());
}
if (legacyOctalEscape() !== "matches") {
    fail("a legacy octal escape", "matches", legacyOctalEscape());
}
if (quantifiedAssertion() !== "matches") {
    fail("a quantified assertion", "matches", quantifiedAssertion());
}
