// RegExp match indices (ES2022): the d flag adds [start, end] offsets.

const re = /(?<year>\d{4})-(?<month>\d{2})/d;
const m = re.exec('date: 2022-06');

console.log('match:', m[0], 'at', JSON.stringify(m.indices[0]));   // 2022-06 at [6,13]
console.log('year group:', m[1], 'at', JSON.stringify(m.indices[1]));   // 2022 at [6,10]
console.log('named month at:', JSON.stringify(m.indices.groups.month)); // [11,13]

// a group that did not participate has an undefined entry
const opt = /(a)(b)?/d.exec('a');
console.log('optional group indices:', JSON.stringify(opt.indices[2] ?? null));  // null

// the flag shows up in the flags string, sorted first
console.log('flags:', re.flags, 'hasIndices:', re.hasIndices);   // dg? -> "d" ; true
