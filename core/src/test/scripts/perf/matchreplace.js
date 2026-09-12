// String.prototype.match and replace over a regular expression. The direct
// route walks a matcher; the property-driven one reads flags, writes lastIndex
// and calls exec through the object on every match.
var subject = "";
for (var i = 0; i < 120; i++) { subject += "key" + i + " = value" + i + "; "; }
var word = /[a-z]+/g;
var pair = /(\w+)\s*=\s*(\w+)/g;
var total = 0;
for (var round = 0; round < 400; round++) {
    total += subject.match(word).length;
    total += subject.replace(pair, "$2=$1").length;
    total += subject.replace(word, "x").length;
    total += subject.replace(pair, function (m, k, v) { return v + "=" + k; }).length;
}
if (total === 0) { throw new Error("optimised away"); }
