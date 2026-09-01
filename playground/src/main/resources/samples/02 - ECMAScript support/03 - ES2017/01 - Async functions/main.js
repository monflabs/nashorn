// async functions: a function whose body may await a promise, and which
// itself returns a promise. The waiting below is real - timers from the host
// library - so the timestamps show what runs when.
var started = Date.now();
function log(message) {
    console.log(String(Date.now() - started).padStart(4) + ' ms  ' + message);
}

// a promise that resolves after a delay: the shape of any asynchronous API
function later(value, ms) {
    return new Promise(function (resolve) { setTimeout(function () { resolve(value); }, ms); });
}

async function sequential() {
    const first = await later('first', 100);    // pauses this function, not the program
    const second = await later('second', 100);  // starts only after the first resolved
    return first + ' then ' + second;            // 200 ms in all
}

async function parallel() {
    const both = await Promise.all([later('a', 100), later('b', 100)]);   // started together
    return both.join(' and ');                                             // 100 ms in all
}

async function failing() {
    try {
        await Promise.reject(new Error('the operation failed'));
    } catch (e) {
        return 'caught: ' + e.message;   // a rejection is an exception at the await
    }
}

async function main() {
    log('main starts, synchronously, up to its first await');
    log(await sequential());
    log(await parallel());
    log(await failing());
    return 'done';
}

main().then(function (result) { log('main resolved with "' + result + '"'); });
log('after calling main(): it has returned a promise and is waiting on its first timer');
