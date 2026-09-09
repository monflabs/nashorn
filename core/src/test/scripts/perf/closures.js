// Function-call throughput: closure creation, calls through call/apply, and arguments.
function make(k) { return function (x) { return x + k; }; }
function sum() { var s = 0; for (var i = 0; i < arguments.length; i++) { s += arguments[i]; } return s; }
var total = 0;
for (var round = 0; round < 20000; round++) {
    var fs = [];
    for (var i = 0; i < 16; i++) { fs.push(make(i)); }
    for (var j = 0; j < fs.length; j++) {
        total += fs[j](round) + fs[j].call(null, 1) + fs[j].apply(null, [2]);
    }
    total += sum(1, 2, 3, 4) + sum.apply(null, [round, 1]);
}
if (total === 0) { throw new Error("optimised away"); }
