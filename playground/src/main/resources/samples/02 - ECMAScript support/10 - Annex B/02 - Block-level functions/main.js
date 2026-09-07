// B.3.3: a function declared in a block is also visible in the enclosing
// function, the way browsers have always done it. Without Annex B a
// block-level function is block scoped, like let.

print(typeof before);  // undefined: hoisted as a var, assigned when the block runs

{
    function before() { return 'declared in a block'; }
}

print(typeof before, before());

function f(flag) {
    if (flag) {
        function g() { return 'g when true'; }
    } else {
        function g() { return 'g when false'; }
    }
    return g();
}
print(f(true), f(false));

// A var may share a name with a simple catch parameter (B.3.5)
try {
    throw 'thrown';
} catch (e) {
    var e = 'redeclared';
    print(e);
}

// Sloppy-mode octal escapes and legacy octal literals (B.1)
print('\101', 010);
