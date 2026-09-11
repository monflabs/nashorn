# The host library

`host` provides the functions every JavaScript host has and ECMAScript does not define: the WHATWG
timers, `queueMicrotask`, and the Base64 pair. It is part of `nashorn-core`, but not installed automatically: hand `new HostLibrary()` to the
engine builder's `library(...)` to have it; a bare engine does not. (`jjs` installs it by default -
see [jjs](../reference/jjs.md).)

## Timers

```js
var id = setTimeout(function (who) { print('hello ' + who); }, 100, 'there');   // extra arguments reach the callback
clearTimeout(id);                                                             // cancels; harmless if it fired already

var ticks = 0;
var interval = setInterval(function () {
    if (++ticks === 3) clearInterval(interval);                               // an interval runs until cleared
}, 50);
```

- `setTimeout(callback, delay, ...args)` and `setInterval(callback, delay, ...args)` return small
  positive integer ids, per global; a delay that is missing or negative is 0. A call with nothing
  callable returns 0 and schedules nothing — a string is not evaluated.
- `clearTimeout(id)` and `clearInterval(id)` are interchangeable, as the specification says;
  unknown, missing or `null` ids are ignored.
- Callbacks run on the [event loop](overview.md#the-event-loop): on the script's thread, once the
  delay has passed and the script is between turns, in due-time order, `this` undefined. An `eval`
  that scheduled a timer returns when the last has fired; an interval keeps it alive until cleared
  or the thread is interrupted.
- An exception in a callback ends the `eval` with it, as an uncaught error would.

## queueMicrotask

```js
queueMicrotask(function () { print('after the current code, before any timer'); });
```

The callback goes on the microtask queue `then` and `await` use, in FIFO order with the promise
reactions queued before it; a microtask queued by a microtask still runs before the loop moves on
to timers. A non-callable argument is a `TypeError`.

## atob and btoa

```js
btoa('Hello, world')            // "SGVsbG8sIHdvcmxk"
atob('SGVsbG8sIHdvcmxk')        // "Hello, world"
atob(' SGVs bG8 ')              // "Hello" - whitespace and missing padding are forgiven
btoa(unescape(encodeURIComponent('café')))   // text beyond Latin-1: UTF-8 first
```

`btoa` encodes a *binary string* — one character per byte, every char code 0 to 255 — and throws
an `Error` for a character beyond that (a browser's `InvalidCharacterError`); `atob` decodes with
the forgiving-Base64 rules and throws an `Error` for input that is not Base64. Both convert their
argument with ToString and require one.

## From Java

`HostLibrary` in `org.monflabs.nashorn.libs` is the implementation: one class, a built-in
`ScriptFunction` per entry of its `Function` enum bound to one `switch`, and a timer table per
global — `initialize` runs once per global and hands each its own. The functions are ordinary
functions to a script (`Function.prototype` applies) and non-enumerable on the global, like the
language's own. Pass `new HostLibrary()` to the builder's `library(...)` to have it; there is no discovery.
