// String.prototype.split over a regular expression: the direct route matches
// forward from where the last piece ended, where the property-driven algorithm
// re-anchors at every position in between.
var subject = "";
for (var i = 0; i < 200; i++) { subject += "field" + i + " = value" + i + ";  "; }
var semis = /\s*;\s*/;
var spaces = /\s+/;
var equals = /\s*=\s*/;
var total = 0;
for (var round = 0; round < 300; round++) {
    total += subject.split(semis).length;
    total += subject.split(spaces).length;
    total += subject.split(/(\w+)\s*=/).length;
    total += "a,b,c,d,e".split(equals).length;
}
if (total === 0) { throw new Error("optimised away"); }
