// Private members (ES2022): #fields, #methods, #accessors, statics, and #x in obj.

class Account {
  #balance = 0;                       // private field
  static #count = 0;                  // static private field
  constructor(opening) { this.#balance = opening; Account.#count++; }

  #log(op) { return `${op}: ${this.#balance}`; }   // private method
  get #summary() { return this.#log('balance'); }   // private accessor

  deposit(n) { this.#balance += n; return this.#summary; }
  static get opened() { return Account.#count; }

  static isAccount(o) { return #balance in o; }      // ergonomic brand check
}

const acc = new Account(100);
console.log(acc.deposit(50));            // balance: 150
console.log('opened:', Account.opened);  // 1

// private members are invisible to reflection
console.log('keys:', JSON.stringify(Object.keys(acc)));  // []
console.log('json:', JSON.stringify(acc));               // {}

// the brand check distinguishes an Account from a look-alike
console.log('isAccount(acc):', Account.isAccount(acc));  // true
console.log('isAccount({}):', Account.isAccount({}));    // false

// reaching a private field from the wrong object is a TypeError
try { Account.prototype.deposit.call({}, 1); }
catch (e) { console.log('wrong receiver:', e.constructor.name); }  // TypeError
