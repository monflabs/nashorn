#!/usr/bin/env node
// Hashbang grammar (ES2023): the #! line above is a comment because it is the
// very first thing in the source. Everything below runs as normal.

console.log('the hashbang line above was ignored as a comment');
console.log('1 + 1 =', 1 + 1);

// away from the start, # is not a hashbang - here it is a private member
class Counter {
  #n = 0;
  bump() { return ++this.#n; }
}
console.log('a private #field still works:', new Counter().bump());  // 1
