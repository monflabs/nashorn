// Error cause (ES2022): wrap a low-level failure while keeping it reachable.

function readConfig() {
  try {
    JSON.parse('{ not valid');            // throws a SyntaxError
  } catch (inner) {
    throw new Error('failed to read config', { cause: inner });
  }
}

try {
  readConfig();
} catch (e) {
  console.log('message:', e.message);                 // failed to read config
  console.log('cause:', e.cause.constructor.name);    // SyntaxError
  console.log('has own cause:', Object.hasOwn(e, 'cause'));  // true
}

// the cause is only set when the options object actually carries one
console.log('no options -> cause:', new Error('plain').cause);   // undefined
