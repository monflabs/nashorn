// String building: += in a loop builds a ConsString chain that is then flattened.
var total = 0;
for (var round = 0; round < 60; round++) {
    var s = "";
    for (var i = 0; i < 4000; i++) { s += "item" + i + ","; }
    total += s.length + s.charCodeAt(s.length >> 1);
    var t = "";
    for (var k = 0; k < 2000; k++) { t = t + "ab"; }
    total += t.indexOf("ba", 10);
}
if (total === 0) { throw new Error("optimised away"); }
