// Symbols as WeakMap keys (ES2023): a plain symbol can be held weakly.

const wm = new WeakMap();
const key = Symbol('id');
wm.set(key, { user: 'ada' });
console.log('has symbol key:', wm.has(key));            // true
console.log('get:', JSON.stringify(wm.get(key)));       // {"user":"ada"}

const ws = new WeakSet();
ws.add(key);
console.log('weakset has it:', ws.has(key));            // true

new WeakRef(key);                                        // a symbol may be a WeakRef target
console.log('WeakRef over a symbol: ok');

// a registered symbol (Symbol.for) is still rejected, as is any other primitive
try { wm.set(Symbol.for('shared'), 1); } catch (e) { console.log('registered symbol:', e.constructor.name); }  // TypeError
try { wm.set('a string', 1); } catch (e) { console.log('string key:', e.constructor.name); }  // TypeError
