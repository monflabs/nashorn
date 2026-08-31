// app.js - imports are live bindings, resolved before anything runs
import theDefault, { count, increment } from './counter.js';
import * as counter from './counter.js';

increment();                          // runs while this module evaluates

export { count };                     // re-export: still the same live binding
export const first = theDefault;
export function bump() { increment(); }
export const viaNamespace = counter.count;
