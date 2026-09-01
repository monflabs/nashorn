# The fetch library

`fetch` provides the WHATWG fetch API: `fetch(input, init)` returning a promise of a `Response`,
and the `Headers`, `Request` and `Response` classes. It is in `nashorn-libs` and discovered
automatically; `--libraries=fetch` selects it alone (it does not need `host`).

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
  `entries`/`keys`/`values` and iteration in name order, several values of one name joined with
  `", "`; an invalid header name is a `TypeError`.
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

## From Java

`FetchLibrary` in `org.monflabs.nashorn.libs` is the transport: one Java function that sends the
request through a shared `HttpClient` and, through the event loop's `pending()`, hands the
response back on the script's thread. The classes and `fetch` itself are script, in the library's
`fetch.js`, over that transport — the shape to copy for a library whose API is easiest written in
JavaScript and whose work is Java's.
