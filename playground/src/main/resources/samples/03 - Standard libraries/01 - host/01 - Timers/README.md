# Timers

`setTimeout`, `setInterval`, `clearTimeout` and `clearInterval` from the **host** standard
library, with WHATWG semantics: a delay in milliseconds, extra arguments passed to the
callback, small positive integer ids, `clearTimeout` and `clearInterval` interchangeable.

The callbacks run on the engine's **event loop**, on the script's own thread, once the delay has
passed and the script is between turns. An `eval` that scheduled a timer returns only when the
script is idle - so this run lasts about 450 ms, and the status line says so - while a script that
schedules nothing returns as soon as its synchronous code is done, exactly as before.

An interval nobody clears keeps the run alive forever; **Stop** ends it, the way the debugger's
`terminate` and a thread interrupt do for an embedder.
