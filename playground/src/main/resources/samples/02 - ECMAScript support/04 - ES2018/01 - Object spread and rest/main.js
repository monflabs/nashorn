// Object spread copies a source's own enumerable properties into a new object
// literal; object rest gathers "everything else" when destructuring. Both are
// ES2018, and both work on plain objects the way array spread/rest work on
// iterables.

// spread: shallow-merge, later keys winning
const defaults = { host: 'localhost', port: 80, tls: false };
const override = { port: 443, tls: true };
const config = { ...defaults, ...override, path: '/api' };
console.log(JSON.stringify(config));   // {"host":"localhost","port":443,"tls":true,"path":"/api"}

// spread is a copy, not a reference: mutating the result leaves the source alone
const copy = { ...defaults };
copy.host = 'elsewhere';
console.log(defaults.host);            // still localhost

// rest in destructuring: pull a few named keys, keep the remainder as an object
const { host, ...rest } = config;
console.log(host);                     // localhost
console.log(JSON.stringify(rest));     // {"port":443,"tls":true,"path":"/api"}

// only own enumerable properties are spread - inherited ones are not
const base = { inherited: 1 };
const derived = Object.create(base);
derived.own = 2;
console.log(JSON.stringify({ ...derived }));   // {"own":2}
