// import.meta (ES2020): an object of host-provided metadata about the current
// module, whose standard field is import.meta.url - the module's own URL, the
// natural anchor for resolving a resource beside it. It is valid only inside a
// module (a plain script cannot use it), so the demonstration lives in the
// imported module meta.js, which reports its own URL.
import { describe } from './meta.js';

const url = describe();
console.log('main.js received the module URL:', url);
