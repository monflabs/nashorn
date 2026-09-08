// Promise.withResolvers (ES2024): a promise and its resolvers, unwrapped.

const { promise, resolve } = Promise.withResolvers();

// something elsewhere settles it later
setTimeout(() => resolve('settled from outside'), 10);

promise.then(value => console.log('resolved with:', value));
console.log('withResolvers returned:', typeof resolve, typeof promise.then);
