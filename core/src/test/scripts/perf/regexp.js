// Regexp: an exec loop over one large subject, test in a loop, and a literal
// evaluated inside a loop (the RegExpFactory cache).
var words = [];
for (var i = 0; i < 3000; i++) { words.push("w" + i + " " + (i % 13) + ";"); }
var text = words.join(" ");
var count = 0, sum = 0;
for (var round = 0; round < 6; round++) {
    var re = /(\w+) (\d+);/g, m;
    while ((m = re.exec(text)) !== null) { count++; sum += m[2].length; }
}
for (var j = 0; j < 20000; j++) {
    if (/^w\d+ 7;$/.test(words[j % words.length])) { count++; }
    if (words[j % words.length].search(/ 1[0-2];/) >= 0) { count++; }
}
if (count === 0 || sum === 0) { throw new Error("optimised away"); }
