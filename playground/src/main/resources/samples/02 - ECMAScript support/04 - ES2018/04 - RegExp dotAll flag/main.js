// The `s` (dotAll) flag (ES2018): with it, `.` matches every character,
// including line terminators. Without it, `.` stops at a newline - the default
// that ES has always had.

const text = 'first line\nsecond line';

// no s flag: `.` will not cross the newline
console.log(/first.*second/.test(text));    // false

// with the s flag: `.` matches the newline too
console.log(/first.*second/s.test(text));   // true

// the flag shows up in .flags and .dotAll
const re = /a.b/s;
console.log(re.flags);     // s
console.log(re.dotAll);    // true

// a practical use: grab everything between two markers, newlines and all
const doc = '<body>\nhello\nworld\n</body>';
const body = doc.match(/<body>(.*)<\/body>/s);
console.log(JSON.stringify(body[1]));   // "\nhello\nworld\n"
