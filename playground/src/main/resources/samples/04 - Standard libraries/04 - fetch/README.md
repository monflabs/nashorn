# fetch

`fetch(input, init)` from the **fetch** library of `nashorn-libs`: a promise of a `Response`,
with `Headers`, `Request` and `Response` as WHATWG defines them - `ok`, `status`, `statusText`,
`headers`, `text()`, `json()`, `arrayBuffer()`, a body read once. The request runs on the JDK's
`HttpClient`; the promise settles on the script's thread through the event loop, so the run lasts
until every request has completed and `async`/`await` reads as it does anywhere else.

The sample calls public APIs that need no key: current weather from
[Open-Meteo](https://open-meteo.com), and repository details from the GitHub REST API - including
a repository that does not exist, to show that an HTTP error status *resolves* with `ok` false, as
the specification says, while a network, DNS or connection failure *rejects* with a `TypeError`.
It needs the network, and prints a note instead of failing when there is none.

Not implemented: streams, `AbortController`, `FormData`, credentials and CORS (there is no
origin), and a body other than a string.
