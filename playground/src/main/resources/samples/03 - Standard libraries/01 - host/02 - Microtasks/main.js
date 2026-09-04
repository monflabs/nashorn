// queueMicrotask runs a callback after the current code, before any timer,
// in order with promise reactions - the same queue await uses.
print('1  synchronous');

setTimeout(function () { print('7  timer, last of all'); }, 0);

Promise.resolve().then(function () { print('3  promise reaction'); });

queueMicrotask(function () {
    print('4  microtask');
    queueMicrotask(function () { print('6  microtask queued by a microtask - still before the timer'); });
});

(async function () {
    await null;
    print('5  after an await: a microtask too');
})();

print('2  synchronous, still');
