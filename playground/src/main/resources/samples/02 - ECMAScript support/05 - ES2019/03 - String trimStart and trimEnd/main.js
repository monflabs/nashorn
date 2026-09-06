// String.prototype.trimStart / trimEnd (ES2019) trim whitespace from one end
// only - the standard names for what browsers long shipped as trimLeft/trimRight.
// The old names remain, as Annex B aliases for the very same functions.

const padded = '\t  hello world  \n';

console.log(JSON.stringify(padded.trimStart()));  // "hello world  \n"
console.log(JSON.stringify(padded.trimEnd()));    // "\t  hello world"
console.log(JSON.stringify(padded.trim()));       // "hello world"

// trimLeft/trimRight are the SAME function objects (B.2.3), not copies
console.log(String.prototype.trimLeft === String.prototype.trimStart);   // true
console.log(String.prototype.trimRight === String.prototype.trimEnd);    // true

// a right-aligning helper, using only the end that matters
function padTable(rows) {
    return rows.map(function (r) { return ('     ' + r).slice(-5); });
}
console.log(JSON.stringify(padTable(['1', '22', '333'])));   // ["    1","   22","  333"]
