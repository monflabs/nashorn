// RegExp.prototype.test and String.prototype.replace over a search string:
// neither needs what a full match result costs. test never reads the captures,
// and a search string is not a pattern.
var subject = "";
for (var i = 0; i < 120; i++) { subject += "key" + i + " = value" + i + "; "; }
var pair = /(\w+)\s*=\s*(\w+)/;
var wide = /((\w)(\w*))\s*=\s*((\w)(\w*))/;
var total = 0;
for (var round = 0; round < 4000; round++) {
    if (pair.test(subject)) { total++; }
    if (wide.test(subject)) { total++; }
    total += subject.replace("key7", "K").length;
    total += subject.replace("absent", "K").length;
}
if (total === 0) { throw new Error("optimised away"); }
