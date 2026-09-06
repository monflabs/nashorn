// Nullish coalescing (ES2020): a ?? b evaluates to b only when a is null or
// undefined - unlike ||, it lets through 0, '', NaN and false, which are falsy
// but not nullish.

console.log(null ?? 'fallback');       // fallback
console.log(undefined ?? 'fallback');  // fallback
console.log(0 ?? 'fallback');          // 0    (|| would give 'fallback')
console.log('' ?? 'fallback');         // ''    (|| would give 'fallback')
console.log(false ?? 'fallback');      // false

// the right side is only evaluated when needed (short-circuit)
function side() { console.log('  computed'); return 'x'; }
console.log('kept' ?? side());         // kept   (side() never runs)

// a real use: a default that must survive a legitimate 0
function volume(config) {
    return config.gain ?? 1;           // 0 stays 0; a missing gain becomes 1
}
console.log(volume({ gain: 0 }));      // 0
console.log(volume({}));               // 1

// ?? cannot be mixed with && or || without parentheses - an early SyntaxError
// that keeps the precedence unambiguous: (a ?? b) || c is fine, a ?? b || c is not.
console.log((null ?? 'a') || 'b');     // a
