// The playground runs every script with the debugger on. Tick "Debug in
// Chrome", open chrome://inspect in Chrome, click "inspect" on the
// nashorn target, and run again: the debugger statement below pauses here,
// with the scopes, the call stack and the console in DevTools.

function fibonacci(n) {
    var a = 0, b = 1;
    for (var i = 0; i < n; i++) {
        var next = a + b;
        a = b;
        b = next;
        if (i === 5) {
            debugger;   // pauses when a client is attached; a no-op otherwise
        }
    }
    return a;
}

var results = [];
for (var n = 0; n < 10; n++) {
    results.push(fibonacci(n));
}
print(results.join(', '));

// console.log goes to DevTools too, with the objects expandable there
console.log('done', { results: results, when: new Date() });
