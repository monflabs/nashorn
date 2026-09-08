// String.prototype.isWellFormed / toWellFormed (ES2024).

const ok = 'café \u{1F600}';      // é and an emoji (a proper surrogate pair)
const bad = 'lone \uD83D surrogate';   // a high surrogate with no low half

console.log('ok.isWellFormed():', ok.isWellFormed());    // true
console.log('bad.isWellFormed():', bad.isWellFormed());  // false

const fixed = bad.toWellFormed();
console.log('fixed.isWellFormed():', fixed.isWellFormed());          // true
console.log('replacement char present:', fixed.includes('�')); // true
