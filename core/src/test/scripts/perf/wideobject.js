// Wide objects: 40 properties added one at a time, past the 8-slot spill chunk.
var total = 0;
for (var round = 0; round < 300; round++) {
    var o = {};
    for (var i = 0; i < 40; i++) { o["p" + i] = i; }
    var keys = Object.keys(o);
    for (var k = 0; k < keys.length; k++) { total += o[keys[k]]; }
    var d = {};
    for (var j = 0; j < 40; j++) { d["q" + (j * 7 % 40)] = j; }
    total += Object.keys(d).length;
}
if (total === 0) { throw new Error("optimised away"); }
