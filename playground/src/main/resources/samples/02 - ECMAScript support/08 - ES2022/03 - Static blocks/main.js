// Static initializer blocks (ES2022): run once at class definition, this = the class.

class Config {
  static #entries = new Map();     // static private state
  static keys = [];

  static {                          // set-up that needs statements
    for (const [k, v] of Object.entries({ host: 'localhost', port: 8080 })) {
      Config.#entries.set(k, v);
      Config.keys.push(k);
    }
  }

  static get(k) { return Config.#entries.get(k); }
}

console.log('keys:', Config.keys.join(', '));   // host, port
console.log('port:', Config.get('port'));       // 8080

// several static blocks run in source order, interleaved with static fields
class Ordered {
  static log = [];
  static { Ordered.log.push('block 1'); }
  static mid = Ordered.log.push('field') && 'mid';
  static { Ordered.log.push('block 2'); }
}
console.log('order:', Ordered.log.join(' -> '));  // block 1 -> field -> block 2
