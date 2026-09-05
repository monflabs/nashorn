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
 * ES2018 template literal revision: an invalid escape sequence in a *tagged*
 * template no longer fails the parse - the cooked element is undefined while
 * the raw text is preserved. An untagged template still rejects it.
 *
 * @test
 * @run
 */

function tag(strs) {
    return "cooked=" + strs[0] + " raw=" + strs.raw[0];
}

// invalid escapes -> cooked undefined, raw kept
print(tag`\01`);
print(tag`\8`);
print(tag`\xg`);
print(tag`\u0g`);
print(tag`\u{g`);

// a valid escape still cooks
print(tag`okA`);

// an untagged template with an invalid escape is still a SyntaxError
try {
    eval("`\\u{g`");
    print("FAIL: untagged did not throw");
} catch (e) {
    print("untagged SyntaxError: " + (e instanceof SyntaxError));
}
