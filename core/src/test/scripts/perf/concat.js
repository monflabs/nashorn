// At risk from Phase 6: @@isConcatSpreadable must not be consulted per element.
var a = [1, 2, 3, 4, 5, 6, 7, 8], b = [9, 10], total = 0;
for (var i = 0; i < 300000; i++) {
    total += a.concat(b, i, [i, i + 1]).length;
}
if (total === 0) { throw new Error("optimised away"); }
