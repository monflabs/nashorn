// Promise.allSettled (ES2020): waits for every promise to settle and reports
// each outcome, never short-circuiting on a rejection the way Promise.all does.

const tasks = [
    Promise.resolve(1),
    Promise.reject(new Error('boom')),
    Promise.resolve(3)
];

Promise.allSettled(tasks).then(results => {
    for (const r of results) {
        if (r.status === 'fulfilled') {
            console.log('fulfilled:', r.value);
        } else {
            console.log('rejected:', r.reason.message);
        }
    }
    // fulfilled: 1
    // rejected: boom
    // fulfilled: 3

    const ok = results.filter(r => r.status === 'fulfilled').length;
    console.log(ok, 'of', results.length, 'succeeded');   // 2 of 3 succeeded
});
