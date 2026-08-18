// Property map / inline cache health: shapes, adds, deletes, megamorphic reads.
var total = 0;
for (var round = 0; round < 200; round++) {
    var objs = [];
    for (var i = 0; i < 500; i++) {
        var o = { x: i, y: i * 2 };
        o["k" + (i % 7)] = i;
        objs.push(o);
    }
    for (var j = 0; j < objs.length; j++) { total += objs[j].x + objs[j].y; }
}
if (total === 0) { throw new Error("optimised away"); }
