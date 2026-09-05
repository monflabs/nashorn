// Promise.prototype.finally (ES2018): a callback that runs once a promise
// settles, whichever way. It takes no argument, and - unless it throws or
// returns a rejected promise - it passes the original outcome through
// unchanged. It is where cleanup belongs: the code you would otherwise have to
// write twice, in both then and catch.

function work(shouldFail) {
    return new Promise(function (resolve, reject) {
        setTimeout(function () {
            if (shouldFail) reject(new Error('boom'));
            else resolve('result');
        }, 30);
    });
}

function run(shouldFail) {
    let busy = true;                       // pretend this is a spinner / lock
    return work(shouldFail)
        .then(function (v) { console.log('resolved: ' + v); })
        .catch(function (e) { console.log('rejected: ' + e.message); })
        .finally(function () {
            busy = false;                  // cleanup, exactly once, either way
            console.log('cleanup ran (busy=' + busy + ')');
        });
}

// finally passes the value through: the chain's value is unchanged by it
Promise.resolve(42)
    .finally(function () { console.log('settling...'); })
    .then(function (v) { console.log('still ' + v); });

run(false).then(function () { return run(true); });
