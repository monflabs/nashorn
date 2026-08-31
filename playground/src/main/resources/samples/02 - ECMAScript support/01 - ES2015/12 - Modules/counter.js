// counter.js - a module: its top-level declarations are its own, not the global's
export let count = 0;

export function increment() {
    count += 1;
}

export default 'the counter module';

var secret = 'module scoped';
