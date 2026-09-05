// Named capture groups (ES2018): (?<name>...) names a group, \k<name>
// backreferences it, match.groups exposes the captures by name, and $<name>
// substitutes them in replace(). Far more readable than counting positions.

const dateRe = /(?<year>\d{4})-(?<month>\d{2})-(?<day>\d{2})/;
const m = '2018-06-27'.match(dateRe);
console.log(m.groups.year + '/' + m.groups.month + '/' + m.groups.day);   // 2018/06/27

// $<name> in a replacement string - reorder the fields by name
const reordered = '2018-06-27'.replace(dateRe, '$<day>.$<month>.$<year>');
console.log(reordered);   // 27.06.2018

// \k<name> backreference: match a doubled word by naming the first capture
const doubled = /\b(?<word>\w+)\s+\k<word>\b/;
console.log('the the end'.match(doubled).groups.word);   // the

// named groups work with a global regex and exec, reading each match by name
const csv = 'a=1, b=2, c=3';
const pairRe = /(?<key>\w+)=(?<val>\d+)/g;
let pair;
while ((pair = pairRe.exec(csv)) !== null) {
    console.log(pair.groups.key + ' -> ' + pair.groups.val);
}
