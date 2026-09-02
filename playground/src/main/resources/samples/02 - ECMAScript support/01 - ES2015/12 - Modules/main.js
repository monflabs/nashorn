// This file IS a module: import and export are reserved words, so a source
// that parses as a module runs as one - and in the playground, an import's
// specifier resolves to this sample's own tabs. app.js and counter.js are
// the files next to this one.
import { count, first, bump, viaNamespace } from './app.js';
import theDefault, { count as direct, increment } from './counter.js';

print('the default export of counter.js:', theDefault);
print('app.js re-exports count:', count, '- and counter.js agrees:', direct);
print('read through a namespace import at load time:', viaNamespace);

// live bindings: bump() calls increment() inside counter.js, and every
// imported view of count moves with it
bump();
increment();
print('after two increments:', count, '=', direct);

// a module runs once per realm: app.js's own increment() ran a single time,
// however many imports reach it
print('first, computed when app.js ran:', first);

// module scope: these declarations belong to this module, not the global
const local = 'module scoped';
export { count, local };
