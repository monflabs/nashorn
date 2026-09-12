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
 * A direct eval's compiled class is cached, so the same eval runs its
 * compiler once rather than once per call. What makes that safe is that the
 * key carries the whole compilation context - strict, new.target, super,
 * arguments, the private names in scope - and not the source alone, since the
 * same text means different things in different eval contexts. Each case here
 * runs one text in two contexts, in both orders, and each must keep its own
 * meaning: caching must not let the first answer stand for the second. The
 * template case is the one thing no key can capture - 13.2.8.3 hands out one
 * object per parse node and two evals are two nodes - so eval code holding a
 * backquote is not cached at all, which the identity test pins. The last case
 * is the hit path itself.
 *
 * @test
 * @option --optimistic-types=true
 * @run
 */

function sloppyOctal() {
    return eval("var evalOctal = 010; evalOctal");
}

function strictOctal() {
    "use strict";
    try {
        return eval("var evalOctal = 010; evalOctal");
    } catch (e) {
        return e.name;
    }
}

// legacy octal is a value in sloppy code and a SyntaxError in strict code
print("octal sloppy then strict:", sloppyOctal(), strictOctal());
print("octal strict then sloppy:", strictOctal(), sloppyOctal());

function newTargetInFunction() {
    return String(eval("new.target"));
}

function newTargetAtTopLevel() {
    // an indirect eval is program code, where new.target is a SyntaxError
    var indirect = eval;
    try {
        return String(indirect("new.target"));
    } catch (e) {
        return e.name;
    }
}

print("new.target in a function then at top level:",
      newTargetInFunction(), newTargetAtTopLevel());
print("new.target at top level then in a function:",
      newTargetAtTopLevel(), newTargetInFunction());

var base = { who: function () { return "base"; } };

var derived = {
    __proto__: base,
    inMethod() {
        return eval("super.who()");
    }
};

function superOutsideAMethod() {
    try {
        return eval("super.who()");
    } catch (e) {
        return e.name;
    }
}

print("super in a method then outside:", derived.inMethod(), superOutsideAMethod());
print("super outside then in a method:", superOutsideAMethod(), derived.inMethod());

function argumentsInAFunction() {
    return eval("arguments.length");
}

function argumentsInAFieldInitializer() {
    // a field initializer is one of the two ES2022 contexts where naming
    // arguments is an early error, direct eval included
    try {
        var C = eval("(class { f = eval('arguments.length'); })");
        return new C().f;
    } catch (e) {
        return e.name;
    }
}

print("arguments in a function then a field initializer:",
      argumentsInAFunction(1, 2), argumentsInAFieldInitializer());
print("arguments in a field initializer then a function:",
      argumentsInAFieldInitializer(), argumentsInAFunction(1, 2));

function withPrivate() {
    var C = eval("(class { #x = 1; has(o) { return eval('#x in o'); } })");
    return new C().has(new C());
}

function withoutPrivate() {
    try {
        return eval("#x in {}");
    } catch (e) {
        return e.name;
    }
}

print("#x in scope then out of scope:", withPrivate(), withoutPrivate());
print("#x out of scope then in scope:", withoutPrivate(), withPrivate());

function tag(parts) { return parts; }

function taggedTwice() {
    // two evaluations of the same text are two parse nodes, so two template
    // objects: sharing a compiled class would make them one
    return eval("tag`x`") === eval("tag`x`");
}

print("two evals of the same tagged template share an object:", taggedTwice());

function repeated() {
    var total = 0;
    for (var i = 0; i < 50; i++) {
        total += eval("1 + 1");
    }
    return total;
}

print("a repeated eval of the same text:", repeated());
