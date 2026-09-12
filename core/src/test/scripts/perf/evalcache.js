// Direct eval in a function: the compiled class is cached per (source, eval
// context), so a loop over a handful of distinct texts compiles each once
// instead of once per call. SunSpider's date-format-tofte is this shape.
var pieces = ["a()", "b()", "c()", "d()", "e()", "f()", "g()", "h()"];

function a() { return 1; }
function b() { return 2; }
function c() { return 3; }
function d() { return 4; }
function e() { return 5; }
function f() { return 6; }
function g() { return 7; }
function h() { return 8; }

function format(input) {
    var total = 0;
    for (var i = 0; i < input.length; i++) {
        total += eval(input[i]);
    }
    return total;
}

var total = 0;
for (var round = 0; round < 4000; round++) {
    total += format(pieces);
}
if (total === 0) { throw new Error("optimised away"); }
