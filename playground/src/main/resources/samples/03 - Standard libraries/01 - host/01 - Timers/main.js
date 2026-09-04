// setTimeout, setInterval, clearTimeout, clearInterval: the host library's
// timers run on the engine's event loop. The synchronous code ends at the last
// line below, but the run - like a Node.js process - goes on until the last
// timer has fired, and the console shows them arriving in delay order.
var started = Date.now();
function stamp(label) {
    print(String(Date.now() - started).padStart(4) + ' ms  ' + label);
}

stamp('start');
setTimeout(function () { stamp('after 300 ms'); }, 300);
setTimeout(function (who) { stamp('after 100 ms, with an argument: ' + who); }, 100, 'you');

// clearTimeout cancels a timer that has not fired yet
var cancelled = setTimeout(function () { stamp('never printed'); }, 150);
clearTimeout(cancelled);

// setInterval repeats until cleared - clear it, or the run never ends
var ticks = 0;
var interval = setInterval(function () {
    ticks++;
    stamp('tick ' + ticks);
    if (ticks === 3) {
        clearInterval(interval);
        stamp('interval cleared');
    }
}, 80);

// a timer may schedule the next one
setTimeout(function () {
    stamp('first');
    setTimeout(function () { stamp('second, scheduled by the first'); }, 50);
}, 400);

stamp('synchronous code done - the run continues until the loop is idle');
