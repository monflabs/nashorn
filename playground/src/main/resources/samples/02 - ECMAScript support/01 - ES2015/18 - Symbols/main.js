//1. Object properties
let id = Symbol("id");
let user = {
    name: "John",
    age: 40,
    [id]: 111
};

for (let key in user) {
    console.log(key); // name, age without symbols
}

console.log(JSON.stringify(user)); // {"name":"John", "age": 40}
console.log(Object.keys(user)); // ["name", "age"]

console.log( "User Id: " + user[id] ); // Direct access by the symbol works

//2. Unique constants
const logLevels = {
    DEBUG: Symbol('debug'),
    INFO: Symbol('info'),
    WARN: Symbol('warn'),
    ERROR: Symbol('error'),
};
console.log(logLevels.DEBUG.toString(), 'debug message');
console.log(String(logLevels.INFO), 'info message');

// A symbol does not convert to a string implicitly: '' + sym is a TypeError
try {
    console.log('' + logLevels.WARN);
} catch (e) {
    console.log(e.name + ': ' + e.message);
}
console.log(logLevels.ERROR.description === undefined ? 'no description property before ES2019' : logLevels.ERROR.description);

//3. Equality Checks

console.log(Symbol('foo') === Symbol('foo'));  // false
console.log(Symbol.for('foo') === Symbol.for('foo'));  // true