# Microtasks

`queueMicrotask(callback)` from the **host** library: the callback runs once the current script
code has finished, before any timer or other task, in order with promise reactions - because it
goes on the same microtask queue that `then` and `await` use.

The numbers in the output are the order the specification prescribes: all synchronous code, then
the microtask queue in FIFO order (a microtask queued by a microtask still runs before the loop
moves on), then the timer.
