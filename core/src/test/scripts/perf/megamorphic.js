// Megamorphic property reads: one site sees 24 shapes, past the relink threshold.
var shapes = [];
for (var s = 0; s < 24; s++) {
    var o = {};
    for (var f = 0; f <= s; f++) { o["f" + f] = f; }
    o.v = s;
    shapes.push(o);
}
function read(o) { return o.v; }
var total = 0;
for (var round = 0; round < 40000; round++) {
    total += read(shapes[round % shapes.length]);
}
if (total === 0) { throw new Error("optimised away"); }
