// Symbol.prototype.description (ES2019) reads back the description a symbol was
// created with, on its own - without the "Symbol( ... )" wrapping that toString
// adds, and without having to parse it out. It is a getter, and it is nullable.

const s = Symbol('mySymbol');
console.log(s.description);            // mySymbol
console.log(s.toString());            // Symbol(mySymbol)

// no description at all reads back as undefined - distinct from an empty one
console.log(Symbol().description);            // undefined
console.log(JSON.stringify(Symbol('').description));   // ""

// well-known symbols carry their spec name as the description
console.log(Symbol.iterator.description);     // Symbol.iterator

// so a symbol can label itself without toString parsing
function describe(sym) {
    return sym.description === undefined ? '(anonymous)' : sym.description;
}
console.log(describe(Symbol('id')));   // id
console.log(describe(Symbol()));       // (anonymous)
