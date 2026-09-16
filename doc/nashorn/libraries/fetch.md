# The fetch library

`fetch` provides the WHATWG fetch API: `fetch(input, init)` returning a promise of a `Response`,
and the `Headers`, `Request` and `Response` classes. It is part of `nashorn-core`, but not installed automatically: hand `new FetchLibrary()` to the
engine builder's `library(...)` to have it (it does not need the `host` library). It does need the
[event loop](overview.md#the-event-loop) — `.eventLoop(true)` — since it returns a promise. Simply
not adding the library is the right setting for an engine that must not reach the network. (The
shell turns on both by default — see [The shell](../reference/shell.md).)

```js
(async function () {
    const response = await fetch('https://example.org/api/items', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: 'new item' })
    });
    if (!response.ok) {
        throw new Error('HTTP ' + response.status + ' ' + response.statusText);
    }
    const item = await response.json();
    print(item.id, response.headers.get('content-type'));
})();
```

The request runs on the JDK's `java.net.http.HttpClient` — asynchronously, with connection pooling,
following redirects — and the promise settles on the script's thread through the
[event loop](overview.md#the-event-loop), so an `eval` that started a request returns once the
response has been handled, and several requests started together are in flight together.

## What is there

- **`fetch(input, init)`** — `input` a URL string or a `Request`; `init.method` (any token,
  upper-cased), `init.headers` (a `Headers`, an array of `[name, value]` pairs, or a plain object),
  `init.body` (a string; not allowed on GET and HEAD). Resolves with a `Response` for *any* HTTP
  status — `ok` says whether it was 2xx — and rejects with a `TypeError` for a network failure, an
  unresolvable host or a malformed URL.
- **`Headers`** — case-insensitive names, `append`/`set`/`get`/`has`/`delete`, `forEach`,
  `entries()`/`keys()`/`values()` iterators in name order, and `Symbol.iterator`
  (`for (const [name, value] of headers)`), several values of one name joined with `", "`; an
  invalid header name is a `TypeError`.
- **`Request`** — `url`, `method`, `headers`, `text()`, `json()`, `clone()`.
- **`Response`** — `status`, `statusText`, `ok`, `url`, `headers`, `bodyUsed`; `text()`,
  `json()`, `arrayBuffer()` (each usable once, then a `TypeError`), `clone()`; `new Response(body,
  { status, statusText, headers })` for one of your own, `Response.error()`. The body is decoded by
  the response's `charset`, UTF-8 by default.

## What is not

Streams (`response.body` as a `ReadableStream`), `AbortController`/`signal`, `FormData` and
`Blob` bodies, `credentials`, `mode` and CORS (there is no origin), `cache`, `redirect: 'manual'`,
`Response.redirect`. Headers the JDK client controls itself (`Host`, `Content-Length`,
`Connection`) are set by it and cannot be overridden.

## Timeouts

Two ceilings, both on the JDK's `HttpClient`: **30 seconds to connect**, and **30 seconds for a
request to complete**. The second is configurable, and exists because there is no other way out of
a stalled request:

```java
// a tighter ceiling
.library(new FetchLibrary(Duration.ofSeconds(5)))

// none at all - what the specification describes
.library(new FetchLibrary(null))
```

A request in flight is a **pending operation** on the realm's [event
loop](overview.md#the-event-loop), so `eval` returns only once it has finished. A script cannot
cancel one — there is no `AbortSignal` here, and racing the promise against a timer settles the
*promise* while leaving the request, and therefore the loop, exactly where they were. So without a
ceiling a server that accepts a connection and then says nothing keeps an embedder waiting for as
long as it likes. On expiry the promise rejects with a `TypeError`, like any other network failure,
and the pending operation ends.

Pass `null` if your host does its own supervision and you would rather have the specification's
behaviour.

## The shape

The classes have the shape WebIDL gives them, because they are built-ins: `Headers.prototype`,
`Request.prototype` and `Response.prototype` are real prototype objects holding the methods, so
`hasOwnProperty`, `Headers.prototype.append.call(h, …)`, `instanceof` through the chain,
`Object.prototype.toString` (`[object Response]`) and monkey-patching all behave; `status`, `ok`,
`headers` and the rest are read-only accessors on the prototype (assignment is ignored, or a
`TypeError` in strict code); `Headers` is iterable through `Symbol.iterator`. All of it is Java:
`NativeHeaders`, `NativeRequest` and `NativeResponse` in `internal.objects` are `@ScriptClass`
classes that nasgen turns into the constructor and prototype objects, and `FetchLibrary` in
`org.monflabs.nashorn.libs` installs them into a global on request and provides `fetch` — a
built-in function that sends the request through a shared `HttpClient` and settles a promise on
the script's thread through the event loop.
