// Async iteration (ES2018): an `async function*` is an async generator - it may
// both `yield` and `await` - and `for await (x of source)` consumes it, awaiting
// each step. The waiting below is real (timers from the host library).

function later(value, ms) {
    return new Promise(function (resolve) { setTimeout(function () { resolve(value); }, ms); });
}

// an async generator: yields values that each take time to produce
async function* pages() {
    for (let page = 1; page <= 3; page++) {
        const rows = await later(page * 10, 60);   // as if fetched from a server
        yield 'page ' + page + ' -> ' + rows + ' rows';
    }
}

// for await consumes the async iterator one settled value at a time
async function main() {
    for await (const line of pages()) {
        console.log(line);
    }

    // for await also accepts a plain (synchronous) iterable of promises,
    // awaiting each element via the async-from-sync adaptation
    const promises = [later('a', 30), later('b', 20), later('c', 10)];
    for await (const value of promises) {
        console.log('got ' + value);   // in source order: a, b, c
    }
    return 'done';
}

main().then(function (r) { console.log(r); });
