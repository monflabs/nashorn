// Class fields (ES2022): instance and static fields, no constructor needed.

class Counter {
  count = 0;                 // instance field, initialised per instance
  static created = 0;        // static field, initialised once on the class
  label = `#${++Counter.created}`;   // initializers see the class and run in order

  bump() { return ++this.count; }
}

const a = new Counter();
const b = new Counter();
a.bump(); a.bump();
console.log('a.count =', a.count, 'a.label =', a.label);   // 2  #1
console.log('b.count =', b.count, 'b.label =', b.label);   // 0  #2
console.log('Counter.created =', Counter.created);          // 2

// a derived class initialises its fields after super() returns
class Base { constructor() { this.tag = 'base'; } }
class Derived extends Base {
  tag = this.tag + '+field';   // reads what the base constructor set
}
console.log('derived.tag =', new Derived().tag);            // base+field

// a computed key is evaluated once, at class definition, in source order
const key = 'dynamic';
class WithComputed { [key] = 42; }
console.log('computed field:', new WithComputed().dynamic); // 42
