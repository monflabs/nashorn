# fetch

`fetch(input, init)` from the **fetch** library of `nashorn-libs`: a promise of a `Response`,
with `Headers`, `Request` and `Response` as WHATWG defines them - `ok`, `status`, `statusText`,
`headers`, `text()`, `json()`, `arrayBuffer()`, a body read once. The request runs on the JDK's
`HttpClient`; the promise settles on the script's thread through the event loop, so the run lasts
until every request has completed and `async`/`await` reads as it does anywhere else.

An HTTP error status *resolves* with `ok` false, as the specification says; a network or URL
failure *rejects* with a `TypeError`. Not implemented: streams, `AbortController`, `FormData`,
credentials and CORS (there is no origin), and a body other than a string.

The sample starts its own server with the JDK's `com.sun.net.httpserver` so that it works offline
and shows the request side too - the handlers are script functions the server calls.
