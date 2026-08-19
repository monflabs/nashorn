// At risk from ArraySpeciesCreate: map, filter and slice must not walk
// constructor -> @@species on an ordinary array.
var a = [];
for (var i = 0; i < 64; i++) { a.push(i); }
var total = 0;
for (var n = 0; n < 40000; n++) {
    total += a.map(function (x) { return x + 1; }).length;
    total += a.filter(function (x) { return (x & 1) === 0; }).length;
    total += a.slice(8, 56).length;
}
if (total === 0) { throw new Error("optimised away"); }
